package ucf.visor.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ucf.visor.stt.CameraCommand
import java.time.Instant
import java.time.ZoneId

/**
 * One answer VISOR gave today, for Settings > History: what it said, when,
 * which camera feature said it, and, for "Ask a question", what was asked.
 */
data class HistoryEntry(
    val text: String,
    val atMillis: Long,
    val command: CameraCommand,
    val question: String? = null,
)

/**
 * The rules for VISOR's response history, kept apart from where it is saved
 * (HistoryStore) so they can be tested off the phone.
 *
 * Only today's answers, newest first, and at most [MaxEntries] of them. The
 * history is a way to catch up on what was missed today, not a record: an
 * answer may be text read off someone's mail, so it is kept on the phone only,
 * gone at midnight, and gone when the user logs out.
 */
object ResponseHistory {

    /**
     * Enough for a busy day (about one answer every ten minutes from waking),
     * few enough to page through with a screen reader or a magnifier.
     */
    const val MaxEntries = 50

    /** When today began, at midnight where the phone is. */
    fun startOfDay(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** The [entries] given today; anything from before midnight is gone. */
    fun today(entries: List<HistoryEntry>, nowMillis: Long, zone: ZoneId): List<HistoryEntry> {
        val start = startOfDay(nowMillis, zone)
        return entries.filter { it.atMillis >= start }
    }

    /** [entries] with [entry] at the top, without yesterday's, and the oldest dropped past [MaxEntries]. */
    fun add(entries: List<HistoryEntry>, entry: HistoryEntry, zone: ZoneId): List<HistoryEntry> =
        (listOf(entry) + today(entries, entry.atMillis, zone)).take(MaxEntries)

    // -------------------------------------------------------------------------
    // Saving

    /**
     * How an entry is saved. The camera feature by name, so one that a later
     * version of VISOR drops (or renames) loses its entries rather than the
     * whole history.
     */
    @Serializable
    private data class Saved(
        val text: String,
        val at: Long,
        val command: String,
        val question: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Saved.serializer())

    fun encode(entries: List<HistoryEntry>): String =
        json.encodeToString(serializer, entries.map { Saved(it.text, it.atMillis, it.command.name, it.question) })

    /** What [encode] saved, newest first. Nothing, rather than a crash, if it can't be read. */
    fun decode(saved: String?): List<HistoryEntry> {
        if (saved.isNullOrBlank()) return emptyList()
        val entries = runCatching { json.decodeFromString(serializer, saved) }.getOrNull() ?: return emptyList()
        return entries
            .mapNotNull { s ->
                val command = runCatching { CameraCommand.valueOf(s.command) }.getOrNull() ?: return@mapNotNull null
                HistoryEntry(s.text, s.at, command, s.question)
            }
            .sortedByDescending { it.atMillis }
    }
}
