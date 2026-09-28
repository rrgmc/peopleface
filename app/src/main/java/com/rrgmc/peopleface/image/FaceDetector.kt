package com.rrgmc.peopleface.image

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device face detection (ML Kit, bundled model, works offline). */
class FaceDetector {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(0.05f)
            .build()
    )

    /** Returns face boxes in bitmap pixel coordinates, sorted left to right. */
    suspend fun detect(bitmap: Bitmap): List<Box> = suspendCancellableCoroutine { cont ->
        detector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                cont.resume(
                    faces.map { f ->
                        val r = f.boundingBox
                        Box(
                            r.left.toFloat().coerceAtLeast(0f),
                            r.top.toFloat().coerceAtLeast(0f),
                            r.right.toFloat().coerceAtMost(bitmap.width.toFloat()),
                            r.bottom.toFloat().coerceAtMost(bitmap.height.toFloat()),
                        )
                    }.sortedBy { it.left }
                )
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
