package ucf.visor.wearables

/**
 * What VISOR is doing about its link to the glasses, for everything that
 * shows it: the notice at the top of every screen (GlassesConnectionNotice
 * in ui/components), background listening's notification, and the restart
 * notification. VISOR also says each change aloud (VisorEngine), so it is
 * heard with the screen off.
 */
enum class GlassesConnectionNotice {
    /** Nothing to show. */
    NONE,

    /** The SDK lost glasses still on the phone, and VISOR is getting it to reconnect in place. */
    RECONNECTING,

    /** VISOR is restarting itself to reconnect, or has just restarted and is waiting for the glasses. */
    RESTARTING,

    /** The glasses are back. Shown for a few seconds, then [NONE]. */
    RECONNECTED,

    /** VISOR couldn't reconnect yet. It keeps trying; opening VISOR lets it restart. */
    LOST,
}
