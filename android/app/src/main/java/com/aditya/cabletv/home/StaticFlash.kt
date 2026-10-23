package com.aditya.cabletv.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import kotlin.random.Random

/**
 * StaticTransition (design-spec §3, R-22): a 180 ms flash of TV snow before an app opens.
 * Three 480 × 270 noise frames are made once and cycled at 12 fps, upscaled without smoothing.
 * Reduced motion swaps it for a 100 ms fade to static-dark.
 */
class StaticFlash(context: Context) : ImageView(context) {

    private val frames = AnimationDrawable().apply {
        isOneShot = false
        repeat(3) { addFrame(BitmapDrawable(context.resources, noise()).apply { paint.isFilterBitmap = false }, 83) }
    }

    init {
        scaleType = ScaleType.FIT_XY
        visibility = GONE
        isClickable = false
    }

    /** Plays the flash, then runs [then]. */
    fun play(reduced: Boolean, then: () -> Unit) {
        visibility = VISIBLE
        if (reduced) {
            setImageDrawable(null)
            setBackgroundColor(Tokens.staticDark)
            alpha = 0f
            animate().alpha(1f).setDuration(100).withEndAction { then() }.start()
        } else {
            background = null
            alpha = 1f
            setImageDrawable(frames)
            frames.start()
            postDelayed({ then() }, 180)
        }
    }

    fun reset() {
        frames.stop()
        animate().cancel()
        visibility = View.GONE
    }

    private fun noise(): Bitmap {
        val w = 480; val h = 270
        val px = IntArray(w * h)
        val dark = intArrayOf(0x1a, 0x1f, 0x1c); val light = intArrayOf(0xdf, 0xe6, 0xe0)
        for (y in 0 until h) {
            val band = if (y % 3 == 0) 0.55f else 1f // faint scanlines
            for (x in 0 until w) {
                val t = Random.nextFloat() * band
                px[y * w + x] = Color.rgb(
                    (dark[0] + (light[0] - dark[0]) * t).toInt(),
                    (dark[1] + (light[1] - dark[1]) * t).toInt(),
                    (dark[2] + (light[2] - dark[2]) * t).toInt(),
                )
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.RGB_565)
    }
}
