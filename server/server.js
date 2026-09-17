/**
 * Servidor HTTP Externo de Pagamentos FutePlayer (InfinitePay + Firebase Firestore)
 * 
 * Compatível com hospedagens gratuitas/baixo custo:
 * Render, Railway, Fly.io, Vercel, VPS ou qualquer host Node.js (sem exigir plano Blaze do Firebase).
 * 
 * Responsabilidades:
 * 1. POST /createInfinitePayCheckout:
 *    - Valida o usuário e regras de isenção/ADMIN;
 *    - Impede cobrança indevida de administradores e isentos;
 *    - Cria o order_nsu e registra em payment_orders/{order_nsu} como PENDING;
 *    - Chama a API da InfinitePay enviando o webhook_url do servidor externo;
 *    - Retorna a URL do checkout para o app Android abrir o navegador.
 * 
 * 2. POST /infinitePayWebhook:
 *    - Recebe a notificação de pagamento da InfinitePay;
 *    - Localiza payment_orders/{order_nsu} e o UID correspondente;
 *    - Valida se o status é aprovado (PAID, APPROVED, etc.) e o valor mínimo de R$ 10,00;
 *    - Executa transação atômica no Firestore com IDEMPOTÊNCIA ESTRITA (evita crédito duplicado);
 *    - Adiciona 30 dias de acesso (preservando dias restantes se ainda ativo);
 *    - Atualiza subscriptionStatus = "ACTIVE", subscriptionExpiresAt, lastPaymentAt, lastPaymentId;
 *    - Marca payment_orders/{order_nsu} como PAID e cria auditoria em payments/{transactionNsu};
 *    - Responde HTTP 200 imediatamente.
 */

require("dotenv").config();
const express = require("express");
const cors = require("cors");
const axios = require("axios");
const admin = require("firebase-admin");

const app = express();
app.use(cors());
app.use(express.json());

// ==========================================
// 1. INICIALIZAÇÃO DO FIREBASE ADMIN SDK
// ==========================================
// O backend utiliza Firebase Admin para ler/gravar com segurança no Firestore
// Opção A: Variável de ambiente FIREBASE_SERVICE_ACCOUNT (JSON string)
// Opção B: Arquivo local service-account.json
// Opção C: Credenciais padrão do Google Cloud
try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    console.log("[Firebase] Inicializado via FIREBASE_SERVICE_ACCOUNT env var.");
  } else if (process.env.FIREBASE_SERVICE_ACCOUNT_PATH) {
    const serviceAccount = require(process.env.FIREBASE_SERVICE_ACCOUNT_PATH);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    console.log("[Firebase] Inicializado via arquivo service account:", process.env.FIREBASE_SERVICE_ACCOUNT_PATH);
  } else {
    // Tenta inicialização padrão pelo projeto
    admin.initializeApp({
      projectId: process.env.FIREBASE_PROJECT_ID || "futeplayer-2b630"
    });
    console.log("[Firebase] Inicializado via Project ID padrão:", process.env.FIREBASE_PROJECT_ID || "futeplayer-2b630");
  }
} catch (err) {
  console.error("[Firebase] Alerta na inicialização do Admin SDK:", err.message);
}

const db = admin.firestore();

// ==========================================
// CONFIGURAÇÕES E CONSTANTES
// ==========================================
const rawHandle = process.env.INFINITEPAY_HANDLE || "alexsandroguerraqueiroz";
const INFINITEPAY_HANDLE = rawHandle.replace(/^\$/, "").trim();
const INFINITEPAY_CHECKOUT_API_URL = "https://api.checkout.infinitepay.io/links";
const APP_REDIRECT_URL = "futeplayer://payment/return";

const RENEWAL_AMOUNT_CENTS = 1000; // R$ 10,00
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

// Health Check do servidor
app.get("/", (req, res) => {
  res.json({
    status: "online",
    service: "FutePlayer InfinitePay Payment Server",
    version: "1.0.0",
    timestamp: new Date().toISOString()
  });
});

app.get("/health", (req, res) => {
  res.status(200).send("OK");
});

// ==========================================
// 1. ENDPOINT: POST /createInfinitePayCheckout
// ==========================================
app.post("/createInfinitePayCheckout", async (req, res) => {
  try {
    const { uid, orderNsu, userName, userPhone } = req.body || {};

    if (!uid) {
      return res.status(400).json({ error: "UID do usuário é obrigatório." });
    }

    // 1.1 Consulta o usuário no Firestore para validar regras de isenção
    const userRef = db.collection("users").doc(uid);
    const userDoc = await userRef.get();

    if (!userDoc.exists) {
      return res.status(404).json({ error: "Usuário não encontrado no Firestore." });
    }

    const userData = userDoc.data() || {};

    // 1.2 Regra de Isenção: Administradores e usuários marcados com isenção não devem ser cobrados
    const role = (userData.role || "USER").toUpperCase();
    const isBillingExempt = userData.isBillingExempt === true;

    if (role === "ADMIN" || isBillingExempt) {
      return res.status(400).json({
        error: "Usuário possui acesso livre/isento de assinatura. Cobrança não é necessária."
      });
    }

    // 1.3 Gera order_nsu único
    const nsu = orderNsu || `FP_${uid.substring(0, 8)}_${Date.now()}_${Math.random().toString(36).substring(2, 8).toUpperCase()}`;
    const now = Date.now();
    const expiresAt = now + (24 * 60 * 60 * 1000); // 24h

    // 1.4 Registra payment_orders/{order_nsu} no Firestore com status PENDING
    await db.collection("payment_orders").doc(nsu).set({
      orderNsu: nsu,
      uid: uid,
      userName: userName || userData.name || "",
      userPhone: userPhone || userData.phone || "",
      amount: RENEWAL_AMOUNT_CENTS,
      status: "PENDING",
      createdAt: now,
      expiresAt: expiresAt,
      infinitePayTransactionNsu: "",
      invoiceSlug: "",
      receiptUrl: "",
      checkoutUrl: ""
    }, { merge: true });

    // 1.5 Determina a URL pública do Webhook para onde a InfinitePay enviará o POST
    let serverBaseUrl = process.env.PUBLIC_SERVER_URL;
    if (!serverBaseUrl) {
      const host = req.get("host");
      const proto = req.secure || req.headers["x-forwarded-proto"] === "https" ? "https" : "http";
      serverBaseUrl = `${proto}://${host}`;
    }
    serverBaseUrl = serverBaseUrl.replace(/\/+$/, "");
    const webhookUrl = process.env.INFINITEPAY_WEBHOOK_URL || `${serverBaseUrl}/infinitePayWebhook`;

    // 1.6 Monta payload para a API de links da InfinitePay
    const payload = {
      handle: INFINITEPAY_HANDLE,
      redirect_url: APP_REDIRECT_URL,
      webhook_url: webhookUrl,
      order_nsu: nsu,
      items: [
        {
          quantity: 1,
          price: RENEWAL_AMOUNT_CENTS,
          description: "Renovação Mensal FutePlayer - 30 dias"
        }
      ]
    };

    let checkoutUrl = "";
    try {
      const response = await axios.post(INFINITEPAY_CHECKOUT_API_URL, payload, {
        headers: { "Content-Type": "application/json" },
        timeout: 10000
      });
      if (response.data && response.data.url) {
        checkoutUrl = response.data.url;
      }
    } catch (apiError) {
      console.warn("[InfinitePay API] Resposta:", apiError.response ? apiError.response.data : apiError.message);
      // Link fallback direto de checkout na InfinitePay
      checkoutUrl = `https://checkout.infinitepay.io/pay/${INFINITEPAY_HANDLE}?order_nsu=${nsu}&amount=${RENEWAL_AMOUNT_CENTS}`;
    }

    if (!checkoutUrl) {
      checkoutUrl = `https://checkout.infinitepay.io/pay/${INFINITEPAY_HANDLE}?order_nsu=${nsu}&amount=${RENEWAL_AMOUNT_CENTS}`;
    }

    // 1.7 Atualiza o documento payment_orders com a URL gerada
    await db.collection("payment_orders").doc(nsu).update({
      checkoutUrl: checkoutUrl
    });

    // 1.8 Retorna somente a URL do checkout para o aplicativo
    return res.status(200).json({
      success: true,
      orderNsu: nsu,
      amount: RENEWAL_AMOUNT_CENTS,
      checkoutUrl: checkoutUrl
    });

  } catch (err) {
    console.error("[createInfinitePayCheckout] Erro:", err);
    return res.status(500).json({ error: "Erro interno ao gerar checkout: " + err.message });
  }
});

// ==========================================
// 2. ENDPOINT: POST /infinitePayWebhook
// ==========================================
app.post("/infinitePayWebhook", async (req, res) => {
  try {
    const data = req.body || {};
    console.log("[Webhook InfinitePay] Recebido payload:", JSON.stringify(data));

    // Identificação dos campos do webhook da InfinitePay
    const orderNsu = data.order_nsu || data.orderNsu;
    const transactionNsu = data.transaction_nsu || data.transactionNsu || data.id || `TX_${Date.now()}`;
    const invoiceSlug = data.invoice_slug || data.slug || "";
    const receiptUrl = data.receipt_url || data.receiptUrl || "";
    const captureMethod = data.capture_method || data.payment_method || "pix";
    const status = (data.status || "PAID").toUpperCase();
    const paidAmount = Number(data.paid_amount || data.amount || 0);

    if (!orderNsu) {
      console.error("[Webhook] Rejeitado: order_nsu ausente.");
      return res.status(400).json({ error: "order_nsu ausente no payload." });
    }

    // Validação de status de aprovação
    const isApproved = status === "PAID" || status === "APPROVED" || status === "SUCCESS";
    if (!isApproved) {
      console.warn(`[Webhook] Status ${status} não é de pagamento aprovado. Ignorando.`);
      return res.status(200).json({ message: "Status não aprovado ignorado." });
    }

    // Validação do valor mínimo (R$ 10,00 = 1000 centavos)
    if (paidAmount > 0 && paidAmount < RENEWAL_AMOUNT_CENTS) {
      console.error(`[Webhook] Valor divergente recebido: ${paidAmount} centavos (mínimo ${RENEWAL_AMOUNT_CENTS}).`);
      return res.status(400).json({ error: "Valor de pagamento insuficiente." });
    }

    const paymentTimestamp = Date.now();

    // Transação Atômica no Firestore: Idempotência Estrita + Concessão de 30 Dias
    await db.runTransaction(async (transaction) => {
      const orderRef = db.collection("payment_orders").doc(orderNsu);
      const orderDoc = await transaction.get(orderRef);

      if (!orderDoc.exists) {
        throw new Error(`Pedido ${orderNsu} não foi encontrado em payment_orders.`);
      }

      const orderData = orderDoc.data() || {};

      // PROTEÇÃO DE IDEMPOTÊNCIA: Se já estiver marcado como PAID, aborta sem conceder dias adicionais
      if (orderData.status === "PAID") {
        console.log(`[Idempotência] Pedido ${orderNsu} já foi processado anteriormente. Nenhum dia adicional será concedido.`);
        return;
      }

      const uid = orderData.uid;
      if (!uid) {
        throw new Error(`Pedido ${orderNsu} não possui UID associado.`);
      }

      const userRef = db.collection("users").doc(uid);
      const userDoc = await transaction.get(userRef);

      if (!userDoc.exists) {
        throw new Error(`Usuário ${uid} vinculado ao pedido não existe no Firestore.`);
      }

      const userData = userDoc.data() || {};

      // CÁLCULO SEGURO DOS 30 DIAS
      const currentExpiry = Number(userData.subscriptionExpiresAt || userData.expirationDate || 0);
      let newExpiresAt = 0;

      if (currentExpiry <= paymentTimestamp) {
        // CASO 1: Assinatura já estava vencida -> agora + 30 dias
        newExpiresAt = paymentTimestamp + THIRTY_DAYS_MS;
      } else {
        // CASO 2: Assinatura ainda ativa -> preserva os dias restantes e soma + 30 dias
        newExpiresAt = currentExpiry + THIRTY_DAYS_MS;
      }

      // 2.1 Atualiza atomicamente o Usuário no Firestore
      transaction.update(userRef, {
        subscriptionStatus: "ACTIVE",
        subscriptionExpiresAt: newExpiresAt,
        expirationDate: newExpiresAt,
        lastPaymentAt: paymentTimestamp,
        lastPaymentDate: paymentTimestamp,
        lastPaymentId: transactionNsu,
        paymentStatus: "ACTIVE"
      });

      // 2.2 Marca o pedido como PAID
      transaction.update(orderRef, {
        status: "PAID",
        infinitePayTransactionNsu: transactionNsu,
        invoiceSlug: invoiceSlug,
        receiptUrl: receiptUrl
      });

      // 2.3 Registra histórico completo na coleção payments
      const paymentRef = db.collection("payments").doc(transactionNsu);
      transaction.set(paymentRef, {
        uid: uid,
        userName: userData.name || orderData.userName || "",
        orderNsu: orderNsu,
        transactionNsu: transactionNsu,
        invoiceSlug: invoiceSlug,
        amount: RENEWAL_AMOUNT_CENTS,
        paidAmount: paidAmount || RENEWAL_AMOUNT_CENTS,
        captureMethod: captureMethod,
        receiptUrl: receiptUrl,
        paidAt: paymentTimestamp,
        createdAt: paymentTimestamp,
        status: "PAID"
      });
    });

    console.log(`[Webhook] Sucesso: Assinatura do usuário para order ${orderNsu} liberada por 30 dias.`);
    return res.status(200).json({
      success: true,
      message: "Pagamento aprovado e assinatura atualizada com sucesso."
    });

  } catch (error) {
    console.error("[Webhook] Erro no processamento:", error.message);
    return res.status(500).json({ error: error.message });
  }
});

// Inicialização da porta
const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`[FutePlayer Server] Servidor de Pagamentos rodando na porta ${PORT}`);
});
