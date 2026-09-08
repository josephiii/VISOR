# Meta Ray-Ban Glasses: Features, Capabilities & Current Offerings
*Research by LucSal6868*

---

## Toolkit Capabilities *(according to documentation)*

The glasses expose a developer toolkit that enables web apps to interface with the hardware. The toolkit provides access to:

- **Glasses display** — the built-in AR display surface
- **Neural Band & Cap Touch controls** — input signals from the Meta Neural Band wristband and the MRBD cap touch interface
- **Motion sensors** — accelerometer, gyroscope, and compass
- **User location** — provided via a connected mobile device
- **Local storage**

---

## Input Mechanisms: Neural Band & Cap Touch

The Neural Band enables a **captouch gesture** interface. Per the documentation:

> MRBD UI navigation is driven by two input mechanisms: the Neural Band and a touch strip on the glasses temple arm that senses swipe gestures. They produce directional and selection inputs that the glasses OS translates into standard arrow key (ArrowUp, ArrowDown, ArrowLeft, ArrowRight) and Enter events delivered to your Web App.

In short: the Neural Band and the temple arm touch strip together act as a navigation controller, producing keyboard-equivalent inputs that web apps can listen for natively.

---

## Hardware Specifications

| Spec | Value |
|---|---|
| **Resolution** | 600 × 600 per eye |
| **Refresh Rate** | 90 Hz |
| **Visible FoV** | 20° diagonal |
| **Rendered FoV** | 20° diagonal |

> Source: [vr-compare.com/headset/metaray-bandisplay](https://vr-compare.com/headset/metaray-bandisplay)

---

## Similar / Comparable Applications

The following applications represent the current landscape of similar assistive/visual tools for reference and competitive analysis:

### Be My Eyes
A platform connecting blind and low-vision users with sighted volunteers or AI for real-time visual assistance.

### Microsoft Seeing AI
An AI-powered app from Microsoft that narrates the world around the user — reads text, identifies people, describes scenes, and more.

### HumanWare — StellarTrek
A dedicated GPS and navigation device designed for blind and low-vision users.
- Product page: [store.humanware.com/hus/stellartrek.html](https://store.humanware.com/hus/stellartrek.html)

---

## Notes & Open Questions

> *(To be expanded as research continues)*

- How does the 20° FoV affect usability for low-vision users who rely on larger visual fields?
- What is the latency profile of the Neural Band input → glasses OS → web app event chain?
- Are there accessibility APIs or hooks beyond the standard arrow key / Enter event model?
