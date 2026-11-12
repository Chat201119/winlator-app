package com.winlator.butcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import kotlinx.coroutines.delay
import android.provider.OpenableColumns
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

val BRAND_RED = Color(0xFF7A0A0A)
val SOFT_GREY = Color(0xFFBBBBBB)

class MenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var screen by remember { mutableStateOf("home") }
                var settingsTab by remember { mutableStateOf(0) }

                // Plain dark background everywhere; only the main menu draws the picture.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0B0B),
                    contentColor = Color.White
                ) {
                    when (screen) {
                        "home" -> HomeScreen(
                            settings = settings,
                            onDemo = { screen = "demo" },
                            onSettings = { screen = "settings" },
                            onSetup = { screen = "setup" },
                            onQuit = { finishAffinity() }
                        )
                        "settings" -> SettingsScreen(
                            settings = settings,
                            tab = settingsTab,
                            onTab = { settingsTab = it },
                            onBack = { screen = "home" },
                            onCustomize = { screen = "editor" }
                        )
                        "demo" -> DemoScreen(settings = settings, onBack = { screen = "home" })
                        "setup" -> SetupScreen(settings = settings, onBack = { screen = "home" })
                        "editor" -> TouchEditor(settings = settings, onDone = { screen = "settings" })
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------ home

@Composable
fun HomeScreen(
    settings: AppSettings,
    onDemo: () -> Unit,
    onSettings: () -> Unit,
    onSetup: () -> Unit,
    onQuit: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var hasGame by remember { mutableStateOf(settings.hasGame()) }
    val openImport = rememberGameImporter(settings) { hasGame = true }
    val clock = rememberVhsClock()
    // Changes only once a second, so the screen is not rebuilt every frame just for the counter.
    val seconds by remember { derivedStateOf { (clock.value / 1_000_000_000L).toInt() } }

    var showPcWarning by remember { mutableStateOf(false) }
    val startHint = if (!hasGame) "No game yet. Tap to import yours." else null


    if (showPcWarning) {
        AlertDialog(
            onDismissRequest = { showPcWarning = false },
            title = { Text("PC emulator detected") },
            text = {
                Text(
                    "This looks like a PC Android emulator (for example LDPlayer), not an ARM phone. " +
                        "The game system inside this app is built for ARM phones and normally crashes " +
                        "on PC emulators. If you are on a PC, run the normal Windows version of the " +
                        "game instead. You can still try starting it."
                )
            },
            confirmButton = {
                VhsButton("Start anyway") {
                    showPcWarning = false
                    val message = Bridge.start(activity, settings)
                    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            dismissButton = { VhsButton("Cancel") { showPcWarning = false } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        VhsBackground(clock)
        // Darker on the left so the menu text is easy to read.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.9f), Color.Black.copy(alpha = 0.3f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(start = 40.dp, end = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "BUTCHER CREEKS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                "Unofficial android port ($GPU_FAMILY)",
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                color = SOFT_GREY
            )
            Spacer(Modifier.height(20.dp))

            MenuItem(if (hasGame) "Start Snuff Film" else "Start Snuff", startHint, big = true) {
                if (!hasGame) {
                    openImport()
                } else if (!runsOnArmPhone()) {
                    showPcWarning = true
                } else {
                    // Goes straight into the game. No container screens.
                    val message = Bridge.start(activity, settings)
                    if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
            MenuItem("Borrow Demo", onClick = onDemo)
            MenuItem("Settings", onClick = onSettings)
            MenuItem("Quits watching", onClick = onQuit)
        }

        Text(
            "Game setup",
            fontSize = 12.sp,
            color = SOFT_GREY,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(start = 40.dp, bottom = 12.dp)
                .vhsClickable(onClick = onSetup)
        )

        // Small credits on the right side.
        var noteKey by remember { mutableStateOf(0) }
        var showNote by remember { mutableStateOf(false) }
        LaunchedEffect(noteKey) {
            if (noteKey > 0) {
                showNote = true
                delay(5000)
                showNote = false
            }
        }
        fun open(url: String) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(context, "No browser found.", Toast.LENGTH_SHORT).show()
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .safeDrawingPadding()
                .padding(end = 24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("CREDITS", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFFF6B6B))
            listOf(
                "David Szymanski" to { open("https://steamcommunity.com/groups/davidszymanski") },
                "CLAUDE" to { open("https://claude.ai") },
                "Chat201119" to { noteKey++; Unit },
                "Winlator" to { open("https://github.com/brunodev85/winlator") }
            ).forEach { (label, action) ->
                Text(
                    label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = SOFT_GREY,
                    modifier = Modifier
                        .vhsClickable(onClick = action)
                        .padding(vertical = 2.dp)
                )
            }
        }
        if (showNote) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .background(Color(0xDD000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    "this is useless guy so don't care about him",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }

        // VHS on-screen text.
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

        // Scanlines, static and flicker on top of everything. It does not block touches.
        VhsOverlay(clock)
    }
}

@Composable
fun MenuItem(label: String, hint: String? = null, big: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .vhsClickable(big = big, onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        if (hint != null) Text(hint, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SOFT_GREY)
    }
}

@Composable
fun BackLink(text: String, onClick: () -> Unit) {
    Text(
        text.uppercase(),
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFFF6B6B),
        modifier = Modifier
            .vhsClickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text.uppercase(),
        fontFamily = FontFamily.Monospace,
        fontSize = 26.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 4.sp
    )
}

// -------------------------------------------------------------------------- settings

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
            BackLink("< Back to menu", onBack)
            ScreenTitle("Options")
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Display", "Audio", "Control").forEachIndexed { index, name ->
                    VhsButton(name, selected = index == tab) { onTab(index) }
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (tab) {
                    0 -> DisplayTab(settings)
                    1 -> AudioTab(settings)
                    else -> ControlTab(settings, onCustomize)
                }
                Text(
                    "Saved automatically and applied when you press Start Snuff. Not applied yet: " +
                        "V-Sync, Music and Effect (the game system has no switch for them). " +
                        "Boost audio may not work on every phone.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = SOFT_GREY
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun DisplayTab(settings: AppSettings) {
    var resolution by remember { mutableStateOf(settings.resolution) }
    var preset by remember { mutableStateOf(settings.graphicsPreset) }
    var overlay by remember { mutableStateOf(settings.overlay) }
    var vsync by remember { mutableStateOf(settings.vsync) }
    var driver by remember { mutableStateOf(settings.graphicsDriver) }
    var renderer by remember { mutableStateOf(settings.renderer) }

    DriverImportBlock(settings)

    ChoiceRow("Renderer (Vulkan or OpenGL)", RENDERERS, renderer) {
        renderer = it
        settings.renderer = it
    }

    ChoiceRow("Graphics Driver", GRAPHICS_DRIVERS, driver) {
        driver = it
        settings.graphicsDriver = it
    }
    ChoiceRow("Resolution", RESOLUTIONS, resolution) {
        resolution = it
        settings.resolution = it
    }
    ChoiceRow("Graphics Preset", GRAPHICS_PRESETS, preset) {
        preset = it
        settings.graphicsPreset = it
    }
    ChoiceRow("Overlay Performance", OVERLAYS, overlay) {
        overlay = it
        settings.overlay = it
    }
    ToggleRow("V-Sync", vsync) {
        vsync = it
        settings.vsync = it
    }
}

@Composable
fun AudioTab(settings: AppSettings) {
    var master by remember { mutableStateOf(settings.masterVolume) }
    var music by remember { mutableStateOf(settings.musicVolume) }
    var effects by remember { mutableStateOf(settings.effectsVolume) }
    var boost by remember { mutableStateOf(settings.boostAudio) }

    PercentSliderRow("Master Sound", master, 0f..1f) {
        master = it
        settings.masterVolume = it
    }
    PercentSliderRow("Music", music, 0f..1f) {
        music = it
        settings.musicVolume = it
    }
    PercentSliderRow("Effect", effects, 0f..1f) {
        effects = it
        settings.effectsVolume = it
    }
    ToggleRow("Boost audio to 200%", boost) {
        boost = it
        settings.boostAudio = it
    }
}

@Composable
fun ControlTab(settings: AppSettings, onCustomize: () -> Unit) {
    var touch by remember { mutableStateOf(settings.touchscreen) }
    var sensitivity by remember { mutableStateOf(settings.sensitivity) }
    var sprint by remember { mutableStateOf(settings.toggleSprint) }
    var holdAttack by remember { mutableStateOf(settings.toggleHoldAttack) }

    ToggleRow("Touchscreen", touch) {
        touch = it
        settings.touchscreen = it
    }
    OptionBlock("Customize Touchscreen") {
        VhsButton("Open layout editor", onClick = onCustomize)
    }
    PercentSliderRow("Sensitivity", sensitivity, 0f..2f) {
        sensitivity = it
        settings.sensitivity = it
    }
    ToggleRow("Toggle Sprint", sprint) {
        sprint = it
        settings.toggleSprint = it
    }
    ToggleRow("Toggle Hold Attack", holdAttack) {
        holdAttack = it
        settings.toggleHoldAttack = it
    }
}

/** One option: a red marker, a monospace label, and the controls underneath. */
@Composable
fun OptionBlock(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x66000000), RoundedCornerShape(2.dp))
            .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "▌" + label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color(0xFFFF6B6B)
        )
        content()
    }
}

@Composable
fun ChoiceRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    OptionBlock(label) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                VhsButton(option, selected = option == selected) { onSelect(option) }
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ChoiceRow(label, listOf("Off", "On"), if (value) "On" else "Off") { onChange(it == "On") }
}

@Composable
fun PercentSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    OptionBlock("$label  ${(value * 100).roundToInt()}%") {
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF6B6B),
                activeTrackColor = BRAND_RED,
                inactiveTrackColor = Color(0x55FFFFFF)
            )
        )
    }
}

// ----------------------------------------------------------------------------- demo

@Composable
fun DemoScreen(settings: AppSettings, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val activity = context.findActivity()
    var link by remember { mutableStateOf(settings.demoLink) }
    var status by remember { mutableStateOf<String?>(null) }
    val busy = remember { Busy() }

    VhsFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BackLink("< Back to menu", onBack)
            ScreenTitle("Borrow Demo")
            Text(
                "Downloads the demo straight into this app, no browser needed, and sets it up as the game. " +
                    "Paste a direct https link to a demo .zip you are allowed to download. The link is remembered.",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            OutlinedTextField(
                value = link,
                onValueChange = {
                    link = it
                    settings.demoLink = it
                },
                label = { Text("Direct download link (https://...)") },
                singleLine = true,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
            )
            VhsButton("Download demo", selected = true) {
                val trimmed = link.trim()
                if (!trimmed.startsWith("https://")) {
                    status = "Paste a direct https:// link to the demo .zip first."
                } else {
                    status = null
                    busy.text = "Downloading..."
                    Thread {
                        val file = downloadInto(trimmed, settings.downloadsDir) { total ->
                            busy.text = "Downloading... ${total / (1024 * 1024)} MB"
                        }
                        activity.runOnUiThread {
                            if (file == null) {
                                busy.clear()
                                status = "Download failed. Check the link and your connection."
                            } else if (!file.name.endsWith(".zip", ignoreCase = true)) {
                                busy.clear()
                                file.delete()
                                status = "That link is not a .zip file."
                            } else {
                                Bridge.installZip(
                                    activity, settings, busy, file.nameWithoutExtension,
                                    { file.inputStream() }
                                ) { error ->
                                    file.delete()
                                    status = error ?: "Demo ready. Go back and press Start Snuff."
                                }
                            }
                        }
                    }.start()
                }
            }
            status?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
        }
    }

    BusyDialog(busy)
}

// ----------------------------------------------------------------------------- setup

/** Shows the installed game. Reached from "Game setup" on the main menu. */
@Composable
fun SetupScreen(settings: AppSettings, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var gameName by remember { mutableStateOf(if (settings.hasGame()) settings.gameName ?: "Installed" else null) }
    val openImport = rememberGameImporter(settings) {
        gameName = if (settings.hasGame()) settings.gameName ?: "Installed" else null
    }

    VhsFrame {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BackLink("< Back to menu", onBack)
            ScreenTitle("Game setup")

            OptionBlock("Game") {
                Text(gameName ?: "None imported", fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VhsButton(if (gameName == null) "Import" else "Replace", onClick = openImport)
                    if (gameName != null) {
                        VhsButton("Remove") {
                            Bridge.removeGame(context, settings)
                            gameName = null
                        }
                    }
                }
            }
            Text(
                "The game is copied into this app, so it uses extra storage. You can delete the original afterwards.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = SOFT_GREY
            )
        }
    }
}

// ----------------------------------------------------------------------------- busy popup

@Composable
fun BusyDialog(busy: Busy) {
    val text = busy.text
    if (text != null) {
        val percent = busy.percent
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Please wait") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text)
                    if (percent != null) {
                        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("$percent%", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { }
        )
    }
}

fun Context.findActivity(): Activity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    error("No activity")
}

fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return c.getString(index)
        }
    }
    return uri.lastPathSegment ?: "imported_file"
}

// --------------------------------------------------------------------------- download

/** Downloads a file from an https link into [dir]. Returns the file, or null if it failed. */
fun downloadInto(urlText: String, dir: File, onProgress: (Long) -> Unit): File? {
    var connection: HttpURLConnection? = null
    var dest: File? = null
    try {
        val url = URL(urlText)
        if (url.protocol != "https") return null

        val conn = url.openConnection() as HttpURLConnection
        connection = conn
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) return null

        val raw = Uri.decode(url.path.substringAfterLast('/')).ifBlank { "demo_download" }
        val target = File(dir, raw.replace(Regex("[^A-Za-z0-9._ -]"), "_"))
        dest = target

        conn.inputStream.use { source ->
            target.outputStream().use { out ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    total += read
                    onProgress(total)
                }
            }
        }
        return target
    } catch (e: Exception) {
        dest?.delete()
        return null
    } finally {
        connection?.disconnect()
    }
}


/** Import / remove an Adreno driver .zip. Only Snapdragon (Adreno) phones can use it. */
@Composable
fun DriverImportBlock(settings: AppSettings) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var name by remember { mutableStateOf(settings.driverName) }
    val busy = remember { Busy() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val picked = displayName(context, uri)
            if (!picked.endsWith(".zip", ignoreCase = true)) {
                Toast.makeText(context, "That is not a .zip file.", Toast.LENGTH_LONG).show()
            } else {
                Bridge.installDriver(
                    activity, settings, busy, picked.removeSuffix(".zip"),
                    { context.contentResolver.openInputStream(uri) }
                ) { error ->
                    if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    name = settings.driverName
                }
            }
        }
    }

    OptionBlock("Import Driver (Snapdragon only)") {
        Text(
            name ?: "None imported",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = SOFT_GREY
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VhsButton(if (name == null) "Import driver .zip" else "Replace driver") { picker.launch(arrayOf("*/*")) }
            if (name != null) {
                VhsButton("Remove") {
                    Bridge.removeDriver(context, settings)
                    name = null
                }
            }
        }
    }
    BusyDialog(busy)
}

/** True on a normal ARM phone. False on PC emulators, which are x86 and only fake ARM. */
fun runsOnArmPhone(): Boolean = android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
