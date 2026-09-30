package com.rrgmc.peopleface.ui.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Reordering the items of a LazyColumn by long-pressing and dragging them. Only items whose key passes
 * [canMove] can be dragged or be dropped on. [onMove] is called with the keys each time the dragged item
 * passes over another one (the list must then show it at the new place); [onDrop] when it is released.
 * Put [dragReorder] on the LazyColumn and [draggableItem] on each movable item.
 */
@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    canMove: (key: Any) -> Boolean,
    onMove: (from: Any, to: Any) -> Unit,
    onDrop: () -> Unit,
): DragReorderState {
    val scope = rememberCoroutineScope()
    val currentCanMove by rememberUpdatedState(canMove)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val state = remember(listState) {
        DragReorderState(listState, scope, { currentCanMove(it) }, { a, b -> currentOnMove(a, b) }, { currentOnDrop() })
    }
    LaunchedEffect(state) {
        // Scrolls when the dragged item is held against the top or bottom edge.
        for (delta in state.scrollChannel) listState.scrollBy(delta)
    }
    return state
}

class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val canMove: (Any) -> Boolean,
    private val onMove: (Any, Any) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Key of the item being dragged. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    internal val scrollChannel = Channel<Float>(Channel.CONFLATED)
    private var draggedDelta by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)

    private val draggingItem: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    /** How far the dragged item is drawn from its place in the list. */
    internal val draggingOffset: Float
        get() = draggingItem?.let { initialOffset + draggedDelta - it.offset } ?: 0f

    internal fun onDragStart(position: Offset) {
        // Item offsets start after the top content padding; the touch position starts at the list's top edge.
        val y = position.y.toInt() + listState.layoutInfo.viewportStartOffset
        val item = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { y in it.offset..(it.offset + it.size) && canMove(it.key) } ?: return
        draggingKey = item.key
        initialOffset = item.offset
        draggedDelta = 0f
    }

    internal fun onDragEnd() {
        if (draggingKey != null) onDrop()
        draggingKey = null
        draggedDelta = 0f
    }

    internal fun onDrag(delta: Offset) {
        if (draggingKey == null) return
        draggedDelta += delta.y
        val item = draggingItem ?: return
        val start = item.offset + draggingOffset
        val end = start + item.size
        val middle = (start + end) / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            middle.toInt() in it.offset..(it.offset + it.size) && it.key != item.key && canMove(it.key)
        }
        if (target != null) {
            // Moving the first visible item would otherwise scroll the list along with it.
            if (item.index == listState.firstVisibleItemIndex || target.index == listState.firstVisibleItemIndex) {
                val index = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                scope.launch { listState.scrollToItem(index, offset) }
            }
            onMove(item.key, target.key)
        } else {
            val info = listState.layoutInfo
            val overscroll = when {
                draggedDelta > 0 -> (end - info.viewportEndOffset).coerceAtLeast(0f)
                draggedDelta < 0 -> (start - info.viewportStartOffset).coerceAtMost(0f)
                else -> 0f
            }
            if (overscroll != 0f) scrollChannel.trySend(overscroll)
        }
    }
}

/** Starts dragging an item of the list after a long press on it. */
@Composable
fun Modifier.dragReorder(state: DragReorderState): Modifier {
    val haptics = LocalHapticFeedback.current
    return pointerInput(state) {
        detectDragGesturesAfterLongPress(
            onDragStart = { position ->
                state.onDragStart(position)
                if (state.draggingKey != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDrag = { change, delta ->
                if (state.draggingKey != null) change.consume()
                state.onDrag(delta)
            },
            onDragEnd = state::onDragEnd,
            onDragCancel = state::onDragEnd,
        )
    }
}

/** Draws the item where it is being dragged, above the others, which slide out of its way. */
@Composable
fun LazyItemScope.draggableItem(state: DragReorderState, key: Any): Modifier =
    if (state.draggingKey == key) {
        Modifier.zIndex(1f).graphicsLayer {
            translationY = state.draggingOffset
            scaleX = 1.03f
            scaleY = 1.03f
        }
    } else {
        Modifier.animateItem()
    }
