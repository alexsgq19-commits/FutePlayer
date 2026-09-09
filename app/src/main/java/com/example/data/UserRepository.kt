package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.models.User
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class UserRepository(private val context: Context) {

    companion object {
        private const val TAG = "UserRepository"
    }

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_repo_cache", Context.MODE_PRIVATE)
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseApp.getInstance()
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore: ${e.message}")
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
                        phone = obj.optString("phone", ""),
                        password = obj.optString("password"),
                        role = obj.optString("role", "USER"),
                        isActive = obj.optBoolean("isActive", true),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isOnline = obj.optBoolean("isOnline", false),
                        lastSeen = obj.optLong("lastSeen", 0L),
                        deviceId = obj.optString("deviceId", ""),
                        sessionToken = obj.optString("sessionToken", "")
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
                    put("phone", user.phone)
                    put("password", user.password)
                    put("role", user.role)
                    put("isActive", user.isActive)
                    put("createdAt", user.createdAt)
                    put("isOnline", user.isOnline)
                    put("lastSeen", user.lastSeen)
                    put("deviceId", user.deviceId)
                    put("sessionToken", user.sessionToken)
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
        val docId = doc.id
        val storedUid = doc.getString("uid")
        val finalUid = if (!storedUid.isNullOrBlank()) storedUid else docId
        val devId = doc.getString("deviceId") ?: doc.getString("currentDeviceId") ?: ""
        val sToken = doc.getString("sessionToken") ?: ""
        return User(
            uid = finalUid,
            name = doc.getString("name") ?: "",
            cpf = doc.getString("cpf") ?: "",
            phone = doc.getString("phone") ?: "",
            password = doc.getString("password") ?: "",
            role = doc.getString("role") ?: "USER",
            isActive = rawActive,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            isOnline = rawOnline,
            lastSeen = rawLastSeen,
            deviceId = devId,
            sessionToken = sToken
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
                db.collection("users").document(adminUser.uid).set(data, SetOptions.merge()).await()
            }
        } catch (_: Exception) {}
    }

    private fun isIdentifierMatch(user: User, identifier: String): Boolean {
        val cleanInput = identifier.trim()
        if (cleanInput.isBlank()) return false
        val inputDigits = cleanInput.filter { it.isDigit() }

        val userCpf = user.cpf.trim()
        val userCpfDigits = userCpf.filter { it.isDigit() }

        val userPhone = user.phone.trim()
        val userPhoneDigits = userPhone.filter { it.isDigit() }

        // 1. CPF match
        if (userCpf.equals(cleanInput, ignoreCase = true)) return true
        if (inputDigits.isNotBlank() && userCpfDigits.isNotBlank() && userCpfDigits == inputDigits) return true

        // 2. Phone match
        if (userPhone.isNotBlank()) {
            if (userPhone.equals(cleanInput, ignoreCase = true)) return true
            if (inputDigits.isNotBlank() && userPhoneDigits.isNotBlank()) {
                if (userPhoneDigits == inputDigits) return true
                if (inputDigits.length >= 8 && userPhoneDigits.length >= 8) {
                    if (inputDigits.endsWith(userPhoneDigits) || userPhoneDigits.endsWith(inputDigits)) return true
                }
            }
        }

        return false
    }

    suspend fun login(
        identifier: String,
        pass: String,
        deviceId: String = "",
        sessionToken: String = ""
    ): Result<User?> {
        val cleanIdentifier = identifier.trim()
        val cleanDigits = cleanIdentifier.filter { it.isDigit() }
        val cleanPass = pass.trim()
        val now = System.currentTimeMillis()

        // 1. Try Firestore
        try {
            val db = firestore
            if (db != null) {
                // Try CPF / Phone matching queries
                var matchedDoc: DocumentSnapshot? = null

                // Direct CPF query
                val cpfSnap = db.collection("users")
                    .whereEqualTo("cpf", cleanIdentifier)
                    .whereEqualTo("password", cleanPass)
                    .get()
                    .await()
                if (!cpfSnap.isEmpty) {
                    matchedDoc = cpfSnap.documents.first()
                }

                // Digits-only CPF query
                if (matchedDoc == null && cleanDigits.isNotBlank() && cleanDigits != cleanIdentifier) {
                    val cpfDigitsSnap = db.collection("users")
                        .whereEqualTo("cpf", cleanDigits)
                        .whereEqualTo("password", cleanPass)
                        .get()
                        .await()
                    if (!cpfDigitsSnap.isEmpty) {
                        matchedDoc = cpfDigitsSnap.documents.first()
                    }
                }

                // Direct Phone query
                if (matchedDoc == null) {
                    val phoneSnap = db.collection("users")
                        .whereEqualTo("phone", cleanIdentifier)
                        .whereEqualTo("password", cleanPass)
                        .get()
                        .await()
                    if (!phoneSnap.isEmpty) {
                        matchedDoc = phoneSnap.documents.first()
                    }
                }

                // Digits-only Phone query
                if (matchedDoc == null && cleanDigits.isNotBlank() && cleanDigits != cleanIdentifier) {
                    val phoneDigitsSnap = db.collection("users")
                        .whereEqualTo("phone", cleanDigits)
                        .whereEqualTo("password", cleanPass)
                        .get()
                        .await()
                    if (!phoneDigitsSnap.isEmpty) {
                        matchedDoc = phoneDigitsSnap.documents.first()
                    }
                }

                // Fallback: Query all users with matching password and verify flexible match
                if (matchedDoc == null) {
                    val passSnap = db.collection("users")
                        .whereEqualTo("password", cleanPass)
                        .get()
                        .await()
                    for (d in passSnap.documents) {
                        val candidate = docToUser(d)
                        if (isIdentifierMatch(candidate, cleanIdentifier)) {
                            matchedDoc = d
                            break
                        }
                    }
                }

                if (matchedDoc != null) {
                    var user = docToUser(matchedDoc)

                    // Se deviceId foi fornecido, registra como o dispositivo ativo único imediatamente
                    if (deviceId.isNotBlank()) {
                        user = user.copy(
                            deviceId = deviceId,
                            sessionToken = sessionToken,
                            isOnline = true,
                            lastSeen = now
                        )
                        val updates = hashMapOf<String, Any>(
                            "deviceId" to deviceId,
                            "currentDeviceId" to deviceId,
                            "sessionToken" to sessionToken,
                            "isOnline" to true,
                            "online" to true,
                            "lastSeen" to now,
                            "lastLoginAt" to now
                        )
                        db.collection("users").document(matchedDoc.id).set(updates, SetOptions.merge()).await()
                        if (matchedDoc.id != user.uid && user.uid.isNotBlank()) {
                            db.collection("users").document(user.uid).set(updates, SetOptions.merge()).await()
                        }
                    }

                    // Update local cache
                    val list = getLocalUsers().toMutableList()
                    val idx = list.indexOfFirst { it.uid == user.uid || (it.cpf.isNotBlank() && it.cpf == user.cpf) }
                    if (idx >= 0) list[idx] = user else list.add(user)
                    saveLocalUsers(list)
                    return Result.success(user)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore login error: ${e.message}", e)
        }

        // 2. Fallback to local cache
        val localList = getLocalUsers()
        val localMatch = localList.find { it.password == cleanPass && isIdentifierMatch(it, cleanIdentifier) }
        if (localMatch != null) {
            val updatedLocal = if (deviceId.isNotBlank()) {
                localMatch.copy(
                    deviceId = deviceId,
                    sessionToken = sessionToken,
                    isOnline = true,
                    lastSeen = now
                )
            } else {
                localMatch
            }
            val list = localList.toMutableList()
            val idx = list.indexOfFirst { it.uid == localMatch.uid }
            if (idx >= 0) list[idx] = updatedLocal else list.add(updatedLocal)
            saveLocalUsers(list)
            return Result.success(updatedLocal)
        }

        // Special hardcoded master admin fallback if list empty
        if ((cleanIdentifier == "06462555505" || cleanDigits == "06462555505") && cleanPass == "123456") {
            val admin = User(
                uid = "admin_06462555505",
                name = "Administrador Master",
                cpf = "06462555505",
                phone = "",
                password = "123456",
                role = "ADMIN",
                isActive = true,
                deviceId = deviceId,
                sessionToken = sessionToken,
                isOnline = true,
                lastSeen = now
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

    suspend fun registerUser(
        name: String,
        phone: String,
        cpf: String,
        pass: String
    ): Result<User> {
        val cleanName = name.trim()
        val cleanPhone = phone.trim()
        val cleanCpf = cpf.trim()
        val cleanPass = pass.trim()

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Nome é obrigatório."))
        }
        if (cleanPhone.isBlank()) {
            return Result.failure(IllegalArgumentException("Número de celular é obrigatório."))
        }
        if (cleanPass.isBlank()) {
            return Result.failure(IllegalArgumentException("Senha é obrigatória."))
        }

        val phoneDigits = cleanPhone.filter { it.isDigit() }
        val cpfDigits = cleanCpf.filter { it.isDigit() }

        // 1. Check local cache uniqueness
        val localList = getLocalUsers()
        val localConflict = localList.find { user ->
            val uPhone = user.phone.trim()
            val uPhoneDigits = uPhone.filter { it.isDigit() }
            val uCpf = user.cpf.trim()
            val uCpfDigits = uCpf.filter { it.isDigit() }

            (phoneDigits.isNotBlank() && uPhoneDigits.isNotBlank() && phoneDigits == uPhoneDigits) ||
            (cpfDigits.isNotBlank() && uCpfDigits.isNotBlank() && cpfDigits == uCpfDigits)
        }
        if (localConflict != null) {
            return Result.failure(IllegalStateException("Já existe uma conta cadastrada com este número de celular ou CPF."))
        }

        // 2. Check Firestore uniqueness
        try {
            val db = firestore
            if (db != null) {
                val phoneQuery = db.collection("users").whereEqualTo("phone", cleanPhone).get().await()
                if (!phoneQuery.isEmpty) {
                    return Result.failure(IllegalStateException("Já existe uma conta cadastrada com este número de celular."))
                }
                if (phoneDigits.isNotBlank() && phoneDigits != cleanPhone) {
                    val phoneDigitsQuery = db.collection("users").whereEqualTo("phone", phoneDigits).get().await()
                    if (!phoneDigitsQuery.isEmpty) {
                        return Result.failure(IllegalStateException("Já existe uma conta cadastrada com este número de celular."))
                    }
                }
                if (cleanCpf.isNotBlank()) {
                    val cpfQuery = db.collection("users").whereEqualTo("cpf", cleanCpf).get().await()
                    if (!cpfQuery.isEmpty) {
                        return Result.failure(IllegalStateException("Já existe uma conta cadastrada com este CPF."))
                    }
                    if (cpfDigits.isNotBlank() && cpfDigits != cleanCpf) {
                        val cpfDigitsQuery = db.collection("users").whereEqualTo("cpf", cpfDigits).get().await()
                        if (!cpfDigitsQuery.isEmpty) {
                            return Result.failure(IllegalStateException("Já existe uma conta cadastrada com este CPF."))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking user existence during registration: ${e.message}", e)
        }

        // Create new inactive user
        val newUser = User(
            uid = "user_${System.currentTimeMillis()}",
            name = cleanName,
            cpf = cleanCpf,
            phone = cleanPhone,
            password = cleanPass,
            role = "USER",
            isActive = false, // Conta inativa até aprovação de um admin
            createdAt = System.currentTimeMillis(),
            isOnline = false,
            lastSeen = 0L,
            deviceId = "",
            sessionToken = ""
        )

        saveUser(newUser)
        return Result.success(newUser)
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
                    "phone" to finalUser.phone,
                    "password" to finalUser.password,
                    "role" to finalUser.role,
                    "isActive" to finalUser.isActive,
                    "active" to finalUser.isActive,
                    "createdAt" to finalUser.createdAt,
                    "isOnline" to finalUser.isOnline,
                    "lastSeen" to finalUser.lastSeen,
                    "deviceId" to finalUser.deviceId,
                    "currentDeviceId" to finalUser.deviceId,
                    "sessionToken" to finalUser.sessionToken
                )
                db.collection("users").document(finalUser.uid).set(data, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user: ${e.message}", e)
            Result.success(Unit)
        }
    }

    /**
     * Atualiza o heartbeat do usuário se e somente se o dispositivo local for o dispositivo
     * atualmente ativo no Firestore. Se outro dispositivo conectou, retorna false sem
     * sobrescrever o deviceId remoto.
     */
    suspend fun updateUserHeartbeat(uid: String, myDeviceId: String): Boolean {
        if (uid.isBlank()) return false
        val timestamp = System.currentTimeMillis()
        val db = firestore ?: return true
        return try {
            val doc = db.collection("users").document(uid).get(Source.SERVER).await()
            if (!doc.exists()) return false

            val activeDeviceId = doc.getString("deviceId") ?: doc.getString("currentDeviceId") ?: ""
            if (activeDeviceId.isNotBlank() && activeDeviceId != myDeviceId) {
                Log.w(TAG, "Heartbeat conflict for $uid: active=$activeDeviceId, current=$myDeviceId")
                return false
            }

            val isActive = doc.getBoolean("isActive") ?: doc.getBoolean("active") ?: true
            if (!isActive) {
                return false
            }

            val updates = hashMapOf<String, Any>(
                "isOnline" to true,
                "online" to true,
                "lastSeen" to timestamp
            )
            db.collection("users").document(uid).set(updates, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            // Em falhas de rede transitórias não desconecta imediatamente
            true
        }
    }

    suspend fun updateUserPresence(
        uid: String,
        isOnline: Boolean,
        deviceId: String? = null,
        sessionToken: String? = null
    ): Result<Unit> {
        if (uid.isBlank()) return Result.success(Unit)
        val timestamp = System.currentTimeMillis()

        // 1. Update local cache
        val current = getLocalUsers().toMutableList()
        val idx = current.indexOfFirst { it.uid == uid }
        if (idx >= 0) {
            val u = current[idx]
            current[idx] = u.copy(
                isOnline = isOnline,
                lastSeen = timestamp,
                deviceId = deviceId ?: u.deviceId,
                sessionToken = sessionToken ?: u.sessionToken
            )
            saveLocalUsers(current)
        }

        // 2. Update Firestore
        return try {
            val db = firestore
            if (db != null) {
                val updates = hashMapOf<String, Any>(
                    "isOnline" to isOnline,
                    "online" to isOnline,
                    "lastSeen" to timestamp
                )
                if (!deviceId.isNullOrBlank()) {
                    updates["deviceId"] = deviceId
                    updates["currentDeviceId"] = deviceId
                }
                if (!sessionToken.isNullOrBlank()) {
                    updates["sessionToken"] = sessionToken
                }
                db.collection("users").document(uid).set(updates, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user presence: ${e.message}", e)
            Result.success(Unit)
        }
    }

    suspend fun getUserFromServer(uid: String): Result<User?> {
        val db = firestore ?: return getUser(uid)
        return try {
            val doc = db.collection("users").document(uid).get(Source.SERVER).await()
            if (doc.exists()) {
                val u = docToUser(doc)
                Result.success(u)
            } else {
                getUser(uid)
            }
        } catch (_: Exception) {
            getUser(uid)
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

    /**
     * Observa em tempo real alterações no documento do usuário logado (ex: conexão em outro dispositivo ou desativação de conta)
     */
    fun observeUser(uid: String): Flow<User?> = callbackFlow {
        val db = firestore
        if (db == null) {
            awaitClose {}
            return@callbackFlow
        }

        val listener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing user $uid: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = docToUser(snapshot)
                    trySend(user)
                }
            }

        awaitClose {
            listener.remove()
        }
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
                Log.e(TAG, "Error in getAllUsers listener: ${error.message}")
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
