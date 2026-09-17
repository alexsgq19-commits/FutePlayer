# FutePlayer - Servidor HTTP de Pagamentos (InfinitePay + Firestore)

Este backend substitui o Firebase Cloud Functions, permitindo funcionamento completo sem a necessidade do plano Blaze (Google Cloud).

## Hospedagem Recomendada (Gratuita / Baixo Custo)
- **Render** (render.com): Web Service gratuito ou $7/mês, suporte nativo a Node.js.
- **Railway** (railway.app): Deploy via GitHub, cobrança por uso.
- **Fly.io** ou **VPS** (DigitalOcean, Hetzner, Oracle Cloud Free Tier).

## Endpoints Disponíveis
- `GET /`: Health check / Status do serviço
- `GET /health`: Retorna 200 OK
- `POST /createInfinitePayCheckout`: Cria o pedido no Firestore (`PENDING`), gera link na InfinitePay e retorna a URL
- `POST /infinitePayWebhook`: Recebe notificações de pagamento da InfinitePay, aplica idempotência, calcula +30 dias e atualiza o Firestore

## Variáveis de Ambiente Necessárias
Configure as seguintes variáveis no painel da sua hospedagem:
- `PORT`: Porta do servidor (padrão: 3000 ou fornecida pela plataforma)
- `FIREBASE_PROJECT_ID`: `futeplayer-2b630`
- `INFINITEPAY_HANDLE`: `alexsandroguerraqueiroz`
- `PUBLIC_SERVER_URL`: URL pública do seu servidor (ex: `https://futeplayer-api.onrender.com`)
- `FIREBASE_SERVICE_ACCOUNT`: Conteúdo JSON da chave privada da Conta de Serviço do Firebase.
