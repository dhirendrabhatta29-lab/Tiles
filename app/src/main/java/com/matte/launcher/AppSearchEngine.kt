package com.matte.launcher

import java.util.Locale

/**
 * Instant app search supporting prefix, word-boundary, substring, fuzzy subsequence,
 * and T9 keypad digit queries (e.g. "225" -> Calendar/Calculator, "ytm" -> YouTube Music).
 */
internal object AppSearchEngine {

    /**
     * Returns a positive relevance score (higher = better match), or 0 if [appLabel] does not match [rawQuery].
     */
    fun matchScore(appLabel: String, rawQuery: String): Int {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return 1
        val label = appLabel.lowercase(Locale.ROOT)

        // 1. Direct text matching
        if (label == q) return 1000
        if (label.startsWith(q)) return 900 - (label.length - q.length).coerceAtMost(80)

        val words = label.split(' ', '-', '_', '.')
        if (words.any { it.startsWith(q) }) return 800

        val containsIdx = label.indexOf(q)
        if (containsIdx >= 0) return 700 - containsIdx.coerceAtMost(80)

        // 2. Acronym / initials match (e.g. "ytm" -> "YouTube Music", "gm" -> "Google Maps")
        val initials = buildString {
            for (w in words) if (w.isNotEmpty()) append(w[0])
        }
        if (initials.startsWith(q)) return 650

        // 3. Fuzzy subsequence match
        val fuzzy = fuzzyScore(label, q)
        if (fuzzy > 0) return 450 + fuzzy

        // 4. T9 keypad digit matching when query consists of digits 2..9
        if (q.all { it in '2'..'9' }) {
            val t9Label = toT9(label)
            if (t9Label.startsWith(q)) return 580
            val t9Words = words.map { toT9(it) }
            if (t9Words.any { it.startsWith(q) }) return 540
            if (t9Label.contains(q)) return 420
            val t9Initials = toT9(initials)
            if (t9Initials.startsWith(q)) return 500
        }

        return 0
    }

    private fun fuzzyScore(text: String, query: String): Int {
        var qi = 0
        var consecutive = 0
        var bonus = 0
        for (i in text.indices) {
            if (qi >= query.length) break
            if (text[i] == query[qi]) {
                qi++
                consecutive++
                bonus += if (consecutive > 1) 12 else 4
                if (i == 0 || text[i - 1] == ' ') bonus += 10
            } else {
                consecutive = 0
            }
        }
        return if (qi == query.length) bonus.coerceAtMost(180) else 0
    }

    private fun toT9(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                'a', 'b', 'c', '2' -> append('2')
                'd', 'e', 'f', '3' -> append('3')
                'g', 'h', 'i', '4' -> append('4')
                'j', 'k', 'l', '5' -> append('5')
                'm', 'n', 'o', '6' -> append('6')
                'p', 'q', 'r', 's', '7' -> append('7')
                't', 'u', 'v', '8' -> append('8')
                'w', 'x', 'y', 'z', '9' -> append('9')
            }
        }
    }
}
