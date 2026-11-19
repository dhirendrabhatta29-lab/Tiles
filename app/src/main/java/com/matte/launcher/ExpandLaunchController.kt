package com.matte.launcher

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.max

/**
 * "Slow Expand + Dock Return".
 * Launch: a snapshot of the tapped tile grows from its place toward full screen while the app starts.
 * Return: the snapshot shrinks into a floating 1x1 copy in a strip above the dock. All views live in
 * [host] (the content frame) as overlays, so the home grid is never re-laid-out or shifted.
 */
internal class ExpandLaunchController(private val host: FrameLayout, private val u: Float) {

    private class Staged(val view: View, val slot: Int, val dismiss: Runnable)

    private val main = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private var overlay: ImageView? = null
    private var overlayAnimator: ValueAnimator? = null
    private var overlayLeft = 0
    private var overlayTop = 0
    private val staged = ArrayList<Staged>()
    private var zone: View? = null
    private val safety = Runnable { cancelExpand() }

    val hasOverlay: Boolean get() = overlay != null

    private fun windowOrigin(view: View): IntArray = IntArray(2).also { view.getLocationInWindow(it) }

    /** Starts growing a snapshot of [source]. The caller launches the app right after this returns. */
    fun startExpand(source: View, durationMs: Long): Boolean {
        removeOverlayNow()
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0 || host.width <= 0 || host.height <= 0) return false
        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { source.draw(Canvas(it)) }
        } catch (_: Throwable) { return false }
        val src = windowOrigin(source)
        val org = windowOrigin(host)
        overlayLeft = src[0] - org[0]
        overlayTop = src[1] - org[1]
        val view = ImageView(host.context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            pivotX = w / 2f
            pivotY = h / 2f
            elevation = u * 24f
        }
        host.addView(view, FrameLayout.LayoutParams(w, h).apply {
            leftMargin = overlayLeft
            topMargin = overlayTop
        })
        overlay = view
        val dx = host.width / 2f - (overlayLeft + w / 2f)
        val dy = host.height / 2f - (overlayTop + h / 2f)
        val target = max(host.width / w.toFloat(), host.height / h.toFloat()) * 1.05f
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.scaleX = target; view.scaleY = target; view.translationX = dx; view.translationY = dy
        } else {
            overlayAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = ease
                addUpdateListener {
                    val f = it.animatedValue as Float
                    val s = 1f + (target - 1f) * f
                    view.scaleX = s; view.scaleY = s
                    view.translationX = dx * f; view.translationY = dy * f
                }
                start()
            }
        }
        // If the app never opens (launch failed), shrink back instead of hiding the home screen forever.
        main.removeCallbacks(safety)
        main.postDelayed(safety, 4000L)
        return true
    }

    /** Shrinks the expanded snapshot back to its tile and removes it. */
    fun cancelExpand() {
        main.removeCallbacks(safety)
        val view = overlay ?: return
        overlayAnimator?.cancel(); overlayAnimator = null
        view.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).alpha(0f)
            .setDuration(220L).withEndAction { if (overlay === view) removeOverlayNow() }.start()
    }

    private fun removeOverlayNow() {
        main.removeCallbacks(safety)
        overlayAnimator?.cancel(); overlayAnimator = null
        overlay?.let { v ->
            v.animate().cancel()
            host.removeView(v)
            (v.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
        }
        overlay = null
    }

    /**
     * Moves the expanded snapshot into a 1x1 [copy] placed above the dock. [slots] is the number of
     * staged tiles that fit (the grid column count). Coordinates are in [host] space.
     */
    fun stageReturn(
        copy: View, tile: Int, gap: Int, leftEdge: Int, top: Int, slots: Int, durationMs: Long,
        dockCenterY: Float, holdMs: Long, zoneColor: Int
    ) {
        main.removeCallbacks(safety)
        val maxSlots = slots.coerceAtLeast(1)
        // Lowest free slot; evict the oldest when full so slots never collide.
        val used = staged.map { it.slot }.toSet()
        val free = (0 until maxSlots).firstOrNull { it !in used }
        val slot = free ?: staged.first().also { dismiss(it, fast = true) }.slot
        val x = leftEdge + slot * (tile + gap)

        ensureZone(leftEdge, top, tile, zoneColor)
        copy.alpha = 0f
        host.addView(copy, FrameLayout.LayoutParams(tile, tile).apply { leftMargin = x; topMargin = top })
        copy.elevation = u * 8f
        val hold = Runnable { staged.firstOrNull { it.view === copy }?.let { dismiss(it, fast = false, dockY = dockCenterY) } }
        val real = Staged(copy, slot, hold)
        staged.add(real)
        main.postDelayed(hold, holdMs + durationMs)

        val view = overlay
        if (view == null || !ValueAnimator.areAnimatorsEnabled()) {
            removeOverlayNow()
            copy.alpha = 1f
            return
        }
        overlayAnimator?.cancel()
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val s0 = view.scaleX
        val tx0 = view.translationX
        val ty0 = view.translationY
        val s1 = tile / max(w, h)
        val tx1 = (x + tile / 2f) - (overlayLeft + w / 2f)
        val ty1 = (top + tile / 2f) - (overlayTop + h / 2f)
        overlayAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = durationMs
            interpolator = ease
            addUpdateListener {
                val f = it.animatedValue as Float
                val s = s0 + (s1 - s0) * f
                view.scaleX = s; view.scaleY = s
                view.translationX = tx0 + (tx1 - tx0) * f
                view.translationY = ty0 + (ty1 - ty0) * f
                val fade = ((f - 0.35f) / 0.65f).coerceIn(0f, 1f)
                view.alpha = 1f - fade
                copy.alpha = fade
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (overlayAnimator === animation) {
                        copy.alpha = 1f
                        removeOverlayNow()
                    }
                }
                override fun onAnimationCancel(animation: android.animation.Animator) { copy.alpha = 1f }
            })
            start()
        }
    }

    private fun ensureZone(leftEdge: Int, top: Int, tile: Int, color: Int) {
        if (zone != null) return
        val pad = (u * 6f).toInt()
        val strip = View(host.context).apply {
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = u * 18f
            }
            alpha = 0f
        }
        host.addView(strip,
            FrameLayout.LayoutParams(host.width - (leftEdge - pad) * 2, tile + pad * 2).apply {
                leftMargin = leftEdge - pad
                topMargin = top - pad
            })
        strip.elevation = u * 2f
        strip.animate().alpha(1f).setDuration(180L).start()
        zone = strip
    }

    private fun dismiss(item: Staged, fast: Boolean, dockY: Float = 0f) {
        main.removeCallbacks(item.dismiss)
        staged.remove(item)
        val v = item.view
        val dy = if (fast) 0f else dockY - (v.top + v.height / 2f)
        v.animate().cancel()
        v.animate().translationY(dy).scaleX(0.5f).scaleY(0.5f).alpha(0f)
            .setDuration(if (fast) 120L else 280L)
            .withEndAction {
                host.removeView(v)
                if (staged.isEmpty()) removeZone()
            }.start()
    }

    private fun removeZone() {
        val z = zone ?: return
        zone = null
        z.animate().alpha(0f).setDuration(160L).withEndAction { host.removeView(z) }.start()
    }

    /** Removes every overlay immediately (activity teardown). */
    fun clear() {
        removeOverlayNow()
        for (s in staged.toList()) { main.removeCallbacks(s.dismiss); s.view.animate().cancel(); host.removeView(s.view) }
        staged.clear()
        zone?.let { it.animate().cancel(); host.removeView(it) }
        zone = null
    }
}
