package com.aditya.cabletv.saver

import android.app.Activity
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.KeyEvent

/**
 * The TV screensaver (R-17 to R-20): picked in TV Settings → Device Preferences → Screen saver.
 * Non-interactive, so any key wakes the TV back to where it was.
 */
class CableDream : DreamService() {
    private var host: SceneHost? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        host = SceneHost(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        host?.start()
    }

    override fun onDreamingStopped() {
        host?.stop()
        super.onDreamingStopped()
    }
}

/** Settings → Screensaver → Preview: the same scenes in an activity; any key closes it. */
class ScreensaverPreviewActivity : Activity() {
    private lateinit var host: SceneHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = SceneHost(this)
        setContentView(host)
    }

    override fun onResume() { super.onResume(); host.start(intent.getStringExtra("scene")) }
    override fun onPause() { host.stop(); super.onPause() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }
}
