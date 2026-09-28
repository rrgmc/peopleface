package com.rrgmc.peopleface.ui.addpeople

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrgmc.peopleface.data.Repository
import com.rrgmc.peopleface.data.db.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PersonRowState(val key: Int, role: Role) {
    var role by mutableStateOf(role)
    var roleLabel by mutableStateOf("")
    var name by mutableStateOf("")
}

/**
 * Adds several people at once, to an existing family ([familyId] != 0) or to a new family in [groupId].
 * Rows with a blank name are ignored.
 */
class AddPeopleViewModel(
    private val repo: Repository,
    private val groupId: Long,
    val familyId: Long,
) : ViewModel() {
    private var nextKey = 0

    val rows = mutableStateListOf<PersonRowState>()
    var familyName by mutableStateOf("")
    var loading by mutableStateOf(true)
        private set
    var saving by mutableStateOf(false)
        private set

    val canSave get() = !saving && (rows.any { it.name.isNotBlank() } || (familyId == 0L && familyName.isNotBlank()))

    init {
        viewModelScope.launch {
            // Suggest the parents the family doesn't have yet, then a kid.
            val existing = if (familyId != 0L) repo.observePersonsInFamily(familyId).first().map { it.person.role } else emptyList()
            if (Role.FATHER !in existing) addRow(Role.FATHER)
            if (Role.MOTHER !in existing) addRow(Role.MOTHER)
            addRow(Role.KID)
            loading = false
        }
    }

    fun addRow(role: Role = Role.KID) {
        rows += PersonRowState(nextKey++, role)
    }

    fun removeRow(row: PersonRowState) {
        rows.remove(row)
        if (rows.isEmpty()) addRow()
    }

    fun onNameChange(row: PersonRowState, name: String) {
        row.name = name
        // Always keep an empty row at the end, so the next kid can just be typed in.
        if (row === rows.lastOrNull() && name.isNotBlank()) addRow(Role.KID)
    }

    /** [onSaved] receives the family id, or null if nothing was added. */
    fun save(onSaved: (Long?) -> Unit) {
        saving = true
        viewModelScope.launch {
            try {
                val people = rows.map { Repository.NewPerson(it.name, it.role, it.roleLabel) }
                onSaved(repo.addPeople(groupId, familyId, familyName, people))
            } finally {
                saving = false
            }
        }
    }
}
