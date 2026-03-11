package com.example.hearo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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

/** Display-only text field: white background, black text and label, gray border. Used for NFC ID and Playlist URI. */
@Composable
private fun PlayerDisplayTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { },
        readOnly = true,
        label = { Text(label, color = Color.Black) },
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedBorderColor = Color.Black.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Black.copy(alpha = 0.5f),
            focusedLabelColor = Color.Black,
            unfocusedLabelColor = Color.Black
        )
    )
}

/** Red-styled icon button for transport controls. */
@Composable
private fun PlayerIconButton(
    onClick: () -> Unit,
    iconResId: Int,
    contentDescription: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFFF0000).copy(alpha = 0.6f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        )
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription
        )
    }
}

/** Player UI button: same red filled style as InitialScreen Sign in button. */
@Composable
private fun PlayerOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFFF0000).copy(alpha = 0.6f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        content = content
    )
}

@Composable
fun HearoScreen(
    artistName: String = "Artist",
    trackTitle: String = "Title",
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
    preferredDeviceDisplay: String = "No preferred device",
    onEditPreferredDeviceClick: () -> Unit = {},
    listeningCounterDisplay: String = "0:00",
    listeningLimitDisplay: String = "1:00:00",
    listeningLimitReached: Boolean = false,
    listeningProgress: Float = 0f,
    onResetListeningTime: () -> Unit = {},
    onEditListeningLimitClick: () -> Unit = {},
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

            val transportEnabled = nfcId.isNotEmpty() && !listeningLimitReached
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerIconButton(
                        onClick = onSkipBack,
                        iconResId = R.drawable.skip_previous_24,
                        contentDescription = "Skip back",
                        enabled = transportEnabled
                    )
                    PlayerSeekButton(
                        iconResId = R.drawable.fast_rewind_24,
                        contentDescription = "Seek back",
                        onSeek = onSeekBack,
                        enabled = transportEnabled
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerSeekButton(
                        iconResId = R.drawable.fast_forward_24,
                        contentDescription = "Seek forward",
                        onSeek = onSeekFwd,
                        enabled = transportEnabled
                    )
                    PlayerIconButton(
                        onClick = onSkipFwd,
                        iconResId = R.drawable.skip_next_24,
                        contentDescription = "Skip forward",
                        enabled = transportEnabled
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            PlayerDisplayTextField(value = nfcId.ifEmpty { "No Tag" }, label = "NFC ID")
            Spacer(Modifier.height(8.dp))
            PlayerDisplayTextField(value = playlistUrl.ifEmpty { "-" }, label = "Playlist URI")
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

            Spacer(Modifier.height(16.dp))
            PlayerDisplayTextField(value = preferredDeviceDisplay, label = "Preferred device")
            Spacer(Modifier.height(8.dp))
            PlayerOutlinedButton(onClick = onEditPreferredDeviceClick) { Text("Edit") }

            if (listeningLimitReached) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "Daily limit reached. Tap Reset to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Listening Time: $listeningCounterDisplay",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(2.dp, Color(0xFFFF0000), RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(listeningProgress)
                        .background(Color(0xFFFF0000), RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerOutlinedButton(onClick = onResetListeningTime) { Text("Reset") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listeningLimitDisplay,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Black
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditListeningLimitClick,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Black)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.edit_24),
                            contentDescription = "Set listening limit"
                        )
                    }
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
private fun PlayerSeekButton(
    iconResId: Int,
    contentDescription: String,
    onSeek: () -> Unit,
    enabled: Boolean
) {
    val scope = rememberCoroutineScope()
    Box {
        PlayerIconButton(
            onClick = { },
            iconResId = iconResId,
            contentDescription = contentDescription,
            enabled = enabled
        )
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
