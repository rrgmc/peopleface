package com.rrgmc.peopleface.ui.person

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.ui.common.AsyncBlobImage
import com.rrgmc.peopleface.ui.common.ChooseTagDialog
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import com.rrgmc.peopleface.ui.common.PersonDialog
import com.rrgmc.peopleface.ui.common.PhotoSourceMenu
import com.rrgmc.peopleface.ui.common.rememberPhotoSource
import com.rrgmc.peopleface.ui.common.TagChip
import com.rrgmc.peopleface.ui.common.roleText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    personId: Long,
    onBack: () -> Unit,
    onCrop: (fileNames: List<String>) -> Unit,
    onOpenFamily: (familyId: Long) -> Unit,
    onManageTags: (groupId: Long) -> Unit,
) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val row by repo.observePerson(personId).collectAsStateWithLifecycle(initialValue = null)
    val photos by repo.observePhotoThumbs(personId).collectAsStateWithLifecycle(initialValue = emptyList())

    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var choosingTag by rememberSaveable { mutableStateOf(false) }
    var viewingPhoto by rememberSaveable { mutableStateOf<Long?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    val photoSource = rememberPhotoSource(onCrop)

    val r = row
    val familyId = r?.person?.familyId ?: 0L
    val family by remember(familyId) { repo.observeFamily(familyId) }.collectAsStateWithLifecycle(initialValue = null)
    val groupId = r?.groupId ?: 0L
    val tags by remember(groupId) { repo.observeTags(groupId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(r?.person?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { deleting = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                },
            )
        },
    ) { padding ->
        if (r == null) return@Scaffold
        val p = r.person
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncBlobImage(
                        key = p.thumbnailPhotoId ?: 0L,
                        modifier = Modifier.size(200.dp).clip(CircleShape),
                        contentDescription = p.name,
                        role = p.role,
                    ) { p.thumbnailPhotoId?.let { repo.photoImage(it) } }
                    Text(p.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        listOf(roleText(p), r.familyName, r.groupName).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Also the way to reach one-person families, which have no card of their own.
                        AssistChip(
                            onClick = { onOpenFamily(p.familyId) },
                            label = { Text(stringResource(R.string.open_family)) },
                            leadingIcon = { Icon(Icons.Default.FamilyRestroom, null) },
                        )
                        // The tag of the person's family.
                        AssistChip(
                            onClick = { choosingTag = true },
                            label = {
                                if (r.tagName != null && r.tagColor != null) TagChip(r.tagName, r.tagColor)
                                else Text(stringResource(R.string.tag))
                            },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                        )
                    }
                    if (p.notes.isNotBlank()) {
                        Text(
                            p.notes,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.photos_count, photos.size),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        PhotoSourceMenu(photoSource, photoMenu, { photoMenu = false }) {
                            FilledTonalButton(onClick = { photoMenu = true }) {
                                Icon(Icons.Default.AddAPhoto, null, Modifier.padding(end = 8.dp))
                                Text(stringResource(R.string.add_photo))
                            }
                        }
                    }
                    if (photos.isEmpty()) {
                        Text(
                            stringResource(R.string.photos_empty),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
            items(photos, key = { it.id }) { photo ->
                val isThumb = photo.id == p.thumbnailPhotoId
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isThumb) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { viewingPhoto = photo.id },
                ) {
                    AsyncBlobImage(photo.id, Modifier.fillMaxSize()) { repo.photoImage(photo.id) }
                    if (isThumb) {
                        Icon(
                            Icons.Default.Star, stringResource(R.string.thumbnail),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        )
                    }
                }
            }
        }
    }

    viewingPhoto?.let { photoId ->
        AlertDialog(
            onDismissRequest = { viewingPhoto = null },
            text = {
                AsyncBlobImage(photoId, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                    repo.photoImage(photoId)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewingPhoto = null
                    scope.launch { repo.setThumbnail(personId, photoId) }
                }) { Text(stringResource(R.string.use_as_thumbnail)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewingPhoto = null
                    scope.launch { repo.deletePhoto(photoId) }
                }) { Text(stringResource(R.string.delete)) }
            },
        )
    }
    if (editing && r != null) {
        val p = r.person
        PersonDialog(
            title = stringResource(R.string.edit_person),
            initialName = p.name,
            initialRole = p.role,
            initialNotes = p.notes,
            onSave = { name, role, notes ->
                scope.launch { repo.updatePerson(p.copy(name = name, role = role, notes = notes)) }
            },
            onDismiss = { editing = false },
        )
    }
    val f = family
    if (choosingTag && f != null) {
        ChooseTagDialog(
            tags = tags,
            selectedId = f.tagId,
            onSelect = { tagId -> scope.launch { repo.setFamilyTag(f.id, tagId) } },
            onManage = { onManageTags(f.groupId) },
            onDismiss = { choosingTag = false },
        )
    }
    if (deleting && r != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_person_title),
            text = stringResource(R.string.delete_person_text, r.person.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { scope.launch { repo.deletePerson(personId); onBack() } },
            onDismiss = { deleting = false },
        )
    }
}
