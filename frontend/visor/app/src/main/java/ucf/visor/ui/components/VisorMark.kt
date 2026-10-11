package ucf.visor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ucf.visor.ui.theme.AppTheme
import ucf.visor.ui.theme.VisorTheme

private const val BandLeft = 23f
private const val BandTop = 34f
private const val BandRight = 85f
private const val BandBottom = 74f
private const val BandCorner = 14f

private const val BridgeHalfWidth = 14f
private const val BridgeDepth = 6f

private const val ApertureRadius = 11f
private const val IrisRadius = 6f

private const val CenterX = (BandLeft + BandRight) / 2f
private const val CenterY = 54f

private const val InkLeft = BandLeft
private const val InkTop = BandTop
private const val InkWidth = BandRight - BandLeft
private const val InkHeight = BandBottom - BandTop
private const val MarkAspectRatio = InkWidth / InkHeight

/**
 * VISOR's brand mark - a visor band with an aperture cut through its center and
 * a nose bridge swept up out of its lower edge, so the silhouette reads as a
 * pair of glasses. The same silhouette the launcher icon and the splash screen
 * use, so the mark a user taps is the mark that greets them.
 *
 * Drawn in a single [color] on purpose. A two-tone mark would need its own
 * contrast check against each of VISOR's four themes, whereas one color taken
 * from the surrounding [LocalContentColor] is guaranteed to clear 4.5:1
 * wherever it is placed - and the aperture stays legible because it is a real
 * hole showing the surface behind it, not a second fill.
 *
 * Sized by height: give it a [Modifier.height] and the width follows from the
 * mark's proportions.
 */
@Composable
fun VisorMark(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    // Built in grid units, so it only has to be built once however large the
    // mark is drawn; the scale to pixels happens in the transform below.
    val band = remember { buildBandPath() }
    val iris = remember { buildIrisPath() }

    Canvas(modifier = modifier.aspectRatio(MarkAspectRatio)) {
        withTransform({
            scale(
                scaleX = size.height / InkHeight,
                scaleY = size.height / InkHeight,
                pivot = Offset.Zero,
            )
            translate(left = -InkLeft, top = -InkTop)
        }) {
            drawPath(path = band, color = color)
            drawPath(path = iris, color = color)
        }
    }
}

/**
 * The band, its bridge, and the aperture as one path. [PathFillType.EvenOdd]
 * turns the aperture into a hole rather than a second filled disc, which is what
 * lets the mark flatten to a single tone without losing its center.
 */
private fun buildBandPath() = Path().apply {
    fillType = PathFillType.EvenOdd

    // Corner arcs are quarter turns clockwise, starting from the point the
    // preceding line ends on. Angles are the Android convention: 0 degrees at
    // three o'clock, growing clockwise.
    moveTo(BandLeft + BandCorner, BandTop)
    lineTo(BandRight - BandCorner, BandTop)
    arcTo(topRightCorner(), -90f, 90f, false)
    lineTo(BandRight, BandBottom - BandCorner)
    arcTo(bottomRightCorner(), 0f, 90f, false)

    // The nose bridge: half an ellipse swept up INTO the band's lower edge. The
    // negative sweep is what carries it over the top of the ellipse; a positive
    // one would hang the bridge below the band instead of cutting into it.
    lineTo(CenterX + BridgeHalfWidth, BandBottom)
    arcTo(bridgeBounds(), 0f, -180f, false)

    lineTo(BandLeft + BandCorner, BandBottom)
    arcTo(bottomLeftCorner(), 90f, 90f, false)
    lineTo(BandLeft, BandTop + BandCorner)
    arcTo(topLeftCorner(), 180f, 90f, false)
    close()

    addOval(Rect(center = Offset(CenterX, CenterY), radius = ApertureRadius))
}

private fun buildIrisPath() = Path().apply {
    addOval(Rect(center = Offset(CenterX, CenterY), radius = IrisRadius))
}

private fun cornerBounds(centerX: Float, centerY: Float) =
    Rect(center = Offset(centerX, centerY), radius = BandCorner)

private fun topLeftCorner() = cornerBounds(BandLeft + BandCorner, BandTop + BandCorner)
private fun topRightCorner() = cornerBounds(BandRight - BandCorner, BandTop + BandCorner)
private fun bottomLeftCorner() = cornerBounds(BandLeft + BandCorner, BandBottom - BandCorner)
private fun bottomRightCorner() = cornerBounds(BandRight - BandCorner, BandBottom - BandCorner)

private fun bridgeBounds() = Rect(
    left = CenterX - BridgeHalfWidth,
    top = BandBottom - BridgeDepth,
    right = CenterX + BridgeHalfWidth,
    bottom = BandBottom + BridgeDepth,
)
