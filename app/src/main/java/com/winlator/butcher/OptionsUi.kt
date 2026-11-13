package com.winlator.butcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// A VHS player's on-screen setup menu: white monospace text on a dark blue panel,
// rows with a value in the middle of "< >" arrows, and segmented bars for levels.

private val PANEL = Color(0xCC000A1F)
private val PANEL_LINE = Color(0x66FFFFFF)
private val ROW_LINE = Color(0x22FFFFFF)
private val OSD_WHITE = Color(0xFFF2F2F2)
private val OSD_DIM = Color(0xFF9FB0C8)
private val OSD_RED = Color(0xFFFF6B6B)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    onCustomize: () -> Unit
) {
    BackHandler(onBack = onBack)

    VhsFrame {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BackLink("< Back to menu", onBack)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                ScreenTitle("Options")
                Spacer(Modifier.width(14.dp))
                Text(
                    "SET UP",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                    color = OSD_DIM,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Display", "Audio", "Control").forEachIndexed { index, name ->
                    OsdTab(name, index == tab) { onTab(index) }
                }
            }

            // The panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(PANEL)
                    .border(1.dp, PANEL_LINE)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                when (tab) {
                    0 -> DisplayTab(settings)
                    1 -> AudioTab(settings)
                    else -> ControlTab(settings, onCustomize)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Saved automatically and applied when you press Start Snuff.\n" +
                        "Not applied yet: V-Sync, Music, Effect. Boost audio may not work on every phone.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = OSD_DIM
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "TAP < > TO CHANGE",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = OSD_DIM,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun OsdTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) OSD_WHITE else Color(0x55000A1F))
            .border(1.dp, PANEL_LINE)
            .vhsClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = if (selected) Color.Black else OSD_WHITE
        )
    }
}

/** A row of the setup menu: the name on the left, the control on the right. */
@Composable
private fun OsdRow(label: String, control: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                color = OSD_WHITE,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            control()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ROW_LINE))
    }
}

/** A titled block for things that are not a simple value (buttons, import). */
@Composable
fun OptionBlock(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "▌" + label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = OSD_RED
        )
        content()
        Box(Modifier.fillMaxWidth().height(1.dp).background(ROW_LINE))
    }
}

/** Value between two arrows. Tap an arrow (or the value) to step through the choices. */
@Composable
fun ChoiceRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val index = options.indexOf(selected).coerceAtLeast(0)

    fun step(delta: Int) {
        VhsSound.click()
        onSelect(options[(index + delta + options.size) % options.size])
    }

    OsdRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow("◄") { step(-1) }
            Text(
                selected.uppercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier
                    .widthIn(min = 130.dp)
                    .padding(horizontal = 6.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .vhsClickable { step(1) }
                    .padding(vertical = 10.dp)
            )
            Arrow("►") { step(1) }
        }
    }
}

@Composable
private fun Arrow(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, PANEL_LINE)
            .clickableQuiet(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = OSD_RED)
    }
}

private fun Modifier.clickableQuiet(onClick: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTapGestures { onClick() } })

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ChoiceRow(label, listOf("Off", "On"), if (value) "On" else "Off") { onChange(it == "On") }
}

/** A level shown as a row of blocks. Tap or drag across it. */
@Composable
fun PercentSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    val segments = 20
    val span = range.endInclusive - range.start
    val filled = if (span <= 0f) 0 else (((value - range.start) / span) * segments).roundToInt().coerceIn(0, segments)
    var width by remember { mutableStateOf(1f) }
    var lastTick by remember { mutableStateOf(filled) }

    fun set(x: Float) {
        val fraction = (x / width).coerceIn(0f, 1f)
        val snapped = (fraction * segments).roundToInt() / segments.toFloat()
        val newValue = range.start + span * snapped
        val seg = (snapped * segments).roundToInt()
        if (seg != lastTick) { lastTick = seg; VhsSound.click() }
        onChange(newValue)
    }

    OsdRow(label) {
        Text(
            "${(value * 100).roundToInt()}%",
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(bottom = 6.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { set(it.x) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> set(change.position.x) } },
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(if (i < filled) OSD_WHITE else Color(0x33FFFFFF))
            )
        }
    }
}
