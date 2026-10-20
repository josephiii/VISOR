package ucf.visor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How "All sounds" and each kind of sound's own slider combine into what is heard. */
class AudioMixTest {

    @Test
    fun allSoundsScalesEachKindOfSound() {
        val mix = AudioMix().withMaster(0.5f).with(VoiceCategory.ANSWERS, 0.8f)

        assertEquals(0.4f, mix.loudness(VoiceCategory.ANSWERS), TOLERANCE)
        assertEquals(0.5f, mix.loudness(VoiceCategory.SESSION_UPDATES), TOLERANCE)
    }

    @Test
    fun turningAllSoundsOffSilencesEverything() {
        val mix = AudioMix().withMaster(Volumes.Off)

        AudioCategory.all.forEach { assertTrue("$it", Volumes.isOff(mix.loudness(it))) }
    }

    @Test
    fun oneKindOfSoundCanBeSilencedOnItsOwn() {
        val mix = AudioMix().with(VoiceCategory.SESSION_UPDATES, Volumes.Off)

        assertTrue(Volumes.isOff(mix.loudness(VoiceCategory.SESSION_UPDATES)))
        assertEquals(Volumes.Max, mix.loudness(VoiceCategory.ANSWERS), TOLERANCE)
        assertEquals(
            Volumes.DefaultSoundEffects,
            mix.loudness(SoundCategory.SESSION_CHIMES),
            TOLERANCE,
        )
    }

    @Test
    fun theVoiceStartsAtFullVolumeAndSoundEffectsWhereTheBeepsAlwaysWere() {
        val mix = AudioMix()

        VoiceCategory.entries.forEach { assertEquals(Volumes.Max, mix.loudness(it), TOLERANCE) }
        SoundCategory.entries.forEach {
            assertEquals(Volumes.DefaultSoundEffects, mix.loudness(it), TOLERANCE)
        }
    }

    @Test
    fun aChangedSliderKeepsTheOthersAsTheyWere() {
        val before = AudioMix().with(SoundCategory.THINKING_BEEPS, 0.2f)
        val after = before.with(VoiceCategory.VOICE_COMMANDS, 0.3f)

        assertEquals(0.2f, after.level(SoundCategory.THINKING_BEEPS), TOLERANCE)
        assertEquals(before.master, after.master, TOLERANCE)
    }

    @Test
    fun everyKindOfSoundIsSavedUnderItsOwnKey() {
        val keys = AudioCategory.all.map { it.key }

        assertEquals(keys.size, keys.toSet().size)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
