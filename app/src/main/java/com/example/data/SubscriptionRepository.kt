package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.models.PaymentOrder
import com.example.data.models.PaymentRecord
import com.example.data.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubscriptionRepository(private val context: Context) {

    companion object {
        private const val TAG = "SubscriptionRepo"
        const val RENEWAL_AMOUNT_CENTS = 1000L // R$ 10,00
        const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

        // InfiniteTag padrão do lojista (sem $)
        private const val DEFAULT_HANDLE = "alexsandroguerraqueiroz"

        // Endpoint configurável do Cloudflare Worker de Pagamentos
        // O app pode ler de 'app_config/settings.paymentServerUrl' ou usar o valor padrão abaixo.
        // Substitua pelo subdomínio do seu Cloudflare Worker (ex: https://futeplayer-payment-worker.SEU_SUBDOMINIO.workers.dev)
        private const val DEFAULT_BACKEND_BASE_URL = "https://futeplayer-payment-worker.workers.dev"
        private const val PREFS_KEY_BACKEND_URL = "custom_payment_server_url"
    }

    /**
     * Retorna a URL base do backend de pagamento configurada no app ou no Firestore.
     */
    fun getBackendBaseUrl(): String {
        val prefs = context.getSharedPreferences("futemais_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREFS_KEY_BACKEND_URL, DEFAULT_BACKEND_BASE_URL)
            ?.trim()
            ?.removeSuffix("/")
            ?: DEFAULT_BACKEND_BASE_URL
    }

    /**
     * Permite atualizar a URL do backend de pagamento em tempo de execução via configuração remota ou admin.
     */
    fun setBackendBaseUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        val prefs = context.getSharedPreferences("futemais_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_BACKEND_URL, clean).apply()
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseApp.getInstance()
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore initialization error: ${e.message}")
            null
        }
    }

    /**
     * Gera um orderNsu único associado ao UID do usuário:
     * Exemplo: FP_uid_timestamp_random
     */
    fun generateOrderNsu(uid: String): String {
        val cleanUid = uid.replace(Regex("[^a-zA-Z0-9]"), "").take(10)
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
        return "FP_${cleanUid}_${timestamp}_$random"
    }

    /**
     * Solicita ao backend (Firebase Cloud Function) a criação do checkout da InfinitePay.
     * O Android NUNCA usa a API Secret da InfinitePay diretamente.
     */
    suspend fun createCheckout(user: User): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        val uid = user.uid
        if (uid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Usuário inválido ou não autenticado."))
        }

        val orderNsu = generateOrderNsu(uid)
        val now = System.currentTimeMillis()
        val expiresAt = now + (24L * 60L * 60L * 1000L) // Link expira em 24h

        val newOrder = PaymentOrder(
            orderNsu = orderNsu,
            uid = uid,
            amount = RENEWAL_AMOUNT_CENTS,
            status = "PENDING",
            createdAt = now,
            expiresAt = expiresAt,
            checkoutUrl = ""
        )

        // 1. Registra o pedido no Firestore com status PENDING
        val db = firestore
        if (db != null) {
            try {
                val orderData = hashMapOf<String, Any>(
                    "orderNsu" to orderNsu,
                    "uid" to uid,
                    "userName" to user.name,
                    "userPhone" to user.phone,
                    "amount" to RENEWAL_AMOUNT_CENTS,
                    "status" to "PENDING",
                    "createdAt" to now,
                    "expiresAt" to expiresAt,
                    "infinitePayTransactionNsu" to "",
                    "invoiceSlug" to "",
                    "receiptUrl" to "",
                    "checkoutUrl" to ""
                )
                db.collection("payment_orders").document(orderNsu).set(orderData).await()
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao registrar payment_orders localmente: ${e.message}")
            }
        }

        // 2. Chama o Servidor / Cloudflare Worker POST /createInfinitePayCheckout
        try {
            val jsonBody = JSONObject().apply {
                put("uid", uid)
                put("orderNsu", orderNsu)
                put("amount", RENEWAL_AMOUNT_CENTS)
                put("userName", user.name)
                put("userPhone", user.phone)
                if (user.sessionToken.isNotBlank()) {
                    put("sessionToken", user.sessionToken)
                }
                if (user.deviceId.isNotBlank()) {
                    put("deviceId", user.deviceId)
                }
            }

            val backendUrl = getBackendBaseUrl()
            val request = Request.Builder()
                .url("$backendUrl/createInfinitePayCheckout")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)
                val checkoutUrl = respJson.optString("checkoutUrl", respJson.optString("url", ""))

                if (checkoutUrl.isNotBlank()) {
                    newOrder.checkoutUrl = checkoutUrl
                    // Atualiza URL no Firestore
                    db?.collection("payment_orders")?.document(orderNsu)?.update("checkoutUrl", checkoutUrl)?.await()
                    Result.success(newOrder)
                } else {
                    // Fallback para URL direta do checkout associando handle e orderNsu
                    val fallbackUrl = "https://checkout.infinitepay.io/pay/$DEFAULT_HANDLE?order_nsu=$orderNsu&amount=$RENEWAL_AMOUNT_CENTS"
                    newOrder.checkoutUrl = fallbackUrl
                    Result.success(newOrder)
                }
            } else {
                // Se o servidor externo estiver offline ou retornando erro, usa link direto com handle e orderNsu
                val fallbackUrl = "https://checkout.infinitepay.io/pay/$DEFAULT_HANDLE?order_nsu=$orderNsu&amount=$RENEWAL_AMOUNT_CENTS"
                newOrder.checkoutUrl = fallbackUrl
                db?.collection("payment_orders")?.document(orderNsu)?.update("checkoutUrl", fallbackUrl)?.await()
                Result.success(newOrder)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao chamar backend externo de checkout: ${e.message}", e)
            // Em caso de indisponibilidade temporária de rede com o backend, provê link de checkout seguro
            val fallbackUrl = "https://checkout.infinitepay.io/pay/$DEFAULT_HANDLE?order_nsu=$orderNsu&amount=$RENEWAL_AMOUNT_CENTS"
            newOrder.checkoutUrl = fallbackUrl
            db?.collection("payment_orders")?.document(orderNsu)?.update("checkoutUrl", fallbackUrl)?.await()
            Result.success(newOrder)
        }
    }

    /**
     * Observa o status do pedido de pagamento em tempo real no Firestore.
     * Quando o backend receber o Webhook da InfinitePay, o status mudará para "PAID".
     */
    fun observePaymentOrder(orderNsu: String): Flow<PaymentOrder?> = callbackFlow {
        val db = firestore
        if (db == null || orderNsu.isBlank()) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val listener = db.collection("payment_orders").document(orderNsu)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing order $orderNsu: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val order = PaymentOrder(
                        orderNsu = snap.getString("orderNsu") ?: snap.id,
                        uid = snap.getString("uid") ?: "",
                        amount = snap.getLong("amount") ?: RENEWAL_AMOUNT_CENTS,
                        status = snap.getString("status") ?: "PENDING",
                        createdAt = snap.getLong("createdAt") ?: 0L,
                        expiresAt = snap.getLong("expiresAt") ?: 0L,
                        infinitePayTransactionNsu = snap.getString("infinitePayTransactionNsu") ?: "",
                        invoiceSlug = snap.getString("invoiceSlug") ?: "",
                        receiptUrl = snap.getString("receiptUrl") ?: "",
                        checkoutUrl = snap.getString("checkoutUrl") ?: ""
                    )
                    trySend(order)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Observa o histórico de pagamentos para exibição no painel administrativo.
     */
    fun observeAllPayments(): Flow<List<PaymentRecord>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = db.collection("payments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing payments: ${error.message}")
                    return@addSnapshotListener
                }

                if (snap != null) {
                    val list = snap.documents.map { doc ->
                        PaymentRecord(
                            uid = doc.getString("uid") ?: "",
                            userName = doc.getString("userName") ?: "",
                            orderNsu = doc.getString("orderNsu") ?: "",
                            transactionNsu = doc.getString("transactionNsu") ?: doc.id,
                            invoiceSlug = doc.getString("invoiceSlug") ?: "",
                            amount = doc.getLong("amount") ?: RENEWAL_AMOUNT_CENTS,
                            paidAmount = doc.getLong("paidAmount") ?: (doc.getLong("amount") ?: RENEWAL_AMOUNT_CENTS),
                            captureMethod = doc.getString("captureMethod") ?: "pix",
                            receiptUrl = doc.getString("receiptUrl") ?: "",
                            paidAt = doc.getLong("paidAt") ?: 0L,
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            status = doc.getString("status") ?: "PAID"
                        )
                    }
                    trySend(list)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Simulação / Teste do Webhook da InfinitePay (para uso exclusivo em testes / homologação e diagnóstico).
     * Aplica exatamente as mesmas regras de validação e idempotência do backend:
     * - Verifica se o pedido já está PAID (idempotência);
     * - Se já estiver PAID, rejeita novo incremento de dias;
     * - Calcula +30 dias preservando os dias restantes se ainda ativo;
     * - Atualiza usuário para ACTIVE e registra documento em `payments`.
     */
    suspend fun simulateWebhookProcessing(
        orderNsu: String,
        transactionNsu: String = "TX_${System.currentTimeMillis()}",
        captureMethod: String = "pix",
        paidAmountCents: Long = 1000L
    ): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore não inicializado"))

        return try {
            val paymentTimestamp = System.currentTimeMillis()

            db.runTransaction { tx ->
                val orderRef = db.collection("payment_orders").document(orderNsu)
                val orderSnap = tx.get(orderRef)

                if (!orderSnap.exists()) {
                    throw IllegalStateException("Pedido $orderNsu não encontrado no sistema.")
                }

                val currentStatus = orderSnap.getString("status") ?: "PENDING"
                if (currentStatus == "PAID") {
                    // IDEMPOTÊNCIA: não conceder dias duplicados!
                    throw IllegalStateException("PAGAMENTO DUPLICADO: Este pedido já foi processado anteriormente.")
                }

                val uid = orderSnap.getString("uid")
                if (uid.isNullOrBlank()) {
                    throw IllegalStateException("Pedido não possui UID associado.")
                }

                val userRef = db.collection("users").document(uid)
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) {
                    throw IllegalStateException("Usuário $uid não encontrado.")
                }

                // Cálculo dos 30 dias com preservação de dias restantes
                val currentExpiresAt = userSnap.getLong("subscriptionExpiresAt")
                    ?: userSnap.getLong("expirationDate")
                    ?: 0L

                val newExpiresAt = if (currentExpiresAt <= paymentTimestamp) {
                    // CASO A: Vencida -> agora + 30 dias
                    paymentTimestamp + THIRTY_DAYS_MS
                } else {
                    // CASO B: Ativa -> expiração anterior + 30 dias (não perde dias restantes!)
                    currentExpiresAt + THIRTY_DAYS_MS
                }

                val userName = userSnap.getString("name") ?: ""

                // 1. Atualiza Usuário
                tx.update(
                    userRef,
                    mapOf(
                        "subscriptionStatus" to "ACTIVE",
                        "subscriptionExpiresAt" to newExpiresAt,
                        "expirationDate" to newExpiresAt,
                        "lastPaymentAt" to paymentTimestamp,
                        "lastPaymentDate" to paymentTimestamp,
                        "lastPaymentId" to transactionNsu,
                        "paymentStatus" to "ACTIVE"
                    )
                )

                // 2. Atualiza payment_order
                tx.update(
                    orderRef,
                    mapOf(
                        "status" to "PAID",
                        "infinitePayTransactionNsu" to transactionNsu,
                        "invoiceSlug" to "INV_$orderNsu",
                        "receiptUrl" to "https://infinitepay.io/comprovante/$transactionNsu"
                    )
                )

                // 3. Registra documento em payments
                val paymentDocRef = db.collection("payments").document(transactionNsu)
                val paymentData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "userName" to userName,
                    "orderNsu" to orderNsu,
                    "transactionNsu" to transactionNsu,
                    "invoiceSlug" to "INV_$orderNsu",
                    "amount" to RENEWAL_AMOUNT_CENTS,
                    "paidAmount" to paidAmountCents,
                    "captureMethod" to captureMethod,
                    "receiptUrl" to "https://infinitepay.io/comprovante/$transactionNsu",
                    "paidAt" to paymentTimestamp,
                    "createdAt" to paymentTimestamp,
                    "status" to "PAID"
                )
                tx.set(paymentDocRef, paymentData)
            }.await()

            Result.success("Pagamento aprovado com sucesso! Assinatura estendida em +30 dias.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro no processamento do webhook: ${e.message}", e)
            Result.failure(e)
        }
    }
}
