package com.rrgmc.peopleface.ui.addpeople

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.data.db.Role
import com.rrgmc.peopleface.ui.common.roleText

/**
 * Adds several people at once. With [familyId] 0 it creates a new family in [groupId] on save.
 * [onSaved] receives the family id (null when nothing was added).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPeopleScreen(
    groupId: Long,
    familyId: Long,
    individuals: Boolean,
    onBack: () -> Unit,
    onSaved: (Long?) -> Unit,
) {
    val repo = appContainer().repository
    val vm: AddPeopleViewModel = viewModel { AddPeopleViewModel(repo, groupId, familyId, individuals) }
    val newFamily = familyId == 0L && !individuals

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                individuals -> R.string.add_individuals
                                newFamily -> R.string.new_family
                                else -> R.string.add_people
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(enabled = vm.canSave, onClick = { vm.save(onSaved) }) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        if (vm.loading) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (newFamily) {
                item {
                    OutlinedTextField(
                        value = vm.familyName,
                        onValueChange = { vm.familyName = it },
                        label = { Text(stringResource(R.string.family_name_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    stringResource(if (individuals) R.string.add_individuals_hint else R.string.add_people_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            items(vm.rows, key = { it.key }) { row ->
                PersonRow(
                    row = row,
                    onNameChange = { vm.onNameChange(row, it) },
                    onRemove = { vm.removeRow(row) },
                )
            }
        }
    }
}

@Composable
private fun PersonRow(row: PersonRowState, onNameChange: (String) -> Unit, onRemove: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoleSelector(row.role, onSelect = { row.role = it })
            OutlinedTextField(
                value = row.name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(R.string.name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, stringResource(R.string.remove)) }
        }
        if (row.role == Role.OTHER) {
            OutlinedTextField(
                value = row.roleLabel,
                onValueChange = { row.roleLabel = it },
                placeholder = { Text(stringResource(R.string.role_label_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 112.dp, end = 48.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun RoleSelector(role: Role, onSelect: (Role) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.width(112.dp)) {
            Text(roleText(role), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Role.entries.forEach { r ->
                DropdownMenuItem(text = { Text(roleText(r)) }, onClick = { open = false; onSelect(r) })
            }
        }
    }
}
