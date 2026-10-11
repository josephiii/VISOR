package ucf.visor.motionlab.recording

import java.io.Writer
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A minimal streaming JSON writer.
 *
 * Hand-rolled rather than `org.json`/`android.util.JsonWriter` so the session
 * format is produced by plain Kotlin that the JVM unit tests exercise for real
 * (Android's JSON classes are stubs off-device), and so a ten-minute recording
 * streams straight into the gzip stream without being built in memory first.
 *
 * Numbers: `NaN` and infinities are written as `null` (see [ColumnTable]), and
 * values are rounded to a fixed number of decimals, which is what keeps a
 * 60 Hz recording small enough to upload.
 */
class JsonWriter(private val out: Writer) {

    /** One entry per open container: whether anything has been written in it yet. */
    private val hasContent = ArrayDeque<Boolean>()
    private val inObject = ArrayDeque<Boolean>()
    private var afterName = false

    fun beginObject(): JsonWriter = open('{', isObject = true)
    fun endObject(): JsonWriter = close('}', isObject = true)
    fun beginArray(): JsonWriter = open('[', isObject = false)
    fun endArray(): JsonWriter = close(']', isObject = false)

    fun name(name: String): JsonWriter {
        check(inObject.lastOrNull() == true && !afterName) { "name() outside an object" }
        if (hasContent.last()) out.write(','.code)
        markContent()
        string(name)
        out.write(':'.code)
        afterName = true
        return this
    }

    fun value(value: String?): JsonWriter {
        beforeValue()
        if (value == null) out.write("null") else string(value)
        return this
    }

    fun value(value: Boolean?): JsonWriter {
        beforeValue()
        out.write(value?.toString() ?: "null")
        return this
    }

    fun value(value: Long?): JsonWriter {
        beforeValue()
        out.write(value?.toString() ?: "null")
        return this
    }

    fun value(value: Double?, decimals: Int = DEFAULT_DECIMALS): JsonWriter {
        beforeValue()
        out.write(formatNumber(value, decimals))
        return this
    }

    /**
     * Writes any of: null, String, Boolean, integer and floating types, enums
     * (by name), Map with any keys (stringified), or Iterable/Array/DoubleArray
     * of the same — enough for session metadata.
     */
    fun any(value: Any?): JsonWriter {
        when (value) {
            null -> value(null as String?)
            is String -> value(value)
            is Boolean -> value(value)
            is Int -> value(value.toLong())
            is Long -> value(value)
            is Short -> value(value.toLong())
            is Byte -> value(value.toLong())
            is Float -> value(value.toDouble())
            is Double -> value(value)
            is Enum<*> -> value(value.name)
            is Map<*, *> -> {
                beginObject()
                for ((k, v) in value) {
                    name(k.toString())
                    any(v)
                }
                endObject()
            }

            is Iterable<*> -> {
                beginArray()
                for (v in value) any(v)
                endArray()
            }

            is Array<*> -> any(value.asList())
            is DoubleArray -> doubles(value)
            else -> value(value.toString())
        }
        return this
    }

    /** A whole column as one array — the hot path for sensor data. */
    fun doubles(
        values: DoubleArray,
        count: Int = values.size,
        decimals: Int = DEFAULT_DECIMALS,
    ): JsonWriter {
        beforeValue()
        out.write('['.code)
        for (i in 0 until count) {
            if (i > 0) out.write(','.code)
            out.write(formatNumber(values[i], decimals))
        }
        out.write(']'.code)
        return this
    }

    fun flush() = out.flush()

    private fun open(bracket: Char, isObject: Boolean): JsonWriter {
        beforeValue()
        out.write(bracket.code)
        hasContent.addLast(false)
        inObject.addLast(isObject)
        return this
    }

    private fun close(bracket: Char, isObject: Boolean): JsonWriter {
        check(inObject.lastOrNull() == isObject && !afterName) { "Unbalanced '$bracket'" }
        hasContent.removeLast()
        inObject.removeLast()
        out.write(bracket.code)
        return this
    }

    private fun beforeValue() {
        if (afterName) {
            // The name already wrote the separator for this object member.
            afterName = false
            return
        }
        val container = inObject.lastOrNull() ?: return // a top-level value
        check(!container) { "A value inside an object needs a name() first" }
        if (hasContent.last()) out.write(','.code)
        markContent()
    }

    private fun markContent() {
        hasContent.removeLast()
        hasContent.addLast(true)
    }

    private fun string(value: String) {
        out.write('"'.code)
        for (ch in value) {
            when {
                ch == '"' -> out.write("\\\"")
                ch == '\\' -> out.write("\\\\")
                ch == '\n' -> out.write("\\n")
                ch == '\r' -> out.write("\\r")
                ch == '\t' -> out.write("\\t")
                ch < ' ' -> out.write(String.format("\\u%04x", ch.code))
                else -> out.write(ch.code)
            }
        }
        out.write('"'.code)
    }

    companion object {
        const val DEFAULT_DECIMALS = 6

        private val POWERS = DoubleArray(10) { Math.pow(10.0, it.toDouble()) }

        /**
         * Rounds to [decimals] places and prints the shortest form that reads
         * back to that value. Integral values print without a fraction.
         */
        fun formatNumber(value: Double?, decimals: Int): String {
            if (value == null || value.isNaN() || value.isInfinite()) return "null"
            val scale = POWERS[decimals.coerceIn(0, POWERS.size - 1)]
            // Past ~9e18 the rounding would overflow a Long; print as-is.
            if (abs(value) * scale >= 9.0e18) return value.toString()
            val rounded = (value * scale).roundToLong() / scale
            val asLong = rounded.toLong()
            if (rounded == asLong.toDouble() && abs(rounded) < 1e15) return asLong.toString()
            return rounded.toString()
        }
    }
}
