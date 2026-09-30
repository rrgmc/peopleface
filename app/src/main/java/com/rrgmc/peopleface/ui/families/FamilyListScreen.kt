package com.rrgmc.peopleface.ui.families

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.data.db.TagEntity
import com.rrgmc.peopleface.data.sortedByName
import com.rrgmc.peopleface.ui.common.Avatar
import com.rrgmc.peopleface.ui.common.nameStyle
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import com.rrgmc.peopleface.ui.common.dragReorder
import com.rrgmc.peopleface.ui.common.draggableItem
import com.rrgmc.peopleface.ui.common.rememberDragReorderState
import com.rrgmc.peopleface.ui.common.GroupIcon
import com.rrgmc.peopleface.ui.common.NameNotesDialog
import com.rrgmc.peopleface.ui.common.PhotoSourceMenu
import com.rrgmc.peopleface.ui.common.TagChip
import com.rrgmc.peopleface.ui.common.rememberPhotoSource
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
    onNewFamily: (groupId: Long) -> Unit,
    onAddIndividuals: (groupId: Long) -> Unit,
    onCropGroupPhoto: (groupId: Long, fileNames: List<String>) -> Unit,
    onCropGroupIcon: (groupId: Long, fileNames: List<String>) -> Unit,
    onManageTags: (groupId: Long) -> Unit,
) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val group by repo.observeGroup(groupId).collectAsStateWithLifecycle(initialValue = null)
    val families by repo.observeFamilies(groupId).collectAsStateWithLifecycle(initialValue = null)
    val persons by repo.observePersonsInGroup(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
    val membersByFamily = remember(persons) { persons.groupBy { it.person.familyId } }
    val tags by repo.observeTags(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
    val tagsById = remember(tags) { tags.associateBy { it.id } }

    var menu by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    val photoSource = rememberPhotoSource { onCropGroupPhoto(groupId, it) }
    var iconMenu by remember { mutableStateOf(false) }
    val iconSource = rememberPhotoSource { onCropGroupIcon(groupId, it) }
    val context = LocalContext.current
    var faceSize by remember { mutableStateOf(loadFaceSize(context)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tapping the icon (or the groups symbol while there is none) picks a new one.
                        if (group != null) {
                            PhotoSourceMenu(iconSource, iconMenu, { iconMenu = false }) {
                                GroupIcon(
                                    group?.icon,
                                    Modifier.clip(RoundedCornerShape(8.dp)).clickable { iconMenu = true },
                                    size = 36.dp,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }
                        Text(group?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sizeMenu = true }) {
                            Icon(Icons.Default.PhotoSizeSelectLarge, stringResource(R.string.face_size))
                        }
                        DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                            FaceSize.entries.forEach { size ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(size.label)) },
                                    leadingIcon = {
                                        if (size == faceSize) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp))
                                    },
                                    onClick = { sizeMenu = false; faceSize = size; saveFaceSize(context, size) },
                                )
                            }
                        }
                    }
                    // Everything else is in the menu, leaving room for the group name. The photo source
                    // choices for "Faces from a photo" open from the same spot.
                    PhotoSourceMenu(photoSource, photoMenu, { photoMenu = false }) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (persons.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.faces_from_group_photo)) },
                                    leadingIcon = { Icon(Icons.Default.AddAPhoto, null) },
                                    onClick = { menu = false; photoMenu = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.quiz)) },
                                leadingIcon = { Icon(Icons.Default.Quiz, null) },
                                onClick = { menu = false; onQuiz(groupId) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_individuals)) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                                onClick = { menu = false; onAddIndividuals(groupId) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tags)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, null) },
                                onClick = { menu = false; onManageTags(groupId) },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (group?.icon == null) R.string.set_group_icon else R.string.change_group_icon))
                                },
                                leadingIcon = { Icon(Icons.Default.Image, null) },
                                onClick = { menu = false; iconMenu = true },
                            )
                            if (group?.icon != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.remove_group_icon)) },
                                    leadingIcon = { Icon(Icons.Default.HideImage, null) },
                                    onClick = { menu = false; scope.launch { repo.setGroupIcon(groupId, null) } },
                                )
                            }
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
                onClick = { onNewFamily(groupId) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_family)) },
            )
        },
    ) { padding ->
        // One-person families are shown together in a single card at the top; the others can be dragged.
        val (singleFamilies, dbOtherFamilies) = families.orEmpty().partition { membersByFamily[it.id].orEmpty().size == 1 }
        // While dragging, and until the database has the new order: the order on screen.
        var dragOrder by remember(families) { mutableStateOf<List<Long>?>(null) }
        val otherFamilies = dragOrder?.let { order ->
            val byId = dbOtherFamilies.associateBy { it.id }
            order.mapNotNull { byId[it] } + dbOtherFamilies.filter { it.id !in order }
        } ?: dbOtherFamilies
        val listState = rememberLazyListState()
        val reorder = rememberDragReorderState(
            listState,
            canMove = { it is Long },
            onMove = { from, to ->
                val ids = otherFamilies.map { it.id }.toMutableList()
                val toIndex = ids.indexOf(to as Long)
                if (ids.remove(from as Long) && toIndex >= 0) ids.add(toIndex, from)
                dragOrder = ids
            },
            onDrop = {
                val order = dragOrder
                if (order != null && order != dbOtherFamilies.map { it.id }) {
                    scope.launch { repo.reorderFamilies(order + singleFamilies.map { it.id }) }
                }
            },
        )
        val list = families
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).dragReorder(reorder),
            state = listState,
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
            if (singleFamilies.isNotEmpty()) {
                item(key = "individuals") {
                    FamilyCard(
                        title = stringResource(R.string.individuals),
                        members = singleFamilies.flatMap { membersByFamily[it.id].orEmpty() }
                            .sortedByName { it.person.name },
                        onClick = null,
                        onPersonClick = onOpenPerson,
                        onAdd = { onAddIndividuals(groupId) },
                        showMemberTags = true,
                        faceSize = faceSize,
                    )
                }
            }
            items(otherFamilies, key = { it.id }) { family ->
                val members = membersByFamily[family.id].orEmpty()
                FamilyCard(
                    modifier = draggableItem(reorder, family.id),
                    title = familyTitle(family.name, members.map { it.person }),
                    members = members,
                    tag = family.tagId?.let { tagsById[it] },
                    onClick = { onOpenFamily(family.id) },
                    onPersonClick = onOpenPerson,
                    faceSize = faceSize,
                )
            }
        }
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

/** How far below a picture its tag reaches in the individuals card. */
private val TAG_BELOW_AVATAR = 10.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyCard(
    title: String,
    members: List<PersonRow>,
    onClick: (() -> Unit)?,
    onPersonClick: (Long) -> Unit,
    tag: TagEntity? = null,
    onAdd: (() -> Unit)? = null,
    /** For the individuals card: each person's (one-person family's) tag is shown on their picture. */
    showMemberTags: Boolean = false,
    faceSize: FaceSize = FaceSize.NORMAL,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // With a tag, the tag follows the title instead of the end of the row.
                    modifier = Modifier.weight(1f, fill = tag == null),
                )
                if (tag != null) TagChip(tag, Modifier.padding(start = 8.dp))
                if (onAdd != null) {
                    IconButton(onClick = onAdd) { Icon(Icons.Default.PersonAdd, stringResource(R.string.add_individuals)) }
                }
            }
            if (members.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    members.forEach { m ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // Bottom-aligned, so names stay on one line when only some people have a tag.
                            modifier = Modifier.align(Alignment.Bottom).width(faceSize.width).clickable { onPersonClick(m.person.id) },
                        ) {
                            // The tag sits on the lower edge of the picture, mostly below it, so the face stays visible.
                            val hasTag = showMemberTags && m.tagName != null && m.tagColor != null
                            Box(contentAlignment = Alignment.BottomCenter) {
                                Avatar(
                                    m.thumb, m.person.role, size = faceSize.avatar,
                                    modifier = if (hasTag) Modifier.padding(bottom = TAG_BELOW_AVATAR) else Modifier,
                                )
                                if (hasTag) TagChip(m.tagName!!, m.tagColor!!, compact = true)
                            }
                            Text(
                                m.person.name,
                                style = nameStyle(m.person, MaterialTheme.typography.labelMedium),
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
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { content() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { content() }
    }
}
