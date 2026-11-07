# Ambient Hazard Detection
*Feasibility, tool evaluation, and design for a hands-free hazard watch*

---

## The Question

Today, "Check for hazards" takes **one** picture when the user asks, and VISOR says what's in it: "chair, 3 feet ahead on your left." Can VISOR keep watching instead, while the user walks, and warn them only when something matters? Two MVP user stories ask for this:

> As a low-vision user, I want VISOR to detect obstacles in my immediate path while walking, so that I can avoid collisions and navigate safely without relying on a cane or guide alone.

> ...during obstacle navigation modes, audio alerts notify me of the direction and urgency of a nearby obstacle ("chair, 2 o'clock, close"), so that I can react appropriately without needing visual confirmation.

**Short answer: yes. A first version can be built on VISOR's current models and server, as a "hazard watch" the user turns on and off. Three limits of the current tools need to be fixed before it can be trusted outdoors. Each fix is a small step from what VISOR already uses.**

| | Verdict | Evidence |
|---|---|---|
| Can the current pipeline (glasses stream → YOLO11n + Depth-Anything-V2 on the server) run continuously? | **Yes, by design**. The glasses stream already runs for minutes in the head-motion lab, and both models are small. Its speed is **not measured yet**: the bench that measures it is checked in. Sending today's photos would cost about **600 MB of data an hour**; smaller frames cut that to about 270 MB | [Measurements](#what-we-measured) |
| Does it find the hazards that matter? | **Partly.** It finds people, vehicles, bikes and furniture well. It **cannot** find drop-offs, holes, stairs, curbs or poles, because those aren't among YOLO11's 80 classes. | Manhole and street tests below |
| Are its distances right? | **Indoors, roughly. Outdoors, no.** The depth model is the *indoor* metric model. | Indoor vs outdoor depth below |
| Does it work offline? | **No.** YOLO and depth run on the server, as "Check for hazards" does today | [Offline features](../developer/offline-features.md) |

**Recommendation:** build it in three phases (see the [plan](#the-plan)). Phase 1 uses only today's toolset and ships the interaction design, alert policy and safety rules. Phase 2 fixes what the detector can see and how well it judges distance. Phase 3 moves detection onto the phone for offline use and lower latency.

---

## A Watch, Not a Mode

VISOR removed modes on purpose (commit `ff7dee0`, *Replace modes with one button per camera feature*). A mode made the same button do different things, took two steps, and invited pressing the wrong thing. "VISOR GO, hazard mode" now replies with how to ask directly.

Ambient detection is still compatible with that decision, as long as it **doesn't change what anything else does**. So it is designed as a **watch**: one thing that is either on or off, like a timer.

- Every other button and wake phrase keeps doing exactly what it does now. "VISOR, read this" still reads text while the watch is on. It takes its picture from the stream the watch already has open instead of opening a new session.
- Its state is always announced and can be checked: "Hazard watch is on" / "off," on screen and by asking.
- It is named after what it does, not after a state of the app: **Start hazard watch** / **Stop hazard watch**.

> The rest of this document calls it the *hazard watch*. If the team prefers "Hazard Detection Mode," the design still holds, but the word *mode* would bring back the confusion `ff7dee0` removed.

---

## What VISOR Already Has

| Need | What exists | Ready? |
|---|---|---|
| A continuous camera feed | MWDAT camera **stream**, used by the head-motion lab (`motionlab/LabGlassesLink`) for minutes at a time at up to 30 fps | **Yes.** Today's `GlassesPhotoSource` opens and closes a session *per photo*. The watch needs a long-lived session like the lab's. |
| Object detection + distance | `POST /yolo/runHazardDetection`: YOLO11n + Depth-Anything-V2 Metric **Indoor** Small | **Yes**, with the limits below |
| Left / ahead / right | `SpokenPhrases.hazardsAhead` splits the picture into thirds | Needs clock positions (see [Direction](#direction-clock-positions)) |
| Head motion and walking | MWDAT **Motion** (IMU, 5–60 Hz), attached alongside the camera in one session (lab), with step detection validated in the [pose report](../testing/imu-pose-tracking-report.md) | **Yes**: used to pace checks and skip blurred frames |
| Speech that can be interrupted and resumed | `tts/Speaker`: a short utterance pauses a reading and resume works | **Yes**: an alert can pause a long answer without losing it |
| Distinct sounds and vibrations | `tts/Chimes` (synthesized tones), `ui/feedback/Haptics` (one pattern per meaning) | Needs two new patterns each (critical, warning) |
| Per-category volume | `data/AudioMix` | Needs a *Hazard alerts* category |
| Offline locking | `capture/Availability` | The watch is **Locked** offline in phases 1–2 |
| A one-shot VLM | `POST /vlm/image_prompt` (Qwen3-VL-2B) | **Too slow for alerts** (measured below), but useful for a "what's ahead?" check on demand |

---

## What We Measured

> **Model timings are not in this document yet.** The development laptop's Windows Application Control policy blocks PyTorch's native library (`torch.dll`), so YOLO and the depth models couldn't run on it. The bench is checked in as [`backend/scripts/bench_hazard_watch.py`](../../backend/scripts/bench_hazard_watch.py), to run on the GPU server where the watch's real numbers come from (see [Measurements to take](#measurements-to-take)). Everything below is either measured, from the model's own documentation, or from VISOR's code, and each says which.

### 1. Upload size and mobile data *(measured)*

Each of the 14 sample images was resized to the glasses' stream size and compressed as `ApiClient.jpeg` does (quality 85), and again at 640 px and quality 70:

| Frame sent | Median size | Data at 1 check/s | Data at 2 checks/s |
|---|---|---|---|
| 896 px, quality 85 (what "Check for hazards" sends today) | **85 KB** | 308 MB/hour | **615 MB/hour** |
| 640 px, quality 70 | **37 KB** | 133 MB/hour | 267 MB/hour |

**Finding:** sending today's photos continuously would use about half a gigabyte an hour, which is too much for many users' data plans. The watch should send 640 px at quality 70. YOLO resizes to 640 internally anyway, so detection loses nothing; depth runs at 518. Phase 3 (detection on the phone) brings the data use close to zero.

### 2. What the detector can name *(from the model)*

`yolo11n.pt` knows the 80 COCO classes. That list has the moving hazards (person, bicycle, car, motorcycle, bus, truck, dog), street furniture (bench, fire hydrant, stop sign, parking meter, traffic light) and indoor furniture (chair, couch, dining table, potted plant). **It has no class for:** stairs, curb, hole or open manhole, drop-off or ledge, pole or bollard, traffic cone, door or glass door, wall, barrier, low branch, wet floor sign.

**Finding:** two of the sample images VISOR's team chose as hazards, `manhole.jpg` (an open manhole in the walking path) and `edge.jpg` (a drop from a rooftop), show hazards the current detector **has no word for**. Whatever else it finds in them, it can't warn about the hole or the edge. These are the trip-and-fall hazards that matter most to someone walking, and the earlier [VLM test](../testing/vlm-tests.md) shows the gap isn't covered elsewhere: asked to describe `manhole.jpg`, Qwen3 called it a manhole with a visible interior, but not a danger.

### 3. How far the depth model can see *(from the model)*

The server uses **Depth-Anything-V2 Metric Indoor Small**, fine-tuned on the synthetic indoor Hypersim set with a **maximum depth of 20 m**. The outdoor sibling, **Metric Outdoor Small**, is fine-tuned on Virtual KITTI 2 (driving scenes) with a **maximum of 80 m** ([Depth-Anything-V2 metric depth](https://github.com/DepthAnything/Depth-Anything-V2/tree/main/metric_depth)). Both share the same code path: `DepthEstService` would change only its model id.

**Finding:** outdoors, the indoor model squeezes a street into a room-sized scale. For distances under 4 m, the only range the watch speaks about, the error matters less than the cap. But it is unmeasured, and "close" is exactly where a wrong number misleads. The bench reports both models' distances for the same boxes. Phase 2 checks them with a tape measure.

### 4. How long the pipeline takes *(to measure)*

"Check for hazards" already runs YOLO11n and then the depth model on every photo, one after the other (`routes/YoloRoutes.py`), and skips depth when YOLO finds nothing. The bench times each stage on the server's hardware. Until then, the design uses a **budget** instead of a measurement: **≤ 1.2 s from frame to first sound**, at the 90th percentile. That is the latency every threshold in the [urgency levels](#urgency-levels) is built around. If the bench shows the server can't meet it, the phase 3 order changes: on-device detection moves first.

### Measurements to take

From `backend/` on the GPU server (and once on a laptop for comparison):

```
python -m scripts.bench_hazard_watch          # add --vlm to time the VLM too
```

| Record | Why it matters | Decision it settles |
|---|---|---|
| YOLO11n ms, depth ms (indoor), per image | The server's share of the 1.2 s budget | Whether phase 1 can promise 2 checks/s |
| Depth indoor vs outdoor, for the same boxes, on `street.jpg` and `dangerous.jpg` | How wrong indoor depth is outdoors | Whether phase 2's depth switch is urgent |
| YOLOE's boxes on `manhole.jpg`, `edge.jpg`, `street.jpg` | Can it name the hole, the edge, the curb? | Whether YOLOE is worth carrying past labeling help |
| YOLOE ms vs YOLO11n ms | Its cost | Server-side YOLOE in phase 2, or fine-tuned YOLO11n only |
| VLM ms (`--vlm`) | Why the VLM isn't the alert path | Confirms or overturns the VLM verdict below |

Add the results here as a table, with the machine they came from.

---

## Tool Quality Analysis

The [measurements](#what-we-measured) found three gaps in today's tools: hazards with no class, depth with the wrong scale outdoors, and no offline path. These are the tools considered for each. Every new tool is judged on the same questions: does it close the gap, how good is it on the evidence, what does it cost per frame, can it run on the phone later, and what are its strings attached?

| Tool | Gap it closes | Quality evidence | Cost | Verdict |
|---|---|---|---|---|
| **YOLO11n** (in use) | — | Proven, fast; 2.6M parameters. Only the 80 COCO classes | Smallest of the YOLO family | **Keep** for phase 1 |
| **YOLOE** (Ultralytics, open-vocabulary YOLO) | Names hazards by *text prompt*: "stairs," "open manhole," "pole" | LVIS minival (1,203 classes), text prompts: **YOLOE-26n 24.7, YOLOE-26s 30.8 mAP** ([docs](https://docs.ultralytics.com/models/yoloe)). That's useful, but zero-shot rare classes are missed often. For comparison, YOLO11n scores 39.5 on COCO's 80 common classes. | 26s: 10.7M parameters, about 4× YOLO11n. Prompts can be baked in at export (ONNX, LiteRT), so it then runs as a plain YOLO. The class comparison adds about 19% at 1,203 classes, and less at VISOR's ~26 | **Adopt as a tool, not yet as the detector:** use it to auto-label VISOR's hazard dataset (cuts labeling time), and as a server-side candidate if the bench shows it finds `manhole.jpg`'s hole. Not trusted alone for a safety alert on zero-shot accuracy |
| **YOLO11n fine-tuned** on VISOR's hazard classes | Same, with VISOR's own data | The recipe is in [yolo-research.md](./yolo-research.md); fine-tuned small models reliably beat zero-shot on their own classes, and accuracy is measured on VISOR's own validation set | Same speed as today. Costs data collection: hundreds of varied, labeled images per new class for a pilot, more for production. Ultralytics' [training tips](https://docs.ultralytics.com/yolov5/tutorials/tips_for_best_training_results/) suggest 1,500+ per class | **Phase 2 target.** The model that also goes on the phone in phase 3 |
| **Depth-Anything-V2 Metric Outdoor Small** | Outdoor scale | Same family and size as the indoor model; 80 m range vs 20 m. Trained on synthetic *driving* scenes, so its viewpoint is a car's, not a pedestrian's | Same as indoor; a one-line change in `DepthEstService` | **Adopt in phase 2**, chosen per scene, after a tape-measure check |
| **Ground-plane distance** (camera height + IMU pitch + box bottom) | Distance with no depth model, so offline too | Pure geometry. The glasses' roll and pitch were validated in the [pose report](../testing/imu-pose-tracking-report.md) (they agree with an IMU-only filter). It assumes flat ground and that the object touches the floor, and the error grows with distance, which is acceptable under 4 m | Microseconds | **Adopt in phase 2 as a cross-check; phase 3 as the offline distance** |
| **Box growth (looming)** | "Coming toward me," with no depth | A well-established time-to-contact cue (the rate an object's image grows), used in collision warning. It needs consistent tracking across frames, and it's noisy for small, far boxes, which the watch ignores anyway | Free once the tracker exists | **Adopt in phase 1**: it drives "approaching" |
| **Qwen3-VL-2B** (VISOR's VLM) | Understands any hazard, in context | Rich answers, but it is generative: it writes its answer word by word, so one answer takes many forward passes of a 2B model, against one pass of a 2.6M-parameter YOLO. It can also hallucinate, and in the [VLM test](../testing/vlm-tests.md) it described an open manhole without calling it a danger | Seconds per answer (bench `--vlm` measures it), plus heavy GPU memory per user | **Reject for alerts.** Keep for an on-demand "VISOR, what's ahead?" while the watch runs, and later perhaps as a slow second opinion when the user stops walking |
| **ML Kit Object Detection** (on-device) | Offline detection | Its base model sorts objects into 5 broad categories (fashion goods, food, home goods, places, plants), with no person, vehicle or stairs | Fast, on the phone | **Reject**: none of its categories is a walking hazard |
| **ARCore Depth** | On-device depth | Works on the phone's camera only, and needs the phone moving | — | **Reject**: can't see through the glasses, which the watch is built around |
| **LiteRT (TFLite) on the phone** | Offline, lower latency | YOLOv8n (similar size to YOLO11n) runs in 75–117 ms on a mid-range phone ([nx_tflite_mob](https://hexdocs.pm/nx_tflite_mob/delegates.html)); results vary a lot by chip | Phone battery and heat | **Phase 3**, after measuring on the team's test phone |

### One string attached: the license

Ultralytics' code and weights (YOLO11, YOLOE) are **AGPL-3.0**, or a paid Enterprise license. The backend already uses them; on a server, AGPL obligations mostly concern people who reach the service over the network. **Phase 3 ships the model inside the Android app**, and AGPL then reaches the app itself. Raise this with the sponsor before phase 3. The alternatives are an Ultralytics Enterprise license, or a permissively licensed detector trained on the same data.

---

## Design

### How it works

```
 Glasses camera stream (504 x 896, 15 fps)        Glasses IMU (Motion, 30 Hz)
            │  latest frame only                          │ walking? turning fast?
            ▼                                             ▼
   ┌──────────────────┐   one request in flight   ┌──────────────────┐
   │  Frame sampler   │◄──────── pace ────────────│  Motion gate     │
   └────────┬─────────┘                           └──────────────────┘
            │ JPEG (640 px, q70)
            ▼
   POST /yolo/runHazardDetection  ──►  [{object, coords, conf, depth}]
            │
            ▼
   ┌──────────────────┐   ┌──────────────────┐   ┌───────────────────────────┐
   │  Tracker         │──►│  Hazard scorer   │──►│  Alert policy             │──► Speaker / Chimes / Haptics
   │  (same object    │   │  (in path? how   │   │  (what to say, when, and  │
   │   across frames) │   │   close? coming?)│   │   when to stay quiet)     │
   └──────────────────┘   └──────────────────┘   └───────────────────────────┘
```

Everything after the server call runs on the phone, in plain Kotlin with no Android dependencies. That way the tracker, the scorer and the alert policy can be unit-tested with scripted detections, as `ReadingsTest` and `AvailabilityTest` test their logic today.

**Frame sampler.** It keeps one request in flight and always sends the *newest* frame when the last answer returns. It never queues frames: an alert about where things were two seconds ago is worse than none. The pace is set by motion:

| What the IMU says | Checks per second | Why |
|---|---|---|
| Walking (steps detected) | as fast as the server answers, up to 2 | Things approach at walking speed |
| Standing still | 0.5 | Something may still approach (a person, a bike), but nothing new enters the path through the user's own movement |
| Head turning faster than ~120 °/s | skip the frame | It would be motion-blurred, and the box positions would be stale by the time the answer arrives |

**Tracker.** It matches each detection to one from the previous frames by class and box overlap (IoU ≥ 0.3), and keeps per object its **smoothed distance** (median of the last three), its **approach rate**, and how many recent frames it was seen in. An object is **confirmed** once it has been seen in 2 of the last 3 frames. Only confirmed objects are announced, except for the critical case below. This is the main defense against a one-frame false detection setting off an alarm.

**Hazard scorer.** It decides, for each confirmed object, whether it is **in the walking path** and how urgent it is:

- *In path*: the box's horizontal center is in the middle 40% of the frame, **and** the box reaches the lower half of the picture (it stands on the ground the user is walking on, not across the room on a far wall). With the glasses this follows the head, not the feet; the [open questions](#open-questions) cover that.
- *Approaching*: smoothed distance shrinking faster than 0.5 m/s, or the box growing (box height up by more than 15% a second). Box growth needs no depth model at all, so it still works where depth is wrong. Time to contact is about *(box height) ÷ (growth rate)*.

### Urgency levels

Three levels. **Only the first two are on by default.**

| Level | When | How it sounds | Repeats |
|---|---|---|---|
| **Critical** | Anything in path within **1 m** (3 ft). A vehicle, bike or motorcycle approaching with time to contact under 3 s. Later: a drop-off or stairs going down, in path, within 2 m. | Critical tone + strong vibration, then: "**Stop.** Car, 1 o'clock, very close." Interrupts anything VISOR is saying (a reading pauses and can be resumed). | Every 2 s while it stays critical, at most 3 times, then only if it gets closer |
| **Warning** | In path within **1–2.5 m** (3–8 ft). A person or vehicle approaching from any direction within 4 m. | Warning tone, then "Chair, 12 o'clock, close." Waits for a short reply or status update to finish, but cuts into a long reading (which pauses). | Only when it moves up a distance band, enters the path, or is still in path after 8 s |
| **Notice** *(off by default)* | Off to the side within 2.5 m, or in path at 2.5–4 m | No tone. Spoken only when VISOR is otherwise quiet: "Bench, 10 o'clock." | Once per object |

Why these thresholds: at a typical walking speed of 1.2–1.4 m/s, the system's own delay eats into the warning distance. With the **1.2 s budget** from frame to first sound (see [measurements](#4-how-long-the-pipeline-takes-to-measure)), the user walks about **1.6 m** before hearing about something. A warning at 2.5 m leaves under a metre and about a second to react, which is why the warning band starts no closer than that. It is also why "very close" is called critical: by the time it is spoken, the object may be at arm's length. When phase 3 cuts the latency, the bands can come in and fewer alerts fire. **Once the bench has run, recompute the bands as (walking speed × measured 90th-percentile latency) + 1 m of reaction room.**

### Staying quiet: the rules that make it bearable

An ambient feature that talks too much gets turned off, and then it protects no one. Practitioners who build these tools report that continuous alarm-style tones are too aggressive to live with. They move to subtle cues and escalate only when it matters ([vrtonung](https://www.vrtonung.de/en/?p=15629)). These rules keep VISOR quiet unless something has changed:

1. **One object, one announcement**, until it gets closer by a band, moves into the path, or (critical only) stays critical.
2. **Group like objects**: "Three people, 12 o'clock, close," not three sentences.
3. **At most one alert every 1.5 s**, highest urgency first. Anything waiting longer than 1 s is dropped, not delivered late.
4. **Never more than 8 words** in a warning. Object first, then direction, then distance, the order the user story asks for.
5. **Nothing new, nothing said.** The user learns that silence means the path is clear *of what VISOR can see*. That is why rule 6 matters.
6. **Silence must never mean "broken."** If no frame has been checked for 3 s (network, glasses disconnected, server error), VISOR says so at once with its own sound: "Hazard watch can't see right now. I'll tell you when it's back." When it recovers: "Hazard watch is back." A quiet heartbeat (a soft tick every 15 s, *off by default*) can be turned on by users who want proof it is still running.

### Direction: clock positions

The user story's "2 o'clock" is the convention orientation-and-mobility instructors teach. The camera only sees ahead, so positions run from about **10 to 2 o'clock**. Each hour is 30°, so the horizontal position *x* in the frame (0 to 1) maps to an angle of `(x − 0.5) × horizontal field of view` and then to the nearest hour. The current left / ahead / right thirds become 10, 11, 12, 1, 2 o'clock. The glasses' horizontal field of view has to be measured once on the team's glasses (see open questions); the phone camera's comes from CameraX.

Users who don't know the clock convention can choose **Left / Ahead / Right** in Settings. That is what "Check for hazards" says today.

### Distance: words, not feet

The one-shot check says feet ("6 feet ahead"). The watch says **bands**: *very close* (under 1 m / 3 ft), *close* (1–2.5 m / 3–8 ft), and *ahead* (2.5–4 m / 8–13 ft). Bands are quicker to hear and act on while walking, and they don't promise a precision the depth model doesn't have (see measurements). Users who want numbers can choose feet in Settings.

### Sound and vibration

| Signal | Sound | Vibration | Why |
|---|---|---|---|
| Watch started | Rising two-note chime + "Hazard watch on. I'll warn you about things in your path." | `Haptics.ready` | Matches the existing ready cue |
| Watch stopped | Falling two-note chime + "Hazard watch off." | single tick | The opposite of start, so the two can't be confused |
| Critical | A distinct, sharp three-pulse tone, played first, *then* the words | Three long buzzes (new `Haptics.hazardCritical`) | The tone arrives within milliseconds, faster than any sentence |
| Warning | Single soft tone | One medium buzz (new `Haptics.hazardWarning`) | Distinct from critical without listening to the words |
| Can't see | Low two-note "uh-oh" | `Haptics.rejected` | Reuses the existing meaning: something isn't working |

- **Direction by sound:** pan each tone toward the hazard's side in stereo. The Ray-Ban glasses have open-ear speakers on both arms, so a tone from the left arm says "left" before a word is spoken. Android's `AudioTrack.setStereoVolume` does this with the existing `Chimes` synthesis. Whether the glasses' Bluetooth audio keeps stereo is an [open question](#open-questions).
- **Vibration** is on the phone, which is often in a pocket, so it backs up the sound and never replaces it. Like every VISOR vibration, it follows the Vibration setting, and it is marked `USAGE_ACCESSIBILITY`.
- **Volume:** a new **Hazard alerts** slider in `AudioMix`. It starts at 100%. If the user starts the watch with hazard alerts or "All sounds" turned off, VISOR says so: "Hazard alerts are muted. Turn them up in Settings to hear warnings." A watch that can't be heard shouldn't silently run.
- **Flashing:** the glasses display may show the hazard's direction as an arrow, but never as flashing. Section 508 allows at most 3 flashes a second, and VISOR's motion rules already avoid flashing.

### Starting, stopping, and checking it

| Way | Start | Stop | Notes |
|---|---|---|---|
| Home | A **Hazard watch** switch at the top of Home, above Check for hazards. Full width, with its state in words ("Hazard watch: On") and in TalkBack ("Hazard watch, switch, on") | the same switch | Kept separate from the one-shot button, so neither ever changes meaning |
| Wake phrase | "VISOR, start hazard watch" | "VISOR, stop hazard watch" | Two new phrases in `tools/kws/keywords_raw.txt`. Like the other camera wake phrases, each can be turned off in Settings |
| VISOR GO | "start hazard watch" | "stop hazard watch" | "VISOR GO, hazard mode" now starts the watch too. Bare "stop" still only stops the speaking: a quiet request must never end safety alerts by accident |
| Glasses | A **Hazard watch** item on the glasses display, first in the list | same item | The watch holds the glasses session, so it needs the display's items to share that session (see open questions) |
| Ask | "VISOR GO, is hazard watch on?" | | Answers "Hazard watch is on, and can see." or "...on, but can't see right now." |

**While the watch is on:**

- The other camera features still work. They take their picture from the watch's stream, so there's no reconnect delay, and their answers can be paused by a critical alert and then resumed.
- It keeps running with the screen off. On Android this needs a foreground service with an ongoing notification ("VISOR hazard watch is on. Tap to stop."). That is also how the user can find and stop it from the lock screen.
- **With the phone camera** (`CameraChoice.PHONE`), Android only allows the camera while VISOR is on screen, and the phone has to be held or chest-mounted facing forward. The watch says so when it starts: "Using your phone's camera. Hold it facing forward, at chest height." If the screen turns off, it tells the user it has stopped seeing, as in rule 6.
- **Auto-stop**: after 30 minutes without steps, VISOR asks "Still using hazard watch?" and stops 30 s later if there's no answer. This saves battery and glasses heat for someone who sat down and forgot.

### The first time

The first time the watch starts, VISOR says once:

> "Hazard watch warns you about things it recognizes in your path. It can miss things, like steps, curbs, and holes, so keep using your cane or guide."

That sentence names what it **can't** see, which is honest and specific. Once phase 2 adds drop-offs and stairs, the sentence changes with it. Help gets a **Hazard watch** page with the same message, the sounds (with a button to play each), and the voice commands.

### Settings: Hazard watch section

| Setting | Options | Default | Why it's there |
|---|---|---|---|
| Alerts | **Essential** (critical + warnings) / **Everything** (adds notices) | Essential, or Everything for users who said in setup they have *trouble seeing the sides* (peripheral loss), who most need to hear about things beside them | Different visions need different amounts of information |
| Direction | Clock (10–2 o'clock) / Left, ahead, right | Clock | The convention from O&M training; the other option for those who don't know it |
| Distance | Words (very close, close) / Feet | Words | See above |
| Heartbeat | On / Off | Off | Proof it's running, for those who want it |
| Hazard alerts volume | in Sounds, with the other sliders | 100% | `AudioMix` |

Users who said their vision affects them **a lot** (`Severity.SEVERE`) get the warning band widened to 3 m by default, for more time to react.

---

## The Plan

### Phase 1: Hazard watch on today's toolset

*Goal: the whole interaction design working end to end, with honest limits.*

| Where | What |
|---|---|
| `backend/services/DepthEstService.py` | Do the existing TODO: downscale to the model's 518 px before inference. Return the image size the boxes refer to. Log per-stage time. |
| `backend/routes/YoloRoutes.py` | Accept `?watch=1` to skip objects below 0.35 confidence, and return processing time in a header so the app can show and log real latency |
| `network/HazardClient` | For the watch, send 640 px JPEGs at quality 70 (37 KB rather than 85 KB; [measured](#1-upload-size-and-mobile-data-measured)) |
| `backend/scripts/bench_hazard_watch.py` *(added)* | Run it on the GPU server first. Its numbers set the bands and the check rate |
| `capture/StreamingFrameSource` *(new)* | A long-lived glasses session (camera + Motion, as `LabGlassesLink` does) that holds `GlassesLease` as `"hazard_watch"`. It hands out the latest frame, and it implements `PhotoSource`, so the other features can take their pictures from it while it runs. |
| `hazards/` *(new package, pure Kotlin)* | `HazardTracker`, `HazardScorer`, `AlertPolicy`, each unit-tested with scripted detection sequences: flicker, approach, grouping, cooldowns, stale drops, can't-see |
| `hazards/HazardWatch` *(new)* | Runs sampler → server → tracker → policy, owns the foreground service, and reports `HazardWatchState` (off / starting / watching / can't see) to `VisorUiState` |
| `tts/SpokenPhrases` | Every hazard-watch sentence, in one place, as the rest are |
| `tts/Chimes`, `ui/feedback/Haptics` | Critical and warning tones (stereo-panned) and vibration patterns |
| `data/AudioMix`, `data/UserProfile` | *Hazard alerts* volume category; the hazard-watch settings |
| Home, glasses, voice, wake phrases, Help, Settings | As designed above. `capture/Availability`: hazard watch is **Locked** offline |

### Phase 2: See what matters, judge distance correctly

*Goal: drop-offs, stairs, curbs, poles, and distances that hold up outdoors.* See the [tool analysis](#tool-quality-analysis) for why each step was chosen.

1. **Detector:** a YOLO11n fine-tuned with VISOR's hazard classes (stairs up, stairs down, curb, pole/bollard, hole/drop-off, open door, low branch). It follows the two-stage recipe already in [yolo-research.md](./yolo-research.md), with COCO images mixed in so people and vehicles aren't forgotten. YOLOE text prompts can bootstrap the labels for the new classes (auto-label, then review in CVAT or Roboflow), which cuts the labeling work.
2. **Depth:** use the **Outdoor** metric model outdoors and the Indoor one indoors. Choose by GPS accuracy and ambient light, or by a scene label from ML Kit, which is already on the phone.
3. **Ground-plane distance as a cross-check:** for anything standing on the floor, its distance follows from the camera's height and pitch (IMU) and the bottom edge of its box: `distance ≈ eye height ÷ tan(pitch below horizon + angle of the box's bottom edge)`. It needs no depth model, so it works offline and catches a depth model that is badly wrong.

### Phase 3: On the phone

*Goal: works offline, and responds about 3× faster.*

- Export the phase 2 detector to LiteRT (TFLite), as `ultralytics export format=tflite` does, and run it on the phone at about 5–10 checks a second. Published YOLOv8n timings on mid-range phones are 75–117 ms ([nx_tflite_mob](https://hexdocs.pm/nx_tflite_mob/delegates.html)); YOLO11n is a similar size. Measure on the team's test phone before committing.
- Distance on the phone: ground-plane geometry plus box growth. The server depth model becomes an online-only refinement.
- `Availability`: hazard watch becomes **Works** offline (or *Limited*: "distances are estimates offline").

---

## How We'll Know It Works

A test course indoors (hallway with chairs, a door, a person crossing) and outdoors (sidewalk, curb, pole, parked bike, a car passing at a safe distance), walked by sighted testers wearing low-vision simulation glasses, with a spotter, before any low-vision participant.

| Measure | Target for phase 1 | How |
|---|---|---|
| In-path obstacles *of supported classes* warned at ≥ 1.5 m | ≥ 90% | Course log vs alert log |
| False critical alerts | < 1 per 10 minutes | Alert log, reviewed against video |
| Total alerts while walking a cluttered hallway | ≤ 6 per minute | Alert log |
| Hazard to first sound (end-to-end) | ≤ 1.2 s at the 90th percentile | Timestamps from frame capture to `Chimes` start |
| "Can't see" announced after a dropout | ≤ 3.5 s, every time | Pull Wi-Fi / power off glasses mid-walk |
| Users who'd keep it on | Majority, after a 10-minute walk | Short interview: too much, too little, too late? |

---

## Open Questions

To answer on the team's glasses before or during phase 1:

1. **Glasses horizontal field of view.** Needed for clock positions. Measure: stand 2 m from a wall, mark the frame's left and right edges.
2. **Can one session carry Camera, Motion and Display?** If not, the glasses' tap navigation steps aside while the watch runs (as it does for the lab today), and stopping the watch from the glasses would need the capture button (MWDAT Inputs, still experimental).
3. **Heat and battery** during 30+ minutes of streaming at 15 fps. The head-motion lab's V3/V4 thermal marks are the first data. The MEDIUM stream at a lower frame rate (the watch only needs about 2 frames a second) should help.
4. **Stereo audio** through the glasses' Bluetooth connection. Does panning a tone left reach only the left arm?
5. **Head vs path.** The glasses look where the head points, not where the feet go. A user looking left while walking straight gets "in path" judged against the wrong direction. The IMU's head yaw relative to the walking direction (from step detection) could correct this; phase 2.
6. **Background streaming.** Confirm that the MWDAT stream keeps delivering frames to a foreground service with the phone locked.

---

## Sources

- VISOR: [yolo-research.md](./yolo-research.md), [vlm-model-research-and-selection.md](./vlm-model-research-and-selection.md), [phone-camera-fallback.md](./phone-camera-fallback.md), [section-508-accessibility-guidelines.md](./section-508-accessibility-guidelines.md), [preexisting-low-vision-tech.md](./preexisting-low-vision-tech.md) (SuperSense and SWAN as prior art for walking alerts), [mwdat-1.0-upgrade.md](../developer/mwdat-1.0-upgrade.md), [imu-pose-tracking-report.md](../testing/imu-pose-tracking-report.md)
- [Ultralytics YOLOE documentation](https://docs.ultralytics.com/models/yoloe): open-vocabulary detection with text prompts
- [Depth-Anything-V2 Metric Outdoor Small](https://huggingface.co/depth-anything/Depth-Anything-V2-Metric-Outdoor-Small-hf)
- [TFLite delegate timings on mobile (nx_tflite_mob)](https://hexdocs.pm/nx_tflite_mob/delegates.html): YOLOv8n on a mid-range phone
- [Obstacle warning sound design for blind users (vrtonung)](https://www.vrtonung.de/en/?p=15629): a practitioner's account of moving away from continuous alarm tones
- [Mobilio, Harvard SEAS](https://seas.harvard.edu/index%2ephp/news/smartphone-navigation-app-people-blindness-and-low-vision): audio cues personalized to each walker
