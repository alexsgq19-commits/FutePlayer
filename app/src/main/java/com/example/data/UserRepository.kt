package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class UserRepository(private val context: Context) {

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_repo_cache", Context.MODE_PRIVATE)
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseApp.getInstance()
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val _localUsersFlow = MutableStateFlow<List<User>>(emptyList())

    private fun getLocalUsers(): List<User> {
        val jsonStr = sharedPrefs.getString("users_list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<User>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    User(
                        uid = obj.optString("uid"),
                        name = obj.optString("name"),
                        cpf = obj.optString("cpf"),
                        password = obj.optString("password"),
                        role = obj.optString("role", "USER"),
                        isActive = obj.optBoolean("isActive", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isOnline = obj.optBoolean("isOnline", false),
                        lastSeen = obj.optLong("lastSeen", 0L)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveLocalUsers(users: List<User>) {
        try {
            val arr = JSONArray()
            users.forEach { user ->
                val obj = JSONObject().apply {
                    put("uid", user.uid)
                    put("name", user.name)
                    put("cpf", user.cpf)
                    put("password", user.password)
                    put("role", user.role)
                    put("isActive", user.isActive)
                    put("createdAt", user.createdAt)
                    put("isOnline", user.isOnline)
                    put("lastSeen", user.lastSeen)
                }
                arr.put(obj)
            }
            sharedPrefs.edit().putString("users_list", arr.toString()).apply()
            _localUsersFlow.value = users
        } catch (_: Exception) {}
    }

    private fun docToUser(doc: DocumentSnapshot): User {
        val rawActive = doc.getBoolean("isActive") ?: doc.getBoolean("active") ?: true
        val rawOnline = doc.getBoolean("isOnline") ?: doc.getBoolean("online") ?: false
        val rawLastSeen = doc.getLong("lastSeen") ?: 0L
        return User(
            uid = doc.getString("uid") ?: doc.id,
            name = doc.getString("name") ?: "",
            cpf = doc.getString("cpf") ?: "",
            password = doc.getString("password") ?: "",
            role = doc.getString("role") ?: "USER",
            isActive = rawActive,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            isOnline = rawOnline,
            lastSeen = rawLastSeen
        )
    }

    suspend fun ensureAdminExists() {
        val adminUser = User(
            uid = "admin_06462555505",
            name = "Administrador Master",
            cpf = "06462555505",
            password = "123456",
            role = "ADMIN",
            isActive = true,
            createdAt = 1700000000000L
        )

        // Ensure in local cache
        val currentLocal = getLocalUsers().toMutableList()
        if (currentLocal.none { it.cpf == adminUser.cpf || it.uid == adminUser.uid }) {
            currentLocal.add(0, adminUser)
            saveLocalUsers(currentLocal)
        }

        try {
            val db = firestore ?: return
            val adminDoc = db.collection("users").document(adminUser.uid).get().await()
            if (!adminDoc.exists()) {
                val data = hashMapOf<String, Any>(
                    "uid" to adminUser.uid,
                    "name" to adminUser.name,
                    "cpf" to adminUser.cpf,
                    "password" to adminUser.password,
                    "role" to adminUser.role,
                    "isActive" to true,
                    "active" to true,
                    "createdAt" to adminUser.createdAt
                )
                db.collection("users").document(adminUser.uid).set(data).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun login(cpf: String, pass: String): Result<User?> {
        val cleanCpf = cpf.trim()
        val cleanPass = pass.trim()

        // 1. Try Firestore
        try {
            val db = firestore
            if (db != null) {
                val querySnapshot = db.collection("users")
                    .whereEqualTo("cpf", cleanCpf)
                    .whereEqualTo("password", cleanPass)
                    .get()
                    .await()

                if (!querySnapshot.isEmpty) {
                    val user = docToUser(querySnapshot.documents.first())
                    // Update local cache
                    val list = getLocalUsers().toMutableList()
                    val idx = list.indexOfFirst { it.uid == user.uid }
                    if (idx >= 0) list[idx] = user else list.add(user)
                    saveLocalUsers(list)
                    return Result.success(user)
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback to local cache
        val localList = getLocalUsers()
        val localMatch = localList.find { it.cpf == cleanCpf && it.password == cleanPass }
        if (localMatch != null) {
            return Result.success(localMatch)
        }

        // Special hardcoded master admin fallback if list empty
        if (cleanCpf == "06462555505" && cleanPass == "123456") {
            val admin = User(
                uid = "admin_06462555505",
                name = "Administrador Master",
                cpf = "06462555505",
                password = "123456",
                role = "ADMIN",
                isActive = true
            )
            val list = localList.toMutableList()
            if (list.none { it.uid == admin.uid }) {
                list.add(admin)
                saveLocalUsers(list)
            }
            return Result.success(admin)
        }

        return Result.success(null)
    }

    suspend fun saveUser(user: User): Result<Unit> {
        val finalUser = if (user.uid.isBlank()) {
            user.copy(uid = "user_${System.currentTimeMillis()}")
        } else {
            user
        }

        // 1. Save to local cache first for instant feedback & offline capability
        val current = getLocalUsers().toMutableList()
        val idx = current.indexOfFirst { it.uid == finalUser.uid }
        if (idx >= 0) {
            current[idx] = finalUser
        } else {
            current.add(finalUser)
        }
        saveLocalUsers(current)

        // 2. Sync with Firestore if available
        return try {
            val db = firestore
            if (db != null) {
                val data = hashMapOf<String, Any>(
                    "uid" to finalUser.uid,
                    "name" to finalUser.name,
                    "cpf" to finalUser.cpf,
                    "password" to finalUser.password,
                    "role" to finalUser.role,
                    "isActive" to finalUser.isActive,
                    "active" to finalUser.isActive,
                    "createdAt" to finalUser.createdAt,
                    "isOnline" to finalUser.isOnline,
                    "lastSeen" to finalUser.lastSeen
                )
                db.collection("users").document(finalUser.uid).set(data).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            // Even if firestore fails, local save succeeded
            Result.success(Unit)
        }
    }

    suspend fun updateUserPresence(uid: String, isOnline: Boolean): Result<Unit> {
        if (uid.isBlank()) return Result.success(Unit)
        val timestamp = System.currentTimeMillis()

        // 1. Update local cache
        val current = getLocalUsers().toMutableList()
        val idx = current.indexOfFirst { it.uid == uid }
        if (idx >= 0) {
            val u = current[idx]
            current[idx] = u.copy(isOnline = isOnline, lastSeen = timestamp)
            saveLocalUsers(current)
        }

        // 2. Update Firestore
        return try {
            val db = firestore
            if (db != null) {
                val updates = hashMapOf<String, Any>(
                    "isOnline" to isOnline,
                    "lastSeen" to timestamp
                )
                db.collection("users").document(uid).update(updates).await()
            }
            Result.success(Unit)
        } catch (_: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun getUser(uid: String): Result<User?> {
        // Try Firestore first
        try {
            val db = firestore
            if (db != null) {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val u = docToUser(doc)
                    return Result.success(u)
                }
            }
        } catch (_: Exception) {}

        // Fallback local
        val local = getLocalUsers().find { it.uid == uid }
        return Result.success(local)
    }

    fun getAllUsers(): Flow<List<User>> = callbackFlow {
        // Immediately emit local cache
        val local = getLocalUsers()
        if (local.isNotEmpty()) {
            _localUsersFlow.value = local
            trySend(local)
        } else {
            val fallbackAdmin = User(
                uid = "admin_06462555505",
                name = "Administrador Master",
                cpf = "06462555505",
                password = "123456",
                role = "ADMIN",
                isActive = true
            )
            val initialList = listOf(fallbackAdmin)
            _localUsersFlow.value = initialList
            trySend(initialList)
        }

        val localJob = launch {
            _localUsersFlow.collect { updatedList ->
                if (updatedList.isNotEmpty()) {
                    trySend(updatedList)
                }
            }
        }

        val db = firestore
        if (db == null) {
            awaitClose {
                localJob.cancel()
            }
            return@callbackFlow
        }

        val listener = db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
                val firestoreUsers = snapshot.documents.map { docToUser(it) }
                saveLocalUsers(firestoreUsers)
                trySend(firestoreUsers)
            }
        }

        awaitClose {
            localJob.cancel()
            listener.remove()
        }
    }

    suspend fun deleteUser(uid: String): Result<Unit> {
        // Remove locally
        val current = getLocalUsers().toMutableList()
        current.removeAll { it.uid == uid }
        saveLocalUsers(current)

        return try {
            val db = firestore
            if (db != null) {
                db.collection("users").document(uid).delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}
