package com.winlator.butcher

import com.winlator.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Keeps only the red or only the blue part of the picture, used for the colour-split ghost.
private val RED_ONLY = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

private val BLUE_ONLY = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/**
 * A clock that ticks once per screen frame while the menu is showing. The value is nanoseconds since
 * the menu appeared. It stops by itself when the menu is left or the app is in the background.
 */
@Composable
fun rememberVhsClock(): State<Long> {
    val clock = remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        var first = -1L
        while (true) {
            withFrameNanos { now ->
                if (first < 0L) first = now
                clock.value = now - first
            }
        }
    }
    return clock
}

/** The menu picture with a slow wobble, colour split, a rolling tracking band and random tear glitches. */
@Composable
fun VhsBackground(clock: State<Long>) {
    val image = ImageBitmap.imageResource(R.drawable.menu_bg)

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value / 1_000_000_000f
        val w = size.width
        val h = size.height
        val iw = image.width
        val ih = image.height

        // Fill the screen with the picture (crop the extra), centered.
        val scale = max(w / iw, h / ih)
        val dw = (iw * scale).roundToInt()
        val dh = (ih * scale).roundToInt()
        val dx = ((w - dw) / 2f).roundToInt()
        val dy = ((h - dh) / 2f).roundToInt()
        val full = IntSize(dw, dh)
        val wobble = (sin(t * 2.1f) * 2f).roundToInt()

        drawImage(
            image,
            dstOffset = IntOffset(dx + wobble, dy),
            dstSize = full,
            filterQuality = FilterQuality.Low
        )

        // Red and blue ghosts, slightly apart, like worn tape.
        val split = (3f + 2f * sin(t * 1.3f)).roundToInt()
        drawImage(
            image,
            dstOffset = IntOffset(dx + wobble + split, dy),
            dstSize = full,
            alpha = 0.3f,
            colorFilter = RED_ONLY,
            blendMode = BlendMode.Plus,
            filterQuality = FilterQuality.Low
        )
        drawImage(
            image,
            dstOffset = IntOffset(dx + wobble - split, dy),
            dstSize = full,
            alpha = 0.3f,
            colorFilter = BLUE_ONLY,
            blendMode = BlendMode.Plus,
            filterQuality = FilterQuality.Low
        )

        // Redraws a horizontal slice of the picture pushed sideways: a tape tracking error.
        fun band(y: Float, bandHeight: Float, shift: Int) {
            val top = y.coerceIn(0f, h)
            val bottom = (y + bandHeight).coerceIn(0f, h)
            if (bottom - top < 2f) return
            val srcY = ((top - dy) / scale).toInt().coerceIn(0, ih - 1)
            val srcH = ((bottom - top) / scale).toInt().coerceIn(1, ih - srcY)
            drawImage(
                image,
                srcOffset = IntOffset(0, srcY),
                srcSize = IntSize(iw, srcH),
                dstOffset = IntOffset(dx + wobble + shift, top.roundToInt()),
                dstSize = IntSize(dw, (srcH * scale).roundToInt()),
                filterQuality = FilterQuality.Low
            )
        }

        // A thin band that keeps rolling down the screen and wiggles sideways.
        val bandY = ((t * 0.16f) % 1.25f - 0.12f) * h
        band(bandY, h * 0.06f, (sin(t * 45f) * 12f).roundToInt())

        // Every few seconds, a short burst of bigger glitches.
        if (t % 6f < 0.3f) {
            val rnd = Random((t * 25f).toInt())
            repeat(3) {
                band(
                    rnd.nextFloat() * h,
                    h * (0.02f + rnd.nextFloat() * 0.06f),
                    (rnd.nextFloat() * 60f - 30f).roundToInt()
                )
            }
        }
    }
}

/** Scanlines, flicker, static specks and dark corners drawn over everything. It does not block touches. */
@Composable
fun VhsOverlay(clock: State<Long>) {
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value / 1_000_000_000f
        val w = size.width
        val h = size.height

        // Brightness flicker.
        drawRect(Color.Black.copy(alpha = (0.05f + 0.04f * sin(t * 23f)).coerceIn(0f, 1f)))

        // Scanlines.
        val step = 4.dp.toPx()
        val thick = 1.5f.dp.toPx()
        var y = 0f
        while (y < h) {
            drawRect(
                Color.Black.copy(alpha = 0.22f),
                topLeft = Offset(0f, y),
                size = Size(w, thick)
            )
            y += step
        }

        // A faint light band rolling slowly down.
        val bandY = ((t * 0.05f) % 1.3f - 0.15f) * h
        drawRect(
            Color.White.copy(alpha = 0.05f),
            topLeft = Offset(0f, bandY),
            size = Size(w, h * 0.12f)
        )

        // Static specks that change about 20 times a second.
        val rnd = Random((t * 20f).toInt())
        repeat(140) {
            drawRect(
                Color.White.copy(alpha = 0.06f + rnd.nextFloat() * 0.2f),
                topLeft = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h),
                size = Size(4f + rnd.nextFloat() * 36f, 1.5f + rnd.nextFloat() * 2.5f)
            )
        }

        // Dark corners.
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                center = center,
                radius = max(w, h) * 0.8f
            )
        )
    }
}
