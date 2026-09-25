/**
 * VISOR IMU Lab — test protocol definitions, audio cues, and trial runner.
 *
 * Each trial is a fixed, repeatable procedure with machine-readable cue marks
 * written into the recording. Cue marks are the ground truth the analysis
 * pipeline segments against: without them, a head-turn in the signal cannot be
 * distinguished from noise that happens to look like one.
 *
 * Two constraints shape the trial set:
 *
 *   1. Every trial begins with a preparation countdown. Recording starts only
 *      when the countdown ends, so the act of taking the glasses off and
 *      setting them down is not captured as if it were the measurement.
 *   2. A trial that shows on-screen cues must be performed while wearing the
 *      glasses, because the display is unreadable on a table. Trials that
 *      needed both an unworn device and visual cues have been removed rather
 *      than left in place to produce unusable data.
 *
 * Tiers:
 *   A  instrument characterization (static, not worn)
 *   B  dynamic response (worn)
 *   C  rehabilitation-relevant behaviour (worn)
 *   D  system constraints (worn)
 */

(function(global) {
  'use strict';

  // ==================== AUDIO ====================
  // Audio is not decoration here. During a static trial the glasses are face
  // down on a table, so sound is the only channel that can tell the tester
  // when recording started and when the trial finished.

  var audio = {
    ctx: null,
    available: false,

    /** Must be called from within a user gesture, or the context stays suspended. */
    ensure: function() {
      if (this.ctx) {
        if (this.ctx.state === 'suspended') this.ctx.resume();
        return this.available;
      }
      var Ctor = global.AudioContext || global.webkitAudioContext;
      if (!Ctor) return false;
      try {
        this.ctx = new Ctor();
        this.available = true;
      } catch (e) {
        this.available = false;
      }
      return this.available;
    },

    _tone: function(freq, startOffset, durationSec, peakGain) {
      if (!this.available || !this.ctx) return;
      var t0 = this.ctx.currentTime + startOffset;
      var osc = this.ctx.createOscillator();
      var gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, t0);
      // Short ramps rather than hard starts/stops; a square-edged envelope
      // clicks audibly on small speakers.
      gain.gain.setValueAtTime(0.0001, t0);
      gain.gain.exponentialRampToValueAtTime(peakGain, t0 + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.0001, t0 + durationSec);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(t0);
      osc.stop(t0 + durationSec + 0.02);
    },

    /** Countdown blip, one per second over the final seconds of preparation. */
    tick: function() { this._tone(880, 0, 0.08, 0.18); },

    /** Rising two-tone: recording has begun. */
    start: function() {
      this._tone(523.25, 0, 0.14, 0.25);
      this._tone(783.99, 0.15, 0.22, 0.25);
    },

    /** Falling three-tone, deliberately unlike the start chime. */
    finish: function() {
      this._tone(783.99, 0, 0.14, 0.28);
      this._tone(659.25, 0.16, 0.14, 0.28);
      this._tone(523.25, 0.32, 0.30, 0.28);
    },

    /** Low double tone for an aborted trial. */
    abort: function() {
      this._tone(392.00, 0, 0.16, 0.25);
      this._tone(311.13, 0.18, 0.28, 0.25);
    },

    /** Soft blip marking a cue change, audible without looking at the display. */
    cue: function() { this._tone(1046.5, 0, 0.06, 0.14); },
  };

  // ==================== TRIALS ====================

  var TRIALS = [
    // ---------- Tier A: instrument characterization (NOT worn) ----------
    {
      id: 'A1_static_rest',
      tier: 'A',
      title: 'Static rest',
      worn: false,
      prepSec: 20,
      durationSec: 120,
      purpose: 'Noise floor, accelerometer bias, gyroscope zero-rate offset, Allan deviation.',
      setup: 'Start the trial, then take the glasses off and lay them flat on a solid ' +
             'surface before the countdown ends. Do not touch them again until you hear ' +
             'the three falling tones.',
      instructions: ['Start, then set glasses down', 'Hands off until the end chime'],
      cue: null,
    },
    {
      id: 'A2_static_extended',
      tier: 'A',
      title: 'Static extended',
      worn: false,
      prepSec: 20,
      durationSec: 300,
      purpose: 'Bias instability and low-frequency drift at longer averaging times.',
      setup: 'As for Static rest, but five minutes. Start the trial, lay the glasses flat ' +
             'before the countdown ends, and leave them completely undisturbed. ' +
             'Do not lean on or bump the table.',
      instructions: ['Start, then set glasses down', '5 minutes undisturbed'],
      cue: null,
    },

    // ---------- Tier B: dynamic response (worn) ----------
    {
      id: 'B1_yaw_paced',
      tier: 'B',
      title: 'Yaw sweeps (paced)',
      worn: true,
      prepSec: 8,
      durationSec: 60,
      purpose: 'Yaw-axis response and repeatability at a controlled cadence.',
      setup: 'Wear the glasses and sit or stand comfortably. Turn your head left and ' +
             'right, following the on-screen cue. Use your full comfortable range.',
      instructions: ['Turn head to the cue', 'Full comfortable range'],
      cue: { type: 'alternate', intervalSec: 2, steps: ['LEFT', 'RIGHT'] },
    },
    {
      id: 'B2_pitch_paced',
      tier: 'B',
      title: 'Pitch sweeps (paced)',
      worn: true,
      prepSec: 8,
      durationSec: 60,
      purpose: 'Pitch-axis response; nod detection feasibility.',
      setup: 'Wear the glasses. Nod your head up and down, following the cue.',
      instructions: ['Nod to the cue'],
      cue: { type: 'alternate', intervalSec: 2, steps: ['UP', 'DOWN'] },
    },
    {
      id: 'B3_roll_paced',
      tier: 'B',
      title: 'Roll sweeps (paced)',
      worn: true,
      prepSec: 8,
      durationSec: 60,
      purpose: 'Roll-axis response; axis cross-coupling assessment.',
      setup: 'Wear the glasses. Tilt your head ear-toward-shoulder, following the cue.',
      instructions: ['Tilt head to the cue'],
      cue: { type: 'alternate', intervalSec: 2, steps: ['TILT LEFT', 'TILT RIGHT'] },
    },
    {
      id: 'B4_rapid_turns',
      tier: 'B',
      title: 'Rapid head turns',
      worn: true,
      prepSec: 8,
      durationSec: 30,
      purpose: 'Angular-rate range and saturation; aliasing at the reported sample rate.',
      setup: 'Wear the glasses and sit down. Turn your head left and right as fast as is ' +
             'comfortable and safe. Stop immediately if you feel dizzy.',
      instructions: ['Turn as FAST as comfortable', 'Sit down · stop if dizzy'],
      cue: { type: 'alternate', intervalSec: 1, steps: ['FAST LEFT', 'FAST RIGHT'] },
    },

    // ---------- Tier C: rehabilitation-relevant behaviour (worn) ----------
    {
      id: 'C1_stillness_hold',
      tier: 'C',
      title: 'Stillness hold',
      worn: true,
      prepSec: 8,
      durationSec: 60,
      purpose: 'Worn-but-still baseline. Sets the head-stability threshold VISOR would ' +
               'use to gate camera capture on the wearer having settled.',
      setup: 'Wear the glasses. Sit still and look steadily at one fixed point across ' +
             'the room. Breathe normally — do not try to freeze.',
      instructions: ['Sit still', 'Fix gaze on one point'],
      cue: null,
    },
    {
      id: 'C2_scanning_pattern',
      tier: 'C',
      title: 'Compensatory scanning',
      worn: true,
      prepSec: 8,
      durationSec: 90,
      purpose: 'Systematic horizontal scanning as taught in visual-field-loss training. ' +
               'Tests whether scan amplitude, rate and left/right symmetry are ' +
               'recoverable from the IMU alone.',
      setup: 'Wear the glasses. Sweep your gaze and head across the room in wide, ' +
             'deliberate left-and-right scans, following the cue.',
      instructions: ['Wide deliberate scans', 'Sweep the full field'],
      cue: { type: 'alternate', intervalSec: 3, steps: ['SCAN LEFT', 'SCAN RIGHT'] },
    },
    {
      id: 'C3_walk_straight',
      tier: 'C',
      title: 'Walking gait',
      worn: true,
      prepSec: 12,
      durationSec: 60,
      purpose: 'Step cadence and head-bob amplitude from a head-mounted IMU; ' +
               'ambulation detection for context-aware assistance.',
      setup: 'Wear the glasses. Walk at a natural, comfortable pace along a clear, ' +
             'level path. Have someone nearby if your balance is at all uncertain.',
      instructions: ['Walk naturally', 'Clear level path'],
      cue: null,
    },
    {
      id: 'C4_sit_stand',
      tier: 'C',
      title: 'Sit-to-stand cycles',
      worn: true,
      prepSec: 12,
      durationSec: 75,
      purpose: 'Postural-transition signature; mobility-event detection feasibility.',
      setup: 'Wear the glasses and sit in a stable chair with no wheels. Stand up and ' +
             'sit down following the cue. Use the armrests if you need them.',
      instructions: ['Stand / sit on cue', 'Stable chair, no wheels'],
      cue: { type: 'alternate', intervalSec: 5, steps: ['STAND UP', 'SIT DOWN'] },
    },
    {
      id: 'C5_reading_posture',
      tier: 'C',
      title: 'Reading posture',
      worn: true,
      prepSec: 8,
      durationSec: 60,
      purpose: 'Sustained near-task head pose. Separating reading from walking is the ' +
               'basis for context-appropriate assistance.',
      setup: 'Wear the glasses and read printed text held at a comfortable distance. ' +
             'Read normally — do not hold unnaturally still.',
      instructions: ['Read printed text', 'Natural posture'],
      cue: null,
    },
    {
      id: 'C6_free_living',
      tier: 'C',
      title: 'Unstructured baseline',
      worn: true,
      prepSec: 8,
      durationSec: 300,
      purpose: 'Realistic mixed activity for classifier training data and ' +
               'false-positive rate estimation.',
      setup: 'Wear the glasses and go about ordinary activity for five minutes: sit, ' +
             'stand, walk about, look around. Nothing scripted.',
      instructions: ['Ordinary activity', '5 minutes'],
      cue: null,
    },

    // ---------- Tier D: system constraints (worn) ----------
    {
      id: 'D1_endurance',
      tier: 'D',
      title: 'Endurance capture',
      worn: true,
      prepSec: 8,
      durationSec: 600,
      purpose: 'Sample-rate stability, dropout rate and battery drain over a ' +
               'sustained session.',
      setup: 'Wear the glasses for ten minutes of ordinary activity. Keep the app in ' +
             'the foreground. Note the battery percentage before and after.',
      instructions: ['Keep app in foreground', '10 minutes', 'Note battery before/after'],
      cue: null,
    },
  ];

  function byId(id) {
    for (var i = 0; i < TRIALS.length; i++) if (TRIALS[i].id === id) return TRIALS[i];
    return null;
  }

  function totalSeconds() {
    var total = 0;
    for (var i = 0; i < TRIALS.length; i++) {
      total += TRIALS[i].durationSec + (TRIALS[i].prepSec || 0);
    }
    return total;
  }

  // ==================== RUNNER ====================

  /**
   * Drives one trial through two phases:
   *
   *   prep   — countdown only. Nothing is recorded, so the tester can remove
   *            the glasses, get into position, or settle before measurement.
   *   record — the recorder runs and cue marks are written.
   *
   * Audio marks both transitions, because for an unworn trial the display is
   * not visible and the chimes are the only feedback available.
   */
  function Runner(recorder, callbacks) {
    this.recorder = recorder;
    this.cb = callbacks || {};
    this.trial = null;
    this.timers = [];
    this.running = false;
    this.phase = 'idle';
  }

  Runner.prototype._clearTimers = function() {
    for (var i = 0; i < this.timers.length; i++) {
      clearTimeout(this.timers[i]);
      clearInterval(this.timers[i]);
    }
    this.timers = [];
  };

  Runner.prototype.start = function(trialId, meta) {
    var trial = byId(trialId);
    if (!trial) throw new Error('unknown trial: ' + trialId);
    if (this.running) throw new Error('trial already running');

    this.trial = trial;
    this.meta = meta || {};
    this.running = true;

    // Called from the Start button's activation, which is the user gesture the
    // audio context needs in order to leave the suspended state.
    audio.ensure();

    var prep = trial.prepSec || 0;
    if (prep > 0) {
      this._runPrep(prep);
    } else {
      this._beginRecording();
    }
  };

  Runner.prototype._runPrep = function(prepSec) {
    var self = this;
    this.phase = 'prep';
    var remaining = prepSec;

    if (this.cb.onPrep) this.cb.onPrep(remaining, prepSec, this.trial);

    var timer = setInterval(function() {
      remaining -= 1;
      if (remaining > 0) {
        if (remaining <= 3) audio.tick();
        if (self.cb.onPrep) self.cb.onPrep(remaining, prepSec, self.trial);
      } else {
        clearInterval(timer);
        self._beginRecording();
      }
    }, 1000);
    this.timers.push(timer);
  };

  Runner.prototype._beginRecording = function() {
    var self = this;
    var trial = this.trial;
    this.phase = 'recording';

    var sessionMeta = Object.assign({}, this.meta, {
      trialId: trial.id,
      trialTitle: trial.title,
      tier: trial.tier,
      worn: trial.worn,
      prepSec: trial.prepSec || 0,
      plannedDurationSec: trial.durationSec,
      purpose: trial.purpose,
    });

    this.recorder.start(sessionMeta);
    this.recorder.mark('trial_start', { trialId: trial.id });
    audio.start();
    if (this.cb.onRecordingStart) this.cb.onRecordingStart(trial);

    var startPerf = performance.now();

    if (trial.cue) {
      var steps = trial.cue.steps;
      var intervalMs = trial.cue.intervalSec * 1000;
      var index = 0;
      var emit = function() {
        var label = steps[index % steps.length];
        self.recorder.mark('cue', { index: index, label: label });
        audio.cue();
        if (self.cb.onCue) self.cb.onCue(label, index);
        index++;
        if (trial.cue.type === 'sequence' && index >= steps.length) return;
        self.timers.push(setTimeout(emit, intervalMs));
      };
      emit();
    }

    this.timers.push(setInterval(function() {
      var elapsed = (performance.now() - startPerf) / 1000;
      if (self.cb.onTick) {
        self.cb.onTick(Math.min(elapsed, trial.durationSec), trial.durationSec,
                       self.recorder.liveCounts());
      }
    }, 250));

    this.timers.push(setTimeout(function() {
      self.finish('completed');
    }, trial.durationSec * 1000));
  };

  Runner.prototype.abort = function() {
    if (!this.running) return;
    // Aborting during preparation discards nothing: recording has not started.
    if (this.phase === 'prep') {
      this._clearTimers();
      this.running = false;
      this.phase = 'idle';
      audio.abort();
      if (this.cb.onPrepAbort) this.cb.onPrepAbort(this.trial);
      return;
    }
    this.finish('aborted');
  };

  Runner.prototype.finish = function(outcome) {
    if (!this.running || this.phase !== 'recording') return;
    this._clearTimers();
    this.recorder.mark('trial_end', { outcome: outcome });
    this.recorder.stop();
    this.running = false;
    this.phase = 'idle';

    if (outcome === 'completed') audio.finish();
    else audio.abort();

    var session = this.recorder.serialize();
    session.meta.outcome = outcome;
    if (this.cb.onFinish) this.cb.onFinish(session, this.trial);
  };

  global.VisorProtocol = {
    TRIALS: TRIALS,
    byId: byId,
    Runner: Runner,
    audio: audio,
    totalSeconds: totalSeconds,
    tiers: {
      A: 'Instrument characterization',
      B: 'Dynamic response',
      C: 'Rehabilitation behaviour',
      D: 'System constraints',
    },
  };
})(window);
