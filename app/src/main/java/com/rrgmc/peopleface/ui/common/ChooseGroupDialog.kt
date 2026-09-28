package com.rrgmc.peopleface.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer

/** Asks which group pictures shared from another app are for. */
@Composable
fun ChooseGroupDialog(onPick: (groupId: Long) -> Unit, onDismiss: () -> Unit) {
    val repo = appContainer().repository
    val groups by remember { repo.observeGroups() }.collectAsState(initial = null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_choose_group)) },
        text = {
            val list = groups
            when {
                list == null -> {}
                list.isEmpty() -> Text(stringResource(R.string.share_no_groups))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.group.id }) { (group) ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            modifier = Modifier.fillMaxWidth().clickable { onPick(group.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
