package com.rrgmc.peopleface.ui.crop

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrgmc.peopleface.PeopleFaceApp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.image.ViewZoom
import com.rrgmc.peopleface.image.Box as ImageBox
import com.rrgmc.peopleface.ui.common.Avatar
import com.rrgmc.peopleface.ui.common.familyLabels
import com.rrgmc.peopleface.ui.search.matches
import com.rrgmc.peopleface.ui.search.normalizeForSearch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Maps between image pixels and screen pixels. */
private data class Fit(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toImage(p: Offset) = Offset((p.x - offsetX) / scale, (p.y - offsetY) / scale)
    fun toScreen(b: ImageBox) =
        Rect(offsetX + b.left * scale, offsetY + b.top * scale, offsetX + b.right * scale, offsetY + b.bottom * scale)
}

private const val GESTURE_NONE = 0
private const val GESTURE_MOVE = 1
private const val GESTURE_RESIZE = 2
private const val GESTURE_PAN = 3
private const val GESTURE_ZOOM = 4

/**
 * Cuts faces out of a larger picture. With [personId] the crop goes to that person and the screen closes.
 * With [familyId] or [groupId] ("faces from a photo") each face can be assigned in turn to someone of that
 * family or group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceCropScreen(
    fileName: String,
    personId: Long,
    familyId: Long,
    groupId: Long,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as PeopleFaceApp
    val repo = app.container.repository
    val vm: CropViewModel = viewModel { CropViewModel(app, fileName, personId) }
    val pickMode = familyId != 0L || groupId != 0L
    val people by remember(familyId, groupId) {
        when {
            familyId != 0L -> repo.observePersonsInFamily(familyId)
            groupId != 0L -> repo.observePersonsInGroup(groupId).map { list -> list.sortedBy { it.person.name.lowercase() } }
            else -> flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by rememberSaveable { mutableStateOf("") }
    val labels = remember(people) { if (groupId != 0L) familyLabels(people) else emptyMap() }
    val shown = remember(people, filter) {
        val q = normalizeForSearch(filter.trim())
        if (q.isEmpty()) people else people.filter { it.matches(q, labels[it.person.id].orEmpty()) }
    }
    val snackbar = remember { SnackbarHostState() }

    // Leaving drops the picture: ask first while there is still something to save.
    val unfinished = vm.image != null && (!pickMode || vm.faces.isEmpty() || vm.doneFaces.size < vm.faces.size)
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (unfinished) confirmLeave = true else onDone() }
    BackHandler(enabled = unfinished) { confirmLeave = true }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_photo_title)) },
            text = {
                Text(
                    stringResource(if (vm.savedFor.isEmpty()) R.string.leave_photo_nothing_saved else R.string.leave_photo_some_saved)
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onDone() }) { Text(stringResource(R.string.leave)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.stay)) }
            },
        )
    }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.photo_saved_for)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (pickMode) R.string.crop_group_title else R.string.crop_title)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (pickMode) TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(
                // Clip, or the zoomed picture is drawn over the controls below.
                Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    vm.loading -> CircularProgressIndicator()
                    vm.error != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(stringResource(R.string.image_load_error), color = Color.White, textAlign = TextAlign.Center)
                        Text(
                            vm.error.orEmpty(),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    else -> CropCanvas(vm)
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val image = vm.image
                    val crop = vm.crop
                    if (image != null && crop != null) CropPreview(image, crop, 96.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!vm.loading && vm.error == null) {
                            Text(
                                if (vm.faces.isEmpty()) stringResource(R.string.no_faces_found)
                                else pluralStringResource(R.plurals.faces_found, vm.faces.size, vm.faces.size) +
                                    " " + stringResource(R.string.crop_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (pickMode) {
                            Text(stringResource(R.string.who_is_this), style = MaterialTheme.typography.titleSmall)
                            if (groupId != 0L) {
                                OutlinedTextField(
                                    value = filter,
                                    onValueChange = { filter = it },
                                    placeholder = { Text(stringResource(R.string.search_person_hint)) },
                                    leadingIcon = { Icon(Icons.Default.Search, null) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                if (pickMode) {
                    PeopleChips(shown, labels, selected = vm.target, saved = vm.savedFor) { vm.target = it }
                }
                Button(
                    enabled = vm.crop != null && vm.target != null && !vm.saving,
                    onClick = {
                        vm.save { savedId ->
                            if (pickMode) {
                                val name = people.firstOrNull { it.person.id == savedId }?.person?.name.orEmpty()
                                vm.advance()
                                filter = ""
                                scope.launch { snackbar.showSnackbar(savedMessage.format(name)) }
                            } else {
                                onDone()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save_face))
                }
            }
        }
    }
}

@Composable
private fun PeopleChips(
    people: List<PersonRow>,
    familyLabels: Map<Long, String>,
    selected: Long?,
    saved: Set<Long>,
    onSelect: (Long) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(people, key = { it.person.id }) { m ->
            val family = familyLabels[m.person.id].orEmpty()
            FilterChip(
                selected = selected == m.person.id,
                onClick = { onSelect(m.person.id) },
                label = {
                    // Second line tells apart people with the same name.
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(m.person.name)
                        if (family.isNotBlank()) {
                            Text(family, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                leadingIcon = { Avatar(m.thumb, size = 24.dp) },
                trailingIcon = if (m.person.id in saved) {
                    { Icon(Icons.Default.Check, null) }
                } else null,
            )
        }
    }
}

/** The part of the picture that will be saved, shown large enough to recognise the person. */
@Composable
private fun CropPreview(image: ImageBitmap, crop: ImageBox, size: Dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
        drawImage(
            image,
            srcOffset = IntOffset(crop.left.roundToInt(), crop.top.roundToInt()),
            srcSize = IntSize(
                crop.width.roundToInt().coerceAtMost(image.width - crop.left.roundToInt()),
                crop.height.roundToInt().coerceAtMost(image.height - crop.top.roundToInt()),
            ),
            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/**
 * The picture with face boxes and the crop square. One finger: tap a face, drag the square or a corner
 * (or pan when zoomed and dragging outside the square). Two fingers: pinch to zoom and pan.
 */
@Composable
private fun CropCanvas(vm: CropViewModel) {
    val image = vm.image ?: return
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableStateOf(ViewZoom()) }
    val base = remember(viewSize, image) {
        val scale = min(viewSize.width.toFloat() / vm.imageWidth, viewSize.height.toFloat() / vm.imageHeight)
            .let { if (it.isFinite() && it > 0) it else 1f }
        Fit(scale, (viewSize.width - vm.imageWidth * scale) / 2, (viewSize.height - vm.imageHeight * scale) / 2)
    }
    val fit = Fit(base.scale * zoom.zoom, base.offsetX * zoom.zoom + zoom.panX, base.offsetY * zoom.zoom + zoom.panY)
    val currentFit by rememberUpdatedState(fit)
    val handleRadiusPx = with(LocalDensity.current) { 32.dp.toPx() }
    val faceColor = Color(0xFFFFD54F)
    val doneColor = Color(0xFF66BB6A)

    // When zoomed in and the selection jumps to another face, bring it into view.
    LaunchedEffect(vm.selectedFace) {
        val c = vm.crop ?: return@LaunchedEffect
        if (!zoom.isZoomed) return@LaunchedEffect
        val cx = base.offsetX + c.centerX * base.scale
        val cy = base.offsetY + c.centerY * base.scale
        zoom = zoom.copy(panX = viewSize.width / 2f - zoom.zoom * cx, panY = viewSize.height / 2f - zoom.zoom * cy)
            .clamped(viewSize.width.toFloat(), viewSize.height.toFloat())
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val start = awaitFirstDown(requireUnconsumed = false).position
                        var mode = GESTURE_NONE
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            val f = currentFit
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (pressed.size >= 2) {
                                mode = GESTURE_ZOOM
                                val c = event.calculateCentroid(useCurrent = true)
                                val pan = event.calculatePan()
                                zoom = zoom.transform(c.x, c.y, event.calculateZoom(), pan.x, pan.y, w, h)
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            if (mode == GESTURE_ZOOM) continue // wait until all fingers are up
                            val change = pressed.first()
                            if (mode == GESTURE_NONE && (change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                val crop = vm.crop?.let { f.toScreen(it) }
                                mode = when {
                                    crop != null && nearCorner(crop, start, handleRadiusPx) -> GESTURE_RESIZE
                                    crop != null && crop.contains(start) -> GESTURE_MOVE
                                    zoom.isZoomed -> GESTURE_PAN
                                    crop != null -> GESTURE_MOVE
                                    else -> GESTURE_PAN
                                }
                            }
                            val delta = change.position - change.previousPosition
                            when (mode) {
                                GESTURE_MOVE -> vm.moveBy(delta.x / f.scale, delta.y / f.scale)
                                GESTURE_RESIZE -> f.toImage(change.position).let { vm.resizeTo(it.x, it.y) }
                                GESTURE_PAN -> zoom = zoom.pan(delta.x, delta.y, w, h)
                            }
                            if (mode != GESTURE_NONE) change.consume()
                        }
                        if (mode == GESTURE_NONE) {
                            val p = currentFit.toImage(start)
                            vm.onTap(p.x, p.y)
                        }
                    }
                },
        ) {
            val imageRect = Rect(
                fit.offsetX, fit.offsetY,
                fit.offsetX + vm.imageWidth * fit.scale, fit.offsetY + vm.imageHeight * fit.scale,
            )
            drawImage(
                image,
                dstOffset = IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()),
                dstSize = IntSize(imageRect.width.roundToInt(), imageRect.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            val stroke = Stroke(2.dp.toPx())
            vm.faces.forEachIndexed { i, face ->
                if (i == vm.selectedFace) return@forEachIndexed
                val r = fit.toScreen(face)
                drawRect(if (i in vm.doneFaces) doneColor else faceColor, r.topLeft, r.size, style = stroke)
            }
            vm.crop?.let { c ->
                val r = fit.toScreen(c)
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(imageRect)
                    addRect(r)
                }
                // Light shade: enough to show the square, without making the photo look dark.
                drawPath(shade, Color.Black.copy(alpha = 0.3f))
                drawRect(Color.White, r.topLeft, r.size, style = stroke)
                listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach {
                    drawCircle(Color.White, radius = 7.dp.toPx(), center = it)
                }
            }
        }
        if (zoom.isZoomed) {
            FilledTonalIconButton(
                onClick = { zoom = ViewZoom() },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) { Icon(Icons.Default.ZoomOutMap, stringResource(R.string.reset_zoom)) }
        }
    }
}

private fun nearCorner(r: Rect, p: Offset, radius: Float) =
    listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).any { abs(it.x - p.x) < radius && abs(it.y - p.y) < radius }
