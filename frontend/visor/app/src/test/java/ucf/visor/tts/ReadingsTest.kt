package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A long answer is read a piece at a time, so it can be paused and resumed
 * from the start of the sentence it stopped in.
 */
class ReadingsTest {

    @Test
    fun onlyLongAnswersAreReadings() {
        assertFalse(Readings.isLong("No text found."))
        assertFalse(Readings.isLong(" ".repeat(200)))
        assertTrue(Readings.isLong("a".repeat(Readings.MIN_CHARS)))
    }

    @Test
    fun eachSentenceIsAPiece() {
        assertEquals(
            listOf("The bus leaves at noon.", "It stops at Main Street.", "Is that right?"),
            Readings.pieces("The bus leaves at noon. It stops at Main Street. Is that right?"),
        )
    }

    @Test
    fun eachLineIsAPieceAndBlankLinesAreDropped() {
        assertEquals(
            listOf("MENU", "Burgers 5 dollars", "Fries 2 dollars"),
            Readings.pieces("MENU\n\nBurgers 5 dollars\r\nFries 2 dollars\n"),
        )
    }

    @Test
    fun aLongSentenceIsSplitBetweenWordsAndNothingIsLost() {
        val words = (1..120).map { "word$it" }
        val text = words.joinToString(" ")
        val pieces = Readings.pieces(text)

        assertTrue(pieces.size > 1)
        pieces.forEach { assertTrue(it, it.length <= Readings.MAX_PIECE_CHARS) }
        assertEquals(words, pieces.flatMap { it.split(" ") })
    }

    @Test
    fun aWordLongerThanAPieceIsStillSaidInFull() {
        val address = "x".repeat(Readings.MAX_PIECE_CHARS * 2 + 10)
        val pieces = Readings.pieces("Go to $address now")

        pieces.forEach { assertTrue(it.length <= Readings.MAX_PIECE_CHARS) }
        // Every character is said, in order: only the gaps between pieces move.
        assertEquals("Goto${address}now", pieces.joinToString("").replace(" ", ""))
    }

    @Test
    fun nothingToReadIsNoPieces() {
        assertEquals(emptyList<String>(), Readings.pieces("  \n \n"))
    }
}
