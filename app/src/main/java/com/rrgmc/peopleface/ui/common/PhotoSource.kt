package com.rrgmc.peopleface.ui.common

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.rrgmc.peopleface.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Where picked and captured pictures are kept while cropping; they are deleted afterwards.
 * Working on a private copy avoids depending on the gallery's temporary read permission.
 */
fun cameraDir(context: Context) = File(context.cacheDir, "camera").apply { mkdirs() }

fun pickedFile(context: Context, name: String) = File(cameraDir(context), name)

class PhotoSource(val pickFromGallery: () -> Unit, val takePhoto: () -> Unit)

/** Gallery / camera launchers. [onPicked] receives the name of a temporary file inside [cameraDir]. */
@Composable
fun rememberPhotoSource(onPicked: (fileName: String) -> Unit): PhotoSource {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    val openError = stringResource(R.string.image_load_error)

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                onPicked(withContext(Dispatchers.IO) { copyToCache(context, uri) }.name)
            } catch (e: Exception) {
                Log.e("PeopleFace", "Cannot copy picked image $uri", e)
                Toast.makeText(context, openError, Toast.LENGTH_LONG).show()
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera ?: return@rememberLauncherForActivityResult
        pendingCamera = null
        val file = File(path)
        if (ok && file.length() > 0) onPicked(file.name) else file.delete()
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

private fun copyToCache(context: Context, uri: Uri): File {
    val file = File(cameraDir(context), "picked_${System.currentTimeMillis()}")
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open $uri")
    } catch (e: Exception) {
        file.delete()
        throw e
    }
    return file
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
