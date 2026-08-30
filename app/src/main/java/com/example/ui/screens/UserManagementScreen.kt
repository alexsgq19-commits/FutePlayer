package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.User
import com.example.ui.theme.StadiumGreenPrimary
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
    onDeleteUser: (String, (Boolean, String?) -> Unit) -> Unit
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
                    user.name.contains(searchQuery, ignoreCase = true) ||
                    user.cpf.contains(searchQuery, ignoreCase = true) ||
                    user.role.contains(searchQuery, ignoreCase = true)

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
    var name by remember { mutableStateOf(userToEdit?.name ?: "") }
    var cpf by remember { mutableStateOf(userToEdit?.cpf ?: "") }
    var password by remember { mutableStateOf(userToEdit?.password ?: "") }
    var role by remember { mutableStateOf(userToEdit?.role ?: "USER") }
    var isActive by remember { mutableStateOf(userToEdit?.isActive ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (userToEdit == null) "Novo Usuário" else "Editar Usuário") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
                    label = { Text("CPF") },
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

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || cpf.isBlank() || password.isBlank()) {
                        error = "Preencha todos os campos."
                        return@Button
                    }
                    val user = (userToEdit ?: User()).copy(
                        name = name.trim(),
                        cpf = cpf.trim(),
                        password = password.trim(),
                        role = role,
                        isActive = isActive
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
        title = { Text("Mudar Senha de ${user.name}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
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
