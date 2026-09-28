package com.rrgmc.peopleface.ui.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrgmc.peopleface.PeopleFaceApp
import com.rrgmc.peopleface.R
import com.rrgmc.peopleface.data.db.PersonRow
import com.rrgmc.peopleface.image.Box as ImageBox
import com.rrgmc.peopleface.ui.common.Avatar
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Maps between image pixels and screen pixels for an image fitted (centred) into a view. */
private data class Fit(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toImage(p: Offset) = Offset((p.x - offsetX) / scale, (p.y - offsetY) / scale)
    fun toScreen(b: ImageBox) =
        Rect(offsetX + b.left * scale, offsetY + b.top * scale, offsetX + b.right * scale, offsetY + b.bottom * scale)
}

private const val DRAG_NONE = 0
private const val DRAG_MOVE = 1
private const val DRAG_RESIZE = 2

/**
 * Cuts one face out of a larger picture. With [personId] the crop goes to that person and the screen closes;
 * with [familyId] ("group photo" mode) each detected face can be assigned to a family member in turn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceCropScreen(
    fileName: String,
    personId: Long,
    familyId: Long,
    onDone: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as PeopleFaceApp
    val vm: CropViewModel = viewModel { CropViewModel(app, fileName, personId) }
    val groupMode = familyId != 0L
    val members by remember(familyId) {
        if (groupMode) app.container.repository.observePersonsInFamily(familyId) else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.photo_saved_for)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (groupMode) R.string.crop_group_title else R.string.crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (groupMode) TextButton(onClick = onDone) { Text(stringResource(R.string.done)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color.Black),
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
                Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!vm.loading && vm.error == null) {
                    Text(
                        if (vm.faces.isEmpty()) stringResource(R.string.no_faces_found)
                        else pluralStringResource(R.plurals.faces_found, vm.faces.size, vm.faces.size) +
                            " " + stringResource(R.string.crop_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (groupMode) {
                    Text(stringResource(R.string.who_is_this), style = MaterialTheme.typography.titleSmall)
                    MemberChips(members, vm.target, vm.savedFor) { vm.target = it }
                }
                Button(
                    enabled = vm.crop != null && vm.target != null && !vm.saving,
                    onClick = {
                        vm.save { savedId ->
                            if (groupMode) {
                                val name = members.firstOrNull { it.person.id == savedId }?.person?.name.orEmpty()
                                vm.advance()
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
private fun MemberChips(members: List<PersonRow>, selected: Long?, saved: Set<Long>, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(members, key = { it.person.id }) { m ->
            FilterChip(
                selected = selected == m.person.id,
                onClick = { onSelect(m.person.id) },
                label = { Text(m.person.name) },
                leadingIcon = { Avatar(m.thumb, size = 24.dp) },
                trailingIcon = if (m.person.id in saved) {
                    { Icon(Icons.Default.Check, null) }
                } else null,
            )
        }
    }
}

@Composable
private fun CropCanvas(vm: CropViewModel) {
    val image = vm.image ?: return
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val fit = remember(viewSize, image) {
        val scale = min(viewSize.width.toFloat() / vm.imageWidth, viewSize.height.toFloat() / vm.imageHeight)
        Fit(
            scale = if (scale.isFinite() && scale > 0) scale else 1f,
            offsetX = (viewSize.width - vm.imageWidth * scale) / 2,
            offsetY = (viewSize.height - vm.imageHeight * scale) / 2,
        )
    }
    val handleRadiusPx = with(LocalDensity.current) { 32.dp.toPx() }
    val faceColor = Color(0xFFFFD54F)
    val doneColor = Color(0xFF66BB6A)

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it }
            .pointerInput(fit) {
                detectTapGestures { p ->
                    val ip = fit.toImage(p)
                    vm.onTap(ip.x, ip.y)
                }
            }
            .pointerInput(fit) {
                var mode = DRAG_NONE
                detectDragGestures(
                    onDragStart = { p ->
                        val c = vm.crop
                        mode = when {
                            c == null -> DRAG_NONE
                            nearCorner(fit.toScreen(c), p, handleRadiusPx) -> DRAG_RESIZE
                            else -> DRAG_MOVE
                        }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        when (mode) {
                            DRAG_MOVE -> vm.moveBy(delta.x / fit.scale, delta.y / fit.scale)
                            DRAG_RESIZE -> {
                                val ip = fit.toImage(change.position)
                                vm.resizeTo(ip.x, ip.y)
                            }
                        }
                    },
                )
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
            drawPath(shade, Color.Black.copy(alpha = 0.55f))
            drawRect(Color.White, r.topLeft, r.size, style = stroke)
            listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach {
                drawCircle(Color.White, radius = 7.dp.toPx(), center = it)
            }
        }
    }
}

private fun nearCorner(r: Rect, p: Offset, radius: Float) =
    listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).any { abs(it.x - p.x) < radius && abs(it.y - p.y) < radius }
