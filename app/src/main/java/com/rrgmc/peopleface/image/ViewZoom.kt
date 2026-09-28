package com.rrgmc.peopleface.image

/**
 * User zoom on top of the fitted image: screen = [zoom] * fitted + ([panX], [panY]).
 * Kept free of Android types so it can be unit tested.
 */
data class ViewZoom(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f) {

    /**
     * Applies a pinch gesture: zooms by [zoomChange] around ([centroidX], [centroidY]) and moves by
     * ([dx], [dy]); the result stays within [MIN_ZOOM]..[MAX_ZOOM] and keeps the view covered.
     */
    fun transform(
        centroidX: Float, centroidY: Float, zoomChange: Float, dx: Float, dy: Float,
        viewWidth: Float, viewHeight: Float,
    ): ViewZoom {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = newZoom / zoom
        return ViewZoom(
            newZoom,
            centroidX - (centroidX - panX) * k + dx,
            centroidY - (centroidY - panY) * k + dy,
        ).clamped(viewWidth, viewHeight)
    }

    fun pan(dx: Float, dy: Float, viewWidth: Float, viewHeight: Float) =
        copy(panX = panX + dx, panY = panY + dy).clamped(viewWidth, viewHeight)

    /** Doesn't let the zoomed content leave a gap at the view's edges. */
    fun clamped(viewWidth: Float, viewHeight: Float) = copy(
        panX = panX.coerceIn(viewWidth * (1 - zoom), 0f),
        panY = panY.coerceIn(viewHeight * (1 - zoom), 0f),
    )

    val isZoomed get() = zoom > 1.01f

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 8f
    }
}
