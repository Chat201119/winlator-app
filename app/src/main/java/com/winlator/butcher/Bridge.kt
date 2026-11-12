package com.winlator.butcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.winlator.XServerDisplayActivity
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.container.DXWrappers
import com.winlator.core.AppUtils
import com.winlator.core.Callback
import com.winlator.core.DefaultVersion
import com.winlator.core.FileUtils
import com.winlator.core.GeneralComponents
import com.winlator.core.KeyValueSet
import com.winlator.core.TarCompressorUtils
import com.winlator.core.WineInfo
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.FrameRating
import com.winlator.xenvironment.RootFS
import com.winlator.xenvironment.RootFSInstaller
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

/** What the "please wait" popup shows while something long is running. */
class Busy {
    var text by mutableStateOf<String?>(null)
    var percent by mutableStateOf<Int?>(null)

    fun clear() {
        text = null
        percent = null
    }
}

/**
 * The glue between the menu and Winlator. The player never sees Winlator's own screens:
 * this installs its system files, makes one hidden container for the game, puts the game
 * into it, applies the menu's Settings, and starts the game directly.
 */
object Bridge {
    /** Id of the on-screen button layout the menu writes for Winlator. */
    const val PROFILE_ID = 100

    private const val GAME_DIR = ".wine/drive_c/ButcherCreek"

    private val SKIP_EXE = Regex(
        "unins|crash|handler|redist|vcredist|dxsetup|dotnet|setup|installer|helper|launcher_old",
        RegexOption.IGNORE_CASE
    )

    // ------------------------------------------------------------------------ system files

    private fun rootFsReady(context: Context): Boolean {
        val fs = RootFS.find(context)
        return fs.isValid && fs.version >= RootFSInstaller.LATEST_VERSION
    }

    private fun clearOptDir(optDir: File) {
        optDir.listFiles()?.forEach { f ->
            if (f.name != "installed-wine") FileUtils.delete(f)
        }
    }

    private fun clearRootDir(rootDir: File) {
        if (rootDir.isDirectory) {
            rootDir.listFiles()?.forEach { f ->
                if (f.isDirectory && (f.name == "home" || f.name == "opt")) {
                    if (f.name == "opt") clearOptDir(f)
                } else {
                    FileUtils.delete(f)
                }
            }
        } else {
            rootDir.mkdirs()
        }
    }

    /** Same bookkeeping Winlator does after it installs its system files. */
    private fun resetContainerVersions(context: Context) {
        val manager = ContainerManager(context)
        for (container in manager.containers) {
            val rfsVersion = container.getExtra("rfsVersion")
            val wineVersion = container.wineVersion
            if (rfsVersion.isNotEmpty() && WineInfo.isMainWineVersion(wineVersion) &&
                rfsVersion.toShort() <= RootFSInstaller.UPDATE_WINEPREFIX_VERSION
            ) {
                container.putExtra("wineprefixNeedsUpdate", "t")
            }
            container.putExtra("rfsVersion", null)
            container.saveData()
        }
    }

    private fun installRootFs(activity: Activity, busy: Busy, onDone: (Boolean) -> Unit) {
        AppUtils.keepScreenOn(activity)
        busy.text = "Installing the game system (first time only). Please wait..."
        busy.percent = 0
        Thread {
            val fs = RootFS.find(activity)
            val rootDir = fs.rootDir

            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putString("box64_version", DefaultVersion.BOX64)
                .remove("current_box64_version")
                .remove("current_graphics_driver")
                .apply()

            clearRootDir(rootDir)
            val total = TarCompressorUtils.getContentLength(
                TarCompressorUtils.Type.ZSTD, activity, RootFSInstaller.FILENAME, rootDir
            )
            val done = AtomicLong()
            val ok = TarCompressorUtils.extract(
                TarCompressorUtils.Type.ZSTD, activity, RootFSInstaller.FILENAME, rootDir,
                TarCompressorUtils.OnExtractFileListener { file, size ->
                    if (size > 0 && total > 0) {
                        busy.percent = ((done.addAndGet(size) * 100) / total).toInt().coerceIn(0, 100)
                    }
                    file
                }
            )
            if (ok) {
                fs.createRFSVersionFile(RootFSInstaller.LATEST_VERSION.toInt())
                resetContainerVersions(activity)
            }
            activity.runOnUiThread {
                busy.clear()
                onDone(ok)
            }
        }.start()
    }

    // ---------------------------------------------------------------------------- container

    /** Makes sure the system files and the hidden game container exist, then calls [next]. */
    private fun prepare(activity: Activity, settings: AppSettings, busy: Busy, next: (Container?) -> Unit) {
        fun withContainer() {
            val manager = ContainerManager(activity)
            val existing = manager.getContainerById(settings.containerId)
            if (existing != null) {
                next(existing)
                return
            }
            busy.text = "Creating the game environment..."
            val data = JSONObject()
                .put("name", "Butcher Creek")
                .put("screenSize", screenSizeFor(activity, settings.resolution))
            manager.createContainerAsync(data, Callback<Container> { created ->
                busy.clear()
                if (created != null) settings.containerId = created.id
                next(created)
            })
        }

        if (rootFsReady(activity)) {
            withContainer()
        } else {
            installRootFs(activity, busy) { ok -> if (ok) withContainer() else next(null) }
        }
    }

    private fun screenSizeFor(context: Context, resolution: String): String = when (resolution) {
        "1080p" -> "1920x1080"
        "720p" -> "1280x720"
        "480p" -> "854x480"
        "360p" -> "640x360"
        "240p" -> "426x240"
        else -> {
            val dm = context.resources.displayMetrics
            "${max(dm.widthPixels, dm.heightPixels)}x${min(dm.widthPixels, dm.heightPixels)}"
        }
    }

    /** Copies the menu's Settings into the hidden container just before the game starts. */
    private fun applySettings(context: Context, container: Container, s: AppSettings) {
        container.setScreenSize(screenSizeFor(context, s.resolution))

        // Graphics Driver: Auto uses Turnip on Snapdragon (full Vulkan, what DirectX 11 games need)
        // and Vortek on Mali.
        val vulkan = when (s.graphicsDriver) {
            "Turnip" -> "turnip"
            "Vortek" -> "vortek"
            else -> if (GPU_FAMILY == "Snapdragon") "turnip" else "vortek"
        }
        // An imported Adreno driver is used through Vortek (unless Turnip was picked by hand).
        val customDriver = s.driverName?.takeIf {
            File(GeneralComponents.getComponentDir(GeneralComponents.Type.ADRENOTOOLS_DRIVER, context), it).isDirectory
        }
        val useCustom = customDriver != null && s.graphicsDriver != "Turnip"
        val openGl = s.renderer == "OpenGL"
        val vulkanFinal = if (useCustom) "vortek" else vulkan
        container.setGraphicsDriver(if (openGl) "$vulkanFinal,virgl" else "$vulkanFinal,gladio")
        container.setGraphicsDriverConfig(
            if (useCustom) KeyValueSet("").put("adrenotoolsDriver", customDriver).toString() else ""
        )
        container.setDXWrapper(if (openGl) DXWrappers.WINED3D else DXWrappers.DXVK)

        container.setHUDMode(
            when (s.overlay) {
                "FPS" -> FrameRating.Mode.SIMPLE.ordinal
                "FULL" -> FrameRating.Mode.FULL.ordinal
                else -> FrameRating.Mode.DISABLED.ordinal
            }.toByte()
        )

        // Graphics Preset = how fast the CPU translation runs and a frame-rate cap.
        val box64: String
        val fpsCap: Int
        when (s.graphicsPreset) {
            "Battery Performance" -> { box64 = "PERFORMANCE"; fpsCap = 30 }
            "Low" -> { box64 = "PERFORMANCE"; fpsCap = 30 }
            "Medium" -> { box64 = "INTERMEDIATE"; fpsCap = 45 }
            "High" -> { box64 = "INTERMEDIATE"; fpsCap = 60 }
            else -> { box64 = "INTERMEDIATE"; fpsCap = 0 }
        }
        container.setBox64Preset(box64)

        val dxConfig = container.getDXWrapperConfig()
        if (!openGl && !dxConfig.contains("|")) {
            container.setDXWrapperConfig(KeyValueSet(dxConfig).put("framerate", fpsCap).toString())
        }

        // Master Sound and Boost audio become Winlator's audio volume.
        val volume = (s.masterVolume * if (s.boostAudio) 2f else 1f).coerceIn(0f, 2f)
        container.setAudioDriverConfig(
            KeyValueSet(container.getAudioDriverConfig()).put("volume", volume).toString()
        )

        container.saveData()
    }

    // ---------------------------------------------------------------------- game install

    private fun gameDir(container: Container) = File(container.rootDir, GAME_DIR)

    /** Picks the file that starts the game. Skips installers and crash handlers. */
    private fun findExe(dir: File): File? {
        val all = dir.walkTopDown().filter { it.isFile && it.extension.equals("exe", true) }.toList()
        val good = all.filter { !SKIP_EXE.containsMatchIn(it.name) }
        val pool = good.ifEmpty { all }
        return pool.firstOrNull { it.name.contains("butcher", true) }
            ?: pool.sortedWith(compareBy({ it.relativeTo(dir).path.count { c -> c == '/' } }, { -it.length() }))
                .firstOrNull()
    }

    private fun finishInstall(
        activity: Activity, settings: AppSettings, busy: Busy, dir: File, name: String,
        onDone: (String?) -> Unit
    ) {
        val exe = findExe(dir)
        activity.runOnUiThread {
            busy.clear()
            if (exe == null) {
                onDone("No .exe found in that game. Pick the folder or .zip that has the game's .exe in it.")
            } else {
                settings.gameExe = exe.path
                settings.gameName = name
                onDone(null)
            }
        }
    }

    private fun unzipInto(input: InputStream, target: File, busy: Busy) {
        var total = 0L
        val base = target.canonicalPath + File.separator
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(target, entry.name.replace('\\', '/'))
                if (!out.canonicalPath.startsWith(base)) continue // ignore unsafe paths
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { o ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            o.write(buffer, 0, read)
                            total += read
                            busy.text = "Unpacking the game... ${total / (1024 * 1024)} MB"
                        }
                    }
                }
            }
        }
    }

    /** Unpacks a game .zip into the hidden container. [onDone] gets null, or an error message. */
    fun installZip(
        activity: Activity, settings: AppSettings, busy: Busy, name: String,
        open: () -> InputStream?, onDone: (String?) -> Unit
    ) {
        prepare(activity, settings, busy) { container ->
            if (container == null) {
                onDone("Could not set up the game system.")
                return@prepare
            }
            busy.text = "Unpacking the game..."
            Thread {
                val dir = gameDir(container)
                try {
                    FileUtils.delete(dir)
                    dir.mkdirs()
                    val input = open()
                    if (input == null) {
                        activity.runOnUiThread { busy.clear(); onDone("Could not read that file.") }
                        return@Thread
                    }
                    unzipInto(input, dir, busy)
                    finishInstall(activity, settings, busy, dir, name, onDone)
                } catch (e: Exception) {
                    activity.runOnUiThread { busy.clear(); onDone("Unpacking failed: ${e.message}") }
                }
            }.start()
        }
    }

    private fun copyTree(context: Context, src: DocumentFile, dst: File, busy: Busy, counter: AtomicLong) {
        src.listFiles().forEach { child ->
            val childName = (child.name ?: return@forEach).replace('/', '_')
            val out = File(dst, childName)
            if (child.isDirectory) {
                out.mkdirs()
                copyTree(context, child, out, busy, counter)
            } else {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    out.outputStream().use { o ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            o.write(buffer, 0, read)
                            busy.text = "Copying the game... ${counter.addAndGet(read.toLong()) / (1024 * 1024)} MB"
                        }
                    }
                }
            }
        }
    }

    /** Copies a game folder into the hidden container. [onDone] gets null, or an error message. */
    fun installFolder(
        activity: Activity, settings: AppSettings, busy: Busy, treeUri: Uri, name: String,
        onDone: (String?) -> Unit
    ) {
        prepare(activity, settings, busy) { container ->
            if (container == null) {
                onDone("Could not set up the game system.")
                return@prepare
            }
            busy.text = "Copying the game..."
            Thread {
                val dir = gameDir(container)
                try {
                    val root = DocumentFile.fromTreeUri(activity, treeUri)
                    if (root == null) {
                        activity.runOnUiThread { busy.clear(); onDone("Could not read that folder.") }
                        return@Thread
                    }
                    FileUtils.delete(dir)
                    dir.mkdirs()
                    copyTree(activity, root, dir, busy, AtomicLong())
                    finishInstall(activity, settings, busy, dir, name, onDone)
                } catch (e: Exception) {
                    activity.runOnUiThread { busy.clear(); onDone("Copying failed: ${e.message}") }
                }
            }.start()
        }
    }

    /** Imports an Adreno driver .zip (the kind with a .json file and a .so file inside). */
    fun installDriver(
        activity: Activity, settings: AppSettings, busy: Busy, name: String,
        open: () -> InputStream?, onDone: (String?) -> Unit
    ) {
        busy.text = "Importing the driver..."
        Thread {
            val tmp = File(activity.cacheDir, "driver_tmp")
            var error: String? = null
            try {
                FileUtils.delete(tmp)
                tmp.mkdirs()
                val input = open()
                if (input == null) {
                    error = "Could not read that file."
                } else {
                    unzipInto(input, tmp, busy)
                    val manifest = tmp.walkTopDown().firstOrNull { f ->
                        f.isFile && f.extension.equals("json", true) &&
                            try { JSONObject(f.readText()).has("libraryName") } catch (e: Exception) { false }
                    }
                    if (manifest == null) {
                        error = "That zip is not an Adreno driver (no file with a libraryName inside)."
                    } else {
                        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "driver" }
                        val dest = File(
                            GeneralComponents.getComponentDir(GeneralComponents.Type.ADRENOTOOLS_DRIVER, activity), safe
                        )
                        FileUtils.delete(dest)
                        manifest.parentFile!!.copyRecursively(dest, overwrite = true)
                        settings.driverName?.takeIf { it != safe }?.let {
                            FileUtils.delete(File(dest.parentFile, it))
                        }
                        settings.driverName = safe
                    }
                }
            } catch (e: Exception) {
                error = "Importing failed: ${e.message}"
            }
            FileUtils.delete(tmp)
            activity.runOnUiThread {
                busy.clear()
                onDone(error)
            }
        }.start()
    }

    fun removeDriver(context: Context, settings: AppSettings) {
        settings.driverName?.let {
            FileUtils.delete(
                File(GeneralComponents.getComponentDir(GeneralComponents.Type.ADRENOTOOLS_DRIVER, context), it)
            )
        }
        settings.driverName = null
    }

    fun removeGame(context: Context, settings: AppSettings) {
        ContainerManager(context).getContainerById(settings.containerId)?.let { FileUtils.delete(gameDir(it)) }
        settings.clearGame()
    }

    // ------------------------------------------------------------------------- touch buttons

    private fun element(
        type: String, shape: String, bindings: List<String>, scale: Float, x: Float, y: Float,
        toggle: Boolean, text: String
    ): JSONObject {
        val four = (bindings + List(4) { "NONE" }).take(4)
        return JSONObject()
            .put("type", type)
            .put("shape", shape)
            .put("bindings", JSONArray(four))
            .put("scale", scale.toDouble().coerceIn(0.4, 2.5))
            .put("x", x.toDouble())
            .put("y", y.toDouble())
            .put("toggleSwitch", toggle)
            .put("text", text)
            .put("iconId", 0)
    }

    /**
     * Turns the menu's touch layout (Customize Touchscreen) into a Winlator on-screen controls
     * profile. Moving a finger over empty screen turns the camera, like Winlator's FPS layout.
     */
    private fun writeControlsProfile(context: Context, s: AppSettings): Int {
        // Makes Winlator create its profile folder (with its templates) if it does not exist yet.
        InputControlsManager(context).getProfiles(true)

        fun item(key: String) = TOUCH_ITEMS.first { it.key == key }
        fun add(list: JSONArray, key: String, type: String, shape: String, b: List<String>, toggle: Boolean, text: String, k: Float) {
            val it = item(key)
            list.put(element(type, shape, b, s.touchScale(it) * k, s.touchX(it), s.touchY(it), toggle, text))
        }

        val elements = JSONArray()
        add(elements, "move", "STICK", "CIRCLE", listOf("KEY_W", "KEY_D", "KEY_S", "KEY_A"), false, "", 0.55f)
        add(elements, "sprint", "BUTTON", "CIRCLE", listOf("KEY_SHIFT_L"), s.toggleSprint, "SPRINT", 0.85f)
        add(elements, "attack", "BUTTON", "CIRCLE", listOf("MOUSE_LEFT_BUTTON"), s.toggleHoldAttack, "ATTACK", 0.75f)
        add(elements, "aim", "BUTTON", "CIRCLE", listOf("MOUSE_RIGHT_BUTTON"), false, "AIM", 0.85f)
        add(elements, "use", "BUTTON", "CIRCLE", listOf("KEY_E"), false, "USE", 0.85f)
        add(elements, "throw", "BUTTON", "CIRCLE", listOf("KEY_R"), false, "THROW", 0.85f)
        add(elements, "kick", "BUTTON", "CIRCLE", listOf("KEY_F"), false, "KICK", 0.85f)
        add(elements, "pause", "BUTTON", "ROUND_RECT", listOf("KEY_ESC"), false, "PAUSE", 0.85f)

        val profile = JSONObject()
            .put("id", PROFILE_ID)
            .put("name", "Butcher Creek")
            .put("cursorSpeed", s.sensitivity.toDouble().coerceAtLeast(0.1))
            .put("elements", elements)

        FileUtils.writeString(File(InputControlsManager.getProfilesDir(context), "controls-$PROFILE_ID.icp"), profile.toString())
        return PROFILE_ID
    }

    // ----------------------------------------------------------------------------- start

    /** Starts the game directly. Returns a message to show if it cannot start, or null on success. */
    fun start(activity: Activity, settings: AppSettings): String? {
        val exe = settings.gameExe
        if (exe == null || !File(exe).exists()) return "Game files are missing. Import the game again."
        if (!rootFsReady(activity)) return "Game system is missing. Import the game again."

        val container = ContainerManager(activity).getContainerById(settings.containerId)
            ?: return "Game environment is missing. Import the game again."

        applySettings(activity, container, settings)
        val profileId = if (settings.touchscreen) writeControlsProfile(activity, settings) else 0

        val intent = Intent(activity, XServerDisplayActivity::class.java)
            .putExtra("container_id", container.id)
            .putExtra("exec_path", exe)
            .putExtra("controls_profile_id", profileId)
        activity.startActivity(intent)
        return null
    }
}
