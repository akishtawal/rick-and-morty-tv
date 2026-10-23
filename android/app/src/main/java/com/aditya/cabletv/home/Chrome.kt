package com.aditya.cabletv.home

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** A remote hint: a 40 px key cap and its label on one centred line (GuideBar, design-spec §3). */
fun remoteHint(context: Context, scale: Scale, fonts: Fonts, key: String, label: String) =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            style(scale, fonts.osd, 30, Tokens.keyInk, 2f)
            text = key
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = scale.pxF(6f); setColor(Tokens.keyCap) }
            setPadding(scale.px(10), 0, scale.px(10), 0)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, scale.px(40)))
        addView(TextView(context).apply { style(scale, fonts.osd, 30, Tokens.inkMuted, 2f); text = label },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = scale.px(10)
            })
    }

/** Up to three hints in a row, 28 px apart. */
fun remoteHints(context: Context, scale: Scale, fonts: Fonts, vararg hints: Pair<String, String>) =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        hints.forEachIndexed { i, (key, label) ->
            addView(remoteHint(context, scale, fonts, key, label),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = scale.px(28)
                })
        }
    }
