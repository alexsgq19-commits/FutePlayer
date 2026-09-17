package com.example.data.models

import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PaymentOrder(
    @get:PropertyName("orderNsu") @set:PropertyName("orderNsu") var orderNsu: String = "",
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("amount") @set:PropertyName("amount") var amount: Long = 1000L, // R$ 10,00 em centavos
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "PENDING", // PENDING, PAID, CANCELLED, EXPIRED
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("expiresAt") @set:PropertyName("expiresAt") var expiresAt: Long = 0L,
    @get:PropertyName("infinitePayTransactionNsu") @set:PropertyName("infinitePayTransactionNsu") var infinitePayTransactionNsu: String = "",
    @get:PropertyName("invoiceSlug") @set:PropertyName("invoiceSlug") var invoiceSlug: String = "",
    @get:PropertyName("receiptUrl") @set:PropertyName("receiptUrl") var receiptUrl: String = "",
    @get:PropertyName("checkoutUrl") @set:PropertyName("checkoutUrl") var checkoutUrl: String = ""
) {
    fun getFormattedCreatedAt(): String {
        if (createdAt <= 0L) return "-"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        return sdf.format(Date(createdAt))
    }

    fun getFormattedAmount(): String {
        val reais = amount / 100.0
        return String.format(Locale("pt", "BR"), "R$ %.2f", reais)
    }
}
