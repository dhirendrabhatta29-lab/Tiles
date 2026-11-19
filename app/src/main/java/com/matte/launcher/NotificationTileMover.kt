package com.matte.launcher

/**
 * Computes notification-driven priority placement for Home tiles without mutating the user's
 * saved layout coordinates. When active notifications arrive, matching tiles smoothly elevate
 * to the top priority zone; once dismissed, all tiles return cleanly to their saved coordinates.
 */
internal object NotificationTileMover {

    data class Result(
        val positions: List<HomeGrid.Position>,
        val elevatedIndices: Set<Int>
    )

    fun computeDisplayPositions(
        items: List<Item>,
        requests: List<HomeGrid.Request>,
        packageNames: List<String?>,
        summaries: Map<String, TileNotificationStore.Summary>,
        columnsPerViewport: Int,
        enabled: Boolean
    ): Result {
        val cols = columnsPerViewport.coerceAtLeast(1)
        if (!enabled || summaries.isEmpty() || items.isEmpty()) {
            return Result(HomeGrid.pack(requests, cols), emptySet())
        }

        // Identify movable app/slot tiles that currently have active notifications.
        val elevated = items.indices.filter { idx ->
            val type = items[idx].type
            if (type != "app" && type != "slot") return@filter false
            val pkg = packageNames.getOrNull(idx) ?: return@filter false
            (summaries[pkg]?.count ?: 0) > 0
        }.sortedByDescending { idx ->
            val pkg = packageNames[idx]
            summaries[pkg]?.postTime ?: 0L
        }

        if (elevated.isEmpty()) {
            return Result(HomeGrid.pack(requests, cols), emptySet())
        }

        val elevatedSet = elevated.toSet()

        // Keep top glance/weather banners anchored at row 0 if present, then place elevated tiles
        // directly into the top priority band, followed by all remaining tiles cleanly packed below.
        val topBanners = items.indices.filter { idx ->
            val t = items[idx].type
            (t == "glance" || t == "wx") && (requests[idx].row == null || requests[idx].row == 0)
        }
        val bannerSet = topBanners.toSet()
        val others = items.indices.filter { it !in bannerSet && it !in elevatedSet }

        val orderedIndices = ArrayList<Int>(items.size)
        orderedIndices.addAll(topBanners)
        orderedIndices.addAll(elevated)
        orderedIndices.addAll(others)

        // Build clean auto-pack requests for the priority zone while preserving relative order.
        val dynamicRequests = orderedIndices.map { idx ->
            val orig = requests[idx]
            val clampedWidth = orig.width.coerceAtMost(cols)
            when {
                idx in bannerSet -> orig.copy(column = 0, width = clampedWidth, fixedColumn = false, row = 0)
                idx in elevatedSet -> orig.copy(column = 0, width = clampedWidth, fixedColumn = false, row = null)
                else -> orig.copy(column = 0, width = clampedWidth, fixedColumn = false, row = null)
            }
        }

        val packedOrdered = HomeGrid.pack(dynamicRequests, cols)
        val finalPositions = arrayOfNulls<HomeGrid.Position>(items.size)
        orderedIndices.forEachIndexed { posIdx, originalIdx ->
            finalPositions[originalIdx] = packedOrdered[posIdx]
        }
        return Result(finalPositions.map { it!! }, elevatedSet)
    }
}
