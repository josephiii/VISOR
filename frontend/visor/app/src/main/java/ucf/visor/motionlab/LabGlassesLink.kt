package ucf.visor.motionlab

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.motion.Motion
import com.meta.wearable.dat.motion.addMotion
import com.meta.wearable.dat.motion.removeMotion
import com.meta.wearable.dat.motion.types.MotionConfiguration
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSamplingRate
import com.meta.wearable.dat.motion.types.MotionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ucf.visor.wearables.GlassesStatus
import ucf.visor.wearables.describeSessionError
import ucf.visor.wearables.isWarningOnly
import ucf.visor.wearables.toGlassesStatus

/**
 * The head-motion lab's DAT session: one `DeviceSession` carrying the Motion
 * capability and, for camera trials, the Camera capability's video stream.
 *
 * What this has to get right, much of it learned from Meta's BirdSpotter
 * sample (which streams the camera and IMU together):
 *
 * - **Motion attaches after the camera is streaming.** A capability started
 *   while the device is still arriving fails with `DEVICE_DISCONNECTED`. The
 *   camera's stream waits for the device; its first STREAMING is the proof
 *   the device is really there, so Motion is attached off that beat. An
 *   IMU-only trial has no such proof and attaches on session STARTED instead,
 *   with a bounded retry for a sensor that does not come up.
 * - **Motion must be started.** `addMotion` leaves it STOPPED; without
 *   `start()` the sample flow is silent forever.
 * - **Samples do not replay.** The collector subscribes (UNDISPATCHED) before
 *   `start()`, or the first samples are lost.
 * - **A stop nobody asked for is revived, not mourned.** Something else on the
 *   link can put Motion down mid-session; it is started again, and the gap is
 *   written into the recording as marks so the analysis knows it is a hole.
 * - **Raw (decoded) video.** The lab measures image shift on the luma plane, so
 *   it asks for YUV frames (`compressVideo = false`) at the lowest resolution and
 *   highest frame rate MWDAT offers: temporal resolution matters more than pixels
 *   here, and Meta's docs note lower resolutions suffer less compression loss.
 *
 * Frame and sample callbacks run on [Dispatchers.Default], never on the main
 * thread, and must return quickly: a slow collector stalls the SDK's stream.
 */
class LabGlassesLink(
    private val scope: CoroutineScope,
    private val sink: Sink,
) {
    interface Sink {
        fun onMotionSample(sample: MotionSample, phoneNanos: Long)
        fun onVideoFrame(frame: VideoFrame, phoneNanos: Long)

        /** A lifecycle event worth a mark in the recording. */
        fun onEvent(label: String, extra: Map<String, Any?> = emptyMap())

        /** The glasses' battery, worn, hinge and thermal state changed. */
        fun onGlassesStatus(status: GlassesStatus)

        /** The session cannot continue; [reason] is fit to speak aloud. */
        fun onFatal(reason: String)
    }

    data class Config(
        val camera: Boolean,
        val videoQuality: VideoQuality = VideoQuality.LOW,
        val frameRate: Int = 30,
        val samplingRate: MotionSamplingRate = MotionSamplingRate.HZ_60,
    ) {
        val samplingRateHz: Int
            get() = when (samplingRate) {
                MotionSamplingRate.HZ_5 -> 5
                MotionSamplingRate.HZ_10 -> 10
                MotionSamplingRate.HZ_15 -> 15
                MotionSamplingRate.HZ_24 -> 24
                MotionSamplingRate.HZ_30 -> 30
                MotionSamplingRate.HZ_60 -> 60
            }
    }

    private val jobs = mutableListOf<Job>()
    private val firstMotionSample = CompletableDeferred<Unit>()
    private val firstVideoFrame = CompletableDeferred<Unit>()

    private var config: Config? = null
    private var session: DeviceSession? = null
    private var camera: Camera? = null
    private var motion: Motion? = null
    private var motionRevival: Job? = null
    private var lastSessionError: DeviceSessionError? = null
    private var streamHasStreamed = false

    @Volatile private var closing = false

    /**
     * Creates and starts a session on [deviceId]. Returns null once the session
     * is starting, or a spoken-language reason it could not be created.
     */
    suspend fun open(deviceId: DeviceIdentifier, config: Config): String? {
        this.config = config
        val created = createSessionWithRetry(deviceId) ?: return lastSessionError
            ?.let { describeSessionError(it) }
            ?: "VISOR could not start a session with the glasses."
        session = created

        jobs += scope.launch {
            created.errors.collect { error ->
                lastSessionError = error
                Log.w(TAG, "session error ${error.name}: ${error.description}")
                sink.onEvent(
                    if (error.isWarningOnly) "session_warning" else "session_error",
                    mapOf("error" to error.name, "description" to error.description),
                )
            }
        }
        jobs += scope.launch {
            created.state.collect { state ->
                Log.i(TAG, "session state $state")
                sink.onEvent("session_state", mapOf("state" to state.name))
                when (state) {
                    DeviceSessionState.STARTED ->
                        if (config.camera) attachCamera(created) else attachMotion(created)
                    DeviceSessionState.STOPPED -> if (!closing) {
                        sink.onFatal(
                            lastSessionError?.let { describeSessionError(it) }
                                ?: "The glasses ended the session.",
                        )
                    }
                    else -> Unit
                }
            }
        }
        jobs += scope.launch {
            created.deviceInfo
                .map { it.toGlassesStatus() }
                .distinctUntilChanged()
                .collect { sink.onGlassesStatus(it) }
        }
        created.start()
        return null
    }

    /**
     * Suspends until data is actually arriving — a motion sample, and a video
     * frame for camera trials — or [timeoutMs] passes. Returns null when ready,
     * otherwise what is missing, in words fit to speak.
     */
    suspend fun awaitData(timeoutMs: Long): String? {
        val wantsCamera = config?.camera == true
        val ready = withTimeoutOrNull(timeoutMs) {
            if (wantsCamera) firstVideoFrame.await()
            firstMotionSample.await()
        }
        if (ready != null) return null
        return when {
            wantsCamera && !firstVideoFrame.isCompleted ->
                "No camera frames arrived from the glasses. Check that VISOR has camera access in the Meta AI app."
            else ->
                "No motion data arrived from the glasses. Check that Motion is enabled for VISOR in the Wearables Developer Center."
        }
    }

    /** Stops every capability and the session. Safe to call more than once. */
    fun close() {
        if (closing) return
        closing = true
        motionRevival?.cancel()
        jobs.forEach { it.cancel() }
        jobs.clear()
        val activeSession = session
        motion?.stop()
        if (motion != null) activeSession?.removeMotion()
        motion = null
        camera?.stop()
        camera = null
        activeSession?.stop()
        session = null
    }

    private suspend fun createSessionWithRetry(deviceId: DeviceIdentifier): DeviceSession? {
        // The glasses allow one session per app; the navigation controller
        // releases its own when the lab takes the lease, but its stop is
        // asynchronous. SESSION_ALREADY_EXISTS in that window is worth waiting out.
        repeat(SESSION_ATTEMPTS) { attempt ->
            val result = Wearables.createSession(SpecificDeviceSelector(deviceId))
            val created = result.getOrNull()
            if (created != null) return created
            val error = result.errorOrNull()
            lastSessionError = error
            Log.w(TAG, "createSession attempt ${attempt + 1} failed: ${error?.name}")
            if (error != DeviceSessionError.SESSION_ALREADY_EXISTS) return null
            delay(SESSION_RETRY_MS)
        }
        return null
    }

    @SuppressLint("AutoCloseableUse") // The camera lives for the session; closed in close().
    private fun attachCamera(session: DeviceSession) {
        if (camera != null) return
        val config = config ?: return
        session.addCamera(
            StreamConfiguration(
                videoQuality = config.videoQuality,
                frameRate = config.frameRate,
                compressVideo = false,
            ),
        ).onSuccess { attached ->
            camera = attached
            val stream = attached.stream
            jobs += scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                stream.videoStream.collect { frame ->
                    if (frame.isCompressed || frame.isCodecConfig) return@collect
                    sink.onVideoFrame(frame, SystemClock.elapsedRealtimeNanos())
                    firstVideoFrame.complete(Unit)
                }
            }
            jobs += scope.launch {
                stream.state.collect { state ->
                    sink.onEvent("stream_state", mapOf("state" to state.name))
                    when (state) {
                        StreamState.STREAMING -> {
                            streamHasStreamed = true
                            attachMotion(session)
                        }
                        StreamState.STOPPED, StreamState.CLOSED ->
                            if (streamHasStreamed && !closing) {
                                sink.onFatal("The glasses camera stopped.")
                            }
                        else -> Unit
                    }
                }
            }
            jobs += scope.launch {
                stream.errorStream.collect { error ->
                    sink.onEvent("stream_error", mapOf("error" to error.name, "description" to error.description))
                    if (error == StreamError.PERMISSIONS_DENIED && !closing) {
                        sink.onFatal("VISOR does not have camera access. Allow it in the Meta AI app.")
                    }
                }
            }
            jobs += scope.launch {
                attached.state.collect { sink.onEvent("camera_state", mapOf("state" to it.name)) }
            }
            stream.start().onFailure { error, _ ->
                if (!closing) sink.onFatal("The glasses camera would not start: ${error.description}")
            }
        }.onFailure { error, _ ->
            if (!closing) sink.onFatal(describeSessionError(error))
        }
    }

    private fun attachMotion(session: DeviceSession) {
        if (motion != null || closing) return
        val config = config ?: return
        session.addMotion(MotionConfiguration(samplingRate = config.samplingRate))
            .onSuccess { attached ->
                motion = attached
                // UNDISPATCHED: `samples` does not replay, so the subscription must
                // exist before start() is called below.
                jobs += scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    attached.samples.collect { sample ->
                        sink.onMotionSample(sample, SystemClock.elapsedRealtimeNanos())
                        firstMotionSample.complete(Unit)
                    }
                }
                jobs += scope.launch {
                    attached.state.collect { state ->
                        sink.onEvent("motion_state", mapOf("state" to state.name))
                        if (state == MotionState.STOPPED && !closing) reviveMotion(attached)
                    }
                }
                jobs += scope.launch {
                    attached.errors.collect { error ->
                        if (error != null) {
                            sink.onEvent("motion_error", mapOf("error" to error.name, "description" to error.description))
                        }
                    }
                }
                attached.start()
            }
            .onFailure { error, _ ->
                if (closing) return@onFailure
                sink.onFatal(
                    if (error == DeviceSessionError.CAPABILITY_DENIED) {
                        "Motion has not been enabled for VISOR in the Wearables Developer Center."
                    } else {
                        "The glasses' motion sensors could not be attached. ${describeSessionError(error)}"
                    },
                )
            }
    }

    /**
     * Starts Motion again after it stopped without being asked to — or never
     * came up. `start()` only acts on a STOPPED capability, which makes a retry
     * harmless if the sensor recovered on its own in the meantime.
     */
    private fun reviveMotion(target: Motion) {
        // STOPPED is also the state a new capability is born in, replayed to the
        // state collector on subscription — so every attempt first waits a beat
        // and looks again, giving the initial start() its chance.
        if (motionRevival?.isActive == true) return
        motionRevival = scope.launch {
            repeat(MOTION_REVIVAL_ATTEMPTS) { attempt ->
                delay(MOTION_REVIVAL_DELAY_MS)
                if (closing || motion !== target) return@launch
                if (target.state.value != MotionState.STOPPED) return@launch
                sink.onEvent("motion_revive", mapOf("attempt" to attempt + 1))
                target.start()
            }
            if (!closing && target.state.value == MotionState.STOPPED) {
                sink.onFatal("The glasses' motion sensors stopped and would not restart.")
            }
        }
    }

    private companion object {
        const val TAG = "VisorMotionLab"
        const val SESSION_ATTEMPTS = 20
        const val SESSION_RETRY_MS = 250L
        const val MOTION_REVIVAL_ATTEMPTS = 5
        const val MOTION_REVIVAL_DELAY_MS = 1_000L
    }
}
