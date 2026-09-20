package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.models.PaymentOrder
import com.example.data.models.PaymentRecord
import com.example.data.models.PaymentRequestItem
import com.example.data.models.PaymentSetting
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
     * Prepara o pedido de pagamento com o link configurado da InfinitePay.
     * Não depende de worker externo, utilizando o link cadastrado no Firestore ou padrão.
     */
    suspend fun createCheckout(user: User): Result<PaymentOrder> = withContext(Dispatchers.IO) {
        val uid = user.uid
        if (uid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Usuário inválido ou não autenticado."))
        }

        val orderNsu = generateOrderNsu(uid)
        val now = System.currentTimeMillis()
        val expiresAt = now + (24L * 60L * 60L * 1000L) // Link expira em 24h

        // Obtém o link cadastrado no Firestore ou fallback padrão
        var effectiveUrl = "https://checkout.infinitepay.io/pay/$DEFAULT_HANDLE"
        val db = firestore
        if (db != null) {
            try {
                val doc = db.collection("settings").document("payment").get().await()
                if (doc.exists()) {
                    val url = doc.getString("paymentUrl")?.trim().orEmpty()
                    if (url.isNotBlank()) {
                        effectiveUrl = url
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao obter link de settings/payment: ${e.message}")
            }
        }

        val newOrder = PaymentOrder(
            orderNsu = orderNsu,
            uid = uid,
            amount = RENEWAL_AMOUNT_CENTS,
            status = "PENDING",
            createdAt = now,
            expiresAt = expiresAt,
            checkoutUrl = effectiveUrl
        )

        // Registra o pedido no Firestore com status PENDING
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
                    "checkoutUrl" to effectiveUrl
                )
                db.collection("payment_orders").document(orderNsu).set(orderData).await()
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao registrar payment_orders: ${e.message}")
            }
        }

        Result.success(newOrder)
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
                            id = doc.id,
                            uid = doc.getString("uid") ?: "",
                            userName = doc.getString("userName") ?: "",
                            orderNsu = doc.getString("orderNsu") ?: "",
                            transactionNsu = doc.getString("transactionNsu") ?: doc.id,
                            invoiceSlug = doc.getString("invoiceSlug") ?: "",
                            amount = doc.getLong("amount") ?: RENEWAL_AMOUNT_CENTS,
                            paidAmount = doc.getLong("paidAmount") ?: (doc.getLong("amount") ?: RENEWAL_AMOUNT_CENTS),
                            captureMethod = doc.getString("captureMethod") ?: "PIX",
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

    // =========================================================================
    // NOVO FLUXO MANUAL DE PAGAMENTO VIA SUPORTE (LINK INFINITEPAY + CONFIRMAÇÃO ADMIN)
    // =========================================================================

    /**
     * Observa as configurações de pagamento em settings/payment no Firestore.
     */
    fun observePaymentSetting(): Flow<PaymentSetting> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(PaymentSetting(paymentUrl = "https://checkout.infinitepay.io/pay/$DEFAULT_HANDLE"))
            awaitClose {}
            return@callbackFlow
        }

        val listener = db.collection("settings").document("payment")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "Erro ao observar settings/payment: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val setting = PaymentSetting(
                        paymentUrl = snap.getString("paymentUrl") ?: "",
                        enabled = snap.getBoolean("enabled") ?: true,
                        amountCents = snap.getLong("amountCents") ?: RENEWAL_AMOUNT_CENTS,
                        durationDays = (snap.getLong("durationDays") ?: 30L).toInt(),
                        updatedAt = snap.getLong("updatedAt") ?: 0L,
                        updatedBy = snap.getString("updatedBy") ?: ""
                    )
                    trySend(setting)
                } else {
                    // Documento ainda não criado no Firestore; retorna padrão com link seguro
                    trySend(PaymentSetting(paymentUrl = "https://checkout.infinitepay.io/pay/$DEFAULT_HANDLE"))
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Salva ou atualiza o link de pagamento em settings/payment (Apenas ADMIN).
     */
    suspend fun updatePaymentSetting(paymentUrl: String, adminUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore indisponível"))
        try {
            val data = hashMapOf<String, Any>(
                "paymentUrl" to paymentUrl.trim(),
                "enabled" to true,
                "amountCents" to RENEWAL_AMOUNT_CENTS,
                "durationDays" to 30,
                "updatedAt" to System.currentTimeMillis(),
                "updatedBy" to adminUid
            )
            db.collection("settings").document("payment").set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar settings/payment: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra uma solicitação de pagamento ("JÁ FIZ O PAGAMENTO") na coleção payment_requests.
     */
    suspend fun createPaymentRequest(user: User): Result<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore indisponível"))
        val uid = user.uid
        if (uid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Usuário inválido."))
        }

        try {
            // Verifica se já existe uma solicitação pendente para este usuário para evitar duplicados
            val userRequests = db.collection("payment_requests")
                .whereEqualTo("uid", uid)
                .get()
                .await()

            val hasPending = userRequests.documents.any { doc ->
                doc.getString("status") == "PENDING"
            }

            if (hasPending) {
                return@withContext Result.success("Sua solicitação de pagamento já está pendente e em análise pelo administrador.")
            }

            val docRef = db.collection("payment_requests").document()
            val now = System.currentTimeMillis()
            val data = hashMapOf<String, Any>(
                "id" to docRef.id,
                "uid" to uid,
                "userName" to user.name,
                "userCpf" to user.cpf,
                "userPhone" to user.phone,
                "amountCents" to RENEWAL_AMOUNT_CENTS,
                "durationDays" to 30,
                "status" to "PENDING",
                "createdAt" to now,
                "updatedAt" to now,
                "confirmedAt" to 0L,
                "confirmedBy" to ""
            )

            docRef.set(data).await()
            Result.success("Solicitação enviada com sucesso! O administrador confirmará seu pagamento em breve.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao criar payment_request: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observa todas as solicitações de pagamento pendentes (exclusivo para painel Admin).
     */
    fun observePendingPaymentRequests(): Flow<List<PaymentRequestItem>> = callbackFlow {
        val db = firestore
        if (db == null) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }

        val listener = db.collection("payment_requests")
            .whereEqualTo("status", "PENDING")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "Erro ao observar payment_requests: ${error.message}")
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val list = snap.documents.mapNotNull { doc ->
                        try {
                            PaymentRequestItem(
                                id = doc.getString("id") ?: doc.id,
                                uid = doc.getString("uid") ?: "",
                                userName = doc.getString("userName") ?: "",
                                userCpf = doc.getString("userCpf") ?: "",
                                userPhone = doc.getString("userPhone") ?: "",
                                amountCents = doc.getLong("amountCents") ?: RENEWAL_AMOUNT_CENTS,
                                durationDays = (doc.getLong("durationDays") ?: 30L).toInt(),
                                status = doc.getString("status") ?: "PENDING",
                                createdAt = doc.getLong("createdAt") ?: 0L,
                                updatedAt = doc.getLong("updatedAt") ?: 0L,
                                confirmedAt = doc.getLong("confirmedAt") ?: 0L,
                                confirmedBy = doc.getString("confirmedBy") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedByDescending { it.createdAt }
                    trySend(list)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * Confirmação manual de pagamento realizada pelo ADMIN com transação atômica e idempotência:
     * 1. Verifica se a solicitação ainda está PENDING (impede liberação duplicada).
     * 2. Calcula +30 dias de acesso (se vencido: agora + 30d; se ativo: expiração + 30d).
     * 3. Atualiza os campos de assinatura do usuário no Firestore (subscriptionStatus = ACTIVE, subscriptionExpiresAt, etc.).
     * 4. Altera o status da solicitação em payment_requests para CONFIRMED.
     * 5. Registra o comprovante na coleção payments.
     */
    suspend fun confirmManualPaymentRequest(
        requestItem: PaymentRequestItem,
        adminUser: User
    ): Result<String> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore indisponível"))
        val now = System.currentTimeMillis()
        val durationDaysMs = requestItem.durationDays.toLong() * 24L * 60L * 60L * 1000L

        try {
            db.runTransaction { tx ->
                val requestRef = db.collection("payment_requests").document(requestItem.id)
                val requestSnap = tx.get(requestRef)

                if (!requestSnap.exists()) {
                    throw IllegalStateException("Solicitação de pagamento não encontrada no banco de dados.")
                }

                val currentReqStatus = requestSnap.getString("status") ?: "PENDING"
                if (currentReqStatus != "PENDING") {
                    throw IllegalStateException("Esta solicitação já foi processada anteriormente (Status: $currentReqStatus).")
                }

                val uid = requestItem.uid
                if (uid.isBlank()) {
                    throw IllegalStateException("Solicitação não possui UID de usuário.")
                }

                val userRef = db.collection("users").document(uid)
                val userSnap = tx.get(userRef)

                if (!userSnap.exists()) {
                    throw IllegalStateException("Usuário $uid não encontrado.")
                }

                // Cálculo dos 30 dias (se ativo: preserva restantes; se vencido: conta a partir de agora)
                val currentExpiresAt = userSnap.getLong("subscriptionExpiresAt")
                    ?: userSnap.getLong("expirationDate")
                    ?: 0L

                val newExpiresAt = if (currentExpiresAt <= now) {
                    now + durationDaysMs
                } else {
                    currentExpiresAt + durationDaysMs
                }

                val userName = userSnap.getString("name") ?: requestItem.userName
                val paymentRecordId = "MANUAL_${requestItem.id.take(12)}_${now}"

                // 1. Atualiza Usuário
                tx.update(
                    userRef,
                    mapOf(
                        "subscriptionStatus" to "ACTIVE",
                        "subscriptionExpiresAt" to newExpiresAt,
                        "expirationDate" to newExpiresAt,
                        "lastPaymentAt" to now,
                        "lastPaymentDate" to now,
                        "lastPaymentId" to paymentRecordId,
                        "paymentStatus" to "ACTIVE"
                    )
                )

                // 2. Atualiza payment_requests
                tx.update(
                    requestRef,
                    mapOf(
                        "status" to "CONFIRMED",
                        "confirmedAt" to now,
                        "confirmedBy" to (adminUser.name.ifBlank { adminUser.uid }),
                        "updatedAt" to now
                    )
                )

                // 3. Registra documento em payments
                val paymentDocRef = db.collection("payments").document(paymentRecordId)
                val paymentData = hashMapOf<String, Any>(
                    "uid" to uid,
                    "userName" to userName,
                    "orderNsu" to requestItem.id,
                    "transactionNsu" to paymentRecordId,
                    "invoiceSlug" to "MANUAL_INV_${requestItem.id}",
                    "amount" to requestItem.amountCents,
                    "paidAmount" to requestItem.amountCents,
                    "captureMethod" to "PIX",
                    "receiptUrl" to "",
                    "paidAt" to now,
                    "createdAt" to now,
                    "status" to "PAID"
                )
                tx.set(paymentDocRef, paymentData)
            }.await()

            Result.success("Pagamento confirmado com sucesso! +${requestItem.durationDays} dias liberados para o usuário.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao confirmar pagamento manual: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Atualiza os dados de um registro de pagamento no Firestore.
     */
    suspend fun updatePaymentRecord(
        paymentId: String,
        userName: String,
        amountCents: Long,
        captureMethod: String,
        status: String,
        receiptUrl: String
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore não inicializado"))
        val targetId = paymentId.trim()
        if (targetId.isBlank()) return Result.failure(Exception("ID de pagamento inválido"))
        return try {
            val upperStatus = status.trim().uppercase()
            val updates = mapOf(
                "userName" to userName.trim(),
                "amount" to amountCents,
                "paidAmount" to amountCents,
                "captureMethod" to captureMethod.trim().uppercase(),
                "status" to upperStatus,
                "receiptUrl" to receiptUrl.trim(),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("payments").document(targetId).update(updates).await()

            if (upperStatus == "PAID") {
                grantUserIfPaid(db, targetId)
            } else {
                blockUserIfNonPaid(db, targetId, upperStatus)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atualizar pagamento $targetId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Altera apenas o status de um registro de pagamento (ex: PAID, PENDING, CANCELLED, EXPIRED, REFUNDED).
     */
    suspend fun updatePaymentStatus(paymentId: String, newStatus: String): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore não inicializado"))
        val targetId = paymentId.trim()
        if (targetId.isBlank()) return Result.failure(Exception("ID de pagamento inválido"))
        return try {
            val upperStatus = newStatus.trim().uppercase()
            val updates = mapOf(
                "status" to upperStatus,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("payments").document(targetId).update(updates).await()

            if (upperStatus == "PAID") {
                grantUserIfPaid(db, targetId)
            } else {
                blockUserIfNonPaid(db, targetId, upperStatus)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao alterar status do pagamento $targetId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Exclui um registro de pagamento do Firestore.
     */
    suspend fun deletePaymentRecord(paymentId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(Exception("Firestore não inicializado"))
        val targetId = paymentId.trim()
        if (targetId.isBlank()) return Result.failure(Exception("ID de pagamento inválido"))
        return try {
            blockUserOnDelete(db, targetId)
            db.collection("payments").document(targetId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao excluir pagamento $targetId: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun blockUserIfNonPaid(db: com.google.firebase.firestore.FirebaseFirestore, paymentId: String, newStatus: String) {
        try {
            val doc = db.collection("payments").document(paymentId).get().await()
            val uid = doc.getString("uid") ?: return
            if (uid.isNotBlank() && newStatus != "PAID") {
                val userRef = db.collection("users").document(uid)
                userRef.update(
                    mapOf(
                        "subscriptionStatus" to "EXPIRED",
                        "subscriptionExpiresAt" to 0L,
                        "expirationDate" to 0L,
                        "paymentStatus" to newStatus
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao bloquear usuário para pagamento $paymentId: ${e.message}")
        }
    }

    private suspend fun blockUserOnDelete(db: com.google.firebase.firestore.FirebaseFirestore, paymentId: String) {
        try {
            val doc = db.collection("payments").document(paymentId).get().await()
            val uid = doc.getString("uid") ?: return
            if (uid.isNotBlank()) {
                val userRef = db.collection("users").document(uid)
                userRef.update(
                    mapOf(
                        "subscriptionStatus" to "EXPIRED",
                        "subscriptionExpiresAt" to 0L,
                        "expirationDate" to 0L,
                        "paymentStatus" to "DELETED"
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao bloquear usuário por exclusão de pagamento $paymentId: ${e.message}")
        }
    }

    private suspend fun grantUserIfPaid(db: com.google.firebase.firestore.FirebaseFirestore, paymentId: String) {
        try {
            val doc = db.collection("payments").document(paymentId).get().await()
            val uid = doc.getString("uid") ?: return
            val durationDays = 30L
            val durationDaysMs = durationDays * 24L * 60L * 60L * 1000L
            val now = System.currentTimeMillis()

            if (uid.isNotBlank()) {
                val userRef = db.collection("users").document(uid)
                val userSnap = userRef.get().await()
                val currentExpiresAt = userSnap.getLong("subscriptionExpiresAt")
                    ?: userSnap.getLong("expirationDate")
                    ?: 0L

                val newExpiresAt = if (currentExpiresAt <= now) {
                    now + durationDaysMs
                } else {
                    currentExpiresAt + durationDaysMs
                }

                userRef.update(
                    mapOf(
                        "subscriptionStatus" to "ACTIVE",
                        "subscriptionExpiresAt" to newExpiresAt,
                        "expirationDate" to newExpiresAt,
                        "lastPaymentAt" to now,
                        "lastPaymentDate" to now,
                        "lastPaymentId" to paymentId,
                        "paymentStatus" to "ACTIVE"
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar usuário para pagamento $paymentId: ${e.message}")
        }
    }
}
