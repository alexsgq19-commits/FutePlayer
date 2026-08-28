package com.example.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val castUiState: StateFlow<CastUiState> = _castUiState.asStateFlow()

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            Log.d(TAG, "Cast session starting...")
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session started: $sessionId")
            onCastSessionConnected(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session start failed: $error")
            onCastSessionDisconnected()
        }

        override fun onSessionEnding(session: CastSession) {
            Log.d(TAG, "Cast session ending...")
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            Log.d(TAG, "Cast session ended: $error")
            onCastSessionDisconnected()
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            Log.d(TAG, "Cast session resuming...")
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            Log.d(TAG, "Cast session resumed")
            onCastSessionConnected(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            Log.e(TAG, "Cast session resume failed: $error")
            onCastSessionDisconnected()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            Log.d(TAG, "Cast session suspended: $reason")
        }
    }

    private val castStateListener = CastStateListener { state ->
        _castUiState.value = _castUiState.value.copy(castStateCode = state)
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updateRemoteMediaStatus()
        }

        override fun onMetadataUpdated() {
            updateRemoteMediaStatus()
        }
    }

    init {
        try {
            castContext = CastContext.getSharedInstance(appContext)
            castContext?.addCastStateListener(castStateListener)
            castContext?.sessionManager?.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
            castContext?.sessionManager?.currentCastSession?.let {
                onCastSessionConnected(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize CastContext (may be running in environment without Play Services): ${e.message}")
        }
    }

    private fun onCastSessionConnected(session: CastSession) {
        currentSession = session
        val deviceName = session.castDevice?.friendlyName ?: "Chromecast"
        val rmc = session.remoteMediaClient
        rmc?.registerCallback(remoteMediaClientCallback)

        _castUiState.value = _castUiState.value.copy(
            isConnected = true,
            deviceName = deviceName
        )
        updateRemoteMediaStatus()
    }

    private fun onCastSessionDisconnected() {
        currentSession?.remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        currentSession = null
        _castUiState.value = _castUiState.value.copy(
            isConnected = false,
            deviceName = null,
            isPlaying = false,
            currentTitle = null,
            currentSubtitle = null,
            currentStreamUrl = null
        )
    }

    private fun updateRemoteMediaStatus() {
        val rmc = currentSession?.remoteMediaClient ?: return
        val mediaInfo = rmc.mediaInfo
        val metadata = mediaInfo?.metadata
        val isPlaying = rmc.isPlaying

        _castUiState.value = _castUiState.value.copy(
            isPlaying = isPlaying,
            currentTitle = metadata?.getString(MediaMetadata.KEY_TITLE) ?: _castUiState.value.currentTitle,
            currentSubtitle = metadata?.getString(MediaMetadata.KEY_SUBTITLE) ?: _castUiState.value.currentSubtitle,
            streamPositionMs = rmc.approximateStreamPosition,
            streamDurationMs = rmc.streamDuration
        )
    }

    fun castMedia(
        title: String,
        subtitle: String,
        streamUrl: String,
        posterUrl: String? = null,
        isLive: Boolean = true
    ) {
        val session = currentSession ?: return
        val rmc = session.remoteMediaClient ?: return

        try {
            val metadata = MediaMetadata(
                if (isLive) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE
            ).apply {
                putString(MediaMetadata.KEY_TITLE, title)
                putString(MediaMetadata.KEY_SUBTITLE, subtitle)
                if (!posterUrl.isNullOrBlank()) {
                    addImage(WebImage(Uri.parse(posterUrl)))
                }
            }

            val contentType = if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                "application/x-mpegurl"
            } else if (streamUrl.contains(".mpd", ignoreCase = true)) {
                "application/dash+xml"
            } else {
                "video/mp4"
            }

            val streamType = if (isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED

            val mediaInfo = MediaInfo.Builder(streamUrl)
                .setStreamType(streamType)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()

            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()

            rmc.load(request)

            _castUiState.value = _castUiState.value.copy(
                currentTitle = title,
                currentSubtitle = subtitle,
                currentStreamUrl = streamUrl,
                isPlaying = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error casting media: ${e.message}", e)
        }
    }

    fun togglePlayPause() {
        val rmc = currentSession?.remoteMediaClient ?: return
        if (rmc.isPlaying) {
            rmc.pause()
        } else {
            rmc.play()
        }
    }

    fun stop() {
        currentSession?.remoteMediaClient?.stop()
    }

    fun disconnect() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Cast session: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CastManager"

        @Volatile
        private var instance: CastManager? = null

        fun getInstance(context: Context): CastManager {
            return instance ?: synchronized(this) {
                instance ?: CastManager(context).also { instance = it }
            }
        }
    }
}
