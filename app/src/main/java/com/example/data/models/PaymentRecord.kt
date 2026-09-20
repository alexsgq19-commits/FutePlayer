package com.example.data.models

import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PaymentRecord(
    @get:PropertyName("id") @set:PropertyName("id") var id: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("userName") @set:PropertyName("userName") var userName: String = "",
    @get:PropertyName("orderNsu") @set:PropertyName("orderNsu") var orderNsu: String = "",
    @get:PropertyName("transactionNsu") @set:PropertyName("transactionNsu") var transactionNsu: String = "",
    @get:PropertyName("invoiceSlug") @set:PropertyName("invoiceSlug") var invoiceSlug: String = "",
    @get:PropertyName("amount") @set:PropertyName("amount") var amount: Long = 1000L,
    @get:PropertyName("paidAmount") @set:PropertyName("paidAmount") var paidAmount: Long = 1000L,
    @get:PropertyName("captureMethod") @set:PropertyName("captureMethod") var captureMethod: String = "",
    @get:PropertyName("receiptUrl") @set:PropertyName("receiptUrl") var receiptUrl: String = "",
    @get:PropertyName("paidAt") @set:PropertyName("paidAt") var paidAt: Long = 0L,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "PAID"
) {
    fun getDocId(): String = id.ifBlank { transactionNsu.ifBlank { orderNsu } }

    fun getFormattedPaidAt(): String {
        val target = if (paidAt > 0L) paidAt else createdAt
        if (target <= 0L) return "-"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        return sdf.format(Date(target))
    }

    fun getFormattedAmount(): String {
        val reais = (if (paidAmount > 0L) paidAmount else amount) / 100.0
        return String.format(Locale("pt", "BR"), "R$ %.2f", reais)
    }

    fun getFormattedCaptureMethod(): String {
        val method = captureMethod.trim().uppercase()
        return when {
            method.isEmpty() || method == "PIX" || method == "MANUAL_ADMIN" || method == "MANUAL" -> "PIX"
            method == "CREDIT_CARD" || method == "CARD" -> "CARTÃO"
            else -> method
        }
    }
}
