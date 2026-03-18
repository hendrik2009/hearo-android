package com.example.hearo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.ripple
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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

private val nfcDotRed = Color(0xFFFF0000)
private val nfcDotBlue = Color(0xFF2196F3)
private val nfcDotWhite = Color(0xFFFFFFFF)

/** Centered dot: static when no tag, pulse + ripples when tag present. Red = no tag, blue = tag with playlist, white = unknown tag. */
@Composable
private fun NfcPulseDot(hasTag: Boolean, tagHasPlaylist: Boolean = false) {
    val dotColor = when {
        !hasTag -> nfcDotRed
        tagHasPlaylist -> nfcDotBlue
        else -> nfcDotWhite
    }
    val sizeDp = 60.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasTag) {
            val infiniteTransition = rememberInfiniteTransition(label = "nfcPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 2000
                        1f at 0
                        1.1f at 66
                        1f at 330
                        1.1f at 660
                        1f at 2000
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse"
            )
            val ripple1Progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 2000
                        0f at 0
                        1f at 2000
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple1"
            )
            val ripple2Progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 2330
                        0f at 0
                        0f at 330
                        1f at 2330
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "ripple2"
            )
            Box(
                modifier = Modifier.size(sizeDp * 3.5f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .graphicsLayer {
                            scaleX = 1f + ripple2Progress * 2f
                            scaleY = 1f + ripple2Progress * 2f
                            alpha = 0.6f * (1f - ripple2Progress)
                        }
                        .background(dotColor.copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .graphicsLayer {
                            scaleX = 1f + ripple1Progress * 2f
                            scaleY = 1f + ripple1Progress * 2f
                            alpha = 0.6f * (1f - ripple1Progress)
                        }
                        .background(dotColor.copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(sizeDp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .background(dotColor, CircleShape)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(sizeDp)
                    .background(dotColor, CircleShape)
            )
        }
    }
}

/** Icon-only transport button. Active = black, inactive = light grey. Visible ripple, scale 0.8 on press. */
@Composable
private fun PlayerIconButton(
    onClick: () -> Unit,
    iconResId: Int,
    contentDescription: String,
    enabled: Boolean = true,
    iconSize: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .scale(if (isPressed) 0.8f else 1f)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                onClick = onClick,
                indication = ripple(color = Color.Black.copy(alpha = 0.9f), bounded = true),
                interactionSource = interactionSource
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) Color.Black else Color(0xFFD3D3D3)
        )
    }
}

@Composable
fun HearoScreen(
    artistName: String = "Artist",
    trackTitle: String = "Title",
    progressMs: Long = 0L,
    durationMs: Long = 0L,
    nfcId: String = "",
    signedIn: Boolean = false,
    nfcReady: Boolean = true,
    showSignInBanner: Boolean = false,
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onSkipBack: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onSeekFwd: () -> Unit = {},
    onSkipFwd: () -> Unit = {},
    listeningLimitReached: Boolean = false,
    listeningProgress: Float = 0f,
    tagHasPlaylist: Boolean = false,
    onTapSound: () -> Unit = {},
    onSeekRepeatSound: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
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

            if (listeningLimitReached) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_out_of_playtime),
                        contentDescription = "You ran out of playtime",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .heightIn(max = 200.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(16.dp))
            } else {
                PlayerGroup {
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerIconButton(
                                onClick = { onTapSound(); onSkipBack() },
                                iconResId = R.drawable.ic_skip_previous_48,
                                contentDescription = "Skip back",
                                enabled = transportEnabled
                            )
                            PlayerSeekButton(
                                iconResId = R.drawable.ic_fast_rewind_48,
                                contentDescription = "Seek back",
                                onSeek = onSeekBack,
                                enabled = transportEnabled,
                                onTapSound = onTapSound,
                                onSeekRepeatSound = onSeekRepeatSound
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlayerSeekButton(
                                iconResId = R.drawable.ic_fast_forward_48,
                                contentDescription = "Seek forward",
                                onSeek = onSeekFwd,
                                enabled = transportEnabled,
                                onTapSound = onTapSound,
                                onSeekRepeatSound = onSeekRepeatSound
                            )
                            PlayerIconButton(
                                onClick = { onTapSound(); onSkipFwd() },
                                iconResId = R.drawable.ic_skip_next_48,
                                contentDescription = "Skip forward",
                                enabled = transportEnabled
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(2.dp, Color(0xFFFF0000), RoundedCornerShape(8.dp))
                    ) {
                        Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(listeningProgress)
                            .background(Color(0xFFFF0000), RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            NfcPulseDot(hasTag = nfcId.isNotEmpty(), tagHasPlaylist = tagHasPlaylist)
            Spacer(Modifier.height(60.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (signedIn) {
                    IconButton(
                        onClick = { onTapSound(); onSettingsClick() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_48),
                            contentDescription = "Settings",
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (signedIn) {
                    IconButton(
                        onClick = { onTapSound(); onSignOutClick() }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logout_48),
                            contentDescription = "Sign out",
                            tint = Color.Black,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    PlayerOutlinedButton(onClick = { onTapSound(); onSignInClick() }) { Text("Sign in with Spotify") }
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
    enabled: Boolean,
    onTapSound: () -> Unit = {},
    onSeekRepeatSound: () -> Unit = {}
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
                                onTapSound()
                                onSeek()
                                val job = scope.launch {
                                    while (isActive) {
                                        delay(SEEK_HOLD_INTERVAL_MS)
                                        onSeekRepeatSound()
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
