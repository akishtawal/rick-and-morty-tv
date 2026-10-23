package com.aditya.cabletv.home

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * The home hero (R-9, D-014, D-018): a pre-rendered swirl turning once per 9 s and a breathing
 * halo, with the focused app's logo popping onto a pale glowing core. Apps without a logo show
 * their name in the display face instead (no core then). Sizes are design px at 1920 × 1080.
 */
class PortalView(context: Context, private val scale: Scale, displayFace: Typeface) : FrameLayout(context) {

    private val logo = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val core = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                Tokens.coreGlow, Tokens.coreGlow,
                Color.argb(190, 244, 255, 200), Color.argb(0, 244, 255, 200),
            )
            gradientRadius = scale.pxF(190f)
        }
    }
    private val name = TextView(context).apply {
        style(scale, displayFace, 80, Tokens.coreGlow, 2f)
        gravity = Gravity.CENTER
        maxWidth = scale.px(340)
        setShadowLayer(scale.pxF(1f), 0f, scale.pxF(6f), Tokens.onPortal)
    }
    private var popAnimator: ObjectAnimator? = null
    private val loops = mutableListOf<ObjectAnimator>()

    /** Reduced motion (Settings → Look → Motion): the portal holds still and logos fade in. */
    var reducedMotion = false
        set(value) {
            field = value
            loops.forEach { if (value) it.pause() else it.resume() }
        }

    init {
        clipChildren = false
        val halo = image("portal/portal-halo.png")
        val swirl = image("portal/portal-swirl.png")
        addView(halo, LayoutParams(scale.px(752), scale.px(752), Gravity.CENTER))
        addView(swirl, LayoutParams(scale.px(540), scale.px(540), Gravity.CENTER))
        addView(core, LayoutParams(scale.px(380), scale.px(260), Gravity.CENTER))
        addView(logo, LayoutParams(scale.px(290), scale.px(140), Gravity.CENTER))
        addView(name, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        loops += ObjectAnimator.ofFloat(swirl, View.ROTATION, 0f, 360f).apply {
            duration = 9_000; interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE; start()
        }
        loops += ObjectAnimator.ofPropertyValuesHolder(
            halo,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.8f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.97f, 1.03f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.97f, 1.03f),
        ).apply {
            duration = 1_600; repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE; start()
        }
    }

    /**
     * Shows [logoBitmap] on the glowing core, or as a tilted sticker with no core when [sticker]
     * (Settings → Look → Portal logo style, D-022), or [fallbackName] when there's no logo.
     */
    fun show(logoBitmap: Bitmap?, fallbackName: String, sticker: Boolean = false) {
        val target: View = if (logoBitmap != null) {
            logo.setImageBitmap(logoBitmap)
            val lp = logo.layoutParams
            lp.width = scale.px(if (sticker) 360 else 290); lp.height = scale.px(if (sticker) 180 else 140)
            logo.layoutParams = lp
            logo.rotation = if (sticker) -4f else 0f
            logo.visibility = VISIBLE; name.visibility = GONE
            core.visibility = if (sticker) GONE else VISIBLE
            logo
        } else {
            name.text = fallbackName
            name.visibility = VISIBLE; logo.visibility = GONE; core.visibility = GONE
            name
        }
        popAnimator?.cancel()
        popAnimator = if (reducedMotion) {
            ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f).apply { duration = 150 }
        } else {
            ObjectAnimator.ofPropertyValuesHolder(
                target,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.2f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.2f, 1f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
            ).apply { duration = 480; interpolator = OvershootInterpolator(2.2f) }
        }
        target.scaleX = 1f; target.scaleY = 1f
        popAnimator?.start()
    }

    private fun image(asset: String) = ImageView(context).apply {
        setImageBitmap(context.assets.open(asset).use { BitmapFactory.decodeStream(it) })
        setLayerType(LAYER_TYPE_HARDWARE, null) // animate on the GPU; the frame only re-composites
    }
}
