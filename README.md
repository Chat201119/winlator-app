# Butcher Creeks with Winlator built in

This is a set of files you put on top of a copy ("fork") of Winlator. The result is one app:

- Opens on the Butcher Creeks VHS menu. Winlator's own screens are never shown.
- **Start Snuff** (first time: Import) takes a .zip or a Folder, puts the game into a hidden Winlator
  container, and from then on goes straight into the game.
- All settings live in **Settings**. On-screen buttons are drawn in the game from **Customize Touchscreen**.

## How to build it (all on the GitHub website)
1. Open https://github.com/brunodev85/winlator-app and press **Fork** (top right), then **Create fork**.
2. In YOUR fork press **Add file > Upload files**. Drag in these from this zip (after extracting it):
   the `app` folder, `build.gradle` and `gradle.properties`. Press **Commit changes**.
   (They replace Winlator's files with the same names. That is intended.)
3. Press **Add file > Create new file**. Type the name exactly: `.github/workflows/build-apk.yml`
   (typing the `/` makes the folders). Paste in everything from `build-apk.yml.txt`. Commit.
4. Open the **Actions** tab. The build starts by itself. The first one takes 20-40 minutes.
5. When it has a green tick, open it and download **ButcherCreek-APKs** at the bottom. Inside are
   `app-snapdragon-debug.apk` and `app-mali-debug.apk`. Install the Snapdragon one on a Redmi 15 5G.
6. **Uninstall the normal Winlator first** (and any older Butcher Creeks app). They share one package
   name, so they cannot be installed together. For the same reason, the Snapdragon and Mali versions
   cannot be installed together either.

## Touch buttons
MOVE (W A S D), SPRINT (Shift), ATTACK (left mouse), AIM (right mouse), USE (E), RELOAD (R),
JUMP (Space), CROUCH (Left Ctrl), PAUSE (Esc). Dragging a finger on empty screen turns the camera.
If the game uses other keys, tell me and I will change them.

## What Settings really do
Applied when you press Start Snuff: Resolution, Graphics Preset (CPU translation speed + frame cap:
Battery Performance/Low 30, Medium 45, High 60, Quality uncapped), Overlay (Off/FPS/FULL),
Master Sound and Boost audio, Touchscreen, Sensitivity, Toggle Crouch/Sprint/Hold Attack, button layout.
Saved only (Winlator has no switch for them): V-Sync, Music, Effect.
Button sizes in the game are close to, not exactly, the sizes in the editor.

## Not tested
Nothing here has been compiled or run by me. Expect to send me build errors and fix them in rounds.
Whether Butcher's Creek runs on a Snapdragon 695 through Winlator is unknown until you try it.
Winlator is open source (LGPL 2.1). Keep this build for personal use; do not publish the APK.
