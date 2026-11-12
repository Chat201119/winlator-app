package com.winlator.butcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Some file managers label zip files differently and would grey out a real .zip if the picker
// filtered by type, so every file is shown and the name is checked after picking.
private val ZIP_TYPES = arrayOf("*/*")

/**
 * Sets up everything needed to import a game and returns a function that opens the
 * ".zip or Folder" choice. [onChanged] is called after a game was installed.
 *
 * Both choices are unpacked or copied into the app's hidden game environment.
 */
@Composable
fun rememberGameImporter(settings: AppSettings, onChanged: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context.findActivity()
    var showChoice by remember { mutableStateOf(false) }
    val busy = remember { Busy() }

    fun done(error: String?) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Game imported. Press Start Snuff.", Toast.LENGTH_LONG).show()
            onChanged()
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val picked = displayName(context, uri)
            if (!picked.endsWith(".zip", ignoreCase = true)) {
                Toast.makeText(context, "That is not a .zip file.", Toast.LENGTH_LONG).show()
            } else {
                Bridge.installZip(
                    activity, settings, busy, picked.removeSuffix(".zip"),
                    { context.contentResolver.openInputStream(uri) }, ::done
                )
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            keepAccess(context, uri)
            Bridge.installFolder(activity, settings, busy, uri, folderName(uri), ::done)
        }
    }

    if (showChoice) {
        ImportChoiceDialog(
            onZip = {
                showChoice = false
                zipPicker.launch(ZIP_TYPES)
            },
            onFolder = {
                showChoice = false
                folderPicker.launch(null)
            },
            onDismiss = { showChoice = false }
        )
    }
    BusyDialog(busy)

    return { showChoice = true }
}

@Composable
fun ImportChoiceDialog(onZip: () -> Unit, onFolder: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What are you importing?")
                VhsButton(".zip", selected = true, modifier = Modifier.fillMaxWidth(), onClick = onZip)
                VhsButton("Folder", selected = true, modifier = Modifier.fillMaxWidth(), onClick = onFolder)
            }
        },
        confirmButton = { },
        dismissButton = { VhsButton("Cancel", onClick = onDismiss) }
    )
}

/** Keep read access to the picked folder after the app restarts. */
fun keepAccess(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (e: Exception) {
        // Some providers do not allow persistable access; the folder is still remembered.
    }
}

/** A readable name for a picked folder, taken from its document id (for example "primary:Games/Butcher"). */
fun folderName(uri: Uri): String {
    val id = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (e: Exception) {
        uri.toString()
    }
    return id.substringAfterLast('/').substringAfterLast(':').ifBlank { "Selected folder" }
}
