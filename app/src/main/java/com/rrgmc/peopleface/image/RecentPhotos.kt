package com.rrgmc.peopleface.image

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * The last [MAX] pictures faces were cut from, so the same group photo can be opened again.
 * Kept as downscaled JPEG files in app-private storage that is not backed up; not in the database.
 */
object RecentPhotos {
    const val MAX = 10
    private const val PREFIX = "recent_"

    fun dir(context: Context) = File(context.noBackupFilesDir, "recent").apply { mkdirs() }

    fun isRecent(fileName: String) = fileName.startsWith(PREFIX)

    fun file(context: Context, fileName: String) = File(dir(context), fileName)

    /** Newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.startsWith(PREFIX) }.orEmpty().sortedByDescending { it.lastModified() }

    /** Stores [bitmap] (already upright and downscaled) and drops the oldest beyond [MAX]. */
    fun add(context: Context, bitmap: Bitmap): File {
        val file = File(dir(context), "$PREFIX${System.currentTimeMillis()}_${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        list(context).drop(MAX).forEach { it.delete() }
        return file
    }

    /** Moves an existing recent photo to the top of the list. */
    fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    fun clear(context: Context) {
        dir(context).listFiles().orEmpty().forEach { it.delete() }
    }
}
