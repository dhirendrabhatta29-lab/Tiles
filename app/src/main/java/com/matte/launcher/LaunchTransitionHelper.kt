package com.matte.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Handles physics-based app launch transitions expanding from the tapped tile's bounds
 * and smooth, jump-free return settlement when coming back from system Recents / Overview.
 */
internal object LaunchTransitionHelper {

    // Material emphasized decelerate curve for natural spring-like settling without overshoot bounce.
    private val emphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

    /** Returns the screen-space bounds of [source], used for LauncherApps and clip-reveal transitions. */
    fun sourceBounds(source: View?): Rect? {
        if (source == null || !source.isAttachedToWindow || source.width <= 0 || source.height <= 0) return null
        val loc = IntArray(2)
        source.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + source.width, loc[1] + source.height)
    }

    /**
     * Builds ActivityOptions tailored to the user's chosen launch style:
     * - morph: expands smoothly out of the tapped tile's exact bounds via makeClipRevealAnimation.
     * - turn / none: suppresses system transition so the launcher's own 3D turn or instant handoff plays cleanly.
     * - slide: uses the custom slide-in / exit resources.
     */
    fun buildLaunchOptions(
        activity: Activity,
        source: View?,
        launchStyle: String,
        directHandoff: Boolean
    ): Bundle {
        if (directHandoff || launchStyle == LauncherPrefs.LAUNCH_TURN || launchStyle == LauncherPrefs.LAUNCH_NONE ||
            launchStyle == LauncherPrefs.LAUNCH_EXPAND) {
            return ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        }
        if (launchStyle == LauncherPrefs.LAUNCH_SLIDE) {
            return ActivityOptions.makeCustomAnimation(activity, R.anim.app_enter, R.anim.home_exit).toBundle()
        }
        // Default: LAUNCH_MORPH — expand directly from the tapped tile's bounds
        if (source != null && source.isAttachedToWindow && source.width > 0 && source.height > 0) {
            return try {
                ActivityOptions.makeClipRevealAnimation(source, 0, 0, source.width, source.height).toBundle()
            } catch (_: Exception) {
                try {
                    ActivityOptions.makeScaleUpAnimation(source, 0, 0, source.width, source.height).toBundle()
                } catch (_: Exception) {
                    ActivityOptions.makeCustomAnimation(activity, R.anim.app_enter, R.anim.home_exit).toBundle()
                }
            }
        }
        return ActivityOptions.makeCustomAnimation(activity, R.anim.app_enter, R.anim.home_exit).toBundle()
    }

    /**
     * Plays a brief spring-like morph pulse on the tapped tile while Android expands the launched window
     * from its bounds, then cleanly restores its resting transform.
     */
    fun pulseMorphSource(source: View, speedMs: Int) {
        val dur = (speedMs * 0.35f).toLong().coerceIn(60L, 140L)
        source.animate().cancel()
        source.pivotX = source.width / 2f
        source.pivotY = source.height / 2f
        source.animate()
            .scaleX(1.06f)
            .scaleY(1.06f)
            .alpha(0.92f)
            .setDuration(dur)
            .setInterpolator(emphasizedDecelerate)
            .withEndAction {
                source.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(dur + 40L)
                    .setInterpolator(emphasizedDecelerate)
                    .start()
            }
            .start()
    }

    /**
     * Smoothly settles the home canvas when returning from Recents / Overview or closing an app,
     * eliminating jarring cuts and layout snaps.
     */
    fun animateRecentsReturn(target: View, enabled: Boolean, speedMs: Int) {
        target.animate().cancel()
        if (!enabled) {
            target.scaleX = 1f
            target.scaleY = 1f
            target.alpha = 1f
            target.translationY = 0f
            return
        }
        val dur = speedMs.toLong().coerceIn(180L, 420L)
        target.pivotX = target.width / 2f
        target.pivotY = target.height / 2f
        target.scaleX = 0.945f
        target.scaleY = 0.945f
        target.alpha = 0.86f
        target.translationY = 0f
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(dur)
            .setInterpolator(emphasizedDecelerate)
            .start()
    }
}
