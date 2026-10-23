package com.aditya.cabletv.saver

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.aditya.cabletv.home.Fonts
import com.aditya.cabletv.home.Scale
import com.aditya.cabletv.home.StaticFlash
import com.aditya.cabletv.home.style
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Screensaver colours (design-spec S5): always dark, whatever the launcher theme. */
private object SaverColors {
    val ground = Color.parseColor("#050806")
    val hint = Color.parseColor("#5e6f64")
    val onPortal = Color.parseColor("#0b1410")
    val portalDeep = Color.parseColor("#2c6a2b")
    val ink = Color.parseColor("#e9efe6")
    val core = Color.parseColor("#f4ffc8")
    val bars = listOf("#94cc66", "#f2c94c", "#c3dac0", "#454993", "#b83a1d", "#e4d2a6", "#2b3132").map(Color::parseColor)
}

/** A scene the rotator can show; [tick] refreshes the clock, [stop] ends its animations. */
abstract class Scene(context: Context) : FrameLayout(context) {
    protected val anims = mutableListOf<ValueAnimator>()
    abstract fun tick(now: Date)
    open fun stop() = anims.forEach { it.cancel() }
}

/**
 * The spinning portal with a glowing core and the clock inside it (design-spec S5).
 * [size] is the swirl's diameter in design px; the halo spills 39% further.
 */
class SaverPortal(
    context: Context, scale: Scale, fonts: Fonts, size: Int, spinMs: Long, breatheMs: Long,
    reduced: Boolean, anims: MutableList<ValueAnimator>, topLine: String?,
    timePx: Int, datePx: Int,
) : FrameLayout(context) {
    val time = TextView(context)
    val date = TextView(context)

    init {
        clipChildren = false
        fun img(asset: String) = ImageView(context).apply {
            setImageBitmap(context.assets.open(asset).use { BitmapFactory.decodeStream(it) })
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        val halo = img("portal/portal-halo.png")
        val swirl = img("portal/portal-swirl.png")
        addView(halo, LayoutParams(scale.px((size * 1.39f).toInt()), scale.px((size * 1.39f).toInt()), Gravity.CENTER))
        addView(swirl, LayoutParams(scale.px(size), scale.px(size), Gravity.CENTER))
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(SaverColors.core, SaverColors.core, Color.argb(190, 244, 255, 200), Color.argb(0, 244, 255, 200))
                gradientRadius = scale.pxF(size * 0.37f)
            }
        }, LayoutParams(scale.px((size * 0.73f).toInt()), scale.px((size * 0.5f).toInt()), Gravity.CENTER))

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        topLine?.let {
            col.addView(TextView(context).apply { style(scale, fonts.osd, 28, SaverColors.portalDeep, 4f); text = it },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        time.style(scale, fonts.osd, timePx, SaverColors.onPortal)
        date.style(scale, fonts.osd, datePx, SaverColors.portalDeep, 3f)
        listOf(time, date).forEach { it.gravity = Gravity.CENTER }
        col.addView(time, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(date, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(col, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        if (!reduced) {
            anims += ObjectAnimator.ofFloat(swirl, ROTATION, 0f, 360f).apply {
                duration = spinMs; interpolator = LinearInterpolator(); repeatCount = ValueAnimator.INFINITE; start()
            }
            anims += ObjectAnimator.ofPropertyValuesHolder(
                halo,
                PropertyValuesHolder.ofFloat(ALPHA, 0.6f, 1f),
                PropertyValuesHolder.ofFloat(SCALE_X, 0.96f, 1.04f),
                PropertyValuesHolder.ofFloat(SCALE_Y, 0.96f, 1.04f),
            ).apply { duration = breatheMs / 2; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; start() }
        }
    }

    fun tick(now: Date, showClock: Boolean, weather: String?) {
        time.visibility = if (showClock) VISIBLE else GONE
        time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val d = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(now).uppercase(Locale.ENGLISH)
        date.text = if (weather != null) "$d · $weather" else d
    }
}

private fun pressAnyKey(context: Context, scale: Scale, fonts: Fonts) = TextView(context).apply {
    style(scale, fonts.osd, 30, SaverColors.hint, 2f)
    text = "PRESS ANY KEY TO RETURN"
}

/** Scene 1 · Portal idle: the portal drifts on near-black with the time inside; static every 20 s. */
class PortalIdleScene(
    context: Context, scale: Scale, fonts: Fonts, reduced: Boolean,
    private val showClock: Boolean, private val weather: () -> String?,
) : Scene(context) {
    private val portal = SaverPortal(context, scale, fonts, 640, 30_000, 6_000, reduced, anims, null, 150, 32)
    private val flash = StaticFlash(context)

    init {
        setBackgroundColor(SaverColors.ground)
        clipChildren = false
        addView(portal, LayoutParams(scale.px(640), scale.px(640), Gravity.CENTER))
        addView(pressAnyKey(context, scale, fonts), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = scale.px(96); bottomMargin = scale.px(60)
        })
        addView(flash, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (!reduced) {
            // Drift along a slow 90 s path so nothing sits in one place for hours.
            anims += ObjectAnimator.ofPropertyValuesHolder(
                portal,
                PropertyValuesHolder.ofFloat(TRANSLATION_X, 0f, scale.pxF(-180f), scale.pxF(120f), scale.pxF(200f), 0f),
                PropertyValuesHolder.ofFloat(TRANSLATION_Y, 0f, scale.pxF(-40f), scale.pxF(30f), scale.pxF(-50f), 0f),
            ).apply { duration = 90_000; interpolator = AccelerateDecelerateInterpolator(); repeatCount = ValueAnimator.INFINITE; start() }
            // A short burst of static every 20 s.
            anims += ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 20_000; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationRepeat(animation: android.animation.Animator) {
                        flash.play(false) { flash.postDelayed({ flash.reset() }, 420) }
                    }
                })
                start()
            }
        }
    }

    override fun tick(now: Date) = portal.tick(now, showClock, weather())
    override fun stop() { super.stop(); flash.reset() }
}

/** Scene 3 · Off the air: test-card bars, the portal centred with the time, "Be right back". */
class OffAirScene(
    context: Context, scale: Scale, fonts: Fonts, reduced: Boolean,
    private val showClock: Boolean, private val weather: () -> String?,
) : Scene(context) {
    private val portal = SaverPortal(context, scale, fonts, 600, 20_000, 4_000, reduced, anims, "CH 000 · OFF THE AIR", 140, 30)

    init {
        setBackgroundColor(SaverColors.ground)
        clipChildren = false
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        SaverColors.bars.forEach { c -> top.addView(View(context).apply { setBackgroundColor(c) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)) }
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, scale.px(640)))
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        SaverColors.bars.reversed().forEach { c -> strip.addView(View(context).apply { setBackgroundColor(c) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)) }
        addView(strip, LayoutParams(LayoutParams.MATCH_PARENT, scale.px(90)).apply { topMargin = scale.px(640) })
        addView(portal, LayoutParams(scale.px(600), scale.px(600), Gravity.CENTER))
        addView(TextView(context).apply {
            style(scale, fonts.display, 104, SaverColors.ink, 2f); text = "Be right back"
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
            marginStart = scale.px(96); bottomMargin = scale.px(50)
        })
        addView(pressAnyKey(context, scale, fonts), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = scale.px(96); bottomMargin = scale.px(60)
        })
        if (!reduced) {
            // The lower strip jolts sideways every 7 s, like a bad signal.
            anims += ObjectAnimator.ofFloat(strip, TRANSLATION_X, 0f, 0f, scale.pxF(-40f), scale.pxF(26f), scale.pxF(-12f), 0f).apply {
                duration = 7_000; repeatCount = ValueAnimator.INFINITE
                setInterpolator { t -> if (t < 0.9f) 0f else (t - 0.9f) / 0.1f * 0.8f + 0.2f }
                start()
            }
        }
    }

    override fun tick(now: Date) = portal.tick(now, showClock, weather())
}
