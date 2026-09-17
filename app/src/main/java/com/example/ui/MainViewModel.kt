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
import com.example.data.UserRepository
import com.example.data.MoviesRepository
import com.example.data.models.User
import com.example.data.models.MovieItem
import com.example.data.models.AutoCorrectionLog
import com.example.data.models.ChannelOption
import com.example.data.models.ChannelTestSummary
import com.example.data.models.EpisodeItem
import com.example.data.models.MatchItem
import com.example.data.models.MediaContentType
import com.example.data.models.MediaItem
import com.example.data.models.OFFLINE_FALLBACK_URL
import com.example.data.models.PlayableVideo
import com.example.data.models.SeasonItem
import com.example.data.SubscriptionRepository
import com.example.data.models.PaymentOrder
import com.example.data.models.PaymentRecord
import com.example.ui.components.SubscriptionFlowState
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.notifications.AppNotificationManager
import com.example.util.SearchUtils
import kotlinx.coroutines.delay

sealed interface UiScreen {
    data object Home : UiScreen
    data object Player : UiScreen
    data object UserManagement : UiScreen
    data object MoviesApi : UiScreen
}

enum class NavigationTab {
    MATCHES,
    CHANNELS,
    MOVIES_SERIES,
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
    val mediaCatalog: List<MediaItem> = emptyList(),
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
    val latestVersionName: String = "1.1.0",
    val hasStoredApk: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float = 0f,
    val updateDownloadError: String? = null,
    val showInstallPromptDialog: Boolean = false,
    val webVideoCasterUrl: String = "https://github.com/instantbits/WebVideoCaster/releases/download/v5.7.0/WebVideoCaster-v5.7.0.apk",
    val isDownloadingWvc: Boolean = false,
    val wvcDownloadProgress: Float = 0f,
    val wvcDownloadError: String? = null,
    val showWvcInstallPromptDialog: Boolean = false,
    val supportWhatsappNumber: String = "(75) 9 9249-0975",
    val isRegistrationEnabled: Boolean = true,
    val isTestingChannels: Boolean = false,
    val channelTestProgressText: String? = null,
    val lastChannelTestSummary: ChannelTestSummary? = null,
    val adminChannelAlert: String? = null,
    val isTestingMovies: Boolean = false,
    val movieTestProgressText: String? = null,
    val autoCorrectionLogs: List<AutoCorrectionLog> = emptyList(),
    val selectedMediaItem: MediaItem? = null,
    val selectedSeason: SeasonItem? = null,
    val selectedEpisode: EpisodeItem? = null,
    val hasNextEpisode: Boolean = false,
    val nextEpisodeTitle: String? = null,
    val nextSeasonForEpisode: SeasonItem? = null,
    val nextEpisodeItem: EpisodeItem? = null,
    val watchProgressMap: Map<String, com.example.data.WatchProgress> = emptyMap(),
    val initialPlaybackPositionMs: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FutemaisRepository(application)
    private val castManager = CastManager.getInstance(application)
    private val userRepository = UserRepository(application)
    private val notificationManager = AppNotificationManager(application)
    private val watchProgressRepository = com.example.data.WatchProgressRepository(application)

    private val movieApiRepository = com.example.data.MovieApiRepository(application)
    private val _movieApiSources = MutableStateFlow<List<com.example.data.models.MovieApiSource>>(emptyList())
    val movieApiSources: StateFlow<List<com.example.data.models.MovieApiSource>> = _movieApiSources.asStateFlow()

    private val _movieApiVideos = MutableStateFlow<List<PlayableVideo>>(emptyList())
    val movieApiVideos: StateFlow<List<PlayableVideo>> = _movieApiVideos.asStateFlow()

    private val _isSyncingMovieApis = MutableStateFlow(false)
    val isSyncingMovieApis: StateFlow<Boolean> = _isSyncingMovieApis.asStateFlow()

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

    private val _loginNoticeMessage = MutableStateFlow<String?>(null)
    val loginNoticeMessage: StateFlow<String?> = _loginNoticeMessage.asStateFlow()

    fun clearLoginNotice() {
        _loginNoticeMessage.value = null
    }

    val subscriptionRepository = SubscriptionRepository(application)

    private val _subscriptionFlowState = MutableStateFlow<SubscriptionFlowState>(SubscriptionFlowState.Idle)
    val subscriptionFlowState: StateFlow<SubscriptionFlowState> = _subscriptionFlowState.asStateFlow()

    private val _showSubscriptionDialog = MutableStateFlow(false)
    val showSubscriptionDialog: StateFlow<Boolean> = _showSubscriptionDialog.asStateFlow()

    private val _showPaymentHistoryDialog = MutableStateFlow(false)
    val showPaymentHistoryDialog: StateFlow<Boolean> = _showPaymentHistoryDialog.asStateFlow()

    val allPayments: StateFlow<List<PaymentRecord>> = subscriptionRepository.observeAllPayments()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var activePaymentOrderJob: kotlinx.coroutines.Job? = null

    fun openSubscriptionDialog() {
        _subscriptionFlowState.value = SubscriptionFlowState.Idle
        _showSubscriptionDialog.value = true
    }

    fun dismissSubscriptionDialog() {
        _showSubscriptionDialog.value = false
        if (_subscriptionFlowState.value is SubscriptionFlowState.Success) {
            _subscriptionFlowState.value = SubscriptionFlowState.Idle
        }
    }

    fun openPaymentHistoryDialog() {
        _showPaymentHistoryDialog.value = true
    }

    fun dismissPaymentHistoryDialog() {
        _showPaymentHistoryDialog.value = false
    }

    fun startSubscriptionPayment(context: Context) {
        val user = _currentUser.value
        if (user == null) {
            _subscriptionFlowState.value = SubscriptionFlowState.Error("Usuário não autenticado.")
            return
        }

        viewModelScope.launch {
            _subscriptionFlowState.value = SubscriptionFlowState.CreatingCheckout
            val result = subscriptionRepository.createCheckout(user)
            result.onSuccess { order ->
                _subscriptionFlowState.value = SubscriptionFlowState.AwaitingPayment(order)

                // Abre o checkout no navegador
                if (order.checkoutUrl.isNotBlank()) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(order.checkoutUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Erro ao abrir navegador: ${e.message}")
                    }
                }

                // Inicia escuta em tempo real do pedido no Firestore
                observeOrderCompletion(order.orderNsu)
            }.onFailure { err ->
                _subscriptionFlowState.value = SubscriptionFlowState.Error(
                    err.localizedMessage ?: "Erro ao gerar link de pagamento na InfinitePay."
                )
            }
        }
    }

    private fun observeOrderCompletion(orderNsu: String) {
        activePaymentOrderJob?.cancel()
        activePaymentOrderJob = viewModelScope.launch {
            subscriptionRepository.observePaymentOrder(orderNsu).collect { order ->
                if (order != null && order.status.equals("PAID", ignoreCase = true)) {
                    _subscriptionFlowState.value = SubscriptionFlowState.Success(
                        "Assinatura renovada com sucesso! Seu acesso foi estendido por 30 dias."
                    )
                }
            }
        }
    }

    fun checkSubscriptionStatusManually() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            userRepository.observeUser(user.uid).collect { updated ->
                if (updated != null) {
                    _currentUser.value = updated
                    if (updated.canAccessPremiumContent()) {
                        _subscriptionFlowState.value = SubscriptionFlowState.Success(
                            "Assinatura ativa e confirmada!"
                        )
                    }
                }
            }
        }
    }

    fun simulateAdminPaymentApproval(orderNsu: String) {
        viewModelScope.launch {
            val result = subscriptionRepository.simulateWebhookProcessing(orderNsu)
            result.onSuccess { msg ->
                _subscriptionFlowState.value = SubscriptionFlowState.Success(msg)
            }.onFailure { err ->
                _subscriptionFlowState.value = SubscriptionFlowState.Error(
                    err.message ?: "Erro ao processar simulação de webhook."
                )
            }
        }
    }

    fun setUserBillingExempt(targetUser: User, isExempt: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = userRepository.setUserBillingExempt(targetUser.uid, isExempt)
            result.onSuccess {
                onResult(true, null)
            }.onFailure { err ->
                onResult(false, err.message)
            }
        }
    }

    private var observeUserJob: kotlinx.coroutines.Job? = null

    private fun startObservingCurrentUser(user: User) {
        observeUserJob?.cancel()
        observeUserJob = viewModelScope.launch {
            userRepository.observeUser(user.uid).collect { updatedUser ->
                if (updatedUser == null) return@collect
                _currentUser.value = updatedUser
                val myDeviceId = getDeviceId()

                if (!updatedUser.isActive) {
                    logout("Sua conta foi desativada pelo administrador.")
                    return@collect
                }

                // Se o usuário estiver no Player e expirar a assinatura:
                if (!updatedUser.canAccessPremiumContent() && _currentScreen.value is UiScreen.Player) {
                    onPlayerBack()
                    openSubscriptionDialog()
                } else if (!updatedUser.canAccessPremiumContent() && _subscriptionFlowState.value is SubscriptionFlowState.Idle && !_showSubscriptionDialog.value) {
                    // Inicia automaticamente o fluxo de renovação ao detectar vencimento fora do player
                    openSubscriptionDialog()
                }

                // Se estava aguardando pagamento e a assinatura foi confirmada como ativa:
                if (updatedUser.canAccessPremiumContent() && _subscriptionFlowState.value is SubscriptionFlowState.AwaitingPayment) {
                    _subscriptionFlowState.value = SubscriptionFlowState.Success(
                        "Assinatura renovada com sucesso! Seu acesso foi estendido por 30 dias."
                    )
                }

                val remoteDeviceId = updatedUser.deviceId.ifBlank { updatedUser.sessionToken }
                if (remoteDeviceId.isNotBlank() && remoteDeviceId != myDeviceId) {
                    android.util.Log.w("MainViewModel", "Sessão duplicada: desconectando dispositivo $myDeviceId em prol de $remoteDeviceId")
                    logout("Sua conta foi conectada em outro dispositivo. Você foi desconectado automaticamente.")
                }
            }
        }
    }

    fun getDeviceId(): String {
        var id = sharedPrefs.getString("device_unique_id", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            sharedPrefs.edit().putString("device_unique_id", id).apply()
        }
        return id
    }

    init {
        val savedUrl = sharedPrefs.getString("latest_apk_url", "") ?: ""
        val savedVersion = sharedPrefs.getString("latest_version_name", "1.1.0") ?: "1.1.0"
        val savedWvcUrl = sharedPrefs.getString("wvc_apk_url", "") ?: ""
        val defaultWvcUrl = "https://github.com/instantbits/WebVideoCaster/releases/download/v5.7.0/WebVideoCaster-v5.7.0.apk"
        val finalWvcUrl = if (savedWvcUrl.isNotBlank()) savedWvcUrl else defaultWvcUrl

        _uiState.value = _uiState.value.copy(
            quickChannels = repository.getQuickChannels(),
            customCategories = repository.getCustomCategories(),
            mediaCatalog = repository.getMediaCatalog(),
            latestApkUrl = savedUrl,
            latestVersionName = savedVersion,
            hasStoredApk = savedUrl.isNotBlank() || savedVersion != "1.1.0",
            webVideoCasterUrl = finalWvcUrl,
            supportWhatsappNumber = repository.getSupportWhatsappNumber(),
            autoCorrectionLogs = repository.getAutoCorrectionLogs(),
            watchProgressMap = watchProgressRepository.progressFlow.value
        )

        viewModelScope.launch {
            watchProgressRepository.progressFlow.collect { progressMap ->
                _uiState.value = _uiState.value.copy(watchProgressMap = progressMap)
            }
        }

        viewModelScope.launch {
            repository.mediaCatalogFlow.collect { catalog ->
                _uiState.value = _uiState.value.copy(mediaCatalog = catalog)
            }
        }

        viewModelScope.launch {
            castManager.castUiState.collect { castState ->
                if (castState.isConnected && castState.currentStreamUrl.isNullOrBlank() && _uiState.value.currentVideo != null) {
                    castCurrentVideo()
                }
            }
        }
        
        repository.syncFromFirestore {
            _uiState.value = _uiState.value.copy(
                quickChannels = repository.getQuickChannels(),
                customCategories = repository.getCustomCategories()
            )
        }

        repository.syncSupportWhatsappFromFirestore { number ->
            if (number.isNotBlank()) {
                _uiState.value = _uiState.value.copy(supportWhatsappNumber = number)
            }
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

        viewModelScope.launch {
            _movieApiSources.value = movieApiRepository.getSources()
            if (_movieApiSources.value.any { it.isActive && it.apiUrl.isNotBlank() }) {
                syncMovieApiSources()
            }
        }

        // Automatic testing and notification for admin every 12 hours in background
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                try {
                    val lastCheckTime = sharedPrefs.getLong("last_auto_check_time", 0L)
                    val currentTime = System.currentTimeMillis()
                    val interval = 12 * 60 * 60 * 1000L // 12 hours
                    if (currentTime - lastCheckTime > interval) {
                        // Test channels (testing only, no auto link substitution)
                        val channelSummary = repository.testAllChannels { _, _, _, _ -> }
                        
                        // Test movies and series (testing only, no auto link substitution)
                        val offlineMediaTitles = mutableListOf<String>()
                        try {
                            val catalog = repository.getMediaCatalog()
                            val moviesRepo = MoviesRepository()
                            catalog.forEach { item ->
                                val isSeries = item.type == MediaContentType.SERIES
                                val currentStreamUrl = if (!isSeries) {
                                    item.movieStreamUrl.orEmpty()
                                } else {
                                    item.seasons.firstOrNull()?.episodes?.firstOrNull()?.streamUrl.orEmpty()
                                }
                                val isWorking = if (currentStreamUrl.isNotBlank()) {
                                    moviesRepo.testMovieServer(currentStreamUrl)
                                } else {
                                    false
                                }
                                if (isWorking) {
                                    repository.addOrUpdateMediaItem(item.copy(isWorking = true))
                                } else {
                                    offlineMediaTitles.add(item.title)
                                    val offlineItem = if (!isSeries) {
                                        item.copy(isWorking = false)
                                    } else {
                                        item.copy(isWorking = false)
                                    }
                                    repository.addOrUpdateMediaItem(offlineItem)
                                    repository.addAutoCorrectionLog(
                                        AutoCorrectionLog(
                                            itemType = if (isSeries) "SÉRIE" else "FILME",
                                            title = item.title,
                                            description = "Título testado e detectado fora do ar. Admin notificado para inclusão de novo link.",
                                            status = "Fora do Ar (Requer Atenção do Admin)"
                                        )
                                    )
                                }
                            }
                        } catch (_: Exception) {}

                        val totalOffline = channelSummary.offlineCount + offlineMediaTitles.size
                        if (totalOffline > 0) {
                            notificationManager.showAdminChannelAlertNotification(
                                title = "⚠️ Itens Fora do Ar Detectados",
                                message = "$totalOffline item(ns) fora do ar (${channelSummary.offlineCount} canais, ${offlineMediaTitles.size} filmes/séries). Toque para gerenciar.",
                                offlineCount = totalOffline
                            )
                        }

                        sharedPrefs.edit().putLong("last_auto_check_time", currentTime).apply()
                        _uiState.value = _uiState.value.copy(
                            autoCorrectionLogs = repository.getAutoCorrectionLogs(),
                            mediaCatalog = repository.getMediaCatalog(),
                            quickChannels = repository.getQuickChannels()
                        )
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(60 * 60 * 1000L) // Check hourly
            }
        }

        repository.syncRegistrationEnabledFromFirestore { enabled ->
            _uiState.value = _uiState.value.copy(isRegistrationEnabled = enabled)
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
                val result = userRepository.getUserFromServer(savedUid)
                result.onSuccess { user ->
                    if (user != null && user.isActive) {
                        val myDeviceId = getDeviceId()
                        val mySessionToken = java.util.UUID.randomUUID().toString()
                        val activeUser = user.copy(
                            isOnline = true,
                            lastSeen = System.currentTimeMillis(),
                            deviceId = myDeviceId,
                            sessionToken = mySessionToken
                        )
                        _currentUser.value = activeUser
                        userRepository.updateUserPresence(
                            uid = user.uid,
                            isOnline = true,
                            deviceId = myDeviceId,
                            sessionToken = mySessionToken
                        )
                        startObservingCurrentUser(activeUser)
                    } else {
                        sharedPrefs.edit().remove("saved_uid").apply()
                    }
                }
            }
        }

        // Heartbeat de presença online e validação de dispositivo conectado a cada 6 segundos
        viewModelScope.launch {
            while (true) {
                delay(6_000L)
                val user = _currentUser.value
                if (user != null && user.isActive) {
                    val myDeviceId = getDeviceId()
                    val isValidSession = userRepository.updateUserHeartbeat(user.uid, myDeviceId)
                    if (!isValidSession) {
                        logout("Sua conta foi conectada em outro dispositivo. Você foi desconectado automaticamente.")
                    }
                }
            }
        }

        // Monitora o status ativo do usuário atual e o dispositivo conectado em tempo real pela lista geral
        viewModelScope.launch {
            allUsers.collect { usersList ->
                val current = _currentUser.value
                if (current != null) {
                    val updatedSelf = usersList.find { it.uid == current.uid }
                    if (updatedSelf == null || !updatedSelf.isActive) {
                        logout("Sua conta foi desativada pelo administrador.")
                    } else {
                        val remoteDeviceId = updatedSelf.deviceId.ifBlank { updatedSelf.sessionToken }
                        if (remoteDeviceId.isNotBlank() && remoteDeviceId != getDeviceId()) {
                            logout("Sua conta foi conectada em outro dispositivo. Você foi desconectado automaticamente.")
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.favoriteIds.collect { favs ->
                val updatedChannels = _uiState.value.quickChannels.map { it.copy(isFavorite = favs.contains(it.id)) }
                _uiState.value = _uiState.value.copy(
                    quickChannels = updatedChannels
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
                SearchUtils.matchesCombined(query, it.homeTeam, it.awayTeam, it.championship, it.time, it.dateTag)
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
        val user = _currentUser.value
        if (user != null && !user.canAccessPremiumContent()) {
            openSubscriptionDialog()
            return
        }

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

    fun addMovieApiSource(name: String, url: String, type: String = "JSON / REST") {
        val newSource = com.example.data.models.MovieApiSource(
            name = name.ifBlank { "Minha API de Filmes" },
            apiUrl = url.trim(),
            apiType = type
        )
        _movieApiSources.value = movieApiRepository.addSource(newSource)
        syncMovieApiSources()
    }

    fun updateMovieApiSource(source: com.example.data.models.MovieApiSource) {
        _movieApiSources.value = movieApiRepository.updateSource(source)
        syncMovieApiSources()
    }

    fun deleteMovieApiSource(id: String) {
        _movieApiSources.value = movieApiRepository.deleteSource(id)
        syncMovieApiSources()
    }

    fun toggleMovieApiSource(id: String) {
        _movieApiSources.value = movieApiRepository.toggleSourceActive(id)
        syncMovieApiSources()
    }

    fun testMovieApiUrl(url: String, type: String, onResult: (Boolean, String, Int) -> Unit) {
        viewModelScope.launch {
            val res = movieApiRepository.testApiUrl(url, type)
            res.onSuccess { (count, _) ->
                onResult(true, "Sucesso! $count filmes/séries encontrados na API.", count)
            }.onFailure { err ->
                onResult(false, err.message ?: "Erro ao testar API", 0)
            }
        }
    }

    fun syncMovieApiSources() {
        viewModelScope.launch {
            _isSyncingMovieApis.value = true
            val videos = movieApiRepository.fetchAllActiveMovies()
            _movieApiVideos.value = videos
            _movieApiSources.value = movieApiRepository.getSources()
            _isSyncingMovieApis.value = false

            // Asynchronously resolve true titles and posters from IMDb / Cinemeta
            movieApiRepository.enrichVideosMetadata(videos) { updatedVideos ->
                _movieApiVideos.value = updatedVideos
            }
        }
    }

    fun playDirectVideo(video: PlayableVideo) {
        val user = _currentUser.value
        if (user != null && !user.canAccessPremiumContent()) {
            openSubscriptionDialog()
            return
        }

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
        val favs = repository.favoriteIds.value
        val updatedChannels = _uiState.value.quickChannels.map { it.copy(isFavorite = favs.contains(it.id)) }
        _uiState.value = _uiState.value.copy(
            quickChannels = updatedChannels
        )
    }

    fun addQuickChannel(
        title: String,
        subtitle: String,
        url: String,
        isWebPlayer: Boolean,
        category: String = "Esportes",
        isWorking: Boolean = true
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
            category = category,
            isWorking = isWorking
        )

        val updatedChannels = repository.addCustomChannel(newChannel)
        if (!isWorking) {
            repository.setChannelWorkingStatus(id, false)
        }
        _uiState.value = _uiState.value.copy(
            quickChannels = repository.getQuickChannels(),
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
        category: String = "Esportes",
        isWorking: Boolean? = null
    ) {
        val cleanUrl = url.trim()
        val cleanTitle = if (title.isNotBlank()) title.trim() else "Canal Rápido"
        val cleanSubtitle = if (subtitle.isNotBlank()) subtitle.trim() else ""

        val existing = _uiState.value.quickChannels.find { it.id == id }
        val targetWorking = isWorking ?: existing?.isWorking ?: true
        val updatedChannel = (existing ?: PlayableVideo(id = id, title = cleanTitle, subtitle = cleanSubtitle, streamUrl = cleanUrl)).copy(
            id = id,
            title = cleanTitle,
            subtitle = cleanSubtitle,
            streamUrl = cleanUrl,
            embedUrl = if (isWebPlayer) cleanUrl else existing?.embedUrl,
            forceWebPlayer = isWebPlayer,
            isLive = true,
            category = category,
            isWorking = targetWorking
        )

        repository.updateQuickChannel(updatedChannel)
        repository.setChannelWorkingStatus(id, targetWorking)
        _uiState.value = _uiState.value.copy(
            quickChannels = repository.getQuickChannels(),
            customCategories = repository.getCustomCategories()
        )
    }

    fun toggleChannelWorkingStatus(channelId: String) {
        val updated = repository.toggleChannelWorkingStatus(channelId)
        _uiState.value = _uiState.value.copy(quickChannels = updated)
        val channel = updated.find { it.id == channelId }
        if (channel != null) {
            val statusEmoji = if (channel.isWorking) "✅" else "🔴"
            val statusName = if (channel.isWorking) "FUNCIONANDO" else "FORA DO AR"
            _uiState.value = _uiState.value.copy(
                adminChannelAlert = "$statusEmoji Status de \"${channel.title}\" alterado para $statusName"
            )
        }
    }

    fun setChannelWorkingStatus(channelId: String, isWorking: Boolean) {
        val updated = repository.setChannelWorkingStatus(channelId, isWorking)
        _uiState.value = _uiState.value.copy(quickChannels = updated)
        val channel = updated.find { it.id == channelId }
        if (channel != null) {
            val statusEmoji = if (isWorking) "✅" else "🔴"
            val statusName = if (isWorking) "FUNCIONANDO" else "FORA DO AR"
            _uiState.value = _uiState.value.copy(
                adminChannelAlert = "$statusEmoji Status de \"${channel.title}\" alterado para $statusName"
            )
        }
    }

    private fun isCurrentUserAdmin(): Boolean {
        val user = _currentUser.value
        return user?.role == "ADMIN" || user?.cpf == "06462555505"
    }

    fun testAllChannels(isAdmin: Boolean = isCurrentUserAdmin()) {
        if (_uiState.value.isTestingChannels) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingChannels = true,
                channelTestProgressText = "Iniciando verificação de canais...",
                adminChannelAlert = null
            )
            try {
                val summary = repository.testAllChannels { index, total, channel, isWorking ->
                    val statusEmoji = if (isWorking) "🟢" else "🔴"
                    _uiState.value = _uiState.value.copy(
                        channelTestProgressText = "Testando ($index/$total): ${channel.title} $statusEmoji"
                    )
                }

                val updatedList = repository.getQuickChannels()
                val alertMsg = buildString {
                    if (summary.offlineCount > 0) {
                        val offlineNames = summary.offlineChannels.take(3).joinToString { it.title }
                        val extra = if (summary.offlineChannels.size > 3) " e +${summary.offlineChannels.size - 3}" else ""
                        append("⚠️ Atenção Admin: ${summary.offlineCount} canais fora do ar ($offlineNames$extra). Admin notificado.")
                    } else {
                        append("✅ Diagnóstico: Todos os ${summary.workingCount} canais estão funcionando normalmente!")
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isTestingChannels = false,
                    channelTestProgressText = null,
                    quickChannels = updatedList,
                    lastChannelTestSummary = summary,
                    adminChannelAlert = alertMsg,
                    autoCorrectionLogs = repository.getAutoCorrectionLogs()
                )

                if (isAdmin && summary.offlineCount > 0) {
                    notificationManager.showAdminChannelAlertNotification(
                        title = "⚠️ Alerta Admin: Canais Fora do Ar",
                        message = "${summary.offlineCount} canais estão inoperantes. Toque para gerenciar.",
                        offlineCount = summary.offlineCount
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingChannels = false,
                    channelTestProgressText = null,
                    adminChannelAlert = "Falha ao testar canais: ${e.message}"
                )
            }
        }
    }

    fun testAllMoviesAndSeries(isAdmin: Boolean = isCurrentUserAdmin()) {
        if (_uiState.value.isTestingMovies) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isTestingMovies = true,
                movieTestProgressText = "Iniciando teste de filmes e séries...",
                adminChannelAlert = null
            )
            try {
                val catalog = repository.getMediaCatalog()
                val moviesRepo = MoviesRepository()
                var operationalCount = 0
                var offlineCount = 0
                val offlineTitles = mutableListOf<String>()

                catalog.forEachIndexed { index, item ->
                    _uiState.value = _uiState.value.copy(
                        movieTestProgressText = "Testando (${index + 1}/${catalog.size}): ${item.title}"
                    )
                    try {
                        val isSeries = item.type == MediaContentType.SERIES
                        val currentStreamUrl = if (!isSeries) {
                            item.movieStreamUrl.orEmpty()
                        } else {
                            item.seasons.firstOrNull()?.episodes?.firstOrNull()?.streamUrl.orEmpty()
                        }

                        // Test current stream only (no auto-search or substitution)
                        val isWorking = if (currentStreamUrl.isNotBlank()) {
                            moviesRepo.testMovieServer(currentStreamUrl)
                        } else {
                            false
                        }

                        if (isWorking) {
                            operationalCount++
                            repository.addOrUpdateMediaItem(item.copy(isWorking = true))
                        } else {
                            offlineCount++
                            offlineTitles.add(item.title)
                            val offlineItem = if (!isSeries) {
                                item.copy(isWorking = false)
                            } else {
                                item.copy(isWorking = false)
                            }
                            repository.addOrUpdateMediaItem(offlineItem)
                            repository.addAutoCorrectionLog(
                                AutoCorrectionLog(
                                    itemType = if (isSeries) "SÉRIE" else "FILME",
                                    title = item.title,
                                    description = "Título testado e detectado fora do ar. Admin notificado para inclusão manual de link.",
                                    status = "Fora do Ar (Requer Atenção do Admin)"
                                )
                            )
                        }
                    } catch (_: Exception) {
                        offlineCount++
                        offlineTitles.add(item.title)
                        val isItemSeries = item.type == MediaContentType.SERIES
                        val offlineItem = if (!isItemSeries) {
                            item.copy(isWorking = false)
                        } else {
                            item.copy(isWorking = false)
                        }
                        repository.addOrUpdateMediaItem(offlineItem)
                    }
                }

                val alertMsg = if (offlineTitles.isNotEmpty()) {
                    "⚠️ Diagnóstico: $operationalCount operacionais e ${offlineTitles.size} fora do ar (${offlineTitles.take(3).joinToString(", ")}${if (offlineTitles.size > 3) " e mais ${offlineTitles.size - 3}" else ""}). Admin notificado."
                } else {
                    "✅ Diagnóstico: Todos os $operationalCount títulos testados estão operacionais!"
                }

                _uiState.value = _uiState.value.copy(
                    isTestingMovies = false,
                    movieTestProgressText = null,
                    adminChannelAlert = alertMsg,
                    autoCorrectionLogs = repository.getAutoCorrectionLogs(),
                    mediaCatalog = repository.getMediaCatalog()
                )

                if (isAdmin && offlineTitles.isNotEmpty()) {
                    notificationManager.showAdminChannelAlertNotification(
                        title = "⚠️ Títulos Fora do Ar Detectados",
                        message = "${offlineTitles.size} filme(s)/série(s) fora do ar (${offlineTitles.take(2).joinToString(", ")}). Requer adição de link manual.",
                        offlineCount = offlineCount
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingMovies = false,
                    movieTestProgressText = null,
                    adminChannelAlert = "Falha ao testar filmes e séries: ${e.message}"
                )
            }
        }
    }

    fun clearCorrectionLogs() {
        repository.clearAutoCorrectionLogs()
        _uiState.value = _uiState.value.copy(autoCorrectionLogs = emptyList())
    }

    fun testSingleChannel(channelId: String, isAdmin: Boolean = isCurrentUserAdmin()) {
        viewModelScope.launch {
            val channel = _uiState.value.quickChannels.find { it.id == channelId } ?: return@launch
            val isWorking = repository.testSingleChannel(channel)

            if (!isWorking) {
                val offlineItem = channel.copy(isWorking = false)
                repository.addCustomChannel(offlineItem)
                repository.addAutoCorrectionLog(
                    AutoCorrectionLog(
                        itemType = "CANAL",
                        title = channel.title,
                        description = "Canal testado individualmente e confirmado fora do ar.",
                        status = "Fora do Ar (Requer Atenção do Admin)"
                    )
                )
            }

            val updated = repository.setChannelWorkingStatus(channelId, isWorking)
            val alertMsg = if (isWorking) {
                "✅ O canal \"${channel.title}\" está funcionando normalmente!"
            } else {
                "⚠️ O canal \"${channel.title}\" foi testado e está FORA DO AR!"
            }

            _uiState.value = _uiState.value.copy(
                quickChannels = updated,
                adminChannelAlert = alertMsg,
                autoCorrectionLogs = repository.getAutoCorrectionLogs()
            )

            if (isAdmin && !isWorking) {
                notificationManager.showAdminChannelAlertNotification(
                    title = "⚠️ Canal Fora do Ar",
                    message = "O canal \"${channel.title}\" não respondeu e foi marcado como Fora do Ar.",
                    offlineCount = 1
                )
            }
        }
    }

    fun reportChannelPlaybackError(channelId: String, isAdmin: Boolean = isCurrentUserAdmin()) {
        viewModelScope.launch {
            val channel = _uiState.value.quickChannels.find { it.id == channelId }
            val updated = repository.setChannelWorkingStatus(channelId, false)
            _uiState.value = _uiState.value.copy(
                quickChannels = updated,
                adminChannelAlert = "⚠️ Erro de reprodução: Canal \"${channel?.title ?: channelId}\" marcado como Fora do Ar."
            )
            if (isAdmin) {
                notificationManager.showAdminChannelAlertNotification(
                    title = "⚠️ Canal Fora do Ar (Falha de Reprodução)",
                    message = "O canal \"${channel?.title ?: channelId}\" apresentou erro ao reproduzir.",
                    offlineCount = 1
                )
            }
        }
    }

    fun dismissAdminChannelAlert() {
        _uiState.value = _uiState.value.copy(adminChannelAlert = null)
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

    // =========================================================================
    // FILMES & SÉRIES MEDIA ACTIONS
    // =========================================================================

    fun addOrUpdateMedia(item: MediaItem) {
        val updated = repository.addOrUpdateMediaItem(item)
        _uiState.value = _uiState.value.copy(mediaCatalog = updated)
    }

    fun deleteMedia(id: String) {
        val updated = repository.deleteMediaItem(id)
        _uiState.value = _uiState.value.copy(mediaCatalog = updated)
    }

    fun toggleMediaFavorite(id: String) {
        val updated = repository.toggleMediaFavorite(id)
        _uiState.value = _uiState.value.copy(mediaCatalog = updated)
    }

    private fun isDirectMedia(url: String): Boolean {
        val l = url.lowercase()
        return (l.contains(".m3u8") || l.contains(".mp4") || l.contains(".ts") || l.contains(".mpd")) &&
               !l.contains(".php") && !l.contains("cxtv.com.br/tv-ao-vivo") && !l.contains("temporariofutemais")
    }

    fun selectMediaItem(media: MediaItem?) {
        _uiState.value = _uiState.value.copy(
            selectedMediaItem = media,
            selectedSeason = media?.seasons?.firstOrNull(),
            selectedEpisode = null,
            hasNextEpisode = false,
            nextEpisodeTitle = null,
            nextSeasonForEpisode = null,
            nextEpisodeItem = null
        )
    }

    fun clearSelectedMedia() {
        _uiState.value = _uiState.value.copy(
            selectedMediaItem = null,
            selectedSeason = null,
            selectedEpisode = null,
            hasNextEpisode = false,
            nextEpisodeTitle = null,
            nextSeasonForEpisode = null,
            nextEpisodeItem = null
        )
    }

    fun selectSeason(season: SeasonItem) {
        _uiState.value = _uiState.value.copy(
            selectedSeason = season
        )
    }

    private fun findNextEpisode(series: MediaItem, season: SeasonItem, episode: EpisodeItem): Pair<SeasonItem, EpisodeItem>? {
        val seasons = series.seasons
        val currentSeasonIndex = seasons.indexOfFirst { it.seasonNumber == season.seasonNumber }.takeIf { it >= 0 } ?: 0
        val currentSeasonObj = seasons.getOrNull(currentSeasonIndex) ?: season
        val epIndex = currentSeasonObj.episodes.indexOfFirst { it.id == episode.id || it.episodeNumber == episode.episodeNumber }

        if (epIndex >= 0 && epIndex + 1 < currentSeasonObj.episodes.size) {
            return Pair(currentSeasonObj, currentSeasonObj.episodes[epIndex + 1])
        }

        // Check next season
        if (currentSeasonIndex + 1 < seasons.size) {
            val nextSeason = seasons[currentSeasonIndex + 1]
            val firstEp = nextSeason.episodes.firstOrNull()
            if (firstEp != null) {
                return Pair(nextSeason, firstEp)
            }
        }
        return null
    }

    fun playMovie(movie: MediaItem) {
        val streamUrl = movie.movieStreamUrl.orEmpty()
        if (streamUrl.isBlank()) return
        val isWeb = movie.isWebPlayer || !isDirectMedia(streamUrl)
        val video = PlayableVideo(
            id = movie.id,
            title = movie.title,
            subtitle = "Filme • ${movie.category} (${movie.year})",
            streamUrl = streamUrl,
            posterUrl = movie.backdropUrl ?: movie.coverUrl,
            isLive = false,
            embedUrl = if (isWeb) streamUrl else null,
            forceWebPlayer = isWeb,
            category = movie.category,
            isFavorite = movie.isFavorite,
            isWorking = movie.isWorking
        )
        _uiState.value = _uiState.value.copy(
            currentTab = NavigationTab.MOVIES_SERIES,
            selectedMediaItem = movie,
            selectedSeason = null,
            selectedEpisode = null,
            hasNextEpisode = false,
            nextEpisodeTitle = null,
            nextSeasonForEpisode = null,
            nextEpisodeItem = null
        )
        playDirectVideo(video)
    }

    fun playEpisode(series: MediaItem, season: SeasonItem, episode: EpisodeItem) {
        val streamUrl = episode.streamUrl
        if (streamUrl.isBlank()) return
        val isWeb = episode.isWebPlayer || series.isWebPlayer || !isDirectMedia(streamUrl)
        val video = PlayableVideo(
            id = episode.id,
            title = "${series.title} - T${season.seasonNumber}:E${episode.episodeNumber}",
            subtitle = episode.title.ifBlank { "Episódio ${episode.episodeNumber}" },
            streamUrl = streamUrl,
            posterUrl = series.backdropUrl ?: series.coverUrl,
            isLive = false,
            embedUrl = if (isWeb) streamUrl else null,
            forceWebPlayer = isWeb,
            category = series.category,
            isFavorite = series.isFavorite,
            isWorking = series.isWorking
        )

        val nextPair = findNextEpisode(series, season, episode)
        val nextEpTitle = nextPair?.let { (nextS, nextE) ->
            "T${nextS.seasonNumber}:E${nextE.episodeNumber} - ${nextE.title.ifBlank { "Episódio ${nextE.episodeNumber}" }}"
        }

        _uiState.value = _uiState.value.copy(
            currentTab = NavigationTab.MOVIES_SERIES,
            selectedMediaItem = series,
            selectedSeason = season,
            selectedEpisode = episode,
            hasNextEpisode = nextPair != null,
            nextEpisodeTitle = nextEpTitle,
            nextSeasonForEpisode = nextPair?.first,
            nextEpisodeItem = nextPair?.second
        )
        playDirectVideo(video)
    }

    fun playNextEpisode() {
        val series = _uiState.value.selectedMediaItem ?: return
        val nextSeason = _uiState.value.nextSeasonForEpisode ?: return
        val nextEpisode = _uiState.value.nextEpisodeItem ?: return

        playEpisode(series, nextSeason, nextEpisode)
    }

    fun onPlayerBack() {
        _uiState.value = _uiState.value.copy(
            currentVideo = null
        )
        _currentScreen.value = UiScreen.Home
    }

    fun castCurrentVideo(streamUrlOverride: String? = null) {
        val video = _uiState.value.currentVideo ?: return
        val effectiveUrl = streamUrlOverride?.takeIf { it.isNotBlank() }
            ?: video.streamUrl.ifBlank { video.embedUrl ?: "" }
        if (effectiveUrl.isBlank()) return
        castManager.castMedia(
            title = video.title,
            subtitle = video.subtitle,
            streamUrl = effectiveUrl,
            posterUrl = video.posterUrl,
            isLive = video.isLive,
            headers = video.headers
        )
    }

    fun toggleCastPlayPause() {
        castManager.togglePlayPause()
    }

    fun seekCast(positionMs: Long) {
        castManager.seekTo(positionMs)
    }

    fun seekCastForward() {
        castManager.seekForward()
    }

    fun seekCastBackward() {
        castManager.seekBackward()
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

    fun login(identifier: String, pass: String, rememberMe: Boolean, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val myDeviceId = getDeviceId()
            val mySessionToken = java.util.UUID.randomUUID().toString()
            val result = userRepository.login(identifier, pass, myDeviceId, mySessionToken)
            result.onSuccess { user ->
                if (user != null) {
                    if (!user.isActive) {
                        onResult(false, "Sua conta está inativa. Contate o administrador.")
                        return@launch
                    }
                    val onlineUser = user.copy(
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        deviceId = myDeviceId,
                        sessionToken = mySessionToken
                    )
                    _currentUser.value = onlineUser
                    startObservingCurrentUser(onlineUser)

                    if (rememberMe) {
                        sharedPrefs.edit().putString("saved_uid", user.uid).apply()
                    } else {
                        sharedPrefs.edit().remove("saved_uid").apply()
                    }
                    _loginNoticeMessage.value = null
                    onResult(true, null)
                } else {
                    onResult(false, "CPF, celular ou senha inválidos.")
                }
            }.onFailure { e ->
                onResult(false, e.localizedMessage ?: "Erro ao realizar login.")
            }
        }
    }

    fun registerUser(
        name: String,
        phone: String,
        cpf: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = userRepository.registerUser(name, phone, cpf, pass)
            result.onSuccess {
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.message ?: "Erro ao criar conta.")
            }
        }
    }

    fun changeCurrentUserPassword(newPassword: String, onResult: (Boolean, String?) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onResult(false, "Usuário não autenticado.")
            return
        }
        val cleanPassword = newPassword.trim()
        if (cleanPassword.isBlank()) {
            onResult(false, "A senha não pode estar em branco.")
            return
        }

        val updatedUser = user.copy(password = cleanPassword)
        viewModelScope.launch {
            val result = userRepository.saveUser(updatedUser)
            result.onSuccess {
                _currentUser.value = updatedUser
                onResult(true, null)
            }.onFailure { e ->
                onResult(false, e.localizedMessage ?: "Erro ao salvar nova senha.")
            }
        }
    }

    fun logout(reason: String? = null) {
        observeUserJob?.cancel()
        observeUserJob = null

        val user = _currentUser.value
        // Se logout foi voluntário pelo usuário (reason == null), remove a presença online.
        // Se ocorreu por conexão em outro dispositivo (reason != null), NÃO sobrescreve a presença do novo dispositivo.
        if (user != null && reason == null) {
            viewModelScope.launch {
                userRepository.updateUserPresence(user.uid, isOnline = false)
            }
        }
        _currentUser.value = null
        sharedPrefs.edit().remove("saved_uid").apply()

        try {
            disconnectCast()
        } catch (_: Exception) {}

        navigateTo(UiScreen.Home)

        if (!reason.isNullOrBlank()) {
            _loginNoticeMessage.value = reason
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(getApplication(), reason, android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun checkCurrentSession() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val myDeviceId = getDeviceId()
            val result = userRepository.getUserFromServer(user.uid)
            result.onSuccess { remoteUser ->
                if (remoteUser != null) {
                    if (!remoteUser.isActive) {
                        logout("Sua conta foi desativada pelo administrador.")
                        return@launch
                    }
                    val remoteDeviceId = remoteUser.deviceId.ifBlank { remoteUser.sessionToken }
                    if (remoteDeviceId.isNotBlank() && remoteDeviceId != myDeviceId) {
                        logout("Sua conta foi conectada em outro dispositivo. Você foi desconectado automaticamente.")
                    }
                }
            }
        }
    }

    fun refreshUserPresence() {
        val user = _currentUser.value
        if (user != null && user.isActive) {
            viewModelScope.launch {
                userRepository.updateUserPresence(user.uid, isOnline = true, deviceId = getDeviceId())
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

    fun updateSupportWhatsappNumber(newNumber: String) {
        val trimmed = newNumber.trim()
        if (trimmed.isNotBlank()) {
            repository.saveSupportWhatsappNumber(trimmed)
            _uiState.value = _uiState.value.copy(supportWhatsappNumber = trimmed)
        }
    }

    fun publishCustomNotification(title: String, message: String) {
        viewModelScope.launch {
            repository.publishCustomNotificationToFirestore(title, message)
        }
    }

    fun setRegistrationEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isRegistrationEnabled = enabled)
        repository.publishRegistrationEnabledToFirestore(enabled)
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
