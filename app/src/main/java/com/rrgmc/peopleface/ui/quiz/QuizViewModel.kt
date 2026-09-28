package com.rrgmc.peopleface.ui.quiz

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrgmc.peopleface.data.Repository
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.data.db.Role
import com.rrgmc.peopleface.ui.common.toImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class QuizMode { CHOICES, FLASHCARD }

class Question(val person: PersonRow, val image: ImageBitmap?, val options: List<PersonRow>)

/** Pick 3 other people with different names, preferring the same kind (kid vs. adult) so it isn't too easy. */
fun pickOptions(target: PersonRow, candidates: List<PersonRow>, count: Int = 4): List<PersonRow> {
    fun isKid(r: PersonRow) = r.person.role == Role.KID
    val others = candidates
        .filter { it.person.id != target.person.id && !it.person.name.equals(target.person.name, ignoreCase = true) }
        .distinctBy { it.person.name.lowercase() }
        .shuffled()
    val (same, different) = others.partition { isKid(it) == isKid(target) }
    return (listOf(target) + (same + different).take(count - 1)).shuffled()
}

class QuizViewModel(private val repo: Repository, private val groupId: Long) : ViewModel() {
    private var candidates: List<PersonRow> = emptyList()
    private var lastPersonId: Long? = null

    var loading by mutableStateOf(true)
        private set
    var mode by mutableStateOf(QuizMode.CHOICES)
    var question by mutableStateOf<Question?>(null)
        private set
    var answered by mutableStateOf<Long?>(null)
        private set
    var revealed by mutableStateOf(false)
        private set
    var correct by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    val isEmpty get() = !loading && candidates.isEmpty()

    init {
        viewModelScope.launch {
            candidates = repo.personsWithPhotos(groupId)
            if (candidates.size < 2) mode = QuizMode.FLASHCARD
            next()
            loading = false
        }
    }

    val canUseChoices get() = candidates.size >= 2

    fun next() {
        if (candidates.isEmpty()) return
        val pool = candidates.filter { it.person.id != lastPersonId }.ifEmpty { candidates }
        val target = pool.random()
        lastPersonId = target.person.id
        answered = null
        revealed = false
        viewModelScope.launch {
            val image = withContext(Dispatchers.IO) { repo.randomImage(target.person.id)?.toImageBitmap() }
            question = Question(target, image, pickOptions(target, candidates))
        }
    }

    fun answer(personId: Long) {
        val q = question ?: return
        if (answered != null) return
        answered = personId
        revealed = true
        total++
        if (personId == q.person.person.id) correct++
    }

    fun reveal() {
        revealed = true
    }

    fun selfGrade(knewIt: Boolean) {
        total++
        if (knewIt) correct++
        next()
    }
}
