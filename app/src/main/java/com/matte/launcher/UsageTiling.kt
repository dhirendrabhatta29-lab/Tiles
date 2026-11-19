package com.matte.launcher

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Usage-driven tiling. Reads foreground time per package (last 7 days) and produces a
 * display-only plan: the most used apps become larger and move to the front.
 * Saved tile sizes and positions are never modified.
 */
internal object UsageTiling {
    private const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    private const val STALE_MS = 5L * 60 * 1000

    private var scores: Map<String, Long> = emptyMap()
    private var loadedAt = 0L
    @Volatile private var loading = false
    private val main = Handler(Looper.getMainLooper())

    fun hasData(): Boolean = scores.isNotEmpty()
    fun score(packageName: String?): Long = if (packageName == null) 0L else scores[packageName] ?: 0L

    /** Loads usage off the main thread, then calls [onLoaded] on it. No-op without Usage Access. */
    fun refreshIfStale(context: Context, onLoaded: () -> Unit) {
        if (!PermissionsHelper.isUsageAccessGranted(context)) {
            if (scores.isNotEmpty()) scores = emptyMap()
            return
        }
        val now = System.currentTimeMillis()
        if (loading || (scores.isNotEmpty() && now - loadedAt < STALE_MS)) return
        loading = true
        val app = context.applicationContext
        Thread {
            try {
                val usm = app.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val raw = usm?.queryAndAggregateUsageStats(now - WINDOW_MS, now).orEmpty()
                val next = HashMap<String, Long>()
                for ((pkg, stats) in raw) if (stats.totalTimeInForeground > 0L) next[pkg] = stats.totalTimeInForeground
                main.post {
                    val changed = next != scores
                    scores = next
                    loadedAt = System.currentTimeMillis()
                    if (changed) onLoaded()
                }
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }.start()
    }

    class Result(val sizes: List<TileSize?>, val positions: List<HomeGrid.Position>, val elevated: Set<Int>)

    /**
     * [packages] aligns with [items]. [elevated] indices (active notifications) go first.
     * Top 1 -> 2x2, next 2 -> 2x1, other used apps -> 1x1. Unused or non-app tiles keep their size.
     */
    fun layout(items: List<Item>, packages: List<String?>, elevated: Set<Int>, columns: Int): Result {
        val cols = columns.coerceAtLeast(2)
        val isBanner = { i: Int -> items[i].type == "glance" || items[i].type == "wx" }
        val candidate = { i: Int -> (items[i].type == "app" || items[i].type == "slot") && score(packages.getOrNull(i)) > 0L }
        val banners = items.indices.filter(isBanner)
        val ranked = items.indices.filter { candidate(it) && !isBanner(it) }
            .sortedWith(compareByDescending<Int> { it in elevated }.thenByDescending { score(packages.getOrNull(it)) })
        val rest = items.indices.filter { !isBanner(it) && !candidate(it) }
        val order = banners + ranked + rest

        val sizes = arrayOfNulls<TileSize>(items.size)
        ranked.forEachIndexed { rank, idx ->
            sizes[idx] = when (rank) {
                0 -> TileSize(2, 2)
                1, 2 -> TileSize(2, 1)
                else -> TileSize(1, 1)
            }
        }
        val requests = order.map { idx ->
            val size = sizes[idx] ?: TileSize.fromKey(items[idx].sz)
            val width = size.width.coerceAtMost(cols)
            if (isBanner(idx)) HomeGrid.Request(0, width, size.height, false, 0)
            else HomeGrid.Request(0, width, size.height, false, null)
        }
        val packed = HomeGrid.pack(requests, cols)
        val positions = arrayOfNulls<HomeGrid.Position>(items.size)
        order.forEachIndexed { slot, idx -> positions[idx] = packed[slot] }
        return Result(sizes.toList(), positions.map { it!! }, elevated.filter { it in ranked }.toSet())
    }
}
