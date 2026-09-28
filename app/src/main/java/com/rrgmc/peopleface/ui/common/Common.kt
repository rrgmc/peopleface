package com.rrgmc.peopleface.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.data.db.PersonEntity
import com.rrgmc.peopleface.data.db.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun ByteArray.toImageBitmap(): ImageBitmap? = BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()

/** Shows JPEG bytes (decoded synchronously; meant for small thumbnails). */
@Composable
fun BlobImage(bytes: ByteArray?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap = remember(bytes) { bytes?.toImageBitmap() }
    ImageOrPlaceholder(bitmap, modifier, contentDescription)
}

/** Loads a full-size image off the main thread and shows it. */
@Composable
fun AsyncBlobImage(
    key: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    load: suspend () -> ByteArray?,
) {
    val bitmap by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.IO) { load()?.toImageBitmap() }
    }
    ImageOrPlaceholder(bitmap, modifier, contentDescription)
}

@Composable
private fun ImageOrPlaceholder(bitmap: ImageBitmap?, modifier: Modifier, contentDescription: String?) {
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Person, contentDescription,
                Modifier.fillMaxSize(0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun Avatar(thumb: ByteArray?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    BlobImage(thumb, modifier.size(size).clip(CircleShape))
}

@Composable
fun roleText(role: Role, label: String = ""): String = when (role) {
    Role.FATHER -> stringResource(R.string.role_father)
    Role.MOTHER -> stringResource(R.string.role_mother)
    Role.KID -> stringResource(R.string.role_kid)
    Role.OTHER -> label.ifBlank { stringResource(R.string.role_other) }
}

@Composable
fun roleText(person: PersonEntity) = roleText(person.role, person.roleLabel)

/** A family without its own name is shown by the names of its members. */
@Composable
fun familyTitle(name: String, memberNames: List<String>): String = when {
    name.isNotBlank() -> name
    memberNames.isNotEmpty() -> memberNames.joinToString(", ")
    else -> stringResource(R.string.family_unnamed)
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Dialog with a name field and a notes field. */
@Composable
fun NameNotesDialog(
    title: String,
    nameLabel: String,
    initialName: String = "",
    initialNotes: String = "",
    nameRequired: Boolean = true,
    onSave: (name: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(nameLabel) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !nameRequired || name.isNotBlank(),
                onClick = { onDismiss(); onSave(name, notes) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
