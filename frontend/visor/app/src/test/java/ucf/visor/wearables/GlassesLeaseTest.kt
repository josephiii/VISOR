package ucf.visor.wearables

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glasses' one session, shared. The camera's held session gives way to
 * the head-motion lab; nothing gives way to the camera.
 */
class GlassesLeaseTest {

    @After
    fun freeTheGlasses() {
        listOf("camera", "lab", "nav").forEach(GlassesLease::release)
    }

    @Test
    fun aYieldingHolderGivesWayAndIsTold() {
        var yielded = 0
        assertTrue(GlassesLease.acquireYielding("camera") { yielded++ })
        assertTrue(GlassesLease.acquire("lab"))
        assertEquals(1, yielded)
        assertEquals("lab", GlassesLease.holder.value)
    }

    /**
     * The yielding holder lets go at once (on the main thread) and may try to
     * take the glasses straight back: the new owner already has them by then.
     */
    @Test
    fun aYieldingHolderCannotTakeTheGlassesBackWhileLettingGo() {
        var tookBack: Boolean? = null
        GlassesLease.acquireYielding("camera") {
            GlassesLease.release("camera")
            tookBack = GlassesLease.acquireYielding("camera") {}
        }
        assertTrue(GlassesLease.acquire("lab"))
        assertEquals(false, tookBack)
        assertEquals("lab", GlassesLease.holder.value)
    }

    @Test
    fun aPlainHolderDoesNotGiveWay() {
        assertTrue(GlassesLease.acquire("lab"))
        assertFalse(GlassesLease.acquireYielding("camera") {})
        assertFalse(GlassesLease.acquire("nav"))
        assertEquals("lab", GlassesLease.holder.value)
    }

    @Test
    fun releasingAYieldingHolderForgetsIt() {
        var yielded = 0
        GlassesLease.acquireYielding("camera") { yielded++ }
        GlassesLease.release("camera")
        assertNull(GlassesLease.holder.value)
        assertTrue(GlassesLease.acquire("lab"))
        assertEquals(0, yielded)
    }

    @Test
    fun holdingAgainIsFine() {
        assertTrue(GlassesLease.acquireYielding("camera") {})
        assertTrue(GlassesLease.acquireYielding("camera") {})
        assertTrue(GlassesLease.acquire("camera"))
        assertEquals("camera", GlassesLease.holder.value)
    }
}
