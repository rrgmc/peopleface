package com.rrgmc.peopleface.ui.common

import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.data.db.Role

/**
 * For each person (by id), a short text telling their family apart from others, so two kids with the same
 * name can be distinguished: the family name (if any) plus the parents' names, or the other members' names
 * when there are no parents. Never includes the person's own name.
 */
fun familyLabels(people: List<PersonRow>): Map<Long, String> {
    val byFamily = people.groupBy { it.person.familyId }
    return people.associate { row ->
        val others = byFamily[row.person.familyId].orEmpty().filter { it.person.id != row.person.id }
        val parents = others.filter { it.person.role == Role.FATHER || it.person.role == Role.MOTHER }
        val members = if (parents.isNotEmpty()) {
            parents.joinToString(" & ") { it.person.name }
        } else {
            others.joinToString(", ") { it.person.name }
        }
        row.person.id to listOf(row.familyName, members).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
