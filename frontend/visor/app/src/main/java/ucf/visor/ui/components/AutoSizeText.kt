package ucf.visor.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A single-line [Text] that shrinks its own font size in small steps until it fits
 * the available width, instead of wrapping or getting clipped.
 *
 * VISOR defaults to a large text-size preference (see UserProfile.textScale), which
 * is exactly what makes short button/chip labels ("Extra large", "Standard") prone
 * to wrapping in tight spaces — three chips in a row, for instance. Wrapping isn't
 * itself an accessibility failure, but on a fixed-height control it either clips a
 * line or forces uneven, hard-to-scan button heights. Shrinking to fit keeps every
 * label on one line and every chip the same height — down to [minFontSize], never
 * past it, so a label never becomes smaller than the app's own established floor
 * (see Type.kt's labelSmall: "this app shouldn't ship any text smaller than that").
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    minFontSize: TextUnit = 14.sp,
) {
    var fontSize by remember(text, style) { mutableStateOf(style.fontSize) }
    var readyToDraw by remember(text, style) { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            val next = fontSize * 0.92f
            if (result.didOverflowWidth && next.value >= minFontSize.value) {
                fontSize = next
            } else {
                readyToDraw = true
            }
        },
    )
}
