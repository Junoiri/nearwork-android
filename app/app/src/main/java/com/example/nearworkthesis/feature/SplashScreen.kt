package com.example.nearworkthesis.feature

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.nearworkthesis.R
import com.example.nearworkthesis.core.ui.components.DarkGradientBackground
import com.example.nearworkthesis.core.ui.theme.BrightSnow
import com.example.nearworkthesis.core.ui.theme.NearworkTheme
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(4000L)
        onFinished()
    }

    SplashScreenContent()
}

@Composable
fun SplashScreenContent() {
    DarkGradientBackground {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.gradient_ring),
                        contentDescription = null,
                        modifier = Modifier.size(360.dp)
                    )
                    Image(
                        painter = painterResource(R.drawable.logo_white),
                        contentDescription = "CloseCall logo",
                        modifier = Modifier.width(88.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "CloseCall",
                    style = MaterialTheme.typography.displaySmall,
                    color = BrightSnow
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "An app that warns you when your nearwork habits are becoming a close call for your eyes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = BrightSnow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(280.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    NearworkTheme {
        SplashScreenContent()
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SplashScreenPreviewDark() {
    NearworkTheme {
        SplashScreenContent()
    }
}
