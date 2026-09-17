package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import coil.compose.AsyncImage
import com.example.data.MoviesRepository
import com.example.data.models.OFFLINE_FALLBACK_URL
import com.example.data.models.isOfflineFallbackUrl
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.cast.CastUiState
import com.example.data.models.PlayableVideo
import com.example.ui.components.ChromecastButton
import com.example.ui.components.WebVideoCasterButton
import com.example.ui.components.launchWebVideoCaster
import com.example.ui.theme.StadiumAccentRed
import com.example.ui.theme.StadiumCyanSecondary
import com.example.ui.theme.StadiumGreenPrimary
import kotlinx.coroutines.delay

class WebPlayerBridge(
    private val onProgress: (currentTimeMs: Long, durationMs: Long) -> Unit,
    private val onEnded: () -> Unit,
    private val onPlayStateChanged: (Boolean) -> Unit,
    private val onMediaDetected: ((url: String, mimeType: String?) -> Unit)? = null
) {
    @JavascriptInterface
    fun onVideoProgress(currentTimeSeconds: Double, durationSeconds: Double) {
        val curMs = (currentTimeSeconds * 1000).toLong().coerceAtLeast(0L)
        val durMs = (durationSeconds * 1000).toLong().coerceAtLeast(0L)
        onProgress(curMs, durMs)
    }

    @JavascriptInterface
    fun onVideoEnded() {
        onEnded()
    }

    @JavascriptInterface
    fun onVideoPlayState(isPlaying: Boolean) {
        onPlayStateChanged(isPlaying)
    }

    @JavascriptInterface
    fun onMediaFound(streamUrl: String, mimeType: String?) {
        if (streamUrl.isNotBlank() && streamUrl.startsWith("http")) {
            onMediaDetected?.invoke(streamUrl, mimeType)
        }
    }
}

@Composable
fun NextEpisodeBannerCard(
    nextEpisodeTitle: String?,
    isPlaybackEnded: Boolean,
    onPlayNextEpisode: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13111C).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFFD500F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.testTag("btn_bottom_next_episode_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPlaybackEnded) StadiumGreenPrimary else Color(0xFFD500F9))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPlaybackEnded) "Episódio Finalizado" else "Próximo Episódio a seguir",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                if (!nextEpisodeTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = nextEpisodeTitle,
                        color = StadiumCyanSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onPlayNextEpisode,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD500F9)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("btn_autoplay_next_episode")
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Próximo Episódio",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Próximo Ep.",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
                if (onDismiss != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar aviso",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    video: PlayableVideo,
    castUiState: CastUiState,
    hasNextEpisode: Boolean = false,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: () -> Unit = {},
    onBack: () -> Unit,
    onCastToggle: () -> Unit,
    onSeekCast: (Long) -> Unit = {},
    onSeekCastForward: () -> Unit = {},
    onSeekCastBackward: () -> Unit = {},
    onDisconnectCast: () -> Unit,
    onPlaybackError: (String) -> Unit = {},
    onCastVideo: (String?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isPlaybackEnded by remember { mutableStateOf(false) }
    var isNextEpisodeDismissed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // State for media sniffed from WebView
    var sniffedMediaUrl by remember { mutableStateOf<String?>(null) }
    var sniffedMimeType by remember { mutableStateOf<String?>(null) }
    var mediaRouteButtonRef by remember { mutableStateOf<androidx.mediarouter.app.MediaRouteButton?>(null) }
    
    val activeStreamUrl = sniffedMediaUrl ?: video.streamUrl

    // Auto-detect if direct HLS / video media URL is available or if web player should be primary
    val hasDirectMediaUrl = remember(activeStreamUrl) {
        val url = activeStreamUrl.lowercase()
        com.example.util.VideoLinkCompatibility.isDirectMediaStream(url) &&
        !url.contains(".php") && !url.contains("cxtv.com.br/tv-ao-vivo") && !url.contains("temporariofutemais") || sniffedMediaUrl != null
    }
    val initialHasDirectMedia = remember(video.streamUrl) {
        com.example.util.VideoLinkCompatibility.isDirectMediaStream(video.streamUrl.lowercase())
    }
    var useWebviewPlayer by remember(video.streamUrl, video.embedUrl, video.forceWebPlayer) { 
        mutableStateOf(video.forceWebPlayer || !initialHasDirectMedia || !video.embedUrl.isNullOrBlank()) 
    }
    var activeEmbedUrl by remember(video.embedUrl, video.streamUrl) {
        mutableStateOf(video.embedUrl?.takeIf { it.isNotBlank() } ?: video.streamUrl)
    }

    val movieImdbId = remember(video.streamUrl, video.embedUrl, video.id, video.title) {
        com.example.data.MovieMetadataResolver.extractImdbId(video.streamUrl)
            ?: com.example.data.MovieMetadataResolver.extractImdbId(video.embedUrl ?: "")
            ?: com.example.data.MovieMetadataResolver.extractImdbId(video.id)
            ?: com.example.data.MovieMetadataResolver.extractImdbId(video.title)
    }

    val availableServers = remember(movieImdbId, video.subtitle, video.category, video.streamUrl, video.embedUrl) {
        if (!movieImdbId.isNullOrBlank()) {
            val isSeries = video.subtitle?.contains("Série", ignoreCase = true) == true || video.category?.contains("Série", ignoreCase = true) == true
            listOf(
                "AutoEmbed" to if (isSeries) "https://autoembed.co/tv/imdb/$movieImdbId-1-1" else "https://autoembed.co/movie/imdb/$movieImdbId",
                "VidSrc" to if (isSeries) "https://vidsrcme.ru/embed/tv?imdb=$movieImdbId" else "https://vidsrcme.ru/embed/movie?imdb=$movieImdbId",
                "MultiEmbed" to "https://multiembed.mov/?video_id=$movieImdbId",
                "SuperFlix" to if (isSeries) "https://superflixapi.beer/serie/$movieImdbId/1/1" else "https://superflixapi.beer/filme/$movieImdbId",
                "Streamtape" to "https://streamtape.com/e/$movieImdbId",
                "2Embed" to if (isSeries) "https://www.2embed.cc/embedseries/$movieImdbId/1/1" else "https://www.2embed.cc/embed/$movieImdbId"
            )
        } else {
            emptyList()
        }
    }
    var showServerDialog by remember { mutableStateOf(false) }

    val targetCastUrl = remember(sniffedMediaUrl, video.streamUrl, activeEmbedUrl, video.embedUrl, video.isLive) {
        val validSniffed = sniffedMediaUrl?.takeIf { !com.example.util.VideoLinkCompatibility.isAdVideoUrl(it) }
        val validStream = video.streamUrl.takeIf { it.isNotBlank() && !com.example.util.VideoLinkCompatibility.isAdVideoUrl(it) }
        val validEmbed = video.embedUrl?.takeIf { it.isNotBlank() && !com.example.util.VideoLinkCompatibility.isAdVideoUrl(it) }
        
        if (video.isLive) {
            // Para canais ao vivo: transmite exatamente o link de transmissão cadastrado no canal pelo administrador
            validStream ?: validSniffed ?: validEmbed ?: activeEmbedUrl.takeIf { !com.example.util.VideoLinkCompatibility.isAdVideoUrl(it) } ?: ""
        } else {
            // Para filmes e séries: prioriza a mídia sniffada se disponível
            validSniffed ?: validStream ?: validEmbed ?: activeEmbedUrl.takeIf { !com.example.util.VideoLinkCompatibility.isAdVideoUrl(it) } ?: ""
        }
    }

    LaunchedEffect(castUiState.isConnected, targetCastUrl) {
        if (castUiState.isConnected && targetCastUrl.isNotBlank()) {
            onCastVideo(targetCastUrl)
        }
    }

    var isSniffingMedia by remember { mutableStateOf(false) }
    val isMovieOrSeries = !video.isLive

    LaunchedEffect(sniffedMediaUrl) {
        if (sniffedMediaUrl != null && isMovieOrSeries) {
            useWebviewPlayer = false
            isSniffingMedia = false
        }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webViewReloadKey by remember { mutableIntStateOf(0) }
    var isWebViewLoading by remember { mutableStateOf(true) }
    var isWebViewError by remember { mutableStateOf(false) }
    

    LaunchedEffect(video.streamUrl, video.embedUrl, video.id) {
        activeEmbedUrl = video.embedUrl?.takeIf { it.isNotBlank() } ?: video.streamUrl
        isPlaybackEnded = false
        isNextEpisodeDismissed = false
        currentPositionMs = 0L
        durationMs = 0L
        errorMessage = null
        sniffedMediaUrl = null
        sniffedMimeType = null
        
        if (activeEmbedUrl.contains("embedplayapi.top/embed")) {
            isWebViewLoading = true
            val abysUrl = com.example.data.AbysResolver.resolveAbysUrl(activeEmbedUrl)
            if (abysUrl != null) {
                activeEmbedUrl = abysUrl
            }
        }
    }

    LaunchedEffect(webViewReloadKey, activeEmbedUrl) {
        isWebViewLoading = true
        isSniffingMedia = true
        kotlinx.coroutines.delay(10000)
        isSniffingMedia = false
        isWebViewLoading = false
    }

    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || isFullscreen
    var areExtraActionsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isLandscape, isFullscreen) {
        areExtraActionsExpanded = false
    }

    // Toggle screen orientation
    fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        areExtraActionsExpanded = false
        activity?.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto hide controls after 4 seconds when not interacting with expanded options
    LaunchedEffect(showControls, isPlaying, areExtraActionsExpanded) {
        if (showControls && isPlaying && !areExtraActionsExpanded) {
            delay(4000)
            showControls = false
        }
    }

    // Periodic progress query for Web Player to ensure episode end detection
    LaunchedEffect(useWebviewPlayer, webViewInstance) {
        if (useWebviewPlayer) {
            while (true) {
                delay(1200)
                try {
                    webViewInstance?.evaluateJavascript("""
                        (function() {
                            try {
                                var v = document.querySelector('video');
                                if (v && window.AndroidBridge && !isNaN(v.duration) && v.duration > 0) {
                                    window.AndroidBridge.onVideoProgress(v.currentTime, v.duration);
                                    if (v.ended) { window.AndroidBridge.onVideoEnded(); }
                                }
                            } catch(e){}
                        })();
                    """.trimIndent(), null)
                } catch (_: Exception) {}
            }
        }
    }

    val isEpisodeEnding = hasNextEpisode && !isNextEpisodeDismissed && (
        isPlaybackEnded ||
        (durationMs > 15_000L && (currentPositionMs >= durationMs - 45_000L || (durationMs > 60_000L && (currentPositionMs.toDouble() / durationMs.toDouble()) >= 0.93)))
    )

    // Create and configure ExoPlayer with robust buffer, decoders and data sources
    val exoPlayer = remember(activeStreamUrl, useWebviewPlayer) {
        if (useWebviewPlayer || !hasDirectMediaUrl) null
        else {
            val headerMap = com.example.util.VideoLinkCompatibility.buildWvcHeaders(
                streamUrl = activeStreamUrl,
                embedUrl = video.embedUrl,
                customHeaders = video.headers
            )
            val userAgent = headerMap["User-Agent"] ?: "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
                .setConnectTimeoutMs(25000)
                .setReadTimeoutMs(25000)
                .setDefaultRequestProperties(headerMap)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val uri = Uri.parse(activeStreamUrl)
            val mimeType = com.example.util.VideoLinkCompatibility.resolveMimeType(activeStreamUrl)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)
            if (!mimeType.isNullOrBlank()) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            val mediaItem = mediaItemBuilder.build()
            val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000, // minBufferMs
                    50_000, // maxBufferMs
                    1_500,  // bufferForPlaybackMs
                    3_000   // bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        .setAllowVideoNonSeamlessAdaptiveness(true)
                        .setExceedVideoConstraintsIfNecessary(true)
                        .setExceedRendererCapabilitiesIfNecessary(true)
                )
            }

            ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    playWhenReady = true
                    prepare()
                }
        }
    }

    // Player events listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isPlaybackEnded = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                    durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                val rawMsg = error.localizedMessage ?: ""
                val is403 = rawMsg.contains("403") || error.errorCodeName.contains("403") || error.cause?.message?.contains("403") == true
                val is404 = rawMsg.contains("404") || error.cause?.message?.contains("404") == true
                
                // Automatically fallback to WebView player on playback error if using native player
                if (!useWebviewPlayer) {
                    useWebviewPlayer = true
                    errorMessage = "Player nativo falhou. Alternando automaticamente para Web Player..."
                } else {
                    errorMessage = when {
                        is403 -> "Servidor bloqueou requisição (Erro 403). Tente o Web Video Caster."
                        is404 -> "Arquivo não encontrado (Erro 404)."
                        else -> "Erro de reprodução: ${error.localizedMessage ?: "Falha ao carregar stream"}"
                    }
                }
                isBuffering = false
                onPlaybackError(video.id)
            }
        }

        exoPlayer?.addListener(listener)

        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Periodic progress update
    LaunchedEffect(exoPlayer, isPlaying) {
        while (true) {
            exoPlayer?.let {
                currentPositionMs = it.currentPosition.coerceAtLeast(0L)
                if (it.duration > 0 && it.duration != C.TIME_UNSET) {
                    durationMs = it.duration
                }
            }
            delay(1000)
        }
    }

    val playerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen_root")
            .focusRequester(playerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            showControls = true
                            if (exoPlayer != null) {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            showControls = true
                            exoPlayer?.play()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            showControls = true
                            exoPlayer?.pause()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            showControls = true
                            exoPlayer?.let { player ->
                                player.seekTo((player.currentPosition + 10_000L).coerceAtMost(if (player.duration > 0) player.duration else Long.MAX_VALUE))
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            showControls = true
                            exoPlayer?.let { player ->
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showControls = true
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Hidden Chromecast button instance to bind MediaRouteButton reference for 3-dots menu
        Box(modifier = Modifier.size(1.dp).alpha(0.001f)) {
            ChromecastButton(
                castUiState = castUiState,
                onCastConnectedClick = { onCastToggle() },
                onMediaRouteButtonCreated = { button -> mediaRouteButtonRef = button }
            )
        }

        val isOfflineItem = !video.isWorking ||
                isOfflineFallbackUrl(video.streamUrl) ||
                isOfflineFallbackUrl(activeEmbedUrl) ||
                isOfflineFallbackUrl(video.embedUrl)

        if (castUiState.isConnected) {
            // ==========================================
            // CHROMECAST REMOTE PLAYBACK HUB
            // ==========================================
            CastPlaybackHub(
                video = video,
                castUiState = castUiState,
                streamUrl = targetCastUrl,
                hasNextEpisode = hasNextEpisode,
                nextEpisodeTitle = nextEpisodeTitle,
                onPlayNextEpisode = onPlayNextEpisode,
                onReloadCast = {
                    if (targetCastUrl.isNotBlank()) {
                        onCastVideo(targetCastUrl)
                    }
                },
                onBack = onBack,
                onDisconnect = onDisconnectCast,
                onTogglePlayPause = { onCastToggle() },
                onSeekCast = onSeekCast,
                onSeekCastForward = onSeekCastForward,
                onSeekCastBackward = onSeekCastBackward
            )
        } else if (isOfflineItem) {
            // ==========================================
            // CANAL / FILME / SÉRIE INDISPONÍVEL
            // ==========================================
            UnavailablePlaybackScreen(
                video = video,
                onBack = onBack
            )
        } else if (useWebviewPlayer) {
            // ==========================================
            // ISOLATED WEB PLAYER (PURE VIDEO ONLY)
            // ==========================================
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                if (!isWebViewError) {
                    key(activeEmbedUrl, webViewReloadKey) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                try {
                                    java.io.File(ctx.cacheDir, "app_webview/Default/HTTP Cache/Code Cache/js").mkdirs()
                                    java.io.File(ctx.cacheDir, "app_webview/Default/HTTP Cache/Code Cache/wasm").mkdirs()
                                    java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
                                    java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
                                    java.io.File(ctx.dataDir, "app_webview/Default/HTTP Cache/Code Cache/js").mkdirs()
                                    java.io.File(ctx.dataDir, "app_webview/Default/HTTP Cache/Code Cache/wasm").mkdirs()
                                } catch (_: Exception) {}

                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    try {
                                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                    } catch (_: Exception) {}

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                settings.setSupportMultipleWindows(true)
                                settings.setJavaScriptCanOpenWindowsAutomatically(true)
                                settings.setGeolocationEnabled(false)
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.allowContentAccess = true
                                settings.allowFileAccess = false
                                try {
                                    settings.safeBrowsingEnabled = false
                                } catch (_: Exception) {}
                                
                                try {
                                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                } catch (_: Exception) {}

                                // Bridge for progress, episode end detection, and video stream sniffing
                                addJavascriptInterface(
                                    WebPlayerBridge(
                                        onProgress = { cur, dur ->
                                            currentPositionMs = cur
                                            if (dur > 0L) {
                                                durationMs = dur
                                            }
                                            if (dur > 0L && cur >= dur - 1500L) {
                                                isPlaybackEnded = true
                                            }
                                        },
                                        onEnded = {
                                            isPlaybackEnded = true
                                        },
                                        onPlayStateChanged = { playing ->
                                            isPlaying = playing
                                        },
                                        onMediaDetected = { sniffedUrl, mime ->
                                            if (!sniffedUrl.isNullOrBlank() && sniffedMediaUrl != sniffedUrl) {
                                                sniffedMediaUrl = sniffedUrl
                                                sniffedMimeType = mime
                                            }
                                        }
                                    ),
                                    "AndroidBridge"
                                )

                                webChromeClient = object : WebChromeClient() {
                                    override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                        try {
                                            request?.grant(request.resources)
                                        } catch (_: Exception) {}
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?
                                    ): Boolean {
                                        // Block popups for movies and series
                                        if (isMovieOrSeries) {
                                            return false
                                        }
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        transport?.webView = view
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?
                                    ): android.webkit.WebResourceResponse? {
                                        val reqUrl = request?.url?.toString() ?: ""
                                        
                                        // NEVER block direct playable streams or HLS/TS chunks
                                        if (com.example.util.VideoLinkCompatibility.isDirectMediaStream(reqUrl)) {
                                            if (sniffedMediaUrl != reqUrl && !com.example.util.VideoLinkCompatibility.isAdVideoUrl(reqUrl)) {
                                                post {
                                                    sniffedMediaUrl = reqUrl
                                                    sniffedMimeType = com.example.util.VideoLinkCompatibility.resolveMimeType(reqUrl)
                                                }
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }

                                        // Block ad domains only for movies/series when NOT a media stream
                                        if (isMovieOrSeries && com.example.util.AdBlocker.isAd(reqUrl)) {
                                            return com.example.util.AdBlocker.createEmptyResource()
                                        }

                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        // Block intrusive redirects and store schemes
                                        if (reqUrl.startsWith("intent:") || reqUrl.startsWith("market:") || reqUrl.startsWith("whatsapp:") || reqUrl.startsWith("tg:") || reqUrl.startsWith("mailto:") || reqUrl.startsWith("tel:")) {
                                            return true
                                        }
                                        // Block ad redirects for movies and series
                                        if (isMovieOrSeries && com.example.util.AdBlocker.isAd(reqUrl)) {
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isWebViewLoading = true
                                        isWebViewError = false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                        try {
                                            view?.evaluateJavascript("""
                                                (function() {
                                                    try {
                                                        if (!window.__networkHooked) {
                                                            window.__networkHooked = true;
                                                            var origOpen = XMLHttpRequest.prototype.open;
                                                            XMLHttpRequest.prototype.open = function() {
                                                                var url = arguments[1];
                                                                if (url && typeof url === 'string' && (url.indexOf('.m3u8') !== -1 || url.indexOf('.mp4') !== -1)) {
                                                                    if (window.AndroidBridge) window.AndroidBridge.onMediaFound(url, '');
                                                                }
                                                                origOpen.apply(this, arguments);
                                                            };
                                                            var origFetch = window.fetch;
                                                            window.fetch = function() {
                                                                var url = arguments[0];
                                                                if (url && typeof url === 'string' && (url.indexOf('.m3u8') !== -1 || url.indexOf('.mp4') !== -1)) {
                                                                    if (window.AndroidBridge) window.AndroidBridge.onMediaFound(url, '');
                                                                }
                                                                return origFetch.apply(this, arguments);
                                                            };
                                                        }

                                                        var playBtns = document.querySelectorAll('.play-btn, .vjs-big-play-button, #play-button, .jw-display-icon-container, [aria-label="Play"], .plyr__control--overlaid');
                                                        playBtns.forEach(function(btn) { try { btn.click(); } catch(e){} });
                                                        
                                                        // Click the center of the screen to trigger any overlays (like ABYS layer)
                                                        var trigger = document.getElementById('trigger');
                                                        if (trigger) { trigger.click(); }
                                                        document.body.click();

                                                        function hookVideos() {
                                                            var vids = document.querySelectorAll('video');
                                                            for (var i = 0; i < vids.length; i++) {
                                                                var v = vids[i];
                                                                var videoSrc = v.currentSrc || v.src;
                                                                if (videoSrc && videoSrc.indexOf('http') === 0 && window.AndroidBridge) {
                                                                    window.AndroidBridge.onMediaFound(videoSrc, v.type || '');
                                                                }
                                                                var sources = v.querySelectorAll('source');
                                                                for (var s = 0; s < sources.length; s++) {
                                                                    var sUrl = sources[s].src;
                                                                    if (sUrl && sUrl.indexOf('http') === 0 && window.AndroidBridge) {
                                                                        window.AndroidBridge.onMediaFound(sUrl, sources[s].type || '');
                                                                    }
                                                                }
                                                                if (!v.__androidBridgeHooked) {
                                                                    v.__androidBridgeHooked = true;
                                                                    v.addEventListener('timeupdate', function() {
                                                                        if (window.AndroidBridge && !isNaN(this.duration) && this.duration > 0) {
                                                                            window.AndroidBridge.onVideoProgress(this.currentTime, this.duration);
                                                                        }
                                                                    });
                                                                    v.addEventListener('ended', function() {
                                                                        if (window.AndroidBridge) {
                                                                            window.AndroidBridge.onVideoEnded();
                                                                        }
                                                                    });
                                                                    v.addEventListener('play', function() {
                                                                        if (window.AndroidBridge) {
                                                                            window.AndroidBridge.onVideoPlayState(true);
                                                                        }
                                                                    });
                                                                    v.addEventListener('pause', function() {
                                                                        if (window.AndroidBridge) {
                                                                            window.AndroidBridge.onVideoPlayState(false);
                                                                        }
                                                                    });
                                                                }
                                                                if (window.AndroidBridge && !isNaN(v.duration) && v.duration > 0 && !v.paused) {
                                                                    window.AndroidBridge.onVideoProgress(v.currentTime, v.duration);
                                                                }
                                                            }
                                                        }

                                                        // Hook window.open to block popups
                                                        window.open = function() {
                                                            return null;
                                                        };

                                                        hookVideos();
                                                        if (!window.__androidBridgeWatcher) {
                                                            window.__androidBridgeWatcher = setInterval(hookVideos, 1000);
                                                        }
                                                    } catch(e) {}
                                                })();
                                            """.trimIndent(), null)
                                        } catch (_: Exception) {}
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true) {
                                            isWebViewLoading = false
                                        }
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: android.webkit.RenderProcessGoneDetail?
                                    ): Boolean {
                                        // Crucial: return true so Chromium renderer crashes do not kill the host app
                                        try {
                                            view?.let {
                                                (it.parent as? ViewGroup)?.removeView(it)
                                                it.destroy()
                                            }
                                        } catch (_: Exception) {}
                                        webViewInstance = null
                                        isWebViewLoading = false
                                        isWebViewError = true
                                        return true
                                    }
                                }

                                val targetStream = activeEmbedUrl.ifBlank { video.streamUrl }
                                val isFutemais = targetStream.contains("futemais", ignoreCase = true) || targetStream.contains("temporariofutemais", ignoreCase = true)
                                val isDirectMedia = (
                                    targetStream.contains(".m3u8", ignoreCase = true) || 
                                    targetStream.contains(".mp4", ignoreCase = true) ||
                                    targetStream.contains(".ts", ignoreCase = true)
                                ) && !isFutemais
                                val isHttpUrl = targetStream.startsWith("http://", ignoreCase = true) || targetStream.startsWith("https://", ignoreCase = true)

                                if (isFutemais) {
                                    val headers = mutableMapOf("Referer" to "https://futemais.link/")
                                    video.headers?.let { headers.putAll(it) }
                                    loadUrl(targetStream, headers)
                                } else if (isDirectMedia) {
                                    val htmlData = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <meta charset="utf-8">
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                                            <style>
                                                * { box-sizing: border-box; margin: 0; padding: 0; }
                                                html, body { width: 100vw; height: 100vh; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                                                video { width: 100%; height: 100%; object-fit: contain; background: #000; }
                                            </style>
                                        </head>
                                        <body>
                                            <video id="webPlayer" controls autoplay playsinline></video>
                                            <script>
                                                var video = document.getElementById('webPlayer');
                                                var sourceUrl = '$targetStream';
                                                video.addEventListener('timeupdate', function() {
                                                    if (window.AndroidBridge && !isNaN(video.duration) && video.duration > 0) {
                                                        window.AndroidBridge.onVideoProgress(video.currentTime, video.duration);
                                                    }
                                                });
                                                video.addEventListener('ended', function() {
                                                    if (window.AndroidBridge) {
                                                        window.AndroidBridge.onVideoEnded();
                                                    }
                                                });
                                                if (Hls.isSupported()) {
                                                    var hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                                                    hls.loadSource(sourceUrl);
                                                    hls.attachMedia(video);
                                                    hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                                        video.play().catch(function(e) { console.log(e); });
                                                    });
                                                    hls.on(Hls.Events.ERROR, function(event, data) {
                                                        if (data.fatal) {
                                                            switch (data.type) {
                                                                case Hls.ErrorTypes.NETWORK_ERROR:
                                                                    hls.startLoad();
                                                                    break;
                                                                case Hls.ErrorTypes.MEDIA_ERROR:
                                                                    hls.recoverMediaError();
                                                                    break;
                                                                default:
                                                                    hls.destroy();
                                                                    break;
                                                                }
                                                        }
                                                    });
                                                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                                    video.src = sourceUrl;
                                                    video.play().catch(function(e) { console.log(e); });
                                                } else {
                                                    video.src = sourceUrl;
                                                    video.play().catch(function(e) { console.log(e); });
                                                }
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL(targetStream, htmlData, "text/html", "UTF-8", null)
                                } else if (isHttpUrl) {
                                    val headers = mutableMapOf<String, String>()
                                    video.headers?.let { headers.putAll(it) }
                                    if (headers.isNotEmpty()) {
                                        loadUrl(targetStream, headers)
                                    } else {
                                        loadUrl(targetStream)
                                    }
                                } else {
                                    val iframeHtml = """
                                        <!DOCTYPE html>
                                        <html>
                                        <head>
                                            <meta charset="utf-8">
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                            <style>
                                                * { box-sizing: border-box; margin: 0; padding: 0; }
                                                html, body { width: 100vw; height: 100vh; background: #000; overflow: hidden; }
                                                iframe { width: 100%; height: 100%; border: none; background: #000; }
                                            </style>
                                        </head>
                                        <body>
                                            <iframe src="$targetStream" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"></iframe>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL(targetStream, iframeHtml, "text/html", "UTF-8", null)
                                }
                            }.also { webViewInstance = it }
                        },
                        update = { webView ->
                            webViewInstance = webView
                        },
                        onRelease = { webView ->
                            try {
                                webView.stopLoading()
                                webView.loadUrl("about:blank")
                                webView.clearHistory()
                                webView.removeAllViews()
                                webView.destroy()
                            } catch (_: Exception) {}
                            if (webViewInstance === webView) {
                                webViewInstance = null
                            }
                        }
                    )
                }
            }

                if ((isWebViewLoading || isSniffingMedia) && !isWebViewError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = StadiumGreenPrimary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Buscando vídeo...",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                if (isWebViewError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 420.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Erro no reprodutor",
                                    tint = StadiumGreenPrimary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Falha no servidor atual",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "O servidor selecionado demorou para responder ou falhou no carregamento. Tente outro servidor ou use o Web Video Caster.",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            webViewReloadKey++
                                            isWebViewLoading = true
                                            isWebViewError = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Recarregar", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                    if (availableServers.isNotEmpty()) {
                                        Button(
                                            onClick = { showServerDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Trocar Servidor", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    TextButton(
                                        onClick = { useWebviewPlayer = false },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Player Nativo", color = StadiumGreenPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Top overlay for Webview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (availableServers.isNotEmpty()) {
                            TextButton(
                                onClick = { showServerDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = StadiumCyanSecondary),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = "Servidores", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Servidores", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        IconButton(
                            onClick = { toggleFullscreen() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("webview_rotate_btn")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.ScreenRotation,
                                contentDescription = "Rotacionar Tela",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box {
                            IconButton(
                                onClick = { areExtraActionsExpanded = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .testTag("btn_expand_webview_options")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Mais opções",
                                    tint = Color.White
                                )
                            }
                            
                            androidx.compose.material3.DropdownMenu(
                                expanded = areExtraActionsExpanded,
                                onDismissRequest = { areExtraActionsExpanded = false },
                                modifier = Modifier.background(Color(0xFF13111C))
                            ) {
                                if (availableServers.isNotEmpty()) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("Trocar Servidor (${availableServers.size} disponíveis)", color = StadiumCyanSecondary) },
                                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = StadiumCyanSecondary) },
                                        onClick = {
                                            areExtraActionsExpanded = false
                                            showServerDialog = true
                                        }
                                    )
                                }
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Recarregar", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White) },
                                    onClick = {
                                        areExtraActionsExpanded = false
                                        isWebViewLoading = true
                                        webViewInstance?.reload()
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Player Nativo", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = Color.White) },
                                    onClick = {
                                        areExtraActionsExpanded = false
                                        useWebviewPlayer = false 
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Web Video Caster", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFF1E88E5)) },
                                    onClick = {
                                        areExtraActionsExpanded = false
                                        launchWebVideoCaster(context, targetCastUrl, video.title)
                                    }
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (castUiState.isConnected) "TV Conectada: ${castUiState.deviceName ?: "Cast"}" else "Transmitir (Cast)",
                                            color = if (castUiState.isConnected) StadiumCyanSecondary else Color.White
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (castUiState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                                            contentDescription = null,
                                            tint = if (castUiState.isConnected) StadiumCyanSecondary else Color.White
                                        )
                                    },
                                    onClick = {
                                        areExtraActionsExpanded = false
                                        if (castUiState.isConnected) {
                                            onCastToggle()
                                        } else {
                                            val triggered = mediaRouteButtonRef?.performClick() ?: false
                                            if (!triggered) {
                                                onCastVideo(targetCastUrl)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }



                // Floating Next Episode Overlay for Web Player
                AnimatedVisibility(
                    visible = isEpisodeEnding,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .widthIn(max = 520.dp)
                ) {
                    NextEpisodeBannerCard(
                        nextEpisodeTitle = nextEpisodeTitle,
                        isPlaybackEnded = isPlaybackEnded,
                        onPlayNextEpisode = onPlayNextEpisode,
                        onDismiss = { isNextEpisodeDismissed = true }
                    )
                }
            }
        } else {
            // ==========================================
            // NATIVE MEDIA3 EXOPLAYER
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showControls = !showControls
                    }
            ) {
                // ExoPlayer Surface View
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        try {
                            val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null, false) as PlayerView
                            view.apply {
                                player = exoPlayer
                                useController = false
                                useArtwork = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                keepScreenOn = true
                                this.resizeMode = resizeMode
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        } catch (_: Exception) {
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                useArtwork = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                keepScreenOn = true
                                this.resizeMode = resizeMode
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != exoPlayer) {
                            playerView.player = exoPlayer
                        }
                        playerView.resizeMode = resizeMode
                    },
                    onRelease = { playerView ->
                        playerView.player = null
                    }
                )

                // Buffering Indicator
                if (isBuffering) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = StadiumGreenPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Error Overlay
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = if (video.isLive) "Falha ao carregar a transmissão" else "Falha ao carregar o vídeo",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tentar Novamente", color = Color.Black, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { 
                                        errorMessage = null
                                        useWebviewPlayer = true 
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StadiumGreenPrimary),
                                    border = BorderStroke(1.dp, StadiumGreenPrimary)
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = StadiumGreenPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Player Web", color = StadiumGreenPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.7f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    ) {
                        // Top Bar Controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .testTag("player_back_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Voltar",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = video.title,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = video.subtitle,
                                        color = StadiumCyanSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Screen rotation button in top bar
                                IconButton(
                                    onClick = { toggleFullscreen() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .testTag("player_top_rotate_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.ScreenRotation,
                                        contentDescription = "Rotacionar Tela",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box {
                                    IconButton(
                                        onClick = { areExtraActionsExpanded = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .testTag("btn_expand_player_options")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Mais opções",
                                            tint = Color.White
                                        )
                                    }

                                    androidx.compose.material3.DropdownMenu(
                                        expanded = areExtraActionsExpanded,
                                        onDismissRequest = { areExtraActionsExpanded = false },
                                        modifier = Modifier.background(Color(0xFF13111C))
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Recarregar", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White) },
                                            onClick = {
                                                areExtraActionsExpanded = false
                                                errorMessage = null
                                                exoPlayer?.prepare()
                                                exoPlayer?.play()
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Formato de Tela", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color.White) },
                                            onClick = {
                                                areExtraActionsExpanded = false
                                                resizeMode = when (resizeMode) {
                                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                }
                                            }
                                        )
                                        if (!video.embedUrl.isNullOrBlank() || video.streamUrl.isNotBlank()) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Player Web", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = Color.White) },
                                                onClick = {
                                                    areExtraActionsExpanded = false
                                                    useWebviewPlayer = true 
                                                }
                                            )
                                        }
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text("Web Video Caster", color = Color.White) },
                                            leadingIcon = { Icon(Icons.Default.Cast, contentDescription = null, tint = Color(0xFF1E88E5)) },
                                            onClick = {
                                                areExtraActionsExpanded = false
                                                launchWebVideoCaster(context, targetCastUrl, video.title)
                                            }
                                        )
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (castUiState.isConnected) "TV Conectada: ${castUiState.deviceName ?: "Cast"}" else "Transmitir (Cast)",
                                                    color = if (castUiState.isConnected) StadiumCyanSecondary else Color.White
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (castUiState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                                                    contentDescription = null,
                                                    tint = if (castUiState.isConnected) StadiumCyanSecondary else Color.White
                                                )
                                            },
                                            onClick = {
                                                areExtraActionsExpanded = false
                                                if (castUiState.isConnected) {
                                                    onCastToggle()
                                                } else {
                                                    val triggered = mediaRouteButtonRef?.performClick() ?: false
                                                    if (!triggered) {
                                                        onCastVideo(targetCastUrl)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Center Controls (10s back, Play/Pause, 10s forward)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    exoPlayer?.let {
                                        it.seekTo((it.currentPosition - 10000).coerceAtLeast(0L))
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Voltar 10 segundos",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(StadiumGreenPrimary, StadiumCyanSecondary)
                                        )
                                    )
                                    .clickable {
                                        if (isPlaying) exoPlayer?.pause() else exoPlayer?.play()
                                    }
                                    .testTag("player_play_pause_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    exoPlayer?.let {
                                        it.seekTo(it.currentPosition + 10000)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Avançar 10 segundos",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Bottom Controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Bottom Next Episode Banner - appears at the end of the episode
                            if (isEpisodeEnding) {
                                NextEpisodeBannerCard(
                                    nextEpisodeTitle = nextEpisodeTitle,
                                    isPlaybackEnded = isPlaybackEnded,
                                    onPlayNextEpisode = onPlayNextEpisode,
                                    onDismiss = { isNextEpisodeDismissed = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                )
                            }

                            // Progress bar (if not live or has duration)
                            if (durationMs > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatTime(currentPositionMs),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )

                                    Slider(
                                        value = currentPositionMs.toFloat(),
                                        onValueChange = { newPos ->
                                            currentPositionMs = newPos.toLong()
                                            exoPlayer?.seekTo(newPos.toLong())
                                        },
                                        valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = StadiumGreenPrimary,
                                            activeTrackColor = StadiumGreenPrimary,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                        )
                                    )

                                    Text(
                                        text = formatTime(durationMs),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = StadiumAccentRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StadiumAccentRed.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(StadiumAccentRed)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (video.isLive) "TRANSMISSÃO AO VIVO" else "VÍDEO",
                                            color = StadiumAccentRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { toggleFullscreen() },
                                    modifier = Modifier.testTag("fullscreen_toggle_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Tela Cheia",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating Next Episode Overlay for Native Player when controls are hidden
                AnimatedVisibility(
                    visible = isEpisodeEnding && !showControls,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .widthIn(max = 520.dp)
                ) {
                    NextEpisodeBannerCard(
                        nextEpisodeTitle = nextEpisodeTitle,
                        isPlaybackEnded = isPlaybackEnded,
                        onPlayNextEpisode = onPlayNextEpisode,
                        onDismiss = { isNextEpisodeDismissed = true }
                    )
                }
            }
        }
    }

    if (showServerDialog && availableServers.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = Color(0xFF13111C),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = StadiumGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Selecionar Servidor", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Se o filme não carregar ou falhar, escolha outro servidor de reprodução:",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    availableServers.forEachIndexed { index, (name, url) ->
                        val isSelected = activeEmbedUrl == url
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) StadiumGreenPrimary.copy(alpha = 0.2f) else Color(0xFF1E1B2E)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = if (isSelected) BorderStroke(1.dp, StadiumGreenPrimary) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeEmbedUrl = url
                                    useWebviewPlayer = true
                                    webViewReloadKey++
                                    isWebViewLoading = true
                                    isWebViewError = false
                                    showServerDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.PlayArrow else Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = if (isSelected) StadiumGreenPrimary else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Servidor ${index + 1}: $name",
                                            color = if (isSelected) StadiumGreenPrimary else Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = if (index == 0) "Mais rápido e recomendado" else "Alternativo",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Text("Ativo", color = StadiumGreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Fechar", color = StadiumGreenPrimary)
                }
            }
        )
    }
}

@Composable
fun CastPlaybackHub(
    video: PlayableVideo,
    castUiState: CastUiState,
    streamUrl: String = video.streamUrl.ifBlank { video.embedUrl ?: "" },
    hasNextEpisode: Boolean = false,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: () -> Unit = {},
    onReloadCast: () -> Unit = {},
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekCast: (Long) -> Unit = {},
    onSeekCastForward: () -> Unit = {},
    onSeekCastBackward: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    val currentPosMs = if (isSeeking) sliderValue.toLong() else castUiState.streamPositionMs
    val totalDurationMs = castUiState.streamDurationMs

    val isCastPlaybackEnded = !castUiState.isPlaying && currentPosMs > 0 && totalDurationMs > 0 && currentPosMs >= (totalDurationMs - 10_000L)
    val isNearEnd = totalDurationMs > 30_000L && (currentPosMs >= (totalDurationMs - 45_000L) || (currentPosMs.toDouble() / totalDurationMs.toDouble()) >= 0.92)
    val isEpisodeEndingCast = hasNextEpisode && (isCastPlaybackEnded || isNearEnd || (!castUiState.isPlaying && currentPosMs > 0))

    val streamFormatLabel = remember(streamUrl) {
        com.example.util.VideoLinkCompatibility.getFormatLabel(streamUrl)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF080C14),
                        Color(0xFF101724),
                        Color(0xFF162032)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("cast_playback_hub")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChromecastButton(castUiState = castUiState)
                    Spacer(modifier = Modifier.width(8.dp))
                    WebVideoCasterButton(streamUrl = streamUrl, title = video.title)
                }
            }

            // Center Visualizer & Media Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    StadiumGreenPrimary.copy(alpha = 0.2f),
                                    StadiumCyanSecondary.copy(alpha = 0.2f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = StadiumCyanSecondary,
                        modifier = Modifier.size(50.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = StadiumGreenPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = StadiumGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (video.isLive) "TRANSMISSÃO AO VIVO • ${castUiState.deviceName ?: "TV Remote"}" else "Transmitindo para ${castUiState.deviceName ?: "Chromecast"}",
                            color = StadiumGreenPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Format badge chip
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = streamFormatLabel,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = video.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = video.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StadiumCyanSecondary
                )
            }

            // Remote Controls Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Next Episode Banner Card when ending or finished
                    AnimatedVisibility(
                        visible = isEpisodeEndingCast,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        NextEpisodeBannerCard(
                            nextEpisodeTitle = nextEpisodeTitle,
                            isPlaybackEnded = isCastPlaybackEnded || !castUiState.isPlaying,
                            onPlayNextEpisode = onPlayNextEpisode,
                            onDismiss = null,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Slider if duration > 0 or VOD
                    if (totalDurationMs > 0 && !video.isLive) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Slider(
                                value = if (isSeeking) sliderValue else castUiState.streamPositionMs.toFloat(),
                                onValueChange = {
                                    isSeeking = true
                                    sliderValue = it
                                },
                                onValueChangeFinished = {
                                    onSeekCast(sliderValue.toLong())
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
                                    text = formatTime(currentPosMs),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = formatTime(totalDurationMs),
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // Transport controls row: Rewind - Play/Pause - Forward - Next Episode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSeekCastBackward() }
                        ) {
                            IconButton(
                                onClick = onSeekCastBackward,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Voltar 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "-10s",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Play/Pause Main Button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onTogglePlayPause() }
                        ) {
                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(StadiumGreenPrimary)
                            ) {
                                Icon(
                                    imageVector = if (castUiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (castUiState.isPlaying) "Pausar TV" else "Reproduzir TV",
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp)
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

                        // Forward 10s
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSeekCastForward() }
                        ) {
                            IconButton(
                                onClick = onSeekCastForward,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Avançar 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "+10s",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Next Episode Button (if series has next episode)
                        if (hasNextEpisode) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onPlayNextEpisode() }
                            ) {
                                IconButton(
                                    onClick = onPlayNextEpisode,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD500F9))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Próximo Episódio",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Próximo",
                                    color = Color(0xFFD500F9),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Secondary Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reload/Re-sync live stream button
                        OutlinedButton(
                            onClick = onReloadCast,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StadiumGreenPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recarregar", fontSize = 12.sp)
                        }

                        // Web Video Caster launcher
                        OutlinedButton(
                            onClick = {
                                launchWebVideoCaster(
                                    context = context,
                                    url = streamUrl,
                                    title = video.title
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StadiumCyanSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Web Video Caster", fontSize = 12.sp)
                        }

                        // Disconnect Cast
                        OutlinedButton(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StadiumAccentRed
                            )
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
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun UnavailablePlaybackScreen(
    video: PlayableVideo,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Fallback test card image
        AsyncImage(
            model = OFFLINE_FALLBACK_URL,
            contentDescription = "Canal Fora do Ar",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .size(44.dp)
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
                    text = video.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (video.subtitle.isNotBlank()) {
                    Text(
                        text = video.subtitle,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
            Surface(
                color = Color(0xFFE53935),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "INDISPONÍVEL",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Bottom notice card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(20.dp)
                .widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE1E232A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TvOff,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Transmissão Temporariamente Indisponível",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Este canal ou título está indisponível no momento. O link padrão de fora do ar foi configurado enquanto o sistema busca novos sinais operacionais.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Voltar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
