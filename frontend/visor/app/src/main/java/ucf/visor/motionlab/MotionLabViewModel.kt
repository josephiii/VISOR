package ucf.visor.motionlab

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState
import com.meta.wearable.dat.motion.types.MotionSample
import com.meta.wearable.dat.motion.types.MotionSource
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ucf.visor.BuildConfig
import ucf.visor.motionlab.analysis.LumaGridSampler
import ucf.visor.motionlab.analysis.PhaseCorrelator
import ucf.visor.motionlab.analysis.QuickLook
import ucf.visor.motionlab.analysis.QuickLookResult
import ucf.visor.motionlab.protocol.CueStyle
import ucf.visor.motionlab.protocol.RunnerState
import ucf.visor.motionlab.protocol.Trial
import ucf.visor.motionlab.protocol.TrialFeedback
import ucf.visor.motionlab.protocol.TrialHost
import ucf.visor.motionlab.protocol.TrialOutcome
import ucf.visor.motionlab.protocol.TrialRunner
import ucf.visor.motionlab.protocol.Trials
import ucf.visor.motionlab.recording.SessionRecording
import ucf.visor.wearables.GlassesLease
import ucf.visor.wearables.GlassesStatus
import ucf.visor.wearables.preferredDevice
import ucf.visor.wearables.toGlassesStatus

/** Live numbers for the recording screen, refreshed twice a second. */
data class LiveStats(
    val motionHz: Double = 0.0,
    val videoFps: Double = 0.0,
    val motionSamples: Int = 0,
    val videoFrames: Int = 0,
    val analyzedFrames: Int = 0,
    val headSpeedDps: Double? = null,
    /** Image shift between the last two analyzed frames, in source pixels. */
    val imageShiftPx: Double? = null,
    val trackingPeak: Double? = null,
)

/** What a finished trial produced. */
data class LabResult(
    val trial: Trial,
    val outcome: TrialOutcome,
    val reason: String?,
    val file: File?,
    val quickLook: QuickLookResult?,
    val summary: String,
)

data class MotionLabUiState(
    val participant: String = "",
    val selectedTrialId: String = Trials.all.first().id,
    val runner: RunnerState = RunnerState.Idle,
    val registered: Boolean = false,
    val glasses: GlassesStatus? = null,
    val live: LiveStats = LiveStats(),
    val lastResult: LabResult? = null,
    val message: String? = null,
    /** Checking permission and opening the glasses session, before the countdown. */
    val connecting: Boolean = false,
) {
    val selectedTrial: Trial get() = Trials.byId(selectedTrialId) ?: Trials.all.first()
    val isRunning: Boolean
        get() = runner is RunnerState.Preparing || runner is RunnerState.WaitingForGlasses ||
            runner is RunnerState.Recording
    val isBusy: Boolean get() = connecting || isRunning
}

/**
 * The head-motion lab: runs a protocol trial on the glasses through MWDAT 1.0's
 * Motion capability — with the camera streaming alongside for the VOR tier —
 * and writes a `visor.imu.session/2` recording the analysis toolkit reads.
 *
 * Activity-scoped (see MotionLabScreen), so a trial keeps running if the tester
 * glances at another screen; it owns the glasses' session for the length of a
 * trial through [GlassesLease].
 *
 * Privacy by construction: camera frames are reduced to a 128x256 luma grid in
 * memory, correlated, and discarded. Only the measured shift leaves the phone —
 * no image of the wearer's surroundings is ever written to disk.
 */
class MotionLabViewModel(application: Application) :
    AndroidViewModel(application), TrialHost, TrialFeedback, LabGlassesLink.Sink {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val cues = CuePlayer()
    private val runner = TrialRunner(viewModelScope, this, this) { SystemClock.elapsedRealtime() }

    private val _uiState = MutableStateFlow(
        MotionLabUiState(participant = prefs.getString(KEY_PARTICIPANT, "") ?: ""),
    )
    val uiState: StateFlow<MotionLabUiState> = _uiState.asStateFlow()

    /** Spoken output — VISOR's shared TTS, attached by the screen. */
    @Volatile var speak: (String) -> Unit = {}

    private var link: LabGlassesLink? = null
    @Volatile private var recording: SessionRecording? = null
    @Volatile private var analyzer: FrameAnalyzer? = null
    private var statsJob: Job? = null
    private var lastRecordedStatus: GlassesStatus? = null

    // Live counters, written from the SDK's collector threads.
    private val motionCount = AtomicInteger()
    private val frameCount = AtomicInteger()
    private val analyzedCount = AtomicInteger()
    @Volatile private var headSpeedDps: Double? = null
    @Volatile private var imageShift: Double? = null
    @Volatile private var trackingPeak: Double? = null

    init {
        viewModelScope.launch {
            runner.state.collect { state -> _uiState.update { it.copy(runner = state) } }
        }
    }

    private var observing = false

    /** Called by the screen once the SDK is initialized, to follow registration and the glasses. */
    fun observeGlasses() {
        if (observing) return
        observing = true
        viewModelScope.launch {
            Wearables.registrationState.collect { state ->
                _uiState.update { it.copy(registered = state == RegistrationState.REGISTERED) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                // Between trials the preferred pair is read straight from the
                // SDK's device list; during a trial the session reports it live.
                if (!_uiState.value.isRunning) {
                    val status = runCatching { preferredDevice()?.second?.toGlassesStatus() }.getOrNull()
                    _uiState.update { it.copy(glasses = status) }
                }
                delay(1_000)
            }
        }
    }

    fun setParticipant(value: String) {
        // Codes only (bench01, P07). A name or any other identifier must never
        // go into a recording — the same rule as the web IMU Lab.
        val cleaned = value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(32)
        prefs.edit().putString(KEY_PARTICIPANT, cleaned).apply()
        _uiState.update { it.copy(participant = cleaned) }
    }

    fun selectTrial(id: String) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(selectedTrialId = id, message = null) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    /**
     * Starts the selected trial. Must be called from a user action: for camera
     * trials it may open the Meta AI app to ask for the glasses camera permission.
     */
    fun startSelectedTrial(requestPermission: suspend (Permission) -> PermissionStatus) =
        startTrial(_uiState.value.selectedTrial, requestPermission)

    /**
     * Starts [trial], which need not be one of [Trials.all] — the instrumentation
     * tests run a five-second trial against MockDeviceKit through here.
     */
    fun startTrial(trial: Trial, requestPermission: suspend (Permission) -> PermissionStatus) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(connecting = true, message = null) }
        viewModelScope.launch {
            val problem = prepareGlasses(trial, requestPermission)
            if (problem != null) {
                cues.play(CuePlayer.Sound.ABORT)
                say(problem)
                _uiState.update { it.copy(message = problem, connecting = false) }
                return@launch
            }
            _uiState.update { it.copy(lastResult = null, connecting = false) }
            runner.start(trial)
        }
    }

    fun abort() = runner.abort("Stopped from the phone")

    /** The tester ends a timed balance hold: the wearer stepped or put a foot down. */
    fun balanceLost() = runner.balanceLost()

    /** Marks the app going to the background and back — sampling may pause while hidden. */
    fun onAppVisibilityChanged(visible: Boolean) {
        mark(if (visible) "visibility_visible" else "visibility_hidden")
    }

    fun shareIntent(file: File): Intent {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/gzip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "VISOR head-motion lab: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Every recording on this phone, newest first. */
    fun savedSessions(): List<File> =
        sessionsRoot().walkTopDown().filter { it.isFile && it.name.endsWith(".json.gz") }
            .sortedByDescending { it.lastModified() }.toList()

    fun sessionsRoot(): File =
        File(getApplication<Application>().getExternalFilesDir(null), SESSIONS_DIR)

    // ---------------------------------------------------------------- set-up

    private suspend fun prepareGlasses(
        trial: Trial,
        requestPermission: suspend (Permission) -> PermissionStatus,
    ): String? {
        if (Wearables.registrationState.value != RegistrationState.REGISTERED) {
            return "VISOR is not connected to your glasses yet. Register on the Pair Glasses screen first."
        }
        val (deviceId, _) = preferredDevice()
            ?: return "No glasses found. Turn your glasses on and make sure they are connected in the Meta AI app."
        if (trial.camera) {
            val status = Wearables.checkPermissionStatus(Permission.CAMERA).getOrNull()
            if (status != PermissionStatus.Granted &&
                requestPermission(Permission.CAMERA) != PermissionStatus.Granted
            ) {
                return "This trial needs the glasses camera. Allow camera access for VISOR in the Meta AI app, then try again."
            }
        }
        if (!GlassesLease.acquire(LEASE_OWNER)) {
            return "The glasses are busy. Try again in a moment."
        }
        resetLiveStats()
        val config = LabGlassesLink.Config(camera = trial.camera)
        val newLink = LabGlassesLink(viewModelScope, this)
        link = newLink
        val problem = newLink.open(deviceId, config)
        if (problem != null) {
            releaseGlasses()
            return problem
        }
        startStatsTicker()
        return null
    }

    private fun releaseGlasses() {
        analyzer?.close()
        analyzer = null
        link?.close()
        link = null
        statsJob?.cancel()
        statsJob = null
        GlassesLease.release(LEASE_OWNER)
    }

    // ------------------------------------------------------------- TrialHost

    override suspend fun awaitStreams(trial: Trial): String? {
        // Not `link?.awaitData() ?: …`: awaitData returns null for *ready*, and
        // an elvis would turn every successful start into a failure.
        val active = link ?: return "The glasses session is not running."
        return active.awaitData(STREAM_TIMEOUT_MS)
    }

    override fun beginRecording(trial: Trial) {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val epochMs = System.currentTimeMillis()
        val participant = _uiState.value.participant.ifBlank { "anon" }
        val sessionId = "${trial.id}_$epochMs"
        val meta = mutableMapOf<String, Any?>(
            "sessionId" to sessionId,
            "participant" to participant,
            "trialId" to trial.id,
            "trialTitle" to trial.title,
            "tier" to trial.tier,
            "worn" to trial.worn,
            "prepSec" to trial.prepSec,
            "plannedDurationSec" to trial.durationSec,
            "purpose" to trial.purpose,
            "camera" to trial.camera,
            "cue" to trial.cue?.let {
                mapOf(
                    "intervalMs" to it.intervalMs, "steps" to it.steps, "style" to it.style.name,
                    "jitterMs" to it.jitterMs, "firstAtMs" to it.firstAtMs, "count" to it.count,
                )
            },
            "condition" to trial.condition.ifEmpty { null },
            "timedHold" to trial.timedHold,
            "platform" to "android-mwdat",
            "sdk" to mapOf("name" to "Meta Wearables Device Access Toolkit", "version" to BuildConfig.MWDAT_VERSION),
            "motion" to mapOf("samplingRateHz" to LabGlassesLink.Config(trial.camera).samplingRateHz),
            "video" to if (trial.camera) {
                val config = LabGlassesLink.Config(camera = true)
                mapOf(
                    "videoQuality" to config.videoQuality.name,
                    "frameRateRequested" to config.frameRate,
                    "compressVideo" to false,
                    "analysisGrid" to mapOf(
                        "longSide" to LumaGridSampler.DEFAULT_LONG_SIDE,
                        "shortSide" to LumaGridSampler.DEFAULT_SHORT_SIDE,
                        "method" to "phase correlation; Hann window; Gaussian spectral weight " +
                            "${PhaseCorrelator.DEFAULT_BANDWIDTH} cycles/px; 3-point Gaussian sub-pixel fit",
                        "imagesStored" to false,
                    ),
                )
            } else null,
            "glassesAtStart" to _uiState.value.glasses?.toMeta(),
            "phone" to mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "androidSdk" to Build.VERSION.SDK_INT,
            ),
            "app" to mapOf("versionName" to BuildConfig.VERSION_NAME, "versionCode" to BuildConfig.VERSION_CODE),
        )
        val newRecording = SessionRecording(meta, nowNanos, epochMs, Instant.ofEpochMilli(epochMs).toString())
        if (trial.camera) {
            analyzer = FrameAnalyzer(viewModelScope) { result ->
                newRecording.frameAnalysis(
                    row = result.row,
                    shiftX = result.shiftX,
                    shiftY = result.shiftY,
                    peak = result.estimate.peak,
                    textureSd = result.estimate.textureSd,
                    referenceRow = result.referenceRow,
                    analysisMs = result.analysisMs,
                )
                analyzedCount.incrementAndGet()
                trackingPeak = result.estimate.peak
                imageShift = hypot(result.shiftX, result.shiftY)
            }
        }
        lastRecordedStatus = _uiState.value.glasses
        recording = newRecording
    }

    override fun mark(label: String, extra: Map<String, Any?>?) {
        recording?.mark(SystemClock.elapsedRealtimeNanos(), label, extra)
    }

    override suspend fun endTrial(trial: Trial, outcome: TrialOutcome, reason: String?, recorded: Boolean) {
        val finished = recording
        recording = null
        finished?.stop(SystemClock.elapsedRealtimeNanos())
        val droppedFrames = analyzer?.droppedFrames ?: 0
        releaseGlasses()

        if (!recorded || finished == null) {
            _uiState.update {
                it.copy(lastResult = LabResult(trial, outcome, reason, null, null, outcomeSentence(trial, outcome, reason)))
            }
            return
        }
        finished.putMeta("outcome", outcome.name.lowercase())
        finished.putMeta("outcomeReason", reason)
        finished.putMeta("glassesAtEnd", _uiState.value.glasses?.toMeta())
        finished.putMeta("framesNotAnalyzed", droppedFrames)
        // A timed hold's length is its result, so it goes into the file itself.
        if (trial.timedHold) {
            val start = finished.markTimes("trial_start").firstOrNull()
            val end = finished.markTimes("trial_end").lastOrNull()
            if (start != null && end != null) finished.putMeta("holdSec", (end - start) / 1000.0)
        }

        val participant = (finished.metaValue("participant") as? String) ?: "anon"
        val sessionId = finished.metaValue("sessionId") as String
        val file = File(sessionsRoot(), "$participant/${trial.id}/$sessionId.json.gz")
        val saved = withContext(Dispatchers.IO) {
            runCatching { finished.writeGzip(file) }
                .onFailure { Log.e(TAG, "Could not save $file", it) }
                .isSuccess
        }
        val quickLook = withContext(Dispatchers.Default) { quickLook(trial, outcome, finished) }
        // A battery runs in order: once a trial ends with a usable result, the
        // next one is selected, so the tester only has to press Start again.
        val next = nextInBattery(trial, outcome)
        val summary = summarize(trial, outcome, reason, quickLook, saved) +
            (next?.let { " Next: ${it.title}." } ?: "")
        _uiState.update {
            it.copy(
                lastResult = LabResult(trial, outcome, reason, file.takeIf { saved }, quickLook, summary),
                selectedTrialId = next?.id ?: it.selectedTrialId,
            )
        }
    }

    private fun nextInBattery(trial: Trial, outcome: TrialOutcome): Trial? {
        if (trial.tier != BATTERY_TIER) return null
        if (outcome != TrialOutcome.COMPLETED && outcome != TrialOutcome.BALANCE_LOST) return null
        val tier = Trials.all.filter { it.tier == BATTERY_TIER }
        val index = tier.indexOfFirst { it.id == trial.id }
        return if (index < 0) null else tier.getOrNull(index + 1)
    }

    // ---------------------------------------------------------- TrialFeedback

    override fun countdown(trial: Trial, secondsLeft: Int) {
        if (secondsLeft == trial.prepSec) {
            say(
                when {
                    trial.brief != null ->
                        "${trial.title}. ${trial.brief} Recording starts in $secondsLeft seconds."
                    trial.worn -> "${trial.title}. Get ready. Recording starts in $secondsLeft seconds."
                    else -> "${trial.title}. Set the glasses down now. Recording starts in $secondsLeft seconds."
                },
            )
        } else if (secondsLeft <= 3) {
            cues.play(CuePlayer.Sound.TICK)
        }
    }

    override fun waitingForGlasses(trial: Trial) {
        // Usually the streams are up long before the countdown ends; only say
        // something when the wearer is actually being kept waiting.
        if (motionCount.get() == 0 || (trial.camera && frameCount.get() == 0)) {
            say("Waiting for the glasses.")
        }
    }

    override fun recordingStarted(trial: Trial) {
        cues.play(CuePlayer.Sound.START)
    }

    override fun cue(trial: Trial, label: String, index: Int, style: CueStyle) {
        when (style) {
            CueStyle.METRONOME ->
                cues.play(if (index % 2 == 0) CuePlayer.Sound.BEAT_HIGH else CuePlayer.Sound.BEAT_LOW)
            CueStyle.TONE -> cues.play(CuePlayer.Sound.BEAT_HIGH)
            CueStyle.SPOKEN -> {
                cues.play(CuePlayer.Sound.CUE)
                say(label.lowercase(Locale.US))
            }
        }
    }

    override fun finished(trial: Trial, outcome: TrialOutcome, reason: String?) {
        // A lost hold is a result, so it ends on the finish chime, not the abort tones.
        val valid = outcome == TrialOutcome.COMPLETED || outcome == TrialOutcome.BALANCE_LOST
        cues.play(if (valid) CuePlayer.Sound.FINISH else CuePlayer.Sound.ABORT)
        viewModelScope.launch {
            // The chime first; the summary once the file is written.
            delay(700)
            _uiState.value.lastResult?.let { say(it.summary) }
        }
    }

    // ----------------------------------------------------------- Link sink

    override fun onMotionSample(sample: MotionSample, phoneNanos: Long) {
        motionCount.incrementAndGet()
        if (sample.source == MotionSource.GLASSES) {
            sample.gyroscope?.let { g ->
                val dps = sqrt((g.x * g.x + g.y * g.y + g.z * g.z).toDouble()) * 180.0 / PI
                headSpeedDps = (headSpeedDps ?: dps) * 0.8 + dps * 0.2
            }
        }
        recording?.motionSample(
            phoneNanos = phoneNanos,
            deviceNanos = sample.timestampNs,
            accel = sample.accelerometer?.let { floatArrayOf(it.x, it.y, it.z) },
            gyro = sample.gyroscope?.let { floatArrayOf(it.x, it.y, it.z) },
            mag = sample.magnetometer?.let { floatArrayOf(it.x, it.y, it.z) },
            quaternionWxyz = sample.orientation?.let { floatArrayOf(it.w, it.x, it.y, it.z) },
            sourceCode = when (sample.source) {
                MotionSource.GLASSES -> 0
                MotionSource.NEURAL_BAND -> 1
                MotionSource.UNKNOWN -> 2
            },
        )
    }

    override fun onVideoFrame(frame: VideoFrame, phoneNanos: Long) {
        frameCount.incrementAndGet()
        val active = recording ?: return
        val row = active.videoFrame(phoneNanos, frame.presentationTimeUs, frame.width, frame.height)
        analyzer?.submit(row, frame.buffer, frame.width, frame.height)
    }

    override fun onEvent(label: String, extra: Map<String, Any?>) {
        Log.d(TAG, "$label $extra")
        mark(label, extra)
    }

    override fun onGlassesStatus(status: GlassesStatus) {
        _uiState.update { it.copy(glasses = status) }
        val previous = lastRecordedStatus
        if (recording != null && previous != null && previous != status) {
            // Battery steps, a doff, a fold or rising heat mid-trial are all
            // things the analysis must be able to see next to the signal.
            mark("glasses_status", status.toMeta())
        }
        lastRecordedStatus = status
    }

    override fun onFatal(reason: String) {
        Log.w(TAG, "Fatal: $reason")
        viewModelScope.launch {
            if (runner.isRunning) runner.fail(reason) else _uiState.update { it.copy(message = reason) }
        }
    }

    // ------------------------------------------------------------- helpers

    private fun say(text: String) = speak(text)

    private fun resetLiveStats() {
        motionCount.set(0)
        frameCount.set(0)
        analyzedCount.set(0)
        headSpeedDps = null
        imageShift = null
        trackingPeak = null
        _uiState.update { it.copy(live = LiveStats()) }
    }

    private fun startStatsTicker() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            var lastMotion = 0
            var lastFrames = 0
            var lastAt = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                val seconds = (now - lastAt) / 1000.0
                val motion = motionCount.get()
                val frames = frameCount.get()
                _uiState.update {
                    it.copy(
                        live = LiveStats(
                            motionHz = (motion - lastMotion) / seconds,
                            videoFps = (frames - lastFrames) / seconds,
                            motionSamples = motion,
                            videoFrames = frames,
                            analyzedFrames = analyzedCount.get(),
                            headSpeedDps = headSpeedDps,
                            imageShiftPx = imageShift,
                            trackingPeak = trackingPeak,
                        ),
                    )
                }
                lastMotion = motion
                lastFrames = frames
                lastAt = now
            }
        }
    }

    private fun quickLook(trial: Trial, outcome: TrialOutcome, rec: SessionRecording): QuickLookResult {
        val phone = rec.motionColumn("tPhone")
        val device = rec.motionColumn("tDevice")
        val gx = rec.motionColumn("gx")
        val gy = rec.motionColumn("gy")
        val gz = rec.motionColumn("gz")
        val source = rec.motionColumn("source")
        val look = QuickLook.analyze(
            motionPhoneMs = phone,
            motionDeviceMs = device,
            gyroX = gx,
            gyroY = gy,
            gyroZ = gz,
            motionSource = source,
            videoPhoneMs = rec.videoColumn("tPhone"),
            videoPtsMs = rec.videoColumn("tPts"),
            shiftX = rec.videoColumn("shiftX"),
            shiftY = rec.videoColumn("shiftY"),
            peak = rec.videoColumn("peak"),
            textureSd = rec.videoColumn("textureSd"),
            refIndex = rec.videoColumn("refIndex"),
        )
        val start = rec.markTimes("trial_start").firstOrNull()
        val end = rec.markTimes("trial_end").lastOrNull()
        val balance = if (trial.timedHold && start != null && end != null) {
            QuickLook.balance(phone, device, gx, gy, gz, source, start, end, outcome == TrialOutcome.BALANCE_LOST)
        } else null
        // Yaw is rotation about the glasses' +Y (up) axis: positive turns left.
        val impulses = if (trial.condition["task"] == "head_impulse") {
            QuickLook.headImpulses(phone, device, gy, source, rec.markTimes("cue"))
        } else null
        return look.copy(balance = balance, impulses = impulses)
    }

    private fun outcomeSentence(trial: Trial, outcome: TrialOutcome, reason: String?): String = when (outcome) {
        TrialOutcome.COMPLETED -> "${trial.title} complete."
        TrialOutcome.ABORTED -> "${trial.title} stopped."
        TrialOutcome.FAILED -> "${trial.title} could not finish. ${reason.orEmpty()}".trim()
        TrialOutcome.BALANCE_LOST -> "${trial.title}: balance lost."
    }

    /** What a balance or head impulse task found, for listening: whole numbers, plain words. */
    private fun taskSentences(trial: Trial, look: QuickLookResult): List<String> = buildList {
        look.balance?.let { b ->
            add(
                if (b.lost) "The hold lasted ${b.holdSec.roundToInt()} of ${trial.durationSec} seconds."
                else "Held for the full ${trial.durationSec} seconds.",
            )
            b.headSpeedRmsDps?.let { add("Head movement averaged ${"%.1f".format(Locale.US, it)} degrees per second.") }
        }
        look.impulses?.let { i ->
            add(
                "Found ${i.detected} impulses for ${i.tones} tones: ${i.left} to the left and ${i.right} to the right." +
                    (i.medianPeakDps?.let { " Typical peak head speed ${it.roundToInt()} degrees per second." } ?: ""),
            )
            if (i.slow > 0) {
                add(
                    "${i.slow} ${if (i.slow == 1) "was" else "were"} slower than " +
                        "${QuickLook.IMPULSE_RAPID_DPS.roundToInt()} degrees per second; the test needs quick, brief turns.",
                )
            }
            if (i.detected < i.tones) add("${i.tones - i.detected} tones had no clear head turn after them.")
        }
    }

    /** The spoken and on-screen summary. Plain numbers, rounded for listening. */
    private fun summarize(
        trial: Trial,
        outcome: TrialOutcome,
        reason: String?,
        look: QuickLookResult,
        saved: Boolean,
    ): String = buildList {
        add(outcomeSentence(trial, outcome, reason))
        addAll(taskSentences(trial, look))
        look.motion?.let { m ->
            add(
                "Recorded ${m.samples} motion samples at ${m.effectiveHz.roundToInt()} hertz" +
                    if (m.dropouts == 0) ", with no dropouts." else ", with ${m.dropouts} dropouts."
            )
        }
        look.video?.let { v ->
            add("Recorded ${v.samples} camera frames at ${v.effectiveHz.roundToInt()} frames per second.")
        }
        look.trackedFraction?.let { add("${(it * 100).roundToInt()} percent of frames tracked reliably.") }
        look.fit?.let { f ->
            if (f.reliable) {
                add(
                    "The image followed your head with a lag of ${f.lagMs.roundToInt()} milliseconds, " +
                        "a correlation of ${"%.2f".format(Locale.US, kotlin.math.abs(f.correlation))}, " +
                        "and a scale of ${"%.1f".format(Locale.US, f.pixelsPerDegree)} pixels per degree.",
                )
            } else {
                add(
                    "The camera image did not follow your head movement closely enough to measure. " +
                        "Try again facing a still scene with plenty of detail.",
                )
            }
        }
        addAll(look.notes)
        add(if (saved) "The recording is saved." else "The recording could not be saved.")
    }.joinToString(" ")

    override fun onCleared() {
        super.onCleared()
        recording?.let { rec ->
            // The screen went away mid-trial with the process: keep what was recorded.
            rec.stop(SystemClock.elapsedRealtimeNanos())
            rec.putMeta("outcome", "aborted")
            rec.putMeta("outcomeReason", "VISOR closed during the trial")
            val participant = (rec.metaValue("participant") as? String) ?: "anon"
            val trialId = rec.metaValue("trialId") as? String ?: "unknown"
            val sessionId = rec.metaValue("sessionId") as? String ?: "session"
            runCatching { rec.writeGzip(File(sessionsRoot(), "$participant/$trialId/$sessionId.json.gz")) }
        }
        recording = null
        releaseGlasses()
        cues.release()
    }

    companion object {
        private const val TAG = "VisorMotionLab"
        private const val PREFS = "visor_motion_lab"
        private const val KEY_PARTICIPANT = "participant"
        private const val LEASE_OWNER = "motion_lab"
        const val SESSIONS_DIR = "imu-sessions"

        /** The tier run as a battery: each usable result selects the next trial. */
        private const val BATTERY_TIER = "S"

        /** How long the lab waits for the glasses' data after the countdown. */
        private const val STREAM_TIMEOUT_MS = 25_000L
    }
}
