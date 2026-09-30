package com.rrgmc.peopleface.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.ui.common.GroupIcon
import com.rrgmc.peopleface.ui.common.NameNotesDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onOpenGroup: (Long) -> Unit,
    onSearch: () -> Unit,
    onQuiz: () -> Unit,
    onSettings: () -> Unit,
) {
    val repo = appContainer().repository
    val scope = rememberCoroutineScope()
    val groups by repo.observeGroups().collectAsStateWithLifecycle(initialValue = null)
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                    IconButton(onClick = onQuiz) { Icon(Icons.Default.Quiz, stringResource(R.string.quiz)) }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.new_group)) },
            )
        },
    ) { padding ->
        val list = groups
        if (list != null && list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.groups_empty),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(list.orEmpty(), key = { it.group.id }) { g ->
                    ListItem(
                        headlineContent = { Text(g.group.name) },
                        supportingContent = {
                            Text(
                                pluralStringResource(R.plurals.families_count, g.familyCount, g.familyCount) +
                                    " · " + pluralStringResource(R.plurals.people_count, g.personCount, g.personCount)
                            )
                        },
                        leadingContent = { GroupIcon(g.group.icon) },
                        modifier = Modifier.clickable { onOpenGroup(g.group.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) {
        NameNotesDialog(
            title = stringResource(R.string.new_group),
            nameLabel = stringResource(R.string.group_name_hint),
            onSave = { name, notes -> scope.launch { onOpenGroup(repo.addGroup(name, notes)) } },
            onDismiss = { adding = false },
        )
    }
}
