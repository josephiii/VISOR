# Handoff: Hazard Watch (ambient hazard detection)

*For a teammate picking this up in Claude Code. Starts from commit `d770b9a` on branch `ambient-hazard-detection`.*

## How to start

1. `git fetch && git checkout ambient-hazard-detection` (should be at `d770b9a`, *Initial Ambient Hazard Detection Research & Design*).
2. Put this file in the repo root (don't commit it), open Claude Code there, and paste the kickoff prompt at the [end](#kickoff-prompt).

---

## What this work is

VISOR (Kotlin/Compose Android app + FastAPI backend, for low-vision users with Meta glasses; see `README.md`) can check for hazards **once**, when the user asks ("VISOR, check for hazards" → one photo → YOLO11n + depth on the server → "chair, 3 feet ahead on your left").

This work explores a **hazard watch**: VISOR keeps watching while the user walks, and warns them only when something matters ("Chair, 12 o'clock, close"). It answers two MVP user stories: detecting obstacles in the path while walking, and audio alerts with direction and urgency.

**Status: research and design are done and benchmarked. No app code is written yet.** The next step is phase 1, waiting on approval (see [Next plan](#next-plan-awaiting-approval)).

## Read first

| File | Why |
|---|---|
| `documentation/research/ambient-hazard-detection.md` | **The design doc.** Feasibility, tool evaluation, the whole UX and alert policy, the 3-phase plan, test targets, open questions. Note the corrections below: the committed version predates the benchmark |
| `backend/scripts/bench_hazard_watch.py` | The benchmark (run from `backend/`: `python -m scripts.bench_hazard_watch [--vlm]`). Writes `bench_out/` (gitignored) |
| `capture/HazardDetector.kt`, `network/HazardClient.kt`, `tts/SpokenPhrases.kt` (`hazardsAhead`) | Today's one-shot hazard check |
| `capture/GlassesPhotoSource.kt`, `motionlab/LabGlassesLink.kt`, `wearables/GlassesLease.kt` | One session per photo today; the lab shows how to keep a camera + IMU stream open; the lease is how features share the one glasses session |
| `tts/Speaker.kt`, `tts/Chimes.kt`, `ui/feedback/Haptics.kt`, `data/AudioMix.kt` | Speech that a short alert can interrupt and resume; tones; vibration patterns; per-category volume |
| `backend/routes/YoloRoutes.py`, `backend/services/YoloService.py`, `backend/services/DepthEstService.py` | The server pipeline the watch would call |

*(Android paths are under `frontend/visor/app/src/main/java/ucf/visor/`.)*

## Key design decisions (already made)

- **A watch, not a mode.** Modes were removed on purpose (commit `ff7dee0`). The watch is an on/off thing that never changes what any other button or wake phrase does: "Start hazard watch" / "Stop hazard watch".
- **Three urgency levels.**
  - **Critical** (in path within 1 m, or a vehicle with under 3 s to contact): a tone, then "Stop. Car, 1 o'clock, very close." It interrupts everything, and a reading pauses and can be resumed.
  - **Warning** (in path at 1–2.5 m, or something approaching): "Chair, 12 o'clock, close."
  - **Notice** (off to the side): off by default, except for users with peripheral vision loss.
- **Rules for staying quiet.**
  - Each object is announced once, until it gets closer or moves into the path.
  - An object must be seen in 2 of the last 3 frames before it's announced.
  - Like objects are grouped ("Three people...").
  - At most one alert every 1.5 s, and any alert older than 1 s is dropped, not said late.
  - Warnings are 8 words at most.
- **Silence never means broken.** After 3 s with no frame checked, VISOR says "Hazard watch can't see right now." A heartbeat tick is optional.
- **Directions as clock positions** (10–2 o'clock), with Left/Ahead/Right as an option. **Distances in bands** (very close / close / ahead), with feet as an option.
- **The first-run message names what it can't see** (steps, curbs, holes): "keep using your cane or guide."
- **The pipeline.** A long-lived glasses stream holds `GlassesLease` as `"hazard_watch"` and keeps one request in flight, always the newest frame. The IMU paces checks: 2/s walking, 0.5/s standing, skip frames during fast head turns. Then come a tracker, a scorer and the alert policy, all **pure Kotlin and unit-testable**. A foreground service keeps it running with the screen off.
- **Offline:** **Locked** in phases 1–2, because detection runs on the server.

## What the committed doc does NOT have: benchmark results (2026-11-12)

The bench ran on the GPU server (CUDA). The live endpoint `http://54.173.104.183:8000/yolo/runHazardDetection` was timed from a home connection with 10 calls per frame size.

**Speed: fine.**

| Stage | Median |
|---|---|
| YOLO11n, 640 px frame | 10.4 ms (896 px: 13.7 ms) |
| Depth-Anything-V2 Metric Indoor Small | 16.0 ms |
| Depth-Anything-V2 Metric Outdoor Small | 15.9 ms |
| YOLOE-26s (26 text-prompted hazard words) | 17.2 ms |
| Live endpoint, end to end (640 px, q70 frame) | **0.35 s median, 0.43 s p90** (range 0.21–0.69 s) |

- Model time is about 26 ms. The rest is connection, upload, decode, and depth running on the full-size image: the downscale TODO in `DepthEstService` isn't done yet.
- Estimated frame to first sound on a phone: about **0.8–1.1 s**, inside the **1.2 s budget**, so the 2.5 m warning band holds. This is an estimate, not yet measured on a phone.
- One GPU can serve roughly 15–20 users at 2 checks/s.
- **Data:** 896 px q85 (today) is 80 KB per frame, **576 MB an hour** at 2/s. 640 px q70 is 33 KB, **239 MB an hour**. The watch should send 640 px q70.

**Detectors: YOLOE is now the lead candidate.**
- On `manhole.jpg`, YOLO11n found **nothing**. YOLOE found the **open manhole (0.51)**.
- YOLOE also found traffic cones (`dangerous.jpg`) and a door (`room.jpg`), and was more confident on people (`street.jpg`).
- YOLOE's failures:
  - It called a red hydrant **"fire" (0.42)**, so vague prompt words cause false alarms.
  - It called a shoe **"dog" (0.28)**, which a 0.35 threshold removes.
  - It missed the bed and couch only because they weren't in its word list. **The word list must be curated.**
- **Drop-offs:** on `edge.jpg` (rooftop drop), neither detector found anything, and neither depth model saw the drop. Drop-offs need their own trained class or a ground-plane check.

**Depth: keep the indoor model. The doc's "switch to outdoor" is withdrawn.**
- For the 11 objects under 4 m, the outdoor model reads **1.6–4.9× farther** (median 2.7×) than the indoor model.
- A geometry check says the outdoor model is the implausible one, even outdoors. The crossing person in `dangerous.jpg` at 7.6 m would need a telephoto lens; 2.8 m fits an ordinary camera.
- Overestimating distance is the dangerous direction.
- **A tape-measure test (0.5–5 m) is the most important missing measurement.** The indoor model may also read long.

**Not run yet:** the VLM timing (`--vlm`). The server's instance type (`nvidia-smi`) wasn't recorded either.

### Corrections to make in the committed doc

The working copy on AimoDev's machine already has these, but they weren't committed. Re-apply them if that version doesn't reach you:

- Summary table: speed **measured** (numbers above); YOLOE found the manhole; depth "unproven, models disagree 1.6–4.9×".
- "What We Measured": replace the "Model timings are not in this document yet" note and the "Measurements to take" table with the results above, plus a **Still to measure** list: tape-measure depth, phone end-to-end on mobile data, YOLOE on 50–100 real hazard photos from walking height, VLM time, instance type.
- Tool table: YOLOE becomes the **phase 2 lead candidate**; Depth Outdoor is **rejected for the watch**.
- Urgency thresholds paragraph: use the 0.8–1.1 s estimate (1.0–1.4 m walked, plus 1 m reaction room = 2.0–2.4 m, so 2.5 m holds).
- Phase 2 step 1 (detector): YOLOE-26s with a curated word list first, and fine-tuning if it falls short on curbs, stairs and drop-offs. Step 2 (depth): keep the indoor model, check it against a tape measure, and add a correction factor if it reads long.
- `network/HazardClient` plan row: 33 KB rather than 80 KB.

## Next plan (awaiting approval)

Proposed to AimoDev and **not started. Get the go-ahead before writing code.**

1. **Server prep** (`backend/`):
   - Downscale to 518 px before depth.
   - Add a per-stage timing response header.
   - Add a `watch=1` option that drops objects below 0.35 confidence.
   - Add YOLOE behind a setting, off by default.
2. **Alert-policy core:** a new pure-Kotlin `hazards/` package (`HazardTracker`, `HazardScorer`, `AlertPolicy`) with JVM unit tests using scripted detection sequences: flicker, approach, grouping, cooldowns, stale drops, can't-see. *Steps 1 and 2 need no glasses and fit in one reviewable PR. This is the recommended first chunk.*
3. **The live part:** `capture/StreamingFrameSource` (a long-lived camera + Motion session holding the lease, which also serves the other features' photos), `hazards/HazardWatch`, and the foreground service.
4. **The user-facing parts:**
   - Home switch, and new wake phrases in `tools/kws`.
   - New phrases in `SpokenPhrases`.
   - Critical and warning tones (stereo-panned) and vibrations.
   - A *Hazard alerts* category in `AudioMix`, plus Settings, Help, and `Availability` (Locked offline).
5. **Tests that need the team and glasses:** tape-measure depth, 50–100 hazard photos for YOLOE and its threshold, and a phone end-to-end timing log on mobile data.

## Team rules (AimoDev's, follow them)

- **Plan first, wait for confirmation before code changes.** Report what you did and what you found, then propose the next plan before proceeding.
- **No `Co-Authored-By: Claude` (or any Claude attribution) in commits.**
- **Never commit the root `CLAUDE.md`, and don't add it to `.gitignore`.** It's a local instruction file.
- **Accessibility first.** The audience is low-vision users: audio is the main channel, Section 508 (no more than 3 flashes a second, no meaning by color alone), large text, and every state said aloud.
- On AimoDev's laptop, Windows Application Control blocks `torch.dll`. Run model benchmarks on the GPU server; your machine may differ. Don't work around security policies.
- You'll need your own server access. Don't reuse or go looking for anyone's keys.

## Open questions (need the real glasses)

Glasses horizontal field of view (for clock positions); whether one session can carry Camera + Motion + Display; heat and battery over 30+ minutes of streaming; stereo audio through the glasses' Bluetooth; head direction vs walking direction; MWDAT streaming while the phone is locked. Details are in the doc's *Open Questions*.

---

## Kickoff prompt

> I'm taking over the VISOR hazard watch work from a teammate. Read `hazard-watch-handoff.md` in the repo root first, then `documentation/research/ambient-hazard-detection.md` and the files it lists under "Read first". The committed design doc predates the GPU benchmark: treat the handoff's benchmark results and corrections as current. Follow the team rules in the handoff. Don't change code yet: confirm you understand where things stand, flag anything in the code that contradicts the handoff, and propose how you'd start the "Next plan" (recommended: server prep + the pure-Kotlin alert-policy core with tests), then wait for my go-ahead.
