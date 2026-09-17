/**
 * Firebase Cloud Functions para FutePlayer:
 * Sistema de Assinatura Manual Mensal via InfinitePay + Firestore
 * 
 * Regras Obrigatórias Atendidas:
 * 1. Não há cobrança recorrente automática no cartão;
 * 2. Pagamento manual iniciado pelo usuário (R$ 10,00 por 30 dias);
 * 3. Chaves e comunicação sensível restritas ao backend;
 * 4. Idempotência estrita: reprocessar o mesmo webhook não concede dias duplicados;
 * 5. Preservação de dias restantes se renovado antes de expirar;
 * 6. Atualização atômica via Firestore Transaction;
 * 7. Gravação completa em payment_orders e payments.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const axios = require("axios");

admin.initializeApp();
const db = admin.firestore();

// Configurações do ambiente InfinitePay
// O handle é a InfiniteTag (nome de usuário) cadastrada no app da InfinitePay, sem o caractere '$'
const rawHandle = process.env.INFINITEPAY_HANDLE || "alexsandroguerraqueiroz";
const INFINITEPAY_HANDLE = rawHandle.replace(/^\$/, "").trim();
const INFINITEPAY_CHECKOUT_API_URL = "https://api.checkout.infinitepay.io/links";
const APP_REDIRECT_URL = "futeplayer://payment/return";

const RENEWAL_AMOUNT_CENTS = 1000; // R$ 10,00
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

const CANONICAL_WEBHOOK_URL = "https://us-central1-futeplayer-2b630.cloudfunctions.net/infinitePayWebhook";

/**
 * 1. CRIAÇÃO DO CHECKOUT
 * Endpoint HTTPS chamado pelo app FutePlayer.
 */
exports.createInfinitePayCheckout = functions.https.onRequest(async (req, res) => {
  // CORS
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

  if (req.method === "OPTIONS") {
    return res.status(204).send("");
  }

  if (req.method !== "POST") {
    return res.status(405).json({ error: "Método não permitido. Use POST." });
  }

  try {
    const { uid, orderNsu, userName, userPhone } = req.body;

    if (!uid) {
      return res.status(400).json({ error: "UID do usuário é obrigatório." });
    }

    const nsu = orderNsu || `FP_${uid.substring(0, 8)}_${Date.now()}_${Math.random().toString(36).substring(2, 8).toUpperCase()}`;
    const now = Date.now();
    const expiresAt = now + (24 * 60 * 60 * 1000); // 24h

    // 1. Registra o pedido no Firestore com status PENDING
    await db.collection("payment_orders").doc(nsu).set({
      orderNsu: nsu,
      uid: uid,
      userName: userName || "",
      userPhone: userPhone || "",
      amount: RENEWAL_AMOUNT_CENTS,
      status: "PENDING",
      createdAt: now,
      expiresAt: expiresAt,
      infinitePayTransactionNsu: "",
      invoiceSlug: "",
      receiptUrl: "",
      checkoutUrl: ""
    }, { merge: true });

    // 2. Monta payload para a API da InfinitePay
    // Utiliza preferencialmente variável de ambiente ou a URL canônica fixa da Cloud Function
    const webhookUrl = process.env.INFINITEPAY_WEBHOOK_URL || CANONICAL_WEBHOOK_URL;
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

    const headers = {
      "Content-Type": "application/json"
    };

    let checkoutUrl = "";
    try {
      const response = await axios.post(INFINITEPAY_CHECKOUT_API_URL, payload, { headers, timeout: 10000 });
      if (response.data && response.data.url) {
        checkoutUrl = response.data.url;
      }
    } catch (apiError) {
      console.warn("InfinitePay API notice:", apiError.response ? apiError.response.data : apiError.message);
      // Link direto do checkout com identificador handle e order_nsu
      checkoutUrl = `https://checkout.infinitepay.io/pay/${INFINITEPAY_HANDLE}?order_nsu=${nsu}&amount=${RENEWAL_AMOUNT_CENTS}`;
    }

    if (!checkoutUrl) {
      checkoutUrl = `https://checkout.infinitepay.io/pay/${INFINITEPAY_HANDLE}?order_nsu=${nsu}&amount=${RENEWAL_AMOUNT_CENTS}`;
    }

    // Atualiza checkoutUrl no pedido
    await db.collection("payment_orders").doc(nsu).update({
      checkoutUrl: checkoutUrl
    });

    return res.status(200).json({
      success: true,
      orderNsu: nsu,
      amount: RENEWAL_AMOUNT_CENTS,
      checkoutUrl: checkoutUrl
    });

  } catch (err) {
    console.error("Erro ao criar checkout:", err);
    return res.status(500).json({ error: "Erro interno ao gerar checkout: " + err.message });
  }
});

/**
 * 2. WEBHOOK DA INFINITEPAY
 * Chamado automaticamente pela InfinitePay quando o pagamento for aprovado.
 */
exports.infinitePayWebhook = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    return res.status(405).send("Method Not Allowed");
  }

  try {
    const data = req.body || {};
    console.log("Recebido Webhook InfinitePay:", JSON.stringify(data));

    // Identificação dos campos do webhook da InfinitePay
    const orderNsu = data.order_nsu || data.orderNsu;
    const transactionNsu = data.transaction_nsu || data.transactionNsu || data.id || `TX_${Date.now()}`;
    const invoiceSlug = data.invoice_slug || data.slug || "";
    const receiptUrl = data.receipt_url || data.receiptUrl || "";
    const captureMethod = data.capture_method || data.payment_method || "pix";
    const status = (data.status || "PAID").toUpperCase();
    const paidAmount = Number(data.paid_amount || data.amount || 0);

    if (!orderNsu) {
      console.error("Webhook rejeitado: order_nsu ausente.");
      return res.status(400).json({ error: "order_nsu ausente." });
    }

    // Validação de status de pagamento
    const isApproved = status === "PAID" || status === "APPROVED" || status === "SUCCESS";
    if (!isApproved) {
      console.warn(`Webhook: Status não é aprovado (${status}). Ignorando.`);
      return res.status(200).json({ message: "Status não aprovado ignorado." });
    }

    // Validação de valor mínimo de R$ 10,00 (1000 centavos)
    if (paidAmount > 0 && paidAmount < RENEWAL_AMOUNT_CENTS) {
      console.error(`Webhook: Valor divergente (${paidAmount} < ${RENEWAL_AMOUNT_CENTS}).`);
      return res.status(400).json({ error: "Valor divergente." });
    }

    const paymentTimestamp = Date.now();

    // Transação Firestore para Idempotência e Cálculo Seguro dos 30 Dias
    await db.runTransaction(async (transaction) => {
      const orderRef = db.collection("payment_orders").doc(orderNsu);
      const orderDoc = await transaction.get(orderRef);

      if (!orderDoc.exists) {
        throw new Error(`Pedido ${orderNsu} não existe na coleção payment_orders.`);
      }

      const orderData = orderDoc.data();

      // IDEMPOTÊNCIA: Se já estiver pago, retorna sucesso sem duplicar dias
      if (orderData.status === "PAID") {
        console.log(`Idempotência acionada: Pedido ${orderNsu} já foi marcado como PAID anteriormente.`);
        return;
      }

      const uid = orderData.uid;
      if (!uid) {
        throw new Error(`Pedido ${orderNsu} não possui UID vinculado.`);
      }

      const userRef = db.collection("users").doc(uid);
      const userDoc = await transaction.get(userRef);

      if (!userDoc.exists) {
        throw new Error(`Usuário com UID ${uid} não existe no Firestore.`);
      }

      const userData = userDoc.data();

      // CÁLCULO DOS 30 DIAS
      const currentExpiry = Number(userData.subscriptionExpiresAt || userData.expirationDate || 0);
      let newExpiresAt = 0;

      if (currentExpiry <= paymentTimestamp) {
        // CASO 1: Assinatura já estava vencida -> agora + 30 dias
        newExpiresAt = paymentTimestamp + THIRTY_DAYS_MS;
      } else {
        // CASO 2: Assinatura ainda ativa -> soma + 30 dias preservando os dias restantes
        newExpiresAt = currentExpiry + THIRTY_DAYS_MS;
      }

      // 1. Atualizar Usuário
      transaction.update(userRef, {
        subscriptionStatus: "ACTIVE",
        subscriptionExpiresAt: newExpiresAt,
        expirationDate: newExpiresAt,
        lastPaymentAt: paymentTimestamp,
        lastPaymentDate: paymentTimestamp,
        lastPaymentId: transactionNsu,
        paymentStatus: "ACTIVE"
      });

      // 2. Atualizar payment_orders
      transaction.update(orderRef, {
        status: "PAID",
        infinitePayTransactionNsu: transactionNsu,
        invoiceSlug: invoiceSlug,
        receiptUrl: receiptUrl
      });

      // 3. Criar registro detalhado em payments
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

    console.log(`Pagamento processado com sucesso para order ${orderNsu}!`);
    return res.status(200).json({ success: true, message: "Pagamento aprovado e registrado." });

  } catch (error) {
    console.error("Erro ao processar webhook:", error);
    return res.status(500).json({ error: error.message });
  }
});
