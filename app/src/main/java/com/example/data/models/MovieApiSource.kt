package com.example.data.models

import java.util.UUID

data class MovieApiSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apiUrl: String,
    val apiType: String = "JSON / REST", // Options: "JSON / REST", "M3U Playlist", "Xtream Codes"
    val isActive: Boolean = true,
    val itemCount: Int = 0,
    val lastSynced: Long = System.currentTimeMillis(),
    val lastError: String? = null
)
