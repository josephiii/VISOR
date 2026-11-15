package ucf.visor.ui.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Start hazard watch" and "stop hazard watch", and what they must not take over. */
class HazardWatchCommandTest {

    @Test
    fun startingTheWatch() {
        listOf("start hazard watch", "turn on hazard watch", "hazard watch on", "Start hazard watch.").forEach {
            assertEquals(it, VoiceCommand.SetHazardWatch(on = true), VoiceCommandParser.parse(it))
        }
    }

    @Test
    fun stoppingTheWatch() {
        listOf("stop hazard watch", "turn off hazard watch", "hazard watch off", "end hazard watch").forEach {
            assertEquals(it, VoiceCommand.SetHazardWatch(on = false), VoiceCommandParser.parse(it))
        }
    }

    @Test
    fun bareStopStillOnlyStopsAReading() {
        // A quiet request must never end safety alerts by accident.
        assertEquals(VoiceCommand.StopReading, VoiceCommandParser.parse("stop"))
    }

    @Test
    fun askingWhetherTheWatchIsOn() {
        listOf("is hazard watch on", "is the hazard watch on?", "hazard watch status").forEach {
            assertEquals(it, VoiceCommand.AskHazardWatch, VoiceCommandParser.parse(it))
        }
    }
}
