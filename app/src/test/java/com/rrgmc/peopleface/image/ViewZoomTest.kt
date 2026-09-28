package com.rrgmc.peopleface.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewZoomTest {
    private val w = 1000f
    private val h = 800f

    @Test
    fun zoomKeepsCentroidInPlace() {
        val z = ViewZoom().transform(400f, 300f, 2f, 0f, 0f, w, h)
        assertEquals(2f, z.zoom, 0.001f)
        // The point under the fingers (400,300) must still map to (400,300).
        assertEquals(400f, 400f * z.zoom + z.panX, 0.001f)
        assertEquals(300f, 300f * z.zoom + z.panY, 0.001f)
        assertTrue(z.isZoomed)
    }

    @Test
    fun zoomIsLimited() {
        assertEquals(ViewZoom.MAX_ZOOM, ViewZoom().transform(0f, 0f, 100f, 0f, 0f, w, h).zoom, 0.001f)
        val out = ViewZoom().transform(500f, 400f, 0.2f, 0f, 0f, w, h)
        assertEquals(1f, out.zoom, 0.001f)
        assertFalse(out.isZoomed)
    }

    @Test
    fun panStaysWithinContent() {
        val z = ViewZoom(zoom = 2f)
        assertEquals(ViewZoom(2f, 0f, 0f), z.pan(500f, 500f, w, h))
        assertEquals(ViewZoom(2f, -1000f, -800f), z.pan(-5000f, -5000f, w, h))
        assertEquals(ViewZoom(2f, -100f, -50f), z.pan(-100f, -50f, w, h))
    }

    @Test
    fun noPanWithoutZoom() {
        assertEquals(ViewZoom(), ViewZoom().pan(300f, -300f, w, h))
    }
}
