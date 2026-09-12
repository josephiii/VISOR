package ucf.visor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A single icon + text tip row, used in the Getting Started sheet and the
 * Hardware Pairing screen. [title] is optional: the Getting Started sheet's
 * tips are single lines with no heading, while Hardware Pairing's environment
 * capture tip pairs a heading with a description.
 */
@Composable
fun TipItem(
    iconResId: Int,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = "Tip icon",
            modifier = Modifier
                .padding(start = 4.dp, top = 4.dp)
                .width(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
