package com.rrgmc.peopleface.ui.family

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.db.Role
import com.rrgmc.peopleface.ui.common.Avatar
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import com.rrgmc.peopleface.ui.common.NameNotesDialog
import com.rrgmc.peopleface.ui.common.PersonDialog
import com.rrgmc.peopleface.ui.common.PhotoSourceMenu
import com.rrgmc.peopleface.ui.common.familyTitle
import com.rrgmc.peopleface.ui.common.rememberPhotoSource
import com.rrgmc.peopleface.ui.common.roleText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyDetailScreen(
    familyId: Long,
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
    onCropGroupPhoto: (Uri, Boolean) -> Unit,
) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val family by repo.observeFamily(familyId).collectAsStateWithLifecycle(initialValue = null)
    val members by repo.observePersonsInFamily(familyId).collectAsStateWithLifecycle(initialValue = emptyList())
    val kids = remember(members) { members.filter { it.person.role == Role.KID } }

    var addingRole by rememberSaveable { mutableStateOf<Role?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    val photoSource = rememberPhotoSource(onCropGroupPhoto)

    val title = familyTitle(family?.name.orEmpty(), members.map { it.person.name })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
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
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                val notes = family?.notes.orEmpty()
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AssistChip(
                        onClick = { addingRole = Role.KID },
                        label = { Text(stringResource(R.string.add_kid)) },
                        leadingIcon = { Icon(Icons.Default.ChildCare, null) },
                    )
                    AssistChip(
                        onClick = {
                            addingRole = when {
                                members.none { it.person.role == Role.FATHER } -> Role.FATHER
                                members.none { it.person.role == Role.MOTHER } -> Role.MOTHER
                                else -> Role.OTHER
                            }
                        },
                        label = { Text(stringResource(R.string.add_person)) },
                        leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                    )
                    if (members.isNotEmpty()) {
                        PhotoSourceMenu(photoSource, photoMenu, { photoMenu = false }) {
                            AssistChip(
                                onClick = { photoMenu = true },
                                label = { Text(stringResource(R.string.faces_from_group_photo)) },
                                leadingIcon = { Icon(Icons.Default.AddAPhoto, null) },
                            )
                        }
                    }
                }
                HorizontalDivider()
                if (members.isEmpty()) {
                    Text(stringResource(R.string.family_empty), modifier = Modifier.padding(16.dp))
                }
            }
            items(members, key = { it.person.id }) { row ->
                val p = row.person
                val kidIndex = kids.indexOfFirst { it.person.id == p.id }
                ListItem(
                    leadingContent = { Avatar(row.thumb, size = 56.dp) },
                    headlineContent = { Text(p.name) },
                    supportingContent = {
                        val role = roleText(p)
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

    addingRole?.let { role ->
        PersonDialog(
            title = stringResource(if (role == Role.KID) R.string.add_kid else R.string.add_person),
            initialRole = role,
            onSave = { name, r, label, notes -> scope.launch { repo.addPerson(familyId, name, r, label, notes) } },
            onDismiss = { addingRole = null },
        )
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
