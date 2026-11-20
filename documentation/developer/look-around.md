# Look around

*The head-motion lab's IMU, put to work for VISOR's users: turn your head, and VISOR tells you what is around you by clock hour, then leads your head to any of it by tone.*

Built natively on **MWDAT 1.0.1**: the glasses' camera stream and their **Motion** capability (the IMU) in one glasses session, the pairing the [head-motion lab](./mwdat-1.0-upgrade.md) proved.

---

## Why it exists

One glasses photo covers **43°** (measured by the lab, [below](#numbers-it-rests-on)). People with **peripheral vision loss** (glaucoma, hemianopia, retinitis pigmentosa) already see what is straight ahead. What they miss is the sides, which is exactly what one photo leaves out. Check for hazards answers for that 43° slice.

Look around covers **113°** with three pictures, or **183°** with five for anyone whose setup says the sides are hard to see. It asks the wearer to scan, which is also the **compensatory scanning** taught in visual-field-loss rehabilitation ([IMU capability analysis](../research/imu-capability-analysis.md), use case 2). When the wearer doesn't turn far enough one way, VISOR says so: *"I couldn't get a picture of your left."*

Then it does something no single photo can. VISOR knows where everything is relative to the head, so it can **lead the head back to it**: *"Guide me to the chair."* That covers the "object finder" in VISOR's [low-vision research](../research/low-vision-research.md).

## What the wearer hears

> **"VISOR, look around."**
> *"Looking around. Hold your head still."* (glasses shutter)
> *"Now turn your head slowly to the left."* beep… beep.. beep. beep (silence) (shutter)
> *"Now turn slowly to the right."* … (shutter)
> *"Now face ahead again."* (thinking beeps)
> *"From left to right: Couch at 10 o'clock, 6 feet away. Dining table at 12 o'clock, 4 feet away. Person at 2 o'clock, 9 feet away. To find one, say VISOR GO, then guide me to the couch."*
>
> **"VISOR GO, guide me to the couch."**
> *"Couch, at 10 o'clock. Turn your head slowly to the left."* beep… beep.. beep.beep.beep (two high beeps)
> *"Couch, straight ahead, 6 feet away."*

- **The tone** leads the head toward the next stop, or toward the thing being found. It beeps faster and higher as the head gets closer, like a parking sensor. Pitch and rate both carry the distance, so someone who hears one of them poorly still has the other. It stays between 440 and 880 Hz, below the frequencies age-related hearing loss affects first, as VISOR's chime does. **Silence** at a stop means "hold it there". The glasses' shutter sound follows.
- **Clock hours** are how orientation and mobility training teaches directions. 12 is where the wearer faces at the end, 9 is their left and 3 their right. Distances are in feet, as Check for hazards gives them.
- **From left to right** is the order the wearer just turned through, so the answer builds the room in the same order. A *Brief* Detail setting names the nearest few (3, or 5 for "a lot"), still from left to right.
- **Nothing found** says *"I didn't recognize any objects around you."* It never suggests the room is empty: the detector only knows everyday objects.
- **Stop** at any time: "VISOR GO, stop", or tap the Look around button, which reads *"Looking around… Tap to stop"* while it works.

### Where it is

| Way in | What it does |
|---|---|
| "VISOR, look around" / "VISOR, what is around me" | Wake phrases (can be turned off in Settings > Commands) |
| Home's **Look around** button | Last in the list (compass icon), so no button users already know moved. Second for side loss (`VisionPlan.featureOrder`) |
| "VISOR GO, look around" | Voice navigation |
| "VISOR GO, guide me to the chair" / "where is the table" / "find my cup" | Guidance, for two minutes after a scan (`VoiceCommand.GuideTo`); works with the screen off |
| Home's **Around you** card | One button per thing found ("Chair, 10 o'clock, 4 ft"); a tap leads the head to it. Shown while guidance still works |
| Settings > Sound > **Guidance tones** | The tone's own volume |

It is **not** on the glasses' display menu. A fifth camera button would push *Describe the scene* behind *More*, and Look around needs no screen once it has started.

It **needs the glasses**: the head's movement is theirs. With the phone's camera chosen it says so, and how to switch. It **needs the internet** (each picture goes to the hazard detector), so offline it is locked like Check for hazards ([Offline features](./offline-features.md)).

## How it works

```
"VISOR, look around"
  └─ VisorEngine.runCameraFeature(LOOK_AROUND) → LookAround.start()
       1. HeldGlassesSession.pin("look_around")          keep the one glasses session open
       2. GlassesPhotoSource.prepareStream()             camera streaming first (the lab's rule)
       3. HeadMotion.attach(session)                     MWDAT Motion, 30 Hz, same session
       4. ScanSequencer ← every pose                     cues, stops, steady gate, timeouts
            Capture → glasses photo → hazard detection (async, all in parallel)
       5. SceneMap: direction = head yaw + place in photo; merge overlaps
       6. LookAroundPhrases.found(...)                   spoken, shown on Home, in History
       7. Motion + pin kept 2 min for "guide me to"      GuideSequencer + GuideTonePlayer
```

| File | What it does |
|---|---|
| `motion/HeadMotion.kt` | Attaches MWDAT Motion to a started session (subscribe before `start()`, revive an unasked stop, Neural Band samples ignored), and publishes the head pose |
| `motion/HeadingTracker.kt` | Pure. Integrates the gyro's rotation **about "up"** (gravity from the accelerometer when there is one, else the body's +y) into yaw; total angular speed for steadiness. Skips clock jumps and gaps |
| `motion/SteadyGate.kt` | Pure. "Held still": under 6.4 °/s for 300 ms (the lab's C1 threshold) |
| `lookaround/ScanPlan.kt` | The stops (standard ±35°, wide ±35° and ±70°) and the camera's measured field of view (`CameraOptics`) |
| `lookaround/ScanSequencer.kt` | Pure. One scan, step by step: which way to turn, when a stop is reached and still, "Hold still" and a looser threshold after 2.5 s, a missed stop after 12 s |
| `lookaround/SceneMap.kt` | Pure. Each object's direction, overlap merging, clock hours, and finding "the table" among what was found |
| `lookaround/GuideSequencer.kt` | Pure. Leading the head to one thing: arrived, swung past ("Too far. Back to the left."), gave up after 25 s. Also `GuideTone` (rate and pitch from the angle) |
| `lookaround/GuideTonePlayer.kt` | Synthesized beeps at nine pitches, played as media, so they come from the glasses |
| `lookaround/LookAround.kt` | Runs it all on the engine's scope: session, camera, Motion, pictures, detection, speech, the guidance window |
| `lookaround/LookAroundPhrases.kt` | Everything it says |

### Decisions, and why

- **Motion rides VISOR's held session, not one of its own.** MWDAT allows one session per device. Every session that ends hands the Bluetooth channel back to the Meta AI app, which costs the link on 1.0.x ([`HeldGlassesSession`](../../frontend/visor/app/src/main/java/ucf/visor/wearables/HeldGlassesSession.kt)). `pin()` keeps that session open between pictures even on display glasses with tap navigation, which normally hand the glasses back after each picture.
- **Camera first, then Motion.** The lab's BirdSpotter lesson: a capability added before the device has really arrived fails with `DEVICE_DISCONNECTED`, and a streaming camera is the proof it has arrived.
- **Gyro integration, not MWDAT's fused quaternion.** On Ray-Ban Display the lab found the quaternion's heading tracks the gyro (r ≈ 0.99), but its frame is undocumented and was chosen by a fit, and MockDeviceKit replays a different one. Integrating the gyro needs only the body frame the lab confirmed. Its drift is the measured turn-on bias, **0.015 °/s natively**: about 0.3° over a 20-second scan and 2° over the two-minute guidance window, both well under the 7° "straight ahead" zone.
- **Rotation about "up", not about the body's y axis.** Looking 40° down, the y axis alone would read a turn 23% short. Many native recordings carry no accelerometer data (the characterization report lists `ax..az` null), so then it falls back to +y, the lab's measured yaw axis.
- **A picture waits for the head to hold still.** That makes it sharper, and it also makes its direction trustworthy, since the head isn't moving while the photo is taken. Bluetooth delivers Motion in bursts (arrival gaps up to ~150 ms natively), so steadiness is timed by the glasses' own sample clock.
- **30 Hz.** Turning to look around moves well under 1 Hz. The rest of the Bluetooth bandwidth goes to the 720p camera stream.
- **The map is relative and short-lived.** There is no compass and no position: if the wearer walks away, directions are wrong. So guidance lasts two minutes, then Motion stops, the session goes back to normal, and *"Look around first"* is the answer.

### Numbers it rests on

| Number | Source |
|---|---|
| Camera stream 42.8° wide | Lab V4 slow pan on Ray-Ban Display: 8.02 px/° at 360 px wide, R² 0.97 → focal 459.5 px. Meta: every stream size covers the same view |
| Still = under 6.4 °/s | Lab C1 stillness hold, native, p95 ([characterization report](../testing/imu-characterization-report.md)) |
| Turning left is positive yaw about +y | Lab pose analysis, native gravity test; BirdSpotter's frame |
| Gyro drift 0.015 °/s | [Pose tracking report](../testing/imu-pose-tracking-report.md), native turn-on bias |

## MWDAT version

The app now builds against **MWDAT 1.0.1** (2026-10-09), the latest on Maven Central. 1.0.1 fixes "several performance issues on `Stream` causing lag and stutter" and HEVC frame freezes, which matters here because Look around streams the camera and Motion together. Upgrading only changed `libs.versions.toml`. `GlassesLinkNudge` reads two private SDK fields by type, so the 1.0.0 and 1.0.1 `mwdat-core` class lists and those classes' fields were compared: they are identical.

## Testing it

- **Unit tests** (`./gradlew testDebugUnitTest`):
  - `HeadingTrackerTest`: left is positive; a turn with the head pitched 40° down is recovered with gravity, and is short by cos 40° without it; clock jumps; two cycles of the scripted look-around with no drift.
  - `ScanSequencerTest`: the standard and wide scans driven by synthetic head movement; a side never reached; a head that can't settle; a failed picture.
  - `SceneMapTest`, `LookAroundPhrasesTest`, `GuideSequencerTest`, `LookAroundCommandTest`, and additions to `VisionPlanTest`.
- **On MockDeviceKit** (`LookAroundMockDeviceTest`, instrumentation): mock glasses with a camera feed and a scripted head. The real MWDAT path (held session, camera stream, Motion in the same session, in-stream photos), with a fake detector in place of the server. Checks one picture per stop, the lease held throughout, three chairs placed about 38° right, ahead and 38° left, and *"guide me"* arriving.
- **In the app without glasses:** debug menu → pair mock glasses → set a camera feed or *Select image* → **Replay a look-around head turn** → choose the glasses camera on the Pair screen → Home → **Look around**. The script holds still ahead, at 38° and 72° each way, and ahead again, so a scan finishes within a cycle or two.

### What only real glasses can answer

1. **Motion enabled** for VISOR in the Wearables Developer Center (it is experimental). Without it VISOR says so, aloud.
2. **Camera + Motion in the held session.** The lab ran them together in its own session; this is the first time in VISOR's. Watch logcat `VisorHeadMotion` and `VisorLookAround`.
3. **The camera stopping 45 s after the last picture**, during the guidance window: on 1.0.0 a stream stopping could cut the SDK's link. If Motion stops with it, guidance ends with *"I lost track of your head movement"*, and the fix is to keep the stream for the window.
4. **Head yaw sign**: the lab's hardware checklist item 9. A first turn to the left should be heard as "left".
5. **The stop spacing.** 35° with 7.8° of overlap assumes the 42.8° measured on Ray-Ban Display. Check that an object at the edge between two pictures is said once.

## Limits

- **Experimental API.** Motion cannot ship to a production release channel until Meta graduates it. Developer Mode and beta channels work.
- **The wearer must stay put.** Turning the body instead of the head is fine (it is the same rotation), but walking makes the directions wrong. Guidance ends after two minutes for that reason.
- **Clock hours are coarse on purpose.** One hour is 30°. "Straight ahead" in guidance is ±7°, about the middle third of the camera's view.
- **Only what YOLO knows.** The hazard detector's everyday objects: no doors or stairs yet. A future change could ask the VLM for a few named things per picture instead.
