package com.example.data.models

import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PaymentSetting(
    @get:PropertyName("paymentUrl") @set:PropertyName("paymentUrl") var paymentUrl: String = "",
    @get:PropertyName("enabled") @set:PropertyName("enabled") var enabled: Boolean = true,
    @get:PropertyName("amountCents") @set:PropertyName("amountCents") var amountCents: Long = 1000L,
    @get:PropertyName("durationDays") @set:PropertyName("durationDays") var durationDays: Int = 30,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("updatedBy") @set:PropertyName("updatedBy") var updatedBy: String = ""
) {
    fun getFormattedAmount(): String {
        val safeCents = if (amountCents > 0L) amountCents else 1000L
        val reais = safeCents / 100.0
        return String.format(Locale("pt", "BR"), "R$ %.2f", reais)
    }

    fun getAmountInReaisString(): String {
        val safeCents = if (amountCents > 0L) amountCents else 1000L
        val reais = safeCents / 100.0
        return String.format(Locale("pt", "BR"), "%.2f", reais)
    }
}

data class PaymentRequestItem(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("userName") @set:PropertyName("userName") var userName: String = "",
    @get:PropertyName("userCpf") @set:PropertyName("userCpf") var userCpf: String = "",
    @get:PropertyName("userPhone") @set:PropertyName("userPhone") var userPhone: String = "",
    @get:PropertyName("amountCents") @set:PropertyName("amountCents") var amountCents: Long = 1000L,
    @get:PropertyName("durationDays") @set:PropertyName("durationDays") var durationDays: Int = 30,
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "PENDING", // PENDING, CONFIRMED, CANCELLED
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = 0L,
    @get:PropertyName("confirmedAt") @set:PropertyName("confirmedAt") var confirmedAt: Long = 0L,
    @get:PropertyName("confirmedBy") @set:PropertyName("confirmedBy") var confirmedBy: String = ""
) {
    fun getFormattedCreatedAt(): String {
        if (createdAt <= 0L) return "-"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        return sdf.format(Date(createdAt))
    }

    fun getFormattedAmount(): String {
        val reais = amountCents / 100.0
        return String.format(Locale("pt", "BR"), "R$ %.2f", reais)
    }
}
