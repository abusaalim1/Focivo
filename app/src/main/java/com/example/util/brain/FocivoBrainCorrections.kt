package com.example.util.brain

import android.content.Context
import android.util.Log

/**
 * Worker E (v1.3.0): the user's own corrections memory — layer 4 of the
 * Focivo brain.
 *
 * When the user overrides a verdict ("this was study" / "not study"), the
 * NORMALISED title is persisted and checked FIRST on future judgments
 * (before channels, the doubt gate and both ML brains — exactly like the
 * Python brain's feedback map, which the engine consults at the top of
 * [FocivoBrainEngine.score]).
 *
 * Storage: SharedPreferences (key = normalised title, value = boolean),
 * plus an in-memory copy so the guard hot path never touches disk.
 *
 * Public API (for the next round / any caller):
 *   FocivoBrainCorrections.record(context, title, isStudy)  // user override
 *   FocivoBrainCorrections.lookup(context, title)           // Boolean?; null = none
 *   FocivoBrainCorrections.asLookup(context)                // engine plug-in
 *   FocivoBrainCorrections.count(context) / .clear(context)
 *
 * The Guard Hub "Last YouTube evaluation" card wires two override buttons
 * ("Was study" / "Not study") into [record] — see AiStudyGuardHubSheet.
 */
object FocivoBrainCorrections {

    private const val TAG = "BrainCorrections"
    private const val PREFS = "focivo_brain_corrections"
    private const val MAX_ENTRIES = 2000

    @Volatile
    private var cache: HashMap<String, Boolean>? = null

    private fun cacheOf(context: Context): HashMap<String, Boolean> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val map = HashMap<String, Boolean>()
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                for ((k, v) in prefs.all) {
                    if (v is Boolean) map[k] = v
                }
            } catch (t: Throwable) {
                Log.w(TAG, "corrections load failed", t)
            }
            cache = map
            return map
        }
    }

    /**
     * Record a user override. The title is normalised before storing, so
     * "Newton's Laws!" and "newtons laws" hit the same key — exactly like
     * the Python brain's feedback_add().
     */
    fun record(context: Context, title: String, isStudy: Boolean) {
        val norm = BrainNormalize.normalize(title)
        if (norm.isEmpty()) return
        val map = cacheOf(context)
        synchronized(this) {
            map[norm] = isStudy
            // bound growth: drop oldest-ish entries past the cap (SharedPrefs
            // has no ordering; eviction is arbitrary but bounded)
            if (map.size > MAX_ENTRIES) {
                val it = map.keys.iterator()
                var drop = map.size - MAX_ENTRIES
                while (drop-- > 0 && it.hasNext()) {
                    it.next(); it.remove()
                }
            }
        }
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(norm, isStudy).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "corrections persist failed", t)
        }
        Log.i(TAG, "correction recorded: study=$isStudy title='$norm'")
    }

    /** Look up a correction for a raw title; null when the user never overrode it. */
    fun lookup(context: Context, title: String): Boolean? {
        val norm = BrainNormalize.normalize(title)
        if (norm.isEmpty()) return null
        return cacheOf(context)[norm]
    }

    /** Engine plug-in: normalised-title -> Boolean?. */
    fun asLookup(context: Context): BrainCorrectionsLookup =
        BrainCorrectionsLookup { norm -> cacheOf(context)[norm] }

    fun count(context: Context): Int = cacheOf(context).size

    fun clear(context: Context) {
        synchronized(this) { cacheOf(context).clear() }
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (t: Throwable) {
            Log.w(TAG, "corrections clear failed", t)
        }
    }
}
