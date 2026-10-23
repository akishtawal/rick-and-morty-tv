package com.aditya.cabletv.home

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import com.aditya.cabletv.R

/**
 * Colour tokens from the "Interdimensional Cable TV" design system (D-011, R-26), in its two
 * themes. [apply] switches the palette (Settings → Look → Theme, R-22); screens read the
 * current values when they build. Layout is in design px on a 1920 × 1080 canvas; see [Scale].
 */
object Tokens {
    var ground = 0; private set
    var surface = 0; private set
    var surfaceRaised = 0; private set
    var bezel = 0; private set
    var bezelEdge = 0; private set
    var ink = 0; private set
    var inkMuted = 0; private set
    var portal = 0; private set
    var portalGlow = 0; private set
    var portalInk = 0; private set
    var onPortal = 0; private set
    /** Focus ring and selected-pill fill: portal green at night, portal-deep on cream (contrast). */
    var focus = 0; private set
    var onFocus = 0; private set
    var staticDark = 0; private set
    var keyCap = 0; private set
    var keyInk = 0; private set
    val tileLight = c("#e9efe6")
    val coreGlow = c("#f4ffc8")

    init { apply(Prefs.THEME_NIGHT) }

    fun apply(theme: String) {
        val living = theme == Prefs.THEME_LIVING
        ground = c(if (living) "#e4d2a6" else "#0b1410")
        surface = c(if (living) "#efe3c2" else "#13201a")
        surfaceRaised = c(if (living) "#f6eed6" else "#1d3027")
        bezel = c(if (living) "#323839" else "#2b3132")
        bezelEdge = c(if (living) "#b9a77c" else "#474f4e")
        ink = c(if (living) "#2a2418" else "#e9efe6")
        inkMuted = c(if (living) "#5e5440" else "#9fb3a6")
        portal = c("#94cc66")
        portalGlow = c(if (living) "#b7e07a" else "#c8f08a")
        portalInk = c(if (living) "#2a5f1e" else "#a9dd7e")
        onPortal = c("#0b1410")
        focus = c(if (living) "#2c6a2b" else "#94cc66")
        onFocus = c(if (living) "#f6eed6" else "#0b1410")
        staticDark = c("#1a1f1c")
        keyCap = c(if (living) "#2a2418" else "#1a1f1c")
        keyInk = c(if (living) "#f6eed6" else "#e9efe6")
    }

    private fun c(hex: String) = Color.parseColor(hex)
}

/** Converts 1920 × 1080 design pixels to real pixels for this screen. */
class Scale(context: Context) {
    private val factor = context.resources.displayMetrics.widthPixels / 1920f
    fun px(designPx: Int): Int = Math.round(designPx * factor)
    fun pxF(designPx: Float): Float = designPx * factor
}

class Fonts(context: Context) {
    private fun load(context: Context, id: Int): Typeface = context.resources.getFont(id)
    // Get Schwifty is a fan font that isn't redistributed in the public repo; without it the
    // display face falls back to VT323. Drop get_schwifty.ttf into res/font to restore it.
    val display: Typeface = context.resources.getIdentifier("get_schwifty", "font", context.packageName)
        .takeIf { it != 0 }?.let { load(context, it) } ?: load(context, R.font.vt323)
    val body: Typeface = load(context, R.font.atkinson_regular)
    val bodyBold: Typeface = load(context, R.font.atkinson_bold)
    val osd: Typeface = load(context, R.font.vt323)
}

/** Sets font, size (design px), colour and letter spacing (design px) in one go. */
fun TextView.style(scale: Scale, face: Typeface, sizePx: Int, color: Int, letterSpacingPx: Float = 0f) {
    typeface = face
    setTextSize(TypedValue.COMPLEX_UNIT_PX, scale.pxF(sizePx.toFloat()))
    setTextColor(color)
    letterSpacing = if (letterSpacingPx == 0f) 0f else letterSpacingPx / sizePx
    includeFontPadding = false
}
