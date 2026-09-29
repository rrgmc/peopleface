package com.rrgmc.peopleface.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.data.db.TagEntity

/** Maximum length of a tag name: tags are meant to be short. */
const val TAG_NAME_MAX = 12

/** The background colors a tag can have (ARGB). */
val TAG_COLORS: List<Int> = listOf(
    0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B,
    0xFF1E88E5, 0xFF3949AB, 0xFF8E24AA, 0xFFD81B60, 0xFF6D4C41,
    0xFF757575, 0xFF212121,
).map { it.toInt() }

/**
 * A small colored label with the tag name; the text is black or white, whichever reads better.
 * [compact] is for a badge over a picture: same text size, but no extra height and a border in [borderColor].
 */
@Composable
fun TagChip(
    name: String,
    color: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest, // a Card's background
) {
    val background = Color(color)
    val shape = RoundedCornerShape(4.dp)
    val style = MaterialTheme.typography.labelSmall.let {
        if (compact) {
            it.copy(
                lineHeight = it.fontSize,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            )
        } else it
    }
    Text(
        name,
        color = if (background.luminance() > 0.5f) Color.Black else Color.White,
        style = style,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .widthIn(max = 120.dp)
            .then(if (compact) Modifier.border(1.5.dp, borderColor, shape) else Modifier)
            .background(background, shape)
            .padding(horizontal = if (compact) 4.dp else 6.dp, vertical = if (compact) 1.5.dp else 2.dp),
    )
}

@Composable
fun TagChip(tag: TagEntity, modifier: Modifier = Modifier) = TagChip(tag.name, tag.color, modifier)

/** Dialog to create or edit a tag: a short name and a color from [TAG_COLORS]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    title: String,
    initialName: String = "",
    initialColor: Int = TAG_COLORS.first(),
    onSave: (name: String, color: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(TAG_NAME_MAX) },
                    label = { Text(stringResource(R.string.tag_name_hint)) }, singleLine = true,
                    supportingText = { Text("${name.length} / $TAG_NAME_MAX") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.tag_color),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TAG_COLORS.forEach { c ->
                        val selected = c == color
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { color = c },
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check, null,
                                    tint = if (Color(c).luminance() > 0.5f) Color.Black else Color.White,
                                )
                            }
                        }
                    }
                }
                if (name.isNotBlank()) {
                    TagChip(name.trim(), color, Modifier.padding(top = 16.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onDismiss(); onSave(name.trim(), color) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Picks the tag of a family (or none) among the [tags] of its group. */
@Composable
fun ChooseTagDialog(
    tags: List<TagEntity>,
    selectedId: Long?,
    onSelect: (tagId: Long?) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val choose = { id: Long? -> onDismiss(); onSelect(id) }
                TagOption(selectedId == null, { choose(null) }) { Text(stringResource(R.string.no_tag)) }
                tags.forEach { tag -> TagOption(tag.id == selectedId, { choose(tag.id) }) { TagChip(tag) } }
                if (tags.isEmpty()) {
                    Text(
                        stringResource(R.string.tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onManage() }) { Text(stringResource(R.string.manage_tags)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TagOption(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        content()
    }
}
