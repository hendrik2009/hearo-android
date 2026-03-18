package com.example.hearo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val playerGroupBorder = BorderStroke(1.dp, Color(0xFF424242))
private val playerGroupShape = RoundedCornerShape(10.dp)

@Composable
fun PlayerGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = playerGroupShape,
        color = Color.White.copy(alpha = 0.4f),
        border = playerGroupBorder
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

/** Display-only text field: white background, black text and label, gray border. Used for NFC ID and Playlist URI. */
@Composable
fun PlayerDisplayTextField(
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

/** Player UI button: same red filled style as InitialScreen Sign in button. */
@Composable
fun PlayerOutlinedButton(
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
