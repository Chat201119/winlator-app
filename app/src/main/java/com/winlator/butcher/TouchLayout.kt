package com.winlator.butcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One on-screen touch button. Position is a fraction of the screen (0..1), size is dp * scale. */
data class TouchItem(
    val key: String,
    val label: String,
    val defX: Float,
    val defY: Float,
    val defScale: Float
)

val TOUCH_ITEMS = listOf(
    TouchItem("move", "MOVE", 0.13f, 0.68f, 1.8f),
    TouchItem("sprint", "SPRINT", 0.13f, 0.30f, 0.9f),
    TouchItem("attack", "ATTACK", 0.72f, 0.80f, 1.3f),
    TouchItem("aim", "AIM", 0.72f, 0.56f, 0.9f),
    TouchItem("use", "USE", 0.60f, 0.80f, 0.9f),
    TouchItem("reload", "RELOAD", 0.60f, 0.58f, 0.8f),
    TouchItem("jump", "JUMP", 0.93f, 0.42f, 1.0f),
    TouchItem("crouch", "CROUCH", 0.93f, 0.22f, 0.9f),
    TouchItem("pause", "PAUSE", 0.50f, 0.08f, 0.7f)
)

private const val BASE_SIZE_DP = 72f

/**
 * Full-screen black editor. Drag a button to move it, tap to select it, then use the slider
 * to make it smaller or bigger. The layout is saved when you press Done or Back.
 */
@Composable
fun TouchEditor(settings: AppSettings, onDone: () -> Unit) {
    val xs = remember {
        mutableStateMapOf<String, Float>().apply { TOUCH_ITEMS.forEach { put(it.key, settings.touchX(it)) } }
    }
    val ys = remember {
        mutableStateMapOf<String, Float>().apply { TOUCH_ITEMS.forEach { put(it.key, settings.touchY(it)) } }
    }
    val scales = remember {
        mutableStateMapOf<String, Float>().apply { TOUCH_ITEMS.forEach { put(it.key, settings.touchScale(it)) } }
    }
    var selected by remember { mutableStateOf(TOUCH_ITEMS.first().key) }
    val density = LocalDensity.current

    fun finish() {
        settings.saveTouchLayout(xs, ys, scales)
        onDone()
    }

    BackHandler { finish() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        TOUCH_ITEMS.forEach { item ->
            val x = xs[item.key] ?: item.defX
            val y = ys[item.key] ?: item.defY
            val scale = scales[item.key] ?: item.defScale
            val size = (BASE_SIZE_DP * scale).dp
            val sizePx = with(density) { size.toPx() }
            val isSelected = selected == item.key

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (x * widthPx - sizePx / 2f).roundToInt(),
                            (y * heightPx - sizePx / 2f).roundToInt()
                        )
                    }
                    .size(size)
                    .background(if (isSelected) Color(0x99B01010) else Color(0x44FFFFFF), CircleShape)
                    .border(2.dp, if (isSelected) Color.White else Color(0x99FFFFFF), CircleShape)
                    .pointerInput(item.key) {
                        detectTapGestures { selected = item.key }
                    }
                    .pointerInput(item.key, widthPx, heightPx) {
                        detectDragGestures(onDragStart = { selected = item.key }) { change, dragAmount ->
                            change.consume()
                            xs[item.key] = ((xs[item.key] ?: item.defX) + dragAmount.x / widthPx)
                                .coerceIn(0.03f, 0.97f)
                            ys[item.key] = ((ys[item.key] ?: item.defY) + dragAmount.y / heightPx)
                                .coerceIn(0.05f, 0.95f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        val current = TOUCH_ITEMS.first { it.key == selected }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Drag a button to move it. Tap one to select it.",
                fontSize = 12.sp,
                color = Color.White
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${current.label} size", fontSize = 12.sp, color = Color.White)
                Slider(
                    value = scales[current.key] ?: current.defScale,
                    onValueChange = { scales[current.key] = it },
                    valueRange = 0.5f..2.5f,
                    modifier = Modifier
                        .width(220.dp)
                        .padding(horizontal = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    xs[current.key] = current.defX
                    ys[current.key] = current.defY
                    scales[current.key] = current.defScale
                }) { Text("Reset this") }

                OutlinedButton(onClick = {
                    TOUCH_ITEMS.forEach {
                        xs[it.key] = it.defX
                        ys[it.key] = it.defY
                        scales[it.key] = it.defScale
                    }
                }) { Text("Reset all") }

                Button(
                    onClick = { finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A0A0A))
                ) { Text("Done") }
            }
        }
    }
}
