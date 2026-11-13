package com.winlator.butcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Old CRT TV switching off: the picture squashes into a bright line, the line shrinks to a dot,
 * the dot fades out. Covers the whole screen and swallows touches. Calls onDone when finished.
 */
@Composable
fun TvOffOverlay(onDone: () -> Unit) {
    val squash = remember { Animatable(0f) }  // 0 = full picture, 1 = thin line
    val shrink = remember { Animatable(0f) }  // 0 = full line, 1 = dot
    val fade = remember { Animatable(0f) }    // 0 = dot visible, 1 = gone

    LaunchedEffect(Unit) {
        squash.animateTo(1f, tween(320, easing = LinearEasing))
        shrink.animateTo(1f, tween(220, easing = LinearEasing))
        fade.animateTo(1f, tween(260, easing = LinearEasing))
        onDone()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val w = size.width
        val h = size.height
        val lineH = 4f
        val bandH = h - (h - lineH) * squash.value
        val bandW = w - (w - lineH * 2f) * shrink.value
        val alpha = 1f - fade.value
        val left = (w - bandW) / 2f
        val top = (h - bandH) / 2f
        val black = Color.Black.copy(alpha = if (fade.value > 0f) alpha else 1f)
        // black outside the band, so the menu stays visible inside it while it squashes
        drawRect(black, Offset(0f, 0f), Size(w, top))
        drawRect(black, Offset(0f, top + bandH), Size(w, h - top - bandH))
        drawRect(black, Offset(0f, top), Size(left, bandH))
        drawRect(black, Offset(left + bandW, top), Size(w - left - bandW, bandH))
        // the band glows brighter as it thins out
        val glow = (squash.value * 0.9f + 0.1f) * alpha
        drawRect(
            Color(0xFFE8F4FF).copy(alpha = glow),
            Offset(left, top),
            Size(bandW, bandH)
        )
    }
}
