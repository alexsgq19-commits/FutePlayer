package com.example.data.models

import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class User(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("cpf") @set:PropertyName("cpf") var cpf: String = "",
    @get:PropertyName("phone") @set:PropertyName("phone") var phone: String = "",
    @get:PropertyName("password") @set:PropertyName("password") var password: String = "",
    @get:PropertyName("role") @set:PropertyName("role") var role: String = "USER",
    @get:PropertyName("isActive") @set:PropertyName("isActive") var isActive: Boolean = true,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("isOnline") @set:PropertyName("isOnline") var isOnline: Boolean = false,
    @get:PropertyName("lastSeen") @set:PropertyName("lastSeen") var lastSeen: Long = 0L,
    @get:PropertyName("deviceId") @set:PropertyName("deviceId") var deviceId: String = "",
    @get:PropertyName("sessionToken") @set:PropertyName("sessionToken") var sessionToken: String = "",
    @get:PropertyName("isBillingEnabled") @set:PropertyName("isBillingEnabled") var isBillingEnabled: Boolean = true,
    @get:PropertyName("expirationDate") @set:PropertyName("expirationDate") var expirationDate: Long = 0L,
    @get:PropertyName("monthlyFee") @set:PropertyName("monthlyFee") var monthlyFee: Double = 10.0,
    @get:PropertyName("lastPaymentDate") @set:PropertyName("lastPaymentDate") var lastPaymentDate: Long = 0L,
    @get:PropertyName("paymentStatus") @set:PropertyName("paymentStatus") var paymentStatus: String = "ACTIVE",
    @get:PropertyName("infinitePayTransactionId") @set:PropertyName("infinitePayTransactionId") var infinitePayTransactionId: String = "",
    // Campos de Assinatura InfinitePay + Firestore
    @get:PropertyName("isBillingExempt") @set:PropertyName("isBillingExempt") var isBillingExempt: Boolean = false,
    @get:PropertyName("subscriptionStatus") @set:PropertyName("subscriptionStatus") var subscriptionStatus: String = "NONE",
    @get:PropertyName("subscriptionExpiresAt") @set:PropertyName("subscriptionExpiresAt") var subscriptionExpiresAt: Long = 0L,
    @get:PropertyName("lastPaymentAt") @set:PropertyName("lastPaymentAt") var lastPaymentAt: Long = 0L,
    @get:PropertyName("lastPaymentId") @set:PropertyName("lastPaymentId") var lastPaymentId: String = ""
) {
    // Construtor sem argumentos para o Firebase Firestore
    constructor() : this(
        "", "", "", "", "", "USER", true, System.currentTimeMillis(), false, 0L, "", "",
        true, 0L, 10.0, 0L, "ACTIVE", "",
        false, "NONE", 0L, 0L, ""
    )

    /**
     * Retorna a data de vencimento efetiva da assinatura.
     * Prioriza subscriptionExpiresAt; caso seja 0 (doc antigo), recorre a expirationDate ou createdAt + 30 dias.
     */
    fun getEffectiveSubscriptionExpiry(): Long {
        if (subscriptionExpiresAt > 0L) return subscriptionExpiresAt
        if (expirationDate > 0L) return expirationDate
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
        return createdAt + thirtyDaysMs
    }

    /**
     * Retorna a data de vencimento efetiva para compatibilidade com código legado.
     */
    fun getEffectiveExpirationDate(): Long = getEffectiveSubscriptionExpiry()

    /**
     * REGRA CENTRAL DE ACESSO
     * SE a conta estiver inativa: BLOQUEAR
     * SE role == ADMIN: PERMITIR
     * SE isBillingExempt == true: PERMITIR
     * SE subscriptionStatus == ACTIVE E subscriptionExpiresAt > horário atual: PERMITIR
     * CASO CONTRÁRIO: BLOQUEAR
     */
    fun canAccessPremiumContent(now: Long = System.currentTimeMillis()): Boolean {
        // SE a conta estiver inativa: BLOQUEAR
        if (!isActive) return false

        // SE role == ADMIN: PERMITIR
        if (role == "ADMIN") return true

        // SE isBillingExempt == true: PERMITIR
        if (isBillingExempt) return true

        val effectiveExpiry = getEffectiveSubscriptionExpiry()
        val effectiveStatus = if (subscriptionStatus.isNotBlank() && subscriptionStatus != "NONE") {
            subscriptionStatus
        } else {
            if (effectiveExpiry > now) "ACTIVE" else "EXPIRED"
        }

        // SE subscriptionStatus == ACTIVE E subscriptionExpiresAt > horário atual: PERMITIR
        if (effectiveStatus == "ACTIVE" && effectiveExpiry > now) {
            return true
        }

        // CASO CONTRÁRIO: BLOQUEAR
        return false
    }

    /**
     * Alias da regra central de acesso
     */
    fun hasValidSubscription(now: Long = System.currentTimeMillis()): Boolean = canAccessPremiumContent(now)

    /**
     * Verifica se a assinatura do usuário está vencida.
     * ADMIN e usuários isentos nunca expiram.
     */
    fun isSubscriptionExpired(now: Long = System.currentTimeMillis()): Boolean {
        if (role == "ADMIN" || isBillingExempt || !isActive) return false
        return !canAccessPremiumContent(now)
    }

    /**
     * Verifica se o pagamento do usuário está pendente / vencido (compatibilidade legada).
     */
    fun isPaymentDue(now: Long = System.currentTimeMillis()): Boolean {
        if (role == "ADMIN" || isBillingExempt || !isBillingEnabled || !isActive) return false
        return !canAccessPremiumContent(now)
    }

    fun getFormattedExpirationDate(): String {
        val date = Date(getEffectiveSubscriptionExpiry())
        val format = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return format.format(date)
    }

    fun getFormattedCreatedAt(): String {
        val date = Date(createdAt)
        val format = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return format.format(date)
    }

    fun getFormattedLastPaymentDate(): String {
        if (lastPaymentDate <= 0L) return "Nenhum pagamento registrado"
        val date = Date(lastPaymentDate)
        val format = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        return format.format(date)
    }

    /**
     * Retorna se o usuário está de fato online neste momento.
     * Considera online se a flag isOnline for verdadeira E se a última atividade (heartbeat)
     * tiver ocorrido nos últimos 2 minutos (120.000 ms).
     */
    fun isCurrentlyOnline(now: Long = System.currentTimeMillis(), thresholdMs: Long = 60_000L): Boolean {
        if (!isOnline) return false
        if (lastSeen <= 0L) return false
        return (now - lastSeen) <= thresholdMs
    }

    /**
     * Retorna texto amigável formatado sobre o status online ou última vez visto.
     */
    fun getFormattedLastSeen(now: Long = System.currentTimeMillis()): String {
        if (isCurrentlyOnline(now)) {
            return "Online agora"
        }
        if (lastSeen <= 0L) {
            return "Nunca acessou"
        }
        val diffMs = now - lastSeen
        if (diffMs < 0L) return "Recentemente"

        val diffMinutes = diffMs / (60 * 1000)
        val diffHours = diffMs / (60 * 60 * 1000)
        val diffDays = diffMs / (24 * 60 * 60 * 1000)

        val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        val dateFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        val timeStr = timeFormat.format(Date(lastSeen))

        return when {
            diffMinutes < 1 -> "Visto há poucos segundos"
            diffMinutes < 60 -> "Visto há $diffMinutes min"
            diffHours < 24 -> "Visto hoje às $timeStr"
            diffDays == 1L -> "Visto ontem às $timeStr"
            diffDays < 7 -> "Visto há $diffDays dias ($timeStr)"
            else -> "Visto em ${dateFormat.format(Date(lastSeen))}"
        }
    }

    /**
     * Retorna data e hora exata da última atividade para exibição detalhada.
     */
    fun getFullFormattedLastSeen(): String {
        if (lastSeen <= 0L) return "Nenhum registro de acesso"
        val fullFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss", Locale("pt", "BR"))
        return fullFormat.format(Date(lastSeen))
    }
}
