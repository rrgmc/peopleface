package com.rrgmc.peopleface.image

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.rrgmc.peopleface.ui.common.cameraDir
import com.rrgmc.peopleface.ui.common.pickedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentPhotosTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun keepsOnlyTheNewestTen() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val added = (1..12).map { i ->
            RecentPhotos.add(context, bitmap).also { it.setLastModified(1_000_000L * i) }
        }
        // add() prunes while adding; one more prune pass happens on the next add.
        RecentPhotos.add(context, bitmap)
        val list = RecentPhotos.list(context)
        assertEquals(RecentPhotos.MAX, list.size)
        assertFalse(added.first().exists())

        RecentPhotos.clear(context)
        assertTrue(RecentPhotos.list(context).isEmpty())
    }

    @Test
    fun touchMovesToTop() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val a = RecentPhotos.add(context, bitmap).also { it.setLastModified(1_000_000L) }
        RecentPhotos.add(context, bitmap).also { it.setLastModified(2_000_000L) }
        RecentPhotos.touch(a)
        assertEquals(a.name, RecentPhotos.list(context).first().name)
        RecentPhotos.clear(context)
    }

    @Test
    fun namesResolveToTheRightFolder() {
        val recent = RecentPhotos.add(context, Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        assertTrue(RecentPhotos.isRecent(recent.name))
        assertEquals(recent, pickedFile(context, recent.name))
        assertEquals(cameraDir(context), pickedFile(context, "picked_1").parentFile)
        RecentPhotos.clear(context)
    }
}
