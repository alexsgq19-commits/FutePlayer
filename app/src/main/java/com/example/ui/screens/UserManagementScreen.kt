package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.User
import com.example.ui.theme.StadiumGreenPrimary
import com.example.util.SearchUtils
import kotlinx.coroutines.delay

enum class UserPresenceFilter(val label: String) {
    ALL("Todos"),
    ONLINE("🟢 Online"),
    OFFLINE("⚪ Offline"),
    ACTIVE("Ativos"),
    INACTIVE("Bloqueados")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    users: List<User>,
    currentUser: User?,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    onSaveUser: (User, (Boolean, String?) -> Unit) -> Unit,
    onDeleteUser: (String, (Boolean, String?) -> Unit) -> Unit,
    onOpenPaymentHistory: () -> Unit = {},
    onManualPaymentApproval: ((User) -> Unit)? = null
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedUserForEdit by remember { mutableStateOf<User?>(null) }
    var showPasswordDialog by remember { mutableStateOf<User?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(UserPresenceFilter.ALL) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000L)
            currentTime = System.currentTimeMillis()
        }
    }

    val onlineUsersCount = remember(users, currentTime) { users.count { it.isCurrentlyOnline(currentTime) } }
    val offlineUsersCount = remember(users, onlineUsersCount) { (users.size - onlineUsersCount).coerceAtLeast(0) }

    val filteredUsers = remember(users, searchQuery, selectedFilter, currentTime) {
        users.filter { user ->
            val matchesSearch = searchQuery.isBlank() ||
                    SearchUtils.matchesCombined(searchQuery, user.name, user.cpf, user.phone, user.role)

            val isOnline = user.isCurrentlyOnline(currentTime)
            val matchesFilter = when (selectedFilter) {
                UserPresenceFilter.ALL -> true
                UserPresenceFilter.ONLINE -> isOnline
                UserPresenceFilter.OFFLINE -> !isOnline
                UserPresenceFilter.ACTIVE -> user.isActive
                UserPresenceFilter.INACTIVE -> !user.isActive
            }

            matchesSearch && matchesFilter
        }.sortedWith(
            compareByDescending<User> { it.isCurrentlyOnline(currentTime) }
                .thenByDescending { it.lastSeen }
                .thenBy { it.name }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Gestão de Usuários", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (onlineUsersCount > 0) "$onlineUsersCount online agora • ${users.size} total" else "${users.size} usuários cadastrados",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (onlineUsersCount > 0) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPaymentHistory) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Histórico de Pagamentos", tint = StadiumGreenPrimary)
                    }
                    IconButton(onClick = {
                        onRefresh()
                        snackbarMessage = "Status de presença atualizado!"
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar presença", tint = StadiumGreenPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedUserForEdit = null
                    showAddEditDialog = true
                },
                containerColor = StadiumGreenPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar Usuário")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
            ) {
                // ==========================================
                // 1. CARDS DE RESUMO DE PRESENÇA (DASHBOARD)
                // ==========================================
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card Total
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilter = UserPresenceFilter.ALL },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFilter == UserPresenceFilter.ALL)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (selectedFilter == UserPresenceFilter.ALL)
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            else null
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${users.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Total",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Card Online Agora (Destaque Verde)
                        Card(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { selectedFilter = UserPresenceFilter.ONLINE },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFilter == UserPresenceFilter.ONLINE)
                                    StadiumGreenPrimary.copy(alpha = 0.25f)
                                else StadiumGreenPrimary.copy(alpha = 0.12f)
                            ),
                            border = BorderStroke(
                                if (selectedFilter == UserPresenceFilter.ONLINE) 1.5.dp else 1.dp,
                                StadiumGreenPrimary.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(StadiumGreenPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$onlineUsersCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = StadiumGreenPrimary
                                    )
                                }
                                Text(
                                    text = "Online Agora",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StadiumGreenPrimary
                                )
                            }
                        }

                        // Card Offline
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedFilter = UserPresenceFilter.OFFLINE },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedFilter == UserPresenceFilter.OFFLINE)
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (selectedFilter == UserPresenceFilter.OFFLINE)
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            else null
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$offlineUsersCount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 2. BUSCA
                // ==========================================
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar usuário por nome ou CPF...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StadiumGreenPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ==========================================
                // 3. CHIPS DE FILTRO
                // ==========================================
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(UserPresenceFilter.values()) { filter ->
                            val isSelected = selectedFilter == filter
                            val chipLabel = when (filter) {
                                UserPresenceFilter.ALL -> "Todos (${users.size})"
                                UserPresenceFilter.ONLINE -> "🟢 Online ($onlineUsersCount)"
                                UserPresenceFilter.OFFLINE -> "⚪ Offline ($offlineUsersCount)"
                                UserPresenceFilter.ACTIVE -> "Ativos"
                                UserPresenceFilter.INACTIVE -> "Bloqueados"
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = { Text(chipLabel, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (filter == UserPresenceFilter.ONLINE)
                                        StadiumGreenPrimary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = if (filter == UserPresenceFilter.ONLINE)
                                        StadiumGreenPrimary
                                    else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // ==========================================
                // 4. LISTA DE USUÁRIOS
                // ==========================================
                if (filteredUsers.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Nenhum usuário encontrado para a busca" else "Nenhum usuário neste filtro",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Altere o filtro ou adicione um novo usuário no botão +",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredUsers, key = { it.uid }) { user ->
                        val isOnline = user.isCurrentlyOnline(currentTime)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = if (isOnline) BorderStroke(1.5.dp, StadiumGreenPrimary.copy(alpha = 0.45f)) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header do Card: Avatar, Nome, CPF e Selos
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar com anel e ponto de status
                                    Box(contentAlignment = Alignment.BottomEnd) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isOnline) StadiumGreenPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                            border = if (isOnline) BorderStroke(1.5.dp, StadiumGreenPrimary) else null,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = user.name.take(1).uppercase().ifBlank { "U" },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOnline) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        // Ponto indicador de status
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(if (isOnline) StadiumGreenPrimary else Color.Gray)
                                                .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = user.name.ifBlank { "Sem Nome" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "CPF: ${user.cpf}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (user.phone.isNotBlank()) {
                                            Text(
                                                text = "Cel: ${user.phone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Selos no topo direito
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Selo de Presença (Online / Offline)
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isOnline) StadiumGreenPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isOnline) StadiumGreenPrimary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isOnline) StadiumGreenPrimary else MaterialTheme.colorScheme.outline)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isOnline) "ONLINE" else "OFFLINE",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOnline) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Selo de Perfil (ADMIN / USER)
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (user.role == "ADMIN") StadiumGreenPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        ) {
                                            Text(
                                                text = user.role,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (user.role == "ADMIN") StadiumGreenPrimary else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                // ==========================================
                                // DESTAQUE DE PRESENÇA & ÚLTIMA VEZ ONLINE
                                // ==========================================
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isOnline)
                                        StadiumGreenPrimary.copy(alpha = 0.10f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isOnline) StadiumGreenPrimary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = if (isOnline) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = if (isOnline) "Ativo no aplicativo agora" else "Última vez online: ${user.getFormattedLastSeen(currentTime)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOnline) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isOnline)
                                                    "Conexão ativa em tempo real"
                                                else if (user.lastSeen > 0L)
                                                    "Registrado em: ${user.getFullFormattedLastSeen()}"
                                                else
                                                    "Nenhum registro de conexão recente",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )

                                // Status da Conta (Acesso Liberado / Bloqueado)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Status da Conta",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (user.isActive) "Acesso Liberado" else "Acesso Bloqueado",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (user.isActive) StadiumGreenPrimary else MaterialTheme.colorScheme.error
                                        )
                                    }

                                    val isMasterAdmin = user.cpf == "06462555505"
                                    Switch(
                                        checked = user.isActive,
                                        enabled = !isMasterAdmin,
                                        onCheckedChange = { newStatus ->
                                            val updated = user.copy(isActive = newStatus)
                                            onSaveUser(updated) { success, err ->
                                                snackbarMessage = if (success) {
                                                    if (newStatus) "Usuário '${user.name}' ativado com sucesso!"
                                                    else "Usuário '${user.name}' desativado!"
                                                } else {
                                                    err ?: "Erro ao atualizar status do usuário."
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = StadiumGreenPrimary,
                                            checkedTrackColor = StadiumGreenPrimary.copy(alpha = 0.4f),
                                            uncheckedThumbColor = MaterialTheme.colorScheme.error,
                                            uncheckedTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        )
                                    )
                                }

                                // Status de Cobrança / Assinatura
                                val isExempt = user.isBillingExempt || !user.isBillingEnabled
                                val isExpired = !user.canAccessPremiumContent(currentTime)
                                val isUserAdmin = user.role == "ADMIN"

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isUserAdmin || isExempt) StadiumGreenPrimary.copy(alpha = 0.3f)
                                        else if (isExpired) Color(0xFFFF1744).copy(alpha = 0.5f)
                                        else StadiumGreenPrimary.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Payments,
                                                        contentDescription = null,
                                                        tint = if (isUserAdmin) StadiumGreenPrimary
                                                        else if (isExempt) Color(0xFF64B5F6)
                                                        else if (isExpired) Color(0xFFFF1744)
                                                        else StadiumGreenPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isUserAdmin) "Assinatura: Admin (Permanente)"
                                                        else if (isExempt) "Assinatura: Isento de Cobrança"
                                                        else if (isExpired) "Assinatura: VENCIDA"
                                                        else "Assinatura: ATIVA (Em dia)",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isUserAdmin) StadiumGreenPrimary
                                                        else if (isExempt) Color(0xFF64B5F6)
                                                        else if (isExpired) Color(0xFFFF1744)
                                                        else StadiumGreenPrimary
                                                    )
                                                }

                                                if (!isUserAdmin && !isExempt) {
                                                    Text(
                                                        text = "Vencimento: ${user.getFormattedExpirationDate()} • R$ 10,00/30d",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (user.lastPaymentAt > 0L || user.lastPaymentDate > 0L) {
                                                        val lastPay = if (user.lastPaymentAt > 0L) user.lastPaymentAt else user.lastPaymentDate
                                                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("pt", "BR"))
                                                        Text(
                                                            text = "Último pagto: ${sdf.format(java.util.Date(lastPay))}${if (user.lastPaymentId.isNotBlank()) " (ID: ${user.lastPaymentId.takeLast(8)})" else ""}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                        )
                                                    }
                                                }
                                            }

                                            // Se for usuário normal (não admin), permite alternar Isenção diretamente
                                            if (!isUserAdmin) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = if (user.isBillingExempt) "Isento (SIM)" else "Isentar?",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = if (user.isBillingExempt) Color(0xFF64B5F6) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Switch(
                                                        checked = user.isBillingExempt,
                                                        onCheckedChange = { exempt ->
                                                            val updated = user.copy(
                                                                isBillingExempt = exempt,
                                                                isBillingEnabled = !exempt
                                                            )
                                                            onSaveUser(updated) { success, err ->
                                                                snackbarMessage = if (success) {
                                                                    if (exempt) "Usuário '${user.name}' marcado como ISENTO!"
                                                                    else "Isenção desativada para '${user.name}'."
                                                                } else {
                                                                    err ?: "Erro ao atualizar isenção."
                                                                }
                                                            }
                                                        },
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = Color(0xFF64B5F6),
                                                            checkedTrackColor = Color(0xFF64B5F6).copy(alpha = 0.4f)
                                                        ),
                                                        modifier = Modifier.scale(0.75f)
                                                    )
                                                }
                                            }
                                        }

                                        // Botão para o Admin Aprovar Pagamento Manual (+30 dias)
                                        if (!isUserAdmin && !isExempt && onManualPaymentApproval != null) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Aprovação Manual (PIX/Dinheiro):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                OutlinedButton(
                                                    onClick = { onManualPaymentApproval(user) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Default.AddTask, contentDescription = null, tint = StadiumGreenPrimary, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Aprovar +30d", fontSize = 11.sp, color = StadiumGreenPrimary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Botões de Ações (Senha, Editar, Excluir)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { showPasswordDialog = user }) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Senha")
                                    }

                                    TextButton(
                                        onClick = {
                                            selectedUserForEdit = user
                                            showAddEditDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Editar")
                                    }

                                    if (user.cpf != "06462555505") {
                                        TextButton(
                                            onClick = {
                                                onDeleteUser(user.uid) { success, err ->
                                                    snackbarMessage = if (success) "Usuário excluído com sucesso." else (err ?: "Erro ao excluir.")
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Excluir")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditUserDialog(
            userToEdit = selectedUserForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { user ->
                onSaveUser(user) { success, err ->
                    if (success) {
                        showAddEditDialog = false
                        snackbarMessage = "Usuário salvo com sucesso!"
                    } else {
                        snackbarMessage = err ?: "Erro ao salvar usuário."
                    }
                }
            }
        )
    }

    if (showPasswordDialog != null) {
        ChangePasswordDialog(
            user = showPasswordDialog!!,
            onDismiss = { showPasswordDialog = null },
            onSave = { newPass ->
                val updated = showPasswordDialog!!.copy(password = newPass)
                onSaveUser(updated) { success, err ->
                    if (success) {
                        showPasswordDialog = null
                        snackbarMessage = "Senha alterada com sucesso!"
                    } else {
                        snackbarMessage = err ?: "Erro ao alterar senha."
                    }
                }
            }
        )
    }
}

@Composable
fun AddEditUserDialog(
    userToEdit: User?,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")) }

    var name by remember { mutableStateOf(userToEdit?.name ?: "") }
    var cpf by remember { mutableStateOf(userToEdit?.cpf ?: "") }
    var phone by remember { mutableStateOf(userToEdit?.phone ?: "") }
    var password by remember { mutableStateOf(userToEdit?.password ?: "") }
    var role by remember { mutableStateOf(userToEdit?.role ?: "USER") }
    var isActive by remember { mutableStateOf(userToEdit?.isActive ?: true) }
    
    var isBillingEnabled by remember { mutableStateOf(userToEdit?.isBillingEnabled ?: true) }
    var expirationDate by remember { mutableStateOf(userToEdit?.getEffectiveExpirationDate() ?: (System.currentTimeMillis() + 30L * 24L * 3600L * 1000L)) }
    var expirationDateStr by remember { mutableStateOf(dateFormat.format(java.util.Date(expirationDate))) }

    val initialFee = userToEdit?.monthlyFee ?: 10.0
    var monthlyFeeStr by remember { 
        mutableStateOf(if (initialFee % 1.0 == 0.0) initialFee.toInt().toString() else String.format(java.util.Locale.US, "%.2f", initialFee)) 
    }

    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .imePadding(),
        title = { Text(if (userToEdit == null) "Novo Usuário" else "Editar Usuário") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cpf,
                    onValueChange = { cpf = it },
                    label = { Text("CPF (Opcional)") },
                    placeholder = { Text("000.000.000-00") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de Celular / WhatsApp") },
                    placeholder = { Text("Ex: (75) 99249-0975") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Função (Role):")
                    TextButton(
                        onClick = { role = if (role == "ADMIN") "USER" else "ADMIN" },
                        colors = ButtonDefaults.textButtonColors(contentColor = if (role == "ADMIN") StadiumGreenPrimary else MaterialTheme.colorScheme.primary)
                    ) {
                        Text(role, fontWeight = FontWeight.Bold)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Usuário Ativo:")
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = StadiumGreenPrimary, checkedTrackColor = StadiumGreenPrimary.copy(alpha = 0.5f))
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Seção Cobrança Mensal
                Text("Cobrança Mensal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StadiumGreenPrimary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cobrar Usuário:", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isBillingEnabled) "Mensalidade no dia do cadastro" else "Isento de cobrança",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBillingEnabled) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBillingEnabled,
                        onCheckedChange = { isBillingEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = StadiumGreenPrimary, checkedTrackColor = StadiumGreenPrimary.copy(alpha = 0.5f))
                    )
                }

                AnimatedVisibility(visible = isBillingEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Edição Direta da Data de Vencimento
                        OutlinedTextField(
                            value = expirationDateStr,
                            onValueChange = { input ->
                                expirationDateStr = input
                                try {
                                    val trimmed = input.trim()
                                    if (trimmed.length == 10) {
                                        val parsed = dateFormat.parse(trimmed)
                                        if (parsed != null) {
                                            expirationDate = parsed.time
                                        }
                                    }
                                } catch (_: Exception) {}
                            },
                            label = { Text("Data de Vencimento (DD/MM/AAAA)") },
                            placeholder = { Text("Ex: 25/10/2026") },
                            leadingIcon = {
                                Icon(Icons.Default.Event, contentDescription = null, tint = StadiumGreenPrimary)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Ajuste Rápido
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Atalhos para Ajuste de Data:", style = MaterialTheme.typography.labelSmall)

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            expirationDate += (30L * 24 * 3600 * 1000L)
                                            expirationDateStr = dateFormat.format(java.util.Date(expirationDate))
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        Text("+30d", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            expirationDate += (15L * 24 * 3600 * 1000L)
                                            expirationDateStr = dateFormat.format(java.util.Date(expirationDate))
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        Text("+15d", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            expirationDate = System.currentTimeMillis() - 1000L
                                            expirationDateStr = dateFormat.format(java.util.Date(expirationDate))
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        Text("Vencido", fontSize = 11.sp, color = Color.Red)
                                    }
                                }
                            }
                        }

                        // Edição do Valor de Mensalidade
                        OutlinedTextField(
                            value = monthlyFeeStr,
                            onValueChange = { monthlyFeeStr = it },
                            label = { Text("Valor Mensalidade (R$)") },
                            placeholder = { Text("Ex: 10.00 ou 15,00") },
                            leadingIcon = {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = StadiumGreenPrimary)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || password.isBlank() || (cpf.isBlank() && phone.isBlank())) {
                        error = "Preencha Nome, Senha e pelo menos Celular ou CPF."
                        return@Button
                    }
                    val cleanFee = monthlyFeeStr.replace(",", ".").trim()
                    val parsedFee = cleanFee.toDoubleOrNull() ?: 10.0
                    val user = (userToEdit ?: User()).copy(
                        name = name.trim(),
                        cpf = cpf.trim(),
                        phone = phone.trim(),
                        password = password.trim(),
                        role = role,
                        isActive = isActive,
                        isBillingEnabled = isBillingEnabled,
                        isBillingExempt = !isBillingEnabled,
                        expirationDate = expirationDate,
                        subscriptionExpiresAt = expirationDate,
                        monthlyFee = parsedFee
                    )
                    onSave(user)
                },
                colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ChangePasswordDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .imePadding(),
        title = { Text("Mudar Senha de ${user.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nova Senha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.isBlank()) {
                        error = "Digite a nova senha."
                        return@Button
                    }
                    onSave(newPassword.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
            ) {
                Text("Alterar Senha")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
