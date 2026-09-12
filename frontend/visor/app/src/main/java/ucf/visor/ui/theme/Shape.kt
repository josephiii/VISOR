package ucf.visor.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shared shape tokens so every button, chip, and text field reads as one
 * deliberate design language instead of the mix of CutCornerShape radii
 * (4/8/14/16dp) that had accumulated across screens.
 *
 * VISOR uses a two-tier system: [Control] (cut corner) marks anything
 * tappable — buttons, chips, text fields; [Surface] (rounded) marks
 * non-interactive containers — cards, dialogs, the snackbar.
 */
object VisorShapes {
    val Control = CutCornerShape(12.dp)
    val Surface = RoundedCornerShape(16.dp)
}
