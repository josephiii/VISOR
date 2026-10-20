package ucf.visor.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ucf.visor.ui.components.ScreenTitle
import ucf.visor.ui.theme.VisorShapes

/**
 * The outlined card each account screen (log in, sign up, password reset)
 * holds its form in, under its [title], which is the screen's title.
 *
 * It is always the full width of the screen, less the screen's margins: sized
 * to its content, it widened when an error appeared and moved everything in
 * it, under a magnifier as well as on screen. Its content has its own margins,
 * so nothing, a long error included, runs to the card's edge.
 */
@Composable
fun AuthCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = VisorShapes.Control,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            ScreenTitle(text = title, textAlign = TextAlign.Center)
            content()
        }
    }
}
