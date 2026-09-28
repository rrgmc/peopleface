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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.image.RecentPhotos
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

/** Resolves a picture name given to the crop screen: a temporary copy, or one of the [RecentPhotos]. */
fun pickedFile(context: Context, name: String) =
    if (RecentPhotos.isRecent(name)) RecentPhotos.file(context, name) else File(cameraDir(context), name)

class PhotoSource(
    val pickFromGallery: () -> Unit,
    val pickFromFiles: () -> Unit,
    val takePhoto: () -> Unit,
    val pickRecent: () -> Unit,
)

/** How many pictures can be picked from the gallery at once. */
const val MAX_PICKED_PHOTOS = 10

/**
 * Gallery / camera / recent-photo pickers. [onPicked] receives names for [pickedFile]: temporary files
 * inside [cameraDir], or a recent photo. The gallery can return several (up to [MAX_PICKED_PHOTOS]).
 */
@Composable
fun rememberPhotoSource(onPicked: (fileNames: List<String>) -> Unit): PhotoSource {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    var showRecent by rememberSaveable { mutableStateOf(false) }
    if (showRecent) {
        RecentPhotosDialog(
            onPick = { showRecent = false; onPicked(listOf(it.name)) },
            onDismiss = { showRecent = false },
        )
    }
    val openError = stringResource(R.string.image_load_error)

    val handleUris: (List<Uri>) -> Unit = handle@{ uris ->
        if (uris.isEmpty()) return@handle
        scope.launch {
            val names = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, uri ->
                    try {
                        copyToCache(context, uri, i).name
                    } catch (e: Exception) {
                        Log.e("PeopleFace", "Cannot copy picked image $uri", e)
                        null
                    }
                }
            }
            if (names.size < uris.size) Toast.makeText(context, openError, Toast.LENGTH_LONG).show()
            if (names.isNotEmpty()) onPicked(names)
        }
    }
    val gallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_PHOTOS)
    ) { handleUris(it) }
    // The system document picker also lists cloud storage apps such as Google Drive and Dropbox.
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        handleUris(it.take(MAX_PICKED_PHOTOS))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCamera ?: return@rememberLauncherForActivityResult
        pendingCamera = null
        val file = File(path)
        if (ok && file.length() > 0) onPicked(listOf(file.name)) else file.delete()
    }
    return PhotoSource(
        pickFromGallery = {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        pickFromFiles = { files.launch(arrayOf("image/*")) },
        takePhoto = {
            val file = File(cameraDir(context), "photo_${System.currentTimeMillis()}.jpg")
            pendingCamera = file.path
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        },
        pickRecent = { showRecent = true },
    )
}

/** Copies [uri] into [cameraDir], so it can be read after the caller's temporary permission ends. */
fun copyToCache(context: Context, uri: Uri, index: Int): File {
    val file = File(cameraDir(context), "picked_${System.currentTimeMillis()}_$index")
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

/** Wraps [anchor] with a Gallery / Files / Camera drop-down menu. */
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
                text = { Text(stringResource(R.string.from_files)) },
                leadingIcon = { Icon(Icons.Default.Folder, null) },
                onClick = { onDismiss(); source.pickFromFiles() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.take_photo)) },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, null) },
                onClick = { onDismiss(); source.takePhoto() },
            )
            val context = LocalContext.current
            val hasRecent = remember(expanded) { expanded && RecentPhotos.list(context).isNotEmpty() }
            if (hasRecent) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recent_photos)) },
                    leadingIcon = { Icon(Icons.Default.History, null) },
                    onClick = { onDismiss(); source.pickRecent() },
                )
            }
        }
    }
}
