package com.aditya.cabletv.home

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One channel on the home row (R-7, R-8, design-spec ChannelTile): a bezel holding the app's own
 * banner edge to edge in rounded "glass", then the number plate and the name. Apps that ship no
 * banner get their icon centred on a light tile; apps with neither get their name.
 */
class ChannelTileView(
    context: Context,
    private val scale: Scale,
    private val fonts: Fonts,
    widthPx: Int,
) : LinearLayout(context) {

    private val bezel = FrameLayout(context)
    private val glass = FrameLayout(context)
    private val image = ImageView(context)
    private val fallbackName = TextView(context)
    private val plate = TextView(context)
    private val label = TextView(context)

    init {
        orientation = VERTICAL
        isFocusable = true
        clipChildren = false
        // The banner fills the whole tile; the outline is drawn on top of it (no inner gap).
        val glassW = widthPx
        val glassH = glassW * 9 / 16

        glass.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, scale.pxF(RADIUS))
        }
        glass.clipToOutline = true
        glass.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        fallbackName.style(scale, fonts.display, 26, Tokens.ink, 1f)
        fallbackName.gravity = Gravity.CENTER
        glass.addView(fallbackName, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        bezel.addView(glass, FrameLayout.LayoutParams(glassW, glassH))
        addView(bezel, LayoutParams(widthPx, glassH))

        val head = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        plate.style(scale, fonts.osd, 26, Tokens.portalGlow)
        plate.setPadding(scale.px(8), scale.px(4), scale.px(8), scale.px(4))
        label.style(scale, fonts.bodyBold, 22, Tokens.inkMuted, 0.5f)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        head.addView(plate)
        head.addView(label, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = scale.px(8) })
        addView(head, LayoutParams(widthPx, LayoutParams.WRAP_CONTENT).apply { topMargin = scale.px(8) })

        pivotY = 0f // set properly once laid out
        setFocused(false)
    }

    fun bind(number: Int, name: String, banner: Drawable?, icon: Drawable?, textOnly: String? = null) {
        plate.text = "%03d".format(number)
        label.text = name
        when {
            textOnly != null -> showText(textOnly)
            banner != null -> {
                image.setImageDrawable(BannerFit.zoomToFill(resources, banner))
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.setPadding(0, 0, 0, 0)
                glass.background = solid(Tokens.staticDark)
                fallbackName.visibility = GONE
            }
            icon != null -> {
                // No TV banner: centre the icon on a light tile instead of a black void.
                image.setImageDrawable(icon)
                image.scaleType = ImageView.ScaleType.FIT_CENTER
                val pad = scale.px(18)
                image.setPadding(pad, pad, pad, pad)
                glass.background = solid(Tokens.tileLight)
                fallbackName.visibility = GONE
            }
            else -> showText(name)
        }
    }

    private fun showText(text: String) {
        image.setImageDrawable(null)
        glass.background = solid(Tokens.surfaceRaised)
        fallbackName.text = text
        fallbackName.visibility = VISIBLE
    }

    fun setFocused(focused: Boolean) {
        bezel.foreground = GradientDrawable().apply {
            cornerRadius = scale.pxF(RADIUS)
            setColor(0)
            setStroke(scale.px(if (focused) 5 else 2), if (focused) Tokens.focus else Tokens.bezelEdge)
        }
        bezel.outlineProvider = glass.outlineProvider // so the focus lift casts a rounded shadow
        bezel.elevation = if (focused) scale.pxF(18f) else 0f
        plate.background = GradientDrawable().apply {
            cornerRadius = scale.pxF(6f)
            setColor(if (focused) Tokens.focus else Tokens.staticDark)
        }
        plate.setTextColor(if (focused) Tokens.onFocus else Tokens.portalGlow)
        label.setTextColor(if (focused) Tokens.ink else Tokens.inkMuted)
        pivotX = width / 2f
        pivotY = height.toFloat()
        animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f).setDuration(120).start()
    }

    private companion object {
        const val RADIUS = 20f
    }

    private fun solid(color: Int) = GradientDrawable().apply { setColor(color) }
}
