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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.data.db.PersonEntity
import com.rrgmc.peopleface.data.db.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun ByteArray.toImageBitmap(): ImageBitmap? = BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()

/**
 * The person figure, full size for adults and smaller for children, standing on the same baseline
 * (so a child looks shorter). Used as the picture of people without a photo and on the Adult/Child toggle.
 */
@Composable
fun PersonFigure(role: Role?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val child = role == Role.CHILD
    Box(modifier) {
        Icon(
            Icons.Default.Person, null,
            Modifier
                .fillMaxSize(if (child) 0.7f else 1f)
                .align(if (child) Alignment.BottomCenter else Alignment.Center),
            tint = tint,
        )
    }
}

/** Shows JPEG bytes (decoded synchronously; meant for small thumbnails). */
@Composable
fun BlobImage(
    bytes: ByteArray?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    role: Role? = null,
) {
    val bitmap = remember(bytes) { bytes?.toImageBitmap() }
    ImageOrPlaceholder(bitmap, modifier, contentDescription, role)
}

/** Loads a full-size image off the main thread and shows it. */
@Composable
fun AsyncBlobImage(
    key: Any,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    role: Role? = null,
    load: suspend () -> ByteArray?,
) {
    val bitmap by produceState<ImageBitmap?>(null, key) {
        value = withContext(Dispatchers.IO) { load()?.toImageBitmap() }
    }
    ImageOrPlaceholder(bitmap, modifier, contentDescription, role)
}

/** The image, or (while loading / without a photo) the person figure for [role]. */
@Composable
private fun ImageOrPlaceholder(bitmap: ImageBitmap?, modifier: Modifier, contentDescription: String?, role: Role?) {
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            // The adult figure fills 60% of the box; a child's is smaller, on the same baseline.
            PersonFigure(role, Modifier.fillMaxSize(0.6f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Round thumbnail of a person; without a photo it shows the adult or child figure for [role]. */
@Composable
fun Avatar(thumb: ByteArray?, role: Role?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    BlobImage(thumb, modifier.size(size).clip(CircleShape), role = role)
}

/** The group's icon picture, or the generic groups symbol when it has none. */
@Composable
fun GroupIcon(icon: ByteArray?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val bitmap = remember(icon) { icon?.toImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, null, modifier.size(size).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.size(size), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null) }
    }
}

@Composable
fun roleText(role: Role): String = when (role) {
    Role.ADULT -> stringResource(R.string.role_adult)
    Role.CHILD -> stringResource(R.string.role_child)
}

@Composable
fun roleText(person: PersonEntity) = roleText(person.role)

/** Placeholder names ("Pai", "Mãe") are shown in italic and a softer colour, so they don't read as real names. */
@Composable
fun nameStyle(person: PersonEntity, style: TextStyle = LocalTextStyle.current): TextStyle =
    if (person.isPlaceholder) {
        style.copy(fontStyle = FontStyle.Italic, color = LocalContentColor.current.copy(alpha = 0.7f))
    } else style

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
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
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
