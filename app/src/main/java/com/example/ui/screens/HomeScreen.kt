package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.models.User
import com.example.util.SearchUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cast.CastUiState
import com.example.data.models.ChannelOption
import com.example.data.models.EpisodeItem
import com.example.data.models.MatchItem
import com.example.data.models.MediaItem
import com.example.data.models.PlayableVideo
import com.example.data.models.SeasonItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.HomeUiState
import com.example.ui.NavigationTab
import com.example.ui.NetworkStatus
import com.example.ui.components.ChannelSelectorSheet
import com.example.ui.components.ChromecastButton
import com.example.ui.components.MatchCard
import com.example.ui.theme.StadiumAccentRed
import com.example.ui.theme.StadiumCyanSecondary
import com.example.ui.theme.StadiumGreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    castUiState: CastUiState,
    currentUser: User? = null,
    allUsers: List<User> = emptyList(),
    onAccountClick: () -> Unit = {},
    onOpenUserManagement: () -> Unit = {},
    onRefresh: () -> Unit,
    onSearchChange: (String) -> Unit,
    onChampionshipSelect: (String) -> Unit,
    onTabSelect: (NavigationTab) -> Unit,
    onSelectMatch: (MatchItem) -> Unit,
    onDismissMatch: () -> Unit,
    onSelectChannel: (MatchItem, ChannelOption) -> Unit,
    onPlayDirect: (PlayableVideo) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPlayMovie: (MediaItem) -> Unit = {},
    onPlayEpisode: (MediaItem, SeasonItem, EpisodeItem) -> Unit = { _, _, _ -> },
    onAddOrUpdateMedia: (MediaItem) -> Unit = {},
    onDeleteMedia: (String) -> Unit = {},
    onToggleMediaFavorite: (String) -> Unit = {},
    onToggleChannelWorkingStatus: (String) -> Unit = {},
    onAddQuickChannel: (title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String, isWorking: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onEditQuickChannel: (id: String, title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String, isWorking: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteQuickChannel: (id: String) -> Unit = {},
    onResetDefaultChannel: (id: String) -> Unit = {},
    onCreateCategory: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onEditCategory: (oldName: String, newName: String) -> Unit = { _, _ -> },
    onPublishUpdate: (String, String) -> Unit = { _, _ -> },
    onDownloadUpdate: (String) -> Unit = {},
    onInstallUpdate: (Context) -> Unit = {},
    onDismissInstallPrompt: () -> Unit = {},
    onPublishWvcUrl: (String) -> Unit = {},
    onDownloadWvc: (String) -> Unit = {},
    onInstallWvc: (Context) -> Unit = {},
    onDismissWvcInstallPrompt: () -> Unit = {},
    onUploadAndStoreApk: (android.net.Uri, String) -> Unit = { _, _ -> },
    onPrepareAndPromptInstall: () -> Unit = {},
    onUpdateSupportWhatsapp: (String) -> Unit = {},
    onToggleRegistrationEnabled: (Boolean) -> Unit = {},
    onTestAllChannels: () -> Unit = {},
    onTestSingleChannel: (String) -> Unit = {},
    onDismissAdminChannelAlert: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAdmin = currentUser?.role == "ADMIN" || currentUser?.cpf == "06462555505"
    val onlineUsersCount = remember(allUsers) { allUsers.count { it.isCurrentlyOnline() } }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // ==========================================
            // TOP HEADER
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, StadiumGreenPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.futeplayer_app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FUTE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = StadiumGreenPrimary
                            )
                            Text(
                                text = "PLAYER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StadiumCyanSecondary
                            )
                        }
                        Text(
                            text = "Transmissão & Chromecast",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (onlineUsersCount > 0) StadiumGreenPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (onlineUsersCount > 0) StadiumGreenPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onOpenUserManagement() }
                                .testTag("btn_topbar_admin_presence")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (onlineUsersCount > 0) StadiumGreenPrimary else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (onlineUsersCount > 0) "$onlineUsersCount Online" else "Usuários",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (onlineUsersCount > 0) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    IconButton(
                        onClick = onAccountClick,
                        modifier = Modifier.testTag("account_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Conta / Login",
                            tint = if (currentUser != null) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Chromecast Route Button
                    ChromecastButton(
                        castUiState = castUiState
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("refresh_btn")
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
                        val rotationAnim by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "rotation_anim"
                        )
                        val rotation = if (uiState.isRefreshing) rotationAnim else 0f

                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar jogos",
                            tint = StadiumGreenPrimary,
                            modifier = Modifier.graphicsLayer(rotationZ = rotation)
                        )
                    }
                }
            }

            // ==========================================
            // SEARCH & FILTER BAR (COMPACT) - Shown for Matches & Channels
            // ==========================================
            if (uiState.currentTab != NavigationTab.SUPPORT) {
                var isSearchFocused by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .height(38.dp)
                        .testTag("search_input"),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(
                        1.dp,
                        if (isSearchFocused) StadiumGreenPrimary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isSearchFocused) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = when (uiState.currentTab) {
                                        NavigationTab.MATCHES -> "Buscar jogo ou time..."
                                        NavigationTab.CHANNELS -> "Buscar canal..."
                                        NavigationTab.MOVIES_SERIES -> "Buscar filme ou série..."
                                        NavigationTab.SUPPORT -> ""
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = onSearchChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isSearchFocused = it.isFocused },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(StadiumGreenPrimary)
                            )
                        }

                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpar busca",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // ADMIN ONLINE USERS BANNER
            // ==========================================
            if (isAdmin) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenUserManagement() }
                        .testTag("banner_admin_online_users"),
                    color = StadiumGreenPrimary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (onlineUsersCount > 0) StadiumGreenPrimary else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Painel de Usuários Online",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumGreenPrimary
                                )
                                Text(
                                    text = if (onlineUsersCount > 0)
                                        "$onlineUsersCount usuário(s) online agora no app"
                                    else
                                        "Nenhum outro usuário online no momento",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ver Usuários",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StadiumGreenPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.SupervisorAccount,
                                contentDescription = "Ver usuários online",
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // NAVIGATION TABS
            // ==========================================
            ScrollableTabRow(
                selectedTabIndex = uiState.currentTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = StadiumGreenPrimary,
                edgePadding = 16.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.currentTab.ordinal]),
                        color = StadiumGreenPrimary
                    )
                }
            ) {
                Tab(
                    selected = uiState.currentTab == NavigationTab.MATCHES,
                    onClick = { onTabSelect(NavigationTab.MATCHES) },
                    text = {
                        Text(
                            text = "Jogos & Ao Vivo (${uiState.matches.size})",
                            fontWeight = if (uiState.currentTab == NavigationTab.MATCHES) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == NavigationTab.MATCHES) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_matches")
                )

                Tab(
                    selected = uiState.currentTab == NavigationTab.CHANNELS,
                    onClick = { onTabSelect(NavigationTab.CHANNELS) },
                    text = {
                        Text(
                            text = "Canais Rápidos",
                            fontWeight = if (uiState.currentTab == NavigationTab.CHANNELS) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == NavigationTab.CHANNELS) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_channels")
                )

                Tab(
                    selected = uiState.currentTab == NavigationTab.MOVIES_SERIES,
                    onClick = { onTabSelect(NavigationTab.MOVIES_SERIES) },
                    text = {
                        Text(
                            text = "Filmes & Séries (${uiState.mediaCatalog.size})",
                            fontWeight = if (uiState.currentTab == NavigationTab.MOVIES_SERIES) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == NavigationTab.MOVIES_SERIES) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_movies_series")
                )

                Tab(
                    selected = uiState.currentTab == NavigationTab.SUPPORT,
                    onClick = { onTabSelect(NavigationTab.SUPPORT) },
                    text = {
                        Text(
                            text = "Suporte",
                            fontWeight = if (uiState.currentTab == NavigationTab.SUPPORT) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentTab == NavigationTab.SUPPORT) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.testTag("tab_support")
                )
            }

            // Championship filter chips (only on matches tab)
            if (uiState.currentTab == NavigationTab.MATCHES && uiState.availableChampionships.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.availableChampionships) { champ ->
                        val isSelected = uiState.selectedChampionship == champ
                        FilterChip(
                            selected = isSelected,
                            onClick = { onChampionshipSelect(champ) },
                            label = { Text(champ, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StadiumGreenPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = StadiumGreenPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = StadiumGreenPrimary,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // ==========================================
            // MAIN TAB CONTENT
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (uiState.currentTab) {
                    NavigationTab.MATCHES -> {
                        MatchesListContent(
                            uiState = uiState,
                            onSelectMatch = onSelectMatch,
                            onToggleFavorite = onToggleFavorite,
                            onRefresh = onRefresh
                        )
                    }

                    NavigationTab.CHANNELS -> {
                        ChannelsGridContent(
                            channels = uiState.quickChannels,
                            searchQuery = uiState.searchQuery,
                            customCategories = uiState.customCategories,
                            currentUser = currentUser,
                            isTestingChannels = uiState.isTestingChannels,
                            channelTestProgressText = uiState.channelTestProgressText,
                            adminChannelAlert = uiState.adminChannelAlert,
                            onPlayChannel = onPlayDirect,
                            onToggleFavorite = onToggleFavorite,
                            onToggleChannelWorkingStatus = onToggleChannelWorkingStatus,
                            onTestAllChannels = onTestAllChannels,
                            onTestSingleChannel = onTestSingleChannel,
                            onDismissAdminChannelAlert = onDismissAdminChannelAlert,
                            onAddQuickChannel = onAddQuickChannel,
                            onEditQuickChannel = onEditQuickChannel,
                            onDeleteQuickChannel = onDeleteQuickChannel,
                            onResetDefaultChannel = onResetDefaultChannel,
                            onCreateCategory = onCreateCategory,
                            onDeleteCategory = onDeleteCategory,
                            onEditCategory = onEditCategory
                        )
                    }

                    NavigationTab.MOVIES_SERIES -> {
                        MediaScreenContent(
                            mediaList = uiState.mediaCatalog,
                            searchQuery = uiState.searchQuery,
                            isAdmin = isAdmin,
                            onPlayMovie = onPlayMovie,
                            onPlayEpisode = onPlayEpisode,
                            onAddOrUpdateMedia = onAddOrUpdateMedia,
                            onDeleteMedia = onDeleteMedia,
                            onToggleFavorite = onToggleMediaFavorite
                        )
                    }

                    NavigationTab.SUPPORT -> {
                        SupportContent(
                            uiState = uiState,
                            currentUser = currentUser,
                            onPublishUpdate = onPublishUpdate,
                            onDownloadUpdate = onDownloadUpdate,
                            onPrepareAndPromptInstall = onPrepareAndPromptInstall,
                            onInstallUpdate = onInstallUpdate,
                            onDismissInstallPrompt = onDismissInstallPrompt,
                            onPublishWvcUrl = onPublishWvcUrl,
                            onDownloadWvc = onDownloadWvc,
                            onInstallWvc = onInstallWvc,
                            onDismissWvcInstallPrompt = onDismissWvcInstallPrompt,
                            onUpdateSupportWhatsapp = onUpdateSupportWhatsapp,
                            onToggleRegistrationEnabled = onToggleRegistrationEnabled,
                            networkStatus = uiState.networkStatus
                        )
                    }
                }

                if (uiState.showInstallPromptDialog) {
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = onDismissInstallPrompt,
                        title = { Text("Instalar Atualização?") },
                        text = { Text("O download da nova versão (${uiState.latestVersionName}) foi concluído com sucesso. Deseja instalar a atualização agora?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onInstallUpdate(context)
                                    onDismissInstallPrompt()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary, contentColor = Color.Black)
                            ) {
                                Text("Instalar Agora", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = onDismissInstallPrompt) {
                                Text("Mais Tarde")
                            }
                        }
                    )
                }

                if (uiState.showWvcInstallPromptDialog) {
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = onDismissWvcInstallPrompt,
                        title = { Text("Instalar Web Video Caster?") },
                        text = { Text("O download do Web Video Caster foi concluído com sucesso. Deseja instalar o aplicativo agora?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    onInstallWvc(context)
                                    onDismissWvcInstallPrompt()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White)
                            ) {
                                Text("Instalar Agora", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = onDismissWvcInstallPrompt) {
                                Text("Mais Tarde")
                            }
                        }
                    )
                }

                // Loading Overlay
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = StadiumGreenPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buscando partidas em futemais.link/app2/...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Channel Selection BottomSheet
        if (uiState.selectedMatch != null) {
            ChannelSelectorSheet(
                match = uiState.selectedMatch,
                channels = uiState.selectedMatchChannels,
                isLoadingChannels = uiState.isLoadingChannels,
                isCastConnected = castUiState.isConnected,
                onSelectChannel = { channel ->
                    onSelectChannel(uiState.selectedMatch, channel)
                },
                onDismiss = onDismissMatch,
                sheetState = sheetState
            )
        }
    }
}

@Composable
fun MatchesListContent(
    uiState: HomeUiState,
    onSelectMatch: (MatchItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit
) {
    if (uiState.filteredMatches.isEmpty() && !uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SportsSoccer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Nenhuma partida encontrada.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Toque em atualizar para recarregar as transmissões de futemais.link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
                ) {
                    Text("Atualizar Lista", color = Color.Black)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Group by date tags
            val grouped = uiState.filteredMatches.groupBy { it.dateTag }
            grouped.forEach { (dateTag, matches) ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = dateTag.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = StadiumCyanSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                items(matches, key = { it.id }) { match ->
                    MatchCard(
                        match = match,
                        onClick = { onSelectMatch(match) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelsGridContent(
    channels: List<PlayableVideo>,
    searchQuery: String = "",
    customCategories: List<String> = emptyList(),
    currentUser: User? = null,
    isTestingChannels: Boolean = false,
    channelTestProgressText: String? = null,
    adminChannelAlert: String? = null,
    onPlayChannel: (PlayableVideo) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onToggleChannelWorkingStatus: (String) -> Unit = {},
    onTestAllChannels: () -> Unit = {},
    onTestSingleChannel: (String) -> Unit = {},
    onDismissAdminChannelAlert: () -> Unit = {},
    onAddQuickChannel: (title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String, isWorking: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onEditQuickChannel: (id: String, title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String, isWorking: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteQuickChannel: (id: String) -> Unit = {},
    onResetDefaultChannel: (id: String) -> Unit = {},
    onCreateCategory: (category: String) -> Unit = {},
    onDeleteCategory: (category: String) -> Unit = {},
    onEditCategory: (oldName: String, newName: String) -> Unit = { _, _ -> }
) {
    var selectedCategory by remember { mutableStateOf("Todos") }

    val isAdmin = currentUser?.role == "ADMIN" || currentUser?.cpf == "06462555505"

    val initialDefaults = listOf(
        "Esportes",
        "Católicos (CXTV)",
        "Desenhos & Kids",
        "Filmes & Séries",
        "Abertos & Regionais"
    )

    // Build the full categories list dynamically
    val categories = remember(customCategories, channels, isAdmin) {
        val channelCats = channels.mapNotNull { it.category?.trim() }
            .filter { it.isNotBlank() && it != "Personalizados" && it != "Outros" && it != "Todos" && it != "⭐ Favoritos" && !it.contains("Fora do Ar", ignoreCase = true) }
            .distinct()

        val list = mutableListOf<String>()
        list.add("Todos")
        list.add("⭐ Favoritos")
        if (isAdmin) {
            list.add("🔴 Fora do Ar")
        }

        for (dCat in initialDefaults) {
            if (channelCats.any { it.equals(dCat, ignoreCase = true) } || customCategories.any { it.equals(dCat, ignoreCase = true) }) {
                list.add(dCat)
            }
        }

        for (cat in customCategories) {
            if (list.none { it.equals(cat, ignoreCase = true) } && cat != "Personalizados" && cat != "Outros" && !cat.contains("Fora do Ar", ignoreCase = true)) {
                list.add(cat)
            }
        }

        for (cat in channelCats) {
            if (list.none { it.equals(cat, ignoreCase = true) } && cat != "Personalizados" && cat != "Outros" && !cat.contains("Fora do Ar", ignoreCase = true)) {
                list.add(cat)
            }
        }

        list.add("Personalizados")
        list.add("Outros")
        list
    }

    LaunchedEffect(isAdmin) {
        if (!isAdmin && selectedCategory == "🔴 Fora do Ar") {
            selectedCategory = "Todos"
        }
    }

    val categoryOptions = remember(customCategories, channels) {
        val defaultOptions = listOf(
            "Esportes" to "⚽ Esportes",
            "Católicos (CXTV)" to "⛪ Católicos",
            "Desenhos & Kids" to "🎨 Desenhos & Kids",
            "Filmes & Séries" to "🎬 Filmes & Séries",
            "Abertos & Regionais" to "📺 Abertos & Regionais"
        )
        val customOptions = customCategories
            .filter { cat -> defaultOptions.none { it.first.equals(cat, ignoreCase = true) } && !cat.contains("Fora do Ar", ignoreCase = true) }
            .map { it to "🏷️ $it" }

        val extraChannelCats = channels.mapNotNull { it.category?.trim() }
            .filter { cat ->
                cat.isNotBlank() && 
                defaultOptions.none { it.first.equals(cat, ignoreCase = true) } &&
                customOptions.none { it.first.equals(cat, ignoreCase = true) } &&
                cat != "Outros" && cat != "Personalizados" &&
                !cat.contains("Fora do Ar", ignoreCase = true)
            }
            .distinct()
            .map { it to "🏷️ $it" }

        defaultOptions + customOptions + extraChannelCats + listOf("Outros" to "🌐 Outros")
    }

    // Dialog state for creating and managing custom categories
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var categoryFeedbackError by remember { mutableStateOf<String?>(null) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var editCategoryNameInput by remember { mutableStateOf("") }
    var editCategoryError by remember { mutableStateOf<String?>(null) }

    // Dialog state for adding new quick channel
    var showAddDialog by remember { mutableStateOf(false) }
    var channelTitleInput by remember { mutableStateOf("") }
    var channelSubtitleInput by remember { mutableStateOf("") }
    var channelUrlInput by remember { mutableStateOf("") }
    var channelCategoryInput by remember { mutableStateOf("Esportes") }
    var customCategoryInput by remember { mutableStateOf("") }
    var isWebPlayerOption by remember { mutableStateOf(false) }
    var channelIsWorkingInput by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for editing quick channel
    var showEditDialog by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<PlayableVideo?>(null) }
    var editTitleInput by remember { mutableStateOf("") }
    var editSubtitleInput by remember { mutableStateOf("") }
    var editUrlInput by remember { mutableStateOf("") }
    var editCategoryInput by remember { mutableStateOf("Esportes") }
    var editCustomCategoryInput by remember { mutableStateOf("") }
    var editIsWebPlayer by remember { mutableStateOf(false) }
    var editIsWorking by remember { mutableStateOf(true) }
    var editErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for delete confirmation
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<PlayableVideo?>(null) }

    val filteredChannels = remember(channels, selectedCategory, searchQuery, customCategories, isAdmin) {
        val categoryFiltered = when (selectedCategory) {
            "Todos" -> channels
            "⭐ Favoritos" -> channels.filter { it.isFavorite }
            "🔴 Fora do Ar" -> if (isAdmin) channels.filter { !it.isWorking } else channels
            "Personalizados" -> channels.filter { it.id.startsWith("custom_") || it.subtitle.contains("Admin", ignoreCase = true) || it.category.equals("Personalizados", ignoreCase = true) }
            "Esportes" -> channels.filter {
                it.category.equals("Esportes", ignoreCase = true) ||
                it.category?.contains("esporte", ignoreCase = true) == true ||
                it.id.contains("fifa") || 
                it.id.contains("sportv") || 
                it.id.contains("premiere") || 
                it.id.contains("espn") || 
                it.id.contains("caze") || 
                it.id.contains("globo")
            }
            "Católicos (CXTV)" -> channels.filter {
                it.category.equals("Católicos (CXTV)", ignoreCase = true) ||
                it.category?.contains("católic", ignoreCase = true) == true ||
                it.id.contains("cxtv") || 
                it.title.contains("Aparecida", ignoreCase = true) ||
                it.title.contains("Rede Vida", ignoreCase = true) ||
                it.title.contains("Canção Nova", ignoreCase = true) ||
                it.title.contains("Evangelizar", ignoreCase = true) ||
                it.title.contains("Século 21", ignoreCase = true) ||
                it.title.contains("Pai Eterno", ignoreCase = true) ||
                it.title.contains("Nazaré", ignoreCase = true) ||
                it.subtitle.contains("Missa", ignoreCase = true) ||
                it.subtitle.contains("Fé", ignoreCase = true)
            }
            "Desenhos & Kids" -> channels.filter {
                it.category.equals("Desenhos & Kids", ignoreCase = true) ||
                it.category?.contains("kids", ignoreCase = true) == true ||
                it.category?.contains("desenho", ignoreCase = true) == true ||
                it.id.contains("cartoon") || 
                it.id.contains("kids") || 
                it.id.contains("desenho") || 
                it.title.contains("Cartoon", ignoreCase = true) ||
                it.title.contains("Desenho", ignoreCase = true) ||
                it.title.contains("Infantil", ignoreCase = true)
            }
            "Filmes & Séries" -> channels.filter {
                it.category.equals("Filmes & Séries", ignoreCase = true) ||
                it.category?.contains("filme", ignoreCase = true) == true ||
                it.category?.contains("cinema", ignoreCase = true) == true ||
                it.category?.contains("série", ignoreCase = true) == true ||
                it.id.contains("cinema") || 
                it.id.contains("movie") || 
                it.id.contains("sony") || 
                it.title.contains("Cinema", ignoreCase = true) ||
                it.title.contains("Filme", ignoreCase = true)
            }
            "Abertos & Regionais" -> channels.filter {
                it.category.equals("Abertos & Regionais", ignoreCase = true) ||
                it.category?.contains("aberto", ignoreCase = true) == true ||
                it.category?.contains("regional", ignoreCase = true) == true ||
                it.id.contains("brasil") ||
                it.id.contains("megatv") ||
                it.id.contains("sbt") ||
                it.id.contains("band") ||
                it.id.contains("cultura") ||
                it.id.contains("redetv") ||
                it.id.contains("feira")
            }
            "Outros" -> channels.filter {
                val cat = it.category
                cat.isNullOrBlank() || cat.equals("Outros", ignoreCase = true) || (
                    cat !in listOf("Esportes", "Católicos (CXTV)", "Desenhos & Kids", "Filmes & Séries", "Abertos & Regionais") &&
                    customCategories.none { cc -> cc.equals(cat, ignoreCase = true) }
                )
            }
            else -> channels.filter {
                it.category.equals(selectedCategory, ignoreCase = true) ||
                it.category?.contains(selectedCategory, ignoreCase = true) == true
            }
        }

        if (searchQuery.isBlank()) {
            categoryFiltered
        } else {
            val inCategoryMatches = categoryFiltered.filter { ch ->
                SearchUtils.matchesCombined(searchQuery, ch.title, ch.subtitle, ch.category, ch.id)
            }
            if (inCategoryMatches.isEmpty() && selectedCategory != "Todos") {
                channels.filter { ch ->
                    SearchUtils.matchesCombined(searchQuery, ch.title, ch.subtitle, ch.category, ch.id)
                }
            } else {
                inCategoryMatches
            }
        }
    }

    val groupedChannels = remember(filteredChannels, categories, customCategories) {
        val groups = filteredChannels.groupBy { ch ->
            val cat = ch.category?.trim()
            if (!cat.isNullOrBlank()) {
                cat
            } else {
                val id = ch.id.lowercase()
                val title = ch.title.lowercase()
                val sub = ch.subtitle.lowercase()
                when {
                    id.contains("cxtv") || title.contains("aparecida") || title.contains("rede vida") || title.contains("canção nova") || title.contains("evangelizar") || title.contains("século 21") || title.contains("pai eterno") || title.contains("nazaré") || sub.contains("missa") || sub.contains("fé") || sub.contains("oração") -> "Católicos (CXTV)"
                    id.contains("fifa") || id.contains("sportv") || id.contains("premiere") || id.contains("espn") || id.contains("caze") || id.contains("globo") -> "Esportes"
                    id.contains("cartoon") || id.contains("kids") || id.contains("desenho") || title.contains("cartoon") || title.contains("infantil") || sub.contains("infantil") -> "Desenhos & Kids"
                    id.contains("cinema") || id.contains("filme") || id.contains("movie") || id.contains("sony") || title.contains("cinema") || title.contains("filme") -> "Filmes & Séries"
                    id.contains("brasil") || id.contains("megatv") || id.contains("sbt") || id.contains("band") || id.contains("cultura") || id.contains("redetv") || id.contains("feira") -> "Abertos & Regionais"
                    id.startsWith("custom_") -> "Personalizados"
                    else -> "Outros"
                }
            }
        }
        val defaultCategoryOrder = listOf(
            "Esportes",
            "Católicos (CXTV)",
            "Filmes & Séries",
            "Desenhos & Kids",
            "Abertos & Regionais"
        )
        val orderedCategories = (defaultCategoryOrder + customCategories + listOf("Personalizados", "Outros")).distinct()

        groups.entries.sortedWith(
            compareBy<Map.Entry<String, List<PlayableVideo>>> { entry ->
                val idx = orderedCategories.indexOfFirst { it.equals(entry.key, ignoreCase = true) }
                if (idx >= 0) idx else 999
            }.thenBy { it.key }
        ).map { it.key to it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Canais Rápidos & TV Ao Vivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Filmes & Cinema, Católicos (CXTV), Esportes e Abertos • Player Nativo ou Web",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAdmin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                newCategoryInput = ""
                                categoryFeedbackError = null
                                showCreateCategoryDialog = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StadiumGreenPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_manage_categories")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = "Categorias",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Categorias",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                val defaultCat = when (selectedCategory) {
                                    "Todos", "Personalizados", "Outros" -> "Esportes"
                                    else -> selectedCategory
                                }
                                channelTitleInput = ""
                                channelSubtitleInput = ""
                                channelUrlInput = ""
                                channelCategoryInput = defaultCat
                                customCategoryInput = ""
                                isWebPlayerOption = false
                                channelIsWorkingInput = true
                                errorMessage = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StadiumGreenPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_open_add_channel")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Adicionar Canal",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Novo Canal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (isAdmin) {
                val totalCount = channels.size
                val workingCount = channels.count { it.isWorking }
                val offlineCount = totalCount - workingCount

                // Banner de Alerta para o Administrador
                if (adminChannelAlert != null) {
                    val isWarning = adminChannelAlert.contains("⚠️") || adminChannelAlert.contains("Fora do Ar", ignoreCase = true) || adminChannelAlert.contains("Falha", ignoreCase = true)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (isWarning) Color(0xFFFF1744).copy(alpha = 0.16f) else Color(0xFF00E676).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isWarning) Color(0xFFFF1744).copy(alpha = 0.45f) else Color(0xFF00E676).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_channel_alert_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWarning) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isWarning) Color(0xFFFF5252) else Color(0xFF00E676),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isWarning) "Alerta de Transmissão (Admin)" else "Status dos Canais",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWarning) Color(0xFFFF5252) else Color(0xFF00E676)
                                    )
                                    Text(
                                        text = adminChannelAlert,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.5.sp
                                    )
                                    if (isWarning && offlineCount > 0) {
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = "👉 Toque para ver canais fora do ar",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StadiumGreenPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { selectedCategory = "🔴 Fora do Ar" }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = onDismissAdminChannelAlert,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar alerta",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Progresso animado durante o teste automático de canais
                if (isTestingChannels) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = StadiumCyanSecondary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = StadiumCyanSecondary
                            )
                            Text(
                                text = channelTestProgressText ?: "Testando canais automaticamente...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Status dos Canais:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Botão Testar Canais
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = StadiumCyanSecondary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clickable(enabled = !isTestingChannels) {
                                        onTestAllChannels()
                                    }
                                    .testTag("btn_test_all_channels")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = "Testar funcionamento dos canais",
                                        tint = StadiumCyanSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isTestingChannels) "Testando..." else "Testar",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = StadiumCyanSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Filtro Online
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.15f),
                                modifier = Modifier.clickable {
                                    selectedCategory = "Todos"
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676))
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "$workingCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E676),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Filtro Fora do Ar
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (offlineCount > 0) Color(0xFFFF1744).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.clickable {
                                    selectedCategory = if (selectedCategory == "🔴 Fora do Ar") "Todos" else "🔴 Fora do Ar"
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (offlineCount > 0) Color(0xFFFF1744) else Color.Gray)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "$offlineCount Off",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (offlineCount > 0) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }



        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StadiumGreenPrimary,
                            selectedLabelColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                if (isAdmin) {
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                newCategoryInput = ""
                                categoryFeedbackError = null
                                showCreateCategoryDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = StadiumGreenPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "+ Nova Categoria",
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumGreenPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = StadiumGreenPrimary.copy(alpha = 0.1f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("chip_btn_create_category")
                        )
                    }
                }
            }
        }

        if (filteredChannels.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("empty_category_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Nenhum canal encontrado para \"$searchQuery\""
                                selectedCategory == "⭐ Favoritos" -> "Nenhum canal favoritado ainda"
                                else -> "Nenhum canal na categoria \"$selectedCategory\""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "Tente buscar por outro termo ou selecione a aba \"Todos\"."
                                selectedCategory == "⭐ Favoritos" -> "Toque no ícone de coração ♡ em qualquer canal para salvá-lo nos seus favoritos."
                                else -> "Não há canais cadastrados com esta categoria no momento."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val defaultCat = when (selectedCategory) {
                                        "Todos", "Personalizados", "Outros" -> "Esportes"
                                        else -> selectedCategory
                                    }
                                    channelTitleInput = ""
                                    channelSubtitleInput = ""
                                    channelUrlInput = ""
                                    channelCategoryInput = defaultCat
                                    customCategoryInput = ""
                                    isWebPlayerOption = false
                                    errorMessage = null
                                    showAddDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StadiumGreenPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Adicionar Canal nesta Categoria", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        groupedChannels.forEach { (groupName, channelList) ->
            item(key = "header_$groupName") {
                val (icon, bgColors) = when {
                    groupName.contains("esporte", ignoreCase = true) -> "⚽" to listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                    groupName.contains("católic", ignoreCase = true) -> "⛪" to listOf(Color(0xFFFFD54F), Color(0xFFFF9800))
                    groupName.contains("filme", ignoreCase = true) || groupName.contains("série", ignoreCase = true) || groupName.contains("cinema", ignoreCase = true) -> "🎬" to listOf(Color(0xFFBA68C8), Color(0xFF673AB7))
                    groupName.contains("desenho", ignoreCase = true) || groupName.contains("kid", ignoreCase = true) -> "🎨" to listOf(Color(0xFFFF8A65), Color(0xFFFF5252))
                    groupName.contains("aberto", ignoreCase = true) || groupName.contains("regional", ignoreCase = true) -> "📺" to listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
                    groupName.contains("personalizado", ignoreCase = true) -> "⚡" to listOf(StadiumGreenPrimary, StadiumCyanSecondary)
                    groupName.contains("favorito", ignoreCase = true) -> "⭐" to listOf(Color(0xFFFFD700), Color(0xFFFF8C00))
                    groupName.contains("fora do ar", ignoreCase = true) -> "🔴" to listOf(Color(0xFFFF5252), Color(0xFFD50000))
                    else -> "🏷️" to listOf(Color(0xFF26A69A), Color(0xFF00897B))
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(bgColors)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = icon, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = groupName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${channelList.size} ${if (channelList.size == 1) "canal" else "canais"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }

            items(channelList, key = { it.id }) { channel ->
            val isCustom = channel.id.startsWith("custom_")
            val cat = channel.category ?: ""
            val isCatholic = cat == "Católicos (CXTV)" || cat.contains("católic", ignoreCase = true) ||
                             channel.id.contains("cxtv") || 
                             channel.subtitle.contains("Fé", ignoreCase = true) ||
                             channel.subtitle.contains("Missa", ignoreCase = true) ||
                             channel.subtitle.contains("Oração", ignoreCase = true)

            val isCinema = cat == "Filmes & Séries" || cat.contains("cinema", ignoreCase = true) ||
                           channel.id.contains("cinema") || 
                           channel.id.contains("sony") || 
                           channel.title.contains("Cinema", ignoreCase = true)

            val isKids = cat == "Desenhos & Kids" || cat.contains("kids", ignoreCase = true) ||
                         channel.id.contains("cartoon") ||
                         channel.id.contains("kids") ||
                         channel.title.contains("Cartoon", ignoreCase = true)

            val isRegional = cat == "Abertos & Regionais" || cat.contains("regional", ignoreCase = true) ||
                             channel.id.contains("brasil") ||
                             channel.id.contains("band") ||
                             channel.id.contains("sbt") ||
                             channel.id.contains("cultura")

            val isStandardCategory = cat in listOf("Esportes", "Católicos (CXTV)", "Filmes & Séries", "Desenhos & Kids", "Abertos & Regionais")
            val isCustomCategory = cat.isNotBlank() && !isStandardCategory

            val badgeText = when {
                cat.isNotBlank() && cat != "Esportes" -> cat.replace(" (CXTV)", "").uppercase()
                isCustom -> "PERSONALIZADO"
                isCatholic -> "CATÓLICA"
                isCinema -> "CINEMA"
                isKids -> "INFANTIL"
                isRegional -> "ABERTA"
                else -> "AO VIVO"
            }

            val (badgeBg, badgeColor) = when {
                isCatholic -> Color(0xFFFFB300).copy(alpha = 0.2f) to Color(0xFFFFB300)
                isCinema -> Color(0xFFAB47BC).copy(alpha = 0.25f) to Color(0xFFCE93D8)
                isKids -> Color(0xFFFF5252).copy(alpha = 0.25f) to Color(0xFFFF8A80)
                isRegional -> Color(0xFF29B6F6).copy(alpha = 0.25f) to Color(0xFF81D4FA)
                isCustomCategory -> Color(0xFF26A69A).copy(alpha = 0.25f) to Color(0xFF80CBC4)
                isCustom -> StadiumCyanSecondary.copy(alpha = 0.25f) to StadiumCyanSecondary
                else -> StadiumGreenPrimary.copy(alpha = 0.2f) to StadiumGreenPrimary
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .testTag("channel_card_${channel.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header Row with Channel Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayChannel(channel.copy(forceWebPlayer = false)) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Container do ícone com bolinha indicadora de status no canto para Admin
                            Box(
                                modifier = Modifier.size(52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                if (isCustom) listOf(StadiumGreenPrimary, Color(0xFF00E676))
                                                else if (isCatholic) listOf(Color(0xFFFFD54F), Color(0xFFFF9800))
                                                else if (isCinema) listOf(Color(0xFFBA68C8), Color(0xFF673AB7))
                                                else if (isKids) listOf(Color(0xFFFF8A65), Color(0xFFFF5252))
                                                else if (isRegional) listOf(Color(0xFF4FC3F7), Color(0xFF0288D1))
                                                else listOf(StadiumGreenPrimary, StadiumCyanSecondary)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCustom) Icons.Default.Link else if (isCatholic) Icons.Default.LiveTv else if (isCinema) Icons.Default.Movie else Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = if (isCinema || isKids || isRegional) Color.White else Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Bolinha no canto indicando funcionamento do canal (Verde = funcionando, Vermelho = não funcionando) - Visível apenas para ADMIN
                                if (isAdmin) {
                                    val isWorking = channel.isWorking
                                    val dotColor = if (isWorking) Color(0xFF00E676) else Color(0xFFFF1744)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(15.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(2.5.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            .clickable {
                                                onToggleChannelWorkingStatus(channel.id)
                                            }
                                            .testTag("status_dot_${channel.id}")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                val displayCategory = if (cat.isNotBlank()) cat else badgeText

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Surface(
                                        color = badgeBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = displayCategory,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp
                                        )
                                    }

                                    // Badge de status visual abaixo da categoria (Apenas Admin)
                                    if (isAdmin) {
                                        val isWorking = channel.isWorking
                                        Surface(
                                            color = if (isWorking) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFFFF1744).copy(alpha = 0.22f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, if (isWorking) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF1744).copy(alpha = 0.6f)),
                                            modifier = Modifier
                                                .clickable { onToggleChannelWorkingStatus(channel.id) }
                                                .testTag("badge_status_${channel.id}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(7.5.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isWorking) Color(0xFF00E676) else Color(0xFFFF1744))
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = if (isWorking) "Funcionando" else "Fora do Ar",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isWorking) Color(0xFF00E676) else Color(0xFFFF5252),
                                                    fontSize = 9.sp
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Alternar Status",
                                                    tint = if (isWorking) Color(0xFF00E676) else Color(0xFFFF5252),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (channel.subtitle.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = channel.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onToggleFavorite(channel.id) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_favorite_channel_${channel.id}")
                            ) {
                                Icon(
                                    imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (channel.isFavorite) "Desfavoritar Canal" else "Favoritar Canal",
                                    tint = if (channel.isFavorite) StadiumAccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (isAdmin) {
                                IconButton(
                                    onClick = { onToggleChannelWorkingStatus(channel.id) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_toggle_status_${channel.id}")
                                ) {
                                    Icon(
                                        imageVector = if (channel.isWorking) Icons.Default.CheckCircle else Icons.Default.Close,
                                        contentDescription = if (channel.isWorking) "Mudar para Fora do Ar" else "Mudar para Funcionando",
                                        tint = if (channel.isWorking) Color(0xFF00E676) else Color(0xFFFF5252),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onTestSingleChannel(channel.id) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("btn_test_channel_${channel.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCheck,
                                        contentDescription = "Testar canal ${channel.title}",
                                        tint = StadiumCyanSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        editingChannel = channel
                                        editTitleInput = channel.title
                                        editSubtitleInput = channel.subtitle
                                        editUrlInput = if (channel.streamUrl.isNotBlank()) channel.streamUrl else (channel.embedUrl ?: "")
                                        editIsWebPlayer = channel.forceWebPlayer
                                        editIsWorking = channel.isWorking
                                        val knownCategories = listOf("Esportes", "Católicos (CXTV)", "Desenhos & Kids", "Filmes & Séries", "Abertos & Regionais")
                                        val curCategory = channel.category ?: "Esportes"
                                        if (curCategory in knownCategories) {
                                            editCategoryInput = curCategory
                                            editCustomCategoryInput = ""
                                        } else {
                                            editCategoryInput = "Outros"
                                            editCustomCategoryInput = curCategory
                                        }
                                        editErrorMessage = null
                                        showEditDialog = true
                                    },
                                    modifier = Modifier.testTag("btn_edit_channel_${channel.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Editar Canal",
                                        tint = StadiumCyanSecondary
                                    )
                                }
                            }

                            if (isAdmin) {
                                IconButton(
                                    onClick = {
                                        channelToDelete = channel
                                        showDeleteConfirmDialog = true
                                    },
                                    modifier = Modifier.testTag("delete_channel_${channel.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Excluir Canal",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Player Selection Buttons (Nativo vs Web)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayChannel(channel.copy(forceWebPlayer = false)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_native_${channel.id}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCustom) StadiumGreenPrimary
                                                else if (isCatholic) Color(0xFFFFB300)
                                                else if (isCinema) Color(0xFFAB47BC)
                                                else if (isKids) Color(0xFFFF5252)
                                                else StadiumGreenPrimary,
                                contentColor = if (isCinema || isKids) Color.White else Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Player Nativo",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Player Nativo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { onPlayChannel(channel.copy(forceWebPlayer = true)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("btn_web_${channel.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StadiumCyanSecondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Player Web",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Player Web",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Modal Dialog to Add Quick Channel (Admin only)
    if (showAddDialog) {
        val clipboardManager = LocalClipboardManager.current

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAddDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(decorFitsSystemWindows = true)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("dialog_add_channel"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StadiumGreenPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = StadiumGreenPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Adicionar Canal Rápido",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Disponível apenas para ADMIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = StadiumGreenPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Campo: Nome do Canal
                    OutlinedTextField(
                        value = channelTitleInput,
                        onValueChange = { 
                            channelTitleInput = it
                            errorMessage = null
                        },
                        label = { Text("Nome do Canal") },
                        placeholder = { Text("Ex: Premiere 2, SporTV 4K, Canal Anime...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_channel_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumGreenPrimary,
                            focusedLabelColor = StadiumGreenPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Seleção da Categoria do Canal
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Categoria do Canal",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(categoryOptions) { (key, label) ->
                                val isSelected = (channelCategoryInput == key)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { channelCategoryInput = key },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StadiumGreenPrimary,
                                        selectedLabelColor = Color.Black,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("chip_add_category_$key")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        newCategoryInput = ""
                                        categoryFeedbackError = null
                                        showCreateCategoryDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = StadiumGreenPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            "+ Nova Categoria",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StadiumGreenPrimary
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = StadiumGreenPrimary.copy(alpha = 0.12f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("chip_dialog_add_new_category")
                                )
                            }
                        }

                        if (channelCategoryInput == "Outros") {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customCategoryInput,
                                onValueChange = { customCategoryInput = it },
                                label = { Text("Nome da Categoria") },
                                placeholder = { Text("Ex: Notícias, Documentários, Música...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_custom_category"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = StadiumGreenPrimary,
                                    focusedLabelColor = StadiumGreenPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status de Funcionamento do Canal (Abaixo do campo de Categoria)
                    Text(
                        text = "Status do Canal:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (channelIsWorkingInput) Color(0xFF00E676).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (channelIsWorkingInput) Color(0xFF00E676) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { channelIsWorkingInput = true }
                                .testTag("btn_status_working_add")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Funcionando",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (channelIsWorkingInput) FontWeight.Bold else FontWeight.Normal,
                                    color = if (channelIsWorkingInput) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!channelIsWorkingInput) Color(0xFFFF1744).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (!channelIsWorkingInput) Color(0xFFFF1744) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { channelIsWorkingInput = false }
                                .testTag("btn_status_offline_add")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Fora do Ar",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (!channelIsWorkingInput) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!channelIsWorkingInput) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Campo: Descrição / Subtítulo
                    OutlinedTextField(
                        value = channelSubtitleInput,
                        onValueChange = { channelSubtitleInput = it },
                        label = { Text("Descrição / Subtítulo (Opcional)") },
                        placeholder = { Text("Ex: Transmissão Ao Vivo • Full HD") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_channel_subtitle"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumGreenPrimary,
                            focusedLabelColor = StadiumGreenPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Campo: Link do Canal com botão de Colar
                    OutlinedTextField(
                        value = channelUrlInput,
                        onValueChange = { 
                            channelUrlInput = it
                            errorMessage = null
                        },
                        label = { Text("Link da Transmissão (URL / m3u8 / Web)") },
                        placeholder = { Text("https://exemplo.com/stream.m3u8") },
                        singleLine = false,
                        maxLines = 3,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        channelUrlInput = clip.trim()
                                        errorMessage = null
                                    }
                                },
                                modifier = Modifier.testTag("btn_paste_url")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Colar Link",
                                    tint = StadiumGreenPrimary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_channel_url"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumGreenPrimary,
                            focusedLabelColor = StadiumGreenPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Switch para Forçar Player Web
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Abrir no Player Web por padrão",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Recomendado para links de sites ou players externos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        androidx.compose.material3.Switch(
                            checked = isWebPlayerOption,
                            onCheckedChange = { isWebPlayerOption = it },
                            modifier = Modifier.testTag("switch_web_player"),
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = StadiumGreenPrimary,
                                checkedTrackColor = StadiumGreenPrimary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { showAddDialog = false }
                        ) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (channelTitleInput.isBlank()) {
                                    errorMessage = "Por favor, digite o nome do canal."
                                    return@Button
                                }
                                if (channelUrlInput.isBlank()) {
                                    errorMessage = "Por favor, cole ou digite o link da transmissão."
                                    return@Button
                                }
                                if (!channelUrlInput.startsWith("http://") && !channelUrlInput.startsWith("https://") && !channelUrlInput.startsWith("rtmp://")) {
                                    errorMessage = "O link deve começar com http://, https:// ou rtmp://"
                                    return@Button
                                }

                                val finalCategory = if (channelCategoryInput == "Outros") {
                                    if (customCategoryInput.isNotBlank()) customCategoryInput.trim() else "Outros"
                                } else {
                                    channelCategoryInput
                                }

                                if (finalCategory != "Outros" && finalCategory !in listOf("Esportes", "Católicos (CXTV)", "Desenhos & Kids", "Filmes & Séries", "Abertos & Regionais")) {
                                    onCreateCategory(finalCategory)
                                }

                                onAddQuickChannel(
                                    channelTitleInput.trim(),
                                    channelSubtitleInput.trim(),
                                    channelUrlInput.trim(),
                                    isWebPlayerOption,
                                    finalCategory,
                                    channelIsWorkingInput
                                )
                                showAddDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StadiumGreenPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_confirm_add_channel")
                        ) {
                            Text("Salvar Canal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Dialog: Editar Canal Rápido
    if (showEditDialog && editingChannel != null) {
        val targetChannel = editingChannel!!
        val clipboardManager = LocalClipboardManager.current
        val isTargetCustom = targetChannel.id.startsWith("custom_")

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showEditDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(decorFitsSystemWindows = true)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("dialog_edit_channel"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(StadiumCyanSecondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = StadiumCyanSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Editar Canal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTargetCustom) "Canal Personalizado" else "Canal Rápido do Sistema",
                                style = MaterialTheme.typography.labelSmall,
                                color = StadiumCyanSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Campo: Nome do Canal
                    OutlinedTextField(
                        value = editTitleInput,
                        onValueChange = {
                            editTitleInput = it
                            editErrorMessage = null
                        },
                        label = { Text("Nome do Canal") },
                        placeholder = { Text("Ex: Premiere 2, SporTV...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_channel_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumCyanSecondary,
                            focusedLabelColor = StadiumCyanSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Seleção da Categoria do Canal
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Categoria do Canal",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(categoryOptions) { (key, label) ->
                                val isSelected = (editCategoryInput == key)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { editCategoryInput = key },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StadiumCyanSecondary,
                                        selectedLabelColor = Color.Black,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("chip_edit_category_$key")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        newCategoryInput = ""
                                        categoryFeedbackError = null
                                        showCreateCategoryDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = StadiumCyanSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            "+ Nova Categoria",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = StadiumCyanSecondary
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = StadiumCyanSecondary.copy(alpha = 0.12f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("chip_dialog_edit_new_category")
                                )
                            }
                        }

                        if (editCategoryInput == "Outros") {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = editCustomCategoryInput,
                                onValueChange = { editCustomCategoryInput = it },
                                label = { Text("Nome da Categoria") },
                                placeholder = { Text("Ex: Notícias, Documentários, Música...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_edit_custom_category"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = StadiumCyanSecondary,
                                    focusedLabelColor = StadiumCyanSecondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status de Funcionamento do Canal (Abaixo do campo de Categoria)
                    Text(
                        text = "Status de Funcionamento:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (editIsWorking) Color(0xFF00E676).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (editIsWorking) Color(0xFF00E676) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editIsWorking = true }
                                .testTag("btn_status_working_edit")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Funcionando",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (editIsWorking) FontWeight.Bold else FontWeight.Normal,
                                    color = if (editIsWorking) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!editIsWorking) Color(0xFFFF1744).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, if (!editIsWorking) Color(0xFFFF1744) else MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editIsWorking = false }
                                .testTag("btn_status_offline_edit")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Fora do Ar",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (!editIsWorking) FontWeight.Bold else FontWeight.Normal,
                                    color = if (!editIsWorking) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Campo: Descrição / Subtítulo
                    OutlinedTextField(
                        value = editSubtitleInput,
                        onValueChange = { editSubtitleInput = it },
                        label = { Text("Descrição / Subtítulo (Opcional)") },
                        placeholder = { Text("Ex: Transmissão Ao Vivo • Full HD") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_channel_subtitle"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumCyanSecondary,
                            focusedLabelColor = StadiumCyanSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Campo: Link do Canal com botão de Colar
                    OutlinedTextField(
                        value = editUrlInput,
                        onValueChange = {
                            editUrlInput = it
                            editErrorMessage = null
                        },
                        label = { Text("Link da Transmissão (URL / m3u8 / Web)") },
                        placeholder = { Text("https://exemplo.com/stream.m3u8") },
                        singleLine = false,
                        maxLines = 3,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        editUrlInput = clip.trim()
                                        editErrorMessage = null
                                    }
                                },
                                modifier = Modifier.testTag("btn_paste_edit_url")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Colar Link",
                                    tint = StadiumCyanSecondary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_channel_url"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumCyanSecondary,
                            focusedLabelColor = StadiumCyanSecondary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Switch para Forçar Player Web
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Abrir no Player Web por padrão",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Recomendado para páginas web ou transmissões com proteção",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        androidx.compose.material3.Switch(
                            checked = editIsWebPlayer,
                            onCheckedChange = { editIsWebPlayer = it },
                            modifier = Modifier.testTag("switch_edit_web_player"),
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = StadiumCyanSecondary,
                                checkedTrackColor = StadiumCyanSecondary.copy(alpha = 0.4f)
                            )
                        )
                    }

                    if (editErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = editErrorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Se for um canal padrão, permitir restaurar configurações originais
                    if (!isTargetCustom) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                onResetDefaultChannel(targetChannel.id)
                                showEditDialog = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_reset_default_channel"),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Restaurar Configurações Originais",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = { showEditDialog = false }
                        ) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (editTitleInput.isBlank()) {
                                    editErrorMessage = "Por favor, digite o nome do canal."
                                    return@Button
                                }
                                if (editUrlInput.isBlank()) {
                                    editErrorMessage = "Por favor, cole ou digite o link da transmissão."
                                    return@Button
                                }
                                if (!editUrlInput.startsWith("http://") && !editUrlInput.startsWith("https://") && !editUrlInput.startsWith("rtmp://")) {
                                    editErrorMessage = "O link deve começar com http://, https:// ou rtmp://"
                                    return@Button
                                }

                                val finalEditCategory = if (editCategoryInput == "Outros") {
                                    if (editCustomCategoryInput.isNotBlank()) editCustomCategoryInput.trim() else "Outros"
                                } else {
                                    editCategoryInput
                                }

                                if (finalEditCategory != "Outros" && finalEditCategory !in listOf("Esportes", "Católicos (CXTV)", "Desenhos & Kids", "Filmes & Séries", "Abertos & Regionais")) {
                                    onCreateCategory(finalEditCategory)
                                }

                                onEditQuickChannel(
                                    targetChannel.id,
                                    editTitleInput.trim(),
                                    editSubtitleInput.trim(),
                                    editUrlInput.trim(),
                                    editIsWebPlayer,
                                    finalEditCategory,
                                    editIsWorking
                                )
                                showEditDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StadiumGreenPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("btn_save_edit_channel")
                        ) {
                            Text("Salvar Alterações", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Dialog: Confirmação de Exclusão de Canal
    if (showDeleteConfirmDialog && channelToDelete != null) {
        val target = channelToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Excluir Canal") },
            text = {
                Text("Tem certeza de que deseja remover \"${target.title}\" da lista de canais rápidos?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteQuickChannel(target.id)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("btn_confirm_delete_channel")
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog: Gerenciar e Criar Categorias de Canais Rápidos
    if (showCreateCategoryDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(decorFitsSystemWindows = true)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .testTag("dialog_manage_categories"),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(StadiumGreenPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = StadiumGreenPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Criar Categorias",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Canais Rápidos & TV Ao Vivo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { showCreateCategoryDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Fechar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Crie novas categorias para organizar suas transmissões. Elas aparecerão nos filtros superiores e nos diálogos de adicionar canais.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Input nova categoria
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = {
                            newCategoryInput = it
                            categoryFeedbackError = null
                        },
                        label = { Text("Nome da Nova Categoria") },
                        placeholder = { Text("Ex: Notícias 24h, Animes, Podcasts...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = StadiumGreenPrimary
                            )
                        },
                        trailingIcon = {
                            if (newCategoryInput.isNotBlank()) {
                                IconButton(onClick = { newCategoryInput = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_new_category_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumGreenPrimary,
                            focusedLabelColor = StadiumGreenPrimary
                        )
                    )

                    // Sugestões rápidas
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sugestões rápidas:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        val suggestions = listOf(
                            "Notícias", "Música & Shows", "Anime & Geek", "Documentários",
                            "Fé & Religião", "Podcasts", "Variedades", "Gospel", "Internacionais"
                        )
                        items(suggestions) { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    newCategoryInput = suggestion
                                    categoryFeedbackError = null
                                }
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (categoryFeedbackError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = categoryFeedbackError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val trimmed = newCategoryInput.trim()
                            if (trimmed.isBlank()) {
                                categoryFeedbackError = "Por favor, digite o nome da categoria."
                                return@Button
                            }
                            if (categories.any { it.equals(trimmed, ignoreCase = true) }) {
                                categoryFeedbackError = "Esta categoria já existe na lista."
                                return@Button
                            }

                            onCreateCategory(trimmed)
                            selectedCategory = trimmed
                            channelCategoryInput = trimmed
                            editCategoryInput = trimmed
                            newCategoryInput = ""
                            categoryFeedbackError = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("btn_save_new_category"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StadiumGreenPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Criar e Adicionar Categoria", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val allManageableCategories = remember(categories, customCategories, channels) {
                        categories.filter { it != "Todos" && it != "⭐ Favoritos" && it != "Personalizados" && it != "Outros" }
                    }

                    // Lista de todas as categorias existentes
                    Text(
                        text = "Categorias Existentes (${allManageableCategories.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (allManageableCategories.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nenhuma categoria encontrada.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(allManageableCategories) { cat ->
                                val count = channels.count { it.category.equals(cat, ignoreCase = true) }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = null,
                                                tint = StadiumGreenPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = StadiumGreenPrimary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "$count canal(is)",
                                                    fontSize = 10.sp,
                                                    color = StadiumGreenPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    editingCategory = cat
                                                    editCategoryNameInput = cat
                                                    editCategoryError = null
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("btn_edit_category_$cat")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Editar categoria",
                                                    tint = StadiumGreenPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    onDeleteCategory(cat)
                                                    if (selectedCategory == cat) {
                                                        selectedCategory = "Todos"
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("btn_delete_category_$cat")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Excluir categoria",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showCreateCategoryDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Concluir")
                        }
                    }
                }
            }
        }
    }

    // Dialog: Editar Categoria (Admin Only)
    if (editingCategory != null) {
        val catToEdit = editingCategory!!
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Editar Categoria") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editCategoryNameInput,
                        onValueChange = {
                            editCategoryNameInput = it
                            editCategoryError = null
                        },
                        label = { Text("Novo Nome da Categoria") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editCategoryError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = editCategoryError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editCategoryNameInput.trim()
                        if (trimmed.isBlank()) {
                            editCategoryError = "O nome não pode estar vazio."
                            return@Button
                        }
                        if (categories.any { it.equals(trimmed, ignoreCase = true) } && !trimmed.equals(catToEdit, ignoreCase = true)) {
                            editCategoryError = "Esta categoria já existe."
                            return@Button
                        }
                        onEditCategory(catToEdit, trimmed)
                        if (selectedCategory == catToEdit) {
                            selectedCategory = trimmed
                        }
                        editingCategory = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
                ) {
                    Text("Salvar", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) {
                    Text("Cancelar")
                }
            }
        )
    }


}




@Composable
fun SupportContent(
    uiState: HomeUiState,
    currentUser: User?,
    onPublishUpdate: (String, String) -> Unit,
    onDownloadUpdate: (String) -> Unit,
    onPrepareAndPromptInstall: () -> Unit,
    onInstallUpdate: (Context) -> Unit,
    onDismissInstallPrompt: () -> Unit,
    onPublishWvcUrl: (String) -> Unit,
    onDownloadWvc: (String) -> Unit,
    onInstallWvc: (Context) -> Unit,
    onDismissWvcInstallPrompt: () -> Unit,
    onUpdateSupportWhatsapp: (String) -> Unit = {},
    onToggleRegistrationEnabled: (Boolean) -> Unit = {},
    networkStatus: NetworkStatus
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val developerName = "Alex Queiroz"
    val whatsappNumber = uiState.supportWhatsappNumber.ifBlank { "(75) 9 9249-0975" }
    val digitsOnly = whatsappNumber.filter { it.isDigit() }
    val whatsappClean = if (digitsOnly.startsWith("55")) digitsOnly else "55$digitsOnly"

    var showEditWhatsappDialog by remember { mutableStateOf(false) }
    var newWhatsappInput by remember(whatsappNumber) { mutableStateOf(whatsappNumber) }

    val isAdmin = currentUser?.role == "ADMIN" || currentUser?.cpf == "06462555505"
    var versionNameInput by remember(uiState.latestVersionName) { mutableStateOf(uiState.latestVersionName) }
    var apkUrlInput by remember(uiState.latestApkUrl) { mutableStateOf(uiState.latestApkUrl) }

    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy • HH:mm:ss", java.util.Locale("pt", "BR"))
            while (true) {
                currentTimeString = sdf.format(java.util.Date())
                delay(1000L)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("support_content"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Data e Hora em Tempo Real (Visível Apenas para Admin)
        if (isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = StadiumGreenPrimary.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_realtime_clock_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = StadiumGreenPrimary.copy(alpha = 0.2f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Data e Hora em Tempo Real",
                                        tint = StadiumGreenPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Data e Hora (Painel Admin)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumGreenPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (currentTimeString.isNotBlank()) currentTimeString else "Carregando...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StadiumGreenPrimary,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "TEMPO REAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

        }

        // Status de Conexão com a Internet Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (networkStatus.isConnected) StadiumGreenPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Wifi,
                                        contentDescription = "Status da Conexão",
                                        tint = if (networkStatus.isConnected) StadiumGreenPrimary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Status da Conexão",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (networkStatus.isConnected) "Internet Ativa e Conectada" else "Sem Acesso à Internet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (networkStatus.isConnected) StadiumGreenPrimary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (networkStatus.isConnected) StadiumGreenPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = if (networkStatus.isConnected) "ONLINE" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (networkStatus.isConnected) StadiumGreenPrimary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tipo de Rede:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = networkStatus.connectionType,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = StadiumCyanSecondary
                            )
                        }
                    }
                }
            }
        }
        // Atualizar Aplicativo Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = StadiumGreenPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Update,
                                    contentDescription = "Atualização",
                                    tint = StadiumGreenPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Atualizar Aplicativo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Versão Mais Recente: v${uiState.latestVersionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = StadiumGreenPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isAdmin) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Painel do Admin: Link do APK (Google Drive / URL)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumCyanSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = versionNameInput,
                                    onValueChange = { versionNameInput = it },
                                    label = { Text("Número da Versão (ex: 1.1.0)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = apkUrlInput,
                                    onValueChange = { apkUrlInput = it },
                                    label = { Text("Link de Download do APK (Google Drive / Link Direto)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (apkUrlInput.isNotBlank() && versionNameInput.isNotBlank()) {
                                            onPublishUpdate(apkUrlInput, versionNameInput)
                                            Toast.makeText(context, "Nova versão publicada e notificação enviada aos usuários!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Informe a URL do APK e o número da versão", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Publicar Versão & Notificar Usuários", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.updateDownloadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.updateDownloadError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.isDownloadingUpdate) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Baixando atualização no aplicativo...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(uiState.updateDownloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumGreenPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.updateDownloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = StadiumGreenPrimary,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (uiState.latestApkUrl.isNotBlank()) {
                                    onDownloadUpdate(uiState.latestApkUrl)
                                } else if (uiState.hasStoredApk) {
                                    onPrepareAndPromptInstall()
                                } else {
                                    Toast.makeText(context, "Nenhum link de atualização disponível no momento.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StadiumGreenPrimary,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Baixar e Atualizar"
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (uiState.latestApkUrl.isNotBlank()) "Baixar e Instalar v${uiState.latestVersionName}" else "Nenhuma Atualização Disponível",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
        // Baixar Web Video Caster Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E88E5).copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = "Web Video Caster",
                                    tint = Color(0xFF1E88E5)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Web Vídeo Caster",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Aplicativo recomendado para transmissão (Cast)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1E88E5),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isAdmin) {
                        var wvcUrlInput by remember(uiState.webVideoCasterUrl) { mutableStateOf(uiState.webVideoCasterUrl) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Painel do Admin: Link do APK do Web Video Caster",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumCyanSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = wvcUrlInput,
                                    onValueChange = { wvcUrlInput = it },
                                    label = { Text("Link de Download do WVC APK") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (wvcUrlInput.isNotBlank()) {
                                            onPublishWvcUrl(wvcUrlInput)
                                            Toast.makeText(context, "Link do Web Video Caster atualizado!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Informe a URL do APK do WVC", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5), contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Salvar Link do Web Video Caster", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (uiState.wvcDownloadError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.wvcDownloadError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.isDownloadingWvc) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Baixando Web Video Caster...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(uiState.wvcDownloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E88E5)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { uiState.wvcDownloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF1E88E5),
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (uiState.webVideoCasterUrl.isNotBlank()) {
                                    onDownloadWvc(uiState.webVideoCasterUrl)
                                } else {
                                    Toast.makeText(context, "Nenhum link do Web Video Caster disponível.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E88E5),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Baixar WVC"
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Baixar e Instalar Web Video Caster",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
        // Informações do Desenvolvedor Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = StadiumCyanSecondary.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Person,
                                    contentDescription = "Desenvolvedor",
                                    tint = StadiumCyanSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Desenvolvedor do App",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = developerName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // WhatsApp Button Action
                    Button(
                        onClick = {
                            try {
                                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$whatsappClean&text=Ol%C3%A1%20Alex,%20preciso%20de%20suporte%20no%20FutePlayer.")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                clipboardManager.setText(AnnotatedString(whatsappNumber))
                                Toast.makeText(context, "WhatsApp copiado: $whatsappNumber", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StadiumGreenPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Chat,
                            contentDescription = "WhatsApp"
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "WhatsApp: $whatsappNumber",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(whatsappNumber))
                            Toast.makeText(context, "Número copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                            contentDescription = "Copiar Número",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar Número de Contato")
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                newWhatsappInput = whatsappNumber
                                showEditWhatsappDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_edit_support_whatsapp"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StadiumGreenPrimary
                            ),
                            border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Alterar Número do WhatsApp",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Alterar Número do WhatsApp (Admin)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Painel do Admin: Suspender Criação de Contas
        if (isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_suspend_registration_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = StadiumGreenPrimary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Block,
                                            contentDescription = "Suspender Contas",
                                            tint = StadiumGreenPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Controle de Cadastros",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (uiState.isRegistrationEnabled) "Criação de contas permitida" else "Criação de contas suspensa",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (uiState.isRegistrationEnabled) StadiumGreenPrimary else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Suspender criação na Tela de Login",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Impede novos usuários de se cadastrarem pelo aplicativo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = !uiState.isRegistrationEnabled,
                                    onCheckedChange = { suspended ->
                                        val newValue = !suspended
                                        onToggleRegistrationEnabled(newValue)
                                        val statusText = if (suspended) "Criação de contas suspensa com sucesso!" else "Criação de contas reativada!"
                                        Toast.makeText(context, statusText, Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.error,
                                        checkedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                        uncheckedThumbColor = StadiumGreenPrimary,
                                        uncheckedTrackColor = StadiumGreenPrimary.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.testTag("switch_suspend_registration")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ícone Oficial do Aplicativo (Abaixo do Suporte - Visível Apenas para Admin)
        if (isAdmin) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_app_icon_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = StadiumCyanSecondary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AdminPanelSettings,
                                            contentDescription = "Ícone do Aplicativo",
                                            tint = StadiumCyanSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Logomarca Oficial",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Identidade visual do FutePlayer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = StadiumCyanSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = StadiumCyanSecondary.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.4f)),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = "ADMIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = StadiumCyanSecondary,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color(0xFF0D1B2A),
                                            Color(0xFF1B263B)
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = StadiumGreenPrimary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .border(
                                        width = 2.dp,
                                        brush = Brush.linearGradient(
                                            listOf(StadiumGreenPrimary, StadiumCyanSecondary)
                                        ),
                                        shape = RoundedCornerShape(28.dp)
                                    )
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.futeplayer_app_icon),
                                    contentDescription = "Ícone Atual do Aplicativo FutePlayer",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(28.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "FutePlayer",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ícone oficial em uso no aplicativo para launcher, tela inicial, splash screen e notificações.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StadiumGreenPrimary.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "ÍCONE ATIVO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StadiumGreenPrimary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StadiumCyanSecondary.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "ALTA RESOLUÇÃO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = StadiumCyanSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditWhatsappDialog) {
        AlertDialog(
            onDismissRequest = { showEditWhatsappDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = StadiumGreenPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alterar Contato do Suporte")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Informe o novo número do WhatsApp de suporte (com DDD) que será exibido aos usuários.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = newWhatsappInput,
                        onValueChange = { newWhatsappInput = it },
                        label = { Text("Número do WhatsApp") },
                        placeholder = { Text("(75) 9 9249-0975") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_edit_support_whatsapp")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newWhatsappInput.trim()
                        if (trimmed.isNotBlank()) {
                            onUpdateSupportWhatsapp(trimmed)
                            showEditWhatsappDialog = false
                            Toast.makeText(context, "Número de suporte atualizado!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_save_support_whatsapp"),
                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary, contentColor = Color.Black)
                ) {
                    Text("Salvar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditWhatsappDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}