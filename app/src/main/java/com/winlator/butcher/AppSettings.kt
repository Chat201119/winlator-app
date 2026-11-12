package com.winlator.butcher

import android.content.Context
import com.winlator.BuildConfig
import java.io.File

val RESOLUTIONS = listOf("Native", "1080p", "720p", "480p", "360p", "240p")
val GRAPHICS_PRESETS = listOf("Battery Performance", "Low", "Medium", "High", "Quality")
val OVERLAYS = listOf("Off", "FPS", "FULL")
val GRAPHICS_DRIVERS = listOf("Auto", "Turnip", "Vortek")
val RENDERERS = listOf("Vulkan", "OpenGL")

/** Which build this is: "Snapdragon" or "Mali". Set per build version in Gradle. */
val GPU_FAMILY: String = BuildConfig.GPU_FAMILY

// Starting values for each version. You can change them in Settings.
val DEFAULT_RESOLUTION = if (GPU_FAMILY == "Mali") "480p" else "720p"
val DEFAULT_PRESET = if (GPU_FAMILY == "Mali") "Battery Performance" else "Low"

/** Everything the menu remembers: which game is installed and all the Settings. */
class AppSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("butcher_settings", Context.MODE_PRIVATE)

    /** Where demo downloads are put before they are unpacked. */
    val downloadsDir: File
        get() = File(context.cacheDir, "downloads").apply { mkdirs() }

    // ---- game

    /** Winlator container that holds the game (0 = none yet). */
    var containerId: Int
        get() = prefs.getInt("containerId", 0)
        set(value) { prefs.edit().putInt("containerId", value).apply() }

    /** Full path of the game's .exe inside the container. */
    var gameExe: String?
        get() = prefs.getString("gameExe", null)
        set(value) { prefs.edit().putString("gameExe", value).apply() }

    var gameName: String?
        get() = prefs.getString("gameName", null)
        set(value) { prefs.edit().putString("gameName", value).apply() }

    /** Name of the imported Adreno driver (folder in Winlator's installed components), or null. */
    var driverName: String?
        get() = prefs.getString("driverName", null)
        set(value) { prefs.edit().putString("driverName", value).apply() }

    var demoLink: String
        get() = prefs.getString("demoLink", "") ?: ""
        set(value) { prefs.edit().putString("demoLink", value).apply() }

    fun hasGame(): Boolean = gameExe?.let { File(it).exists() } == true

    fun clearGame() {
        gameExe = null
        gameName = null
    }

    // ---- display

    var resolution: String
        get() {
            val v = prefs.getString("resolution", DEFAULT_RESOLUTION) ?: DEFAULT_RESOLUTION
            return if (v in RESOLUTIONS) v else DEFAULT_RESOLUTION
        }
        set(value) { prefs.edit().putString("resolution", value).apply() }

    var graphicsPreset: String
        get() {
            val v = prefs.getString("graphicsPreset", DEFAULT_PRESET) ?: DEFAULT_PRESET
            return if (v in GRAPHICS_PRESETS) v else DEFAULT_PRESET
        }
        set(value) { prefs.edit().putString("graphicsPreset", value).apply() }

    var graphicsDriver: String
        get() {
            val v = prefs.getString("graphicsDriver", "Auto") ?: "Auto"
            return if (v in GRAPHICS_DRIVERS) v else "Auto"
        }
        set(value) { prefs.edit().putString("graphicsDriver", value).apply() }

    /** Vulkan = DXVK (fast). OpenGL = WineD3D over the phone's OpenGL (slower, avoids Vulkan). */
    var renderer: String
        get() {
            val v = prefs.getString("renderer", "Vulkan") ?: "Vulkan"
            return if (v in RENDERERS) v else "Vulkan"
        }
        set(value) { prefs.edit().putString("renderer", value).apply() }

    var overlay: String
        get() {
            val v = prefs.getString("overlay", "Off") ?: "Off"
            return if (v in OVERLAYS) v else "Off"
        }
        set(value) { prefs.edit().putString("overlay", value).apply() }

    var vsync: Boolean
        get() = prefs.getBoolean("vsync", true)
        set(value) { prefs.edit().putBoolean("vsync", value).apply() }

    // ---- audio

    var masterVolume: Float
        get() = prefs.getFloat("masterVolume", 1f)
        set(value) { prefs.edit().putFloat("masterVolume", value).apply() }

    var musicVolume: Float
        get() = prefs.getFloat("musicVolume", 0.8f)
        set(value) { prefs.edit().putFloat("musicVolume", value).apply() }

    var effectsVolume: Float
        get() = prefs.getFloat("effectsVolume", 1f)
        set(value) { prefs.edit().putFloat("effectsVolume", value).apply() }

    var boostAudio: Boolean
        get() = prefs.getBoolean("boostAudio", false)
        set(value) { prefs.edit().putBoolean("boostAudio", value).apply() }

    // ---- control

    var touchscreen: Boolean
        get() = prefs.getBoolean("touchscreen", true)
        set(value) { prefs.edit().putBoolean("touchscreen", value).apply() }

    /** 0f .. 2f, shown as 0% .. 200%. */
    var sensitivity: Float
        get() = prefs.getFloat("sensitivity", 1f)
        set(value) { prefs.edit().putFloat("sensitivity", value).apply() }

    var toggleCrouch: Boolean
        get() = prefs.getBoolean("toggleCrouch", false)
        set(value) { prefs.edit().putBoolean("toggleCrouch", value).apply() }

    var toggleSprint: Boolean
        get() = prefs.getBoolean("toggleSprint", false)
        set(value) { prefs.edit().putBoolean("toggleSprint", value).apply() }

    var toggleHoldAttack: Boolean
        get() = prefs.getBoolean("toggleHoldAttack", false)
        set(value) { prefs.edit().putBoolean("toggleHoldAttack", value).apply() }

    // ---- touchscreen layout (positions are fractions of the screen, scale is a size multiplier)

    fun touchX(item: TouchItem): Float = prefs.getFloat("tx_${item.key}", item.defX)
    fun touchY(item: TouchItem): Float = prefs.getFloat("ty_${item.key}", item.defY)
    fun touchScale(item: TouchItem): Float = prefs.getFloat("ts_${item.key}", item.defScale)

    fun saveTouchLayout(xs: Map<String, Float>, ys: Map<String, Float>, scales: Map<String, Float>) {
        val e = prefs.edit()
        TOUCH_ITEMS.forEach { item ->
            e.putFloat("tx_${item.key}", xs[item.key] ?: item.defX)
            e.putFloat("ty_${item.key}", ys[item.key] ?: item.defY)
            e.putFloat("ts_${item.key}", scales[item.key] ?: item.defScale)
        }
        e.apply()
    }
}
