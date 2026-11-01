# Phone Camera Fallback
*Analysis and design for using the phone's own camera when the glasses aren't available*

---

## The Question

VISOR's four camera features (check for hazards, read text, identify objects, describe the scene) all take their picture with the Meta glasses. Without a working pair, VISOR does nothing useful. Should VISOR offer the phone's camera instead, and does that serve a low-vision user, or only the developers testing without glasses?

**Short answer: yes, for both, provided the user always knows which camera is in use.**

---

## Who It Helps

| Situation | Today, without the fallback | With it |
|---|---|---|
| Glasses left at home, flat, or charging | Every feature fails: "No connected glasses were found" | Same features, same buttons, phone in hand |
| Glasses waiting on a firmware or glasses-app update | Blocked until the update finishes | Keeps working meanwhile |
| A user who doesn't own Meta glasses yet (or can't afford them) | VISOR can't be tried at all | VISOR is useful from the first day |
| Reading small print | Glasses stream is 720 x 1280, fixed focus | Phone camera: autofocus, about 5 MP, can be brought close to the page |
| Developers and testers | Mock glasses in the debug menu, choosing a photo each time | The emulator's virtual camera, or any phone, works immediately |

The low-vision research ([low-vision-research.md](./low-vision-research.md)) puts reading (labels, mail, finances) at the top of everyday struggles. Those are the moments a user can least afford VISOR not working. The apps users already know (Seeing AI, Lookout, TapTapSee, see [preexisting-low-vision-tech.md](./preexisting-low-vision-tech.md)) all use the phone camera, so holding up a phone to read something is already a familiar habit.

---

## Risks, and How the Design Answers Them

| Risk | Why it matters for low vision | Design response |
|---|---|---|
| **Not knowing which camera took the picture.** A user thinks the glasses are in use, and the phone in their pocket takes a black photo. | They can't see the photo to notice. "No text found. Try moving closer." would send them looking for the wrong problem. | The choice is explicit and never switched automatically. VISOR says "Taking picture **with your phone**" every time, Home shows "Using your phone's camera", and a black photo is caught (`BlackPhoto`) and explained: "The picture came out black. Make sure nothing is covering the camera on the back of your phone." |
| **Aiming a phone without seeing well.** | Glasses point where the head points; a phone points wherever the hand holds it. | The Pair screen and the voice reply say how to hold it ("point the back of your phone at what you want to know about"). The phone's wide lens and higher resolution leave room for error. Aiming guidance is a follow-up (below). |
| **Hands.** | Glasses are hands-free. A cane or guide-dog user has one hand to spare, at most. | The fallback is a fallback: the glasses stay the default, and Help presents the phone as the option for when glasses aren't available. |
| **Android blocks the camera in the background.** | A wake phrase heard while VISOR isn't on screen can't use the phone camera. | Checked before trying, and said plainly: "Open VISOR on your phone to use its camera." |
| **Predictability (Section 508, *Understandable*)** | Behavior that changes on its own is confusing. | A persistent choice the user makes, changed only by the user. |

### Why not switch automatically?

An automatic "glasses if connected, otherwise phone" setting is tempting, but it fails exactly when it matters. Glasses drop out of a connection without the wearer noticing, and VISOR would then quietly take the picture with a phone that is usually in a pocket. The no-glasses error now does the useful part of automatic switching instead: it says how to switch ("...or switch to your phone's camera on the Pair screen"), and leaves the choice with the user.

---

## Where Users Can Choose the Camera

Several ways were considered. The ones marked *built* are in this change.

| Option | Status | Notes |
|---|---|---|
| **Pair screen: "Which camera VISOR uses"** (My glasses / My phone) | Built | Right under Connect my glasses. That is where someone without their glasses goes, and where the error sends them. Radio-button chips (`SelectableChip`) that stack at large text sizes, with a live-region line explaining the chosen one. |
| **Voice: "VISOR GO, use my phone camera" / "use my glasses camera"** | Built | Hands-free and eyes-free, and listed in Settings with the other voice commands. "Switch back to my glasses camera" works too. |
| **The spoken error when no glasses are found** | Built | It now says how to switch cameras, so the user hears the fix right when they hit the problem. |
| **Home: "Using your phone's camera"** | Built | Shown only while the phone is chosen, under the title, so it costs the camera buttons little room. |
| Settings section | Not built | Settings is about how VISOR sounds and looks. The camera is hardware, which belongs on the Pair screen. Easy to add a link later if testers look for it there. |
| Onboarding question ("Do you have Meta glasses?") | Follow-up | Would set the phone camera from the start for users without glasses. |
| Android's camera app (`ACTION_IMAGE_CAPTURE`) | Rejected | Every phone maker's camera app is different, with small shutter buttons and extra steps to confirm the photo. Users would have to learn a different screen on every phone. |
| Live viewfinder on Home | Follow-up | Useful for users with some sight, together with aiming guidance. Not needed for parity with the glasses, which have no viewfinder either. |

---

## How It Works

- `capture/PhoneCameraSource` is a `PhotoSource`, just like `GlassesPhotoSource`, so the four features didn't change. It uses CameraX: for each picture it binds an `ImageCapture` to the activity, takes one photo, and unbinds, so the camera is never on between questions. Photos are turned upright before ML Kit or the server sees them. Most features get about 2.8 MP; `forReading` asks for about 5 MP for small print.
- `capture/ChosenPhotoSource` asks the profile (`UserProfile.camera`, a `CameraChoice`) at every picture, so a switch takes effect on the very next one.
- `capture/BlackPhoto` decides whether a photo is black from a 16 x 16 copy of it: dark on average *and* nowhere bright. A dim room with a lamp or window still counts as a photo. Unit tested in `BlackPhotoTest`.
- Everything VISOR says about the phone camera is in `tts/SpokenPhrases`.

### Testing without glasses

Choose **My phone** on the Pair screen. On the Android emulator, the back camera shows its virtual scene (set it in the AVD's *Camera* settings: *VirtualScene*, *Emulated*, or a webcam). Mock glasses in the debug menu still work as before when **My glasses** is chosen.

---

## Follow-ups

- **Aiming guidance**: say "Move the phone back" or "Part of the page is cut off" using ML Kit's text block bounds, as Seeing AI does.
- **Low light**: turn on the torch automatically in a dark room (the research lists low-light help as a need), with its own setting.
- **A capture cue**: a haptic tick when the photo is taken, so the user knows they can lower the phone. It would need a pattern in `ui/feedback/Haptics`.
- **Onboarding**: ask whether the user has glasses, and choose the camera to match.
