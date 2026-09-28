package com.rrgmc.peopleface.ui.crop

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
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
import com.rrgmc.peopleface.ui.common.pickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the source picture while cutting faces out of it. Only the cropped faces are stored;
 * the temporary copy of the source picture is deleted when leaving.
 */
class CropViewModel(
    private val app: PeopleFaceApp,
    fileName: String,
    personId: Long,
) : ViewModel() {
    private val file = pickedFile(app, fileName)
    private val repo = app.container.repository
    private var bitmap: Bitmap? = null

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
    var savedFor by mutableStateOf<Set<Long>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { ImageUtils.decode(file) }
                bitmap = bmp
                imageWidth = bmp.width
                imageHeight = bmp.height
                image = bmp.asImageBitmap()
                faces = try {
                    app.container.faceDetector.detect(bmp)
                } catch (e: Exception) {
                    emptyList()
                }
                if (faces.isNotEmpty()) selectFace(0) else crop = CropMath.defaultCrop(imageWidth, imageHeight)
            } catch (e: Throwable) {
                Log.e("PeopleFace", "Cannot open ${file.name}", e)
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    fun selectFace(index: Int) {
        selectedFace = index
        crop = CropMath.faceToCrop(faces[index], imageWidth, imageHeight)
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

    /** Group-photo mode: after saving, go to the next face nobody got yet. */
    fun advance() {
        target = null
        val next = faces.indices.firstOrNull { it !in doneFaces }
        if (next != null) {
            selectFace(next)
        } else if (faces.isNotEmpty()) {
            selectedFace = null
            crop = null
        }
    }

    override fun onCleared() {
        bitmap = null
        file.delete()
    }
}
