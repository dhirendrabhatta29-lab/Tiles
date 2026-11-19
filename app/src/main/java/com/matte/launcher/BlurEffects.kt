package com.matte.launcher

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import kotlin.math.roundToInt

/**
 * Real-time blur & glassmorphism helpers using Android 12+ RenderEffect and window background blur,
 * with clean translucent fallback surfaces on Android 8–11.
 */
internal object BlurEffects {

    fun isRealtimeBlurSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Applies or clears live window-behind blur (API 31+) when overlays (drawer, folders, settings, menus)
     * are shown over the wallpaper.
     */
    fun applyWindowBlur(window: Window, intensityPercent: Int, active: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val clamped = intensityPercent.coerceIn(0, 100)
        try {
            if (active && clamped > 0) {
                val radiusPx = (clamped * 0.56f).roundToInt().coerceIn(4, 60)
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(radiusPx)
                val params = window.attributes
                if (params.blurBehindRadius != radiusPx) {
                    params.blurBehindRadius = radiusPx
                    window.attributes = params
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(0)
                val params = window.attributes
                if (params.blurBehindRadius != 0) {
                    params.blurBehindRadius = 0
                    window.attributes = params
                }
            }
        } catch (_: Throwable) {
            // Device OEM does not support cross-window blur; translucent glass surface still applies.
        }
    }

    /**
     * Applies RenderEffect Gaussian blur directly to a background content view (e.g., homeTiles behind
     * an open overlay or settings sheet) on Android 12+.
     */
    fun applyContentBlur(view: View, intensityPercent: Int, active: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val clamped = intensityPercent.coerceIn(0, 100)
        try {
            if (active && clamped > 0) {
                val radius = (clamped * 0.34f).coerceIn(2f, 36f)
                view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            } else {
                view.setRenderEffect(null)
            }
        } catch (_: Throwable) {
            try { view.setRenderEffect(null) } catch (_: Throwable) {}
        }
    }

    /**
     * Builds a frosted glass / translucent surface for the dock, overlays, cards, and settings sheets.
     * On Android 12+ with blur enabled, uses sleeker translucency so live blur shines through;
     * on older APIs, uses higher opacity for crisp legibility.
     */
    fun glassBackground(
        baseColor: Int,
        blurIntensity: Int,
        cornerRadiusPx: Float,
        isDark: Boolean = true,
        strongerScrim: Boolean = false
    ): GradientDrawable {
        val intensity = blurIntensity.coerceIn(0, 100)
        val alpha = when {
            intensity == 0 -> if (strongerScrim) 242 else 224
            isRealtimeBlurSupported() -> {
                val base = if (strongerScrim) 210 else 168
                (base - (intensity * 0.38f)).roundToInt().coerceIn(112, 232)
            }
            else -> {
                val base = if (strongerScrim) 236 else 208
                (base - (intensity * 0.14f)).roundToInt().coerceIn(176, 240)
            }
        }
        val fill = (baseColor and 0x00FFFFFF) or (alpha shl 24)
        val stroke = if (isDark) 0x2EFFFFFF else 0x24000000
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = cornerRadiusPx
            setStroke(1, stroke)
        }
    }

    /** Returns an overlay scrim color tuned to the blur intensity and wallpaper visibility. */
    fun overlayScrim(baseColor: Int, blurIntensity: Int, useWallpaper: Boolean): Int {
        if (!useWallpaper) return baseColor or Color.BLACK
        val intensity = blurIntensity.coerceIn(0, 100)
        val alpha = if (isRealtimeBlurSupported() && intensity > 0) {
            (218 - intensity * 0.55f).roundToInt().coerceIn(135, 225)
        } else {
            (238 - intensity * 0.15f).roundToInt().coerceIn(205, 242)
        }
        return (baseColor and 0x00FFFFFF) or (alpha shl 24)
    }
}
