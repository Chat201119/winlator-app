package com.winlator.butcher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tape-deck sounds for the menus. They are made in code (no sound files): a mechanical
 * "clunk" with a low thump for a normal button, and a longer tape-loading whirr for big actions.
 */
object VhsSound {
    private const val RATE = 22050
    private var click: AudioTrack? = null
    private var whirr: AudioTrack? = null

    private fun generate(ms: Int, f0: Double, f1: Double, decay: Double, noise: Double, wobble: Double): ShortArray {
        val n = RATE * ms / 1000
        val rnd = Random(7)
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val p = i.toDouble() / n
            val f = (f0 + (f1 - f0) * p) * (1.0 + wobble * sin(2 * PI * 9.0 * t))
            phase += 2 * PI * f / RATE
            val env = exp(-t * decay)
            val clunk = if (t < 0.012) (rnd.nextDouble() * 2 - 1) * (1 - t / 0.012) else 0.0
            val hiss = (rnd.nextDouble() * 2 - 1) * noise * exp(-t * decay * 0.6)
            val s = sin(phase) * 0.7 * env + clunk * 0.8 + hiss
            out[i] = (s.coerceIn(-1.0, 1.0) * 26000).toInt().toShort()
        }
        return out
    }

    private fun build(samples: ShortArray): AudioTrack? = try {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track
    } catch (e: Exception) {
        null
    }

    private fun play(track: AudioTrack?) {
        try {
            track ?: return
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (e: Exception) {
            // No sound is better than a crash.
        }
    }

    fun click() {
        if (click == null) click = build(generate(140, 95.0, 45.0, 22.0, 0.10, 0.0))
        play(click)
    }

    fun whirr() {
        if (whirr == null) whirr = build(generate(520, 210.0, 70.0, 5.0, 0.12, 0.06))
        play(whirr)
    }
}

fun Modifier.vhsClickable(big: Boolean = false, onClick: () -> Unit): Modifier = this.clickable {
    if (big) VhsSound.whirr() else VhsSound.click()
    onClick()
}

/** A tape-label style button: monospace capitals, red when selected, with a click sound. */
@Composable
fun VhsButton(text: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Box(
        modifier = modifier
            .background(if (selected) BRAND_RED else Color(0x88000000), shape)
            .border(1.dp, if (selected) Color(0xFFFF6B6B) else Color(0x99FFFFFF), shape)
            .vhsClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color.White
        )
    }
}

/** A dark screen with the VHS look: scanlines, static, flicker and the PLAY / tape counter text. */
@Composable
fun VhsFrame(content: @Composable BoxScope.() -> Unit) {
    val clock = rememberVhsClock()
    val seconds by remember { derivedStateOf { (clock.value / 1_000_000_000L).toInt() } }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        content()
        Text(
            "PLAY ▶",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(top = 12.dp, end = 24.dp)
        )
        Text(
            "SP %d:%02d:%02d".format((seconds / 3600) % 10, (seconds / 60) % 60, seconds % 60),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(bottom = 12.dp, end = 24.dp)
        )
        VhsOverlay(clock)
    }
}
