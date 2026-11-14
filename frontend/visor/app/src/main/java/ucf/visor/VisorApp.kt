package ucf.visor

import android.app.Application
import android.content.Context
import ucf.visor.engine.VisorEngine
import ucf.visor.engine.VisorState

/**
 * VISOR's process. Holds the two things that must outlive any screen: what
 * VISOR knows ([state]) and what listens and speaks ([engine]), so VISOR can
 * keep answering wake phrases with the screen off (background listening),
 * and a screen that is closed and opened again finds them as they were.
 */
class VisorApp : Application() {

    val state: VisorState by lazy { VisorState(this) }

    /** Built the first time a screen or the listening service needs it: it loads the wake-phrase model. */
    val engine: VisorEngine by lazy { VisorEngine(this, state) }

    companion object {
        fun from(context: Context): VisorApp = context.applicationContext as VisorApp
    }
}
