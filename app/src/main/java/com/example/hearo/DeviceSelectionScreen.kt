package com.example.hearo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val NO_DEVICE_ID = ""

/** Display label for a device: "Name (Type)" or "Name" or id fallback. */
private fun SpotifyDevice.displayLabel(): String {
    val n = name?.takeIf { it.isNotBlank() } ?: id
    val t = type?.takeIf { it.isNotBlank() }
    return if (t != null) "$n ($t)" else n
}

/**
 * Screen to choose the preferred Spotify device. Shows "No Device" plus current devices from API.
 * Save persists selection; Cancel closes without applying.
 */
@Composable
fun DeviceSelectionScreen(
    currentDevices: List<SpotifyDevice>,
    currentPreferredId: String?,
    onSave: (deviceId: String?, deviceName: String?, deviceType: String?) -> Unit,
    onCancel: () -> Unit,
    onTapSound: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedId by remember(currentPreferredId) {
        mutableStateOf(currentPreferredId ?: NO_DEVICE_ID)
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Preferred playback device",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = NO_DEVICE_ID) {
                DeviceRow(
                    label = "No Device",
                    id = NO_DEVICE_ID,
                    selected = selectedId == NO_DEVICE_ID,
                    onClick = { onTapSound(); selectedId = NO_DEVICE_ID }
                )
            }
            items(currentDevices, key = { it.id }) { device ->
                DeviceRow(
                    label = device.displayLabel(),
                    id = device.id,
                    selected = selectedId == device.id,
                    onClick = { onTapSound(); selectedId = device.id }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onTapSound(); onCancel() }) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onTapSound()
                    if (selectedId == NO_DEVICE_ID) {
                        onSave(null, null, null)
                    } else {
                        val d = currentDevices.find { it.id == selectedId }
                        onSave(selectedId, d?.name, d?.type)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White)
            ) { Text("Save") }
        }
    }
}

@Composable
private fun DeviceRow(
    label: String,
    id: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFFFF0000).copy(alpha = 0.15f) else Color.White,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Black,
            modifier = Modifier.padding(16.dp)
        )
    }
}
