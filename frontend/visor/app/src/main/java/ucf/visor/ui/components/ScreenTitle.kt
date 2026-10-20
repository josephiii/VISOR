package ucf.visor.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

/**
 * A screen's title, at its top: what a user checks to know where they are,
 * and the heading a screen reader user jumps to first. Every screen has one,
 * in sentence case ("Pair glasses", not "PAIR GLASSES"): capitals take away
 * the word shapes low-vision readers rely on.
 */
@Composable
fun ScreenTitle(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        modifier = modifier.semantics { heading() },
    )
}
