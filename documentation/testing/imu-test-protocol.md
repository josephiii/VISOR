# IMU Test Protocol — Meta Ray-Ban Display

*Methodology for the VISOR inertial-sensor characterization programme*

> **Status: methodology defined, measurements not yet collected.**
> This document specifies *how* the programme is run. Results are written by
> `analysis/analyze.py` into
> [imu-characterization-report.md](./imu-characterization-report.md), which does
> not exist until the first real session is recorded and analyzed. Nothing in
> this file should be cited as a finding.

---

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

Fifteen trials in four tiers. Tier A establishes what the instrument is; B
establishes its dynamic limits; C tests VISOR-relevant behaviour; D tests
sustained operation.

### Tier A — Instrument characterization

| ID | Trial | Duration | Measures |
|---|---|---|---|
| A1 | Static rest | 2 min | Noise floor, accel bias, gyro zero-rate offset, Allan deviation |
| A2 | Static extended | 5 min | Bias instability, low-frequency drift at longer averaging times |
| A3 | Six-position static | 2.5 min | Per-axis bias and scale factor (standard six-position method) |

Tier A trials are performed with the glasses **resting on a solid surface, not
worn**. A worn "still" recording contains physiological tremor and is not a
valid noise-floor measurement.

### Tier B — Dynamic response

| ID | Trial | Duration | Measures |
|---|---|---|---|
| B1 | Yaw sweeps (paced) | 1 min | Yaw response, repeatability, amplitude recovery |
| B2 | Pitch sweeps (paced) | 1 min | Pitch response, nod detection feasibility |
| B3 | Roll sweeps (paced) | 1 min | Roll response, axis cross-coupling |
| B4 | Rapid head turns | 30 s | Angular-rate range, saturation, aliasing |
| B5 | Impulse taps | 45 s | Impulse response, event-timestamp fidelity |

### Tier C — Rehabilitation-relevant behaviour

| ID | Trial | Duration | Measures |
|---|---|---|---|
| C1 | Stillness hold (worn) | 1 min | Dwell threshold for gating capture |
| C2 | Compensatory scanning | 1.5 min | Scan amplitude, rate, left/right symmetry |
| C3 | Walking gait | 1 min | Step cadence, head-bob amplitude |
| C4 | Sit-to-stand cycles | 1.25 min | Postural-transition signature |
| C5 | Reading posture | 1 min | Sustained near-task pose, context classification |
| C6 | Unstructured baseline | 5 min | Realistic mixed activity, false-positive estimation |

### Tier D — System constraints

| ID | Trial | Duration | Measures |
|---|---|---|---|
| D1 | Endurance capture | 10 min | Rate stability, dropout, battery drain |

---

## Execution

Each trial is driven by the on-glasses protocol runner, which displays the setup
instruction, counts down, and writes **cue marks** into the recording at every
commanded movement. Cue marks are what make the data analyzable: without a
machine-readable record of when a movement was *commanded*, a head turn in the
signal cannot be distinguished from noise that happens to resemble one.

Standard run order for a full session:

```
A1 → A2 → A3 → B1 → B2 → B3 → B4 → B5 → C1 → C2 → C3 → C4 → C5 → C6 → D1
```

Total wall time is roughly 35 minutes plus setup. Tiers may be run on separate
days; each trial is self-contained and independently analyzable.

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
| Trial outcome | `completed`, not `aborted` |
| Duration | Within 2% of the planned trial duration |
| Tier A only — motion present | Gravity-vector SD below the worn-still figure from C1 |

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
  synchronized external trigger. B5 (impulse taps) bounds the *relative* timing
  behaviour only.
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
