# IMU Test Protocol — Meta Ray-Ban Display

*Methodology for the VISOR inertial-sensor characterization programme*

> **Status: methodology defined, measurements not yet collected.**
> This document specifies *how* the programme is run. Results are written by
> `analysis/analyze.py` into
> [imu-characterization-report.md](./imu-characterization-report.md), which does
> not exist until the first real session is recorded and analyzed. Nothing in
> this file should be cited as a finding.

---

> Running a session? Use the
> **[Tester Guide](./imu-tester-guide.md)** instead — it is the
> plain-language, step-by-step version written for test runners and testers.
> This document is the formal methodology behind it.

## Purpose

Establish, with reproducible evidence, what the Meta Ray-Ban Display (MRBD)
inertial measurement unit can and cannot support through the Web Apps API, and
whether those capabilities are sufficient for VISOR's rehabilitation use cases
for users with traumatic brain injury (TBI) and low vision.

The programme answers three questions a reviewer will ask:

1. **What is the instrument?** Sample rate, timing stability, noise, bias,
   drift, range, and which APIs are actually populated.
2. **What can be recovered from it?** Which behaviours of interest survive the
   noise floor — head stillness, compensatory scanning, gait, postural change.
3. **What are the constraints?** Endurance, dropout, lifecycle behaviour, and
   the platform limits that bound any production integration.

---

## Equipment and preconditions

| Requirement | Value |
|---|---|
| Glasses | Meta Ray-Ban Display, software ≥ v125 |
| Companion app | Meta AI app ≥ v272, Developer Mode enabled |
| Neural Band | Optional; not required by any trial |
| Capture app | VISOR IMU Lab, `https://stage-visor-webapp.vercel.app` |
| Network | Wi-Fi reachable; sessions buffer locally if it drops |

Before the first session of a day:

1. Note the glasses battery percentage.
2. Open the IMU Lab and run **Capability Probe**. Record the verdict — the
   available sensor set is itself a measurement and can change across firmware.
3. Set the **Participant ID** in Settings. Use `bench01`-style codes for
   developer bench testing. Never enter a name or any other identifier.

---

## Trial battery

Thirteen trials in four tiers. Tier A establishes what the instrument is; B
establishes its dynamic limits; C tests VISOR-relevant behaviour; D tests
sustained operation.

### Preparation countdown

Every trial opens with a **preparation countdown** during which nothing is
recorded. Recording begins only when the countdown ends. This exists because
the Tier A trials are performed with the glasses off: without it, the act of
removing the glasses and setting them down was captured as though it were the
static measurement, and the resulting noise and bias figures described the
tester's hands rather than the sensor.

| Trial type | Preparation |
|---|---|
| Static (A1, A2) | 20 s — remove the glasses and lay them flat |
| Worn, stationary | 8 s — get into position and settle |
| Worn, moving (C3, C4) | 12 s — reach a clear path or a stable chair |

### Audio cues

The app plays four distinct sounds. These are not decoration: during a static
trial the glasses are face down on a table, so sound is the only channel that
can tell the tester what is happening.

| Sound | Meaning |
|---|---|
| Single short blips | Final three seconds of the countdown |
| Two rising tones | Recording has started |
| Three falling tones | Trial finished normally — safe to pick the glasses up |
| Two low tones | Trial aborted |
| Soft high blip | A cue changed (worn trials) |

### Tier A — Instrument characterization (glasses NOT worn)

| ID | Trial | Duration | Measures |
|---|---|---|---|
| A1 | Static rest | 2 min | Noise floor, accel bias, gyro zero-rate offset, Allan deviation |
| A2 | Static extended | 5 min | Bias instability, low-frequency drift at longer averaging times |

Tier A trials are performed with the glasses **resting on a solid surface, not
worn**. A worn "still" recording contains physiological tremor and is not a
valid noise-floor measurement. Neither trial uses cues, so nothing needs to be
read from the display while the glasses are on the table.

### Tier B — Dynamic response (worn)

| ID | Trial | Duration | Measures |
|---|---|---|---|
| B1 | Yaw sweeps (paced) | 1 min | Yaw response, repeatability, amplitude recovery |
| B2 | Pitch sweeps (paced) | 1 min | Pitch response, nod detection feasibility |
| B3 | Roll sweeps (paced) | 1 min | Roll response, axis cross-coupling |
| B4 | Rapid head turns | 30 s | Angular-rate range, saturation, aliasing |

### Tier C — Rehabilitation-relevant behaviour (worn)

| ID | Trial | Duration | Measures |
|---|---|---|---|
| C1 | Stillness hold | 1 min | Dwell threshold for gating capture |
| C2 | Compensatory scanning | 1.5 min | Scan amplitude, rate, left/right symmetry |
| C3 | Walking gait | 1 min | Step cadence, head-bob amplitude |
| C4 | Sit-to-stand cycles | 1.25 min | Postural-transition signature |
| C5 | Reading posture | 1 min | Sustained near-task pose, context classification |
| C6 | Unstructured baseline | 5 min | Realistic mixed activity, false-positive estimation |

### Tier D — System constraints (worn)

| ID | Trial | Duration | Measures |
|---|---|---|---|
| D1 | Endurance capture | 10 min | Rate stability, dropout, battery drain |

### Tier V — Head motion vs. camera image (worn, native app only)

Run from the Android app's **head-motion lab** (Settings → Research), which
records through MWDAT 1.0's Motion capability with the glasses' camera streaming
in the same session. The Web Apps API has no camera access, so this tier has no
web counterpart. Every other tier can be run from either capture path, with the
same ids, timings and cues.

| ID | Trial | Duration | Pacing | Measures |
|---|---|---|---|---|
| V1 | Side-to-side head turns, gaze fixed | 45 s | 1 Hz metronome | Yaw vs. horizontal image shift: lag, scale, residual discrepancy, phase at 1 Hz |
| V2 | Up-and-down nods, gaze fixed | 45 s | 1 Hz metronome | Pitch vs. vertical image shift; second camera axis, IMU-camera alignment |
| V3 | Faster side-to-side turns (seated) | 30 s | 2 Hz metronome | Where motion blur and frame rate start to limit the image measurement |
| V4 | Slow look-around | 45 s | Spoken, every 3 s | Low-blur calibration of the camera's pixels-per-degree scale |

Setup: seated about two metres from a **detailed** scene — phase correlation
needs texture, and a blank wall gives it none. The metronome's high tone means
the first step (left / up), the low tone the second. V1–V3 are the movement of
the VOR×1 gaze-stabilization exercise; the camera is head-fixed, so what it
measures is the image motion the head causes, not the eyes' response to it —
see the limits in `analysis/visor_imu/vor.py`. **No image is stored**: frames are
reduced, correlated and discarded on the phone; the recording holds one shift
estimate per frame, with the correlation peak and texture that qualify it.

Run V4 first on a new pair of glasses: the other V trials are scaled against it.

### Tier S — Balance and vestibular tasks (worn, native app only)

Five standardized tasks, run in order as a quick battery from the head-motion
lab (it lists them first and, after each usable result, selects the next).
About 12 minutes including countdowns. Analyzed by
`analysis/balance_analysis.py`, which writes one figure per task.

| ID | Task | Conditions | Each | Measures |
|---|---|---|---|---|
| S1 | Modified Romberg: feet together, arms by the sides | eyes open, eyes closed | 30 s | Head sway (AP, ML, 95% area, mean velocity, sway acceleration); eyes-closed ÷ open ratio |
| S2 | Tandem stance: heel to toe | left foot in front, right foot in front × eyes open, closed | 30 s | As S1, narrower base; hold time |
| S3 | Single-leg stance | dominant, non-dominant leg × eyes open, closed | 20 s | Hold time; sway while held |
| S4 | Head impulse test, seated, eyes on a target | 10 impulses on unpredictable tones (3 ± 0.75 s apart) | 40 s | Each impulse's side, peak head velocity, amplitude; camera on |
| S5 | Gaze stabilization while reading | side to side, up and down, 1 Hz metronome | 30 s | Movement achieved vs. pacing; camera image vs. head (near-target ratio) |

**Timed holds (S1–S3).** Eyes-closed trials close the eyes at the start tones
and open them at the end chime. In single-leg stance the foot lifts at the
start tones. The tester presses **Balance lost** when the wearer steps, puts a
foot down or opens their eyes. That ends the trial as a *result* (outcome
`balance_lost`, its length recorded as the hold time), not as an incomplete
recording, and QC accepts it. Sway is measured from 2 s after the start tones,
and stops 1 s before a Balance lost press, which holds the step itself.

**Head impulse test (S4).** A tester behind the seated wearer turns the head
quickly and briefly, about 15°, to either side at each tone, holds it, then
returns it slowly. The tone does not say which side. Without a tester, the
wearer makes the turn (an active head impulse, which is not the same test).
The impulse for a tone is the first yaw movement faster than 60 °/s that
starts within 2 s after it; impulses below 150 °/s are counted as slow.

**Gaze stabilization (S5).** A page of large print on the wall at eye level,
read from about an arm's length. It is the V1/V2 movement with a near target.
Compared with the V4 scale measured at a distant scene, the camera's image
moves further than the head turns (about 1 + r/d, for a camera r in front of
the rotation axis and text d away). This is the same geometry that makes a
near target need a VOR gain above 1.

**What is measured, and what is not.** Sway is the head's angular sway in its
gravity-levelled mean pose, integrated from the gyroscope with the bias fitted
against the accelerometer. It is head sway, not centre-of-pressure sway, and
the two agree only as far as the head moves with the body. Head impulses are
head kinematics only: a clinical head impulse test also records the eyes,
which the glasses cannot, so **no VOR gain is reported**. At 60 Hz a 150 ms
impulse spans about nine samples, so peak velocities can read a few percent
low.

**Safety.** These tasks are designed to challenge balance. For S1–S3, stand
next to a wall or counter with a spotter beside the wearer, especially with
eyes closed and for anyone with TBI, vestibular involvement or low vision.
Skip S4 with neck pain or a neck injury (common after TBI), and stop S4 or S5
at any dizziness.

---

## Execution

Each trial is driven by the on-glasses protocol runner, which displays the setup
instruction, counts down, and writes **cue marks** into the recording at every
commanded movement. Cue marks are what make the data analyzable: without a
machine-readable record of when a movement was *commanded*, a head turn in the
signal cannot be distinguished from noise that happens to resemble one.

Standard run order for a full session:

```
A1 → A2 → B1 → B2 → B3 → B4 → C1 → C2 → C3 → C4 → C5 → C6 → D1
```

Total wall time is roughly 34 minutes including preparation countdowns, plus
setup. Running the two Tier A trials first gets the glasses-off work out of the
way in one block rather than interleaving it. Tiers may be run on separate
days; each trial is self-contained and independently analyzable.

The native-only tiers run from the head-motion lab: Tier S in its listed order
(`S1 → S2 → S3 → S4 → S5`, eyes open before eyes closed), and Tier V with V4
first on a new pair of glasses.

**Safety.** B4 (rapid turns) and C3/C4 (walking, sit-to-stand) involve movement
that can provoke dizziness or loss of balance. Stop immediately if dizzy. Walk
only on a clear path. Use a spotter for C3 and C4 if balance is at all
uncertain — this applies with particular force to any future participant with
TBI or vestibular involvement.

---

## Data handling and provenance

```
glasses  →  IndexedDB queue  →  POST /api/ingest  →  private Vercel Blob
                                                          ↓
                                            analysis/fetch_sessions.py
                                                          ↓
                                              data/imu-sessions/*.json.gz
                                                          ↓
                                                analysis/analyze.py
                                                          ↓
                          figures + imu-characterization-report.md + summary.json
```

Sessions are written to IndexedDB on the glasses **before** any upload is
attempted and removed only once the server confirms the write, so a Wi-Fi
dropout costs a retry rather than a ten-minute trial.

Each session records its own provenance: capture app version, user agent,
screen and viewport, battery level, timezone, the capability-probe summary at
the time of recording, and the participant code. Raw samples are never edited.
Unavailable readings are stored as `null` and carried through the analysis as
`NaN` — they are never replaced with zero, because "the API returned nothing"
and "the sensor read zero" are different findings.

---

## Quality control

A session is **valid** for characterization only if all of the following hold.
`analysis/analyze.py` reports each of them per trial.

| Criterion | Threshold |
|---|---|
| Effective sample rate | Within 10% of the session median across the battery |
| Dropout fraction | < 1% of inter-sample intervals exceeding 3× median |
| Longest gap | < 250 ms |
| Trial outcome | `completed` (or `balance_lost` for a Tier S timed hold), not `aborted` |
| Duration | Within 2% of the planned trial duration (not checked on a `balance_lost` hold, whose length is the result) |
| Backgrounding | No `visibility_hidden` marks during recording |
| Tier A only — stationarity | Peak gyro SD < 2 °/s and accel peak-to-peak < 1 m/s² |

The app does **not** stop recording when it is backgrounded, because the
glasses display sleeps during a table-top trial and aborting there would make
Tier A impossible to complete. Sampling really does pause while hidden, so the
transition is written into the recording as a mark and the analysis counts it
as a QC violation — the data is kept and flagged rather than silently lost or
silently trusted.

A session failing any criterion is retained but excluded from pooled
statistics, with the exclusion recorded. Discarding data silently is not
acceptable in a programme intended to support a funding proposal.

---

## Human subjects

The programme as specified is **bench testing of a device**, with a member of
the development team as the wearer. It is not human subjects research and does
not require IRB review in that form.

This changes the moment data is collected from people with TBI or low vision as
research participants. At that point the work becomes human subjects research
and requires **IRB review and approval before any data is collected** —
approval cannot be applied retroactively to data already gathered. Federal
funding agencies will additionally expect a data management plan and documented
informed consent.

The capture schema is already built for that transition: participant fields are
de-identified codes, a `consent` status travels with every session, and no
personally identifying information is collected at any point. No schema change
is required — only the ethics approvals and the consent process.

---

## Known limitations of the method

Stated here so they are not mistaken for findings:

- **No external ground truth.** There is no motion-capture or rate-table
  reference, so scale-factor and absolute-angle accuracy cannot be established
  — only self-consistency, noise, and repeatability. Claims requiring absolute
  accuracy need a calibrated reference.
- **Absolute latency is not measured.** Sensor-to-event latency requires a
  synchronized external trigger.
- **Browser-mediated access.** Everything measured is what the Web API exposes,
  which may be filtered, fused, or rate-limited relative to the raw hardware.
  Findings characterize *the platform as available to a Web App*, which is the
  correct scope for VISOR, but they are not hardware datasheet figures.
- **Single device.** Unit-to-unit variation is unmeasured until the battery is
  repeated on a second pair of glasses.
- **One wearer.** Tier C figures reflect one person's movement patterns and
  should not be generalized to a clinical population.

---

## Reproducing the analysis

```bash
python analysis/fetch_sessions.py          # download sessions from Blob
python analysis/analyze.py                 # figures + report + summary.json
```

To exercise the pipeline without hardware — including a check that Allan
deviation, cadence, and scan-asymmetry estimates recover known values:

```bash
python analysis/make_synthetic.py
python analysis/analyze.py --in data/imu-sessions-synthetic \
    --report /tmp/synthetic-report.md --assets /tmp/synthetic-assets
```

Synthetic sessions are stamped `participant: SYNTHETIC` and `synthetic: true`.
They exist to validate the code and must never be cited as measurements.
