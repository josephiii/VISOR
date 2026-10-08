package ucf.visor.motionlab

import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ucf.visor.motionlab.analysis.GridGeometry
import ucf.visor.motionlab.analysis.LumaGridSampler
import ucf.visor.motionlab.analysis.PhaseCorrelator
import ucf.visor.motionlab.analysis.ShiftEstimate
import java.nio.ByteBuffer

/**
 * Measures the image shift between consecutive camera frames without ever
 * holding up the SDK's video stream.
 *
 * The frame collector does only the cheap part — box-averaging the luma plane
 * into a small grid (about a millisecond) — and hands the grid to a single
 * worker that runs phase correlation. The hand-off is bounded: if the worker
 * falls behind, the newest frame is still *recorded* (its timing row exists)
 * but not analyzed, and the next analyzed frame is measured against the last
 * one that was, with [Result.referenceRow] saying which. Nothing blocks, and
 * nothing is silently paired with the wrong frame.
 *
 * When the stream's resolution changes mid-trial (MWDAT's bandwidth ladder can
 * step it down), grids from before and after are not comparable, so the
 * correlator restarts and the first frame at the new size gets no shift.
 */
class FrameAnalyzer(
    scope: CoroutineScope,
    private val onResult: (Result) -> Unit,
) {
    data class Result(
        val row: Int,
        val referenceRow: Int,
        val estimate: ShiftEstimate,
        val geometry: GridGeometry,
        val analysisMs: Double,
    ) {
        /** The shift in source-frame pixels. */
        val shiftX: Double get() = estimate.dx * geometry.scale
        val shiftY: Double get() = estimate.dy * geometry.scale
    }

    private class Work(val row: Int, val grid: DoubleArray, val geometry: GridGeometry)

    private val sampler = LumaGridSampler()
    private val gridSize = LumaGridSampler.DEFAULT_LONG_SIDE * LumaGridSampler.DEFAULT_SHORT_SIDE
    private val pool = ArrayBlockingQueue<DoubleArray>(POOL_SIZE).apply {
        repeat(POOL_SIZE) { offer(DoubleArray(gridSize)) }
    }
    private val queue = Channel<Work>(capacity = QUEUE_CAPACITY)

    @Volatile var droppedFrames = 0
        private set

    private val worker: Job = scope.launch(Dispatchers.Default) {
        var correlator: PhaseCorrelator? = null
        var lastGeometry: GridGeometry? = null
        var previousRow = -1
        for (work in queue) {
            val started = SystemClock.elapsedRealtimeNanos()
            if (work.geometry != lastGeometry) {
                correlator = PhaseCorrelator(work.geometry.gridWidth, work.geometry.gridHeight)
                lastGeometry = work.geometry
            }
            val estimate = correlator!!.push(work.grid)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
            if (estimate != null && previousRow >= 0) {
                onResult(Result(work.row, previousRow, estimate, work.geometry, elapsedMs))
            }
            previousRow = work.row
            pool.offer(work.grid)
        }
    }

    /**
     * Samples [buffer] (an I420 frame) and queues it for correlation against
     * the previously queued frame. Call from the video collector only — the
     * sampler is single-threaded. Returns false when the frame was not queued.
     */
    fun submit(row: Int, buffer: ByteBuffer, width: Int, height: Int): Boolean {
        val grid = pool.poll() ?: return drop()
        val geometry = sampler.sample(buffer, width, height, grid)
        if (geometry == null) {
            pool.offer(grid)
            return drop()
        }
        if (queue.trySend(Work(row, grid, geometry)).isFailure) {
            pool.offer(grid)
            return drop()
        }
        return true
    }

    fun close() {
        queue.close()
        worker.cancel()
    }

    private fun drop(): Boolean {
        droppedFrames++
        return false
    }

    private companion object {
        const val QUEUE_CAPACITY = 3
        const val POOL_SIZE = QUEUE_CAPACITY + 2
    }
}
