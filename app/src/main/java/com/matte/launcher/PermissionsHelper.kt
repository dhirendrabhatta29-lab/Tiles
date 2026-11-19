package com.matte.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Real-time permission inspection and one-tap system settings routing for Tiles Launcher. */
internal object PermissionsHelper {

    const val REQ_PERM_LOCATION = 201
    const val REQ_PERM_MEDIA = 202

    enum class Kind {
        DEFAULT_HOME,
        NOTIFICATIONS,
        LOCATION,
        WALLPAPER_MEDIA,
        ACCESSIBILITY
    }

    data class Status(
        val kind: Kind,
        val title: String,
        val reason: String,
        val granted: Boolean,
        val optional: Boolean,
        val actionLabel: String
    )

    fun allStatuses(context: Context): List<Status> = listOf(
        Status(
            kind = Kind.DEFAULT_HOME,
            title = "Default Home App",
            reason = "Sets Tiles as your primary Android launcher for instant Home and Recents transitions.",
            granted = isDefaultHome(context),
            optional = false,
            actionLabel = "Set Default"
        ),
        Status(
            kind = Kind.NOTIFICATIONS,
            title = "Notification Access",
            reason = "Enables live notification badges, media playback tiles, and dynamic tile priority movement.",
            granted = isNotificationAccessGranted(context),
            optional = false,
            actionLabel = "Allow Access"
        ),
        Status(
            kind = Kind.LOCATION,
            title = "Location Permission",
            reason = "Powers live local weather updates and forecasts on the Weather and Glance dynamic tiles.",
            granted = isLocationGranted(context),
            optional = false,
            actionLabel = "Grant"
        ),
        Status(
            kind = Kind.WALLPAPER_MEDIA,
            title = "Wallpaper & Media Access",
            reason = "Allows reading wallpaper/media assets for custom tile backgrounds and richer gradient themes.",
            granted = isWallpaperMediaGranted(context),
            optional = true,
            actionLabel = "Grant"
        ),
        Status(
            kind = Kind.ACCESSIBILITY,
            title = "Accessibility Service",
            reason = "Optional: enables double-tap to lock screen, smooth notification shade pull-down, and Recents gestures.",
            granted = isAccessibilityGranted(context),
            optional = true,
            actionLabel = "Configure"
        )
    )

    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = try {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            null
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabled = try {
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        } catch (_: Exception) {
            null
        } ?: return false
        val cn = ComponentName(context, TileNotificationListener::class.java)
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString())
    }

    fun isLocationGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isWallpaperMediaGranted(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityGranted(context: Context): Boolean {
        if (TilesAccessibilityService.isRunning()) return true
        val enabled = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Exception) {
            null
        } ?: return false
        val cn = ComponentName(context, TilesAccessibilityService::class.java)
        return enabled.contains(cn.flattenToString()) || enabled.contains(cn.flattenToShortString())
    }

    fun request(activity: Activity, kind: Kind) {
        when (kind) {
            Kind.DEFAULT_HOME -> openHomeSettings(activity)
            Kind.NOTIFICATIONS -> openNotificationSettings(activity)
            Kind.LOCATION -> {
                if (isLocationGranted(activity)) {
                    openAppDetails(activity)
                } else {
                    activity.requestPermissions(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ),
                        REQ_PERM_LOCATION
                    )
                }
            }
            Kind.WALLPAPER_MEDIA -> {
                if (isWallpaperMediaGranted(activity)) {
                    openAppDetails(activity)
                } else {
                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    activity.requestPermissions(arrayOf(perm), REQ_PERM_MEDIA)
                }
            }
            Kind.ACCESSIBILITY -> showAccessibilityDisclosure(activity)
        }
    }

    fun openHomeSettings(activity: Activity) {
        val launched = launchIntent(activity, Intent(Settings.ACTION_HOME_SETTINGS))
        if (!launched) {
            launchIntent(activity, Intent(Settings.ACTION_SETTINGS))
            Toast.makeText(activity, "Open Default Apps -> Home app", Toast.LENGTH_LONG).show()
        }
    }

    fun openNotificationSettings(activity: Activity) {
        if (!launchIntent(activity, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) {
            Toast.makeText(activity, "Open Notification Access in Android Settings", Toast.LENGTH_LONG).show()
        }
    }

    fun showAccessibilityDisclosure(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Optional Accessibility Service")
            .setMessage(
                "Tiles Launcher offers an optional Accessibility Service strictly to perform user-triggered gestures:\n\n" +
                    "• Double-tap empty home space to lock the screen\n" +
                    "• Pull down the notification shade smoothly\n" +
                    "• Open system Recents / Overview\n\n" +
                    "This service never reads screen text, keystrokes, or personal data. You can disable it anytime in Android Accessibility settings."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open Accessibility Settings") { _, _ ->
                if (!launchIntent(activity, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
                    Toast.makeText(activity, "Open Accessibility in Android Settings", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    fun openAppDetails(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null)
        )
        launchIntent(activity, intent)
    }

    private fun launchIntent(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}
