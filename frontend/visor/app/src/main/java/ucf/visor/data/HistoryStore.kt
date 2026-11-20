package ucf.visor.data

import android.content.Context
import java.time.ZoneId

/**
 * Today's answers (Settings > History), saved on the phone so they survive
 * VISOR being closed, or Android stopping it in the background. Never sent to
 * VISOR's server: they may be text read off someone's mail, and the history
 * has to work offline like the Last answer card does. The app opts out of
 * Android's backups (android:allowBackup="false"), so they stay on this phone.
 *
 * The rules (today only, newest first, at most 50) are in [ResponseHistory].
 */
class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("visor_history", Context.MODE_PRIVATE)

    /** Today's answers, newest first; yesterday's are left behind. */
    fun load(nowMillis: Long = System.currentTimeMillis()): List<HistoryEntry> =
        ResponseHistory.today(ResponseHistory.decode(prefs.getString(KEY_ENTRIES, null)), nowMillis, ZoneId.systemDefault())

    fun save(entries: List<HistoryEntry>) {
        prefs.edit().putString(KEY_ENTRIES, ResponseHistory.encode(entries)).apply()
    }

    /** "Clear history", logging out, and deleting the account. */
    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}
