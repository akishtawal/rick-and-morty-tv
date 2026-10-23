package com.aditya.cabletv.home

import android.app.Activity
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
 * Settings (design-spec S4; R-21 to R-25), opened with MENU. Sections on the left, rows of
 * option pills on the right; every change is saved and applied at once. Also hosts the
 * "promote to main 7" flow from Other Apps (extra [EXTRA_PROMOTE]).
 */
class SettingsActivity : Activity() {

    private lateinit var scale: Scale
    private lateinit var fonts: Fonts
    private lateinit var prefs: Prefs
    private lateinit var apps: Map<String, AppEntry>
    private lateinit var nav: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var scroller: ScrollView
    private var section = SECTION_LOOK
    private var swapSlot = -1          // Home channels: slot whose app is being chosen
    private var promotePkg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scale = Scale(this)
        fonts = Fonts(this)
        prefs = Prefs(this)
        Tokens.apply(prefs.theme)
        apps = Catalog(this).load()
        promotePkg = intent.getStringExtra(EXTRA_PROMOTE)?.takeIf { apps.containsKey(it) }
        section = savedInstanceState?.getString("section")
            ?: intent.getStringExtra(EXTRA_SECTION)
            ?: if (promotePkg != null) SECTION_HOME else SECTION_LOOK

        val root = FrameLayout(this).apply { setBackgroundColor(Tokens.ground) }
        root.addView(TextView(this).apply { style(scale, fonts.display, 72, Tokens.ink, 2f); text = "Settings" },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = scale.px(96); topMargin = scale.px(54)
            })
        root.addView(TextView(this).apply {
            style(scale, fonts.osd, 30, Tokens.inkMuted, 2f)
            text = "CABLE BOX SETUP · CHANGES APPLY STRAIGHT AWAY"
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END).apply {
            topMargin = scale.px(78); marginEnd = scale.px(96)
        })

        nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(nav, FrameLayout.LayoutParams(scale.px(420), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = scale.px(96); topMargin = scale.px(190)
        })

        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(panel) }
        root.addView(scroller, FrameLayout.LayoutParams(scale.px(1224), scale.px(984 - 190 - 24)).apply {
            leftMargin = scale.px(600); topMargin = scale.px(190)
        })

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setBackgroundColor(Tokens.surface)
            setPadding(scale.px(96), 0, scale.px(96), scale.px(16))
            addView(remoteHints(context, scale, fonts, "▲ ▼" to "MOVE", "OK" to "CHANGE", "BACK" to "HOME"))
        }
        root.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, scale.px(96), Gravity.BOTTOM))
        root.addView(View(this).apply { setBackgroundColor(Tokens.bezelEdge) },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, scale.px(2), Gravity.BOTTOM).apply { bottomMargin = scale.px(96) })

        setContentView(root)
        buildNav()
        buildPanel(focusFirst = promotePkg != null)
        if (promotePkg == null) nav.getChildAt(SECTIONS.indexOf(section))?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("section", section)
    }

    @Deprecated("Activity.onBackPressed is fine on API 30")
    override fun onBackPressed() {
        when {
            swapSlot >= 0 -> { swapSlot = -1; buildPanel(focusFirst = true) }
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    // ---- Section list ----
    private fun buildNav() {
        nav.removeAllViews()
        val hints = mapOf(
            SECTION_LOOK to "Theme, portal, motion",
            SECTION_HOME to "The seven on the main row",
            SECTION_SAVER to "Scenes, timing, photos",
            SECTION_ABOUT to "TV settings, this TV, credits",
        )
        SECTIONS.forEachIndexed { i, id ->
            val selected = id == section
            val b = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = true
                setPadding(scale.px(24), scale.px(18), scale.px(24), scale.px(18))
                background = pillBg(if (selected) Tokens.focus else Tokens.surface, scale.pxF(14f))
                addView(TextView(context).apply {
                    style(scale, fonts.bodyBold, 32, if (selected) Tokens.onFocus else Tokens.ink); text = TITLES.getValue(id)
                })
                addView(TextView(context).apply {
                    style(scale, fonts.bodyBold, 22, if (selected) Tokens.onFocus else Tokens.inkMuted, 0.5f); text = hints.getValue(id)
                })
                setOnFocusChangeListener { v, has -> v.foreground = if (has) ring(14f) else null }
                setOnClickListener { switchTo(id) }
            }
            nav.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (i > 0) topMargin = scale.px(12)
            })
        }
    }

    private fun switchTo(id: String) {
        section = id
        swapSlot = -1
        promotePkg = null
        buildNav()
        buildPanel(focusFirst = true)
    }

    // ---- Options panel ----
    private data class Opt(val label: String, val selected: Boolean, val onPick: () -> Unit)
    private data class Row(val label: String, val desc: String, val opts: List<Opt>)

    private fun buildPanel(focusFirst: Boolean, focusRow: Int = -1, focusCol: Int = -1) {
        panel.removeAllViews()
        when (section) {
            SECTION_LOOK -> rows(lookRows(), focusFirst, focusRow, focusCol)
            SECTION_HOME -> if (swapSlot >= 0) appPicker() else rows(homeRows(), focusFirst, focusRow, focusCol)
            SECTION_SAVER -> rows(saverRows(), focusFirst, focusRow, focusCol)
            else -> rows(aboutRows(), focusFirst, focusRow, focusCol)
        }
    }

    private fun lookRows() = listOf(
        Row("Theme", "Night for a dim room, Living room for daytime.", listOf(
            Opt("Night", prefs.theme == Prefs.THEME_NIGHT) { setTheme(Prefs.THEME_NIGHT) },
            Opt("Living room", prefs.theme == Prefs.THEME_LIVING) { setTheme(Prefs.THEME_LIVING) },
        )),
        Row("Home title", "\"${prefs.homeTitle}\" · up to ${Prefs.TITLE_MAX} letters, numbers, spaces and apostrophes.", listOf(
            Opt("Edit", false) { editTitle() },
            Opt("Reset", prefs.homeTitle == Prefs.DEFAULT_TITLE) { prefs.homeTitle = Prefs.DEFAULT_TITLE },
        )),
        Row("Portal logo style", "How the focused app shows up inside the portal.", listOf(
            Opt("Glowing core", prefs.logoStyle == Prefs.LOGO_CORE) { prefs.logoStyle = Prefs.LOGO_CORE },
            Opt("Stickers", prefs.logoStyle == Prefs.LOGO_STICKERS) { prefs.logoStyle = Prefs.LOGO_STICKERS },
        )),
        Row("Motion", "Reduced stops the spinning portal and pop-ins.", listOf(
            Opt("Full", !prefs.reducedMotion) { prefs.reducedMotion = false },
            Opt("Reduced", prefs.reducedMotion) { prefs.reducedMotion = true },
        )),
        Row("Static between channels", "The snow flash when you open an app.", listOf(
            Opt("On", prefs.staticFx) { prefs.staticFx = true },
            Opt("Off", !prefs.staticFx) { prefs.staticFx = false },
        )),
    )

    /** Opens the TV keyboard to rename the home title; filters to what the display font can draw. */
    private fun editTitle() {
        val input = android.widget.EditText(this).apply {
            setText(prefs.homeTitle)
            setSelection(text.length)
            setSingleLine()
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(Prefs.TITLE_MAX),
                android.text.InputFilter { src, start, end, _, _, _ ->
                    // Only intervene when something must go; rewriting every keystroke breaks the keyboard's composing text.
                    val kept = src.subSequence(start, end).filter { Prefs.isTitleChar(it) }
                    if (kept.length == end - start) null else kept
                },
            )
        }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Home title")
            .setMessage("Up to ${Prefs.TITLE_MAX} letters, numbers, spaces and apostrophes. The last word turns green.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                prefs.homeTitle = input.text.toString()
                buildPanel(focusFirst = false, focusRow = 1, focusCol = 0)
            }
            .setNeutralButton("Reset") { _, _ ->
                prefs.homeTitle = Prefs.DEFAULT_TITLE
                buildPanel(focusFirst = false, focusRow = 1, focusCol = 0)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setTheme(theme: String) {
        if (prefs.theme == theme) return
        prefs.theme = theme
        Tokens.apply(theme)
        intent.putExtra(EXTRA_SECTION, SECTION_LOOK)
        recreate() // repaint this screen in the new palette; Home redraws when it comes back
    }

    private fun homeRows(): List<Row> {
        val slots = prefs.homeSlots
        val promote = promotePkg
        return slots.mapIndexed { i, pkg ->
            val name = channelName(pkg)
            if (promote != null) {
                Row("CH %03d · %s".format(i + 1, name), "Replace with ${channelName(promote)}?", listOf(
                    Opt("Replace", false) {
                        prefs.homeSlots = slots.toMutableList().also { it[i] = promote }
                        finish()
                    },
                ))
            } else {
                Row("CH %03d · %s".format(i + 1, name), "", listOf(
                    Opt("Move up", false) { move(i, -1) },
                    Opt("Move down", false) { move(i, +1) },
                    Opt("Swap app", false) { swapSlot = i; buildPanel(focusFirst = true) },
                ))
            }
        }
    }

    private fun move(i: Int, by: Int) {
        val slots = prefs.homeSlots.toMutableList()
        val j = i + by
        if (j !in slots.indices) return
        slots[i] = slots[j].also { slots[j] = slots[i] }
        prefs.homeSlots = slots
        buildPanel(focusFirst = false, focusRow = j, focusCol = if (by < 0) 0 else 1)
    }

    /** Swap app: every launchable app as a pill; OK puts it in [swapSlot]. */
    private fun appPicker() {
        val slot = swapSlot
        panel.addView(TextView(this).apply {
            style(scale, fonts.bodyBold, 32, Tokens.ink)
            text = "Choose an app for CH %03d".format(slot + 1)
        })
        val grid = GridLayout(this).apply { columnCount = 3 }
        val current = prefs.homeSlots
        apps.values.sortedBy { it.label.lowercase() }.forEach { app ->
            val inUse = app.pkg in current
            grid.addView(pill(app.label, selected = current[slot] == app.pkg) {
                if (!inUse || current[slot] == app.pkg) {
                    prefs.homeSlots = current.toMutableList().also { it[slot] = app.pkg }
                }
                swapSlot = -1
                buildPanel(focusFirst = false, focusRow = slot, focusCol = 2)
            }.apply { if (inUse && current[slot] != app.pkg) alpha = 0.4f },
                GridLayout.LayoutParams().apply { setMargins(0, scale.px(14), scale.px(14), 0) })
        }
        panel.addView(grid)
        grid.getChildAt(0)?.requestFocus()
    }

    private fun saverRows(): List<Row> {
        val scenes = prefs.ssScenes
        fun scene(id: String, label: String): Opt {
            val on = id in scenes
            return Opt((if (on) "✓ " else "") + label, on) {
                val next = if (on) scenes - id else scenes + id
                if (next.isNotEmpty()) prefs.ssScenes = next // keep at least one scene
            }
        }
        fun minutes(values: List<Int>, current: Int, set: (Int) -> Unit) = values.map { m -> Opt("$m min", m == current) { set(m) } }
        return listOf(
            Row("Preview", "See the screensaver now. Any key comes back here.", listOf(
                Opt("Preview now", false) {
                    startActivity(android.content.Intent(this, com.aditya.cabletv.saver.ScreensaverPreviewActivity::class.java))
                },
            )),
            Row("Scenes", "Pick any; they take turns.", listOf(
                scene(Prefs.SCENE_PORTAL, "Portal idle"), scene(Prefs.SCENE_SURF, "Channel surf"), scene(Prefs.SCENE_OFFAIR, "Off the air"),
            )),
            Row("Switch scene every", "How long each scene stays up.", minutes(listOf(1, 3, 5, 10), prefs.ssRotateMin) { prefs.ssRotateMin = it }),
            Row("Start after", "Idle time before the screensaver starts.", minutes(listOf(5, 10, 15, 30), prefs.ssStartAfterMin) { prefs.ssStartAfterMin = it }),
            Row("Photos for Channel surf", "Your pictures, shown between the wallpapers.", listOf(
                Opt("Google Photos", prefs.ssPhotos == Prefs.PHOTOS_GOOGLE) { prefs.ssPhotos = Prefs.PHOTOS_GOOGLE },
                Opt("Mac folder", prefs.ssPhotos == Prefs.PHOTOS_MAC) { prefs.ssPhotos = Prefs.PHOTOS_MAC },
                Opt("None", prefs.ssPhotos == Prefs.PHOTOS_NONE) { prefs.ssPhotos = Prefs.PHOTOS_NONE },
            )),
            Row("Clock", "Time inside the portal.", listOf(
                Opt("On", prefs.ssClock) { prefs.ssClock = true }, Opt("Off", !prefs.ssClock) { prefs.ssClock = false },
            )),
            Row("Date and weather", if (prefs.weatherCity.isEmpty()) "Under the clock. Weather needs a city (still to be set)." else "Under the clock. Weather for ${prefs.weatherCity}, from Open-Meteo.", listOf(
                Opt("On", prefs.ssWeather) { prefs.ssWeather = true }, Opt("Off", !prefs.ssWeather) { prefs.ssWeather = false },
            )),
        )
    }

    private fun aboutRows() = listOf(
        Row("Stock home", "Back to the original Android TV home. Shortcut: hold Home for 2 seconds. A short Home press returns here.", listOf(
            Opt("Open stock home", false) { Prefs.openStockHome(this) },
        )),
        Row("TV settings", "The TV's own Android settings: screensaver choice, network, apps, picture and sound.", listOf(
            Opt("Open TV settings", false) {
                runCatching { startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) }
            },
        )),
        Row("This TV", "realme SLED 55 inch 4K · Android TV 11 · 1.35 GB RAM", emptyList()),
        Row("Launcher", "Interdimensional Cable · version ${packageManager.getPackageInfo(packageName, 0).versionName}", emptyList()),
        Row("Credits", "Get Schwifty font by jonizaak · app logos from Wikimedia Commons and the apps themselves", emptyList()),
    )

    private fun rows(rows: List<Row>, focusFirst: Boolean, focusRow: Int, focusCol: Int) {
        promotePkg?.let { pkg ->
            panel.addView(TextView(this).apply {
                style(scale, fonts.bodyBold, 32, Tokens.portalInk)
                text = "Put ${channelName(pkg)} on the main row: pick a channel to replace."
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = scale.px(14)
            })
        }
        rows.forEachIndexed { r, row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(scale.px(24), scale.px(18), scale.px(24), scale.px(18))
                background = pillBg(Tokens.surface, scale.pxF(14f))
            }
            val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            text.addView(TextView(this).apply { style(scale, fonts.bodyBold, 32, Tokens.ink); this.text = row.label })
            if (row.desc.isNotEmpty()) text.addView(TextView(this).apply {
                style(scale, fonts.bodyBold, 22, Tokens.inkMuted, 0.5f); this.text = row.desc
            })
            line.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.opts.forEachIndexed { c, opt ->
                val p = pill(opt.label, opt.selected) {
                    opt.onPick()
                    if (!isFinishing && (section == SECTION_LOOK || section == SECTION_SAVER)) {
                        buildPanel(focusFirst = false, focusRow = r, focusCol = c)
                    }
                }
                line.addView(p, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = scale.px(10)
                })
                if ((focusFirst && r == 0 && c == 0) || (r == focusRow && c == focusCol)) p.post { p.requestFocus() }
            }
            panel.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0 || promotePkg != null) topMargin = scale.px(14)
            })
        }
    }

    // ---- Pieces ----
    private fun pill(label: String, selected: Boolean, onPick: () -> Unit) = TextView(this).apply {
        style(scale, fonts.bodyBold, 24, if (selected) Tokens.onFocus else Tokens.ink)
        text = label
        isFocusable = true
        setPadding(scale.px(18), scale.px(12), scale.px(18), scale.px(12))
        background = pillBg(if (selected) Tokens.focus else Tokens.surfaceRaised, scale.pxF(999f), ringColor = if (selected) 0 else Tokens.bezelEdge)
        setOnFocusChangeListener { v, has -> v.foreground = if (has) ring() else null }
        setOnClickListener { onPick() }
    }

    private fun pillBg(color: Int, radius: Float, ringColor: Int = 0) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
        if (ringColor != 0) setStroke(scale.px(2), ringColor)
    }

    /** The 4 px focus ring (design-spec: Settings pill, focused). */
    private fun ring(radius: Float = 999f) = GradientDrawable().apply {
        cornerRadius = scale.pxF(radius)
        setColor(0)
        setStroke(scale.px(4), Tokens.focus)
    }

    private fun channelName(pkg: String) =
        Channels.primary.firstOrNull { it.pkg == pkg }?.name ?: apps[pkg]?.label ?: pkg

    companion object {
        const val EXTRA_PROMOTE = "promote"
        const val EXTRA_SECTION = "section"
        private const val SECTION_LOOK = "look"
        private const val SECTION_HOME = "home"
        private const val SECTION_SAVER = "saver"
        private const val SECTION_ABOUT = "about"
        private val SECTIONS = listOf(SECTION_LOOK, SECTION_HOME, SECTION_SAVER, SECTION_ABOUT)
        private val TITLES = mapOf(
            SECTION_LOOK to "Look", SECTION_HOME to "Home channels",
            SECTION_SAVER to "Screensaver", SECTION_ABOUT to "About",
        )
    }
}
