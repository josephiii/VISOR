package ucf.visor.tts

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * One of the phone's text-to-speech voices, as much as VISOR needs to know
 * about it. [Speaker] makes these from the engine's `android.speech.tts.Voice`s,
 * so everything that chooses between them ([VoiceCatalog]) is plain Kotlin.
 *
 * @param name the engine's own name for it, such as "en-us-x-iol-local".
 *   What the profile saves.
 * @param needsInternet whether it is spoken by the engine's server rather than
 *   on the phone (`Voice.isNetworkConnectionRequired`). Such a voice can't be
 *   used offline, and sends what VISOR says to that server.
 * @param installed false for a voice the engine lists but hasn't downloaded
 *   (`TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED`).
 */
data class VoiceInfo(
    val name: String,
    val locale: Locale,
    val needsInternet: Boolean,
    val installed: Boolean = true,
)

/**
 * A voice as Settings and "VISOR GO, change voice" offer it.
 *
 * Engines name voices for themselves ("en-us-x-iol-local"), which tells a
 * listener nothing, so each is offered by its accent and a number: "US English
 * voice 2". A voice and its on-phone twin (see [VoiceCatalog.offlineTwin])
 * share their number, so "US English voice 2, needs the internet" and "US
 * English voice 2, works offline" are heard as the same voice two ways.
 */
data class VoiceOption(
    val name: String,
    val accent: String,
    val number: Int,
    val needsInternet: Boolean,
) {
    val label: String get() = "$accent voice $number"
}

/**
 * Choosing VISOR's voice: what [Speaker] offers to Settings and voice
 * commands. The profile says which voice the user chose; [useVoice] is what
 * makes VISOR speak in one right now, before the profile's change has gone
 * round, so a sample or "Voice changed" is said in the new voice.
 */
interface VoiceChooser {
    /** Every voice the phone's engine has, as of the last [refreshVoices]. Empty until it is ready. */
    val voices: StateFlow<List<VoiceInfo>>

    /** Reads the engine's voices again: the user may have downloaded more. */
    fun refreshVoices()

    /** Speaks in [name] from now on, or in VISOR's default voice for null. */
    fun useVoice(name: String?)
}

/**
 * Which of the phone's voices VISOR offers, what each is called, and which to
 * speak in. The engine's list is everything it has for every language; VISOR
 * speaks English (`Locale.US` is its default), so only English voices that are
 * on the phone or can be spoken over the internet are offered.
 */
object VoiceCatalog {

    /**
     * Accents in the order Settings lists them: VISOR's default first. Any
     * other English accent comes after these, by name.
     */
    private val accentOrder = listOf("US", "GB", "AU", "IN", "CA", "IE", "NZ", "ZA", "NG")

    private val accentNames = mapOf(
        "US" to "US English",
        "GB" to "UK English",
        "AU" to "Australian English",
        "IN" to "Indian English",
        "CA" to "Canadian English",
        "IE" to "Irish English",
        "NZ" to "New Zealand English",
        "ZA" to "South African English",
        "NG" to "Nigerian English",
    )

    /**
     * The voices VISOR offers, in the order Settings lists them: every one
     * that works offline, then every one that needs the internet; each group
     * by accent, then number.
     */
    fun options(voices: List<VoiceInfo>): List<VoiceOption> {
        val offered = voices.filter(::isOffered)
        val numbers = mutableMapOf<String, Int>()
        offered.groupBy { accentKey(it.locale) }.forEach { (_, inAccent) ->
            inAccent.map { baseName(it.name) }.distinct().sorted().forEachIndexed { index, base ->
                numbers[base] = index + 1
            }
        }
        return offered
            .map { VoiceOption(it.name, accentName(it.locale), numbers.getValue(baseName(it.name)), it.needsInternet) }
            .sortedWith(
                compareBy<VoiceOption> { it.needsInternet }
                    .thenBy { accentRank(it.accent) }
                    .thenBy { it.accent }
                    .thenBy { it.number },
            )
    }

    /** [name] as offered, or null when it isn't (VISOR's default, or a voice no longer on the phone). */
    fun option(name: String?, voices: List<VoiceInfo>): VoiceOption? =
        name?.let { n -> options(voices).firstOrNull { it.name == n } }

    /**
     * The voice VISOR should speak in, given the one the user [chosen]: null
     * for VISOR's default voice.
     *
     *  - VISOR's default, or a voice no longer on the phone: the default.
     *  - A voice that needs the internet, offline: its on-phone twin if it
     *    has one, else the default. Never the voice itself, which would keep
     *    the user waiting for the engine to give up on its server; a hazard
     *    can't wait. The choice is kept, and used again once back online.
     *  - Otherwise, the voice chosen.
     */
    fun voiceToUse(chosen: String?, voices: List<VoiceInfo>, isOnline: Boolean): String? {
        val voice = chosen?.let { n -> voices.firstOrNull { it.name == n && isOffered(it) } } ?: return null
        if (voice.needsInternet && !isOnline) return offlineTwin(voice.name, voices)?.name
        return voice.name
    }

    /**
     * The on-phone voice that sounds like [name], if the engine has one: the
     * voice whose name only differs in ending "-local" rather than "-network",
     * as Google's speech engine names its two versions of each voice.
     */
    fun offlineTwin(name: String, voices: List<VoiceInfo>): VoiceInfo? {
        val base = baseName(name)
        return voices.firstOrNull { it.name != name && !it.needsInternet && isOffered(it) && baseName(it.name) == base }
    }

    /**
     * The voice after [chosen], for "VISOR GO, next voice": through VISOR's
     * default and then each voice that can be used now ([isOnline]), in the
     * order Settings lists them, and round again. Null is the default voice.
     */
    fun next(chosen: String?, voices: List<VoiceInfo>, isOnline: Boolean): String? =
        step(chosen, voices, isOnline, +1)

    /** The voice before [chosen], for "VISOR GO, previous voice". */
    fun previous(chosen: String?, voices: List<VoiceInfo>, isOnline: Boolean): String? =
        step(chosen, voices, isOnline, -1)

    /** Whether there is any voice to choose besides VISOR's default, right now. */
    fun hasChoices(voices: List<VoiceInfo>, isOnline: Boolean): Boolean =
        usable(voices, isOnline).isNotEmpty()

    private fun step(chosen: String?, voices: List<VoiceInfo>, isOnline: Boolean, by: Int): String? {
        val cycle = listOf<String?>(null) + usable(voices, isOnline).map { it.name }
        val at = cycle.indexOf(chosen)
        // A voice not in the cycle (one that needs the internet, offline)
        // counts as being just before the default, so "next" is the default.
        val from = if (at >= 0) at else if (by > 0) cycle.lastIndex else 0
        return cycle[Math.floorMod(from + by, cycle.size)]
    }

    private fun usable(voices: List<VoiceInfo>, isOnline: Boolean): List<VoiceOption> =
        options(voices).filter { isOnline || !it.needsInternet }

    /**
     * English, on the phone or spoken by its server, and a voice of its own.
     * Google's engine also lists "en-US-language" and the like: the default
     * voice for a language again, under another name, which would be offered
     * twice.
     */
    private fun isOffered(voice: VoiceInfo): Boolean =
        voice.installed &&
            voice.locale.language == Locale.ENGLISH.language &&
            !voice.name.endsWith("-language", ignoreCase = true)

    /** [name] without the ending that says which version of a voice it is. */
    private fun baseName(name: String): String =
        name.removeSuffix("-local").removeSuffix("-network")

    private fun accentKey(locale: Locale): String = locale.country.uppercase(Locale.US)

    private fun accentName(locale: Locale): String {
        val key = accentKey(locale)
        return accentNames[key]
            ?: locale.getDisplayCountry(Locale.US).takeIf { it.isNotBlank() }?.let { "$it English" }
            ?: "English"
    }

    private fun accentRank(accent: String): Int {
        val key = accentNames.entries.firstOrNull { it.value == accent }?.key
        val rank = accentOrder.indexOf(key)
        return if (rank >= 0) rank else accentOrder.size
    }
}
