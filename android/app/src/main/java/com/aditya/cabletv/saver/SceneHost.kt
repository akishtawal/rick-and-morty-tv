package com.aditya.cabletv.saver

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.aditya.cabletv.home.Fonts
import com.aditya.cabletv.home.Prefs
import com.aditya.cabletv.home.Scale
import java.util.Date

/**
 * Rotates the enabled screensaver scenes (R-18, D-021): Portal idle → Channel surf → Off the air,
 * skipping disabled ones, switching every Settings → Screensaver → "Switch scene every" minutes.
 * Shared by the DreamService and Preview.
 */
class SceneHost(context: Context) : FrameLayout(context) {

    private val scale = Scale(context)
    private val fonts = Fonts(context)
    private val prefs = Prefs(context)
    private val handler = Handler(Looper.getMainLooper())
    private var current: Scene? = null
    private var index = 0

    private val weatherClient = Weather(prefs)
    private val order: List<String> = listOf(Prefs.SCENE_PORTAL, Prefs.SCENE_SURF, Prefs.SCENE_OFFAIR)
        .filter { it in prefs.ssScenes }
        .ifEmpty { listOf(Prefs.SCENE_PORTAL) }

    private val clock = object : Runnable {
        override fun run() {
            weatherClient.refreshIfStale()
            current?.tick(Date())
            handler.postDelayed(this, 15_000)
        }
    }
    private val rotate = object : Runnable {
        override fun run() {
            index = (index + 1) % order.size
            show(order[index])
            handler.postDelayed(this, prefs.ssRotateMin * 60_000L)
        }
    }

    /** [firstScene] lets Preview open on a given scene (also handy for testing). */
    fun start(firstScene: String? = null) {
        index = order.indexOf(firstScene).coerceAtLeast(0)
        show(order[index])
        clock.run()
        if (order.size > 1) handler.postDelayed(rotate, prefs.ssRotateMin * 60_000L)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        current?.stop()
    }

    private fun show(id: String) {
        current?.stop()
        removeAllViews()
        val reduced = prefs.reducedMotion
        val weather = { if (prefs.ssWeather) weatherClient.line() else null }
        val scene = when (id) {
            Prefs.SCENE_SURF -> ChannelSurfScene(context, scale, fonts, reduced, prefs.ssClock, prefs.ssPhotos != Prefs.PHOTOS_NONE, weather)
            Prefs.SCENE_OFFAIR -> OffAirScene(context, scale, fonts, reduced, prefs.ssClock, weather)
            else -> PortalIdleScene(context, scale, fonts, reduced, prefs.ssClock, weather)
        }
        addView(scene, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        current = scene
        scene.tick(Date())
    }
}
