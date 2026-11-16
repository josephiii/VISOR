package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class HazardWatchCommandTest {

    @Test
    fun hazardWatchAndStartHazardWatchTurnItOn() {
        listOf("hazard watch", "start hazard watch", "Start Hazard Watch.", "turn on hazard watch").forEach {
            assertEquals(it, VoiceCommand.SetHazardWatch(on = true), VoiceCommandParser.parse(it))
        }
    }

    @Test
    fun stopHazardWatchTurnsItOff() {
        listOf("stop hazard watch", "turn off hazard watch", "hazard watch off").forEach {
            assertEquals(it, VoiceCommand.SetHazardWatch(on = false), VoiceCommandParser.parse(it))
        }
    }
}
