package com.rrgmc.peopleface.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NameOrderTest {
    @Test
    fun accentsAndCaseDoNotPushNamesToTheEnd() {
        val names = listOf("Wilson", "Ângela", "andreia", "Érica", "Barbara", "Eduardo", "Íris", "Igor")
        assertEquals(
            listOf("andreia", "Ângela", "Barbara", "Eduardo", "Érica", "Igor", "Íris", "Wilson"),
            names.sortedByName { it },
        )
    }
}
