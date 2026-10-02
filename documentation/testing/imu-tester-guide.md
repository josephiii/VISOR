# VISOR IMU Lab — Guide for Test Runners and Testers

*Everything you need to run a session. No technical background assumed.*

---

## What this is, and why we're asking

VISOR (Visual Intelligence System for Ocular Rehabilitation) is an assistive
system being built for people with low vision and traumatic brain injury,
running on Meta Ray-Ban Display glasses.

The glasses contain an **IMU** — an inertial measurement unit, the same class of
sensor that tells your phone which way up it is. It reports how the wearer's
head is accelerating and rotating, about sixty times a second.

We need to know, with evidence rather than assumption, exactly how good that
sensor is. That answer decides which features VISOR can honestly promise. Three
concrete examples:

- **Camera capture timing.** If we can reliably detect that you have stopped
  moving your head, VISOR can take its picture at that moment instead of
  continuously. Sharper images, far less battery, fewer wrong answers.
- **Scanning rehabilitation.** People who have lost part of their visual field
  are taught to sweep their head to compensate. Whether that training is
  actually being done at home is currently self-reported. If the IMU can measure
  scanning reliably, it can be measured instead of guessed.
- **Knowing when to stay quiet.** A system that talks at length while you are
  walking is unsafe. If we can tell walking from sitting, VISOR can adapt.

The sessions you run produce the data behind that assessment, which supports a
federal grant proposal. Careful, honest data matters more than a lot of it.

---

## What is recorded — and what is not

**Recorded:** numbers describing motion, roughly sixty times a second.

| | |
|---|---|
| Acceleration | How fast the glasses speed up or slow down, on three axes |
| Rotation rate | How fast the head turns, on three axes |
| Orientation | Which way the head is pointing, including compass heading |
| Timing | Exactly when each reading arrived |
| Device info | Glasses software version, battery level, screen size |

**Not recorded. At all.**

- No camera images or video
- No microphone or audio
- No GPS or location
- No names, email addresses, or any other identifying information

A session is a list of numbers about movement. Someone reading the raw file
could tell that a head turned left, and when. They could not tell whose head it
was, where it was, or what it was looking at.

Testers are identified only by a short code such as `bench01`, chosen by the
test runner. **Please do not enter a name or initials in the Participant ID
field.**

> **A note on scope.** These sessions are engineering tests of a device,
> performed by team members and volunteers. They are not a clinical study. If
> the project later collects data from patients as research participants, that
> requires ethics board (IRB) approval obtained in advance, and a separate
> consent process. Nothing collected now is used for that purpose.

---

## What we do with it

After a session, the files are downloaded and run through an analysis pipeline.
In plain terms, it answers:

**Is the sensor stream reliable?** How many readings per second actually
arrive, how evenly spaced they are, and whether any are dropped. So far the
answer is good: about 60 readings per second, evenly spaced, with no drops
observed.

**How noisy is the sensor when nothing is moving?** This is what the
glasses-on-the-table trials measure. Every sensor reports small fluctuations
even at rest. That noise floor sets the smallest real movement we can detect —
and that number decides whether "the wearer has held still" can be detected at
all. It is the single most important measurement in the set, and it is the one
most easily ruined by handling the glasses during the trial.

**How much does it drift?** Rotation sensors slowly lose their reference. We
measure how quickly, using a standard technique called Allan deviation, which
tells us how long we can trust an accumulated measurement before it has to be
corrected.

**Can real behaviour be recovered?** From the worn trials: can we pick out
individual head turns, measure how far they went, count walking steps, and tell
a deliberate still hold from ordinary movement? Each of these maps to a specific
VISOR feature, and each gets a yes or no backed by numbers.

Results go into a report that is generated automatically, so the same data
always produces the same conclusions.

---

## Before you start

1. **Charge the glasses.** A full session is about 35 minutes of continuous
   sensor use. Note the battery percentage before and after.
2. **Find a suitable space.** You need a solid table that will not be bumped,
   a stable chair without wheels, and a clear level path a few metres long for
   the walking trial.
3. **Open the IMU Lab** on the glasses. If it is not installed, the test runner
   will provide the link or QR code.
4. **Set the Participant ID.** Go to **Settings**, enter the code the test
   runner gives you, and press **Save**. Do this once — it is remembered.
5. **Run the Capability Probe** once at the start of the day, from the home
   screen. It takes three seconds and records which sensors were available,
   which can change between glasses software versions.

---

## Running a session

Choose **Run Protocol** from the home screen. Trials are listed in the order
they should be run. Each one shows whether it is worn or done on a table.

For every trial the sequence is the same:

1. Select the trial. A summary screen shows what to do and how long it takes.
2. Press **Start**. A countdown begins. **Nothing is being recorded yet** —
   this is your time to get ready.
3. When the countdown ends you will hear **two rising tones**. Recording has
   started. Do the activity.
4. When you hear **three falling tones**, the trial is finished.
5. Press **Upload**, or carry on and upload several at once later.

### The table trials (A1, A2)

These two are different and they are the ones most often spoiled, so they are
worth reading twice.

- Press **Start while still wearing the glasses.**
- You have **20 seconds** to take them off and lay them flat on the table.
- Lay them down gently and **take your hands off**.
- Do not touch the glasses, lean on the table, or set anything down on it.
- Wait for the **three falling tones**. You will not be able to see the display,
  which is exactly why the sound is there.
- A2 runs for five minutes. Use the time for something else nearby, but stay in
  the room.

The whole point of these trials is to record the sensor with nothing happening.
A single bump makes the recording unusable — and the analysis will detect it and
reject the session, so a spoiled trial is wasted time rather than bad data
entering the results.

### The worn trials

Wear the glasses normally. Most show a cue in large text — `LEFT`, `RIGHT`,
`STAND UP` — with a soft blip each time it changes. Follow the cue at a
comfortable pace. Do not rush to match it perfectly; the app records exactly
when each cue appeared, so the analysis can measure your actual response.

Move naturally. Exaggerated or robotic movement produces data that does not
resemble how anyone really behaves.

---

## The sounds

| Sound | Meaning |
|---|---|
| Short blips | Final three seconds of the countdown |
| **Two rising tones** | Recording has started — begin the activity |
| Soft high blip | The cue just changed |
| **Three falling tones** | Trial finished — safe to pick the glasses up |
| Two low tones | Trial was aborted |

---

## Safety

Stop any trial at any time by pressing **Abort**. A stopped trial is never a
problem — it is recorded as incomplete and simply re-run.

- **Rapid head turns (B4):** sit down for this one. Stop immediately if you feel
  dizzy or light-headed. Do not push your range.
- **Walking (C3):** use a clear, level path. If your balance is at all
  uncertain, have someone walk with you.
- **Sit-to-stand (C4):** use a stable chair with no wheels. Use the armrests.
- Skip any trial that does not feel safe for you. Tell the test runner which one
  and why — that is useful information in itself, not a failure.

---

## If something goes wrong

| Problem | What to do |
|---|---|
| Bumped the glasses during a table trial | Abort and re-run it. Do not let it finish. |
| Missed the countdown | Abort and start again. Nothing was recorded. |
| Trial ended early on its own | Note it and re-run. Tell the test runner. |
| No sound | Check glasses volume. If still silent, watch the display instead and tell the test runner — the table trials are hard to run without audio. |
| Upload says failed | Nothing is lost. Sessions are stored on the glasses until upload succeeds. Check Wi-Fi and press **Upload All** on the Session Queue screen. |
| Not sure whether a trial was good | Say so. An honest "I think I bumped it" is far more valuable than a clean-looking file we cannot trust. |

The queue on the glasses holds completed trials until they upload successfully,
so a dropped Wi-Fi connection costs a retry, never the recording.

---

## For test runners

- One full battery per tester per session. Record the tester code, date, glasses
  software version, and battery before and after.
- Note anything unusual during the session — a bump, an interruption, a trial
  that felt wrong. Written notes beat guessing from the data later.
- After the session, pull and analyze:

  ```bash
  python analysis/fetch_sessions.py
  python analysis/analyze.py
  ```

  The generated report marks each session pass or fail against the quality
  criteria, with the specific reason for any rejection.
- Expect some rejections, especially early. The pipeline is deliberately strict:
  it would rather discard a questionable recording than let it influence a
  result that ends up in a grant proposal.

Full methodology is in [imu-test-protocol.md](./imu-test-protocol.md).
