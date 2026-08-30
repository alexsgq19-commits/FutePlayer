package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cast.CastManager
import com.example.cast.CastUiState
import com.example.data.FutemaisRepository
import com.example.data.models.ChannelOption
import com.example.data.models.MatchItem
import com.example.data.models.PlayableVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.data.UserRepository
import com.example.data.models.User
import com.example.notifications.AppNotificationManager
import kotlinx.coroutines.delay

sealed interface UiScreen {
    data object Home : UiScreen
    data object Player : UiScreen
    data object UserManagement : UiScreen
}

enum class NavigationTab {
    MATCHES,
    CHANNELS,
    SUPPORT
}

data class NetworkStatus(
    val isConnected: Boolean = true,
    val connectionType: String = "Wi-Fi / Dados Móveis",
    val isInternetValidated: Boolean = true,
    val lastChecked: Long = System.currentTimeMillis()
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val matches: List<MatchItem> = emptyList(),
    val filteredMatches: List<MatchItem> = emptyList(),
    val quickChannels: List<PlayableVideo> = emptyList(),
    val customCategories: List<String> = emptyList(),
    val selectedMatch: MatchItem? = null,
    val selectedMatchChannels: List<ChannelOption> = emptyList(),
    val isLoadingChannels: Boolean = false,
    val currentVideo: PlayableVideo? = null,
    val searchQuery: String = "",
    val selectedChampionship: String = "Todos",
    val availableChampionships: List<String> = listOf("Todos"),
    val currentTab: NavigationTab = NavigationTab.MATCHES,
    val networkStatus: NetworkStatus = NetworkStatus(),
    val latestApkUrl: String = "",
    val latestVersionName: String = "1.0.0",
    val hasStoredApk: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateDownloadError: String? = null,
    val showInstallPromptDialog: Boolean = false,
    val webVideoCasterUrl: String = "https://github.com/instantbits/WebVideoCaster/releases/download/v5.7.0/WebVideoCaster-v5.7.0.apk",
    val isDownloadingWvc: Boolean = false,
    val wvcDownloadProgress: Float = 0f,
    val wvcDownloadError: String? = null,
    val showWvcInstallPromptDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FutemaisRepository(application)
    private val castManager = CastManager.getInstance(application)
    private val userRepository = UserRepository(application)
    private val notificationManager = AppNotificationManager(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _currentScreen = MutableStateFlow<UiScreen>(UiScreen.Home)
    val currentScreen: StateFlow<UiScreen> = _currentScreen.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("futemais_prefs", Context.MODE_PRIVATE)
    private val _isLiveNotificationsEnabled = MutableStateFlow(sharedPrefs.getBoolean("live_notifications_enabled", true))
    val isLiveNotificationsEnabled: StateFlow<Boolean> = _isLiveNotificationsEnabled.asStateFlow()

    fun toggleLiveNotifications() {
        val newValue = !_isLiveNotificationsEnabled.value
        sharedPrefs.edit().putBoolean("live_notifications_enabled", newValue).apply()
        _isLiveNotificationsEnabled.value = newValue
    }

    val allUsers = userRepository.getAllUsers()
        .catch { e ->
            e.printStackTrace()
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val castUiState: StateFlow<CastUiState> = castManager.castUiState

    val favoritesFlow = repository.favoriteIds

    private var downloadedUpdateFile: java.io.File? = null
    private var downloadedWvcFile: java.io.File? = null

    init {
        val savedUrl = sharedPrefs.getString("latest_apk_url", "") ?: ""
        val savedVersion = sharedPrefs.getString("latest_version_name", "1.0.0") ?: "1.0.0"
        val savedWvcUrl = sharedPrefs.getString("wvc_apk_url", "") ?: ""
        val defaultWvcUrl = "https://github.com/instantbits/WebVideoCaster/releases/download/v5.7.0/WebVideoCaster-v5.7.0.apk"
        val finalWvcUrl = if (savedWvcUrl.isNotBlank()) savedWvcUrl else defaultWvcUrl

        _uiState.value = _uiState.value.copy(
            quickChannels = repository.getQuickChannels(),
            customCategories = repository.getCustomCategories(),
            latestApkUrl = savedUrl,
            latestVersionName = savedVersion,
            hasStoredApk = savedUrl.isNotBlank() || savedVersion != "1.0.0",
            webVideoCasterUrl = finalWvcUrl
        )
        
        repository.syncFromFirestore {
            _uiState.value = _uiState.value.copy(
                quickChannels = repository.getQuickChannels(),
                customCategories = repository.getCustomCategories()
            )
        }
        
        repository.syncUpdateFromFirestore { url, version, timestamp ->
            _uiState.value = _uiState.value.copy(
                latestApkUrl = url,
                latestVersionName = version,
                hasStoredApk = url.isNotBlank()
            )
            val lastNotifiedTimestamp = sharedPrefs.getLong("last_notified_timestamp", 0L)
            
            if (url.isNotBlank() && timestamp > lastNotifiedTimestamp) {
                notificationManager.showAppUpdateNotification(version)
                sharedPrefs.edit()
                    .putLong("last_notified_timestamp", timestamp)
                    .putString("last_notified_url", url)
                    .putString("last_notified_version", version)
                    .apply()
            }
        }
        
        repository.syncWvcUrlFromFirestore { url ->
            _uiState.value = _uiState.value.copy(webVideoCasterUrl = url)
        }
        
        loadMatches(isRefresh = false)
        startNetworkMonitoring()

        // Background polling for new live matches & streams every 3 minutes
        viewModelScope.launch {
            while (true) {
                delay(180_000L)
                loadMatches(isRefresh = true, isSilentBackground = true)
            }
        }

        viewModelScope.launch {
            userRepository.ensureAdminExists()
            
            val savedUid = sharedPrefs.getString("saved_uid", null)
            if (savedUid != null) {
                val result = userRepository.getUser(savedUid)
                result.onSuccess { user ->
                    if (user != null && user.isActive) {
                        val activeUser = user.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                        _currentUser.value = activeUser
                        userRepository.updateUserPresence(user.uid, isOnline = true)
                    } else {
                        sharedPrefs.edit().remove("saved_uid").apply()
                    }
                }
            }
        }

        // Heartbeat de presença online a cada 30 segundos
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                val user = _currentUser.value
                if (user != null && user.isActive) {
                    userRepository.updateUserPresence(user.uid, isOnline = true)
                }
            }
        }

        // Monitora o status ativo do usuário atual em tempo real
        viewModelScope.launch {
            allUsers.collect { usersList ->
                val current = _currentUser.value
                if (current != null) {
                    val updatedSelf = usersList.find { it.uid == current.uid }
                    if (updatedSelf == null || !updatedSelf.isActive) {
                        logout()
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.favoriteIds.collect { favs ->
                val updatedMatches = _uiState.value.matches.map { it.copy(isFavorite = favs.contains(it.id)) }
                _uiState.value = _uiState.value.copy(
                    matches = updatedMatches,
                    filteredMatches = applyFilter(updatedMatches, _uiState.value.searchQuery, _uiState.value.selectedChampionship, _uiState.value.currentTab, favs)
                )
            }
        }
    }

    fun loadMatches(isRefresh: Boolean = false, isSilentBackground: Boolean = false) {
        viewModelScope.launch {
            if (!isSilentBackground) {
                _uiState.value = _uiState.value.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }

            val result = repository.fetchMatches()
            result.onSuccess { matchesList ->
                val favs = repository.favoriteIds.value
                val updatedMatches = matchesList.map { it.copy(isFavorite = favs.contains(it.id)) }
                val championships = listOf("Todos") + updatedMatches.map { it.championship }.distinct()

                // Check for new matches and notify
                val previouslyNotifiedIds = sharedPrefs.getStringSet("notified_match_ids", null)
                val currentMatchIds = matchesList.map { it.id }.toSet()

                if (previouslyNotifiedIds == null) {
                    // First run: remember initial matches without spamming
                    sharedPrefs.edit().putStringSet("notified_match_ids", currentMatchIds).apply()
                } else {
                    val newMatches = matchesList.filter { !previouslyNotifiedIds.contains(it.id) }
                    if (newMatches.isNotEmpty()) {
                        if (newMatches.size == 1) {
                            val m = newMatches.first()
                            notificationManager.showNewMatchNotification(
                                title = "${m.homeTeam} vs ${m.awayTeam}",
                                time = m.time,
                                league = m.championship,
                                matchId = m.id
                            )
                        } else {
                            val first = newMatches.first()
                            notificationManager.showNewMatchNotification(
                                title = "${newMatches.size} novos jogos ao vivo disponíveis!",
                                time = "Hoje e Próximos",
                                league = "${first.homeTeam} vs ${first.awayTeam} e outros",
                                matchId = first.id
                            )
                        }
                        val updatedNotified = (previouslyNotifiedIds + currentMatchIds).toSet()
                        sharedPrefs.edit().putStringSet("notified_match_ids", updatedNotified).apply()
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    matches = updatedMatches,
                    availableChampionships = championships,
                    filteredMatches = applyFilter(updatedMatches, _uiState.value.searchQuery, _uiState.value.selectedChampionship, _uiState.value.currentTab, favs)
                )
            }.onFailure { error ->
                if (!isSilentBackground) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "Não foi possível carregar os jogos ao vivo. Usando canais recomendados."
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredMatches = applyFilter(_uiState.value.matches, query, _uiState.value.selectedChampionship, _uiState.value.currentTab, repository.favoriteIds.value)
        )
    }

    fun onChampionshipSelected(championship: String) {
        _uiState.value = _uiState.value.copy(
            selectedChampionship = championship,
            filteredMatches = applyFilter(_uiState.value.matches, _uiState.value.searchQuery, championship, _uiState.value.currentTab, repository.favoriteIds.value)
        )
    }

    fun onTabSelected(tab: NavigationTab) {
        _uiState.value = _uiState.value.copy(
            currentTab = tab,
            filteredMatches = applyFilter(_uiState.value.matches, _uiState.value.searchQuery, _uiState.value.selectedChampionship, tab, repository.favoriteIds.value)
        )
    }

    private fun applyFilter(
        matches: List<MatchItem>,
        query: String,
        championship: String,
        tab: NavigationTab,
        favorites: Set<String>
    ): List<MatchItem> {
        var list = matches

        if (championship != "Todos") {
            list = list.filter { it.championship.equals(championship, ignoreCase = true) }
        }

        if (query.isNotBlank()) {
            list = list.filter {
                it.homeTeam.contains(query, ignoreCase = true) ||
                it.awayTeam.contains(query, ignoreCase = true) ||
                it.championship.contains(query, ignoreCase = true)
            }
        }

        return list
    }

    fun selectMatch(match: MatchItem) {
        _uiState.value = _uiState.value.copy(
            selectedMatch = match,
            isLoadingChannels = true,
            selectedMatchChannels = emptyList()
        )

        viewModelScope.launch {
            val result = repository.fetchChannelsForMatch(match.detailUrl)
            result.onSuccess { channels ->
                _uiState.value = _uiState.value.copy(
                    isLoadingChannels = false,
                    selectedMatchChannels = channels
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoadingChannels = false
                )
            }
        }
    }

    fun dismissMatchDetails() {
        _uiState.value = _uiState.value.copy(
            selectedMatch = null,
            selectedMatchChannels = emptyList()
        )
    }

    fun selectChannelAndPlay(match: MatchItem, channel: ChannelOption) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingChannels = true)
            val result = repository.resolveStream(
                channel = channel,
                matchTitle = match.displayTitle,
                championship = match.championship,
                posterUrl = match.homeLogoUrl.ifBlank { match.awayLogoUrl }
            )

            result.onSuccess { video ->
                _uiState.value = _uiState.value.copy(
                    isLoadingChannels = false,
                    currentVideo = video,
                    selectedMatch = null
                )
                _currentScreen.value = UiScreen.Player

                // If currently connected to Chromecast, cast immediately
                if (castManager.castUiState.value.isConnected) {
                    castCurrentVideo()
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoadingChannels = false)
            }
        }
    }

    fun playDirectVideo(video: PlayableVideo) {
        _uiState.value = _uiState.value.copy(
            currentVideo = video,
            selectedMatch = null
        )
        _currentScreen.value = UiScreen.Player

        if (castManager.castUiState.value.isConnected) {
            castCurrentVideo()
        }
    }

    fun navigateTo(screen: UiScreen) {
        _currentScreen.value = screen
    }

    fun toggleFavorite(id: String) {
        repository.toggleFavorite(id)
    }

    fun addQuickChannel(
        title: String,
        subtitle: String,
        url: String,
        isWebPlayer: Boolean,
        category: String = "Esportes"
    ) {
        val cleanUrl = url.trim()
        val cleanTitle = if (title.isNotBlank()) title.trim() else "Canal Rápido"
        val cleanSubtitle = if (subtitle.isNotBlank()) subtitle.trim() else "Canal Adicionado por Admin"
        val id = "custom_${System.currentTimeMillis()}"

        val newChannel = PlayableVideo(
            id = id,
            title = cleanTitle,
            subtitle = cleanSubtitle,
            streamUrl = cleanUrl,
            embedUrl = if (isWebPlayer) cleanUrl else null,
            forceWebPlayer = isWebPlayer,
            isLive = true,
            category = category
        )

        val updatedChannels = repository.addCustomChannel(newChannel)
        _uiState.value = _uiState.value.copy(
            quickChannels = updatedChannels,
            customCategories = repository.getCustomCategories()
        )

        // Trigger Notification
        notificationManager.showNewChannelNotification(
            channelTitle = cleanTitle,
            channelSubtitle = cleanSubtitle,
            channelId = id
        )
    }

    fun handleNotificationTarget(targetType: String?, targetId: String?) {
        if (targetType == null || targetId == null) return

        when (targetType) {
            AppNotificationManager.TARGET_MATCH -> {
                val match = _uiState.value.matches.find { it.id == targetId }
                if (match != null) {
                    selectMatch(match)
                }
                onTabSelected(NavigationTab.MATCHES)
                _currentScreen.value = UiScreen.Home
            }
            AppNotificationManager.TARGET_CHANNEL -> {
                val channel = _uiState.value.quickChannels.find { it.id == targetId }
                if (channel != null) {
                    playDirectVideo(channel)
                } else {
                    onTabSelected(NavigationTab.CHANNELS)
                    _currentScreen.value = UiScreen.Home
                }
            }
            AppNotificationManager.TARGET_UPDATE -> {
                onTabSelected(NavigationTab.SUPPORT)
                _currentScreen.value = UiScreen.Home
            }
        }
    }

    fun updateQuickChannel(
        id: String,
        title: String,
        subtitle: String,
        url: String,
        isWebPlayer: Boolean,
        category: String = "Esportes"
    ) {
        val cleanUrl = url.trim()
        val cleanTitle = if (title.isNotBlank()) title.trim() else "Canal Rápido"
        val cleanSubtitle = if (subtitle.isNotBlank()) subtitle.trim() else ""

        val existing = _uiState.value.quickChannels.find { it.id == id }
        val updatedChannel = (existing ?: PlayableVideo(id = id, title = cleanTitle, subtitle = cleanSubtitle, streamUrl = cleanUrl)).copy(
            id = id,
            title = cleanTitle,
            subtitle = cleanSubtitle,
            streamUrl = cleanUrl,
            embedUrl = if (isWebPlayer) cleanUrl else existing?.embedUrl,
            forceWebPlayer = isWebPlayer,
            isLive = true,
            category = category
        )

        val updatedChannels = repository.updateQuickChannel(updatedChannel)
        _uiState.value = _uiState.value.copy(
            quickChannels = updatedChannels,
            customCategories = repository.getCustomCategories()
        )
    }

    fun addChannelCategory(name: String) {
        val updated = repository.addCustomCategory(name)
        _uiState.value = _uiState.value.copy(customCategories = updated)
    }

    fun deleteChannelCategory(name: String) {
        val updated = repository.deleteCustomCategory(name)
        _uiState.value = _uiState.value.copy(customCategories = updated)
    }

    fun updateChannelCategory(oldName: String, newName: String) {
        val updated = repository.updateCustomCategory(oldName, newName)
        _uiState.value = _uiState.value.copy(
            customCategories = updated,
            quickChannels = repository.getQuickChannels()
        )
    }

    fun resetDefaultChannel(id: String) {
        val updatedChannels = repository.resetDefaultChannel(id)
        _uiState.value = _uiState.value.copy(quickChannels = updatedChannels)
    }

    fun deleteQuickChannel(id: String) {
        val updatedChannels = repository.deleteCustomChannel(id)
        _uiState.value = _uiState.value.copy(quickChannels = updatedChannels)
    }

    fun castCurrentVideo() {
        val video = _uiState.value.currentVideo ?: return
        castManager.castMedia(
            title = video.title,
            subtitle = video.subtitle,
            streamUrl = video.streamUrl,
            posterUrl = video.posterUrl,
            isLive = video.isLive
        )
    }

    fun toggleCastPlayPause() {
        castManager.togglePlayPause()
    }

    fun disconnectCast() {
        castManager.disconnect()
    }

    private fun startNetworkMonitoring() {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) return

        fun updateStatus() {
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isConnected = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val type = when {
                capabilities == null -> "Sem Conexão"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados Móveis (4G/5G)"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Conectado"
            }
            val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false

            _uiState.value = _uiState.value.copy(
                networkStatus = NetworkStatus(
                    isConnected = isConnected,
                    connectionType = type,
                    isInternetValidated = isValidated,
                    lastChecked = System.currentTimeMillis()
                )
            )
        }

        updateStatus()

        try {
            connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateStatus()
                }
                override fun onLost(network: Network) {
                    updateStatus()
                }
            })
        } catch (_: Exception) {}
    }

    fun login(cpf: String, pass: String, rememberMe: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.login(cpf, pass)
            result.onSuccess { user ->
                if (user != null) {
                    if (!user.isActive) {
                        onResult(false, "Sua conta está inativa. Contate o administrador.")
                        return@launch
                    }
                    val onlineUser = user.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                    _currentUser.value = onlineUser
                    userRepository.updateUserPresence(user.uid, isOnline = true)

                    if (rememberMe) {
                        sharedPrefs.edit().putString("saved_uid", user.uid).apply()
                    } else {
                        sharedPrefs.edit().remove("saved_uid").apply()
                    }
                    onResult(true, null)
                } else {
                    onResult(false, "CPF ou senha inválidos.")
                }
            }.onFailure { e ->
                onResult(false, e.localizedMessage ?: "Erro ao realizar login.")
            }
        }
    }

    fun logout() {
        val user = _currentUser.value
        if (user != null) {
            viewModelScope.launch {
                userRepository.updateUserPresence(user.uid, isOnline = false)
            }
        }
        _currentUser.value = null
        sharedPrefs.edit().remove("saved_uid").apply()
        navigateTo(UiScreen.Home)
    }

    fun refreshUserPresence() {
        val user = _currentUser.value
        if (user != null && user.isActive) {
            viewModelScope.launch {
                userRepository.updateUserPresence(user.uid, isOnline = true)
            }
        }
    }

    fun saveUser(user: User, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.saveUser(user)
            result.onSuccess {
                // If updating currently logged in user, update local session
                if (_currentUser.value?.uid == user.uid) {
                    _currentUser.value = user
                }
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.localizedMessage ?: "Erro ao salvar usuário.")
            }
        }
    }

    fun deleteUser(uid: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.deleteUser(uid)
            result.onSuccess {
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.localizedMessage ?: "Erro ao excluir usuário.")
            }
        }
    }

    private fun cleanGoogleDriveUrl(url: String): String {
        if (url.contains("drive.google.com")) {
            val regex = Regex("/file/d/([a-zA-Z0-9_-]+)")
            val match = regex.find(url)
            if (match != null && match.groupValues.size > 1) {
                val fileId = match.groupValues[1]
                return "https://drive.google.com/uc?export=download&id=$fileId&confirm=t"
            }
        }
        return url
    }

    fun publishNewUpdate(apkUrl: String, versionName: String) {
        val cleanUrl = cleanGoogleDriveUrl(apkUrl)
        sharedPrefs.edit()
            .putString("latest_apk_url", cleanUrl)
            .putString("latest_version_name", versionName)
            .apply()

        _uiState.value = _uiState.value.copy(
            latestApkUrl = cleanUrl,
            latestVersionName = versionName,
            hasStoredApk = cleanUrl.isNotBlank()
        )
        
        repository.publishUpdateToFirestore(cleanUrl, versionName)
    }

    private fun startManagedDownload(
        apkUrl: String,
        fileName: String,
        title: String,
        onStart: suspend () -> Unit,
        onProgress: suspend (Float) -> Unit,
        onSuccess: suspend (java.io.File) -> Unit,
        onError: suspend (String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                onStart()
                val context = getApplication<Application>()
                var url = java.net.URL(apkUrl)
                var connection = url.openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                connection.connect()

                var contentType = connection.contentType
                var finalUrlStr = url.toString()

                if (contentType != null && contentType.contains("text/html") && url.host.contains("drive.google.com")) {
                    val html = connection.inputStream.bufferedReader().use { it.readText() }
                    val confirmRegex = Regex("confirm=([a-zA-Z0-9_-]+)")
                    val match = confirmRegex.find(html)
                    if (match != null) {
                        val confirmToken = match.groupValues[1]
                        val fileIdRegex = Regex("id=([a-zA-Z0-9_-]+)")
                        val idMatch = fileIdRegex.find(url.toString())
                        val fileId = idMatch?.groupValues?.get(1) ?: ""
                        finalUrlStr = "https://drive.google.com/uc?export=download&id=$fileId&confirm=$confirmToken"
                    } else {
                        throw Exception("O link fornecido não é um arquivo APK válido (Google Drive HTML).")
                    }
                } else if (contentType != null && contentType.contains("text/html")) {
                    throw Exception("O link fornecido não é um arquivo APK válido (Página HTML retornada). Certifique-se de usar o link direto.")
                } else {
                    finalUrlStr = connection.url.toString()
                }
                connection.disconnect()

                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val uri = android.net.Uri.parse(finalUrlStr)
                
                val request = android.app.DownloadManager.Request(uri).apply {
                    setTitle(title)
                    setDescription("Baixando arquivo...")
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                    setMimeType("application/vnd.android.package-archive")
                }

                val file = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), fileName)
                if (file.exists()) file.delete()

                val downloadId = downloadManager.enqueue(request)

                var downloading = true
                while (downloading) {
                    kotlinx.coroutines.delay(1000)
                    val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusColumn = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusColumn)

                        val bytesDownloadedColumn = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalColumn = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        if (bytesDownloadedColumn != -1 && bytesTotalColumn != -1) {
                            val bytesDownloaded = cursor.getLong(bytesDownloadedColumn)
                            val bytesTotal = cursor.getLong(bytesTotalColumn)

                            if (bytesTotal > 0) {
                                val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }

                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                onSuccess(file)
                            }
                        } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                            downloading = false
                            throw Exception("Falha no download gerenciado pelo sistema.")
                        }
                    } else if (cursor == null) {
                        downloading = false
                    }
                    cursor?.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Erro ao baixar arquivo.")
                }
            }
        }
    }

    fun downloadAndUpdate(apkUrl: String) {
        if (apkUrl.isBlank()) return
        startManagedDownload(
            apkUrl = apkUrl,
            fileName = "update.apk",
            title = "Atualização do FutePlayer",
            onStart = {
                _uiState.value = _uiState.value.copy(isDownloadingUpdate = true, updateDownloadProgress = 0f, updateDownloadError = null)
            },
            onProgress = { progress ->
                _uiState.value = _uiState.value.copy(updateDownloadProgress = progress)
            },
            onSuccess = { file ->
                downloadedUpdateFile = file
                _uiState.value = _uiState.value.copy(
                    isDownloadingUpdate = false,
                    showInstallPromptDialog = true
                )
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingUpdate = false,
                    updateDownloadError = error
                )
            }
        )
    }

    fun prepareAndPromptInstall() {
        if (downloadedUpdateFile == null || !downloadedUpdateFile!!.exists()) {
            downloadedUpdateFile = java.io.File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "update.apk")
            if (!downloadedUpdateFile!!.exists()) {
                downloadedUpdateFile = java.io.File(getApplication<Application>().cacheDir, "update.apk")
            }
        }
        if (downloadedUpdateFile != null && downloadedUpdateFile!!.exists()) {
            _uiState.value = _uiState.value.copy(showInstallPromptDialog = true)
        } else {
            if (_uiState.value.latestApkUrl.isNotBlank()) {
                downloadAndUpdate(_uiState.value.latestApkUrl)
            }
        }
    }

    fun dismissInstallPrompt() {
        _uiState.value = _uiState.value.copy(showInstallPromptDialog = false)
    }

    fun installDownloadedUpdate(context: Context) {
        var file = downloadedUpdateFile ?: java.io.File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "update.apk")
        
        if (!file.exists()) {
            val cacheFile = java.io.File(getApplication<Application>().cacheDir, "update.apk")
            if (cacheFile.exists()) {
                file = cacheFile
            } else {
                val oldFile = java.io.File(getApplication<Application>().filesDir, "stored_app_update.apk")
                if (oldFile.exists()) {
                    file = oldFile
                }
            }
        }
        
        if (file.exists()) {
            try {
                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun publishWvcUrl(url: String) {
        val cleanUrl = cleanGoogleDriveUrl(url)
        sharedPrefs.edit().putString("wvc_apk_url", cleanUrl).apply()
        _uiState.value = _uiState.value.copy(webVideoCasterUrl = cleanUrl)
        repository.publishWvcUrlToFirestore(cleanUrl)
    }

    fun downloadWebVideoCaster(apkUrl: String) {
        if (apkUrl.isBlank()) return
        startManagedDownload(
            apkUrl = apkUrl,
            fileName = "webvideocaster.apk",
            title = "Web Video Caster",
            onStart = {
                _uiState.value = _uiState.value.copy(isDownloadingWvc = true, wvcDownloadProgress = 0f, wvcDownloadError = null)
            },
            onProgress = { progress ->
                _uiState.value = _uiState.value.copy(wvcDownloadProgress = progress)
            },
            onSuccess = { file ->
                downloadedWvcFile = file
                _uiState.value = _uiState.value.copy(
                    isDownloadingWvc = false,
                    showWvcInstallPromptDialog = true
                )
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    isDownloadingWvc = false,
                    wvcDownloadError = error
                )
            }
        )
    }

    fun dismissWvcInstallPrompt() {
        _uiState.value = _uiState.value.copy(showWvcInstallPromptDialog = false)
    }

    fun installDownloadedWvc(context: Context) {
        var file = downloadedWvcFile ?: java.io.File(getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "webvideocaster.apk")
        
        if (!file.exists()) {
            val cacheFile = java.io.File(getApplication<Application>().cacheDir, "webvideocaster.apk")
            if (cacheFile.exists()) {
                file = cacheFile
            }
        }
        
        if (file.exists()) {
            try {
                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
