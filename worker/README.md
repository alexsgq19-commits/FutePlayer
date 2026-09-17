# FutePlayer - Cloudflare Worker Backend de Pagamentos (InfinitePay + Firestore)

Este Cloudflare Worker substitui completamente as Cloud Functions do Firebase para o processamento de pagamentos e recebimento de webhooks da InfinitePay, operando **100% no plano gratuito da Cloudflare** e sem exigir o plano Blaze do Firebase.

---

## 1. Secrets e Variáveis de Ambiente no Cloudflare

No painel da Cloudflare (Workers & Pages -> Seu Worker -> Settings -> Variables and Secrets) ou via linha de comando `wrangler secret put`, cadastre:

### Variáveis de Texto Simples (Variables)
- `FIREBASE_PROJECT_ID`: `futeplayer-2b630`
- `INFINITEPAY_HANDLE`: `alexsandroguerraqueiroz`
- `PUBLIC_WORKER_URL`: `https://futeplayer-payment-worker.<seu-subdominio>.workers.dev`

### Secrets Criptografados (Secrets)
Para permitir que o Worker acerte leituras e escritas atômicas no Firestore do Firebase sem o SDK de Node, cadastre:
- `FIREBASE_CLIENT_EMAIL`: Email da conta de serviço (ex: `firebase-adminsdk-xxxxx@futeplayer-2b630.iam.gserviceaccount.com`)
- `FIREBASE_PRIVATE_KEY`: A chave privada RSA completa começando com `-----BEGIN PRIVATE KEY-----` e terminando com `-----END PRIVATE KEY-----`
*(Ou alternativamente cadastre `FIREBASE_SERVICE_ACCOUNT_JSON` com o conteúdo completo do JSON baixado do Firebase Console).*

> **Onde obter a chave no Firebase?**
> 1. Acesse o [Firebase Console](https://console.firebase.google.com/project/futeplayer-2b630/settings/serviceaccounts/adminsdk)
> 2. Vá em **Configurações do Projeto -> Contas de Serviço**
> 3. Clique em **Gerar nova chave privada**

---

## 2. Endpoints Disponíveis no Cloudflare Worker

1. `GET /` ou `GET /health`
   - Retorna status do serviço (`online`) e timestamp.

2. `POST /createInfinitePayCheckout`
   - Payload enviado pelo app Android:
     ```json
     {
       "uid": "USER_UID",
       "userName": "Nome do Usuário",
       "userPhone": "11999999999",
       "sessionToken": "TOKEN_DE_SESSAO",
       "orderNsu": "FP_uid_timestamp_random",
       "amount": 1000
     }
     ```
   - Valida existência do usuário no Firestore;
   - Impede cobrança se `role == ADMIN` ou `isBillingExempt == true`;
   - Cria documento `payment_orders/{order_nsu}` com status `PENDING`;
   - Solicita link à InfinitePay (`https://api.checkout.infinitepay.io/links`) enviando `webhook_url: "https://<worker>/infinitePayWebhook"`;
   - Retorna a URL do checkout para abertura no navegador.

3. `POST /infinitePayWebhook`
   - Recebe webhook da InfinitePay quando o cliente paga R$ 10,00;
   - Valida status de pagamento (`PAID` / `APPROVED`) e valor de R$ 10,00;
   - **Idempotência**: Se o pedido já estiver com status `PAID`, ignora reprocessamentos para nunca duplicar dias;
   - Atualiza `subscriptionExpiresAt` com **+30 dias** (preservando dias restantes se ainda ativo);
   - Atualiza `subscriptionStatus = "ACTIVE"`, `lastPaymentAt` e `lastPaymentId`;
   - Marca `payment_orders/{order_nsu}` como `PAID` e salva histórico na coleção `payments`.
   - Responde HTTP 200.

---

## 3. Como Fazer o Deploy (Quando Desejar)

1. No diretório `/worker`, instale as dependências locais:
   ```bash
   npm install
   ```
2. Faça login na sua conta Cloudflare:
   ```bash
   npx wrangler login
   ```
3. Publique o Worker:
   ```bash
   npx wrangler deploy
   ```
4. Configure as secrets no painel da Cloudflare ou via comando:
   ```bash
   npx wrangler secret put FIREBASE_CLIENT_EMAIL
   npx wrangler secret put FIREBASE_PRIVATE_KEY
   ```
