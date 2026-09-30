package com.rrgmc.peopleface.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.db.Role
import com.rrgmc.peopleface.ui.common.Avatar
import com.rrgmc.peopleface.ui.common.nameStyle
import com.rrgmc.peopleface.ui.common.ChooseTagDialog
import com.rrgmc.peopleface.ui.common.TagChip
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import com.rrgmc.peopleface.ui.common.NameNotesDialog
import com.rrgmc.peopleface.ui.common.PhotoSourceMenu
import com.rrgmc.peopleface.ui.common.familyTitle
import com.rrgmc.peopleface.ui.common.rememberPhotoSource
import com.rrgmc.peopleface.ui.common.roleText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyDetailScreen(
    familyId: Long,
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onAddPeople: (groupId: Long) -> Unit,
    onCropGroupPhoto: (fileNames: List<String>) -> Unit,
    onManageTags: (groupId: Long) -> Unit,
) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val family by repo.observeFamily(familyId).collectAsStateWithLifecycle(initialValue = null)
    val members by repo.observePersonsInFamily(familyId).collectAsStateWithLifecycle(initialValue = emptyList())
    val kids = remember(members) { members.filter { it.person.role == Role.CHILD } }
    val groupId = family?.groupId ?: 0L
    val tags by remember(groupId) { repo.observeTags(groupId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val tag = tags.firstOrNull { it.id == family?.tagId }

    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var choosingTag by rememberSaveable { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var overflowMenu by remember { mutableStateOf(false) }
    val photoSource = rememberPhotoSource(onCropGroupPhoto)

    val title = familyTitle(family?.name.orEmpty(), members.map { it.person.name })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (tag != null) TagChip(tag, Modifier.padding(start = 8.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (members.isNotEmpty()) {
                        PhotoSourceMenu(photoSource, photoMenu, { photoMenu = false }) {
                            IconButton(onClick = { photoMenu = true }) {
                                Icon(Icons.Default.AddAPhoto, stringResource(R.string.faces_from_group_photo))
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { overflowMenu = false; editing = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tag)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                                onClick = { overflowMenu = false; choosingTag = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { overflowMenu = false; deleting = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { family?.let { onAddPeople(it.groupId) } },
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text(stringResource(R.string.add_people)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp), // room for the floating button
        ) {
            item {
                val notes = family?.notes.orEmpty()
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    HorizontalDivider()
                }
                if (members.isEmpty()) {
                    Text(stringResource(R.string.family_empty), modifier = Modifier.padding(16.dp))
                }
            }
            items(members, key = { it.person.id }) { row ->
                val p = row.person
                val kidIndex = kids.indexOfFirst { it.person.id == p.id }
                ListItem(
                    leadingContent = { Avatar(row.thumb, row.person.role, size = 80.dp) },
                    headlineContent = { Text(p.name, style = nameStyle(p)) },
                    supportingContent = {
                        val role = if (p.isPlaceholder) "${roleText(p)} · ${stringResource(R.string.placeholder)}" else roleText(p)
                        Text(if (p.notes.isBlank()) role else "$role · ${p.notes}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = if (kidIndex >= 0 && kids.size > 1) {
                        {
                            Row {
                                IconButton(
                                    enabled = kidIndex > 0,
                                    onClick = { scope.launch { repo.swapOrder(p, kids[kidIndex - 1].person) } },
                                ) { Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up)) }
                                IconButton(
                                    enabled = kidIndex < kids.lastIndex,
                                    onClick = { scope.launch { repo.swapOrder(p, kids[kidIndex + 1].person) } },
                                ) { Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down)) }
                            }
                        }
                    } else null,
                    modifier = Modifier.clickable { onOpenPerson(p.id) },
                )
            }
        }
    }

    val f = family
    if (editing && f != null) {
        NameNotesDialog(
            title = stringResource(R.string.edit_family),
            nameLabel = stringResource(R.string.family_name_hint),
            initialName = f.name,
            initialNotes = f.notes,
            nameRequired = false,
            onSave = { name, notes -> scope.launch { repo.updateFamily(f.copy(name = name.trim(), notes = notes.trim())) } },
            onDismiss = { editing = false },
        )
    }
    if (choosingTag && f != null) {
        ChooseTagDialog(
            tags = tags,
            selectedId = f.tagId,
            onSelect = { tagId -> scope.launch { repo.setFamilyTag(f.id, tagId) } },
            onManage = { onManageTags(f.groupId) },
            onDismiss = { choosingTag = false },
        )
    }
    if (deleting && f != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_family_title),
            text = stringResource(R.string.delete_family_text, title),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { scope.launch { repo.deleteFamily(f); onBack() } },
            onDismiss = { deleting = false },
        )
    }
}
