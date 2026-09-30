package com.rrgmc.peopleface.ui.common

import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.data.db.Role

/**
 * For each person (by id), a short text telling their family apart from others, so two kids with the same
 * name can be distinguished: the family name (if any) plus the adults' names, or the other members' names
 * when there are no adults. Never includes the person's own name, nor placeholder names such as "Pai"
 * (they would make every kid match a search for "pai").
 */
fun familyLabels(people: List<PersonRow>): Map<Long, String> {
    val byFamily = people.groupBy { it.person.familyId }
    return people.associate { row ->
        val others = byFamily[row.person.familyId].orEmpty()
            .filter { it.person.id != row.person.id && !it.person.isPlaceholder }
        val parents = others.filter { it.person.role == Role.ADULT }
        val members = if (parents.isNotEmpty()) {
            parents.joinToString(" & ") { it.person.name }
        } else {
            others.joinToString(", ") { it.person.name }
        }
        row.person.id to listOf(row.familyName, members).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
