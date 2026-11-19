package com.matte.launcher

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Resolves Material You (Android 12+) dynamic palettes and WallpaperManager multi-stop gradients
 * with safe fallbacks for Android 8.0–11 (API 26–30).
 */
internal object WallpaperThemeEngine {

    fun isMaterialYouSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun resolveDynamicTheme(context: Context): ThemeColors? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildApi31MonetTheme(context)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return buildWallpaperFallbackTheme(context)
        }
        return null
    }

    private fun buildApi31MonetTheme(context: Context): ThemeColors? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val bg = context.getColor(android.R.color.system_neutral1_900)
            val panel = context.getColor(android.R.color.system_neutral1_800)
            val text = context.getColor(android.R.color.system_neutral1_100)
            val muted = context.getColor(android.R.color.system_neutral2_300)
            val ink = context.getColor(android.R.color.system_neutral1_900)
            val paper = context.getColor(android.R.color.system_accent1_100)
            val charcoal = context.getColor(android.R.color.system_neutral2_800)
            val dark = context.getColor(android.R.color.system_neutral1_1000)
            val secondary = context.getColor(android.R.color.system_accent2_700)
            val accent = context.getColor(android.R.color.system_accent1_500)
            val signal = context.getColor(android.R.color.system_accent3_500)
            ThemeColors(
                background = toUnsigned(bg),
                panel = toUnsigned(panel),
                text = toUnsigned(text),
                mutedText = toUnsigned(muted),
                ink = toUnsigned(ink),
                paper = toUnsigned(paper),
                charcoal = toUnsigned(charcoal),
                dark = toUnsigned(dark),
                secondary = toUnsigned(secondary),
                accent = toUnsigned(accent),
                signal = toUnsigned(signal),
                autoContrastAccents = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildWallpaperFallbackTheme(context: Context): ThemeColors? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        return try {
            val wc = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?: return null
            val primary = wc.primaryColor.toArgb()
            val secondary = wc.secondaryColor?.toArgb() ?: shiftHue(primary, 32f)
            val tertiary = wc.tertiaryColor?.toArgb() ?: shiftHue(primary, -42f)
            val bg = scaleValue(primary, 0.14f, 0.28f)
            val panel = scaleValue(primary, 0.21f, 0.30f)
            val paper = blend(primary, Color.WHITE, 0.72f)
            val charcoal = scaleValue(primary, 0.28f, 0.35f)
            val dark = scaleValue(primary, 0.10f, 0.25f)
            val sec = scaleValue(secondary, 0.42f, 0.55f)
            val acc = scaleValue(primary, 0.62f, 0.68f)
            val sig = scaleValue(tertiary, 0.66f, 0.74f)
            ThemeColors(
                background = toUnsigned(bg),
                panel = toUnsigned(panel),
                text = 0xFFF0F2F5,
                mutedText = 0xFFB0B7C3,
                ink = 0xFF14161A,
                paper = toUnsigned(paper),
                charcoal = toUnsigned(charcoal),
                dark = toUnsigned(dark),
                secondary = toUnsigned(sec),
                accent = toUnsigned(acc),
                signal = toUnsigned(sig),
                autoContrastAccents = true
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Returns 3 opaque stops derived from the active wallpaper (API 27+) or theme fallback. */
    fun extractWallpaperStops(context: Context, fallback: ThemeColors): IntArray {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wc = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (wc != null) {
                    val p = wc.primaryColor.toArgb() or Color.BLACK
                    val s = (wc.secondaryColor?.toArgb() ?: shiftHue(p, 35f)) or Color.BLACK
                    val t = (wc.tertiaryColor?.toArgb() ?: shiftHue(p, -35f)) or Color.BLACK
                    return intArrayOf(p, s, t)
                }
            } catch (_: Exception) { }
        }
        return intArrayOf(
            fallback[TileTone.ACCENT].background or Color.BLACK,
            fallback[TileTone.SECONDARY].background or Color.BLACK,
            fallback[TileTone.CHARCOAL].background or Color.BLACK
        )
    }

    /** Builds a subtle, legible tile gradient tinted with wallpaper colors. */
    fun tileGradient(baseColor: Int, stops: IntArray, cornerRadiusPx: Float, strokeColor: Int = 0): GradientDrawable {
        val primary = stops.getOrElse(0) { baseColor }
        val secondary = stops.getOrElse(1) { baseColor }
        val start = blend(baseColor, primary, 0.24f)
        val end = blend(baseColor, secondary, 0.34f)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = cornerRadiusPx
            if (strokeColor != 0) setStroke(1, strokeColor)
        }
    }

    /** Builds a rich multi-stop wallpaper-derived backdrop gradient. */
    fun backdropGradient(stops: IntArray, alpha: Int = 0xEE): GradientDrawable {
        val c0 = withAlpha(scaleValue(stops.getOrElse(0) { 0xFF1C1D1A.toInt() }, 0.32f, 0.65f), alpha)
        val c1 = withAlpha(scaleValue(stops.getOrElse(1) { 0xFF282A30.toInt() }, 0.24f, 0.60f), alpha)
        val c2 = withAlpha(scaleValue(stops.getOrElse(2) { 0xFF141519.toInt() }, 0.16f, 0.55f), alpha)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c0, c1, c2))
    }

    /** Registers a safe WallpaperColors listener on API 27+. Returns an unregister lambda. */
    fun observeWallpaperColors(context: Context, onChanged: () -> Unit): () -> Unit {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return {}
        return try {
            val wm = WallpaperManager.getInstance(context)
            val listener = WallpaperManager.OnColorsChangedListener { _: WallpaperColors?, which: Int ->
                if ((which and WallpaperManager.FLAG_SYSTEM) != 0) onChanged()
            }
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            { try { wm.removeOnColorsChangedListener(listener) } catch (_: Exception) {} }
        } catch (_: Exception) {
            {}
        }
    }

    fun blend(a: Int, b: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        val inv = 1f - t
        val r = (Color.red(a) * inv + Color.red(b) * t).toInt().coerceIn(0, 255)
        val g = (Color.green(a) * inv + Color.green(b) * t).toInt().coerceIn(0, 255)
        val bl = (Color.blue(a) * inv + Color.blue(b) * t).toInt().coerceIn(0, 255)
        val al = (Color.alpha(a) * inv + Color.alpha(b) * t).toInt().coerceIn(0, 255)
        return Color.argb(al, r, g, bl)
    }

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    private fun toUnsigned(color: Int): Long = (color.toLong() and 0xFFFFFFFFL) or 0xFF000000L

    private fun shiftHue(color: Int, degrees: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees + 360f) % 360f
        return Color.HSVToColor(hsv)
    }

    private fun scaleValue(color: Int, targetV: Float, maxSat: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[1] = hsv[1].coerceAtMost(maxSat)
        hsv[2] = targetV.coerceIn(0.08f, 0.92f)
        return Color.HSVToColor(hsv)
    }
}
