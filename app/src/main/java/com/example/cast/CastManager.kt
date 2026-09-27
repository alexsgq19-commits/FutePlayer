package com.example.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class CastUiState(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isPlaying: Boolean = false,
    val currentTitle: String? = null,
    val currentSubtitle: String? = null,
    val currentStreamUrl: String? = null,
    val streamDurationMs: Long = 0L,
    val streamPositionMs: Long = 0L,
    val castStateCode: Int = CastState.NO_DEVICES_AVAILABLE
)

class CastManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private var castContext: CastContext? = null
    private var currentSession: CastSession? = null

    private val _castUiState = MutableStateFlow(CastUiState())
    val castUiState: StateFlow<CastUiState> =
        _castUiState.asStateFlow()

    /**
     * Listener responsável pelo ciclo de vida da sessão Cast.
     */
    private val sessionManagerListener =
        object : SessionManagerListener<CastSession> {

            override fun onSessionStarting(session: CastSession) {
                Log.d(TAG, "Sessão Cast iniciando...")
            }

            override fun onSessionStarted(
                session: CastSession,
                sessionId: String
            ) {
                Log.d(
                    TAG,
                    "Sessão Cast iniciada: $sessionId"
                )

                onCastSessionConnected(session)
            }

            override fun onSessionStartFailed(
                session: CastSession,
                error: Int
            ) {
                Log.e(
                    TAG,
                    "Falha ao iniciar sessão Cast: $error"
                )

                onCastSessionDisconnected()
            }

            override fun onSessionEnding(session: CastSession) {
                Log.d(TAG, "Sessão Cast encerrando...")
            }

            override fun onSessionEnded(
                session: CastSession,
                error: Int
            ) {
                Log.d(
                    TAG,
                    "Sessão Cast encerrada. Erro: $error"
                )

                onCastSessionDisconnected()
            }

            override fun onSessionResuming(
                session: CastSession,
                sessionId: String
            ) {
                Log.d(
                    TAG,
                    "Retomando sessão Cast: $sessionId"
                )
            }

            override fun onSessionResumed(
                session: CastSession,
                wasSuspended: Boolean
            ) {
                Log.d(
                    TAG,
                    "Sessão Cast retomada. " +
                        "wasSuspended=$wasSuspended"
                )

                onCastSessionConnected(session)
            }

            override fun onSessionResumeFailed(
                session: CastSession,
                error: Int
            ) {
                Log.e(
                    TAG,
                    "Falha ao retomar sessão Cast: $error"
                )

                onCastSessionDisconnected()
            }

            override fun onSessionSuspended(
                session: CastSession,
                reason: Int
            ) {
                Log.w(
                    TAG,
                    "Sessão Cast suspensa. Motivo: $reason"
                )
            }
        }

    /**
     * Atualiza o estado de disponibilidade do Cast.
     */
    private val castStateListener =
        CastStateListener { state ->

            _castUiState.value =
                _castUiState.value.copy(
                    castStateCode = state
                )

            Log.d(
                TAG,
                "Estado Cast alterado: $state"
            )
        }

    /**
     * Callback utilizado para acompanhar o estado
     * da reprodução no dispositivo remoto.
     */
    private val remoteMediaClientCallback =
        object : RemoteMediaClient.Callback() {

            override fun onStatusUpdated() {
                updateRemoteMediaStatus()
            }

            override fun onMetadataUpdated() {
                updateRemoteMediaStatus()
            }
        }

    init {
        initializeCast()
    }

    /**
     * Inicializa o CastContext.
     */
    private fun initializeCast() {

        try {

            castContext =
                CastContext.getSharedInstance(appContext)

            castContext?.addCastStateListener(
                castStateListener
            )

            castContext
                ?.sessionManager
                ?.addSessionManagerListener(
                    sessionManagerListener,
                    CastSession::class.java
                )

            castContext
                ?.sessionManager
                ?.currentCastSession
                ?.let { session ->

                    onCastSessionConnected(session)
                }

            Log.d(
                TAG,
                "CastManager inicializado"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao inicializar CastContext",
                e
            )
        }
    }

    /**
     * Executado quando uma sessão Cast é estabelecida.
     */
    private fun onCastSessionConnected(
        session: CastSession
    ) {

        currentSession = session

        val deviceName =
            session.castDevice
                ?.friendlyName
                ?.takeIf { it.isNotBlank() }
                ?: "Chromecast"

        val remoteMediaClient =
            session.remoteMediaClient

        remoteMediaClient?.unregisterCallback(
            remoteMediaClientCallback
        )

        remoteMediaClient?.registerCallback(
            remoteMediaClientCallback
        )

        _castUiState.value =
            _castUiState.value.copy(
                isConnected = true,
                deviceName = deviceName
            )

        Log.d(
            TAG,
            "Conectado ao dispositivo: $deviceName"
        )

        updateRemoteMediaStatus()
    }

    /**
     * Executado quando a sessão Cast é encerrada.
     */
    private fun onCastSessionDisconnected() {

        try {

            currentSession
                ?.remoteMediaClient
                ?.unregisterCallback(
                    remoteMediaClientCallback
                )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Erro ao remover callback do RemoteMediaClient",
                e
            )
        }

        currentSession = null

        _castUiState.value =
            _castUiState.value.copy(
                isConnected = false,
                deviceName = null,
                isPlaying = false,
                currentTitle = null,
                currentSubtitle = null,
                currentStreamUrl = null,
                streamDurationMs = 0L,
                streamPositionMs = 0L
            )

        Log.d(
            TAG,
            "Sessão Cast desconectada"
        )
    }

    /**
     * Atualiza a interface com o estado real
     * informado pelo receptor Cast.
     */
    private fun updateRemoteMediaStatus() {

        val session = currentSession ?: return

        val remoteMediaClient =
            session.remoteMediaClient
                ?: return

        try {

            val mediaInfo =
                remoteMediaClient.mediaInfo

            val metadata =
                mediaInfo?.metadata

            val currentState =
                _castUiState.value

            val title =
                try {
                    metadata?.getString(
                        MediaMetadata.KEY_TITLE
                    )
                } catch (_: Exception) {
                    null
                }

            val subtitle =
                try {
                    metadata?.getString(
                        MediaMetadata.KEY_SUBTITLE
                    )
                } catch (_: Exception) {
                    null
                }

            _castUiState.value =
                currentState.copy(
                    isConnected = true,

                    isPlaying =
                        remoteMediaClient.isPlaying,

                    currentTitle =
                        title ?: currentState.currentTitle,

                    currentSubtitle =
                        subtitle
                            ?: currentState.currentSubtitle,

                    streamPositionMs =
                        remoteMediaClient
                            .approximateStreamPosition
                            .coerceAtLeast(0L),

                    streamDurationMs =
                        remoteMediaClient
                            .streamDuration
                            .coerceAtLeast(0L)
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao atualizar estado da mídia",
                e
            )
        }
    }

    /**
     * Envia uma mídia para o dispositivo Cast.
     *
     * Compatível com:
     *
     * - HLS (.m3u8)
     * - MPEG-DASH (.mpd)
     * - MP4
     * - WebM
     * - outros formatos suportados pelo receiver
     *
     * IMPORTANTE:
     *
     * O Default Media Receiver do Chromecast não é capaz
     * de reproduzir literalmente qualquer URL.
     *
     * Headers HTTP personalizados são mantidos no customData
     * para eventual utilização por um Custom Web Receiver,
     * mas o Default Media Receiver não interpreta customData
     * como headers HTTP da requisição.
     */
    fun castMedia(
        title: String,
        subtitle: String,
        streamUrl: String,
        posterUrl: String? = null,
        isLive: Boolean = true,
        headers: Map<String, String>? = null
    ) {

        val session =
            currentSession

        if (session == null) {

            Log.w(
                TAG,
                "Tentativa de transmissão sem sessão Cast ativa"
            )

            return
        }

        val remoteMediaClient =
            session.remoteMediaClient

        if (remoteMediaClient == null) {

            Log.w(
                TAG,
                "RemoteMediaClient indisponível"
            )

            return
        }

        val cleanUrl =
            streamUrl.trim()

        if (cleanUrl.isBlank()) {

            Log.e(
                TAG,
                "URL de transmissão vazia"
            )

            return
        }

        try {

            /*
             * ========================================================
             * METADATA
             * ========================================================
             */

            val metadataType =
                if (isLive) {

                    MediaMetadata.MEDIA_TYPE_TV_SHOW

                } else {

                    MediaMetadata.MEDIA_TYPE_MOVIE
                }

            val metadata =
                MediaMetadata(metadataType).apply {

                    putString(
                        MediaMetadata.KEY_TITLE,
                        title
                    )

                    if (subtitle.isNotBlank()) {

                        putString(
                            MediaMetadata.KEY_SUBTITLE,
                            subtitle
                        )
                    }

                    if (!posterUrl.isNullOrBlank()) {

                        try {

                            addImage(
                                WebImage(
                                    Uri.parse(
                                        posterUrl.trim()
                                    )
                                )
                            )

                        } catch (e: Exception) {

                            Log.w(
                                TAG,
                                "URL da capa inválida: $posterUrl",
                                e
                            )
                        }
                    }
                }

            /*
             * ========================================================
             * MIME TYPE
             * ========================================================
             */

            val resolvedMime =
                try {

                    com.example.util
                        .VideoLinkCompatibility
                        .resolveMimeType(cleanUrl)

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "Não foi possível resolver MIME type",
                        e
                    )

                    null
                }

            val contentType =
                resolveCastContentType(
                    url = cleanUrl,
                    resolvedMime = resolvedMime
                )

            Log.d(
                TAG,
                "MIME selecionado: $contentType"
            )

            /*
             * ========================================================
             * STREAM TYPE
             * ========================================================
             */

            val streamType =
                if (isLive) {

                    MediaInfo.STREAM_TYPE_LIVE

                } else {

                    MediaInfo.STREAM_TYPE_BUFFERED
                }

            /*
             * ========================================================
             * CUSTOM DATA
             * ========================================================
             */

            val customData =
                JSONObject().apply {

                    put(
                        "sourceUrl",
                        cleanUrl
                    )

                    put(
                        "isLive",
                        isLive
                    )

                    if (!headers.isNullOrEmpty()) {

                        val headersObject =
                            JSONObject()

                        headers.forEach { (key, value) ->

                            if (
                                key.isNotBlank() &&
                                value.isNotBlank()
                            ) {

                                headersObject.put(
                                    key,
                                    value
                                )
                            }
                        }

                        put(
                            "httpHeaders",
                            headersObject
                        )
                    }
                }

            /*
             * ========================================================
             * MEDIA INFO
             * ========================================================
             */

            val mediaInfo =
                MediaInfo.Builder(cleanUrl)
                    .setStreamType(streamType)
                    .setContentType(contentType)
                    .setMetadata(metadata)
                    .setCustomData(customData)
                    .build()

            /*
             * ========================================================
             * LOAD REQUEST
             * ========================================================
             */

            val request =
                MediaLoadRequestData.Builder()
                    .setMediaInfo(mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(0L)
                    .setCustomData(customData)
                    .build()

            /*
             * ========================================================
             * LIMPA ESTADO ANTERIOR
             * ========================================================
             */

            _castUiState.value =
                _castUiState.value.copy(
                    currentTitle = title,
                    currentSubtitle = subtitle,
                    currentStreamUrl = cleanUrl,
                    streamPositionMs = 0L,
                    streamDurationMs = 0L,
                    isPlaying = false
                )

            Log.d(
                TAG,
                "Enviando mídia para o Cast: $cleanUrl"
            )

            /*
             * ========================================================
             * LOAD ASSÍNCRONO
             * ========================================================
             *
             * NÃO marcamos isPlaying=true imediatamente.
             *
             * Primeiro esperamos o Chromecast responder.
             */

            remoteMediaClient
                .load(request)
                .setResultCallback(
                    object :
                        ResultCallback<
                            RemoteMediaClient.MediaChannelResult
                            > {

                        override fun onResult(
                            result:
                            RemoteMediaClient.MediaChannelResult
                        ) {

                            val status =
                                result.status

                            if (
                                status.statusCode ==
                                CastStatusCodes.SUCCESS
                            ) {

                                Log.d(
                                    TAG,
                                    "Mídia carregada pelo Cast"
                                )

                                _castUiState.value =
                                    _castUiState.value.copy(
                                        currentTitle = title,
                                        currentSubtitle = subtitle,
                                        currentStreamUrl = cleanUrl
                                    )

                                updateRemoteMediaStatus()

                            } else {

                                Log.e(
                                    TAG,
                                    "Falha ao carregar mídia. " +
                                        "Código=${status.statusCode}, " +
                                        "mensagem=${status.statusMessage}"
                                )

                                _castUiState.value =
                                    _castUiState.value.copy(
                                        isPlaying = false
                                    )
                            }
                        }
                    }
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao transmitir mídia",
                e
            )

            _castUiState.value =
                _castUiState.value.copy(
                    isPlaying = false
                )
        }
    }

    /**
     * Determina o MIME type da mídia.
     *
     * A extensão é obtida através do path da URL,
     * ignorando parâmetros como ?token=...
     */
    private fun resolveCastContentType(
        url: String,
        resolvedMime: String?
    ): String {

        val normalizedMime =
            resolvedMime
                ?.trim()
                ?.lowercase()
                ?.takeIf {
                    it.isNotBlank()
                }

        if (
            normalizedMime != null &&
            normalizedMime != "video/*" &&
            normalizedMime != "application/octet-stream"
        ) {

            return normalizedMime
        }

        val path =
            try {

                Uri.parse(url)
                    .path
                    ?.lowercase()
                    ?: url.lowercase()

            } catch (_: Exception) {

                url.lowercase()
            }

        return when {

            path.endsWith(".m3u8") ->
                "application/x-mpegurl"

            path.endsWith(".mpd") ->
                "application/dash+xml"

            path.endsWith(".mp4") ->
                "video/mp4"

            path.endsWith(".m4v") ->
                "video/x-m4v"

            path.endsWith(".webm") ->
                "video/webm"

            path.endsWith(".ts") ->
                "video/mp2t"

            path.endsWith(".mov") ->
                "video/quicktime"

            path.endsWith(".avi") ->
                "video/x-msvideo"

            path.endsWith(".wmv") ->
                "video/x-ms-wmv"

            path.endsWith(".3gp") ->
                "video/3gpp"

            path.endsWith(".ogg") ->
                "video/ogg"

            /*
             * URL sem extensão.
             *
             * Não assumimos HLS automaticamente.
             * O resolver existente do projeto tem prioridade.
             */
            else ->
                "video/mp4"
        }
    }

    /**
     * Move a reprodução para uma posição específica.
     */
    fun seekTo(positionMs: Long) {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            val safePosition =
                positionMs.coerceAtLeast(0L)

            remoteMediaClient.seek(
                safePosition
            )

            _castUiState.value =
                _castUiState.value.copy(
                    streamPositionMs =
                        safePosition
                )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao buscar posição",
                e
            )
        }
    }

    /**
     * Avança a reprodução.
     */
    fun seekForward(
        offsetMs: Long = 10_000L
    ) {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            val currentPosition =
                remoteMediaClient
                    .approximateStreamPosition
                    .coerceAtLeast(0L)

            val duration =
                remoteMediaClient
                    .streamDuration

            val targetPosition =
                if (duration > 0L) {

                    (
                        currentPosition + offsetMs
                    ).coerceAtMost(duration)

                } else {

                    currentPosition + offsetMs
                }

            seekTo(targetPosition)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao avançar reprodução",
                e
            )
        }
    }

    /**
     * Retrocede a reprodução.
     */
    fun seekBackward(
        offsetMs: Long = 10_000L
    ) {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            val currentPosition =
                remoteMediaClient
                    .approximateStreamPosition
                    .coerceAtLeast(0L)

            val targetPosition =
                (
                    currentPosition - offsetMs
                ).coerceAtLeast(0L)

            seekTo(targetPosition)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao retroceder reprodução",
                e
            )
        }
    }

    /**
     * Alterna entre reprodução e pausa.
     */
    fun togglePlayPause() {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            if (remoteMediaClient.isPlaying) {

                remoteMediaClient.pause()

            } else {

                remoteMediaClient.play()
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao alternar play/pause",
                e
            )
        }
    }

    /**
     * Inicia a reprodução.
     */
    fun play() {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            remoteMediaClient.play()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao iniciar reprodução",
                e
            )
        }
    }

    /**
     * Pausa a reprodução.
     */
    fun pause() {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            remoteMediaClient.pause()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao pausar reprodução",
                e
            )
        }
    }

    /**
     * Para a mídia atual.
     */
    fun stop() {

        val remoteMediaClient =
            currentSession
                ?.remoteMediaClient
                ?: return

        try {

            remoteMediaClient.stop()

            _castUiState.value =
                _castUiState.value.copy(
                    isPlaying = false,
                    streamPositionMs = 0L,
                    streamDurationMs = 0L,
                    currentStreamUrl = null
                )

            Log.d(
                TAG,
                "Reprodução parada"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao parar reprodução",
                e
            )
        }
    }

    /**
     * Verifica se existe uma sessão Cast ativa.
     */
    fun isConnected(): Boolean {
        return currentSession != null
    }

    /**
     * Retorna o nome do dispositivo conectado.
     */
    fun getConnectedDeviceName(): String? {
        return currentSession
            ?.castDevice
            ?.friendlyName
    }

    /**
     * Encerra a sessão Cast atual.
     */
    fun disconnect() {

        try {

            currentSession
                ?.remoteMediaClient
                ?.unregisterCallback(
                    remoteMediaClientCallback
                )

            castContext
                ?.sessionManager
                ?.endCurrentSession(true)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao desconectar Cast",
                e
            )
        }
    }

    companion object {

        private const val TAG =
            "CastManager"

        @Volatile
        private var instance:
            CastManager? = null

        fun getInstance(
            context: Context
        ): CastManager {

            return instance
                ?: synchronized(this) {

                    instance
                        ?: CastManager(context)
                            .also {
                                instance = it
                            }
                }
        }
    }
}
