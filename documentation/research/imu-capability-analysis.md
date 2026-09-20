# IMU Capability Analysis — Meta Ray-Ban Display

*Analytical framework for VISOR's inertial-sensing work*

> **Status: framework and decision criteria defined; measurements pending.**
> Every viability judgement below is stated as a *gate* — the measurement that
> would decide it — not as a conclusion. Measured values are written by
> `analysis/analyze.py` into
> [imu-characterization-report.md](../testing/imu-characterization-report.md).
> Where this document records an observation, it is labelled as such with its
> confidence. Nothing here is a result until the report carries the number.

---

## What the platform exposes

VISOR reaches the MRBD inertial sensors through the Web Apps runtime, not a
native SDK. The available surface is therefore the W3C device-motion family
plus, where implemented, the Generic Sensor API:

| API | Provides | Status |
|---|---|---|
| `devicemotion` | Acceleration (with and without gravity), rotation rate, interval | **Confirmed working**, ~60 Hz |
| `deviceorientation` | α/β/γ angles | **Confirmed working**, `absolute = true` — see below |
| `deviceorientationabsolute` | Absolute orientation, explicitly earth-referenced | Not separately required; `deviceorientation` already reports absolute |
| `Accelerometer`, `Gyroscope`, `Magnetometer` | Generic Sensor API classes | Untested |
| `AbsoluteOrientationSensor` | Fused quaternion orientation | Untested |

The IMU Lab's **Capability Probe** tests all of these in one pass and records
which construct, which start, and which actually deliver readings. That probe
output is the authoritative answer and is stamped into every session recorded
afterwards.

### Orientation: resolved, and it is absolute

Project research on MRBD capabilities records the toolkit as exposing
"accelerometer, gyroscope, and **compass**". First-light testing appeared to
contradict this: the orientation readout displayed 0.00 on all three axes for an
entire session while acceleration and rotation rate updated normally.

**That apparent gap was an instrumentation defect, not a platform limitation.**
The first readout build shared a single render-throttle timestamp between the
`devicemotion` and `deviceorientation` handlers, so the faster stream consumed
every render slot; it also coerced `null` to `0` and initialized the display to
`0.00`, making "never fired", "fired with nulls" and "genuine zero"
indistinguishable. Both defects are fixed.

Recorded sessions confirm the compass is present and working:

| Observation | Value |
|---|---|
| `deviceorientation` samples recorded | 3,847 across 5 sessions |
| Fully-null α/β/γ columns | none |
| `absolute` flag | `true` on every sample |
| α (heading) range within one yaw trial | 60.3° – 173.6° |
| Stream rate, device at rest | 57.9 – 60.4 Hz |
| Stream rate, during active head movement | ~49.5 Hz |

The `absolute = true` flag is the significant part: the orientation is
earth-referenced rather than an arbitrary relative frame, which means a true
compass heading is available. Every use case below that would otherwise have
been confined to a drift-bounded gyro-integration window can instead use an
absolute reference.

One behaviour worth carrying forward: the orientation stream **degrades under
motion**, dropping from ~60 Hz at rest to ~49.5 Hz during sustained head
turning, while `devicemotion` held 60.0 Hz through the same trial. Anything
depending on orientation sampling during vigorous movement should not assume
parity with the motion stream.

### Confirmed timing behaviour

Sample-rate and jitter statistics do not require a completed or stationary
recording, so these hold despite most first-light trials being cut short:

| Measure | Observed (5 sessions) |
|---|---|
| `devicemotion` effective rate | 60.0 – 60.5 Hz |
| Median inter-sample interval | 16.70 ms |
| Interval SD | 1.09 – 1.92 ms |
| p99 interval | 19.1 – 22.8 ms |
| Longest gap | 42.2 ms (worst, during active head movement) |
| Dropouts (> 3× median) | **0 across every session** |
| Platform-reported interval | 16 ms (62.5 Hz nominal) |

This is a well-behaved stream: the delivered rate sits just under the advertised
62.5 Hz, jitter is around 1–2 ms, and no session dropped a sample. Integration
and spectral estimates are safe at this stability. Worth noting that the
platform's self-reported 62.5 Hz slightly overstates what is actually delivered
(~60 Hz), so derive rate from timestamps rather than trusting `event.interval`.

---

## Measurement glossary

Why these specific numbers, in the terms a reviewer will apply:

**Effective sample rate and jitter.** The rate actually delivered to the app,
not the advertised one. Head movements occupy roughly 0–10 Hz, so a stable
~60 Hz stream is comfortable; the risk is not the mean rate but *variance*,
because irregular sampling biases any integration or spectral estimate.

**Noise floor (σ).** Per-axis standard deviation at rest. Sets the smallest
movement distinguishable from nothing, which directly bounds a stillness
threshold.

**Bias / zero-rate offset.** What the gyro reads when not rotating. Integrated
over time this *is* the drift, so it determines how long a gyro-derived angle
stays trustworthy.

**Allan deviation.** The standard IMU characterization curve: deviation against
averaging time. Its −1/2 slope gives **random walk** (how fast integration
accumulates error) and its minimum gives **bias instability** (the noise floor
of the bias itself). Together these answer the operationally decisive question:
*how long can we integrate gyro rate into an angle before the answer is
useless?* Without absolute heading, that window is the binding constraint on
half the use cases below.

**Saturation.** Whether rapid head turns exceed the sensor's range. A clipped
peak makes every derived amplitude a lower bound rather than a measurement.

---

## Use-case viability for VISOR

Each row states what the capability requires, what measures it, and the gate
that decides it. TBI and low-vision relevance is the reason each is here.

### 1. Head-stability gated capture — *highest-value, lowest-risk*

Trigger OCR/VLM capture only once the wearer's head has settled, instead of
firing continuously.

- **Why it matters:** low-vision users hold still to attend to something. Gating
  on that intent yields sharper frames, fewer spurious descriptions, and
  materially less compute and battery per useful result. It improves the core
  product, not a peripheral feature.
- **Requires:** angular-speed noise floor well below the speed of ordinary head
  motion.
- **Measured by:** C1 (stillness hold) against C6 (unstructured baseline).
- **Gate:** the C1 95th percentile must sit clearly below the C6 median. If the
  distributions overlap, gating will both miss intended captures and fire during
  ordinary movement.

### 2. Compensatory scanning measurement — *core rehabilitation case*

Quantify the horizontal scanning taught in hemianopia and visual-field-loss
training: amplitude, rate, and left/right symmetry.

- **Why it matters:** compensatory scanning training is standard care for
  homonymous hemianopia, and adherence outside the clinic is normally
  self-reported. An instrumented measure of whether the user actually scans into
  the affected field — and feedback when they do not — is a genuine clinical
  contribution rather than a convenience feature.
- **Requires:** yaw-rate integration trustworthy over a single sweep (1–3 s).
- **Measured by:** C2, with the integration window bounded by A1/A2 drift.
- **Gate:** integrated sweep amplitude must be repeatable within ~10% across
  sweeps of equal commanded size, and the drift over 3 s must be small relative
  to a typical 30–60° sweep.

### 3. Vestibular / gaze-stabilization exercise adherence

Measure performance of prescribed head-movement exercises (for example VOR×1
gaze stabilization), which specify amplitude and frequency.

- **Why it matters:** these exercises are routinely prescribed after TBI, and
  compliance and correct execution are largely unverifiable today. The paced
  sweep trials are already the same movement structure the exercises use.
- **Requires:** accurate rate amplitude and frequency at 0.5–2 Hz.
- **Measured by:** B1–B3 paced sweeps; cue-aligned segmentation gives commanded
  vs performed directly.
- **Gate:** per-cue peak rate and integrated amplitude must be recoverable with
  low variance across repetitions at a fixed commanded amplitude.

### 4. Activity and context classification

Distinguish walking / seated / reading to adapt assistance verbosity and timing.

- **Why it matters:** a system that narrates at length while someone is crossing
  a street is unsafe; one that is terse while they are seated and reading is
  unhelpful. Context is a safety feature.
- **Requires:** separable signatures between states.
- **Measured by:** C3 (gait), C5 (reading posture), C6 (baseline).
- **Gate:** gait spectral peak prominence high enough for confident detection,
  and reading posture separable from stationary-idle.

### 5. Gait and mobility metrics

Step cadence and head-bob variability from a head-mounted IMU.

- **Why it matters:** gait variability is an established mobility and fall-risk
  indicator, and relevant to TBI balance impairment. Head-mounted measurement is
  less validated than waist- or ankle-mounted, so this is exploratory.
- **Measured by:** C3.
- **Gate:** cadence recoverable with high spectral prominence. Treat absolute
  clinical gait parameters as out of scope without a validated reference.

### 6. Dwell-based interaction — *accessibility path*

Head-hold as a selection mechanism, as an alternative to the Neural Band pinch.

- **Why it matters:** the pinch gesture assumes hand function. Users with TBI may
  have motor impairment that makes it unreliable. A head-dwell selector widens
  access, which matters both clinically and for Section 508 conformance.
- **Requires:** the same threshold as use case 1, plus stable dwell durations.
- **Measured by:** C1 dwell interval statistics.
- **Gate:** dwell intervals of ≥ 0.5 s must be detectable without false
  triggering during ordinary viewing.

### 7. Postural transitions

Detect sit-to-stand events.

- **Measured by:** C4.
- **Gate:** transition signature separable from ordinary head movement.
- **Note:** *fall detection is explicitly out of scope.* It requires free-fall
  and impact signatures that this battery does not attempt and that cannot be
  safely elicited. It must not be claimed on the basis of C4.

---

## Platform constraints

These bound any production integration regardless of how the measurements come
out:

- **Foreground only.** A Web App captures only while running in the foreground.
  Passive all-day monitoring is not achievable in this architecture. Any
  longitudinal-monitoring claim would require the native MWDAT path instead.
- **Browser-mediated data.** Values may be filtered, fused, or rate-limited
  relative to raw hardware. This characterizes the platform as available to a
  Web App — the correct scope for VISOR's current architecture, but not
  hardware datasheet figures.
- **Orientation rate is not guaranteed.** Absolute heading is available, but the
  orientation stream slows under motion (~49.5 Hz vs 60.0 Hz for
  `devicemotion` in the same trial). Do not assume the two streams are
  sample-aligned; join them on timestamp, not index.
- **Bandwidth ceiling.** At ~60 Hz the Nyquist limit is 30 Hz. Head movement
  sits well inside that; high-frequency tremor and impact transients do not.
- **Battery and thermal.** Quantified by D1, and a real constraint on any
  always-sensing design.
- **Single device, single wearer.** Until the battery is repeated, results
  characterize one unit and one person's movement.

---

## Open questions

Carried forward from [meta-mrbd-capabilities.md](./meta-mrbd-capabilities.md)
and extended:

1. ~~Does any API path expose absolute heading?~~ **Answered: yes.**
   `deviceorientation` reports `absolute = true` with populated α/β/γ.
2. What is the usable gyro integration window before drift dominates?
   Still open — no valid stationary recording yet. Less critical now that
   absolute heading is available, but still needed to characterize the sensor
   and to bound short-window relative measurements between orientation samples.
3. Does the sample rate hold under sustained load, or degrade thermally? (D1.)
4. What is the end-to-end latency of the Neural Band input chain? — still open;
   requires a synchronized external trigger this battery does not provide.
5. How much does unit-to-unit variation matter? — requires a second device.
6. Do the Tier C signatures hold for users with impaired movement, or are they
   an artefact of unimpaired execution? — requires IRB-approved participant
   data and is the natural next phase.

---

## Related documents

- [imu-test-protocol.md](../testing/imu-test-protocol.md) — trial battery and methodology
- [imu-characterization-report.md](../testing/imu-characterization-report.md) — generated results
- [meta-mrbd-capabilities.md](./meta-mrbd-capabilities.md) — platform capability research
- [low-vision-research.md](./low-vision-research.md) — user population background
- `analysis/` — the analysis toolkit that produces the report
