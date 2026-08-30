package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.OptIn
import com.example.data.MoviesRepository
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.ui.theme.StadiumAccentRed
import com.example.ui.theme.StadiumCyanSecondary
import com.example.ui.theme.StadiumGreenPrimary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    video: PlayableVideo,
    castUiState: CastUiState,
    onBack: () -> Unit,
    onCastToggle: () -> Unit,
    onDisconnectCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var isFullscreen by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Auto-detect if direct HLS / video media URL is available or if web player should be primary
    val hasDirectMediaUrl = remember(video.streamUrl) {
        val url = video.streamUrl.lowercase()
        (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".ts") || url.contains(".mpd")) &&
        !url.contains(".php") && !url.contains("cxtv.com.br/tv-ao-vivo") && !url.contains("temporariofutemais")
    }
    var useWebviewPlayer by remember(video.streamUrl, video.embedUrl, video.forceWebPlayer, hasDirectMediaUrl) { 
        mutableStateOf(video.forceWebPlayer || (!hasDirectMediaUrl && !video.embedUrl.isNullOrBlank())) 
    }
    var activeEmbedUrl by remember(video.embedUrl, video.streamUrl) {
        mutableStateOf(video.embedUrl?.takeIf { it.isNotBlank() } ?: video.streamUrl)
    }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isWebViewLoading by remember { mutableStateOf(true) }
    var isWebViewError by remember { mutableStateOf(false) }

    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

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

    // Create and configure ExoPlayer with robust buffer, decoders and data sources
    val exoPlayer = remember(video.streamUrl, useWebviewPlayer) {
        if (useWebviewPlayer || !hasDirectMediaUrl) null
        else {
            val userAgent = video.headers["User-Agent"]
                ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(25000)
                .setReadTimeoutMs(25000)

            val headerMap = mutableMapOf<String, String>()
            headerMap["User-Agent"] = userAgent
            headerMap["Accept"] = "*/*"
            video.headers.forEach { (k, v) ->
                headerMap[k] = v
            }
            // Only add Referer if explicitly specified in headers, or if streamUrl and embedUrl share the same host
            if (!headerMap.containsKey("Referer") && !video.embedUrl.isNullOrBlank()) {
                val streamHost = try { Uri.parse(video.streamUrl).host } catch (_: Exception) { null }
                val embedHost = try { Uri.parse(video.embedUrl).host } catch (_: Exception) { null }
                if (streamHost != null && embedHost != null && streamHost.equals(embedHost, ignoreCase = true)) {
                    headerMap["Referer"] = video.embedUrl
                }
            }
            httpDataSourceFactory.setDefaultRequestProperties(headerMap)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val uri = Uri.parse(video.streamUrl)
            val isHls = video.streamUrl.contains(".m3u8", ignoreCase = true) || 
                        video.streamUrl.contains("/live/", ignoreCase = true)

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .apply {
                    if (isHls) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .build()

            val mediaSource: MediaSource = if (isHls) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(false) // false allows accurate track & segment resolution on live streams
                    .createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

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

            ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .build().apply {
                    setMediaSource(mediaSource)
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
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                    durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "Erro de reprodução: ${error.localizedMessage ?: "Verifique a conexão"}"
                isBuffering = false
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen_root")
    ) {
        if (castUiState.isConnected) {
            // ==========================================
            // CHROMECAST REMOTE PLAYBACK HUB
            // ==========================================
            CastPlaybackHub(
                video = video,
                castUiState = castUiState,
                onBack = onBack,
                onDisconnect = onDisconnectCast,
                onTogglePlayPause = { onCastToggle() }
            )
        } else if (useWebviewPlayer) {
            // ==========================================
            // ISOLATED WEB PLAYER (PURE VIDEO ONLY)
            // ==========================================
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                key(activeEmbedUrl, isWebViewError) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            try {
                                java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js").mkdirs()
                                java.io.File(ctx.cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm").mkdirs()
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

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?
                                    ): Boolean {
                                        val reqUrl = request?.url?.toString() ?: return false
                                        // Block intrusive redirects and store schemes
                                        if (reqUrl.startsWith("intent:") || reqUrl.startsWith("market:") || reqUrl.startsWith("whatsapp:") || reqUrl.startsWith("tg:")) {
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

                                if (isFutemais) {
                                    loadUrl(targetStream, mapOf("Referer" to "https://futemais.link/"))
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
                                    loadDataWithBaseURL("https://autoembed.co", htmlData, "text/html", "UTF-8", null)
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
                                     loadDataWithBaseURL("https://autoembed.co", iframeHtml, "text/html", "UTF-8", null)
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
                        }
                    )
                }

                if (isWebViewLoading && !isWebViewError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = StadiumGreenPrimary,
                            modifier = Modifier.size(48.dp)
                        )
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
                                            isWebViewError = false
                                            isWebViewLoading = true
                                            webViewInstance?.reload()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Recarregar", color = Color.Black, fontWeight = FontWeight.Bold)
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
                        // Refresh / Reload stream
                        IconButton(
                            onClick = { 
                                isWebViewLoading = true
                                webViewInstance?.reload() 
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("webview_reload_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Recarregar",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Web Video Caster
                        WebVideoCasterButton(
                            streamUrl = activeEmbedUrl.ifBlank { video.streamUrl },
                            title = video.title
                        )

                        Spacer(modifier = Modifier.width(6.dp))

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

                        // Collapsible options menu in both portrait and landscape
                        if (!areExtraActionsExpanded) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .clickable { areExtraActionsExpanded = true }
                                    .testTag("btn_expand_webview_options")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Opções do Player",
                                        tint = StadiumGreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            AnimatedVisibility(
                                visible = areExtraActionsExpanded,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.5f)),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        ChromecastButton(castUiState = castUiState)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        WebVideoCasterButton(streamUrl = activeEmbedUrl.ifBlank { video.streamUrl }, title = video.title)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { 
                                                areExtraActionsExpanded = false
                                                useWebviewPlayer = false 
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp).testTag("btn_switch_native_player")
                                        ) {
                                            Text("Nativo", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { areExtraActionsExpanded = false },
                                            modifier = Modifier.size(30.dp).testTag("btn_collapse_webview_options")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Recolher Opções",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                        val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null)
                        view.findViewById<PlayerView>(R.id.player_view).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        val playerView = view.findViewById<PlayerView>(R.id.player_view)
                        playerView.player = exoPlayer
                        playerView.resizeMode = resizeMode
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
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tentar Novamente", color = Color.Black)
                                }

                                if (!video.embedUrl.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = { useWebviewPlayer = true }
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Abrir no Player Web")
                                    }
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

                                // Collapsible options menu in both portrait and landscape
                                if (!areExtraActionsExpanded) {
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier
                                            .clickable { areExtraActionsExpanded = true }
                                            .testTag("btn_expand_player_options")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Opções do Player",
                                                tint = StadiumGreenPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Opções",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                } else {
                                    AnimatedVisibility(
                                        visible = areExtraActionsExpanded,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally()
                                    ) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(20.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.5f)),
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                // Transmitir (Chromecast)
                                                ChromecastButton(
                                                    castUiState = castUiState,
                                                    onCastConnectedClick = { onCastToggle() }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                WebVideoCasterButton(streamUrl = video.streamUrl.ifBlank { video.embedUrl ?: "" }, title = video.title)
                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Formato de Tela (Aspect ratio)
                                                IconButton(
                                                    onClick = {
                                                        resizeMode = when (resizeMode) {
                                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        }
                                                    },
                                                    modifier = Modifier.size(36.dp).testTag("btn_aspect_ratio")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AspectRatio,
                                                        contentDescription = "Formato de Tela",
                                                        tint = Color.White
                                                    )
                                                }

                                                // Usar Player Web
                                                if (!video.embedUrl.isNullOrBlank() || video.streamUrl.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Button(
                                                        onClick = { 
                                                            areExtraActionsExpanded = false
                                                            useWebviewPlayer = true 
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(34.dp).testTag("btn_switch_web_player")
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Language,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = Color.White
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Player Web", color = Color.White, fontSize = 12.sp)
                                                    }
                                                }

                                                // Recarregar Stream
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(
                                                    onClick = {
                                                        errorMessage = null
                                                        exoPlayer?.prepare()
                                                        exoPlayer?.play()
                                                    },
                                                    modifier = Modifier.size(36.dp).testTag("btn_reload_stream")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Recarregar Transmissão",
                                                        tint = Color.White
                                                    )
                                                }

                                                // Botão Recolher Opções
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { areExtraActionsExpanded = false },
                                                    modifier = Modifier.size(30.dp).testTag("btn_collapse_player_options")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Recolher Opções",
                                                        tint = Color.White.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
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
            }
        }
    }
}

@Composable
fun CastPlaybackHub(
    video: PlayableVideo,
    castUiState: CastUiState,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .padding(24.dp)
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

                ChromecastButton(castUiState = castUiState)
                Spacer(modifier = Modifier.width(8.dp))
                WebVideoCasterButton(streamUrl = video.streamUrl.ifBlank { video.embedUrl ?: "" }, title = video.title)
            }

            // Center Visualizer & Media Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
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
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = StadiumGreenPrimary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.4f))
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
                            text = "Transmitindo para ${castUiState.deviceName ?: "Chromecast"}",
                            color = StadiumGreenPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = video.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = video.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StadiumCyanSecondary
                )
            }

            // Remote Controls
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
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
                                contentDescription = if (castUiState.isPlaying) "Pausar TV" else "Reproduzir TV",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }

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
                            Text("Desconectar Cast")
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
