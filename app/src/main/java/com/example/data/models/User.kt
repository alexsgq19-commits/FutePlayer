package com.example.data.models

import com.google.firebase.firestore.PropertyName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class User(
    @get:PropertyName("uid") @set:PropertyName("uid") var uid: String = "",
    @get:PropertyName("name") @set:PropertyName("name") var name: String = "",
    @get:PropertyName("cpf") @set:PropertyName("cpf") var cpf: String = "",
    @get:PropertyName("password") @set:PropertyName("password") var password: String = "",
    @get:PropertyName("role") @set:PropertyName("role") var role: String = "USER",
    @get:PropertyName("isActive") @set:PropertyName("isActive") var isActive: Boolean = true,
    @get:PropertyName("createdAt") @set:PropertyName("createdAt") var createdAt: Long = System.currentTimeMillis(),
    @get:PropertyName("isOnline") @set:PropertyName("isOnline") var isOnline: Boolean = false,
    @get:PropertyName("lastSeen") @set:PropertyName("lastSeen") var lastSeen: Long = 0L
) {
    // Construtor sem argumentos para o Firebase Firestore
    constructor() : this("", "", "", "", "USER", true, System.currentTimeMillis(), false, 0L)

    /**
     * Retorna se o usuário está de fato online neste momento.
     * Considera online se a flag isOnline for verdadeira E se a última atividade (heartbeat)
     * tiver ocorrido nos últimos 2 minutos (120.000 ms).
     */
    fun isCurrentlyOnline(thresholdMs: Long = 120_000L): Boolean {
        if (!isOnline) return false
        if (lastSeen <= 0L) return false
        val now = System.currentTimeMillis()
        return (now - lastSeen) <= thresholdMs
    }

    /**
     * Retorna texto amigável formatado sobre o status online ou última vez visto.
     */
    fun getFormattedLastSeen(): String {
        if (isCurrentlyOnline()) {
            return "Online agora"
        }
        if (lastSeen <= 0L) {
            return "Nunca acessou"
        }
        val now = System.currentTimeMillis()
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
