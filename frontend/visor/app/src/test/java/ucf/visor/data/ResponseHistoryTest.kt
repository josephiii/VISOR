package ucf.visor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ucf.visor.stt.CameraCommand
import java.time.LocalDateTime
import java.time.ZoneId

/** Settings > History keeps today's answers only, newest first, and at most 50. */
class ResponseHistoryTest {

    private val zone = ZoneId.of("America/New_York")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(atMillis: Long, text: String = "answer", command: CameraCommand = CameraCommand.READ_TEXT) =
        HistoryEntry(text, atMillis, command)

    @Test
    fun newestAnswerGoesToTheTop() {
        val first = entry(at(14, 9), "first")
        val second = entry(at(14, 10), "second")
        val history = ResponseHistory.add(ResponseHistory.add(emptyList(), first, zone), second, zone)
        assertEquals(listOf("second", "first"), history.map { it.text })
    }

    @Test
    fun yesterdaysAnswersAreGoneOnceTodaysArrive() {
        val lateLastNight = entry(at(13, 23, 59), "yesterday")
        val history = ResponseHistory.add(listOf(lateLastNight), entry(at(14, 0, 1), "today"), zone)
        assertEquals(listOf("today"), history.map { it.text })
    }

    @Test
    fun todayStartsAtMidnightWhereThePhoneIs() {
        val entries = listOf(entry(at(14, 0, 0), "midnight"), entry(at(13, 23, 59), "before"))
        assertEquals(listOf("midnight"), ResponseHistory.today(entries, at(14, 9), zone).map { it.text })
        // In Tokyo, 14 hours ahead, both were on the afternoon of the 14th, where it is still the 14th.
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(2, ResponseHistory.today(entries, at(14, 9), tokyo).size)
    }

    @Test
    fun keepsAtMostFiftyDroppingTheOldest() {
        var history = emptyList<HistoryEntry>()
        repeat(ResponseHistory.MaxEntries + 5) { i -> history = ResponseHistory.add(history, entry(at(14, 8) + i * 1000L, "#$i"), zone) }
        assertEquals(ResponseHistory.MaxEntries, history.size)
        assertEquals("#${ResponseHistory.MaxEntries + 4}", history.first().text)
        assertEquals("#5", history.last().text)
    }

    @Test
    fun savesAndReadsBackEverything() {
        val entries = listOf(
            HistoryEntry("A red mug.", at(14, 11), CameraCommand.ASK_QUESTION, question = "what color is my mug"),
            HistoryEntry("Stop sign, 3 meters ahead.", at(14, 10), CameraCommand.CHECK_HAZARDS),
            HistoryEntry("Quotes \"and\" unicode — ✓", at(14, 9), CameraCommand.READ_TEXT),
        )
        assertEquals(entries, ResponseHistory.decode(ResponseHistory.encode(entries)))
    }

    @Test
    fun readsNewestFirstWhateverOrderItWasSavedIn() {
        val saved = ResponseHistory.encode(listOf(entry(at(14, 9), "older"), entry(at(14, 10), "newer")))
        assertEquals(listOf("newer", "older"), ResponseHistory.decode(saved).map { it.text })
    }

    @Test
    fun anAnswerFromAFeatureThatNoLongerExistsIsDroppedNotTheWholeHistory() {
        val saved = """[{"text":"kept","at":1,"command":"READ_TEXT"},{"text":"gone","at":2,"command":"RETIRED_FEATURE"}]"""
        assertEquals(listOf("kept"), ResponseHistory.decode(saved).map { it.text })
    }

    @Test
    fun nothingSavedOrUnreadableIsAnEmptyHistory() {
        assertTrue(ResponseHistory.decode(null).isEmpty())
        assertTrue(ResponseHistory.decode("").isEmpty())
        assertTrue(ResponseHistory.decode("not json").isEmpty())
    }
}
