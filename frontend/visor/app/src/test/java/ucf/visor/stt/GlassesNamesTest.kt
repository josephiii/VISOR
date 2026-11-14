package ucf.visor.stt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which Bluetooth call device is the glasses, so commands are heard through
 * the glasses' microphone and never through some other headset.
 */
class GlassesNamesTest {

    @Test
    fun theNameMwdatReportsMatches() {
        assertTrue(GlassesNames.matches("Joe's Glasses", "Joe's Glasses"))
        assertTrue(GlassesNames.matches("joe's glasses", "Joe's Glasses "))
    }

    @Test
    fun metaGlassesMatchByBrandEvenWhenNamesDiffer() {
        assertTrue(GlassesNames.matches("Ray-Ban Meta 0A1B", null))
        assertTrue(GlassesNames.matches("Oakley Meta HSTN", "My glasses"))
        assertTrue(GlassesNames.matches("Meta Ray-Ban Display", ""))
    }

    @Test
    fun anotherHeadsetIsNotTheGlasses() {
        assertFalse(GlassesNames.matches("Pixel Buds Pro", "Ray-Ban Meta"))
        assertFalse(GlassesNames.matches("Car audio", null))
    }

    @Test
    fun aNamelessDeviceIsNotTheGlasses() {
        assertFalse(GlassesNames.matches(null, "Ray-Ban Meta"))
        assertFalse(GlassesNames.matches("  ", "Ray-Ban Meta"))
    }
}
