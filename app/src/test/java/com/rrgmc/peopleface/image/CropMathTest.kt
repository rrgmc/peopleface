package com.rrgmc.peopleface.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMathTest {
    private fun assertInside(b: Box, w: Int, h: Int) {
        assertTrue("$b inside ${w}x$h", b.left >= 0 && b.top >= 0 && b.right <= w + 0.01f && b.bottom <= h + 0.01f)
    }

    @Test
    fun faceCropIsSquareAndLargerThanFace() {
        val face = Box(400f, 300f, 500f, 420f)
        val crop = CropMath.faceToCrop(face, 2000, 1500)
        assertEquals(crop.width, crop.height, 0.01f)
        assertEquals(120f * CropMath.FACE_SCALE, crop.width, 0.01f)
        assertTrue(crop.left < face.left && crop.right > face.right)
        assertTrue(crop.top < face.top && crop.bottom > face.bottom)
    }

    @Test
    fun faceCropAtEdgeIsShiftedInside() {
        val crop = CropMath.faceToCrop(Box(0f, 0f, 100f, 100f), 1000, 800)
        assertInside(crop, 1000, 800)
        assertEquals(190f, crop.width, 0.01f)
    }

    @Test
    fun hugeFaceIsLimitedToImage() {
        val crop = CropMath.faceToCrop(Box(100f, 100f, 700f, 700f), 800, 600)
        assertEquals(600f, crop.width, 0.01f)
        assertInside(crop, 800, 600)
    }

    @Test
    fun defaultCropIsCentred() {
        val crop = CropMath.defaultCrop(1000, 500)
        assertEquals(300f, crop.width, 0.01f)
        assertEquals(500f, crop.centerX, 0.01f)
        assertEquals(250f, crop.centerY, 0.01f)
    }

    @Test
    fun moveStopsAtBorders() {
        val crop = Box(10f, 10f, 110f, 110f)
        val moved = CropMath.move(crop, -50f, 1000f, 500, 400)
        assertEquals(Box(0f, 300f, 100f, 400f), moved)
    }

    @Test
    fun resizeKeepsCentreAndSquare() {
        val crop = Box(100f, 100f, 200f, 200f)
        val resized = CropMath.resizeTo(crop, 250f, 180f, 1000, 1000)
        assertEquals(150f, resized.centerX, 0.01f)
        assertEquals(150f, resized.centerY, 0.01f)
        assertEquals(200f, resized.width, 0.01f)
        assertEquals(200f, resized.height, 0.01f)
    }

    @Test
    fun resizeHasMinimum() {
        val resized = CropMath.resizeTo(Box(100f, 100f, 200f, 200f), 150f, 150f, 1000, 1000)
        assertEquals(CropMath.MIN_SIZE, resized.width, 0.01f)
    }

    @Test
    fun sampleSize() {
        assertEquals(1, CropMath.sampleSize(2000, 1000, 2048))
        assertEquals(2, CropMath.sampleSize(4096, 3000, 2048))
        assertEquals(1, CropMath.sampleSize(4000, 3000, 2048)) // exact downscale happens after decoding
        assertEquals(4, CropMath.sampleSize(8192, 6000, 2048))
    }
}
