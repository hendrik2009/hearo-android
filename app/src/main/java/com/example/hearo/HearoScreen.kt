package com.example.hearo

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hearo.ui.theme.HearoGreenDefault
import com.example.hearo.ui.theme.HearoGreenInactive
import com.example.hearo.ui.theme.HearoGreenPressed
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SEEK_HOLD_INTERVAL_MS = 500L

@Composable
private fun HearoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val containerColor = when {
        !enabled -> HearoGreenInactive
        pressed -> HearoGreenPressed
        else -> HearoGreenDefault
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = HearoGreenInactive,
            disabledContentColor = Color.White.copy(alpha = 0.8f)
        ),
        content = content
    )
}

@Composable
fun HearoScreen(
    trackTitle: String = "[TRACK TITLE]",
    nfcId: String = "",
    playlistUrl: String = "",
    signedIn: Boolean = false,
    nfcReady: Boolean = true,
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
    ledColor: Color = Color(0xFF00FF00),
) {
    val scrollState = rememberScrollState()
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (signedIn) {
                HearoButton(onClick = onSignOutClick) { Text("Sign out") }
            } else {
                HearoButton(onClick = onSignInClick) { Text("Sign in with Spotify") }
            }
        }
        Spacer(Modifier.height(8.dp))

        Text(
            text = trackTitle,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = nfcId,
            onValueChange = {},
            enabled = false,
            label = { Text("NFC ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = playlistUrl,
            onValueChange = onPlaylistUrlChange,
            enabled = playlistFieldEnabled,
            label = { Text("Playlist URL") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = showDetachButton
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showSaveButton) {
                HearoButton(onClick = onSaveClick, enabled = saveButtonEnabled) { Text("Save") }
            }
            if (showDetachButton) {
                HearoButton(onClick = onDetachClick) { Text("Detach") }
            }
        }

        Spacer(Modifier.height(16.dp))

        val transportEnabled = nfcId.isNotEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HearoButton(onClick = onSkipBack, enabled = transportEnabled) { Text("Skip\nBack") }
            SeekButton(label = "Seek\nBack", onSeek = onSeekBack, enabled = transportEnabled)
            SeekButton(label = "Seek\nFwd", onSeek = onSeekFwd, enabled = transportEnabled)
            HearoButton(onClick = onSkipFwd, enabled = transportEnabled) { Text("Skip\nFwd") }
        }

        Spacer(Modifier.height(16.dp))
        Text("LED indication", style = MaterialTheme.typography.labelMedium)
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 56.dp)
                .background(ledColor)
        )

        Spacer(Modifier.height(16.dp))
        Text("DB TABLE VIEW", style = MaterialTheme.typography.titleMedium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

@Composable
private fun SeekButton(label: String, onSeek: () -> Unit, enabled: Boolean = true) {
    val scope = rememberCoroutineScope()
    var overlayPressed by remember { mutableStateOf(false) }
    val seekContainerColor = when {
        !enabled -> HearoGreenInactive
        overlayPressed -> HearoGreenPressed
        else -> HearoGreenDefault
    }
    // Overlay with pointerInput as second child so it is hit-tested first (last child wins in Box).
    Box {
        Button(
            onClick = { },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = seekContainerColor,
                contentColor = Color.White,
                disabledContainerColor = HearoGreenInactive,
                disabledContentColor = Color.White.copy(alpha = 0.8f)
            )
        ) { Text(label) }
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                overlayPressed = true
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
                                    overlayPressed = false
                                }
                            }
                        )
                    }
            )
        }
    }
}
