package com.rrgmc.peopleface.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.ui.common.Avatar
import com.rrgmc.peopleface.ui.common.nameStyle
import com.rrgmc.peopleface.ui.common.familyLabels
import com.rrgmc.peopleface.ui.common.roleText
import java.text.Normalizer

private val MARKS = "\\p{Mn}+".toRegex()

/** Lower case without accents, so "jose" finds "José". */
fun normalizeForSearch(s: String): String =
    MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

/**
 * Every word of the query must appear in one of the person's texts; [extra] is e.g. the family label.
 * A placeholder name ("Pai") is not a real name, so it isn't searched.
 */
fun PersonRow.matches(normalizedQuery: String, extra: String = ""): Boolean =
    normalizedQuery.split(' ').filter { it.isNotBlank() }.all { term ->
        listOf(if (person.isPlaceholder) "" else person.name, person.notes, familyName, groupName, tagName.orEmpty(), extra)
            .any { normalizeForSearch(it).contains(term) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenPerson: (Long) -> Unit) {
    val repo = appContainer().repository
    val all by repo.observeAllPersons().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val labels = remember(all) { familyLabels(all) }
    val results = remember(all, query) {
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) emptyList() else all.filter { it.matches(q, labels[it.person.id].orEmpty()) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (query.isNotBlank() && results.isEmpty()) {
                item { Text(stringResource(R.string.no_results), Modifier.padding(16.dp)) }
            }
            items(results, key = { it.person.id }) { row ->
                val details = listOf(roleText(row.person), labels[row.person.id].orEmpty(), row.groupName)
                    .filter { it.isNotBlank() }
                ListItem(
                    leadingContent = { Avatar(row.thumb, row.person.role) },
                    headlineContent = { Text(row.person.name, style = nameStyle(row.person)) },
                    supportingContent = { Text(details.joinToString(" · ")) },
                    modifier = Modifier.clickable { onOpenPerson(row.person.id) },
                )
            }
        }
    }
}
