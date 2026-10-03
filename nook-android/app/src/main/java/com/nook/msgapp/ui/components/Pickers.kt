package com.nook.msgapp.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.nook.msgapp.lock.LockStore
import java.io.File

/** Launchers for the system pickers. Opening one never re-locks the app on return. */
class PickerLauncher(private val launch: () -> Unit) {
    operator fun invoke() {
        LockStore.skipNextBackgroundLock()
        launch()
    }
}

/** Photo picker (no storage permission needed). */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): PickerLauncher {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPicked(uri) }
    return remember(launcher) {
        PickerLauncher { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

/** Any file (documents, PDFs, zips...). */
@Composable
fun rememberFilePicker(onPicked: (Uri) -> Unit): PickerLauncher {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onPicked(uri) }
    return remember(launcher) { PickerLauncher { launcher.launch(arrayOf("*/*")) } }
}

/** Takes a photo with the camera app into a private cache file (a new file each time). */
@Composable
fun rememberCameraCapture(onCaptured: (Uri) -> Unit): PickerLauncher {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<Uri>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pending[0]
        if (ok && uri != null) onCaptured(uri)
    }
    return remember(launcher) {
        PickerLauncher {
            val dir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "nook_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pending[0] = uri
            launcher.launch(uri)
        }
    }
}
