package ucf.visor.tts

import kotlinx.coroutines.flow.StateFlow
import java.text.BreakIterator
import java.util.Locale

/**
 * Where a long answer VISOR is reading aloud has got to (see [Readings]).
 */
enum class ReadingState {
    /** Nothing long is being read. Short phrases come and go without controls. */
    NONE,

    /** A long answer is being read aloud right now. */
    READING,

    /**
     * A long answer was stopped part way and can carry on from the sentence it
     * was in: the user paused it, or VISOR said something else over it (such
     * as "Listening." for "VISOR GO").
     */
    PAUSED,
}

/**
 * Pause, resume and stop for a long answer being read aloud: the controls on
 * screen, the glasses' buttons and "VISOR GO, stop / pause / resume" all use
 * these. [Speaker] is the one that does it.
 */
interface ReadingControls {
    val reading: StateFlow<ReadingState>

    /** Stops reading where it is, to carry on later with [resume]. */
    fun pause()

    /** Carries on a paused reading from the start of the sentence it was in. */
    fun resume()

    /** Stops whatever VISOR is saying and forgets the reading. */
    fun stop()
}

/**
 * How VISOR reads a long answer aloud so that it can be paused and resumed.
 *
 * Android's text-to-speech can't pause: it can only stop and forget. So a long
 * answer is said as a queue of short pieces, a sentence (or a line) at a time,
 * and VISOR keeps track of which piece is being said. Resuming starts that
 * piece again, so the listener hears the whole sentence rather than picking
 * up mid-word, which is easier to follow for anyone.
 *
 * Pieces also keep each request well under the engine's input limit
 * (TextToSpeech.getMaxSpeechInputLength, 4,000 characters), which a page of
 * text read with Read text could go over.
 */
object Readings {

    /**
     * An answer at least this long is a reading, with controls on screen and
     * on the glasses: about seven seconds at the normal speech rate. Anything
     * shorter is over before a control could be found and pressed, so it
     * doesn't put one on screen for a moment.
     */
    const val MIN_CHARS = 100

    /**
     * The longest one piece can be. A sentence longer than this (text from a
     * sign or a menu often has no full stops) is split between words, so
     * resuming never goes far back.
     */
    const val MAX_PIECE_CHARS = 200

    /** Whether [text] is long enough to be read with pause, resume and stop. */
    fun isLong(text: String): Boolean = text.trim().length >= MIN_CHARS

    /**
     * [text] in the pieces it is read in, in order: each sentence, each line
     * (text read from a sign or a menu is often one item per line), and long
     * ones split between words. Nothing is left out but blank space.
     */
    fun pieces(text: String): List<String> =
        text.lines()
            .flatMap(::sentences)
            .flatMap(::splitLong)

    private fun sentences(line: String): List<String> {
        val iterator = BreakIterator.getSentenceInstance(Locale.US)
        iterator.setText(line)
        val found = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            line.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(found::add)
            start = end
            end = iterator.next()
        }
        return found
    }

    private fun splitLong(sentence: String): List<String> {
        if (sentence.length <= MAX_PIECE_CHARS) return listOf(sentence)
        val pieces = mutableListOf<String>()
        val piece = StringBuilder()
        sentence.split(Regex("\\s+")).forEach { word ->
            if (piece.isNotEmpty() && piece.length + 1 + word.length > MAX_PIECE_CHARS) {
                pieces += piece.toString()
                piece.clear()
            }
            if (piece.isNotEmpty()) piece.append(' ')
            piece.append(word)
            // A single "word" longer than a piece, such as a long web address.
            while (piece.length > MAX_PIECE_CHARS) {
                pieces += piece.substring(0, MAX_PIECE_CHARS)
                piece.delete(0, MAX_PIECE_CHARS)
            }
        }
        if (piece.isNotEmpty()) pieces += piece.toString()
        return pieces
    }
}
