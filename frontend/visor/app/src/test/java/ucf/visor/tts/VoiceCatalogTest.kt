package ucf.visor.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Which voices VISOR offers, what it calls them, and which it speaks in,
 * online and offline. The voices are named as Google's speech engine names
 * them: each voice twice, "-local" on the phone and "-network" over the internet.
 */
class VoiceCatalogTest {

    private val us = Locale.US
    private val uk = Locale.UK
    private val india = Locale("en", "IN")

    private fun local(name: String, locale: Locale = us) = VoiceInfo("$name-local", locale, needsInternet = false)
    private fun network(name: String, locale: Locale = us) = VoiceInfo("$name-network", locale, needsInternet = true)

    private val voices = listOf(
        network("en-us-x-iol"),
        local("en-us-x-iol"),
        local("en-us-x-sfg"),
        local("en-gb-x-rjs", uk),
        network("en-in-x-ene", india),
        // Not offered: another language, not downloaded, and the default voice again.
        VoiceInfo("fr-fr-x-frd-local", Locale.FRANCE, needsInternet = false),
        VoiceInfo("en-us-x-tpd-local", us, needsInternet = false, installed = false),
        VoiceInfo("en-US-language", us, needsInternet = false),
    )

    @Test
    fun offersOnPhoneVoicesFirstThenByAccent() {
        val labels = VoiceCatalog.options(voices).map { "${it.label}${if (it.needsInternet) " (internet)" else ""}" }
        assertEquals(
            listOf(
                "US English voice 1",
                "US English voice 2",
                "UK English voice 1",
                "US English voice 1 (internet)",
                "Indian English voice 1 (internet)",
            ),
            labels,
        )
    }

    @Test
    fun aVoiceAndItsOnPhoneTwinShareTheirNumber() {
        val options = VoiceCatalog.options(voices)
        val local = options.single { it.name == "en-us-x-iol-local" }
        val network = options.single { it.name == "en-us-x-iol-network" }
        assertEquals(local.label, network.label)
    }

    @Test
    fun otherLanguagesUndownloadedAndDuplicateVoicesAreNotOffered() {
        val names = VoiceCatalog.options(voices).map { it.name }
        assertFalse("fr-fr-x-frd-local" in names)
        assertFalse("en-us-x-tpd-local" in names)
        assertFalse("en-US-language" in names)
    }

    @Test
    fun theDefaultVoiceIsNull() {
        assertNull(VoiceCatalog.voiceToUse(null, voices, isOnline = true))
        assertNull(VoiceCatalog.voiceToUse(null, voices, isOnline = false))
    }

    @Test
    fun anOnPhoneVoiceIsUsedOnlineAndOffline() {
        assertEquals("en-us-x-sfg-local", VoiceCatalog.voiceToUse("en-us-x-sfg-local", voices, isOnline = true))
        assertEquals("en-us-x-sfg-local", VoiceCatalog.voiceToUse("en-us-x-sfg-local", voices, isOnline = false))
    }

    @Test
    fun anInternetVoiceIsUsedOnline() {
        assertEquals("en-us-x-iol-network", VoiceCatalog.voiceToUse("en-us-x-iol-network", voices, isOnline = true))
    }

    @Test
    fun offlineAnInternetVoiceGivesWayToItsOnPhoneTwin() {
        assertEquals("en-us-x-iol-local", VoiceCatalog.voiceToUse("en-us-x-iol-network", voices, isOnline = false))
    }

    @Test
    fun offlineAnInternetVoiceWithNoTwinGivesWayToTheDefault() {
        assertNull(VoiceCatalog.voiceToUse("en-in-x-ene-network", voices, isOnline = false))
    }

    @Test
    fun aVoiceNoLongerOnThePhoneGivesWayToTheDefault() {
        assertNull(VoiceCatalog.voiceToUse("en-au-x-gone-local", voices, isOnline = true))
        assertNull(VoiceCatalog.option("en-au-x-gone-local", voices))
        // Nor is a voice that isn't downloaded used.
        assertNull(VoiceCatalog.voiceToUse("en-us-x-tpd-local", voices, isOnline = true))
    }

    @Test
    fun beforeTheEngineListsItsVoicesTheDefaultIsUsed() {
        assertNull(VoiceCatalog.voiceToUse("en-us-x-sfg-local", emptyList(), isOnline = true))
    }

    @Test
    fun nextGoesThroughEveryVoiceAndRoundAgainOnline() {
        // From the default voice, round until the default comes back.
        val seen = mutableListOf<String?>()
        var voice: String? = null
        do {
            voice = VoiceCatalog.next(voice, voices, isOnline = true)
            seen += voice
        } while (voice != null && seen.size < 10)
        assertEquals(
            listOf(
                "en-us-x-iol-local", "en-us-x-sfg-local", "en-gb-x-rjs-local",
                "en-us-x-iol-network", "en-in-x-ene-network", null,
            ),
            seen,
        )
    }

    @Test
    fun offlineNextPassesOverInternetVoices() {
        assertEquals(null, VoiceCatalog.next("en-gb-x-rjs-local", voices, isOnline = false))
        assertEquals("en-gb-x-rjs-local", VoiceCatalog.previous(null, voices, isOnline = false))
        // From an internet voice, offline: next is the default, as if it came last.
        assertEquals(null, VoiceCatalog.next("en-us-x-iol-network", voices, isOnline = false))
    }

    @Test
    fun previousGoesBackwards() {
        assertEquals("en-us-x-iol-local", VoiceCatalog.previous("en-us-x-sfg-local", voices, isOnline = true))
        assertEquals(null, VoiceCatalog.previous("en-us-x-iol-local", voices, isOnline = true))
        assertEquals("en-in-x-ene-network", VoiceCatalog.previous(null, voices, isOnline = true))
    }

    @Test
    fun withNoOtherVoicesThereIsNothingToChoose() {
        assertFalse(VoiceCatalog.hasChoices(emptyList(), isOnline = true))
        assertFalse(VoiceCatalog.hasChoices(listOf(network("en-us-x-iol")), isOnline = false))
        assertTrue(VoiceCatalog.hasChoices(listOf(network("en-us-x-iol")), isOnline = true))
        assertNull(VoiceCatalog.next(null, emptyList(), isOnline = true))
    }

    @Test
    fun anUnlistedAccentIsNamedByItsCountry() {
        val option = VoiceCatalog.options(listOf(local("en-sg-x-abc", Locale("en", "SG")))).single()
        assertEquals("Singapore English voice 1", option.label)
    }
}
