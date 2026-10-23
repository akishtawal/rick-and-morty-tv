package com.aditya.cabletv.home

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable

/** One launchable app on the TV (R-7, R-15). */
class AppEntry(
    val pkg: String,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable?,
    val launch: Intent,
)

/** A home-row slot: a fixed channel number and the copy shown beside the portal (D-010, design-spec S1). */
data class Channel(val number: Int, val pkg: String, val name: String, val description: String)

object Channels {
    val primary = listOf(
        Channel(1, "com.netflix.ninja", "Netflix", "Picks up where you left off."),
        Channel(2, "com.google.android.youtube.tv", "YouTube", "Subscriptions and the long tail of the multiverse."),
        Channel(3, "com.amazon.amazonvideo.livingroom", "Prime Video", "Films and series from Amazon."),
        Channel(4, "in.startv.hotstar", "JioHotstar", "Cricket, Disney and Indian originals."),
        Channel(5, "com.mubi", "MUBI", "One hand-picked film a day."),
        Channel(6, "org.videolan.vlc", "VLC", "Files from the Mac and the network."),
        Channel(7, "tv.airtel.xstream.tvapp", "Airtel Xstream", "Live TV channels from this dimension."),
    )
    const val OTHER_APPS_NUMBER = 99
}

/** Reads the TV's launchable apps straight from PackageManager, banners included (R-7, R-15). */
class Catalog(private val context: Context) {

    fun load(): Map<String, AppEntry> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .mapNotNull { ri ->
                val ai = ri.activityInfo
                val launch = pm.getLeanbackLaunchIntentForPackage(ai.packageName) ?: return@mapNotNull null
                AppEntry(
                    pkg = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm),
                    icon = ai.loadIcon(pm),
                    launch = launch,
                )
            }
            .associateBy { it.pkg }
    }

    /**
     * Transparent wordmark bundled under assets/logos (D-017), shown on the portal's core.
     * Builds without bundled logos fall back to the app's own TV banner (or icon) from the system.
     */
    fun logo(pkg: String, sticker: Boolean = false): Bitmap? {
        val dir = if (sticker) "stickers" else "logos"
        return runCatching { context.assets.open("$dir/$pkg.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
            ?: systemArt(pkg)
    }

    private fun systemArt(pkg: String): Bitmap? = runCatching {
        val pm = context.packageManager
        val d = pm.getApplicationBanner(pkg) ?: pm.getApplicationIcon(pkg)
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 320
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 180
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            d.setBounds(0, 0, w, h)
            d.draw(android.graphics.Canvas(bmp))
        }
    }.getOrNull()
}
