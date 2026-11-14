# Offline Features

VISOR keeps working without the internet. It shows which features still work, and locks the ones that need VISOR's server, so a user never presses a button that waits 10 seconds and then apologizes. Online, every feature is available.

## What works offline

| Feature | Offline | Why |
|---|---|---|
| Read text | Works | ML Kit OCR on the phone |
| Identify objects | Works | ML Kit labels on the phone |
| Describe the scene | **Limited**: names objects only | Uses the server's VLM; offline it goes straight to ML Kit labels and says *"You're offline, so I can only name objects."* first |
| Check for hazards | **Locked** | YOLO and depth run on the server |
| Ask a question | **Locked** | Uses the server's VLM |
| Log in, sign up, email code | **Locked** | Backend calls |
| Delete account | **Locked** | Backend call. Log out works offline: it only clears the phone. |
| Settings, voice commands, wake phrases, glasses, Phase 1 scripts | Works | All on the phone |
| VISOR's voice: voices on the phone | Works | The phone's text-to-speech engine |
| VISOR's voice: voices that need the internet | **Locked** in Settings; VISOR speaks in the voice's on-phone version, or its default voice, until it reconnects | Spoken by the engine's server. See [Voice options](./voice-options.md) |

The camera-feature rules are one function, `CameraCommand.availability(isOnline)` in [`capture/Availability.kt`](../../frontend/visor/app/src/main/java/ucf/visor/capture/Availability.kt). Change it there and Home, the glasses and the wake phrases all follow.

## How a user can tell

Every signal says the same thing, and none rests on color alone:

- **Notice at the top of every screen**: *"**Offline.** Features that need the internet are locked until you reconnect."* It's a polite live region, so TalkBack reads it when it appears. Without TalkBack, VISOR says it aloud (Status updates volume). When the phone reconnects, *"Back online. Every feature is available again."* shows for 6 seconds and is said aloud the same way.
- **Locked buttons** keep their place and their name, and get a padlock, a dashed outline, and a second line, *"Needs the internet"*. TalkBack reads *"Check for hazards, Needs the internet, Locked"*. Pressing one buzzes three times (`Haptics.rejected`) and says why, along with what still works.
- **Limited buttons** (Describe the scene) stay usable and add a second line: *"Offline: names objects only"*.
- **Wake phrases and glasses taps** for a locked feature buzz, say why, and show the reason at the top of the screen, just like a camera command said before logging in.
- **Glasses display**: locked buttons show a padlock in the quieter style, and every screen's status line reads *"Offline. Some features are locked."*
- **Help → Using VISOR offline** explains all of this.

## How "online" is decided

[`network/Connectivity.kt`](../../frontend/visor/app/src/main/java/ucf/visor/network/Connectivity.kt) watches the default network. "Online" means Android has **validated** that the network reaches the internet (`NET_CAPABILITY_VALIDATED`), not just that a network is connected. Clinic Wi-Fi with a sign-in page, or Wi-Fi that drops outside traffic, counts as offline.

Changes are settled (`Flow.settled()`) before they're reported. Going offline counts after 3 seconds, and coming back after 1 second. This way a Wi-Fi to mobile-data handoff doesn't announce anything or lock buttons under a finger. The state sits in `VisorUiState.isOnline`.

> A backend on your local Wi-Fi with no internet behind it shows as offline. Develop against it from the emulator, whose network always validates.

## Testing it

- **Emulator or phone:** turn on airplane mode, and turn Bluetooth back on if you use glasses. The notice appears after about 3 seconds. Turn airplane mode off to see *"Back online"*.
- **Unit tests:** `AvailabilityTest`, `ConnectivitySettledTest`, and the offline cases in `GlassesNavScreenTest` (`./gradlew testDebugUnitTest`).
