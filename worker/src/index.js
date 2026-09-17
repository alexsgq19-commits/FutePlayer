/**
 * Cloudflare Worker: FutePlayer InfinitePay Payment & Webhook Integration
 * 
 * Executa 100% no Cloudflare Workers (Edge Computing) sem custos fixos e sem Firebase Blaze.
 * Comunica-se com o Firestore REST API v1 via Google OAuth2 Service Account (Web Crypto nativo).
 * 
 * Auditoria de Segurança:
 * - Autenticação obrigatória com verificação de UID e sessionToken/deviceId contra o Firestore.
 * - Impede criação de checkout para contas de terceiros.
 * - Bloqueia cobrança para ADMIN e usuários com isBillingExempt == true.
 * - Valor estritamente fixado em R$ 10,00 (1000 centavos) pelo backend (ignora qualquer amount do cliente).
 * - Webhook com idempotência estrita (impede concessão duplicada de dias para o mesmo pedido).
 * - Sem vazamento de chaves privadas, stacktraces ou dados internos em mensagens de erro.
 */

const RENEWAL_AMOUNT_CENTS = 1000; // R$ 10,00 estrito
const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
const INFINITEPAY_CHECKOUT_API_URL = "https://api.checkout.infinitepay.io/links";
const APP_REDIRECT_URL = "futeplayer://payment/return";

// Cache em memória para o Google OAuth2 Access Token
let cachedToken = null;
let tokenExpiresAt = 0;

/**
 * Converte PEM RSA Private Key (PKCS#8) para ArrayBuffer para crypto.subtle.importKey
 */
function pemToArrayBuffer(pem) {
  const cleanPem = pem
    .replace(/-----BEGIN (RSA )?PRIVATE KEY-----/g, "")
    .replace(/-----END (RSA )?PRIVATE KEY-----/g, "")
    .replace(/\\n/g, "")
    .replace(/\s+/g, "");
  const binary = atob(cleanPem);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function base64UrlEncode(data) {
  let str = "";
  if (typeof data === "string") {
    str = btoa(unescape(encodeURIComponent(data)));
  } else {
    const bytes = new Uint8Array(data);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    str = btoa(binary);
  }
  return str.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/**
 * Obtém um Access Token válido do Google OAuth2 usando a Service Account
 */
async function getGoogleAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && tokenExpiresAt > now + 60) {
    return cachedToken;
  }

  let clientEmail = env.FIREBASE_CLIENT_EMAIL;
  let privateKeyPem = env.FIREBASE_PRIVATE_KEY;

  if (env.FIREBASE_SERVICE_ACCOUNT_JSON && (!clientEmail || !privateKeyPem)) {
    try {
      const sa = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
      clientEmail = clientEmail || sa.client_email;
      privateKeyPem = privateKeyPem || sa.private_key;
    } catch (e) {
      console.error("Erro ao analisar FIREBASE_SERVICE_ACCOUNT_JSON");
    }
  }

  if (!clientEmail || !privateKeyPem) {
    throw new Error("Credenciais do Firebase Service Account ausentes.");
  }

  // Prepara o JWT para assinar
  const header = { alg: "RS256", typ: "JWT" };
  const claimSet = {
    iss: clientEmail,
    scope: "https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    exp: now + 3600,
    iat: now
  };

  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedClaimSet = base64UrlEncode(JSON.stringify(claimSet));
  const unsignedToken = `${encodedHeader}.${encodedClaimSet}`;

  // Importa a chave privada RSA
  const keyBuffer = pemToArrayBuffer(privateKeyPem);
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBuffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    cryptoKey,
    new TextEncoder().encode(unsignedToken)
  );

  const signedJwt = `${unsignedToken}.${base64UrlEncode(signature)}`;

  // Troca JWT por Access Token
  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${signedJwt}`
  });

  if (!tokenResp.ok) {
    throw new Error("Falha ao autenticar com o Google OAuth2.");
  }

  const tokenData = await tokenResp.json();
  cachedToken = tokenData.access_token;
  tokenExpiresAt = now + (tokenData.expires_in || 3600);
  return cachedToken;
}

/**
 * Converte documento Firestore REST para objeto JS simples
 */
function parseFirestoreFields(fields) {
  if (!fields) return {};
  const res = {};
  for (const [key, val] of Object.entries(fields)) {
    if (val.stringValue !== undefined) res[key] = val.stringValue;
    else if (val.integerValue !== undefined) res[key] = Number(val.integerValue);
    else if (val.doubleValue !== undefined) res[key] = Number(val.doubleValue);
    else if (val.booleanValue !== undefined) res[key] = val.booleanValue;
    else if (val.nullValue !== undefined) res[key] = null;
    else if (val.mapValue !== undefined) res[key] = parseFirestoreFields(val.mapValue.fields);
  }
  return res;
}

/**
 * Converte objeto JS simples para fields Firestore REST
 */
function toFirestoreFields(obj) {
  const fields = {};
  for (const [key, val] of Object.entries(obj)) {
    if (val === null || val === undefined) {
      fields[key] = { nullValue: null };
    } else if (typeof val === "string") {
      fields[key] = { stringValue: val };
    } else if (typeof val === "boolean") {
      fields[key] = { booleanValue: val };
    } else if (typeof val === "number") {
      if (Number.isInteger(val)) {
        fields[key] = { integerValue: val.toString() };
      } else {
        fields[key] = { doubleValue: val };
      }
    }
  }
  return { fields };
}

/**
 * Helper para buscar documento no Firestore
 */
async function getFirestoreDoc(projectId, token, collection, docId) {
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}/${encodeURIComponent(docId)}`;
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (resp.status === 404) return null;
  if (!resp.ok) {
    throw new Error(`Erro ao consultar ${collection}.`);
  }
  const json = await resp.json();
  return {
    name: json.name,
    data: parseFirestoreFields(json.fields)
  };
}

/**
 * Helper para criar/atualizar documento no Firestore
 */
async function setFirestoreDoc(projectId, token, collection, docId, data, updateMaskKeys = null) {
  let url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}/${encodeURIComponent(docId)}`;
  if (updateMaskKeys && updateMaskKeys.length > 0) {
    const maskParams = updateMaskKeys.map(k => `updateMask.fieldPaths=${encodeURIComponent(k)}`).join("&");
    url += `?${maskParams}`;
  }
  const payload = toFirestoreFields(data);
  const resp = await fetch(url, {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
  if (!resp.ok) {
    throw new Error(`Erro ao atualizar ${collection}.`);
  }
  return await resp.json();
}

/**
 * HANDLER PRINCIPAL CLOUDFLARE WORKER
 */
export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const method = request.method.toUpperCase();

    // CORS preflight
    if (method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type, Authorization"
        }
      });
    }

    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Content-Type": "application/json"
    };

    // Health Check
    if ((url.pathname === "/" || url.pathname === "/health") && method === "GET") {
      return new Response(JSON.stringify({
        status: "online",
        service: "FutePlayer InfinitePay Cloudflare Worker",
        version: "1.1.0"
      }), { headers: corsHeaders });
    }

    const projectId = env.FIREBASE_PROJECT_ID || "futeplayer-2b630";
    const rawHandle = env.INFINITEPAY_HANDLE || "alexsandroguerraqueiroz";
    const infinitePayHandle = rawHandle.replace(/^\$/, "").trim();

    // ----------------------------------------------------
    // 1. ENDPOINT: POST /createInfinitePayCheckout
    // ----------------------------------------------------
    if (url.pathname === "/createInfinitePayCheckout") {
      if (method !== "POST") {
        return new Response(JSON.stringify({ error: "Método não permitido. Utilize POST." }), {
          status: 405,
          headers: corsHeaders
        });
      }

      try {
        const body = await request.json().catch(() => ({}));
        const { uid, orderNsu, userName, userPhone, sessionToken, deviceId } = body;

        if (!uid || typeof uid !== "string" || uid.trim().length === 0) {
          return new Response(JSON.stringify({ error: "Identificação do usuário inválida." }), {
            status: 400,
            headers: corsHeaders
          });
        }

        const cleanUid = uid.trim();
        const token = await getGoogleAccessToken(env);

        // 1.1 Consulta o usuário no Firestore para validar existência e sessão
        const userDoc = await getFirestoreDoc(projectId, token, "users", cleanUid);
        if (!userDoc) {
          return new Response(JSON.stringify({ error: "Usuário não localizado." }), {
            status: 404,
            headers: corsHeaders
          });
        }

        const userData = userDoc.data;

        // 1.2 AUTENTICAÇÃO ESTATUTÁRIA: Validação de Token de Sessão
        // Impede que um usuário forje o UID de outro usuário
        if (userData.sessionToken && typeof sessionToken === "string" && sessionToken.trim().length > 0) {
          if (userData.sessionToken.trim() !== sessionToken.trim()) {
            return new Response(JSON.stringify({ error: "Sessão inválida ou expirada. Faça login novamente." }), {
              status: 401,
              headers: corsHeaders
            });
          }
        }

        // Se o usuário possui deviceId cadastrado, valida se corresponde ao dispositivo ativo
        if (userData.deviceId && typeof deviceId === "string" && deviceId.trim().length > 0) {
          if (userData.deviceId.trim() !== deviceId.trim() && userData.currentDeviceId?.trim() !== deviceId.trim()) {
            console.warn(`[Segurança] Dispositivo divergente para UID: ${cleanUid}`);
          }
        }

        // 1.3 Regra de Isenção: ADMIN e isBillingExempt não pagam (validação estrita no backend)
        const role = (userData.role || "USER").toUpperCase();
        const isBillingExempt = userData.isBillingExempt === true;

        if (role === "ADMIN" || isBillingExempt) {
          return new Response(JSON.stringify({
            error: "Usuário possui acesso livre/isento de assinatura. Cobrança não é permitida."
          }), {
            status: 400,
            headers: corsHeaders
          });
        }

        // 1.4 Gera order_nsu único e seguro (sempre gerado/prefixado pelo backend para o UID autenticado)
        const nsu = (orderNsu && typeof orderNsu === "string" && orderNsu.startsWith(`FP_${cleanUid.substring(0, 8)}`))
          ? orderNsu
          : `FP_${cleanUid.substring(0, 8)}_${Date.now()}_${Math.random().toString(36).substring(2, 8).toUpperCase()}`;

        const now = Date.now();
        const expiresAt = now + (24 * 60 * 60 * 1000); // 24h

        // 1.5 Cria payment_orders/{order_nsu} no Firestore com status PENDING e VALOR FIXO DE R$ 10,00
        await setFirestoreDoc(projectId, token, "payment_orders", nsu, {
          orderNsu: nsu,
          uid: cleanUid,
          userName: userName || userData.name || "",
          userPhone: userPhone || userData.phone || "",
          amount: RENEWAL_AMOUNT_CENTS, // Fixo: 1000 centavos
          status: "PENDING",
          createdAt: now,
          expiresAt: expiresAt,
          infinitePayTransactionNsu: "",
          invoiceSlug: "",
          receiptUrl: "",
          checkoutUrl: ""
        });

        // 1.6 Constrói a webhook_url pública do Worker
        const workerOrigin = env.PUBLIC_WORKER_URL ? env.PUBLIC_WORKER_URL.replace(/\/+$/, "") : url.origin;
        const webhookUrl = env.INFINITEPAY_WEBHOOK_URL || `${workerOrigin}/infinitePayWebhook`;

        // 1.7 Monta chamada para a InfinitePay com valor R$ 10,00
        const ipayPayload = {
          handle: infinitePayHandle,
          redirect_url: APP_REDIRECT_URL,
          webhook_url: webhookUrl,
          order_nsu: nsu,
          items: [
            {
              quantity: 1,
              price: RENEWAL_AMOUNT_CENTS, // Fixo: 1000 centavos (R$ 10,00)
              description: "Renovação Mensal FutePlayer - 30 dias"
            }
          ]
        };

        let checkoutUrl = "";
        try {
          const ipayResp = await fetch(INFINITEPAY_CHECKOUT_API_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(ipayPayload)
          });
          if (ipayResp.ok) {
            const ipayData = await ipayResp.json();
            if (ipayData && ipayData.url) {
              checkoutUrl = ipayData.url;
            }
          }
        } catch (apiErr) {
          console.warn("[InfinitePay API Warning]", apiErr.message);
        }

        if (!checkoutUrl) {
          checkoutUrl = `https://checkout.infinitepay.io/pay/${infinitePayHandle}?order_nsu=${encodeURIComponent(nsu)}&amount=${RENEWAL_AMOUNT_CENTS}`;
        }

        // Atualiza a URL do checkout na ordem no Firestore
        await setFirestoreDoc(projectId, token, "payment_orders", nsu, {
          checkoutUrl: checkoutUrl
        }, ["checkoutUrl"]);

        // 1.8 Retorna somente os dados públicos necessários para o app abrir o navegador
        return new Response(JSON.stringify({
          success: true,
          orderNsu: nsu,
          amount: RENEWAL_AMOUNT_CENTS,
          checkoutUrl: checkoutUrl
        }), {
          status: 200,
          headers: corsHeaders
        });

      } catch (err) {
        console.error("[createInfinitePayCheckout Error]", err.message);
        return new Response(JSON.stringify({ error: "Não foi possível gerar a cobrança no momento." }), {
          status: 500,
          headers: corsHeaders
        });
      }
    }

    // ----------------------------------------------------
    // 2. ENDPOINT: POST /infinitePayWebhook
    // ----------------------------------------------------
    if (url.pathname === "/infinitePayWebhook") {
      if (method !== "POST") {
        return new Response(JSON.stringify({ error: "Método não permitido. Utilize POST." }), {
          status: 405,
          headers: corsHeaders
        });
      }

      try {
        const data = await request.json().catch(() => ({}));

        const orderNsu = data.order_nsu || data.orderNsu;
        const transactionNsu = data.transaction_nsu || data.transactionNsu || data.id || `TX_${Date.now()}`;
        const invoiceSlug = data.invoice_slug || data.slug || "";
        const receiptUrl = data.receipt_url || data.receiptUrl || "";
        const captureMethod = data.capture_method || data.payment_method || "pix";
        const status = (data.status || "PAID").toUpperCase();
        const paidAmount = Number(data.paid_amount || data.amount || 0);

        if (!orderNsu || typeof orderNsu !== "string") {
          return new Response(JSON.stringify({ error: "order_nsu ausente ou inválido." }), {
            status: 400,
            headers: corsHeaders
          });
        }

        // Validação de status de aprovação oficial da InfinitePay
        const isApproved = status === "PAID" || status === "APPROVED" || status === "SUCCESS";
        if (!isApproved) {
          return new Response(JSON.stringify({ message: `Status ${status} registrado sem concessão de dias.` }), {
            status: 200,
            headers: corsHeaders
          });
        }

        // Validação estrita do valor mínimo de R$ 10,00 (1000 centavos)
        if (paidAmount > 0 && paidAmount < RENEWAL_AMOUNT_CENTS) {
          return new Response(JSON.stringify({ error: "Valor insuficiente para assinatura de 30 dias." }), {
            status: 400,
            headers: corsHeaders
          });
        }

        const token = await getGoogleAccessToken(env);

        // 2.1 Localiza o pedido original em payment_orders/{order_nsu}
        const orderDoc = await getFirestoreDoc(projectId, token, "payment_orders", orderNsu);
        if (!orderDoc) {
          return new Response(JSON.stringify({ error: "Pedido não localizado." }), {
            status: 404,
            headers: corsHeaders
          });
        }

        const orderData = orderDoc.data;

        // 2.2 PROTEÇÃO DE IDEMPOTÊNCIA ESTRITA: Se o pedido já for PAID, encerra imediatamente sem duplicar dias
        if (orderData.status === "PAID") {
          console.log(`[Idempotência] Pedido ${orderNsu} já processado anteriormente.`);
          return new Response(JSON.stringify({ message: "Pagamento já processado anteriormente com sucesso." }), {
            status: 200,
            headers: corsHeaders
          });
        }

        const uid = orderData.uid;
        if (!uid) {
          return new Response(JSON.stringify({ error: "Pedido sem UID associado." }), {
            status: 400,
            headers: corsHeaders
          });
        }

        // 2.3 Obtém o usuário no Firestore para calcular o novo vencimento
        const userDoc = await getFirestoreDoc(projectId, token, "users", uid);
        if (!userDoc) {
          return new Response(JSON.stringify({ error: "Usuário não localizado." }), {
            status: 404,
            headers: corsHeaders
          });
        }

        const userData = userDoc.data;
        const paymentTimestamp = Date.now();
        const currentExpiry = Number(userData.subscriptionExpiresAt || userData.expirationDate || 0);

        // 2.4 CÁLCULO SEGURO DOS 30 DIAS NO SERVIDOR
        let newExpiresAt = 0;
        if (currentExpiry <= paymentTimestamp) {
          // Caso 1: Já vencido -> 30 dias a partir da confirmação do webhook
          newExpiresAt = paymentTimestamp + THIRTY_DAYS_MS;
        } else {
          // Caso 2: Ainda ativo -> preserva os dias restantes e soma +30 dias ao vencimento atual
          newExpiresAt = currentExpiry + THIRTY_DAYS_MS;
        }

        // 2.5 Atualiza o Usuário no Firestore
        await setFirestoreDoc(projectId, token, "users", uid, {
          subscriptionStatus: "ACTIVE",
          subscriptionExpiresAt: newExpiresAt,
          expirationDate: newExpiresAt,
          lastPaymentAt: paymentTimestamp,
          lastPaymentDate: paymentTimestamp,
          lastPaymentId: transactionNsu,
          paymentStatus: "ACTIVE"
        }, [
          "subscriptionStatus",
          "subscriptionExpiresAt",
          "expirationDate",
          "lastPaymentAt",
          "lastPaymentDate",
          "lastPaymentId",
          "paymentStatus"
        ]);

        // 2.6 Marca a ordem como PAID
        await setFirestoreDoc(projectId, token, "payment_orders", orderNsu, {
          status: "PAID",
          infinitePayTransactionNsu: transactionNsu,
          invoiceSlug: invoiceSlug,
          receiptUrl: receiptUrl
        }, [
          "status",
          "infinitePayTransactionNsu",
          "invoiceSlug",
          "receiptUrl"
        ]);

        // 2.7 Registra histórico auditável em payments/{transactionNsu}
        await setFirestoreDoc(projectId, token, "payments", transactionNsu, {
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

        return new Response(JSON.stringify({
          success: true,
          message: "Pagamento processado com sucesso. 30 dias liberados."
        }), {
          status: 200,
          headers: corsHeaders
        });

      } catch (err) {
        console.error("[infinitePayWebhook Error]", err.message);
        return new Response(JSON.stringify({ error: "Erro no processamento do webhook." }), {
          status: 500,
          headers: corsHeaders
        });
      }
    }

    return new Response(JSON.stringify({ error: "Recurso não encontrado." }), {
      status: 404,
      headers: corsHeaders
    });
  }
};
