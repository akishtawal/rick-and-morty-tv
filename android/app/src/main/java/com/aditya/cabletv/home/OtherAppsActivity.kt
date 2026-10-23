package com.aditya.cabletv.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * CH 099 · Other Apps (design-spec S2; R-14, R-15): every launchable app that isn't one of the
 * seven, A to Z, numbered from CH 100, in a 7-column grid of real banners. Built from the TV's
 * app list each time, and rebuilt when an app is installed or removed.
 */
class OtherAppsActivity : Activity() {

    private lateinit var scale: Scale
    private lateinit var fonts: Fonts
    private lateinit var boot: BootOverlay
    private var launching = false
    private var focusedPkg: String? = null

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scale = Scale(this)
        fonts = Fonts(this)
        val prefs = Prefs(this)
        Tokens.apply(prefs.theme)

        val primary = prefs.homeSlots.toSet()
        val apps = Catalog(this).load().values
            .filter { it.pkg !in primary }
            .sortedBy { it.label.lowercase() }

        val root = FrameLayout(this).apply { setBackgroundColor(Tokens.ground) }

        // Header: active plate + title on the left, live count on the right.
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            style(scale, fonts.osd, 40, Tokens.onPortal, 2f)
            text = "CH 099"
            background = GradientDrawable().apply { cornerRadius = scale.pxF(6f); setColor(Tokens.portal) }
            setPadding(scale.px(12), scale.px(6), scale.px(12), scale.px(6))
        })
        head.addView(TextView(this).apply { style(scale, fonts.display, 48, Tokens.ink, 2f); text = "Other Apps" },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = scale.px(24)
            })
        root.addView(head, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = scale.px(96); topMargin = scale.px(54)
        })
        root.addView(TextView(this).apply {
            style(scale, fonts.osd, 30, Tokens.inkMuted, 2f)
            text = "${apps.size} CHANNELS · A–Z"
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = scale.px(66); marginEnd = scale.px(96)
        })

        // Grid: 7 columns across 1728 px, gaps 24 across and 44 down; scrolls if it outgrows the screen.
        val columns = 7
        val gapX = scale.px(24)
        val tileW = (scale.px(1728) - gapX * (columns - 1)) / columns
        val grid = GridLayout(this).apply { columnCount = columns; clipChildren = false; clipToPadding = false }
        apps.forEachIndexed { i, app ->
            val tile = ChannelTileView(this, scale, fonts, tileW)
            tile.bind(100 + i, app.label, app.banner, app.icon)
            tile.setOnFocusChangeListener { v, has ->
                tile.setFocused(has)
                if (has) { focusedPkg = app.pkg; v.parent?.requestChildFocus(v, v) }
            }
            tile.setOnClickListener {
                launching = true
                boot.postDelayed({ if (launching) boot.show("TUNING CH ${100 + i} · ${app.label.uppercase()}") }, 400)
                startActivity(app.launch)
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = tileW
                if (i % columns > 0) marginStart = gapX
                if (i >= columns) topMargin = scale.px(44)
            })
        }
        val scroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            setPadding(0, scale.px(24), 0, scale.px(60)) // room for the focus lift
            addView(grid)
        }
        root.addView(scroller, FrameLayout.LayoutParams(scale.px(1728) + scale.px(40), scale.px(984 - 146)).apply {
            leftMargin = scale.px(96) - scale.px(20); topMargin = scale.px(146)
        })
        grid.setPadding(scale.px(20), 0, scale.px(20), 0)

        // Footer bar with this screen's three hints.
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setBackgroundColor(Tokens.surface)
            setPadding(scale.px(96), 0, scale.px(96), scale.px(16))
            addView(remoteHints(context, scale, fonts, "OK" to "TUNE IN", "MENU" to "PROMOTE TO MAIN 7", "BACK" to "HOME"))
        }
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, scale.px(96), Gravity.BOTTOM))
        root.addView(View(this).apply { setBackgroundColor(Tokens.bezelEdge) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, scale.px(2), Gravity.BOTTOM).apply { bottomMargin = scale.px(96) })

        boot = BootOverlay(this, scale, fonts)
        root.addView(boot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setContentView(root)
        grid.getChildAt(0)?.requestFocus()

        registerReceiver(packageChanges, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        launching = false
        boot.hideNow()
    }

    override fun onStop() {
        super.onStop()
        launching = false // the app is on screen now; nothing left to cover
    }

    /** MENU on an app: put it on the main row (Settings → Home channels, promote flow). */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_MENU || keyCode == android.view.KeyEvent.KEYCODE_SETTINGS) {
            focusedPkg?.let { startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PROMOTE, it)) }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRestart() {
        super.onRestart()
        recreate() // the main row or theme may have changed in Settings
    }

    override fun onDestroy() {
        unregisterReceiver(packageChanges)
        super.onDestroy()
    }
}
