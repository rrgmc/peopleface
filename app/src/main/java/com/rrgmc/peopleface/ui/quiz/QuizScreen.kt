package com.rrgmc.peopleface.ui.quiz

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.appContainer
import com.rrgmc.peopleface.ui.common.roleText
import kotlinx.coroutines.flow.flowOf

private val Right = Color(0xFF2E7D32)
private val Wrong = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(groupId: Long, onBack: () -> Unit) {
    val repo = appContainer().repository
    val vm: QuizViewModel = viewModel { QuizViewModel(repo, groupId) }
    val group by remember(groupId) { if (groupId != 0L) repo.observeGroup(groupId) else flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.quiz))
                        Text(
                            group?.name ?: stringResource(R.string.all_groups),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (vm.total > 0) {
                        Text("${vm.correct} / ${vm.total}", Modifier.padding(end = 16.dp), style = MaterialTheme.typography.titleMedium)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                vm.loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                vm.isEmpty -> Text(
                    stringResource(R.string.quiz_empty),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
                else -> QuizContent(vm)
            }
        }
    }
}

@Composable
private fun QuizContent(vm: QuizViewModel) {
    val q = vm.question ?: return
    Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (vm.canUseChoices) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.mode == QuizMode.CHOICES,
                    onClick = { vm.mode = QuizMode.CHOICES },
                    label = { Text(stringResource(R.string.quiz_mode_choices)) },
                )
                FilterChip(
                    selected = vm.mode == QuizMode.FLASHCARD,
                    onClick = { vm.mode = QuizMode.FLASHCARD },
                    label = { Text(stringResource(R.string.quiz_mode_flashcard)) },
                )
            }
        }
        q.image?.let {
            Image(
                it, null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
            )
        }

        if (vm.revealed) {
            Text(q.person.person.name, style = MaterialTheme.typography.headlineMedium)
            val details = listOf(roleText(q.person.person), q.person.familyName, q.person.groupName).filter { it.isNotBlank() }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }

        if (vm.mode == QuizMode.CHOICES && vm.canUseChoices) {
            q.options.forEach { option ->
                val id = option.person.id
                val answered = vm.answered
                val colors = when {
                    answered == null -> ButtonDefaults.outlinedButtonColors()
                    id == q.person.person.id -> ButtonDefaults.outlinedButtonColors(containerColor = Right, contentColor = Color.White)
                    id == answered -> ButtonDefaults.outlinedButtonColors(containerColor = Wrong, contentColor = Color.White)
                    else -> ButtonDefaults.outlinedButtonColors()
                }
                OutlinedButton(
                    onClick = { vm.answer(id) },
                    colors = colors,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(option.person.name) }
            }
            if (vm.answered != null) {
                Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.next)) }
            }
        } else if (!vm.revealed) {
            Button(onClick = vm::reveal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.show_name)) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { vm.selfGrade(false) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.didnt_know))
                }
                Button(onClick = { vm.selfGrade(true) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.knew_it))
                }
            }
        }
    }
}
