package com.rrgmc.peopleface.image

import kotlin.math.max
import kotlin.math.min

/** A plain rectangle in image pixel coordinates (kept free of Android types so it can be unit tested). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2

    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun offset(dx: Float, dy: Float) = Box(left + dx, top + dy, right + dx, bottom + dy)
}

object CropMath {
    /** How much wider than the detected face the crop is, to include hair, chin and a bit of shoulders. */
    const val FACE_SCALE = 1.9f

    /** Moves the crop centre down a little (fraction of face height): faces sit better slightly above centre. */
    const val DOWN_SHIFT = 0.12f

    const val MIN_SIZE = 48f

    /** Turns a detected face into a square head-and-shoulders crop that fits inside the image. */
    fun faceToCrop(face: Box, imageWidth: Int, imageHeight: Int): Box {
        val size = max(face.width, face.height) * FACE_SCALE
        val cx = face.centerX
        val cy = face.centerY + face.height * DOWN_SHIFT
        return fitSquare(cx, cy, size, imageWidth, imageHeight)
    }

    /** Default crop when no face was found: a centred square covering 60% of the short side. */
    fun defaultCrop(imageWidth: Int, imageHeight: Int): Box =
        fitSquare(imageWidth / 2f, imageHeight / 2f, min(imageWidth, imageHeight) * 0.6f, imageWidth, imageHeight)

    /** Square of [size] centred at ([cx],[cy]), shrunk to fit and shifted to stay inside the image. */
    fun fitSquare(cx: Float, cy: Float, size: Float, imageWidth: Int, imageHeight: Int): Box {
        val s = size.coerceIn(min(MIN_SIZE, min(imageWidth, imageHeight).toFloat()), min(imageWidth, imageHeight).toFloat())
        val left = (cx - s / 2).coerceIn(0f, imageWidth - s)
        val top = (cy - s / 2).coerceIn(0f, imageHeight - s)
        return Box(left, top, left + s, top + s)
    }

    /** Moves [crop] by the given delta, keeping it inside the image. */
    fun move(crop: Box, dx: Float, dy: Float, imageWidth: Int, imageHeight: Int): Box {
        val ndx = dx.coerceIn(-crop.left, imageWidth - crop.right)
        val ndy = dy.coerceIn(-crop.top, imageHeight - crop.bottom)
        return crop.offset(ndx, ndy)
    }

    /**
     * Resizes the square [crop] keeping its centre, so that its half-size equals the Chebyshev distance
     * from the centre to ([x],[y]) (i.e. dragging a corner handle).
     */
    fun resizeTo(crop: Box, x: Float, y: Float, imageWidth: Int, imageHeight: Int): Box {
        val half = max(kotlin.math.abs(x - crop.centerX), kotlin.math.abs(y - crop.centerY))
        return fitSquare(crop.centerX, crop.centerY, half * 2, imageWidth, imageHeight)
    }

    /** Sample size (power of two) so that the decoded image's longest side is at most about [maxSide]. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
