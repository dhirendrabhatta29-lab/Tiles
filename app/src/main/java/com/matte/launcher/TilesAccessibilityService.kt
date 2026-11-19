package com.matte.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * Optional Accessibility Service used strictly for launcher gestures:
 * double-tap to lock screen, expanding the notification shade, and smooth Recents trigger.
 * Never reads window contents or personal data.
 */
class TilesAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: this service only performs user-initiated global actions.
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: TilesAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }

        fun expandNotifications(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }

        fun openRecents(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }
}
