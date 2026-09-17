package com.example.ui.screens

import android.widget.Toast
import com.example.util.tvFocusable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.models.EpisodeItem
import com.example.data.models.MediaContentType
import com.example.data.models.MediaItem
import com.example.data.models.SeasonItem
import com.example.util.SearchUtils

private val NeonGreen = Color(0xFF00E676)
private val NeonCyan = Color(0xFF00E5FF)
private val NeonPurple = Color(0xFFD500F9)
private val NeonPink = Color(0xFFFF4081)
private val NeonGold = Color(0xFFFFD700)
private val DarkBg = Color(0xFF0F172A)
private val DarkCardBg = Color(0xFF1E293B)
private val SurfaceDark = Color(0xFF131E30)

@Composable
private fun customTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
    focusedLabelColor = NeonCyan,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
    cursorColor = NeonCyan,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = SurfaceDark,
    unfocusedContainerColor = SurfaceDark
)

@Composable
fun MediaScreenContent(
    mediaList: List<MediaItem>,
    searchQuery: String,
    isAdmin: Boolean,
    selectedMediaItem: MediaItem? = null,
    selectedSeason: SeasonItem? = null,
    selectedEpisode: EpisodeItem? = null,
    onSelectMedia: (MediaItem?) -> Unit = {},
    onDismissMedia: () -> Unit = {},
    onPlayMovie: (MediaItem) -> Unit,
    onPlayEpisode: (MediaItem, SeasonItem, EpisodeItem) -> Unit,
    onAddOrUpdateMedia: (MediaItem) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    isTestingMovies: Boolean = false,
    movieTestProgressText: String? = null,
    onTestAllMovies: () -> Unit = {},
    onOpenMoviesApi: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTypeFilter by remember { mutableStateOf("Todos") } // "Todos", "Filmes", "Séries", "Favoritos"
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    var mediaToEdit by remember { mutableStateOf<MediaItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var mediaToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMediaForDetail by remember { mutableStateOf<MediaItem?>(null) }

    val activeMedia = selectedMediaItem ?: selectedMediaForDetail

    // Categories extraction
    val allCategories = remember(mediaList) {
        val cats = mediaList.flatMap { item ->
            item.category.split(",", "/", "•", "|").map { it.trim() }.filter { it.isNotBlank() }
        }.distinct().sorted()
        listOf("Todos") + cats
    }

    // Filter list
    val filteredMedia = remember(mediaList, searchQuery, selectedTypeFilter, selectedCategoryFilter) {
        mediaList.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    SearchUtils.matchesCombined(
                        searchQuery,
                        item.title,
                        item.category,
                        item.synopsis,
                        item.year
                    )

            val matchesType = when (selectedTypeFilter) {
                "Filmes" -> item.type == MediaContentType.MOVIE
                "Séries" -> item.type == MediaContentType.SERIES
                "Favoritos" -> item.isFavorite
                "🔴 Fora do Ar" -> !item.isWorking
                else -> true
            }

            val matchesCategory = selectedCategoryFilter == "Todos" ||
                    item.category.split(",", "/", "•", "|")
                        .map { it.trim() }
                        .any { SearchUtils.normalize(it) == SearchUtils.normalize(selectedCategoryFilter) }

            matchesSearch && matchesType && matchesCategory
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(DarkBg)
    ) {
        // Filter Chips Row (Type: Todos, Filmes, Séries, Favoritos, Fora do Ar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeFilters = remember(isAdmin, mediaList) {
                val list = mutableListOf(
                    "Todos" to null,
                    "Filmes" to Icons.Filled.Movie,
                    "Séries" to Icons.Filled.Tv,
                    "Favoritos" to Icons.Filled.Favorite
                )
                if (isAdmin) {
                    val offlineCount = mediaList.count { !it.isWorking }
                    list.add("🔴 Fora do Ar" to Icons.Filled.Warning)
                }
                list
            }

            typeFilters.forEach { (label, icon) ->
                val isSelected = selectedTypeFilter == label
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTypeFilter = label },
                    label = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = if (icon != null) {
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) {
                                    if (label == "🔴 Fora do Ar") Color.White else Color.Black
                                } else if (label == "Favoritos") {
                                    NeonPink
                                } else if (label == "🔴 Fora do Ar") {
                                    Color(0xFFFF1744)
                                } else {
                                    NeonCyan
                                }
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (label) {
                            "Favoritos" -> NeonPink
                            "🔴 Fora do Ar" -> Color(0xFFFF1744)
                            else -> NeonCyan
                        },
                        selectedLabelColor = if (label == "🔴 Fora do Ar") Color.White else Color.Black,
                        containerColor = DarkCardBg,
                        labelColor = Color.White.copy(alpha = 0.85f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (label == "🔴 Fora do Ar") Color(0xFFFF1744).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                        selectedBorderColor = when (label) {
                            "Favoritos" -> NeonPink
                            "🔴 Fora do Ar" -> Color(0xFFFF1744)
                            else -> NeonCyan
                        }
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        // Category Filter Chips
        if (allCategories.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gênero:",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 4.dp)
                )
                allCategories.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) NeonGreen.copy(alpha = 0.2f) else DarkCardBg.copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                if (isSelected) NeonGreen else Color.White.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedCategoryFilter = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 11.sp,
                            color = if (isSelected) NeonGreen else Color.White.copy(alpha = 0.75f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Admin Header / Count Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${filteredMedia.size} ${if (filteredMedia.size == 1) "título encontrado" else "títulos disponíveis"}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (isAdmin) {
                val offlineCount = remember(mediaList) { mediaList.count { !it.isWorking } }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (offlineCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF1744).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFFF1744).copy(alpha = 0.6f)),
                            modifier = Modifier.clickable {
                                selectedTypeFilter = "🔴 Fora do Ar"
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF1744))
                                )
                                Text(
                                    text = "$offlineCount fora",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                            }
                        }
                    }

                    // Botão API de Filmes
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonGreen.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable {
                                onOpenMoviesApi()
                            }
                            .testTag("btn_open_movies_api")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Link,
                                contentDescription = "API de Filmes",
                                tint = NeonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "API Filmes",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen
                            )
                        }
                    }

                    // Botão Testar Filmes & Séries
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonCyan.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clickable(enabled = !isTestingMovies) {
                                onTestAllMovies()
                            }
                            .testTag("btn_test_all_movies")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Movie,
                                contentDescription = "Testar filmes e séries",
                                tint = NeonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isTestingMovies) "Testando..." else "Testar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                    }

                    Button(
                        onClick = {
                            mediaToEdit = null
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Adicionar",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Adicionar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (isTestingMovies) {
            Surface(
                color = NeonGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
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
                        color = NeonGreen
                    )
                    Text(
                        text = movieTestProgressText ?: "Testando filmes e séries (PobreFlix & TapeContent)...",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Media Grid
        if (filteredMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selectedTypeFilter == "🔴 Fora do Ar") Icons.Default.CheckCircle else Icons.Filled.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = if (selectedTypeFilter == "🔴 Fora do Ar") Color(0xFF00E676).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedTypeFilter == "🔴 Fora do Ar") "Nenhum filme ou série fora do ar no momento! Todos os títulos estão operacionais."
                        else if (searchQuery.isNotBlank()) "Nenhum filme ou série encontrado para '$searchQuery'"
                        else "Nenhum filme ou série disponível nesta categoria.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isAdmin && selectedTypeFilter != "🔴 Fora do Ar") {
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                mediaToEdit = null
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Adicionar Filme / Série", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredMedia, key = { it.id }) { item ->
                    MediaCard(
                        media = item,
                        isAdmin = isAdmin,
                        onClick = {
                            selectedMediaForDetail = item
                            onSelectMedia(item)
                        },
                        onPlayClick = {
                            if (item.type == MediaContentType.MOVIE) {
                                onPlayMovie(item)
                            } else {
                                selectedMediaForDetail = item
                                onSelectMedia(item)
                            }
                        },
                        onEditClick = {
                            mediaToEdit = item
                            showAddDialog = true
                        },
                        onDeleteClick = {
                            mediaToDelete = item
                        },
                        onToggleFavorite = {
                            onToggleFavorite(item.id)
                        },
                        onToggleWorkingStatus = {
                            onAddOrUpdateMedia(item.copy(isWorking = !item.isWorking))
                        }
                    )
                }
            }
        }
    }

    // Modal Details (Movie or Series)
    activeMedia?.let { media ->
        if (media.type == MediaContentType.MOVIE) {
            MovieDetailDialog(
                movie = media,
                isAdmin = isAdmin,
                onDismiss = {
                    selectedMediaForDetail = null
                    onDismissMedia()
                },
                onPlay = {
                    onPlayMovie(media)
                },
                onToggleFavorite = { onToggleFavorite(media.id) },
                onEdit = {
                    mediaToEdit = media
                    showAddDialog = true
                }
            )
        } else {
            SeriesDetailDialog(
                series = media,
                isAdmin = isAdmin,
                initialSeason = selectedSeason,
                currentlyPlayingEpisode = selectedEpisode,
                onDismiss = {
                    selectedMediaForDetail = null
                    onDismissMedia()
                },
                onPlayEpisode = { season, episode ->
                    onPlayEpisode(media, season, episode)
                },
                onToggleFavorite = { onToggleFavorite(media.id) },
                onEdit = {
                    mediaToEdit = media
                    showAddDialog = true
                }
            )
        }
    }

    // Add / Edit Media Dialog
    if (showAddDialog) {
        AddEditMediaDialog(
            initialMedia = mediaToEdit,
            onDismiss = {
                showAddDialog = false
                mediaToEdit = null
            },
            onSave = { savedMedia ->
                onAddOrUpdateMedia(savedMedia)
                showAddDialog = false
                mediaToEdit = null
                Toast.makeText(
                    context,
                    "${if (savedMedia.type == MediaContentType.MOVIE) "Filme" else "Série"} salvo com sucesso!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    // Delete Confirmation Dialog
    mediaToDelete?.let { media ->
        AlertDialog(
            onDismissRequest = { mediaToDelete = null },
            title = {
                Text(
                    text = "Excluir ${if (media.type == MediaContentType.MOVIE) "Filme" else "Série"}?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Tem certeza que deseja remover '${media.title}' do catálogo?",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMedia(media.id)
                        mediaToDelete = null
                        Toast.makeText(context, "Item excluído do catálogo.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPink)
                ) {
                    Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mediaToDelete = null }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = DarkCardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

// =============================================================================
// MEDIA CARD COMPONENT
// =============================================================================

@Composable
fun MediaCard(
    media: MediaItem,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleWorkingStatus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isOffline = !media.isWorking
    val borderColor = if (isOffline) Color(0xFFFF1744)
    else if (media.type == MediaContentType.SERIES) NeonPurple.copy(alpha = 0.35f)
    else NeonCyan.copy(alpha = 0.35f)
    val borderWidth = if (isOffline) 2.5.dp else 1.dp

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .tvFocusable(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(borderWidth, borderColor),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOffline) 8.dp else 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(SurfaceDark)
            ) {
                if (media.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(media.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (media.type == MediaContentType.MOVIE) Icons.Filled.Movie else Icons.Filled.Tv,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Gradient Shade
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Top Badges Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Type Badge and Offline Badge
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (media.type == MediaContentType.MOVIE) NeonCyan.copy(alpha = 0.9f)
                                    else NeonPurple.copy(alpha = 0.9f)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (media.type == MediaContentType.MOVIE) Icons.Filled.Movie else Icons.Filled.Tv,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (media.type == MediaContentType.MOVIE) "FILME" else "SÉRIE",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (isOffline) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFF1744))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Fora do Ar",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "OFFLINE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }
                    }

                    // Favorite Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onToggleFavorite() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (media.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favoritar",
                            tint = if (media.isFavorite) NeonPink else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Rating & Year tag at bottom of poster
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (media.rating.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = NeonGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = media.rating,
                                color = NeonGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (media.year.isNotBlank()) {
                        Text(
                            text = media.year,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (media.type == MediaContentType.SERIES) {
                        Text(
                            text = "${media.totalSeasons}T • ${media.totalEpisodes} eps",
                            color = NeonPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = media.category,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isOffline) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF1744))
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Fora do Ar",
                                color = Color(0xFFFF5252),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons (Play & Admin)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onPlayClick() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOffline) Color(0xFFFF1744) else if (media.type == MediaContentType.MOVIE) NeonGreen else NeonPurple,
                            contentColor = if (isOffline) Color.White else Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Filled.Warning else Icons.Filled.PlayArrow,
                            contentDescription = if (isOffline) "Fora do Ar" else "Assistir",
                            modifier = Modifier.size(15.dp),
                            tint = if (isOffline) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isOffline) "Indisponível" else if (media.type == MediaContentType.MOVIE) "Assistir" else "Episódios",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    if (isAdmin) {
                        IconButton(

                            onClick = { onEditClick() },

                            modifier = Modifier.size(32.dp)

                        ) {

                            Icon(

                                imageVector = Icons.Filled.Edit,

                                contentDescription = "Editar informações",

                                tint = NeonCyan,

                                modifier = Modifier.size(16.dp)

                            )

                        }

                    }

                    if (isAdmin) {
                        IconButton(
                            onClick = { onToggleWorkingStatus() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (media.isWorking) Icons.Default.CheckCircle else Icons.Default.Close,
                                contentDescription = if (media.isWorking) "Marcar como Fora do Ar" else "Marcar como Funcionando",
                                tint = if (media.isWorking) Color(0xFF00E676) else Color(0xFFFF1744),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteClick() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Excluir",
                                tint = NeonPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// MOVIE DETAIL DIALOG
// =============================================================================

@Composable
fun MovieDetailDialog(
    movie: MediaItem,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val isOffline = !movie.isWorking
    val dialogScrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(
                            if (isOffline) 2.5.dp else 1.dp,
                            if (isOffline) Color(0xFFFF1744) else NeonCyan.copy(alpha = 0.4f)
                        ),
                        RoundedCornerShape(24.dp)
                    ),
                color = DarkCardBg,
                shape = RoundedCornerShape(24.dp)
            ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalHeight = maxHeight

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(dialogScrollState)
                ) {
                    // Header Image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(SurfaceDark)
                    ) {
                        val imageUrl = movie.backdropUrl?.takeIf { it.isNotBlank() } ?: movie.coverUrl
                        if (imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Gradient overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Transparent,
                                            DarkCardBg
                                        )
                                    )
                                )
                        )

                    }

                    // Details Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonCyan)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("FILME", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (isOffline) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFF1744))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("FORA DO AR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }

                            if (movie.year.isNotBlank()) {
                                Text(
                                    text = movie.year,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (movie.rating.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = NeonGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(movie.rating, color = NeonGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = movie.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = movie.category,
                            color = NeonGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (movie.synopsis.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Sinopse",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = movie.synopsis,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                textAlign = TextAlign.Justify
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Play Button
                        Button(
                            onClick = onPlay,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ASSISTIR FILME AGORA",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // Always-available Floating Header Buttons (Close, Edit, Favorite)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.75f))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isAdmin) {
                            IconButton(

                                onClick = onEdit,

                                modifier = Modifier

                                    .size(38.dp)

                                    .clip(CircleShape)

                                    .background(Color.Black.copy(alpha = 0.75f))

                                    .border(BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)), CircleShape)

                            ) {

                                Icon(

                                    imageVector = Icons.Filled.Edit,

                                    contentDescription = "Editar informações",

                                    tint = NeonCyan,

                                    modifier = Modifier.size(18.dp)

                                )

                            }

                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (movie.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = if (movie.isFavorite) NeonPink else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Scrollbar indicator for the entire page/dialog
                if (dialogScrollState.maxValue > 0) {
                    val scrollProgress = (dialogScrollState.value.toFloat() / dialogScrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                    val thumbHeight = (totalHeight * 0.20f).coerceIn(36.dp, 80.dp)
                    val availableTravel = (totalHeight - thumbHeight - 32.dp).coerceAtLeast(0.dp)
                    val offsetY = 16.dp + availableTravel * scrollProgress

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp, top = 16.dp, bottom = 16.dp)
                            .width(5.dp)
                            .height(totalHeight - 32.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = offsetY - 16.dp)
                                .width(5.dp)
                                .height(thumbHeight)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(NeonGreen)
                        )
                    }
                }
            }
        }
    }
}
}

// =============================================================================
// SERIES DETAIL DIALOG (WITH SEASONS & EPISODES)
// =============================================================================

@Composable
fun SeriesDetailDialog(
    series: MediaItem,
    isAdmin: Boolean,
    initialSeason: SeasonItem? = null,
    currentlyPlayingEpisode: EpisodeItem? = null,
    onDismiss: () -> Unit,
    onPlayEpisode: (SeasonItem, EpisodeItem) -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val isOffline = !series.isWorking
    val seasons = series.seasons.ifEmpty {
        listOf(
            SeasonItem(
                seasonNumber = 1,
                title = "1ª Temporada",
                episodes = emptyList()
            )
        )
    }

    val initialIdx = remember(initialSeason, seasons) {
        if (initialSeason != null) {
            seasons.indexOfFirst { it.seasonNumber == initialSeason.seasonNumber }.coerceAtLeast(0)
        } else 0
    }
    var selectedSeasonIndex by remember(initialIdx) { mutableIntStateOf(initialIdx) }

    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.first()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        BorderStroke(
                            if (isOffline) 2.5.dp else 1.dp,
                            if (isOffline) Color(0xFFFF1744) else NeonPurple.copy(alpha = 0.4f)
                        ),
                        RoundedCornerShape(24.dp)
                    ),
                color = DarkCardBg,
                shape = RoundedCornerShape(24.dp)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(SurfaceDark)
                ) {
                    val imageUrl = series.backdropUrl?.takeIf { it.isNotBlank() } ?: series.coverUrl
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        DarkCardBg.copy(alpha = 0.6f),
                                        DarkCardBg
                                    )
                                )
                            )
                    )

                    // Header Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isAdmin) {
                                IconButton(
                                    onClick = onEdit,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .border(BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Editar informações",
                                        tint = NeonPurple,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onToggleFavorite,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                            ) {
                                Icon(
                                    imageVector = if (series.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favoritar",
                                    tint = if (series.isFavorite) NeonPink else Color.White
                                )
                            }
                        }
                    }

                    // Title Info at bottom of header
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonPurple)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("SÉRIE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            if (isOffline) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFFF1744))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("FORA DO AR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            if (series.year.isNotBlank()) {
                                Text(series.year, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                            if (series.rating.isNotBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = NeonGold, modifier = Modifier.size(12.dp))
                                    Text(series.rating, color = NeonGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            text = series.title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Synopsis for series if available
                if (series.synopsis.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkCardBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = series.synopsis,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Justify,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Season Tabs
                if (seasons.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSeasonIndex.coerceIn(0, seasons.size - 1),
                        containerColor = SurfaceDark,
                        contentColor = NeonPurple,
                        indicator = { tabPositions ->
                            if (selectedSeasonIndex < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                                    color = NeonPurple,
                                    height = 3.dp
                                )
                            }
                        },
                        edgePadding = 16.dp
                    ) {
                        seasons.forEachIndexed { index, season ->
                            Tab(
                                selected = selectedSeasonIndex == index,
                                onClick = { selectedSeasonIndex = index },
                                text = {
                                    Text(
                                        text = season.title.ifBlank { "Temporada ${season.seasonNumber}" },
                                        fontWeight = if (selectedSeasonIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedSeasonIndex == index) NeonPurple else Color.White.copy(alpha = 0.7f),
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = currentSeason.title.ifBlank { "1ª Temporada" },
                            color = NeonPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Episode List
                val episodes = currentSeason.episodes
                if (episodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nenhum episódio cadastrado nesta temporada.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodes, key = { it.id.ifBlank { "${it.episodeNumber}_${it.title}" } }) { ep ->
                            val isCurrentEp = currentlyPlayingEpisode != null && 
                                (ep.id == currentlyPlayingEpisode.id || 
                                (ep.episodeNumber == currentlyPlayingEpisode.episodeNumber && currentSeason.seasonNumber == initialSeason?.seasonNumber))
                            EpisodeCard(
                                episode = ep,
                                isCurrentlyPlaying = isCurrentEp,
                                onPlay = { onPlayEpisode(currentSeason, ep) }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun EpisodeCard(
    episode: EpisodeItem,
    isCurrentlyPlaying: Boolean = false,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .border(
                if (isCurrentlyPlaying) 2.dp else 1.dp,
                if (isCurrentlyPlaying) NeonPurple else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyPlaying) DarkCardBg else SurfaceDark
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Number Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isCurrentlyPlaying) NeonPurple else NeonPurple.copy(alpha = 0.2f))
                    .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", episode.episodeNumber),
                    color = if (isCurrentlyPlaying) Color.White else NeonPurple,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = episode.title.ifBlank { "Episódio ${episode.episodeNumber}" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentlyPlaying) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonPurple)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "ASSISTINDO",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                if (!episode.duration.isNullOrBlank() || !episode.synopsis.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val desc = buildString {
                        if (!episode.duration.isNullOrBlank()) append(episode.duration)
                        if (!episode.duration.isNullOrBlank() && !episode.synopsis.isNullOrBlank()) append(" • ")
                        if (!episode.synopsis.isNullOrBlank()) append(episode.synopsis)
                    }
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Play Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentlyPlaying) NeonPurple else NeonGreen)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproduzir Episódio",
                    tint = if (isCurrentlyPlaying) Color.White else Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// =============================================================================
// ADD / EDIT MEDIA DIALOG (MOVIES & SERIES)
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMediaDialog(
    initialMedia: MediaItem?,
    onDismiss: () -> Unit,
    onSave: (MediaItem) -> Unit
) {
    val isEdit = initialMedia != null
    var mediaType by remember { mutableStateOf(initialMedia?.type ?: MediaContentType.MOVIE) }
    var title by remember { mutableStateOf(initialMedia?.title ?: "") }
    var coverUrl by remember { mutableStateOf(initialMedia?.coverUrl ?: "") }
    var backdropUrl by remember { mutableStateOf(initialMedia?.backdropUrl ?: "") }
    var category by remember { mutableStateOf(initialMedia?.category ?: "Ação") }
    var year by remember { mutableStateOf(initialMedia?.year ?: "2024") }
    var rating by remember { mutableStateOf(initialMedia?.rating ?: "8.5") }
    var synopsis by remember { mutableStateOf(initialMedia?.synopsis ?: "") }
    
    // Movie fields
    var movieStreamUrl by remember { mutableStateOf(initialMedia?.movieStreamUrl ?: "") }
    var isMovieWebPlayer by remember { mutableStateOf(initialMedia?.isWebPlayer ?: false) }
    var isSeriesWebPlayer by remember { mutableStateOf(initialMedia?.isWebPlayer ?: false) }
    var isWorkingState by remember { mutableStateOf(initialMedia?.isWorking ?: true) }

    // Series seasons state
    val seasonsState = remember {
        mutableStateListOf<SeasonItem>().apply {
            if (initialMedia != null && initialMedia.seasons.isNotEmpty()) {
                addAll(initialMedia.seasons)
            } else {
                add(
                    SeasonItem(
                        seasonNumber = 1,
                        title = "1ª Temporada",
                        episodes = listOf(
                            EpisodeItem(
                                id = "ep_1",
                                episodeNumber = 1,
                                title = "Episódio 1",
                                streamUrl = ""
                            )
                        )
                    )
                )
            }
        }
    }

    var selectedSeasonTab by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categorySuggestions = listOf(
        "Ação", "Aventura", "Comédia", "Drama", "Ficção Científica",
        "Animação", "Terror", "Suspense", "Documentário", "Romance",
        "Esportes", "Fantasia", "Policial", "Família", "Mistério"
    )

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val handleDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
            color = DarkCardBg,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEdit) "Editar Conteúdo" else "Adicionar Conteúdo",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Preencha as informações do filme ou série",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = handleDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceDark)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Type Selector (Filme vs Série)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDark)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Movie Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (mediaType == MediaContentType.MOVIE) NeonCyan else Color.Transparent)
                                .clickable { mediaType = MediaContentType.MOVIE }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Movie,
                                    contentDescription = null,
                                    tint = if (mediaType == MediaContentType.MOVIE) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Filme",
                                    color = if (mediaType == MediaContentType.MOVIE) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Series Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (mediaType == MediaContentType.SERIES) NeonPurple else Color.Transparent)
                                .clickable { mediaType = MediaContentType.SERIES }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Tv,
                                    contentDescription = null,
                                    tint = if (mediaType == MediaContentType.SERIES) Color.White else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Série",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Título
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título do ${if (mediaType == MediaContentType.MOVIE) "Filme" else "Série"} *") },
                        placeholder = { Text("Ex: Interestelar, Breaking Bad...") },
                        leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null, tint = NeonCyan) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )

                    // Link da Capa (Cover URL)
                    OutlinedTextField(
                        value = coverUrl,
                        onValueChange = { coverUrl = it },
                        label = { Text("Link da Capa (URL da Imagem / Pôster) *") },
                        placeholder = { Text("https://image.tmdb.org/... ou https://...") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, tint = NeonCyan) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )

                    // Link do Banner / Backdrop (Opcional)
                    OutlinedTextField(
                        value = backdropUrl,
                        onValueChange = { backdropUrl = it },
                        label = { Text("Banner de Fundo / Backdrop TMDb (Opcional)") },
                        placeholder = { Text("https://image.tmdb.org/t/p/original/...") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, tint = NeonCyan) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = customTextFieldColors()
                    )

                    // Cover Image Live Preview
                    if (coverUrl.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 50.dp, height = 75.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkBg)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(coverUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Prévia da Capa",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Prévia da Capa",
                                    color = NeonGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Se a imagem carregar aqui, o link está correto.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Gênero(s), Ano & Classificação TMDb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Gêneros") },
                            placeholder = { Text("Ex: Ação, Aventura") },
                            leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, tint = NeonCyan) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            colors = customTextFieldColors()
                        )

                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            label = { Text("Ano") },
                            placeholder = { Text("2024") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f),
                            singleLine = true,
                            colors = customTextFieldColors()
                        )

                        OutlinedTextField(
                            value = rating,
                            onValueChange = { rating = it },
                            label = { Text("Nota TMDb") },
                            placeholder = { Text("Ex: 8.5") },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, tint = NeonGold, modifier = Modifier.size(16.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.9f),
                            singleLine = true,
                            colors = customTextFieldColors()
                        )
                    }

                    // Multi-Genre Selection Chips
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Gêneros (toque para adicionar ou remover múltiplos):",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val currentGenres = category
                                .split(",", "/", "•", "|")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }

                            categorySuggestions.forEach { cat ->
                                val isSelected = currentGenres.any { it.equals(cat, ignoreCase = true) }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NeonCyan.copy(alpha = 0.25f) else SurfaceDark)
                                        .border(
                                            1.dp,
                                            if (isSelected) NeonCyan else Color.White.copy(alpha = 0.12f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            val updatedList = if (isSelected) {
                                                currentGenres.filterNot { it.equals(cat, ignoreCase = true) }
                                            } else {
                                                currentGenres + cat
                                            }
                                            category = updatedList.joinToString(", ")
                                        }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = NeonCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Text(
                                            text = cat,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) NeonCyan else Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sinopse
                    OutlinedTextField(
                        value = synopsis,
                        onValueChange = { synopsis = it },
                        label = { Text("Sinopse / Descrição") },
                        placeholder = { Text("Breve resumo da trama...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        colors = customTextFieldColors()
                    )

                    // =========================================================
                    // ESPECÍFICO DE FILME
                    // =========================================================
                    if (mediaType == MediaContentType.MOVIE) {
                        Text(
                            text = "Link do Filme (Vídeo / Stream / Iframe)",
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        OutlinedTextField(
                            value = movieStreamUrl,
                            onValueChange = { text -> 
                                var processed = text
                                if (processed.contains("<iframe", ignoreCase = true) && processed.contains("src=\"", ignoreCase = true)) {
                                    val regex = Regex("src=\"([^\"]+)\"")
                                    val match = regex.find(processed)
                                    if (match != null) {
                                        processed = match.groupValues[1]
                                        isMovieWebPlayer = true
                                    }
                                }
                                movieStreamUrl = processed
                            },
                            label = { Text("Link do Filme (URL ou Código <iframe>) *") },
                            placeholder = { Text("https://... ou <iframe src=\"...\"></iframe>") },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = NeonGreen) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = customTextFieldColors()
                        )


                        // Web Player Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reproduzir no Navegador Embutido (Web Player)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Ative caso o link seja uma página de player web (embed)",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isMovieWebPlayer,
                                onCheckedChange = { isMovieWebPlayer = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = NeonGreen
                                )
                            )
                        }
                    }

                    // =========================================================
                    // ESPECÍFICO DE SÉRIE (TEMPORADAS E EPISÓDIOS)
                    // =========================================================
                    if (mediaType == MediaContentType.SERIES) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceDark)
                                .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            // Series Web Player Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkBg)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Reproduzir no Navegador Embutido (Web Player) - Série",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Padrão para episódios sem Web Player individual",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isSeriesWebPlayer,
                                    onCheckedChange = { isSeriesWebPlayer = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = NeonPurple
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Temporadas e Episódios",
                                    color = NeonPurple,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = {
                                        val nextSeasonNum = seasonsState.size + 1
                                        seasonsState.add(
                                            SeasonItem(
                                                seasonNumber = nextSeasonNum,
                                                title = "${nextSeasonNum}ª Temporada",
                                                episodes = listOf(
                                                    EpisodeItem(
                                                        id = "ep_s${nextSeasonNum}_1",
                                                        episodeNumber = 1,
                                                        title = "Episódio 1",
                                                        streamUrl = ""
                                                    )
                                                )
                                            )
                                        )
                                        selectedSeasonTab = seasonsState.size - 1
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonPurple,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+ Temporada", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Seasons Tabs
                            if (seasonsState.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    seasonsState.forEachIndexed { sIdx, season ->
                                        val isSelected = selectedSeasonTab == sIdx
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) NeonPurple else DarkBg)
                                                .clickable { selectedSeasonTab = sIdx }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = season.title.ifBlank { "Temp ${season.seasonNumber}" },
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val currentSeasonIndex = selectedSeasonTab.coerceIn(0, (seasonsState.size - 1).coerceAtLeast(0))
                                val currentSeason = seasonsState.getOrNull(currentSeasonIndex)

                                if (currentSeason != null) {
                                    // Season Title & Delete Season
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = currentSeason.title,
                                            onValueChange = { newTitle ->
                                                seasonsState[currentSeasonIndex] = currentSeason.copy(title = newTitle)
                                            },
                                            label = { Text("Nome da Temporada") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            colors = customTextFieldColors()
                                        )

                                        if (seasonsState.size > 1) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = {
                                                    seasonsState.removeAt(currentSeasonIndex)
                                                    selectedSeasonTab = 0
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Remover Temporada",
                                                    tint = NeonPink
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Episodes of this Season Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Episódios (${currentSeason.episodes.size})",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Button(
                                            onClick = {
                                                val nextEpNum = currentSeason.episodes.size + 1
                                                val newEps = currentSeason.episodes + EpisodeItem(
                                                    id = "ep_s${currentSeason.seasonNumber}_$nextEpNum",
                                                    episodeNumber = nextEpNum,
                                                    title = "Episódio $nextEpNum",
                                                    streamUrl = ""
                                                )
                                                seasonsState[currentSeasonIndex] = currentSeason.copy(episodes = newEps)
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NeonGreen,
                                                contentColor = Color.Black
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.Black)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("+ Episódio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Episode items in Season
                                    currentSeason.episodes.forEachIndexed { epIdx, episode ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Episódio ${episode.episodeNumber}",
                                                        color = NeonCyan,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )

                                                    if (currentSeason.episodes.size > 1) {
                                                        IconButton(
                                                            onClick = {
                                                                val updatedEps = currentSeason.episodes.toMutableList()
                                                                updatedEps.removeAt(epIdx)
                                                                seasonsState[currentSeasonIndex] = currentSeason.copy(episodes = updatedEps)
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Close,
                                                                contentDescription = "Remover",
                                                                tint = NeonPink,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = episode.title,
                                                    onValueChange = { newTitle ->
                                                        val updatedEps = currentSeason.episodes.toMutableList()
                                                        updatedEps[epIdx] = episode.copy(title = newTitle)
                                                        seasonsState[currentSeasonIndex] = currentSeason.copy(episodes = updatedEps)
                                                    },
                                                    label = { Text("Título do Episódio") },
                                                    placeholder = { Text("Ex: Capítulo 1: O Começo") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    colors = customTextFieldColors()
                                                )

                                                OutlinedTextField(
                                                    value = episode.streamUrl,
                                                    onValueChange = { newUrl ->
                                                        var processed = newUrl
                                                        if (processed.contains("<iframe", ignoreCase = true) && processed.contains("src=\"", ignoreCase = true)) {
                                                            val regex = Regex("src=\"([^\"]+)\"")
                                                            val match = regex.find(processed)
                                                            if (match != null) {
                                                                processed = match.groupValues[1]
                                                                isSeriesWebPlayer = true
                                                            }
                                                        }
                                                        val updatedEps = currentSeason.episodes.toMutableList()
                                                        updatedEps[epIdx] = episode.copy(streamUrl = processed)
                                                        seasonsState[currentSeasonIndex] = currentSeason.copy(episodes = updatedEps)
                                                    },
                                                    label = { Text("Link do Episódio (URL ou Código <iframe>) *") },
                                                    placeholder = { Text("https://... ou <iframe src=\"...\"></iframe>") },
                                                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, tint = NeonPurple) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    colors = customTextFieldColors()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Operational Status (Working vs Offline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWorkingState) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF1744).copy(alpha = 0.15f))
                            .border(
                                BorderStroke(1.dp, if (isWorkingState) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFF1744).copy(alpha = 0.6f)),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { isWorkingState = !isWorkingState }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isWorkingState) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isWorkingState) Color(0xFF00E676) else Color(0xFFFF1744),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isWorkingState) "Status: Funcionando" else "Status: Fora do Ar",
                                    color = if (isWorkingState) Color(0xFF00E676) else Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (isWorkingState) "Conteúdo operacional (borda normal)" else "Fora do ar (borda vermelha para o admin)",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = isWorkingState,
                            onCheckedChange = { isWorkingState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF00E676),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFFF1744)
                            )
                        )
                    }

                    // Error Message Display
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = NeonPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Bottom Buttons (Cancel & Save)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = handleDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Por favor, informe o título."
                                return@Button
                            }
                            if (coverUrl.isBlank()) {
                                errorMessage = "Por favor, informe o link da capa."
                                return@Button
                            }
                            if (mediaType == MediaContentType.MOVIE && movieStreamUrl.isBlank()) {
                                errorMessage = "Por favor, informe o link do filme."
                                return@Button
                            }
                            if (mediaType == MediaContentType.SERIES) {
                                val hasAnyEpisode = seasonsState.any { s -> s.episodes.any { it.streamUrl.isNotBlank() } }
                                if (!hasAnyEpisode) {
                                    errorMessage = "Por favor, adicione pelo menos um episódio com link na série."
                                    return@Button
                                }
                            }

                            errorMessage = null
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            val id = initialMedia?.id ?: "media_${System.currentTimeMillis()}"
                            val newMedia = MediaItem(
                                id = id,
                                title = title.trim(),
                                type = mediaType,
                                coverUrl = coverUrl.trim(),
                                backdropUrl = backdropUrl.trim().ifBlank { null },
                                synopsis = synopsis.trim(),
                                category = category.trim().ifBlank { "Geral" },
                                year = year.trim(),
                                rating = rating.trim(),
                                movieStreamUrl = if (mediaType == MediaContentType.MOVIE) movieStreamUrl.trim() else null,
                                isWebPlayer = if (mediaType == MediaContentType.MOVIE) isMovieWebPlayer else isSeriesWebPlayer,
                                seasons = if (mediaType == MediaContentType.SERIES) seasonsState.toList() else emptyList(),
                                isFavorite = initialMedia?.isFavorite ?: false,
                                isWorking = isWorkingState
                            )
                            onSave(newMedia)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isEdit) "Salvar Alterações" else "Salvar Conteúdo",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

