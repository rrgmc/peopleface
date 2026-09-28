package com.rrgmc.peopleface.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.data.db.Role

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonDialog(
    title: String,
    initialName: String = "",
    initialRole: Role = Role.KID,
    initialRoleLabel: String = "",
    initialNotes: String = "",
    onSave: (name: String, role: Role, roleLabel: String, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var role by rememberSaveable { mutableStateOf(initialRole) }
    var roleLabel by rememberSaveable { mutableStateOf(initialRoleLabel) }
    var notes by rememberSaveable { mutableStateOf(initialNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Role.entries.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(roleText(r)) },
                        )
                    }
                }
                if (role == Role.OTHER) {
                    OutlinedTextField(
                        value = roleLabel, onValueChange = { roleLabel = it },
                        label = { Text(stringResource(R.string.role_label_hint)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onDismiss(); onSave(name, role, roleLabel, notes) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
