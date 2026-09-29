package com.rrgmc.peopleface.ui.crop

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrgmc.peopleface.PeopleFaceApp
import com.rrgmc.peopleface.image.Box
import com.rrgmc.peopleface.image.CropMath
import com.rrgmc.peopleface.image.ImageUtils
import com.rrgmc.peopleface.image.RecentPhotos
import com.rrgmc.peopleface.ui.common.pickedFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Cuts faces out of one or more pictures, one picture at a time. Only the cropped faces go to the
 * database. Every picture is first turned into one of the [RecentPhotos] (an upright, downscaled copy
 * outside the database), in the background and in order, so pictures that were not reached can still be
 * reopened later; the temporary copies they were loaded from are deleted.
 */
class CropViewModel(
    private val app: PeopleFaceApp,
    fileNames: List<String>,
    private val personId: Long,
) : ViewModel() {
    private val repo = app.container.repository
    private val sources: List<File> = fileNames.map { pickedFile(app, it) }

    /** Recent-photo file for each picture, or the error that prevented making it. */
    private val prepared = List(sources.size) { CompletableDeferred<Result<File>>() }
    private var bitmap: Bitmap? = null
    private var loadJob: Job? = null

    val photoCount = sources.size
    var photoIndex by mutableIntStateOf(0)
        private set
    val hasNextPhoto get() = photoIndex < photoCount - 1
    val remainingPhotos get() = photoCount - 1 - photoIndex

    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var imageWidth = 0
        private set
    var imageHeight = 0
        private set
    var faces by mutableStateOf<List<Box>>(emptyList())
        private set
    var selectedFace by mutableStateOf<Int?>(null)
        private set
    var crop by mutableStateOf<Box?>(null)
        private set
    var doneFaces by mutableStateOf<Set<Int>>(emptySet())
        private set
    var loading by mutableStateOf(true)
        private set
    /** Why the picture could not be opened, or null. */
    var error by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    /** Who the next saved crop belongs to. */
    var target by mutableStateOf(personId.takeIf { it != 0L })
    /** Everyone who got a face during this session (across all pictures). */
    var savedFor by mutableStateOf<Set<Long>>(emptySet())
        private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            sources.forEachIndexed { i, source ->
                if (!isActive) return@launch // screen left: don't keep converting
                prepared[i].complete(runCatching { toRecent(source) })
            }
        }
        load(0)
    }

    private fun toRecent(source: File): File {
        if (RecentPhotos.isRecent(source.name)) {
            RecentPhotos.touch(source)
            return source
        }
        try {
            return RecentPhotos.add(app, ImageUtils.decode(source))
        } finally {
            source.delete()
        }
    }

    private fun load(index: Int) {
        loadJob?.cancel()
        photoIndex = index
        bitmap = null
        image = null
        faces = emptyList()
        selectedFace = null
        crop = null
        doneFaces = emptySet()
        error = null
        loading = true
        if (personId == 0L) target = null
        loadJob = viewModelScope.launch {
            try {
                val file = prepared[index].await().getOrThrow()
                val bmp = withContext(Dispatchers.IO) { ImageUtils.decode(file) }
                bitmap = bmp
                imageWidth = bmp.width
                imageHeight = bmp.height
                image = bmp.asImageBitmap()
                // Left to right, so stepping through them with previous/next is predictable.
                faces = try {
                    app.container.faceDetector.detect(bmp).sortedBy { it.centerX }
                } catch (e: Exception) {
                    emptyList()
                }
                if (faces.isNotEmpty()) selectFace(0) else crop = CropMath.defaultCrop(imageWidth, imageHeight)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("PeopleFace", "Cannot open picture ${index + 1}", e)
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun nextPhoto() {
        if (hasNextPhoto) load(photoIndex + 1)
    }

    fun selectFace(index: Int) {
        selectedFace = index
        crop = CropMath.faceToCrop(faces[index], imageWidth, imageHeight)
    }

    /** Selects the previous ([step] = -1) or next ([step] = 1) face, wrapping around. */
    fun stepFace(step: Int) {
        if (faces.isEmpty()) return
        val current = selectedFace ?: if (step > 0) -1 else faces.size
        selectFace(Math.floorMod(current + step, faces.size))
    }

    fun onTap(x: Float, y: Float) {
        // Prefer the smallest face containing the point (in case boxes overlap).
        val index = faces.indices.filter { faces[it].contains(x, y) }.minByOrNull { faces[it].width }
        if (index != null) selectFace(index)
    }

    fun moveBy(dx: Float, dy: Float) {
        crop = crop?.let { CropMath.move(it, dx, dy, imageWidth, imageHeight) }
    }

    fun resizeTo(x: Float, y: Float) {
        crop = crop?.let { CropMath.resizeTo(it, x, y, imageWidth, imageHeight) }
    }

    /** Saves the current crop for [target]. [onSaved] receives the person id. */
    fun save(onSaved: (personId: Long) -> Unit) {
        val bmp = bitmap ?: return
        val c = crop ?: return
        val personId = target ?: return
        saving = true
        viewModelScope.launch {
            try {
                val (img, thumb) = withContext(Dispatchers.Default) { ImageUtils.cropToJpegs(bmp, c) }
                repo.addPhoto(personId, img, thumb)
                savedFor = savedFor + personId
                selectedFace?.let { doneFaces = doneFaces + it }
                onSaved(personId)
            } finally {
                saving = false
            }
        }
    }

    /**
     * Group-photo mode: after saving, go to the face nobody got yet that is closest to the one just
     * saved, so the view stays where the user was working instead of jumping back to the first face.
     */
    fun advance() {
        target = null
        val here = crop
        val next = faces.indices.filter { it !in doneFaces }.minByOrNull { i ->
            if (here == null) return@minByOrNull i.toFloat()
            val dx = faces[i].centerX - here.centerX
            val dy = faces[i].centerY - here.centerY
            dx * dx + dy * dy
        }
        if (next != null) {
            selectFace(next)
        } else if (faces.isNotEmpty()) {
            selectedFace = null
            crop = null
        }
    }

    override fun onCleared() {
        bitmap = null
        // Temporary copies not yet turned into recent photos (the background job stops with the scope).
        sources.filterNot { RecentPhotos.isRecent(it.name) }.forEach { it.delete() }
    }
}
