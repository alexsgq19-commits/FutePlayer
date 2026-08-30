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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.example.R
import com.example.cast.CastUiState
import com.example.ui.theme.StadiumCyanSecondary
import com.example.ui.theme.StadiumGreenPrimary
import com.google.android.gms.cast.framework.CastButtonFactory

fun launchWebVideoCaster(context: android.content.Context, url: String, title: String) {
    try {
        val targetUrl = if (url.isNotBlank()) url.trim() else ""
        if (targetUrl.isBlank()) return
        val isDirectStream = targetUrl.contains(".m3u8", ignoreCase = true) ||
                             targetUrl.contains(".mp4", ignoreCase = true) ||
                             targetUrl.contains(".ts", ignoreCase = true) ||
                             targetUrl.contains(".mkv", ignoreCase = true)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.instantbits.cast.webvideo")
            if (isDirectStream) {
                setDataAndType(Uri.parse(targetUrl), "video/*")
            } else {
                data = Uri.parse(targetUrl)
            }
            putExtra("title", title)
            putExtra("secure_uri", targetUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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

@Composable
fun ChromecastButton(
    castUiState: CastUiState,
    modifier: Modifier = Modifier,
    onCastConnectedClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (castUiState.isConnected) StadiumCyanSecondary.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
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
                    }
                }
            )

            AnimatedVisibility(
                visible = castUiState.isConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onCastConnectedClick() }
                        .padding(start = 4.dp, end = 4.dp)
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
