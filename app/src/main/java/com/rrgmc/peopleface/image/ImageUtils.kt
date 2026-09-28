package com.rrgmc.peopleface.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

object ImageUtils {
    /** Longest side of the source image kept in memory while cropping. */
    const val SOURCE_MAX_SIDE = 2048

    /** Side of the stored face image. */
    const val IMAGE_SIDE = 512

    /** Side of the stored thumbnail used in lists. */
    const val THUMB_SIDE = 192

    /** Decodes [uri] downsampled and upright (EXIF orientation applied). */
    fun decode(context: Context, uri: Uri, maxSide: Int = SOURCE_MAX_SIDE): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open image")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Not an image")

        val opts = BitmapFactory.Options().apply {
            inSampleSize = CropMath.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Cannot decode image")

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = orientationMatrix(orientation)
        if (matrix != null) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        // Final exact downscale if the power-of-two sampling left it larger than wanted.
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest > maxSide) {
            val scale = maxSide.toFloat() / longest
            bitmap = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true
            )
        }
        return bitmap
    }

    private fun orientationMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return null
        }
        return m
    }

    /** Cuts [crop] out of [source] and returns (image JPEG, thumbnail JPEG). */
    fun cropToJpegs(source: Bitmap, crop: Box): Pair<ByteArray, ByteArray> {
        val left = crop.left.roundToInt().coerceIn(0, source.width - 1)
        val top = crop.top.roundToInt().coerceIn(0, source.height - 1)
        val width = crop.width.roundToInt().coerceIn(1, source.width - left)
        val height = crop.height.roundToInt().coerceIn(1, source.height - top)
        val cut = Bitmap.createBitmap(source, left, top, width, height)
        val image = scaleDown(cut, IMAGE_SIDE)
        val thumb = scaleDown(cut, THUMB_SIDE)
        return image.toJpeg(88) to thumb.toJpeg(82)
    }

    private fun scaleDown(bitmap: Bitmap, side: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= side) return bitmap
        val scale = side.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}
