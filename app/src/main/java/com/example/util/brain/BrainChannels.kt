package com.example.util.brain

import java.util.Locale

/**
 * Worker E (v1.3.0): exact Kotlin port of focivo_brain/brain.py
 * build_channel_index() + _aliases().
 *
 * assets/focivo_brain_channels.json = the repo's channels.json verbatim
 * (397 blocked, 463 study channels from the owner's Excel). alias/handle ->
 * (group, bonus, display name), built once at load:
 *   black rows            -> group "black", bonus -9.0
 *   white/national rows   -> group "white", bonus +2.0
 *   white/state rows      -> group "white", bonus +1.5
 *
 * Lookup: handle ("@x" form) first, then the normalised channel name —
 * exactly like check_youtube(). First alias wins (setdefault semantics);
 * user-supplied extra lists OVERWRITE (plain assignment, like Python).
 *
 * NOTE: the user's own Study Channels / blocked-channels lists
 * (BlockedChannelsManager, StudyAllowlistManager) are evaluated BEFORE this
 * index in the guard pipeline and always take precedence.
 */
class BrainChannels private constructor(
    val index: Map<String, ChannelHit>
) {
    data class ChannelHit(
        val group: String, // "black" | "white"
        val bonus: Double, // logit bonus added to the combined score
        val displayName: String
    )

    companion object {
        // 'Physics Wallah - Alakh Pandey' -> {'physics wallah alakh pandey',
        // 'physics wallah', 'alakh pandey'} — mirrors Python _aliases()
        private val ALIAS_SPLIT = Regex("""\s+[-|\u2013]\s+|\(|\)""")

        fun aliases(name: String): Set<String> {
            val out = LinkedHashSet<String>()
            val full = BrainNormalize.normalize(name)
            if (full.isNotEmpty()) out.add(full)
            for (part in ALIAS_SPLIT.split(name)) {
                val n = BrainNormalize.normalize(part)
                if (n.length >= 4) out.add(n)
            }
            return out
        }

        /**
         * Build the index from channels.json text.
         * extraWhite/extraBlack mirror the repo's whitelist_channels /
         * blacklist_channels config (names or @handles); the app passes the
         * user's own lists here when desired — they overwrite repo entries.
         */
        fun fromJson(
            json: String,
            extraWhite: List<String> = emptyList(),
            extraBlack: List<String> = emptyList()
        ): BrainChannels {
            val idx = LinkedHashMap<String, ChannelHit>()

            fun parseRows(key: String, kind: String) {
                val kIdx = json.indexOf("\"$key\"")
                if (kIdx < 0) return
                var i = json.indexOf('[', kIdx) + 1
                // each row: {"name": "...", "handle": "...", "group": "..."}
                while (i < json.length) {
                    while (i < json.length && (json[i].isWhitespace() || json[i] == ',')) i++
                    if (i >= json.length || json[i] == ']') break
                    // find the row object's end by brace matching (strings first)
                    var j = i
                    var depth = 0
                    var inStr = false
                    var esc = false
                    while (j < json.length) {
                        val c = json[j]
                        if (inStr) {
                            if (esc) esc = false
                            else if (c == '\\') esc = true
                            else if (c == '"') inStr = false
                        } else {
                            if (c == '"') inStr = true
                            else if (c == '{') depth++
                            else if (c == '}') {
                                depth--
                                if (depth == 0) break
                            }
                        }
                        j++
                    }
                    val row = json.substring(i, j + 1)
                    val name = jsonStringField(row, "name") ?: ""
                    val handle = jsonStringField(row, "handle")
                    val grp = jsonStringField(row, "group")
                    val bonus = when (if (kind == "black") "black" else (grp ?: kind)) {
                        "black" -> -9.0
                        "national" -> 2.0
                        else -> 1.5 // "state" or anything else, mirroring .get(..., 1.5)
                    }
                    val group = if (kind == "black") "black" else "white"
                    val keys = aliases(name).toMutableSet()
                    if (handle != null) keys.add("@" + handle.lowercase(Locale.ROOT))
                    for (k in keys) {
                        if (k !in idx) idx[k] = ChannelHit(group, bonus, name)
                    }
                    i = j + 1
                }
            }

            parseRows("black", "black")
            parseRows("white", "white")

            for (n in extraWhite) {
                val key = if (n.startsWith("@")) n.lowercase(Locale.ROOT) else BrainNormalize.normalize(n)
                idx[key] = ChannelHit("white", 2.0, n)
            }
            for (n in extraBlack) {
                val key = if (n.startsWith("@")) n.lowercase(Locale.ROOT) else BrainNormalize.normalize(n)
                idx[key] = ChannelHit("black", -9.0, n)
            }
            return BrainChannels(idx)
        }

        /** Minimal "field": "value" reader for flat row objects. */
        private fun jsonStringField(row: String, field: String): String? {
            val kIdx = row.indexOf("\"$field\"")
            if (kIdx < 0) return null
            val colon = row.indexOf(':', kIdx)
            var j = colon + 1
            while (j < row.length && row[j].isWhitespace()) j++
            if (j >= row.length || row[j] != '"') return null
            j++
            val sb = StringBuilder()
            while (j < row.length) {
                val c = row[j]
                if (c == '\\') {
                    val e = row[j + 1]
                    sb.append(
                        when (e) {
                            'u' -> row.substring(j + 2, j + 6).toInt(16).toChar()
                            'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'
                            '\\' -> '\\'; '"' -> '"'; '/' -> '/'
                            'b' -> '\b'; 'f' -> '\u000C'
                            else -> e
                        }
                    )
                    j += if (e == 'u') 6 else 2
                } else if (c == '"') {
                    break
                } else {
                    sb.append(c)
                    j++
                }
            }
            return sb.toString()
        }
    }

    /**
     * check_youtube() lookup order: "@handle" first, then normalised
     * channel name. Returns null when neither hits.
     */
    fun lookup(handle: String?, channelName: String?): ChannelHit? {
        if (!handle.isNullOrEmpty()) {
            val h = "@" + handle.trimStart('@').lowercase(Locale.ROOT)
            index[h]?.let { return it }
        }
        if (!channelName.isNullOrEmpty()) {
            index[BrainNormalize.normalize(channelName)]?.let { return it }
        }
        return null
    }
}
