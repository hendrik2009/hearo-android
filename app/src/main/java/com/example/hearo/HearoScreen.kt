package com.example.hearo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hearo.ui.theme.HearoRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SEEK_HOLD_INTERVAL_MS = 500L

/** Formats ms as m:ss or h:mm:ss. No leading zero on first segment. Returns "--:--" when no track. */
private fun formatTrackTime(progressMs: Long, durationMs: Long): String {
    if (durationMs <= 0L && progressMs <= 0L) return "--:--"
    val totalSec = (progressMs / 1000).toInt().coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        else -> "$m:${s.toString().padStart(2, '0')}"
    }
}

/** Player UI button: red outline, white fill, red content (per design). */
@Composable
private fun PlayerOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, HearoRed, RoundedCornerShape(8.dp)),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = HearoRed,
            disabledContainerColor = Color.White.copy(alpha = 0.6f),
            disabledContentColor = HearoRed.copy(alpha = 0.6f)
        ),
        border = null,
        content = content
    )
}

@Composable
fun HearoScreen(
    artistName: String = "—",
    trackTitle: String = "—",
    progressMs: Long = 0L,
    durationMs: Long = 0L,
    nfcId: String = "",
    playlistUrl: String = "",
    signedIn: Boolean = false,
    nfcReady: Boolean = true,
    showSignInBanner: Boolean = false,
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    playlistFieldEnabled: Boolean = false,
    showSaveButton: Boolean = false,
    saveButtonEnabled: Boolean = false,
    showDetachButton: Boolean = false,
    onPlaylistUrlChange: (String) -> Unit = {},
    onSaveClick: () -> Unit = {},
    onDetachClick: () -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekFwd: () -> Unit = {},
    onSkipFwd: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.hearo_player_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            if (!nfcReady) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "NFC could not be accessed. Enable NFC in Settings if needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showSignInBanner) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "Please sign in before playing",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatTrackTime(progressMs, durationMs),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )
            Spacer(Modifier.height(16.dp))

            val transportEnabled = nfcId.isNotEmpty()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerOutlinedButton(onClick = onSkipBack, enabled = transportEnabled) { Text("<<") }
                PlayerSeekButton(symbol = "<", onSeek = onSeekBack, enabled = transportEnabled)
                PlayerSeekButton(symbol = ">", onSeek = onSeekFwd, enabled = transportEnabled)
                PlayerOutlinedButton(onClick = onSkipFwd, enabled = transportEnabled) { Text(">>") }
            }
            Spacer(Modifier.height(16.dp))

            Text(
                text = nfcId.ifEmpty { "[NFC ID]" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            OutlinedTextField(
                value = playlistUrl,
                onValueChange = onPlaylistUrlChange,
                enabled = playlistFieldEnabled,
                readOnly = showDetachButton,
                label = { Text("Playlist URI", color = Color.Black) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = HearoRed,
                    unfocusedBorderColor = Color.Black.copy(alpha = 0.5f),
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black
                )
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showSaveButton) {
                    PlayerOutlinedButton(onClick = onSaveClick, enabled = saveButtonEnabled) { Text("Save") }
                }
                if (showDetachButton) {
                    PlayerOutlinedButton(onClick = onDetachClick) { Text("Discard") }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (signedIn) {
                    PlayerOutlinedButton(onClick = onSignOutClick) { Text("Sign out") }
                } else {
                    PlayerOutlinedButton(onClick = onSignInClick) { Text("Sign in with Spotify") }
                }
            }
        }
    }
}

@Composable
private fun PlayerSeekButton(symbol: String, onSeek: () -> Unit, enabled: Boolean) {
    val scope = rememberCoroutineScope()
    Box {
        PlayerOutlinedButton(
            onClick = { },
            enabled = enabled,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) { Text(symbol) }
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onSeek()
                                val job = scope.launch {
                                    while (isActive) {
                                        delay(SEEK_HOLD_INTERVAL_MS)
                                        onSeek()
                                    }
                                }
                                try {
                                    awaitRelease()
                                } finally {
                                    job.cancel()
                                }
                            }
                        )
                    }
            )
        }
    }
}
