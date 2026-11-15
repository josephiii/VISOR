# Voice Options

VISOR's users rely on its voice as their main source of information all day, so they can choose the voice it speaks in.

> *As a user, I want natural-sounding, clear text-to-speech output, so that I can comfortably rely on audio as my primary information channel throughout the day.*

This is done in two phases. Phase 1 is built. Phase 2 goes ahead only if a voice model passes the benchmark described below.

## Phase 1: the phone's own voices (built)

VISOR already speaks through Android's `TextToSpeech`. Its engine (on most phones, Google's speech services) has several English voices, and `TextToSpeech.getVoices()` lists them. Phase 1 adds no dependencies and no app size.

| Where | What it does |
|---|---|
| [`tts/VoiceCatalog.kt`](../../frontend/visor/app/src/main/java/ucf/visor/tts/VoiceCatalog.kt) | Plain Kotlin, unit tested. Picks which voices are offered and names them. Decides which voice to speak in given the user's choice and the connection, and cycles voices for voice commands. |
| [`tts/Speaker.kt`](../../frontend/visor/app/src/main/java/ucf/visor/tts/Speaker.kt) | Lists the engine's voices (`voices`), and switches to one (`useVoice`), restarting a reading's current sentence in the new voice. |
| `MainActivity` | Applies `VoiceCatalog.voiceToUse(profile.voiceName, voices, isOnline)` whenever any of the three changes. |
| `data/UserProfile.voiceName` | The chosen voice's engine name, saved as `voiceName`. `null` (the default) means VISOR's default voice, so updating changes nothing. |
| Settings → Speech → *How VISOR sounds* | The choice: a sample on every pick, *Hear VISOR's voice*, *Get more voices*. |
| `ui/voice` | "VISOR GO, next voice / previous voice / default voice". The reply is said in the new voice. |

### What is offered

- **English voices only:** VISOR's phrases and sentence splitting are English.
- **Downloaded voices only:** voices whose features include `notInstalled` are not offered.
- **No duplicates:** Google's `en-US-language` and similar names are the default voice again, so they are left out.

Engine names like `en-us-x-iol-local` tell a listener nothing. Each voice is offered as its accent and a number instead, such as **"US English voice 2"**. A voice and its on-phone twin share the number. Google names its two versions of a voice `…-local` and `…-network`.

Settings lists VISOR's default voice first, then the voices on the phone, then the voices that need the internet. Within each group, voices are sorted by accent and then by number.

### Voices that need the internet

`Voice.isNetworkConnectionRequired()` tells VISOR which voices need the internet.

- **Online:** these voices can be chosen. A notice above them explains that what VISOR says, including text it reads, is sent to the phone's speech service.
- **Offline:** these voices are locked the same way as Home's buttons (see [Offline features](./offline-features.md)):
  - padlock and dashed outline
  - a second line reading "Needs the internet"
  - three buzzes when pressed
  - a spoken reason
- **Already chosen when the phone goes offline:** VISOR immediately speaks in the voice's on-phone twin, or in its default voice if there is no twin. It does not wait for the engine to give up on its server, because a hazard warning can't wait. The user's choice is kept and comes back when the phone reconnects. Settings explains this in a note.
- **Weak connection:** a connection can be too poor to use for a while before it counts as offline. As a backstop, `Speaker` passes `KEY_FEATURE_NETWORK_TIMEOUT_MS` (2 seconds) with every utterance.

### Accessibility

- Every voice is a `SelectableChip`, announced as a radio button. Its new `supportingText` line ("Works offline" / "Needs the internet") is part of the name a screen reader reads.
- Every status is shown in words and an icon, never in color alone.
- All the voice lists form one `selectableGroup`, so a screen reader counts them as a single list.
- Chips grow with the user's text size and never truncate.
- Every pick plays a sample in that voice at the user's speed and answer volume. Voices are compared by ear, not by name.
- Notes (offline fallback, voice removed) are polite live regions.

### Testing

- **Unit tests:** `VoiceCatalogTest`, `VoiceChoiceCommandTest`, and `VoiceCommandKindTest` (`./gradlew testDebugUnitTest`).
- **On a phone or emulator:**
  - Choose a voice that needs the internet, then turn on airplane mode. After about 3 seconds VISOR should switch to an on-phone voice, and Settings should show the offline note and padlocks.
  - Turn airplane mode off. The chosen voice should come back.
  - Check the screen with TalkBack and with *Extra large* text.
  - The emulator's system image may have only one or two voices. Install more under *Get more voices*.

## Phase 2: neural voices on the phone (benchmark first)

The [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) library VISOR already bundles for wake phrases (`app/lib/sherpa-onnx-1.13.5.aar`) also contains an offline neural text-to-speech engine, `OfflineTts`. It supports Kitten, Kokoro, VITS/Piper and Matcha models, and its arm64 library exports the TTS functions. The engine is already in the app. The voice models are not: they would be new downloads.

### Candidates

| Model | Size | Speed reported on phones (RTF; above 1 is slower than real time) | License |
|---|---|---|---|
| Kitten Nano | about 25 MB, 8 voices | about 0.4 | Apache 2.0 |
| Kokoro-82M int8 | about 92 MB | about 0.6–2.4 | Apache 2.0 (its espeak-ng dependency is GPLv3: check how it is linked before shipping) |
| Piper | about 63 MB per voice | about 0.1 | **Varies per voice.** Most English voices (lessac and anything fine-tuned from it, ryan) come from non-commercial or research-only datasets. |

These speed figures come from third-party benchmarks, which disagree with each other. They are the reason for the gate below.

### Licensing rule

VISOR may go commercial. A model or voice is a candidate only if both the model and its training data allow commercial use. Read each model card's dataset license, not just the code license, and record it here before adding the model.

### Gate

A model ships only if it passes all of the following on 2–3 target phones, including a mid-range one:

- **Speed:** RTF of 0.5 or lower.
- **Latency:** first audio within 700 ms for a 15-word sentence. Hazard warnings can't lag.
- **Memory:** peak memory of 200 MB or less.
- **Clarity:** in listening sessions with target users, rated at least as clear as the platform voice at 1×, 2× and 3× speed. The test text should include prices, addresses and medication names, as read from OCR.

Benchmark on the phones themselves. The server can generate listening samples, but it can't measure phone speed.

### If a model passes

1. `Speaker` gets a `SpeechEngine` interface. Today's code becomes `PlatformEngine`, and a new `SherpaEngine` plays audio through `AudioTrack`. `SherpaEngine` must do itself what the platform engine does for VISOR now:
   - per-category volume
   - speech rate (the model's `speed`)
   - the chime lead-in
   - reading a sentence at a time, synthesizing the next sentence while the current one plays
   - `isSpeaking()`, which the wake-phrase guard relies on
2. Models are downloaded when the user asks, not built into the app. They are checked against a SHA-256 hash and stored in the app's files. The user is told the size first, and that the download needs the internet but the voice then works offline.
3. They are listed with the voices on the phone, marked "Not downloaded" until they are.
