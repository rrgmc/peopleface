package com.rrgmc.peopleface.ui.families

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.ui.common.Avatar
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import com.rrgmc.peopleface.ui.common.NameNotesDialog
import com.rrgmc.peopleface.ui.common.familyTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyListScreen(
    groupId: Long,
    onBack: () -> Unit,
    onOpenFamily: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onQuiz: (Long) -> Unit,
) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val group by repo.observeGroup(groupId).collectAsStateWithLifecycle(initialValue = null)
    val families by repo.observeFamilies(groupId).collectAsStateWithLifecycle(initialValue = null)
    val persons by repo.observePersonsInGroup(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
    val membersByFamily = remember(persons) { persons.groupBy { it.person.familyId } }

    var menu by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onQuiz(groupId) }) { Icon(Icons.Default.Quiz, stringResource(R.string.quiz)) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menu = false; editing = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; deleting = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_family)) },
            )
        },
    ) { padding ->
        val list = families
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val notes = group?.notes.orEmpty()
            if (notes.isNotBlank()) {
                item {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(4.dp))
                }
            }
            if (list != null && list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.families_empty),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(list.orEmpty(), key = { it.id }) { family ->
                val members = membersByFamily[family.id].orEmpty()
                FamilyCard(
                    title = familyTitle(family.name, members.map { it.person.name }),
                    members = members,
                    onClick = { onOpenFamily(family.id) },
                    onPersonClick = onOpenPerson,
                )
            }
        }
    }

    if (adding) {
        NameNotesDialog(
            title = stringResource(R.string.new_family),
            nameLabel = stringResource(R.string.family_name_hint),
            nameRequired = false,
            onSave = { name, notes -> scope.launch { onOpenFamily(repo.addFamily(groupId, name, notes)) } },
            onDismiss = { adding = false },
        )
    }
    val g = group
    if (editing && g != null) {
        NameNotesDialog(
            title = stringResource(R.string.edit_group),
            nameLabel = stringResource(R.string.group_name_hint),
            initialName = g.name,
            initialNotes = g.notes,
            onSave = { name, notes -> scope.launch { repo.updateGroup(g.copy(name = name.trim(), notes = notes.trim())) } },
            onDismiss = { editing = false },
        )
    }
    if (deleting && g != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_group_title),
            text = stringResource(R.string.delete_group_text, g.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { scope.launch { repo.deleteGroup(g); onBack() } },
            onDismiss = { deleting = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyCard(
    title: String,
    members: List<PersonRow>,
    onClick: () -> Unit,
    onPersonClick: (Long) -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (members.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    members.forEach { m ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(72.dp).clickable { onPersonClick(m.person.id) },
                        ) {
                            Avatar(m.thumb, size = 64.dp)
                            Text(
                                m.person.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
