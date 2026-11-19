package com.matte.launcher

import android.content.SharedPreferences

/** Centralized typed access to all Tiles launcher preferences. */
internal class LauncherPrefs(val raw: SharedPreferences) {

    // Appearance & Theme
    var colorTheme: String
        get() = raw.getString("color_theme", LauncherTheme.MATTE.name) ?: LauncherTheme.MATTE.name
        set(v) = raw.edit().putString("color_theme", v).apply()

    var solidMode: Boolean
        get() = raw.getBoolean("solid_mode", false)
        set(v) = raw.edit().putBoolean("solid_mode", v).apply()

    var solidColor: Int
        get() = raw.getInt("solid_color", DEFAULT_SOLID_COLOR)
        set(v) = raw.edit().putInt("solid_color", v).apply()

    var useWallpaper: Boolean
        get() = raw.getBoolean("use_wallpaper", !raw.contains("home_background"))
        set(v) = raw.edit().putBoolean("use_wallpaper", v).apply()

    var materialYouEnabled: Boolean
        get() = raw.getBoolean("material_you_enabled", false)
        set(v) = raw.edit().putBoolean("material_you_enabled", v).apply()

    var wallpaperGradientEnabled: Boolean
        get() = raw.getBoolean("wallpaper_gradient_enabled", false)
        set(v) = raw.edit().putBoolean("wallpaper_gradient_enabled", v).apply()

    var blurIntensity: Int
        get() = raw.getInt("blur_intensity", 45).coerceIn(0, 100)
        set(v) = raw.edit().putInt("blur_intensity", v.coerceIn(0, 100)).apply()

    var tileCornerRadiusDp: Int
        get() {
            if (raw.contains("tile_corner_radius_dp")) {
                return raw.getInt("tile_corner_radius_dp", 14).coerceIn(0, 28)
            }
            return if (raw.getBoolean("rounded_corners", false)) 14 else 0
        }
        set(v) {
            val clamped = v.coerceIn(0, 28)
            raw.edit()
                .putInt("tile_corner_radius_dp", clamped)
                .putBoolean("rounded_corners", clamped > 0)
                .apply()
        }

    var tileSpacingDp: Int
        get() = raw.getInt("tile_spacing_dp", 6).coerceIn(2, 14)
        set(v) = raw.edit().putInt("tile_spacing_dp", v.coerceIn(2, 14)).apply()

    // Tiles & Layout
    var gridColumns: Int
        get() = raw.getInt("grid_columns", HomeGrid.COLUMNS_PER_VIEWPORT).coerceIn(3, 5)
        set(v) = raw.edit().putInt("grid_columns", v.coerceIn(3, 5)).apply()

    var largeTileDefault: String
        get() = raw.getString("large_tile_default", "2x2") ?: "2x2"
        set(v) = raw.edit().putString("large_tile_default", if (v == "2x1") "2x1" else "2x2").apply()

    var dynamicTilesEnabled: Boolean
        get() = raw.getBoolean("dynamic_tiles_enabled", true)
        set(v) = raw.edit().putBoolean("dynamic_tiles_enabled", v).apply()

    var notificationMoveEnabled: Boolean
        get() = raw.getBoolean("notification_move_enabled", true)
        set(v) = raw.edit().putBoolean("notification_move_enabled", v).apply()

    // Dock & Search
    var dockVisible: Boolean
        get() = raw.getBoolean("dock_visible", true)
        set(v) = raw.edit().putBoolean("dock_visible", v).apply()

    var dockCount: Int
        get() = raw.getInt("dock_count", 4).coerceIn(4, 10)
        set(v) = raw.edit().putInt("dock_count", v.coerceIn(4, 10)).apply()

    var alphabetSliderEnabled: Boolean
        get() = raw.getBoolean("alphabet_slider_enabled", true)
        set(v) = raw.edit().putBoolean("alphabet_slider_enabled", v).apply()

    var usageTilingEnabled: Boolean
        get() = raw.getBoolean("usage_tiling_enabled", false)
        set(v) = raw.edit().putBoolean("usage_tiling_enabled", v).apply()

    var quickKeyboardVisible: Boolean
        get() = raw.getBoolean("quick_keyboard_visible", true)
        set(v) = raw.edit().putBoolean("quick_keyboard_visible", v).apply()

    // Animations
    var launchStyle: String
        get() {
            val stored = raw.getString("launch_style", null)
            if (stored != null) return stored
            return if (raw.getBoolean("flip_on_launch", false)) LAUNCH_TURN else LAUNCH_MORPH
        }
        set(v) {
            raw.edit()
                .putString("launch_style", v)
                .putBoolean("flip_on_launch", v == LAUNCH_TURN)
                .apply()
        }

    var launchSpeedMs: Int
        get() = raw.getInt("launch_speed_ms", 280).coerceIn(140, 560)
        set(v) = raw.edit().putInt("launch_speed_ms", v.coerceIn(140, 560)).apply()

    var recentsSmoothEnabled: Boolean
        get() = raw.getBoolean("recents_smooth_enabled", true)
        set(v) = raw.edit().putBoolean("recents_smooth_enabled", v).apply()

    var doubleTapLockEnabled: Boolean
        get() = raw.getBoolean("double_tap_lock_enabled", true)
        set(v) = raw.edit().putBoolean("double_tap_lock_enabled", v).apply()

    var onboardingSeen: Boolean
        get() = raw.getBoolean("onboarding_seen", false)
        set(v) = raw.edit().putBoolean("onboarding_seen", v).apply()

    companion object {
        const val DEFAULT_SOLID_COLOR = 0xFF62666E.toInt()
        const val LAUNCH_MORPH = "morph"
        const val LAUNCH_TURN = "turn"
        const val LAUNCH_SLIDE = "slide"
        const val LAUNCH_EXPAND = "expand"
        const val LAUNCH_NONE = "none"
    }
}
