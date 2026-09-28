package com.rrgmc.peopleface.ui.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.rrgmc.peopleface.R
import java.io.File

/** Where the camera writes its pictures; they are deleted once the face was cropped. */
fun cameraDir(context: Context) = File(context.cacheDir, "camera").apply { mkdirs() }

class PhotoSource(val pickFromGallery: () -> Unit, val takePhoto: () -> Unit)

/** Gallery / camera launchers. [onPicked] receives the image uri and whether it's a temporary camera file. */
@Composable
fun rememberPhotoSource(onPicked: (uri: Uri, temporary: Boolean) -> Unit): PhotoSource {
    val context = LocalContext.current
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri, false)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera ?: return@rememberLauncherForActivityResult
        pendingCamera = null
        val file = File(path)
        if (ok && file.length() > 0) {
            onPicked(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), true)
        } else {
            file.delete()
        }
    }
    return PhotoSource(
        pickFromGallery = {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        takePhoto = {
            val file = File(cameraDir(context), "photo_${System.currentTimeMillis()}.jpg")
            pendingCamera = file.path
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        },
    )
}

/** Wraps [anchor] with a Gallery / Camera drop-down menu. */
@Composable
fun PhotoSourceMenu(
    source: PhotoSource,
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchor: @Composable () -> Unit,
) {
    Box {
        anchor()
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.from_gallery)) },
                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                onClick = { onDismiss(); source.pickFromGallery() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.take_photo)) },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                onClick = { onDismiss(); source.takePhoto() },
            )
        }
    }
}
