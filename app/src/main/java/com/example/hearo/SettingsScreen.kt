package com.example.hearo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTapSound: () -> Unit = {},
    nfcId: String = "",
    playlistUrl: String = "",
    showSaveButton: Boolean = false,
    saveButtonEnabled: Boolean = false,
    showDetachButton: Boolean = false,
    onSaveClick: () -> Unit = {},
    onDetachClick: () -> Unit = {},
    preferredDeviceDisplay: String = "No preferred device",
    onEditPreferredDeviceClick: () -> Unit = {},
    listeningCounterDisplay: String = "0:00",
    listeningLimitDisplay: String = "1:00:00",
    listeningLimitReached: Boolean = false,
    listeningProgress: Float = 0f,
    onResetListeningTime: () -> Unit = {},
    onEditListeningLimitClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var unlocked by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var wrongPinShown by remember { mutableStateOf(false) }
    val pinFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(unlocked) {
        if (unlocked) {
            keyboardController?.hide()
        } else {
            kotlinx.coroutines.delay(100L)
            pinFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(wrongPinShown) {
        if (wrongPinShown) {
            kotlinx.coroutines.delay(2000L)
            wrongPinShown = false
            pinInput = ""
            pinFocusRequester.requestFocus()
        }
    }

    fun tryPin(digits: String) {
        if (digits.length != 4) return
        onTapSound()
        if (digits == "0000") {
            unlocked = true
            pinInput = ""
        } else {
            wrongPinShown = true
            pinInput = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEDE8D0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onTapSound()
                    onBack()
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back_48),
                    contentDescription = "Back",
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF333333)
            )
        }

        if (!unlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(1f / 3f)
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Text(
                    text = "Enter PIN",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF333333)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap the boxes and type 4 digits",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = pinInput,
                        onValueChange = { newValue ->
                            if (wrongPinShown) wrongPinShown = false
                            val digits = newValue.filter { it.isDigit() }.take(4)
                            pinInput = digits
                            if (digits.length == 4) tryPin(digits)
                        },
                        modifier = Modifier
                            .size(1.dp)
                            .focusRequester(pinFocusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(Color.Transparent),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Transparent),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.size(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                innerTextField()
                            }
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            val filled = index < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(width = 52.dp, height = 56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (filled) Color(0xFF333333) else Color(0xFFAAAAAA),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(Color(0xFFF5F2E8))
                                    .clickable {
                                        pinFocusRequester.requestFocus()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (filled) {
                                    Text(
                                        text = "\u2022",
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = Color(0xFF333333)
                                    )
                                }
                            }
                        }
                    }
                }
                if (wrongPinShown) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Wrong PIN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
            PlayerGroup {
                PlayerDisplayTextField(value = nfcId.ifEmpty { "No Tag" }, label = "NFC ID")
                Spacer(Modifier.height(8.dp))
                PlayerDisplayTextField(value = playlistUrl.ifEmpty { "-" }, label = "Playlist URI")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showSaveButton) {
                        PlayerOutlinedButton(onClick = { onTapSound(); onSaveClick() }, enabled = saveButtonEnabled) { Text("Save") }
                    }
                    if (showDetachButton) {
                        PlayerOutlinedButton(onClick = { onTapSound(); onDetachClick() }) { Text("Discard") }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            PlayerGroup {
                PlayerDisplayTextField(value = preferredDeviceDisplay, label = "Preferred device")
                Spacer(Modifier.height(8.dp))
                PlayerOutlinedButton(onClick = { onTapSound(); onEditPreferredDeviceClick() }) { Text("Edit") }

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
                    text = "Time left: $listeningCounterDisplay",
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
                    PlayerOutlinedButton(onClick = { onTapSound(); onResetListeningTime() }) { Text("Reset") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = listeningLimitDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { onTapSound(); onEditListeningLimitClick() },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Black)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.edit_24),
                                contentDescription = "Set listening limit"
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
