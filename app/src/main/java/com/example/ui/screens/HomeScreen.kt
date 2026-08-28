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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.InsertDriveFile
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.models.MatchItem
import com.example.data.models.PlayableVideo
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
    onAddQuickChannel: (title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String) -> Unit = { _, _, _, _, _ -> },
    onEditQuickChannel: (id: String, title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteQuickChannel: (id: String) -> Unit = {},
    onResetDefaultChannel: (id: String) -> Unit = {},
    onCreateCategory: (String) -> Unit = {},
    onDeleteCategory: (String) -> Unit = {},
    onPublishUpdate: (String, String) -> Unit = { _, _ -> },
    onDownloadUpdate: (String) -> Unit = {},
    onInstallUpdate: (Context) -> Unit = {},
    onDismissInstallPrompt: () -> Unit = {},
    onUploadAndStoreApk: (android.net.Uri, String) -> Unit = { _, _ -> },
    onPrepareAndPromptInstall: () -> Unit = {},
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
            // SEARCH & FILTER BAR
            // ==========================================
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Buscar time, jogo ou campeonato...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar busca",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StadiumGreenPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_input")
            )

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
                            customCategories = uiState.customCategories,
                            currentUser = currentUser,
                            onPlayChannel = onPlayDirect,
                            onAddQuickChannel = onAddQuickChannel,
                            onEditQuickChannel = onEditQuickChannel,
                            onDeleteQuickChannel = onDeleteQuickChannel,
                            onResetDefaultChannel = onResetDefaultChannel,
                            onCreateCategory = onCreateCategory,
                            onDeleteCategory = onDeleteCategory
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
                        onClick = { onSelectMatch(match) },
                        onFavoriteClick = { onToggleFavorite(match.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelsGridContent(
    channels: List<PlayableVideo>,
    customCategories: List<String> = emptyList(),
    currentUser: User? = null,
    onPlayChannel: (PlayableVideo) -> Unit,
    onAddQuickChannel: (title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String) -> Unit = { _, _, _, _, _ -> },
    onEditQuickChannel: (id: String, title: String, subtitle: String, url: String, isWebPlayer: Boolean, category: String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteQuickChannel: (id: String) -> Unit = {},
    onResetDefaultChannel: (id: String) -> Unit = {},
    onCreateCategory: (category: String) -> Unit = {},
    onDeleteCategory: (category: String) -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf("Todos") }

    val defaultCategories = listOf(
        "Todos",
        "Esportes",
        "Católicos (CXTV)",
        "Desenhos & Kids",
        "Filmes & Séries",
        "Abertos & Regionais"
    )

    // Build the full categories list dynamically
    val categories = remember(customCategories, channels) {
        val extraChannelCategories = channels.mapNotNull { it.category?.trim() }
            .filter { it.isNotBlank() && it !in defaultCategories && it !in customCategories && it != "Personalizados" && it != "Outros" }
            .distinct()

        val list = mutableListOf<String>()
        list.addAll(defaultCategories)
        list.addAll(customCategories.filter { it !in list })
        list.addAll(extraChannelCategories.filter { it !in list })
        list.add("Personalizados")
        list.add("Outros")
        list
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
            .filter { cat -> defaultOptions.none { it.first.equals(cat, ignoreCase = true) } }
            .map { it to "🏷️ $it" }

        val extraChannelCats = channels.mapNotNull { it.category?.trim() }
            .filter { cat ->
                cat.isNotBlank() && 
                defaultOptions.none { it.first.equals(cat, ignoreCase = true) } &&
                customOptions.none { it.first.equals(cat, ignoreCase = true) } &&
                cat != "Outros" && cat != "Personalizados"
            }
            .distinct()
            .map { it to "🏷️ $it" }

        defaultOptions + customOptions + extraChannelCats + listOf("Outros" to "🌐 Outros")
    }

    val isAdmin = currentUser?.role == "ADMIN" || currentUser?.cpf == "06462555505"

    // Dialog state for creating and managing custom categories
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var categoryFeedbackError by remember { mutableStateOf<String?>(null) }

    // Dialog state for adding new quick channel
    var showAddDialog by remember { mutableStateOf(false) }
    var channelTitleInput by remember { mutableStateOf("") }
    var channelSubtitleInput by remember { mutableStateOf("") }
    var channelUrlInput by remember { mutableStateOf("") }
    var channelCategoryInput by remember { mutableStateOf("Esportes") }
    var customCategoryInput by remember { mutableStateOf("") }
    var isWebPlayerOption by remember { mutableStateOf(false) }
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
    var editErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog state for delete confirmation
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var channelToDelete by remember { mutableStateOf<PlayableVideo?>(null) }

    val filteredChannels = remember(channels, selectedCategory, customCategories) {
        when (selectedCategory) {
            "Todos" -> channels
            "Personalizados" -> channels.filter { it.id.startsWith("custom_") }
            "Esportes" -> channels.filter {
                it.category == "Esportes" ||
                it.category?.contains("esporte", ignoreCase = true) == true ||
                it.id.contains("fifa") || 
                it.id.contains("sportv") || 
                it.id.contains("premiere") || 
                it.id.contains("espn") || 
                it.id.contains("caze") || 
                it.id.contains("globo")
            }
            "Católicos (CXTV)" -> channels.filter {
                it.category == "Católicos (CXTV)" ||
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
                it.category == "Desenhos & Kids" ||
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
                it.category == "Filmes & Séries" ||
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
                it.category == "Abertos & Regionais" ||
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
                cat != null && cat !in listOf("Esportes", "Católicos (CXTV)", "Desenhos & Kids", "Filmes & Séries", "Abertos & Regionais") && cat !in customCategories
            }
            else -> channels.filter {
                it.category.equals(selectedCategory, ignoreCase = true) ||
                it.category?.contains(selectedCategory, ignoreCase = true) == true
            }
        }
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
        }

        // Seção em destaque de Adicionar Canal Rápido para ADMIN
        if (isAdmin) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_add_channel_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = StadiumGreenPrimary.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sessão Administrador: Canais & Categorias",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StadiumGreenPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Gerencie transmissões ao vivo e crie categorias personalizadas para organizar todos os canais.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(44.dp)
                                    .testTag("btn_quick_add_channel_banner"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StadiumGreenPrimary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Adicionar Canal",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    newCategoryInput = ""
                                    categoryFeedbackError = null
                                    showCreateCategoryDialog = true
                                },
                                modifier = Modifier
                                    .weight(1.1f)
                                    .height(44.dp)
                                    .testTag("btn_quick_create_category_banner"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = StadiumGreenPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+ Categoria",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
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
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Nenhum canal na categoria \"$selectedCategory\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Não há canais cadastrados com esta categoria no momento.",
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

        items(filteredChannels, key = { it.id }) { channel ->
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
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
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
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = badgeBg,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = badgeColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                                Text(
                                    text = channel.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isAdmin) {
                                IconButton(
                                    onClick = {
                                        editingChannel = channel
                                        editTitleInput = channel.title
                                        editSubtitleInput = channel.subtitle
                                        editUrlInput = if (channel.streamUrl.isNotBlank()) channel.streamUrl else (channel.embedUrl ?: "")
                                        editIsWebPlayer = channel.forceWebPlayer
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

    // Modal Dialog to Add Quick Channel (Admin only)
    if (showAddDialog) {
        val clipboardManager = LocalClipboardManager.current

        androidx.compose.ui.window.Dialog(onDismissRequest = { showAddDialog = false }) {
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
                                    finalCategory
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

        androidx.compose.ui.window.Dialog(onDismissRequest = { showEditDialog = false }) {
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
                                    finalEditCategory
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
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCreateCategoryDialog = false }) {
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

                    // Lista de categorias customizadas criadas
                    Text(
                        text = "Categorias Criadas (${customCategories.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (customCategories.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Nenhuma categoria personalizada criada ainda.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(customCategories) { cat ->
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
    networkStatus: NetworkStatus
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val developerName = "Alex Queiroz"
    val whatsappNumber = "(75) 9 9249-0975"
    val whatsappClean = "5575992490975"

    val isAdmin = currentUser?.role == "ADMIN" || currentUser?.cpf == "06462555505"
    var versionNameInput by remember { mutableStateOf(uiState.latestVersionName) }
    var apkUrlInput by remember { mutableStateOf(uiState.latestApkUrl) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("support_content"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                }
            }
        }
    }
}
