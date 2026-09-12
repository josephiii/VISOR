package ucf.visor.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ucf.visor.R

// Material's own Button minimum (40dp tall) is below the ~48dp touch target
// low-vision users need, and aspectRatio(1f) alone doesn't guarantee a floor —
// it just keeps width and height equal, whatever size that ends up being.
private val MinCircleButtonSize = 64.dp

@Composable
fun CircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = modifier
            .sizeIn(minWidth = MinCircleButtonSize, minHeight = MinCircleButtonSize)
            .aspectRatio(1f),
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        content = content,
    )
}

@Composable
fun CaptureButton(onClick: () -> Unit) {
    CircleButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = stringResource(R.string.capture_photo),
        )
    }
}
