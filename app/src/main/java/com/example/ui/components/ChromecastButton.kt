package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.mediarouter.app.MediaRouteButton
import com.example.R
import com.example.cast.CastManager
import com.example.cast.CastUiState
import com.example.ui.theme.StadiumAccentRed
import com.example.ui.theme.StadiumCyanSecondary
import com.example.ui.theme.StadiumGreenPrimary
import com.google.android.gms.cast.framework.CastButtonFactory

fun launchWebVideoCaster(
    context: android.content.Context,
    url: String,
    title: String,
    poster: String? = null,
    headers: Map<String, String>? = null
) {
    val targetUrl = if (url.isNotBlank()) url.trim() else ""
    if (targetUrl.isBlank() || com.example.util.VideoLinkCompatibility.isAdVideoUrl(targetUrl)) {
        android.widget.Toast.makeText(context, "Nenhum link de vídeo válido disponível para transmitir.", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = Uri.parse(targetUrl)
        val isDirectStream = com.example.util.VideoLinkCompatibility.isDirectMediaStream(targetUrl)
        val mimeType = com.example.util.VideoLinkCompatibility.resolveMimeType(targetUrl) ?: "video/*"

        // Build header bundle/strings for Web Video Caster
        val headerMap = com.example.util.VideoLinkCompatibility.buildWvcHeaders(targetUrl, null, headers)
        val headerStrings = headerMap.map { "${it.key}: ${it.value}" }.toTypedArray()

        // Try Web Video Caster package directly
        val wvcIntent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.instantbits.cast.webvideo")
            if (isDirectStream) {
                setDataAndType(uri, mimeType)
            } else {
                data = uri
            }
            putExtra("title", title)
            putExtra("video_title", title)
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra("secure_uri", targetUrl)
            putExtra("video_url", targetUrl)
            putExtra("videoUrl", targetUrl)
            if (!poster.isNullOrBlank()) {
                putExtra("poster", poster)
            }
            putExtra("headers", headerStrings)
            putExtra("mime", mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(wvcIntent)
    } catch (_: Exception) {
        try {
            // Fallback to general video intent chooser (supports Web Video Caster, VLC, MX Player, BubbleUPnP, Cast apps)
            val mimeType = com.example.util.VideoLinkCompatibility.resolveMimeType(targetUrl) ?: "video/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (com.example.util.VideoLinkCompatibility.isDirectMediaStream(targetUrl)) {
                    setDataAndType(Uri.parse(targetUrl), mimeType)
                } else {
                    data = Uri.parse(targetUrl)
                }
                putExtra("title", title)
                putExtra("video_title", title)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra("secure_uri", targetUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Transmitir com Web Video Caster / Outros...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.instantbits.cast.webvideo")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }
}

@Composable
fun WebVideoCasterButton(
    streamUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Button(
        onClick = {
            launchWebVideoCaster(context, streamUrl, title)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1E88E5)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = modifier.height(34.dp).testTag("btn_web_video_caster")
    ) {
        Icon(
            imageVector = Icons.Default.Cast,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Web Video Caster",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatCastTime(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remMinutes = minutes % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, remMinutes, seconds)
    } else {
        String.format("%02d:%02d", remMinutes, seconds)
    }
}

@Composable
fun CastControlDialog(
    castUiState: CastUiState,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onDisconnect: () -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val currentPosMs = if (isSeeking) sliderValue.toLong() else castUiState.streamPositionMs
    val totalDurationMs = castUiState.streamDurationMs

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF101724),
            border = BorderStroke(1.dp, StadiumCyanSecondary.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("cast_control_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StadiumGreenPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CastConnected,
                                contentDescription = null,
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Controle da Transmissão",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = castUiState.deviceName ?: "TV Conectada",
                                style = MaterialTheme.typography.bodySmall,
                                color = StadiumCyanSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Title
                Text(
                    text = castUiState.currentTitle ?: "Mídia em Reprodução",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (!castUiState.currentSubtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = castUiState.currentSubtitle!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = StadiumCyanSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Barra de Progresso (Seek Bar)
                if (totalDurationMs > 0) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = if (isSeeking) sliderValue else castUiState.streamPositionMs.toFloat(),
                            onValueChange = {
                                isSeeking = true
                                sliderValue = it
                            },
                            onValueChangeFinished = {
                                onSeek(sliderValue.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = StadiumGreenPrimary,
                                activeTrackColor = StadiumGreenPrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatCastTime(currentPosMs),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = formatCastTime(totalDurationMs),
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                } else {
                    // Live / Stream indicator
                    Surface(
                        color = StadiumGreenPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StadiumGreenPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TRANSMISSÃO AO VIVO",
                                color = StadiumGreenPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Transport Controls: Voltar 10s - Play/Pause - Avançar 10s
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voltar 10s
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSeekBackward() }
                    ) {
                        IconButton(
                            onClick = onSeekBackward,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Voltar 10s",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "-10s",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Play / Pause Principal
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onTogglePlayPause() }
                    ) {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(StadiumGreenPrimary)
                        ) {
                            Icon(
                                imageVector = if (castUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (castUiState.isPlaying) "Pausar" else "Reproduzir",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (castUiState.isPlaying) "Pausar" else "Play",
                            color = StadiumGreenPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Avançar 10s
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSeekForward() }
                    ) {
                        IconButton(
                            onClick = onSeekForward,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Avançar 10s",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "+10s",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons: Disconnect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDisconnect()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = StadiumAccentRed
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TvOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Desconectar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ChromecastButton(
    castUiState: CastUiState,
    modifier: Modifier = Modifier,
    onCastConnectedClick: (() -> Unit)? = null,
    onMediaRouteButtonCreated: ((MediaRouteButton) -> Unit)? = null
) {
    val context = LocalContext.current
    val castManager = remember { CastManager.getInstance(context) }
    var showControlDialog by remember { mutableStateOf(false) }

    if (showControlDialog) {
        CastControlDialog(
            castUiState = castUiState,
            onDismiss = { showControlDialog = false },
            onTogglePlayPause = { castManager.togglePlayPause() },
            onSeek = { castManager.seekTo(it) },
            onSeekForward = { castManager.seekForward() },
            onSeekBackward = { castManager.seekBackward() },
            onDisconnect = { castManager.disconnect() }
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (castUiState.isConnected) StadiumCyanSecondary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(enabled = castUiState.isConnected) {
                if (onCastConnectedClick != null) {
                    onCastConnectedClick()
                } else {
                    showControlDialog = true
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Android MediaRouteButton handles device discovery & dialog popup automatically
            AndroidView(
                modifier = Modifier
                    .size(36.dp)
                    .testTag("chromecast_media_route_button"),
                factory = { context ->
                    val themeWrapper = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight)
                    MediaRouteButton(themeWrapper).apply {
                        CastButtonFactory.setUpMediaRouteButton(context, this)
                        onMediaRouteButtonCreated?.invoke(this)
                    }
                },
                update = { button ->
                    onMediaRouteButtonCreated?.invoke(button)
                }
            )

            AnimatedVisibility(
                visible = castUiState.isConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StadiumGreenPrimary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = castUiState.deviceName ?: "TV Conectada",
                        color = StadiumCyanSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
