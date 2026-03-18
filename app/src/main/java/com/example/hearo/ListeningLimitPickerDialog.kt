package com.example.hearo

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.ExperimentalMaterial3Api

/** Converts limit [seconds] to (hour, minute) for Time Picker. 24h -> (23, 59) for picker display. */
fun listeningLimitSecondsToHourMin(seconds: Int): Pair<Int, Int> {
    val s = seconds.coerceIn(ListeningTimeStore.MIN_LIMIT_SECONDS, ListeningTimeStore.MAX_LIMIT_SECONDS)
    return if (s >= 24 * 3600) 23 to 59 else (s / 3600) to ((s % 3600) / 60)
}

/** Converts (hour, minute) from Time Picker to limit seconds. 23:59 is treated as 24h. */
fun hourMinToListeningLimitSeconds(hour: Int, minute: Int): Int {
    return if (hour == 23 && minute == 59) 24 * 3600
    else (hour * 3600 + minute * 60).coerceIn(ListeningTimeStore.MIN_LIMIT_SECONDS, ListeningTimeStore.MAX_LIMIT_SECONDS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningLimitPickerDialog(
    initialLimitSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    onTapSound: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val (initHour, initMinute) = listeningLimitSecondsToHourMin(initialLimitSeconds)
    val state = rememberTimePickerState(
        initialHour = initHour,
        initialMinute = initMinute,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Listening limit") },
            text = {
                TimePicker(state = state)
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTapSound()
                        val seconds = hourMinToListeningLimitSeconds(state.hour, state.minute)
                        onConfirm(seconds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000), contentColor = Color.White)
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { onTapSound(); onDismiss() }) {
                    Text("Cancel")
                }
            }
        )
    }
}
