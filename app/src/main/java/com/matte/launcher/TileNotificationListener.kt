package com.matte.launcher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Main-thread notification count snapshot (kept backward-compatible). */
internal object TileNotificationCounts {
    private var counts: Map<String, Int> = emptyMap()
    private val observers = LinkedHashSet<() -> Unit>()

    fun count(packageName: String) = counts[packageName] ?: 0
    fun observe(observer: () -> Unit) { observers.add(observer) }
    fun remove(observer: () -> Unit) { observers.remove(observer) }

    fun replace(next: Map<String, Int>) {
        if (next == counts) return
        counts = next.toMap()
        observers.toList().forEach { it() }
    }
}

/**
 * In-memory live notification summaries and active MediaController bridge.
 * Nothing is persisted to disk; all state clears immediately when disconnected.
 */
internal object TileNotificationStore {

    data class Summary(
        val packageName: String,
        val count: Int,
        val title: String?,
        val text: String?,
        val postTime: Long
    ) {
        fun previewLine(): String? {
            val t = title?.trim().orEmpty()
            val b = text?.trim().orEmpty()
            return when {
                t.isNotEmpty() && b.isNotEmpty() -> "$t · $b"
                t.isNotEmpty() -> t
                b.isNotEmpty() -> b
                else -> null
            }
        }
    }

    data class LiveMedia(
        val packageName: String,
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val albumArt: Bitmap?
    )

    private var summaries: Map<String, Summary> = emptyMap()
    private val summaryObservers = LinkedHashSet<() -> Unit>()

    private var activeController: MediaController? = null
    private var currentMedia: LiveMedia? = null
    private val mediaObservers = LinkedHashSet<() -> Unit>()

    fun summary(packageName: String): Summary? = summaries[packageName]
    fun allSummaries(): Map<String, Summary> = summaries

    fun observeSummaries(observer: () -> Unit) { summaryObservers.add(observer) }
    fun removeSummaryObserver(observer: () -> Unit) { summaryObservers.remove(observer) }

    fun replaceSummaries(next: Map<String, Summary>) {
        if (next == summaries) return
        summaries = next.toMap()
        summaryObservers.toList().forEach { it() }
    }

    fun liveMedia(): LiveMedia? = currentMedia
    fun observeMedia(observer: () -> Unit) { mediaObservers.add(observer) }
    fun removeMediaObserver(observer: () -> Unit) { mediaObservers.remove(observer) }

    fun updateMedia(controller: MediaController?, media: LiveMedia?) {
        val changed = currentMedia != media
        activeController = controller
        currentMedia = media
        if (changed) mediaObservers.toList().forEach { it() }
    }

    fun togglePlayPause(): Boolean {
        val c = activeController ?: return false
        return try {
            val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) c.transportControls.pause() else c.transportControls.play()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun skipNext(): Boolean {
        val c = activeController ?: return false
        return try {
            c.transportControls.skipToNext()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun skipPrevious(): Boolean {
        val c = activeController ?: return false
        return try {
            c.transportControls.skipToPrevious()
            true
        } catch (_: Exception) {
            false
        }
    }
}

class TileNotificationListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private val refresh = Runnable { if (connected) refreshCounts() }

    private var mediaSessionManager: MediaSessionManager? = null
    private var trackedController: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = pushMediaState(trackedController)
        override fun onPlaybackStateChanged(state: PlaybackState?) = pushMediaState(trackedController)
        override fun onSessionDestroyed() = refreshMediaSessions()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handler.post { bindBestController(controllers.orEmpty()) }
    }

    override fun onListenerConnected() {
        connected = true
        refresh.run()
        startMediaBridge()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = scheduleRefresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = scheduleRefresh()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = scheduleRefresh()

    override fun onListenerDisconnected() = clearAll()

    override fun onDestroy() {
        clearAll()
        super.onDestroy()
    }

    private fun scheduleRefresh() {
        if (!connected) return
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    private fun clearAll() {
        connected = false
        handler.removeCallbacks(refresh)
        stopMediaBridge()
        TileNotificationCounts.replace(emptyMap())
        TileNotificationStore.replaceSummaries(emptyMap())
    }

    private fun startMediaBridge() {
        try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            mediaSessionManager = msm
            val cn = ComponentName(this, TileNotificationListener::class.java)
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            bindBestController(msm.getActiveSessions(cn))
        } catch (_: SecurityException) {
            // Permission revoked mid-flight.
        } catch (_: Exception) { }
    }

    private fun refreshMediaSessions() {
        if (!connected) return
        try {
            val cn = ComponentName(this, TileNotificationListener::class.java)
            val list = mediaSessionManager?.getActiveSessions(cn).orEmpty()
            bindBestController(list)
        } catch (_: Exception) {
            bindBestController(emptyList())
        }
    }

    private fun stopMediaBridge() {
        try {
            trackedController?.unregisterCallback(controllerCallback)
        } catch (_: Exception) { }
        trackedController = null
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) { }
        mediaSessionManager = null
        TileNotificationStore.updateMedia(null, null)
    }

    private fun bindBestController(controllers: List<MediaController>) {
        val chosen = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.metadata != null }
            ?: controllers.firstOrNull()
        if (chosen?.sessionToken != trackedController?.sessionToken) {
            try { trackedController?.unregisterCallback(controllerCallback) } catch (_: Exception) { }
            trackedController = chosen
            try { chosen?.registerCallback(controllerCallback, handler) } catch (_: Exception) { }
        }
        pushMediaState(chosen)
    }

    private fun pushMediaState(controller: MediaController?) {
        if (controller == null) {
            TileNotificationStore.updateMedia(null, null)
            return
        }
        val meta = controller.metadata
        val state = controller.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val playing = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING
        if (title.isEmpty() && !playing) {
            TileNotificationStore.updateMedia(controller, null)
            return
        }
        val media = TileNotificationStore.LiveMedia(
            packageName = controller.packageName ?: "",
            title = title.ifEmpty { "Now Playing" },
            artist = artist.ifEmpty { controller.packageName?.substringAfterLast('.') ?: "Media" },
            isPlaying = playing,
            albumArt = art
        )
        TileNotificationStore.updateMedia(controller, media)
    }

    private fun refreshCounts() {
        val active = try { activeNotifications.orEmpty() } catch (_: SecurityException) { emptyArray() }
        val rankingMap = currentRanking ?: return
        val ranking = Ranking()
        val counts = HashMap<String, Int>()
        val summaries = HashMap<Pair<String, String>, Int>()
        val groupsWithChildren = HashSet<Pair<String, String>>()
        val latestTitle = HashMap<String, String>()
        val latestText = HashMap<String, String>()
        val latestTime = HashMap<String, Long>()
        val user = Process.myUserHandle()

        fun add(pkg: String, number: Int) {
            counts[pkg] = ((counts[pkg] ?: 0) + number.coerceIn(1, 100)).coerceAtMost(100)
        }

        for (entry in active) {
            if (entry.user != user || entry.isOngoing || entry.packageName == packageName) continue
            val notification = entry.notification
            if (!rankingMap.getRanking(entry.key, ranking) || !ranking.canShowBadge()) continue
            val pkg = entry.packageName
            val group = pkg to entry.groupKey
            val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isSummary) {
                summaries[group] = maxOf(summaries[group] ?: 0, notification.number.coerceAtLeast(1))
            } else {
                groupsWithChildren.add(group)
                add(pkg, notification.number)
            }
            val prevTime = latestTime[pkg] ?: -1L
            if (!isSummary && entry.postTime >= prevTime) {
                latestTime[pkg] = entry.postTime
                val extras = notification.extras
                val t = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                val body = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                if (!t.isNullOrEmpty()) latestTitle[pkg] = t
                if (!body.isNullOrEmpty()) latestText[pkg] = body
            } else if (pkg !in latestTime) {
                latestTime[pkg] = entry.postTime
            }
        }
        for ((group, number) in summaries) {
            if (group !in groupsWithChildren) add(group.first, number)
        }

        val richSummaries = HashMap<String, TileNotificationStore.Summary>(counts.size)
        for ((pkg, count) in counts) {
            if (count <= 0) continue
            richSummaries[pkg] = TileNotificationStore.Summary(
                packageName = pkg,
                count = count,
                title = latestTitle[pkg],
                text = latestText[pkg],
                postTime = latestTime[pkg] ?: System.currentTimeMillis()
            )
        }
        TileNotificationCounts.replace(counts)
        TileNotificationStore.replaceSummaries(richSummaries)
    }
}
