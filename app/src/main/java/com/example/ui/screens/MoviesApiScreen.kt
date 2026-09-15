package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.data.models.MovieApiSource
import com.example.data.models.PlayableVideo
import com.example.util.tvFocusable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NeonGreen = Color(0xFF00E676)
private val NeonCyan = Color(0xFF00E5FF)
private val DarkBackground = Color(0xFF0F172A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesApiScreen(
    apiSources: List<MovieApiSource>,
    apiVideos: List<PlayableVideo>,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onAddApiSource: (name: String, url: String, type: String) -> Unit,
    onUpdateApiSource: (MovieApiSource) -> Unit,
    onDeleteApiSource: (id: String) -> Unit,
    onToggleApiSource: (id: String) -> Unit,
    onTestApiUrl: (url: String, type: String, onResult: (Boolean, String, Int) -> Unit) -> Unit,
    onSyncAllApis: () -> Unit,
    onPlayVideo: (PlayableVideo) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var nameInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var selectedApiType by remember { mutableStateOf("JSON / REST") }

    var isTesting by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf<Boolean?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }

    var editingSource by remember { mutableStateOf<MovieApiSource?>(null) }
    var deletingSourceId by remember { mutableStateOf<String?>(null) }

    val categories = remember(apiVideos) {
        val list = mutableListOf("Todos")
        val extracted = apiVideos.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct().sorted()
        list.addAll(extracted)
        list
    }

    val filteredVideos = remember(apiVideos, searchQuery, selectedCategory) {
        apiVideos.filter { video ->
            val matchesQuery = searchQuery.isBlank() ||
                    video.title.contains(searchQuery, ignoreCase = true) ||
                    (video.subtitle.contains(searchQuery, ignoreCase = true)) ||
                    (video.category?.contains(searchQuery, ignoreCase = true) == true)

            val matchesCategory = selectedCategory == "Todos" ||
                    video.category.equals(selectedCategory, ignoreCase = true)

            matchesQuery && matchesCategory
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color(0xFF1E2638), CircleShape)
                        .tvFocusable(CircleShape)
                        .testTag("btn_back_movies_api")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "API de Filmes e Séries",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Adicione e gerencie links de API para importar catálogos de filmes",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = onSyncAllApis,
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .tvFocusable(RoundedCornerShape(12.dp))
                        .testTag("btn_sync_all_apis")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Sincronizar",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSyncing) "Atualizando..." else "Sincronizar APIs",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Form Card: Add New API
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2F)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A344D))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(NeonGreen.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Storage,
                                        contentDescription = null,
                                        tint = NeonGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Adicionar Novo Link da API",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Insira um link de API (JSON, M3U VOD ou Xtream Codes) para importar filmes",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Name Input
                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Nome da API (Ex: Servidor Filmes HD)") },
                                placeholder = { Text("Minha API de Filmes") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_api_name"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonGreen,
                                    unfocusedBorderColor = Color(0xFF2A344D),
                                    focusedLabelColor = NeonGreen,
                                    unfocusedLabelColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // URL Input with Paste Button
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("Link da API de Filmes (http://... ou https://...)") },
                                placeholder = { Text("https://meusite.com/api/filmes.json") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val pasted = clipboardManager.getText()?.text
                                            if (!pasted.isNullOrBlank()) {
                                                urlInput = pasted.trim()
                                                Toast.makeText(context, "Link colado da área de transferência", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentPaste,
                                            contentDescription = "Colar link",
                                            tint = NeonCyan
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_api_url"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = Color(0xFF2A344D),
                                    focusedLabelColor = NeonCyan,
                                    unfocusedLabelColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // API Format Selector
                            Text(
                                text = "Formato da API:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("JSON / REST", "M3U Playlist", "Xtream Codes").forEach { type ->
                                    val isSelected = selectedApiType == type
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedApiType = type },
                                        label = {
                                            Text(
                                                text = type,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonGreen,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFF1E2638),
                                            labelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (urlInput.isBlank()) {
                                            Toast.makeText(context, "Insira um link de API para testar", Toast.LENGTH_SHORT).show()
                                            return@OutlinedButton
                                        }
                                        isTesting = true
                                        testResultText = null
                                        isTestSuccess = null
                                        onTestApiUrl(urlInput, selectedApiType) { success, msg, _ ->
                                            isTesting = false
                                            isTestSuccess = success
                                            testResultText = msg
                                        }
                                    },
                                    enabled = !isTesting,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, NeonCyan),
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusable(RoundedCornerShape(12.dp))
                                        .testTag("btn_test_api")
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = NeonCyan,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Testando...", fontSize = 12.sp, color = NeonCyan)
                                    } else {
                                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Testar Conexão", fontSize = 12.sp, color = NeonCyan)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (urlInput.isBlank()) {
                                            Toast.makeText(context, "Preencha o link da API", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        val name = nameInput.ifBlank { "API Filmes (${selectedApiType})" }
                                        onAddApiSource(name, urlInput, selectedApiType)
                                        nameInput = ""
                                        urlInput = ""
                                        testResultText = null
                                        isTestSuccess = null
                                        Toast.makeText(context, "API adicionada e sincronizada com sucesso!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .tvFocusable(RoundedCornerShape(12.dp))
                                        .testTag("btn_add_api")
                                ) {
                                    Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Adicionar API", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Test Result Banner
                            AnimatedVisibility(visible = testResultText != null) {
                                testResultText?.let { msg ->
                                    val isSuccess = isTestSuccess == true
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSuccess) NeonGreen.copy(alpha = 0.15f) else Color(0xFFFF1744).copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, if (isSuccess) NeonGreen else Color(0xFFFF1744))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isSuccess) Icons.Filled.Check else Icons.Filled.Warning,
                                                contentDescription = null,
                                                tint = if (isSuccess) NeonGreen else Color(0xFFFF5252),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = msg,
                                                fontSize = 12.sp,
                                                color = if (isSuccess) Color.White else Color(0xFFFF8A80),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Configured API List
                item {
                    Text(
                        text = "APIs Cadastradas (${apiSources.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                if (apiSources.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161D2F)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Nenhuma API de filmes cadastrada. Adicione uma API acima para começar.",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(apiSources, key = { it.id }) { source ->
                        val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
                        val lastSyncedStr = if (source.lastSynced > 0) dateFormat.format(Date(source.lastSynced)) else "Nunca"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF182238)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (source.isActive) NeonCyan.copy(alpha = 0.5f) else Color(0xFF2A344D)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (source.isActive) NeonGreen else Color.Gray)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = source.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF2A344D)
                                            ) {
                                                Text(
                                                    text = source.apiType,
                                                    fontSize = 10.sp,
                                                    color = NeonCyan,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = source.apiUrl,
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Switch(
                                        checked = source.isActive,
                                        onCheckedChange = { onToggleApiSource(source.id) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = NeonGreen
                                        ),
                                        modifier = Modifier.tvFocusable(CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📦 ${source.itemCount} itens importados  •  Sincronizado: $lastSyncedStr",
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { editingSource = source },
                                            modifier = Modifier.size(32.dp).tvFocusable(CircleShape)
                                        ) {
                                            Icon(imageVector = Icons.Filled.Edit, contentDescription = "Editar", tint = NeonCyan, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = { deletingSourceId = source.id },
                                            modifier = Modifier.size(32.dp).tvFocusable(CircleShape)
                                        ) {
                                            Icon(imageVector = Icons.Filled.Delete, contentDescription = "Excluir", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                source.lastError?.let { err ->
                                    Text(
                                        text = "⚠️ Última sincronização falhou: $err",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF8A80),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 3: Catalog imported from APIs
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filmes e Séries da API (${filteredVideos.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Search & Filters
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar nos filmes da API...") },
                            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null, tint = NeonCyan) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_search_api_movies"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color(0xFF2A344D),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        if (categories.size > 1) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categories) { cat ->
                                    val isSel = selectedCategory == cat
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonGreen,
                                            selectedLabelColor = Color.Black,
                                            containerColor = Color(0xFF1E2638),
                                            labelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Grid of Movies & Series from API
                if (filteredVideos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (apiVideos.isEmpty()) "Nenhum filme carregado das APIs ativas. Clique em 'Sincronizar APIs'." else "Nenhum filme encontrado para a busca.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 1200.dp)
                        ) {
                            items(filteredVideos, key = { it.id }) { video ->
                                MovieApiPosterItem(video = video, onPlay = { onPlayVideo(video) })
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit API Dialog
    editingSource?.let { source ->
        var editName by remember { mutableStateOf(source.name) }
        var editUrl by remember { mutableStateOf(source.apiUrl) }
        var editType by remember { mutableStateOf(source.apiType) }

        AlertDialog(
            onDismissRequest = { editingSource = null },
            title = { Text("Editar API de Filmes", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nome da API") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("Link URL da API") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateApiSource(source.copy(name = editName.trim(), apiUrl = editUrl.trim(), apiType = editType))
                        editingSource = null
                        Toast.makeText(context, "API atualizada", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black)
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSource = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF161D2F)
        )
    }

    // Delete Confirmation Dialog
    deletingSourceId?.let { id ->
        AlertDialog(
            onDismissRequest = { deletingSourceId = null },
            title = { Text("Excluir API?", color = Color.White) },
            text = { Text("Tem certeza que deseja remover este link de API e seus filmes do aplicativo?", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteApiSource(id)
                        deletingSourceId = null
                        Toast.makeText(context, "API removida", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744), contentColor = Color.White)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSourceId = null }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF161D2F)
        )
    }
}

@Composable
private fun MovieApiPosterItem(
    video: PlayableVideo,
    onPlay: () -> Unit
) {
    val isSeries = video.category?.contains("série", ignoreCase = true) == true ||
            video.subtitle.contains("série", ignoreCase = true)

    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.68f)
            .tvFocusable(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2234)),
        border = BorderStroke(1.dp, Color(0xFF2A3650))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!video.posterUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = video.posterUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF161E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = NeonCyan,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF1F293D), Color(0xFF111726))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = null,
                                tint = NeonCyan.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1F293D), Color(0xFF111726))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = NeonCyan.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Top Badge (Série / Filme)
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
                    .background(
                        color = if (isSeries) Color(0xFF7C4DFF).copy(alpha = 0.9f) else Color(0xFF00B0FF).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isSeries) "SÉRIE" else "FILME",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Bottom Gradient Overlay & Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text = video.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (video.subtitle.isNotBlank() && video.subtitle != video.title) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = video.subtitle,
                            fontSize = 9.sp,
                            color = Color(0xFFB0BEC5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = video.category ?: if (isSeries) "Séries" else "Filmes",
                            fontSize = 9.sp,
                            color = NeonCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(NeonGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Assistir",
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
