package com.aditya.cabletv.home

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Zoom-to-fill for banners that park a small logo in a wide dark field (Sony LIV, Apple TV,
 * Zee 5), which read as black gaps in the tile glass. Finds the logo against the dark border
 * colour, crops the empty margin and returns a 16:9 crop so the glass fills edge to edge.
 * Banners that already fill their frame, or have a light background, are returned unchanged.
 */
object BannerFit {

    private const val SAMPLE_W = 320
    private const val SAMPLE_H = 180

    fun zoomToFill(resources: Resources, banner: Drawable): Drawable {
        val bmp = render(banner)
        val bg = borderColor(bmp)
        if (luminance(bg) > DARK_LIMIT) return banner // only dark margins count as gaps

        val box = contentBox(bmp, bg) ?: return banner
        // Pad the logo, then grow the box to 16:9 around its centre, inside the image.
        val padX = (box.width() * 0.12f).toInt()
        val padY = (box.height() * 0.18f).toInt()
        var w = box.width() + 2 * padX
        var h = box.height() + 2 * padY
        if (w * 9 > h * 16) h = w * 9 / 16 else w = h * 16 / 9
        w = min(w, bmp.width); h = min(h, bmp.height)
        val zoom = bmp.width.toFloat() / w
        if (zoom < MIN_ZOOM) return banner // already fills the frame
        val cx = box.centerX(); val cy = box.centerY()
        val left = (cx - w / 2).coerceIn(0, bmp.width - w)
        val top = (cy - h / 2).coerceIn(0, bmp.height - h)
        val crop = Bitmap.createBitmap(bmp, left, top, w, h)
        return BitmapDrawable(resources, crop)
    }

    private fun render(d: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SAMPLE_W * 2, SAMPLE_H * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val old = d.bounds
        d.setBounds(0, 0, bmp.width, bmp.height)
        d.draw(c)
        d.bounds = old
        return bmp
    }

    /** Average colour of the outermost ring of pixels. */
    private fun borderColor(b: Bitmap): Int {
        var r = 0L; var g = 0L; var bl = 0L; var n = 0
        fun add(p: Int) { r += Color.red(p); g += Color.green(p); bl += Color.blue(p); n++ }
        for (x in 0 until b.width step 4) { add(b.getPixel(x, 1)); add(b.getPixel(x, b.height - 2)) }
        for (y in 0 until b.height step 4) { add(b.getPixel(1, y)); add(b.getPixel(b.width - 2, y)) }
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
    }

    /** Bounding box of pixels that stand out from [bg], or null if nothing does. */
    private fun contentBox(b: Bitmap, bg: Int): Rect? {
        var minX = b.width; var minY = b.height; var maxX = -1; var maxY = -1
        for (y in 0 until b.height step 2) for (x in 0 until b.width step 2) {
            val p = b.getPixel(x, y)
            val diff = abs(Color.red(p) - Color.red(bg)) + abs(Color.green(p) - Color.green(bg)) + abs(Color.blue(p) - Color.blue(bg))
            if (diff > CONTENT_DIFF) {
                minX = min(minX, x); minY = min(minY, y); maxX = max(maxX, x); maxY = max(maxY, y)
            }
        }
        return if (maxX < 0) null else Rect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun luminance(c: Int) = (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0

    private const val DARK_LIMIT = 0.25
    private const val CONTENT_DIFF = 90
    private const val MIN_ZOOM = 1.15f
}
