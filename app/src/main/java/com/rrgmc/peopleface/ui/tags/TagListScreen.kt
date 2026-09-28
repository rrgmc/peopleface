package com.rrgmc.peopleface.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.rrgmc.peopleface.ui.common.ConfirmDialog
import com.rrgmc.peopleface.ui.common.TAG_COLORS
import com.rrgmc.peopleface.ui.common.TagChip
import com.rrgmc.peopleface.ui.common.TagEditDialog
import kotlinx.coroutines.launch

/** The tags of a group, which can be put on its families. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagListScreen(groupId: Long, onBack: () -> Unit) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val group by repo.observeGroup(groupId).collectAsStateWithLifecycle(initialValue = null)
    val tags by repo.observeTags(groupId).collectAsStateWithLifecycle(initialValue = null)

    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        listOfNotNull(stringResource(R.string.tags), group?.name).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_tag)) },
            )
        },
    ) { padding ->
        val list = tags
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.tags_empty),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp), // room for the floating button
            ) {
                items(list.orEmpty(), key = { it.id }) { tag ->
                    ListItem(
                        headlineContent = { TagChip(tag) },
                        trailingContent = {
                            IconButton(onClick = { deletingId = tag.id }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete))
                            }
                        },
                        modifier = Modifier.clickable { editingId = tag.id },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) {
        // A different color for each new tag, while there are unused ones.
        val used = tags.orEmpty().map { it.color }.toSet()
        TagEditDialog(
            title = stringResource(R.string.new_tag),
            initialColor = TAG_COLORS.firstOrNull { it !in used } ?: TAG_COLORS.first(),
            onSave = { name, color -> scope.launch { repo.addTag(groupId, name, color) } },
            onDismiss = { adding = false },
        )
    }
    tags?.firstOrNull { it.id == editingId }?.let { tag ->
        TagEditDialog(
            title = stringResource(R.string.edit_tag),
            initialName = tag.name,
            initialColor = tag.color,
            onSave = { name, color -> scope.launch { repo.updateTag(tag.copy(name = name, color = color)) } },
            onDismiss = { editingId = null },
        )
    }
    tags?.firstOrNull { it.id == deletingId }?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.delete_tag_title),
            text = stringResource(R.string.delete_tag_text, tag.name),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { scope.launch { repo.deleteTag(tag) } },
            onDismiss = { deletingId = null },
        )
    }
}
