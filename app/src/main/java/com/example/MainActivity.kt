package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.notifications.AppNotificationManager
import com.example.ui.MainViewModel
import com.example.ui.UiScreen
import com.example.ui.screens.AccountDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        try {
            java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
            java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
        } catch (_: Exception) {
            // Ignore directory creation failures
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleNotificationIntent(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    var showAccountDialog by remember { mutableStateOf(false) }

                    val currentUser by viewModel.currentUser.collectAsState()
                    val allUsers by viewModel.allUsers.collectAsState()
                    val isLiveNotificationsEnabled by viewModel.isLiveNotificationsEnabled.collectAsState()

                    if (showSplash) {
                        SplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else if (currentUser == null) {
                        com.example.ui.screens.LoginScreen(
                            onLogin = { cpf, pass, rememberMe, cb ->
                                viewModel.login(cpf, pass, rememberMe, cb)
                            }
                        )
                    } else {
                        val currentScreen by viewModel.currentScreen.collectAsState()
                        val uiState by viewModel.uiState.collectAsState()
                        val castUiState by viewModel.castUiState.collectAsState()

                        when (currentScreen) {
                            is UiScreen.Home -> {
                                HomeScreen(
                                    uiState = uiState,
                                    castUiState = castUiState,
                                    currentUser = currentUser,
                                    allUsers = allUsers,
                                    onAccountClick = {
                                        showAccountDialog = true
                                    },
                                    onOpenUserManagement = {
                                        viewModel.navigateTo(UiScreen.UserManagement)
                                    },
                                    onRefresh = { viewModel.loadMatches(isRefresh = true) },
                                    onSearchChange = { viewModel.onSearchQueryChanged(it) },
                                    onChampionshipSelect = { viewModel.onChampionshipSelected(it) },
                                    onTabSelect = { viewModel.onTabSelected(it) },
                                    onSelectMatch = { viewModel.selectMatch(it) },
                                    onDismissMatch = { viewModel.dismissMatchDetails() },
                                    onSelectChannel = { match, channel ->
                                        viewModel.selectChannelAndPlay(match, channel)
                                    },
                                    onPlayDirect = { viewModel.playDirectVideo(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAddQuickChannel = { title, subtitle, url, isWebPlayer, category ->
                                        viewModel.addQuickChannel(title, subtitle, url, isWebPlayer, category)
                                    },
                                    onEditQuickChannel = { id, title, subtitle, url, isWebPlayer, category ->
                                        viewModel.updateQuickChannel(id, title, subtitle, url, isWebPlayer, category)
                                    },
                                    onDeleteQuickChannel = { id ->
                                        viewModel.deleteQuickChannel(id)
                                    },
                                    onResetDefaultChannel = { id ->
                                        viewModel.resetDefaultChannel(id)
                                    },
                                    onCreateCategory = { category ->
                                        viewModel.addChannelCategory(category)
                                    },
                                    onDeleteCategory = { category ->
                                        viewModel.deleteChannelCategory(category)
                                    },
                                    onEditCategory = { oldName, newName ->
                                        viewModel.updateChannelCategory(oldName, newName)
                                    },
                                    onPublishUpdate = { url, version ->
                                        viewModel.publishNewUpdate(url, version)
                                    },
                                    onDownloadUpdate = { url ->
                                        viewModel.downloadAndUpdate(url)
                                    },
                                    onPrepareAndPromptInstall = {
                                        viewModel.prepareAndPromptInstall()
                                    },
                                    onInstallUpdate = { ctx ->
                                        viewModel.installDownloadedUpdate(ctx)
                                    },
                                    onDismissInstallPrompt = {
                                        viewModel.dismissInstallPrompt()
                                    },
                                    onPublishWvcUrl = { url ->
                                        viewModel.publishWvcUrl(url)
                                    },
                                    onDownloadWvc = { url ->
                                        viewModel.downloadWebVideoCaster(url)
                                    },
                                    onInstallWvc = { ctx ->
                                        viewModel.installDownloadedWvc(ctx)
                                    },
                                    onDismissWvcInstallPrompt = {
                                        viewModel.dismissWvcInstallPrompt()
                                    }
                                )
                            }

                            is UiScreen.Player -> {
                                BackHandler {
                                    viewModel.navigateTo(UiScreen.Home)
                                }

                                uiState.currentVideo?.let { video ->
                                    PlayerScreen(
                                        video = video,
                                        castUiState = castUiState,
                                        onBack = { viewModel.navigateTo(UiScreen.Home) },
                                        onCastToggle = { viewModel.toggleCastPlayPause() },
                                        onDisconnectCast = { viewModel.disconnectCast() }
                                    )
                                } ?: run {
                                    viewModel.navigateTo(UiScreen.Home)
                                }
                            }

                            is UiScreen.UserManagement -> {
                                BackHandler {
                                    viewModel.navigateTo(UiScreen.Home)
                                }

                                UserManagementScreen(
                                    users = allUsers,
                                    currentUser = currentUser,
                                    onBack = { viewModel.navigateTo(UiScreen.Home) },
                                    onRefresh = { viewModel.refreshUserPresence() },
                                    onSaveUser = { user, cb -> viewModel.saveUser(user, cb) },
                                    onDeleteUser = { uid, cb -> viewModel.deleteUser(uid, cb) }
                                )
                            }

                            else -> {
                                viewModel.navigateTo(UiScreen.Home)
                            }
                        }

                        if (showAccountDialog && currentUser != null) {
                            AccountDialog(
                                user = currentUser!!,
                                allUsers = allUsers,
                                isLiveNotificationsEnabled = isLiveNotificationsEnabled,
                                onToggleLiveNotifications = { viewModel.toggleLiveNotifications() },
                                onDismiss = { showAccountDialog = false },
                                onOpenUserManagement = {
                                    viewModel.navigateTo(UiScreen.UserManagement)
                                },
                                onLogout = {
                                    viewModel.logout()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val targetType = intent.getStringExtra(AppNotificationManager.EXTRA_TARGET_TYPE)
        val targetId = intent.getStringExtra(AppNotificationManager.EXTRA_TARGET_ID)
        if (targetType != null && targetId != null) {
            viewModel.handleNotificationTarget(targetType, targetId)
        }
    }
}
