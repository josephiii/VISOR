package ucf.visor.capture

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** This picture scaled down to [maxWidth] pixels across, keeping its shape; itself when already that small. */
fun Bitmap.scaledDownToWidth(maxWidth: Int): Bitmap {
    if (width <= maxWidth) return this
    val height = (height * maxWidth.toFloat() / width).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, maxWidth, height, true)
}
