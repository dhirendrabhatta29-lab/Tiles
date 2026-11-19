package com.matte.launcher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages the customizable bottom Dock entries (built-in slots or arbitrary pinned apps),
 * supporting 3–5 icons, drag-and-drop replacement, reordering, and reset.
 */
internal class DockManager(private val prefs: SharedPreferences) {

    data class Entry(
        val type: String, // "slot" or "app"
        val key: String   // slot id (e.g. "phone") or ComponentName.flattenToString()
    )

    companion object {
        private const val KEY_DOCK = "dock_entries_v2"
        const val MAX = 10
        const val MIN = 4
        val DEFAULT_SLOTS = listOf("phone", "messaging", "web", "camera", "photos",
            "music", "mail", "calendar", "clock", "settings")
    }

    fun load(count: Int): MutableList<Entry> {
        val targetCount = count.coerceIn(MIN, MAX)
        val stored = ArrayList<Entry>()
        val raw = prefs.getString(KEY_DOCK, null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val t = obj.optString("t", "slot")
                    val k = obj.optString("k", "")
                    if (k.isNotEmpty() && (t == "slot" || t == "app")) {
                        stored.add(Entry(t, k))
                    }
                }
            } catch (_: Exception) { }
        }
        // Backfill missing positions from defaults up to 5
        for (slotId in DEFAULT_SLOTS) {
            if (stored.size >= MAX) break
            if (stored.none { it.type == "slot" && it.key == slotId }) {
                stored.add(Entry("slot", slotId))
            }
        }
        return stored.take(targetCount).toMutableList()
    }

    private fun loadAll(): MutableList<Entry> = load(MAX)

    fun saveAll(entries: List<Entry>) {
        val arr = JSONArray()
        for (e in entries.take(MAX)) {
            arr.put(JSONObject().put("t", e.type).put("k", e.key))
        }
        prefs.edit().putString(KEY_DOCK, arr.toString()).apply()
    }

    fun setAppAt(index: Int, componentKey: String) {
        val all = loadAll()
        if (index !in all.indices) return
        all[index] = Entry("app", componentKey)
        saveAll(all)
    }

    fun setSlotAt(index: Int, slotId: String) {
        val all = loadAll()
        if (index !in all.indices) return
        all[index] = Entry("slot", slotId)
        saveAll(all)
    }

    fun swap(indexA: Int, indexB: Int) {
        val all = loadAll()
        if (indexA !in all.indices || indexB !in all.indices || indexA == indexB) return
        val tmp = all[indexA]
        all[indexA] = all[indexB]
        all[indexB] = tmp
        saveAll(all)
    }

    fun reset() {
        prefs.edit().remove(KEY_DOCK).apply()
    }
}
