# Preexisting Technology and Solutions Surrounding Low Vision - What We Can Learn/Borrow
*Research by Jaurelus*

---

## Wearable Devices

### eSight
A headset/goggle-style wearable with an HD camera that captures the environment and enhances the image in real time. Key features:

- Contrast boosting
- Color adjustment
- Image magnification

Designed for users who still have some functional vision - it amplifies and enhances what's already there rather than replacing it entirely.

---

### IrisVision
A headset/goggle-style wearable using a custom mount for a Samsung Galaxy smartphone as its display and processing unit. Key features:

- Image magnification and contrast adjustment
- Text read aloud
- **Bubble view** - a magnified circular window overlaid on the normal view, letting users focus on a specific area while still seeing surrounding context

The bubble view is particularly relevant for **macular degeneration**, as it is designed around how that condition degrades central vision specifically.

---

### OrCam MyEye
A clip-on camera that attaches to the frame of the user's existing glasses. Processing happens locally on the device, with output delivered via a **bone conduction speaker** (described as whisper-like). Key features:

- Printed text reading (books, signs, screens)
- Handwriting recognition
- Barcode and product label scanning
- Currency identification
- Facial recognition
- Color identification
- Time and date announcements

Notable for its **local AI processing** - no cloud dependency for core functions.

---

### Envision Glasses
Glasses-form-factor device geared primarily toward audio output. Camera input is processed through AI, with output via bone conduction or a speaker. Key features:

- Instant text reading
- Document scanning and handwriting recognition
- Multi-language support
- Scene description in natural language
- Face recognition and identification
- Color and light detection
- Barcode and product scanning
- **Remote sighted assistance** - users can connect to agents or trusted contacts who see through the glasses in real time

---

## Phone Applications

### Seeing AI *(Microsoft)*
A broad, feature-rich app covering a wide range of assistive functions:

- Short text and document reading (with formatting context)
- Barcode and product scanning
- Currency identification
- Light and color detection
- **People** - facial identification, age estimation, emotion detection
- **Scene** - natural language description of the environment around the user

---

### Lookout *(Google)*
Primary strength is **real-time environmental narration**. The explore mode continuously describes the scene as the user moves, focused on painting a picture of the surroundings. Also supports text and food label scanning.

---

### SuperSense
An obstacle detection application built around active navigation:

- Real-time obstacle alerts while walking
- Distance estimation
- Person detection
- Text reading
- Scene description

---

### Be My Eyes
Takes a human-centered approach - a low vision user requests help and a **sighted volunteer connects via live video call** to describe what they see. If no volunteers are available, AI provides descriptions as a fallback.

---

### TapTapSee
Focused specifically on **object identification**. After receiving a photo of a 2D or 3D item, the system identifies the object and returns the result in audio format.

---

### BlindSquare
A GPS navigation app for both indoor and outdoor use. Pulls data from **FourSquare** and **OpenStreetMap**, then communicates points of interest, street intersections, and environment information through a dedicated speech synthesizer. Supports searches, check-ins, look-around, and adding places.

---

### Voice *(Android AR)*
A utility that transforms live camera input into **spatial audio** for navigation:

- Horizontal axis of the image → represented by time (left = start of sound, right = end)
- Vertical axis → represented by pitch (high in image = high pitch, low = low pitch)
- Brightness → represented by volume (bright = loud, dark = quiet)

Additional features: compass detection, facial recognition, talking GPS locator.

---

### SWAN
Provides customized GIS-based navigation through audio only. Combines **wayfinding and obstacle identification** - the user defines their location and the GPS system suggests a safe, optimal path by firing beacon sounds. *(Further research recommended.)*

---

## Foundation / Platform-Level Tools

These are long-standing accessibility utilities that predate many of the above apps and are commonly used as building blocks:

- **VoiceOver** *(Apple)* - screen reader built into iOS/macOS
- **Magnifier** *(Apple)* - built-in iPhone camera magnification tool
- **TalkBack** *(Google)* - screen reader built into Android

---

## Academic References

- [IMT HAL Science - hal-01115851](https://imt.hal.science/hal-01115851/document)
- [Springer - DOI 10.1007/s007790200002](https://link.springer.com/article/10.1007/s007790200002)

---

## Key Takeaways for MIRA

| Capability | Best Example(s) | Relevance to MIRA |
|---|---|---|
| Local AI processing | OrCam MyEye | Reduces latency, no cloud dependency |
| Bone conduction audio | OrCam, Envision | Discreet output without blocking ambient sound |
| Real-time scene narration | Lookout | Model for continuous environment description |
| Text reading | OrCam, Envision, Seeing AI | Core MIRA feature (Image Text-to-Speech) |
| Obstacle / distance detection | SuperSense, SWAN | Relevant to spatial awareness feature (deferred) |
| Remote sighted assistance | Be My Eyes, Envision | Potential future MIRA feature |
| Spatial audio navigation | Voice | Novel input model worth exploring |
| Macular degeneration UX | IrisVision (bubble view) | Design consideration for target users |
