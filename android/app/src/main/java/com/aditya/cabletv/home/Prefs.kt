package com.aditya.cabletv.home

import android.content.Context

/**
 * Launcher settings (R-21 to R-24, design-system "Settings" table). Plain SharedPreferences:
 * a handful of small values doesn't justify the DataStore dependency on this TV (tech-spec:
 * keep dependencies minimal). Every write bumps [revision] so open screens know to redraw.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cabletv", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", THEME_NIGHT)!!
        set(v) = put("theme", v)
    var logoStyle: String
        get() = sp.getString("logoStyle", LOGO_CORE)!!
        set(v) = put("logoStyle", v)
    var reducedMotion: Boolean
        get() = sp.getBoolean("reducedMotion", false)
        set(v) = put("reducedMotion", v)
    var staticFx: Boolean
        get() = sp.getBoolean("staticFx", true)
        set(v) = put("staticFx", v)

    /** The seven home-row packages, in slot order; channel numbers follow the slot (R-23). */
    var homeSlots: List<String>
        get() = sp.getString("homeSlots", null)?.split(",")?.filter { it.isNotBlank() }
            ?.takeIf { it.size == 7 } ?: Channels.primary.map { it.pkg }
        set(v) = put("homeSlots", v.joinToString(","))

    /** Home screen title (Settings → Look → Home title); letters, digits, spaces, apostrophes, max [TITLE_MAX]. */
    var homeTitle: String
        get() = sp.getString("homeTitle", DEFAULT_TITLE)!!.ifBlank { DEFAULT_TITLE }
        set(v) = put("homeTitle", cleanTitle(v).ifBlank { DEFAULT_TITLE })

    // Screensaver settings: stored now, used by the screensaver in M5/M6.
    var ssScenes: Set<String>
        get() = sp.getStringSet("ssScenes", null) ?: setOf(SCENE_PORTAL, SCENE_SURF, SCENE_OFFAIR)
        set(v) = put("ssScenes", v)
    var ssRotateMin: Int
        get() = sp.getInt("ssRotateMin", 3)
        set(v) = put("ssRotateMin", v)
    var ssStartAfterMin: Int
        get() = sp.getInt("ssStartAfterMin", 10)
        set(v) = put("ssStartAfterMin", v)
    var ssPhotos: String
        get() = sp.getString("ssPhotos", PHOTOS_MAC)!!
        set(v) = put("ssPhotos", v)
    var ssClock: Boolean
        get() = sp.getBoolean("ssClock", true)
        set(v) = put("ssClock", v)
    var ssWeather: Boolean
        get() = sp.getBoolean("ssWeather", true)
        set(v) = put("ssWeather", v)

    /** City for the screensaver weather line (Q-014); empty = weather hidden, never faked. */
    var weatherCity: String
        get() = sp.getString("weatherCity", DEFAULT_CITY)!!
        set(v) = put("weatherCity", v)

    /** Last good weather line and when it was fetched (60-minute cache). */
    var weatherLine: String
        get() = sp.getString("weatherLine", "")!!
        set(v) = sp.edit().putString("weatherLine", v).putLong("weatherAt", System.currentTimeMillis()).apply()
    val weatherAt: Long get() = sp.getLong("weatherAt", 0)

    /**
     * True while the user has stepped out to the stock launcher (hold Home, or Settings → About);
     * the Home service stops bouncing back until the next short Home press. Not a visual setting,
     * so it doesn't bump [revision].
     */
    var stockPaused: Boolean
        get() = sp.getBoolean("stockPaused", false)
        set(v) = sp.edit().putBoolean("stockPaused", v).apply()

    val revision: Int get() = sp.getInt("rev", 0)

    private fun put(key: String, value: Any) {
        val e = sp.edit()
        when (value) {
            is String -> e.putString(key, value)
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") e.putStringSet(key, value as Set<String>)
        }
        e.putInt("rev", revision + 1).apply()
    }

    companion object {
        const val THEME_NIGHT = "night"
        const val THEME_LIVING = "living-room"
        const val LOGO_CORE = "core"
        const val LOGO_STICKERS = "stickers"
        const val SCENE_PORTAL = "portal"
        const val SCENE_SURF = "surf"
        const val SCENE_OFFAIR = "offair"
        const val PHOTOS_GOOGLE = "google"
        const val PHOTOS_MAC = "mac"
        const val PHOTOS_NONE = "none"
        /** Aditya's city for the weather line (D-024). */
        const val DEFAULT_CITY = "Ahmedabad"
        const val STOCK_LAUNCHER = "com.google.android.tvlauncher"
        const val DEFAULT_TITLE = "Interdimensional Cable"
        /** Fits the centred 96 px header at 1080p without crowding the clock. */
        const val TITLE_MAX = 24

        /** Get Schwifty has only letters, digits and spaces (D-013), so drop anything else. */
        /** Opens the stock Android TV home and pauses our bounce-back (Q-018: one press to safety). */
        fun openStockHome(context: Context) {
            Prefs(context).stockPaused = true
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .setClassName(STOCK_LAUNCHER, "$STOCK_LAUNCHER.MainActivity")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        /** Characters a title may hold. Get Schwifty has no apostrophe; Android draws it in the system font. */
        fun isTitleChar(c: Char) = c.isLetterOrDigit() || c == ' ' || c == '\'' || c == '’'

        fun cleanTitle(raw: String) = raw.filter(::isTitleChar).replace('’', '\'')
            .replace(Regex(" +"), " ").trim().take(TITLE_MAX)
    }
}
