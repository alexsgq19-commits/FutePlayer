package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
fun MediaScreenContent(
    mediaList: List<MediaItem>,
    searchQuery: String,
    isAdmin: Boolean,
    onPlayMovie: (MediaItem) -> Unit,
    onPlayEpisode: (MediaItem, SeasonItem, EpisodeItem) -> Unit,
    onAddOrUpdateMedia: (MediaItem) -> Unit,
    onDeleteMedia: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTypeFilter by remember { mutableStateOf("Todos") } // "Todos", "Filmes", "Séries", "Favoritos"
    var selectedCategoryFilter by remember { mutableStateOf("Todos") }
    var mediaToEdit by remember { mutableStateOf<MediaItem?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var mediaToDelete by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMediaForDetail by remember { mutableStateOf<MediaItem?>(null) }

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
            .background(DarkBg)
    ) {
        // Filter Chips Row (Type: Todos, Filmes, Séries, Favoritos)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val typeFilters = listOf(
                "Todos" to null,
                "Filmes" to Icons.Filled.Movie,
                "Séries" to Icons.Filled.Tv,
                "Favoritos" to Icons.Filled.Favorite
            )

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
                                tint = if (isSelected) Color.Black else if (label == "Favoritos") NeonPink else NeonCyan
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (label == "Favoritos") NeonPink else NeonCyan,
                        selectedLabelColor = Color.Black,
                        containerColor = DarkCardBg,
                        labelColor = Color.White.copy(alpha = 0.85f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color.White.copy(alpha = 0.15f),
                        selectedBorderColor = if (label == "Favoritos") NeonPink else NeonCyan
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
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Nenhum filme ou série encontrado para '$searchQuery'"
                        else "Nenhum filme ou série disponível nesta categoria.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isAdmin) {
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
                        },
                        onPlayClick = {
                            if (item.type == MediaContentType.MOVIE) {
                                onPlayMovie(item)
                            } else {
                                selectedMediaForDetail = item
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
                        }
                    )
                }
            }
        }
    }

    // Modal Details (Movie or Series)
    selectedMediaForDetail?.let { media ->
        if (media.type == MediaContentType.MOVIE) {
            MovieDetailDialog(
                movie = media,
                onDismiss = { selectedMediaForDetail = null },
                onPlay = {
                    selectedMediaForDetail = null
                    onPlayMovie(media)
                },
                onToggleFavorite = { onToggleFavorite(media.id) }
            )
        } else {
            SeriesDetailDialog(
                series = media,
                onDismiss = { selectedMediaForDetail = null },
                onPlayEpisode = { season, episode ->
                    selectedMediaForDetail = null
                    onPlayEpisode(media, season, episode)
                },
                onToggleFavorite = { onToggleFavorite(media.id) }
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .border(
                BorderStroke(
                    1.dp,
                    if (media.type == MediaContentType.SERIES) NeonPurple.copy(alpha = 0.35f)
                    else NeonCyan.copy(alpha = 0.35f)
                ),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                    // Type Badge
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
                Text(
                    text = media.category,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

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
                            containerColor = if (media.type == MediaContentType.MOVIE) NeonGreen else NeonPurple,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Assistir",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (media.type == MediaContentType.MOVIE) "Assistir" else "Episódios",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = { onEditClick() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Editar",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
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
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
            color = DarkCardBg,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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

                    // Close & Favorite Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                        }

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = if (movie.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favoritar",
                                tint = if (movie.isFavorite) NeonPink else Color.White
                            )
                        }
                    }
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = movie.synopsis,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
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

                    Spacer(modifier = Modifier.height(12.dp))
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
    onDismiss: () -> Unit,
    onPlayEpisode: (SeasonItem, EpisodeItem) -> Unit,
    onToggleFavorite: () -> Unit
) {
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val seasons = series.seasons.ifEmpty {
        listOf(
            SeasonItem(
                seasonNumber = 1,
                title = "1ª Temporada",
                episodes = emptyList()
            )
        )
    }

    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.first()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxSize(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(24.dp)),
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
                        horizontalArrangement = Arrangement.SpaceBetween
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
                            EpisodeCard(
                                episode = ep,
                                onPlay = { onPlayEpisode(currentSeason, ep) }
                            )
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
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
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
                    .background(NeonPurple.copy(alpha = 0.2f))
                    .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = String.format("%02d", episode.episodeNumber),
                    color = NeonPurple,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title.ifBlank { "Episódio ${episode.episodeNumber}" },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

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

            Spacer(modifier = Modifier.width(8.dp))

            // Play Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NeonGreen)
                    .clickable { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproduzir Episódio",
                    tint = Color.Black,
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
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.95f)
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
                            text = "Link do Filme (Vídeo / Stream)",
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        OutlinedTextField(
                            value = movieStreamUrl,
                            onValueChange = { movieStreamUrl = it },
                            label = { Text("Link do Filme (URL do Vídeo / MP4 / M3U8) *") },
                            placeholder = { Text("https://exemplo.com/filme.mp4 ou .m3u8") },
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
                                                        val updatedEps = currentSeason.episodes.toMutableList()
                                                        updatedEps[epIdx] = episode.copy(streamUrl = newUrl)
                                                        seasonsState[currentSeasonIndex] = currentSeason.copy(episodes = updatedEps)
                                                    },
                                                    label = { Text("Link do Episódio (URL do Vídeo / Stream) *") },
                                                    placeholder = { Text("https://exemplo.com/ep1.mp4 ou .m3u8") },
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
                                isWebPlayer = if (mediaType == MediaContentType.MOVIE) isMovieWebPlayer else false,
                                seasons = if (mediaType == MediaContentType.SERIES) seasonsState.toList() else emptyList(),
                                isFavorite = initialMedia?.isFavorite ?: false,
                                isWorking = initialMedia?.isWorking ?: true
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
