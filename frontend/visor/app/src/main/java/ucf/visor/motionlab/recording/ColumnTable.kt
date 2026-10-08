package ucf.visor.motionlab.recording

/**
 * A growable table of `Double` columns, written row by row.
 *
 * Mirrors the web IMU Lab's capture engine (frontend/visor-webapp/capture.js)
 * and its first rule: **a reading that was not supplied is `NaN`, never 0.**
 * It serializes as JSON `null`, and the analysis pipeline carries it as `NaN`
 * end to end. "The glasses sent no magnetometer" and "the magnetometer read
 * zero" are different findings, and conflating them fabricates data.
 *
 * Rows can be amended after they are appended ([set]) — a video frame's row is
 * written the moment it arrives and its image-shift columns are filled in once
 * the analysis worker gets to it. Null counts are therefore computed when the
 * table is serialized, not tracked on append.
 *
 * Not thread-safe on its own; [SessionRecording] guards every access.
 */
class ColumnTable(val names: List<String>) {

    private val columns = Array(names.size) { DoubleArray(INITIAL_CAPACITY) }
    private val indexByName = names.withIndex().associate { (i, name) -> name to i }

    var size: Int = 0
        private set

    init {
        require(names.toSet().size == names.size) { "Duplicate column names: $names" }
    }

    /** Appends one row; [values] are in [names] order. Returns the new row's index. */
    fun append(vararg values: Double): Int {
        require(values.size == names.size) {
            "Expected ${names.size} values (${names.joinToString()}), got ${values.size}"
        }
        ensureCapacity(size + 1)
        for (c in values.indices) columns[c][size] = values[c]
        return size++
    }

    /** Overwrites one cell of an existing row. */
    operator fun set(row: Int, name: String, value: Double) {
        require(row in 0 until size) { "Row $row out of range (size $size)" }
        columns[indexOf(name)][row] = value
    }

    operator fun get(row: Int, name: String): Double {
        require(row in 0 until size) { "Row $row out of range (size $size)" }
        return columns[indexOf(name)][row]
    }

    /** A copy of one column, trimmed to [size]. */
    fun column(name: String): DoubleArray = columns[indexOf(name)].copyOf(size)

    /** How many rows hold `NaN` in each column. */
    fun nullCounts(): Map<String, Int> = names.withIndex().associate { (c, name) ->
        var nulls = 0
        val column = columns[c]
        for (r in 0 until size) if (column[r].isNaN()) nulls++
        name to nulls
    }

    private fun indexOf(name: String): Int =
        indexByName[name] ?: throw IllegalArgumentException("Unknown column '$name'")

    private fun ensureCapacity(needed: Int) {
        val current = columns[0].size
        if (needed <= current) return
        val grown = maxOf(needed, current * 2)
        for (c in columns.indices) columns[c] = columns[c].copyOf(grown)
    }

    private companion object {
        const val INITIAL_CAPACITY = 1024
    }
}
