package com.aditya.cabletv.home

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home screen (design-spec S1; R-6 to R-13): centred title and clock, the Portal hero with
 * the focused app's logo, the info column, the eight-channel row and the GuideBar. Layout values
 * are design px on the 1920 × 1080 canvas, matching the mockup's Main.dc.html.
 */
class HomeActivity : Activity() {

    private lateinit var scale: Scale
    private lateinit var fonts: Fonts
    private lateinit var catalog: Catalog
    private lateinit var portal: PortalView
    private lateinit var infoPlate: TextView
    private lateinit var infoName: TextView
    private lateinit var infoLine: TextView
    private lateinit var guideNumber: TextView
    private lateinit var guideName: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private val tiles = mutableListOf<ChannelTileView>()
    private var apps: Map<String, AppEntry> = emptyMap()
    private val ticker = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var boot: BootOverlay
    private var focusIndex = 0
    private var launching = false
    private lateinit var prefs: Prefs
    private lateinit var settingsButton: TextView
    private var builtRevision = -1
    private lateinit var flash: StaticFlash

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = recreate()
    }

    private data class Slot(val number: Int, val name: String, val description: String, val pkg: String?)

    private lateinit var slots: List<Slot>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scale = Scale(this)
        fonts = Fonts(this)
        catalog = Catalog(this)
        prefs = Prefs(this)
        Tokens.apply(prefs.theme)
        builtRevision = prefs.revision

        root = FrameLayout(this).apply { setBackgroundColor(Tokens.ground); clipChildren = false }
        boot = BootOverlay(this, scale, fonts)
        root.addView(boot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        boot.show("STARTING UP")

        // Read the TV's apps off the main thread, then build the screen (R-16: boot screen covers this).
        Thread {
            val loaded = catalog.load()
            runOnUiThread { if (!isDestroyed) build(loaded) }
        }.start()

        registerReceiver(packageChanges, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        })
    }

    private fun build(loaded: Map<String, AppEntry>) {
        apps = loaded
        val others = apps.keys - prefs.homeSlots.toSet()
        slots = prefs.homeSlots.mapIndexed { i, pkg ->
            val known = Channels.primary.firstOrNull { it.pkg == pkg }
            Slot(i + 1, known?.name ?: apps[pkg]?.label ?: pkg, known?.description ?: "Promoted from Other Apps.", pkg)
        } + Slot(Channels.OTHER_APPS_NUMBER, "Other Apps", otherAppsLine(others), null)

        val content = FrameLayout(this).apply { clipChildren = false }
        buildHeader(content)
        buildHero(content)
        buildRow(content)
        buildGuideBar(content)
        root.addView(content, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        tiles.getOrNull(focusIndex)?.requestFocus()
        tick.run()
        flash = StaticFlash(this)
        root.addView(flash, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        boot.hide()
    }

    private fun otherAppsLine(others: Set<String>): String {
        val names = others.mapNotNull { apps[it]?.label }.sorted()
        val shown = names.take(4)
        return if (names.size > shown.size) "${shown.joinToString(", ")} and ${names.size - shown.size} more." else shown.joinToString(", ") + "."
    }

    // ---- Header: centred title, clock top right (R-11) ----
    private fun buildHeader(root: FrameLayout) {
        val title = TextView(this).apply {
            style(scale, fonts.display, 96, Tokens.ink, 2f)
            // Custom title (Settings → Look); the last word is green, like "Cable" in the default.
            val words = prefs.homeTitle.split(" ")
            val t = words.joinToString("  ")
            text = SpannableString(t).apply {
                if (words.size > 1) setSpan(ForegroundColorSpan(Tokens.focus), t.lastIndexOf(words.last()), t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            gravity = Gravity.CENTER_HORIZONTAL
        }
        root.addView(title, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = scale.px(54)
        })
        // Visible way into Settings: the realme remote has no MENU key (Q-017). ▲ from the row reaches it.
        settingsButton = TextView(this).apply {
            style(scale, fonts.osd, 34, Tokens.ink, 2f)
            text = "⚙ SETTINGS"
            isFocusable = true
            gravity = Gravity.CENTER
            setPadding(scale.px(18), scale.px(8), scale.px(18), scale.px(8))
            fun paint(has: Boolean) {
                background = GradientDrawable().apply {
                    cornerRadius = scale.pxF(10f)
                    setColor(if (has) Tokens.focus else Tokens.surface)
                    setStroke(scale.px(2), if (has) Tokens.focus else Tokens.bezelEdge)
                }
                setTextColor(if (has) Tokens.onFocus else Tokens.ink)
            }
            paint(false)
            setOnFocusChangeListener { _, has -> paint(has) }
            setOnClickListener { openSettings() }
        }
        root.addView(settingsButton, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = scale.px(176); marginEnd = scale.px(96)
        })
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        clock = TextView(this).apply { style(scale, fonts.osd, 64, Tokens.portalInk) }
        date = TextView(this).apply { style(scale, fonts.osd, 30, Tokens.inkMuted, 2f) }
        box.addView(clock); box.addView(date)
        root.addView(box, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = scale.px(54); marginEnd = scale.px(96)
        })
    }

    // ---- Hero: portal + info column (R-9, R-10) ----
    private fun buildHero(root: FrameLayout) {
        portal = PortalView(this, scale, fonts.display).apply { clipChildren = false; reducedMotion = prefs.reducedMotion }
        root.addView(portal, FrameLayout.LayoutParams(scale.px(680), scale.px(480)).apply {
            leftMargin = scale.px(96); topMargin = scale.px(176)
        })

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        val plateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        infoPlate = TextView(this).apply {
            style(scale, fonts.osd, 40, Tokens.onPortal, 2f)
            background = GradientDrawable().apply { cornerRadius = scale.pxF(6f); setColor(Tokens.portal) }
            setPadding(scale.px(12), scale.px(6), scale.px(12), scale.px(6))
        }
        val nowTuned = TextView(this).apply { style(scale, fonts.osd, 30, Tokens.inkMuted, 2f); text = "NOW TUNED" }
        plateRow.addView(infoPlate)
        plateRow.addView(nowTuned, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = scale.px(16) })
        infoName = TextView(this).apply { style(scale, fonts.display, 96, Tokens.ink, 2f) }
        infoLine = TextView(this).apply { style(scale, fonts.body, 26, Tokens.inkMuted); maxWidth = scale.px(760) }
        val okHint = remoteHint(this, scale, fonts, "OK", "TUNE IN")
        listOf(plateRow, infoName, infoLine, okHint).forEachIndexed { i, v ->
            col.addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = scale.px(32)
            })
        }
        root.addView(col, FrameLayout.LayoutParams(scale.px(908), scale.px(480)).apply {
            leftMargin = scale.px(96 + 680 + 64); topMargin = scale.px(176)
        })
    }

    // ---- The eight-channel row (R-6, R-7, R-8, R-13) ----
    private fun buildRow(root: FrameLayout) {
        val heading = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        heading.addView(TextView(this).apply { style(scale, fonts.bodyBold, 32, Tokens.ink); text = "Channels" })
        heading.addView(TextView(this).apply {
            style(scale, fonts.bodyBold, 22, Tokens.inkMuted, 0.5f)
            text = "7 primary · everything else in CH 099"
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = scale.px(24) })
        root.addView(heading, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = scale.px(96); topMargin = scale.px(694)
        })

        tiles.clear()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val tileW = scale.px(195)
        slots.forEachIndexed { i, slot ->
            val tile = ChannelTileView(this, scale, fonts, tileW)
            val app = slot.pkg?.let { apps[it] }
            if (slot.pkg == null) {
                tile.bind(slot.number, slot.name, null, null, textOnly = "${apps.size - prefs.homeSlots.count { apps.containsKey(it) }} more")
            } else {
                tile.bind(slot.number, slot.name, app?.banner, app?.icon)
            }
            tile.setOnFocusChangeListener { _, has ->
                tile.setFocused(has)
                if (has) { focusIndex = i; showChannel(slot) }
            }
            tile.setOnClickListener {
                when {
                    app != null -> launch(app, slot)
                    slot.pkg == null -> startActivity(Intent(this, OtherAppsActivity::class.java))
                }
            }
            tiles += tile
            row.addView(tile, LinearLayout.LayoutParams(tileW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) marginStart = scale.px(24)
            })
        }
        root.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = scale.px(96); topMargin = scale.px(752)
        })
    }

    // ---- GuideBar (R-12) ----
    private fun buildGuideBar(root: FrameLayout) {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Tokens.surface)
            setPadding(scale.px(96), scale.px(16), scale.px(96), scale.px(30))
        }
        guideNumber = TextView(this).apply { style(scale, fonts.osd, 64, Tokens.portalInk) }
        guideName = TextView(this).apply { style(scale, fonts.bodyBold, 52, Tokens.ink); maxLines = 1 }
        val hints = remoteHints(this, scale, fonts, "◀ ▶" to "FLIP", "OK" to "TUNE IN", "▲" to "SETTINGS")
        bar.addView(guideNumber)
        bar.addView(guideName, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = scale.px(48) })
        bar.addView(hints)
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, scale.px(118), Gravity.BOTTOM))
        root.addView(View(this).apply { setBackgroundColor(Tokens.bezelEdge) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, scale.px(2), Gravity.BOTTOM).apply { bottomMargin = scale.px(118) })
    }

    private fun showChannel(slot: Slot) {
        val num = "%03d".format(slot.number)
        infoPlate.text = "CH $num"
        infoName.text = slot.name
        infoLine.text = slot.description
        guideNumber.text = "CH $num"
        guideName.text = slot.name
        val sticker = prefs.logoStyle == Prefs.LOGO_STICKERS
        portal.show(slot.pkg?.let { catalog.logo(it, sticker) }, slot.name, sticker)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!::clock.isInitialized) return // the screen is still being built behind the boot screen
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date.text = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(now).uppercase(Locale.ENGLISH)
            ticker.postDelayed(this, 15_000)
        }
    }

    /** Opens an app; if it hasn't covered us within 400 ms, the boot screen fills the gap (R-16). */
    private fun launch(app: AppEntry, slot: Slot) {
        launching = true
        ticker.postDelayed({
            if (launching) boot.show("TUNING CH %03d · %s".format(slot.number, slot.name.uppercase()))
        }, 400)
        if (prefs.staticFx) flash.play(prefs.reducedMotion) { startActivity(app.launch) } else startActivity(app.launch)
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    override fun onResume() {
        super.onResume()
        launching = false
        if (::prefs.isInitialized && prefs.revision != builtRevision) { recreate(); return }
        if (::flash.isInitialized) flash.reset()
        if (::boot.isInitialized && tiles.isNotEmpty()) boot.hideNow()
        tick.run()
    }

    override fun onPause() { super.onPause(); ticker.removeCallbacks(tick) }

    override fun onStop() { super.onStop(); launching = false }

    override fun onDestroy() {
        unregisterReceiver(packageChanges)
        super.onDestroy()
    }

    // A launcher has nowhere to go back to: Back on the home screen does nothing.
    @Deprecated("Activity.onBackPressed is fine on API 30")
    override fun onBackPressed() = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Up/Down have nothing to move to on this screen (design-spec S1 focus map).
        if (::settingsButton.isInitialized) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { settingsButton.requestFocus(); return true }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { tiles.getOrNull(focusIndex)?.requestFocus(); return true }
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) { openSettings(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
