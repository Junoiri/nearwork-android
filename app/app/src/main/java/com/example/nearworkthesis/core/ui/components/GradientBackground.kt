package com.example.nearworkthesis.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.painterResource
import com.example.nearworkthesis.R
import com.example.nearworkthesis.core.ui.theme.BrightSnow
import com.example.nearworkthesis.core.ui.theme.CarbonBlack
import com.example.nearworkthesis.core.ui.theme.Periwinkle
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val centreColor = if (isDark) Color.Black else BrightSnow
    val edgeColor = if (isDark) CarbonBlack else Periwinkle
    val ringPainter = if (isDark) {
        painterResource(R.drawable.gradient_ring)
    } else {
        painterResource(R.drawable.ring_white)
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val radiusPx = with(LocalDensity.current) {
            sqrt(maxWidth.toPx().pow(2) + maxHeight.toPx().pow(2))
        }
        val brush = Brush.radialGradient(
            colors = listOf(centreColor, edgeColor),
            center = Offset(0f, 0f),
            radius = radiusPx
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )

        val ringSize = maxWidth * 1.6f
        val xOffset = maxWidth * 0.35f
        val yOffset = maxHeight * 0.9f - ringSize / 2
        Image(
            painter = ringPainter,
            contentDescription = null,
            modifier = Modifier
                .size(ringSize)
                .absoluteOffset(x = xOffset, y = yOffset)
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

@Composable
fun DarkGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    GradientBackgroundImpl(Color.Black, CarbonBlack, modifier, content)
}

@Composable
private fun GradientBackgroundImpl(
    centreColor: Color,
    edgeColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val radiusPx = with(LocalDensity.current) {
            sqrt(maxWidth.toPx().pow(2) + maxHeight.toPx().pow(2))
        }
        val brush = Brush.radialGradient(
            colors = listOf(centreColor, edgeColor),
            center = Offset(0f, 0f),
            radius = radiusPx
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush),
            content = content
        )
    }
}
