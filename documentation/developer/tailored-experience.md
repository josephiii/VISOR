# Tailored Experience

Setup asks each user how they see: which kinds of vision loss they have, how much it affects daily life, and their vision in their own words. VISOR uses those answers so that two users with different vision get a noticeably different VISOR.

> *Two people with the same visual acuity score may experience very different struggles.* See [Low vision research](../research/low-vision-research.md).

## How it works

Everything comes from one pure function: `VisionPlan.from(profile)` in [`data/VisionPlan.kt`](../../frontend/visor/app/src/main/java/ucf/visor/data/VisionPlan.kt). It saves nothing. VISOR works it out again each time it needs it, so a change in Settings > About you applies to the very next picture.

VISOR changes two kinds of thing:

- **Behavior always follows the profile.** This covers what the vision language model is asked to put first, which hazards are said first, and the order of the camera buttons. There is no setting for these, so they never conflict with a choice the user made.
- **Suggestions belong to the user once accepted.** This covers the Detail level and the dark, high-contrast look. Setup offers them, and Settings shows them with an Apply button. VISOR never changes them by itself.

If setup was skipped ("Not sure", "A moderate amount", no description), `VisionPlan.isUntailored` is true and VISOR behaves exactly as it did before this feature.

## What changes, per answer

| Answer | Scene description and questions (VLM prompt) | Hazard check | First Home button | Suggested |
|---|---|---|---|---|
| Trouble seeing the center | Start with what is directly ahead; name it exactly; read its text | Straight ahead first | Read text, then Identify objects | |
| Trouble seeing the sides | Start with what is left and right that they could miss, above all what they could walk into | Left and right first | Check for hazards | |
| Everything is blurry | Read any text; give exact numbers (prices, times, signs) | | Read text | |
| Contrast / light sensitivity | Mention glare and dim light, things that blend into the background, and colors | | Describe the scene | Dark, high-contrast look |
| A lot | Describe things as for someone who can't check with their eyes; mention what they could trip over | Brief names up to 5 instead of 3 | | Detailed |
| A little | Skip what is obvious | | | Brief |
| Their own words | Quoted in the prompt: "In their own words…" (only while sharing is on) | | | |

When someone picks more than one kind of vision loss, each kind gives points to the features it needs most (`FeaturePoints`), and the buttons are sorted by total points. Ties keep the usual order, with hazards at the top. If someone has trouble seeing both the center and the sides, hazards are said nearest first, which is the safest order.

### Where each piece lives

| Where | What it does |
|---|---|
| `capture/ScenePrompts` | Adds `VisionPlan.sceneGuidance` to every Describe the scene and Ask a question prompt, just before the final "read aloud" sentence. |
| `capture/SceneDescriber`, `capture/HazardDetector` | Read the plan at each capture, as they already did for Detail. Wired in `engine/VisorEngine` (`visionPlan()`). |
| `tts/SpokenPhrases.hazardsAhead(focus = …)` | Puts first the hazards in the part of the picture the user is likely to miss. A Brief limit never drops those for being further away: it says the nearest *n* plus the nearest *n* in that part. Each hazard is worded the same in any order. |
| `ui/screens/home/HomeScreen`, `ui/glasses/GlassesNavScreen` | List the camera buttons in `VisionPlan.featureOrder`. The glasses show the four that start straight away, in the phone's order. |
| `ui/screens/profile/ProfileCreationScreen` | Setup: the note under the description, the step that confirms a named condition, the recommended Detail level, and "Here's how VISOR fits you". |
| `ui/screens/profile/VisionFit` | The "Because you…" lines (`tailoring_*` in strings.xml), shown and spoken. |
| Settings → About you | The *Share my description with VISOR's AI* switch, *How VISOR fits you*, and *Use the suggested dark, high-contrast look*. |

### Look around

*Trouble seeing the sides* also gives Look around the **wide** scan: five pictures out to about 90° each side instead of three out to about 56° (`VisionPlan.lookAroundPlan`), with its own "Because you…" line (`tailoring_wide_look_around`). Look around is their second button on Home, after Check for hazards. See [Look around](./look-around.md).

## Setup

- **In your own words:** a note under the field, and the spoken question, say that VISOR's AI will use the description and that this can be turned off in Settings.
- **Add this to your answers?** This step appears only when the description names an eye condition whose usual kind of vision loss the user didn't pick. For example, "I have glaucoma" with "Not sure" chosen offers *Trouble seeing the sides*. The conditions are listed in `VisionPlan.suggestedTypes`. Words are matched whole, so "amd" is not found in "Amdi". VISOR never adds an answer without a yes. The step counter counts this step only when it is shown.
- **Detail** starts at the level suggested for the user and labels it *(recommended for you)*. Skipping keeps that level.
- **You're all set** lists what VISOR does differently, shown and spoken. If contrast or light sensitivity was chosen, it offers the dark, high-contrast look, which is on unless the user turns it off. Saying "no" turns it off and asks again; "no, start" turns it off and finishes.

## Privacy

The description is health information. While *Share my description with VISOR's AI* is on (the default, said aloud during setup), it is sent with each scene description and question, trimmed to one line of at most 300 characters (`VisionPlan.OwnWordsMaxLength`). The server does not store it. When the switch is off, the description stays on the phone and VISOR still tailors itself from the user's answers. The rest of the profile never leaves the phone, as before (see `data/ProfileStore`).

## Testing it

- **Unit tests** (`./gradlew testDebugUnitTest`): `VisionPlanTest` (every rule, including two example users who get a different VISOR), `ScenePromptsTest` (untailored prompts are unchanged, and the guidance comes before "read aloud"), `SpokenPhrasesTest` (sides first, ahead first, the Brief limit), and `GlassesNavScreenTest` (the order on the glasses).
- **On a phone or the emulator:** debug menu → *Onboarding process*. Try two users side by side:
  - **Center loss, "A lot", "I can't read my mail".** Read text should be the first button, Detail should be recommended, and the hazard check should start with what is straight ahead.
  - **Side loss and contrast / light sensitivity, "I have glaucoma; sun washes everything out".** Check for hazards should be the first button, the dark, high-contrast look should be offered, and the hazard check should start with the sides.
- **The prompts against the real models:** the added sentences make prompts longer, and smaller models such as Moondream follow long prompts less well. Before changing `sceneGuidance`, run the [VLM tests](../testing/vlm-tests.md) on the server with a tailored prompt (`backend/scripts/try_vlm.py`).
