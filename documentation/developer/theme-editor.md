# My colors (theme editor)

VISOR has four looks of its own: VISOR light, VISOR dark, and their High contrast versions. *My colors* lets a user pick their own color for each part of VISOR instead, starting from any of those four. It lives in **Settings > Text & display > My colors**.

> Low vision is not one thing. Some users read best with yellow on black, some need a dim page because bright light hurts, and some see one color far better than another. See [Low vision research](../research/low-vision-research.md).

## What the user sees

- **Light or dark** now has four choices: *Match phone*, *Light*, *Dark* and *My colors*. Choosing one of the first three keeps the user's colors saved for later. Until the user has picked any colors, *My colors* opens the editor instead of switching to colors that would look the same.
- **Pick my colors** opens the editor. While the user's colors are in use, the *High contrast* switch is replaced by a note and a *Use VISOR's colors* button, because High contrast is one of VISOR's own looks.
- **The editor** shows:
  1. A **preview** drawn in the colors being picked: a heading, smaller notes, a card, a button, a picked and an unpicked choice, a focus ring and an error message.
  2. **Start from**: the four looks. Every color the user doesn't change keeps that look's exact color. Changing the look after picking colors asks first, because it clears them.
  3. **Six parts**, each with its color, the color's name, and whether it is easy to read.
  4. **Use these colors**, plus **Undo changes** and **Use VISOR's colors** when they apply.
- **The picker**, for one part, shows:
  - *Before* and *Now* swatches.
  - Whether the color is easy to read, and **Make readable** when it isn't.
  - 20 **quick colors**.
  - Three sliders: **Hue**, **Colorfulness** and **Lightness**.
  - A **hex code** field, for a sighted helper.
  - **Reset to the starting color**.

### The six parts

| Part | `ThemeElement` | What it colors | Material roles it sets |
|---|---|---|---|
| Background | `BACKGROUND` | The page | `background`, the lowest surface containers |
| Cards and tiles | `SURFACE` | Settings' tiles, cards, chips that aren't picked | `surface`, `surfaceContainer*`, `surfaceVariant` |
| Text | `TEXT` | Words and their icons | `onBackground`, `onSurface`, `onSurfaceVariant`, `inverseSurface` |
| Buttons and headings | `BUTTONS` | Filled buttons, headings, a picked chip's edge | `primary`, `secondary`, `tertiary`, `surfaceTint` |
| Picked choices | `HIGHLIGHT` | A picked chip's fill, a switch that is on | `primaryContainer` and its secondary and tertiary twins |
| Borders and focus ring | `OUTLINE` | Every border, and the focus ring | `outline`, `outlineVariant`, `VisorTheme.focusRing` |

VISOR works out everything else, by three rules (`ui/theme/CustomTheme.kt`):

1. **What isn't touched stays as it was.** Unchanged, a user's colors are exactly the starting look (`CustomThemeTest`).
2. **What follows from a change follows it.** A new page color tints the cards to match, unless the user also picked the cards' color. In High contrast the cards stay the page's color. New button colors carry over to picked choices: a pale tint in VISOR's looks, a solid fill in High contrast.
3. **What VISOR works out is always readable.** That covers text on a filled button or a picked choice (whichever of the page color, black or white reads), softer secondary text (or the text color itself if a softer one wouldn't read), and error, warning and success. Those three keep their red, amber and green but move just light or dark enough to read on every surface. So any pair the check finds too faint involves a color the user picked, and the editor can name it.

## Why it can't lock anyone out

A user with low vision who picks colors they can't see must still be able to get back. So:

- **The editor itself stays in the colors in use.** Only the preview uses the colors being picked, so the controls for fixing colors never become unreadable.
- **Nothing changes until *Use these colors*.**
- **"VISOR GO, use VISOR colors"** (or "reset colors", "default colors", "normal colors") always goes back to VISOR's own look, as the user had it. *My colors* is a switch beside `themeMode` and `appHighContrast`, not a replacement for them, so turning it off restores exactly those. Help says this too.
- Hard-to-read colors are **warned about, not blocked**. Some light-sensitive users want a dimmer pair on purpose. The warning is in words with an icon, never color alone: "Hard to read with Text: 2.1:1, needs 4.5:1". A screen reader hears "2.1 to 1".

## Checking contrast

`ui/theme/ContrastAudit` lists every color pair VISOR draws and the contrast each needs: 4.5:1 for text and 3:1 for the parts of a control (WCAG 1.4.3 and 1.4.11, as Section 508 adopts them). Each pair also records which of the six parts it is made from. The list is used in two places:

- `ThemeContrastTest` runs it on VISOR's four looks. A color changed in `Color.kt` that breaks a pair fails the build.
- The editor runs it on the user's colors as they pick them.

Because both use the same list, the build and the editor can't disagree about what is readable. **When you draw a new color pair anywhere in VISOR, add it to `ContrastAudit.checks`.** That one change covers both.

**Make readable** (`makeReadable`) keeps the part's hue and searches lighter and darker, nearest first, for a color with no failing pair. It moves only as far as it must. It works out the whole look for each lightness it tries, so the editor runs it off the main thread.

## The color picker

The sliders work in **OKLCH**, Björn Ottosson's OKLab as lightness, chroma and hue (`ui/theme/ColorMath.kt`, no new dependency):

- **Lightness** follows how bright a color *looks*. That is what decides contrast, so a user nudging it toward readable sees steady progress. HSV's "value" doesn't do this: pure yellow and pure blue share one, yet black text reads only on the yellow.
- **Colorfulness** is a share of the most chroma the screen can show at that hue and lightness, so every position changes the color. A color beyond what the screen can show is brought in at the same hue.
- **Hue** is kept apart from the color in the picker, so passing through gray doesn't snap the hue back to red.

Each slider is a `VisorSlider` with a gradient track (`trackColors`), so it keeps − and + buttons, a large thumb, focus rings and screen-reader values ("62 percent", "255 degrees, dark blue"). There is no 2D color square: it needs precise dragging, and a screen reader can't use it.

Every color has a plain **name** (`colorName`: "dark navy blue", "light gray"). It is shown under each swatch and is what a screen reader says for each quick color, so a user who can't make out a swatch still knows what they picked.

## Other changes

- **The status bar's icons** now follow VISOR's page, not the phone's dark mode (`MainActivity`). Before, VISOR set to Dark on a light phone had dark icons on a dark page. A user's own colors made this worse.
- **"VISOR GO, high contrast"** with the user's colors in use switches to VISOR's High contrast look.
- **The suggested dark, high-contrast look** (Settings > About you, and setup) switches the user's colors off, and leaves them saved.

## Where each piece lives

| Where | What it does |
|---|---|
| `data/CustomColors.kt` | `ThemeElement`, `ThemeBase`, and `CustomColors`: the look to start from and only the colors picked. Saved as one line (`encode`/`decode`) by `ProfileStore`. |
| `data/UserProfile.kt` | `useCustomColors` and `customColors`. |
| `ui/theme/Theme.kt` | `VisorColors`: everything VISOR draws with, from `VisorColors.forProfile`. `VisorTheme(colors = …)` and `VisorColorsProvider` (the preview). |
| `ui/theme/CustomTheme.kt` | `customVisorColors`, `keyColors`, `makeReadable`. |
| `ui/theme/ContrastAudit.kt` | Every pair, its target, and its parts. |
| `ui/theme/ColorMath.kt` | WCAG contrast, OKLCH, gamut, color names, hex. |
| `ui/screens/profile/ThemeEditor.kt` | The editor, the picker, and the preview. |
| `ui/screens/profile/SettingsScreen.kt` | *My colors* in Text & display. `SettingsSection.COLORS`, opened from Text & display, not the hub. |
| `ui/voice/` | `UseCustomColors`, `UseVisorColors`, and "change my colors" / "open color settings". |

## Testing it

- **Unit tests** (`./gradlew testDebugUnitTest`):
  - `ThemeContrastTest` checks VISOR's four looks through `ContrastAudit`.
  - `CustomThemeTest`:
    - Unchanged colors are exactly the starting look.
    - Each of the three rules holds.
    - What VISOR works out reads with any page and button colors.
    - Error messages stay red and readable on any gray.
    - *Make readable* fixes many faint pairs.
    - Saving and loading round-trips, and damaged saved colors are skipped.
  - `ColorMathTest`:
    - OKLCH matches published values and round-trips over the whole color cube.
    - Out-of-gamut colors keep their hue.
    - `readableOn` always reaches 4.5:1.
    - Hex codes parse, and colors get their names.
  - `ColorsCommandTest`, and the voice tests that list every command.
- **On a phone or the emulator:**
  1. Settings > Text & display > Pick my colors. Start from VISOR dark, set Text to a dark blue, and check that it says it is hard to read with the background. Press *Make readable* and check that the text becomes light enough to read and stays blue.
  2. Press *Use these colors*, then say "VISOR GO, use VISOR colors". VISOR should be dark again, as before.
  3. Repeat with TalkBack on. Every swatch, slider and readability line should be spoken, and the preview should be read as one item.
  4. With your colors in use and a light page, set the phone to dark mode. The status bar's icons should stay dark.
