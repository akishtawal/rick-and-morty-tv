package com.aditya.cabletv.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The blue "WARMING UP..." screen (design-spec S3, R-16): shown on cold start while the catalog
 * loads and when an app is slow to open. Boot blue means loading and nothing else. Once shown it
 * stays up at least 300 ms so it never flickers.
 */
class BootOverlay(context: Context, private val scale: Scale, fonts: Fonts) : FrameLayout(context) {

    private val status = TextView(context)
    private val fill = View(context)
    private var shownAt = 0L
    private var stepper: ObjectAnimator? = null

    init {
        setBackgroundColor(BOOT_BLUE)
        isClickable = true // swallow input while up
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        col.addView(TextView(context).apply { style(scale, fonts.osd, 64, ON_BOOT, 4f); text = "WARMING UP..." })

        val frame = FrameLayout(context).apply {
            background = GradientDrawable().apply { setStroke(scale.px(3), ON_BOOT) }
            val p = scale.px(4)
            setPadding(p, p, p, p)
        }
        fill.setBackgroundColor(ON_BOOT)
        fill.pivotX = 0f
        frame.addView(fill, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        col.addView(frame, LinearLayout.LayoutParams(scale.px(480), scale.px(28)).apply { topMargin = scale.px(32) })

        status.style(scale, fonts.osd, 30, ON_BOOT, 2f)
        col.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = scale.px(32)
        })
        addView(col, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        visibility = GONE
    }

    fun show(statusText: String) {
        status.text = statusText
        if (visibility != VISIBLE) {
            alpha = 1f
            visibility = VISIBLE
            shownAt = SystemClock.uptimeMillis()
        }
        stepper?.cancel()
        // The bar steps across in 12 jumps over 2.4 s, like an old cable box.
        stepper = ObjectAnimator.ofFloat(fill, View.SCALE_X, 0f, 1f).apply {
            duration = 2_400
            repeatCount = ValueAnimator.INFINITE
            setInterpolator { t -> Math.floor(t * 12.0).toFloat() / 12f }
            start()
        }
    }

    /** Fades out, but never before the overlay has been up for [MIN_VISIBLE_MS]. */
    fun hide() {
        if (visibility != VISIBLE) return
        val wait = (MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - shownAt)).coerceAtLeast(0)
        animate().alpha(0f).setStartDelay(wait).setDuration(150).withEndAction {
            visibility = GONE
            stepper?.cancel()
        }.start()
    }

    /** Hides at once, e.g. when coming back to Home after an app. */
    fun hideNow() {
        animate().cancel()
        stepper?.cancel()
        visibility = GONE
    }

    companion object {
        private const val MIN_VISIBLE_MS = 300L
        private val BOOT_BLUE = Color.parseColor("#454993")
        private val ON_BOOT = Color.parseColor("#e4d5a6")
    }
}
