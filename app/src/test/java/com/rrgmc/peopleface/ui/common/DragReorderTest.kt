package com.rrgmc.peopleface.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class DragReorderTest {
    @get:Rule
    val rule = createComposeRule()

    private var order = (1L..20L).toList()
    private var dropped = false

    private fun setList(header: Boolean) {
        rule.setContent {
            var items by remember { mutableStateOf(order) }
            val listState = rememberLazyListState()
            val state = rememberDragReorderState(
                listState,
                canMove = { it is Long },
                onMove = { from, to ->
                    val ids = items.toMutableList()
                    val toIndex = ids.indexOf(to)
                    ids.remove(from)
                    ids.add(toIndex, from as Long)
                    items = ids
                    order = ids
                },
                onDrop = { dropped = true },
            )
            LazyColumn(Modifier.fillMaxSize().testTag("list").dragReorder(state), state = listState) {
                if (header) item(key = "header") { Box(Modifier.fillMaxWidth().height(100.dp)) }
                items(items, key = { it }) { id ->
                    Box(draggableItem(state, id).fillMaxWidth().height(100.dp))
                }
            }
        }
    }

    /** Long-presses at [startY] (dp from the list's top) and drags by [by] dp (down if positive) in small steps. */
    private fun drag(startY: Float, by: Float) {
        val list = rule.onNodeWithTag("list")
        list.performTouchInput { down(Offset(centerX, startY.dp.toPx())) }
        rule.mainClock.advanceTimeBy(1000) // long press
        repeat((kotlin.math.abs(by) / 10).toInt()) {
            list.performTouchInput { moveBy(Offset(0f, (if (by > 0) 10 else -10).dp.toPx())) }
            rule.mainClock.advanceTimeByFrame()
        }
        list.performTouchInput { up() }
        rule.waitForIdle()
    }

    @Test
    fun draggingTheFirstItemMovesItOnePlace() {
        setList(header = false)
        drag(startY = 50f, by = 120f)
        assertEquals(listOf(2L, 1L, 3L, 4L), order.take(4))
        assertEquals(true, dropped)
    }

    @Test
    fun draggingTheSecondItemToTheTop() {
        setList(header = false)
        drag(startY = 150f, by = -120f)
        assertEquals(listOf(2L, 1L, 3L, 4L), order.take(4))
    }

    @Test
    fun draggingBelowAHeaderMovesItOnePlace() {
        setList(header = true)
        drag(startY = 150f, by = 120f)
        assertEquals(listOf(2L, 1L, 3L, 4L), order.take(4))
    }

    @Test
    fun theHeaderCannotBeDragged() {
        setList(header = true)
        drag(startY = 50f, by = 120f)
        assertEquals((1L..20L).toList(), order)
        assertEquals(false, dropped)
    }
}
