package com.example.hearo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Shown while processing auth redirect; avoids flashing InitialScreen and uses theme background (no black). */
@Composable
fun RedirectHandlingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    )
}

/** Start screen: top 4/5 image (width 100%, proportional, top-center, crop if needed), bottom 1/5 white bar with Sign in button. */
@Composable
fun InitialScreen(
    onSignInClick: () -> Unit,
    onTapSound: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .align(Alignment.TopCenter)
                .clipToBounds()
                .background(Color(0xFFFFF8E7)),
            contentAlignment = Alignment.TopCenter,
        ) {
            Image(
                painter = painterResource(id = R.drawable.hearo_teaser),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.TopCenter,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.2f)
                .align(Alignment.BottomCenter)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = { onTapSound(); onSignInClick() },
                modifier = Modifier.widthIn(max = 280.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF0000),
                    contentColor = Color.White,
                ),
            ) {
                Text("Sign in")
            }
        }
    }
}
