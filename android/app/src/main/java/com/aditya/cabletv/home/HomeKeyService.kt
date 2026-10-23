package com.aditya.cabletv.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Home takeover (D-025, R-4). The stock launcher's HOME filter has system priority 2, which a
 * sideloaded app can't beat, so instead this service catches the remote's Home key and re-opens
 * our screen whenever the stock launcher comes to the front (Back, an app closing, boot).
 *
 * One press to safety (Q-018): holding Home for [LONG_PRESS_MS] opens the stock launcher and
 * pauses the bounce-back until the next short Home press.
 */
class HomeKeyService : AccessibilityService() {

    private val prefs by lazy { Prefs(this) }

    override fun onServiceConnected() {
        prefs.stockPaused = false // after a reboot, start on our home again
        openHome()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) {
            if (event.eventTime - event.downTime >= LONG_PRESS_MS) {
                Prefs.openStockHome(this)
            } else {
                prefs.stockPaused = false
                openHome()
            }
        }
        return true // consume both down and up so the stock launcher never sees Home
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.packageName == Prefs.STOCK_LAUNCHER &&
            !prefs.stockPaused
        ) {
            openHome()
        }
    }

    override fun onInterrupt() = Unit

    private fun openHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    private companion object {
        const val LONG_PRESS_MS = 2_000L
    }
}
