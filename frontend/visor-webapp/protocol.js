/**
 * VISOR IMU Lab — test protocol definitions and runner
 *
 * Each trial is a fixed, repeatable procedure with machine-readable cue marks
 * written into the recording. Cue marks are the ground truth the analysis
 * pipeline segments against: without them, a head-turn in the signal cannot be
 * distinguished from noise that happens to look like one.
 *
 * Tiers:
 *   A  instrument characterization (static)   — noise, bias, drift, scale
 *   B  dynamic response                        — bandwidth, range, saturation
 *   C  rehabilitation-relevant behaviour       — VISOR use-case viability
 *   D  system constraints                      — endurance, lifecycle
 */

(function(global) {
  'use strict';

  var TRIALS = [
    // ---------- Tier A: instrument characterization ----------
    {
      id: 'A1_static_rest',
      tier: 'A',
      title: 'Static rest',
      durationSec: 120,
      purpose: 'Noise floor, accelerometer bias, gyroscope zero-rate offset, Allan deviation.',
      setup: 'Place the glasses on a flat, solid surface. Do not wear them. Do not touch the table.',
      instructions: ['Set glasses down', 'Stay completely still', 'Do not touch'],
      cue: null,
    },
    {
      id: 'A2_static_extended',
      tier: 'A',
      title: 'Static extended',
      durationSec: 300,
      purpose: 'Bias instability and low-frequency drift over a longer window; Allan deviation at longer averaging times.',
      setup: 'Glasses flat on a solid surface, undisturbed for the full five minutes.',
      instructions: ['Set glasses down', 'Leave undisturbed', '5 minutes'],
      cue: null,
    },
    {
      id: 'A3_six_position',
      tier: 'A',
      title: 'Six-position static',
      durationSec: 150,
      purpose: 'Per-axis accelerometer bias and scale factor via the standard six-position method.',
      setup: 'You will be prompted to rest the glasses in six orientations, 20s each. Hold each steady.',
      instructions: ['Follow each position prompt', 'Hold steady 20s'],
      cue: {
        type: 'sequence',
        intervalSec: 25,
        steps: ['+Z up (flat, lenses up)', '-Z up (flat, lenses down)',
                '+X up (right temple up)', '-X up (left temple up)',
                '+Y up (nose up)', '-Y up (nose down)'],
      },
    },

    // ---------- Tier B: dynamic response ----------
    {
      id: 'B1_yaw_paced',
      tier: 'B',
      title: 'Yaw sweeps (paced)',
      durationSec: 60,
      purpose: 'Yaw-axis response and repeatability at a controlled cadence; head-turn amplitude recovery.',
      setup: 'Wear the glasses. Turn your head left and right, following the on-screen cue.',
      instructions: ['Wear glasses', 'Turn head to the cue', 'Full comfortable range'],
      cue: { type: 'alternate', intervalSec: 2, steps: ['LEFT', 'RIGHT'] },
    },
    {
      id: 'B2_pitch_paced',
      tier: 'B',
      title: 'Pitch sweeps (paced)',
      durationSec: 60,
      purpose: 'Pitch-axis response; nod detection feasibility.',
      setup: 'Wear the glasses. Nod up and down following the cue.',
      instructions: ['Wear glasses', 'Nod to the cue'],
      cue: { type: 'alternate', intervalSec: 2, steps: ['UP', 'DOWN'] },
    },
    {
      id: 'B3_roll_paced',
      tier: 'B',
      title: 'Roll sweeps (paced)',
      durationSec: 60,
      purpose: 'Roll-axis response; axis cross-coupling assessment.',
      setup: 'Wear the glasses. Tilt your head ear-to-shoulder following the cue.',
      instructions: ['Wear glasses', 'Tilt head to the cue'],
      cue: { type: 'alternate', intervalSec: 2, steps: ['TILT LEFT', 'TILT RIGHT'] },
    },
    {
      id: 'B4_rapid_turns',
      tier: 'B',
      title: 'Rapid head turns',
      durationSec: 30,
      purpose: 'Angular-rate range and saturation; aliasing at the reported sample rate.',
      setup: 'Wear the glasses. Turn your head left and right as fast as is comfortable and safe.',
      instructions: ['Wear glasses', 'Turn as FAST as comfortable', 'Stop if dizzy'],
      cue: { type: 'alternate', intervalSec: 1, steps: ['FAST LEFT', 'FAST RIGHT'] },
    },
    {
      id: 'B5_impulse_taps',
      tier: 'B',
      title: 'Impulse taps',
      durationSec: 45,
      purpose: 'Impulse response and event-timestamp fidelity; upper bound on effective bandwidth.',
      setup: 'Glasses on a flat surface. Tap the frame sharply once per cue.',
      instructions: ['Glasses on surface', 'Sharp single tap per cue'],
      cue: { type: 'alternate', intervalSec: 3, steps: ['TAP', '—'] },
    },

    // ---------- Tier C: rehabilitation-relevant behaviour ----------
    {
      id: 'C1_stillness_hold',
      tier: 'C',
      title: 'Stillness hold (worn)',
      durationSec: 60,
      purpose: 'Worn-but-still baseline. Sets the dwell threshold VISOR would use to gate ' +
               'expensive capture (OCR/VLM) on head stability rather than firing continuously.',
      setup: 'Wear the glasses. Sit still and look straight ahead at a fixed point.',
      instructions: ['Wear glasses', 'Sit still', 'Fix gaze on one point'],
      cue: null,
    },
    {
      id: 'C2_scanning_pattern',
      tier: 'C',
      title: 'Compensatory scanning',
      durationSec: 90,
      purpose: 'Systematic horizontal scanning as taught in hemianopia field-loss training. ' +
               'Tests whether scan amplitude, rate and left/right symmetry are recoverable from IMU alone.',
      setup: 'Wear the glasses. Scan left-to-right across the room in a deliberate, ' +
             'sweeping pattern as a field-loss compensation exercise.',
      instructions: ['Wear glasses', 'Deliberate wide scans', 'Sweep the full field'],
      cue: { type: 'alternate', intervalSec: 3, steps: ['SCAN LEFT', 'SCAN RIGHT'] },
    },
    {
      id: 'C3_walk_straight',
      tier: 'C',
      title: 'Walking gait',
      durationSec: 60,
      purpose: 'Step cadence and head-bob amplitude from a head-mounted IMU; ' +
               'ambulation detection for context-aware assistance.',
      setup: 'Wear the glasses. Walk at a natural pace in a safe, clear, straight path. ' +
             'Have a spotter if balance is a concern.',
      instructions: ['Wear glasses', 'Walk naturally', 'Clear path + spotter'],
      cue: null,
    },
    {
      id: 'C4_sit_stand',
      tier: 'C',
      title: 'Sit-to-stand cycles',
      durationSec: 75,
      purpose: 'Postural-transition signature; fall-risk and mobility-event detection feasibility.',
      setup: 'Wear the glasses. Stand up and sit down following the cue. Use a stable chair.',
      instructions: ['Wear glasses', 'Stand / sit on cue', 'Use a stable chair'],
      cue: { type: 'alternate', intervalSec: 5, steps: ['STAND UP', 'SIT DOWN'] },
    },
    {
      id: 'C5_reading_posture',
      tier: 'C',
      title: 'Reading posture',
      durationSec: 60,
      purpose: 'Sustained near-task head pose. Distinguishing reading from ambulation is the ' +
               'basis for context-appropriate assistance verbosity.',
      setup: 'Wear the glasses. Read printed text held at a comfortable distance.',
      instructions: ['Wear glasses', 'Read printed text', 'Natural posture'],
      cue: null,
    },
    {
      id: 'C6_free_living',
      tier: 'C',
      title: 'Unstructured baseline',
      durationSec: 300,
      purpose: 'Realistic mixed activity for classifier training data and false-positive rate estimation.',
      setup: 'Wear the glasses and go about ordinary activity: sit, stand, walk, look around.',
      instructions: ['Wear glasses', 'Ordinary activity', '5 minutes'],
      cue: null,
    },

    // ---------- Tier D: system constraints ----------
    {
      id: 'D1_endurance',
      tier: 'D',
      title: 'Endurance capture',
      durationSec: 600,
      purpose: 'Sample-rate stability, dropout rate and battery drain over a sustained session.',
      setup: 'Wear or place the glasses. Leave the app in the foreground for the full ten minutes.',
      instructions: ['Keep app in foreground', '10 minutes', 'Note battery before/after'],
      cue: null,
    },
  ];

  function byId(id) {
    for (var i = 0; i < TRIALS.length; i++) if (TRIALS[i].id === id) return TRIALS[i];
    return null;
  }

  /**
   * Drives one trial: countdown, cue scheduling, recorder marks, completion.
   * Cue changes are written into the recording as marks so the analysis can
   * align signal segments to commanded movements.
   */
  function Runner(recorder, callbacks) {
    this.recorder = recorder;
    this.cb = callbacks || {};
    this.trial = null;
    this.timers = [];
    this.running = false;
  }

  Runner.prototype._clearTimers = function() {
    this.timers.forEach(function(t) { clearTimeout(t); clearInterval(t); });
    this.timers = [];
  };

  Runner.prototype.start = function(trialId, meta) {
    var trial = byId(trialId);
    if (!trial) throw new Error('unknown trial: ' + trialId);
    if (this.running) throw new Error('trial already running');

    this.trial = trial;
    this.running = true;

    var sessionMeta = Object.assign({}, meta || {}, {
      trialId: trial.id,
      trialTitle: trial.title,
      tier: trial.tier,
      plannedDurationSec: trial.durationSec,
      purpose: trial.purpose,
    });

    this.recorder.start(sessionMeta);
    this.recorder.mark('trial_start', { trialId: trial.id });

    var self = this;
    var startPerf = performance.now();

    // Cue scheduling
    if (trial.cue) {
      var steps = trial.cue.steps;
      var intervalMs = trial.cue.intervalSec * 1000;
      var idx = 0;
      var emit = function() {
        var label = steps[idx % steps.length];
        self.recorder.mark('cue', { index: idx, label: label });
        if (self.cb.onCue) self.cb.onCue(label, idx);
        idx++;
        if (trial.cue.type === 'sequence' && idx >= steps.length) {
          // sequence cues run once through, then hold on the final step
          return;
        }
        self.timers.push(setTimeout(emit, intervalMs));
      };
      emit();
    }

    // Progress ticks
    this.timers.push(setInterval(function() {
      var elapsed = (performance.now() - startPerf) / 1000;
      if (self.cb.onTick) {
        self.cb.onTick(Math.min(elapsed, trial.durationSec), trial.durationSec,
                       self.recorder.liveCounts());
      }
    }, 250));

    // Completion
    this.timers.push(setTimeout(function() { self.finish('completed'); },
                                trial.durationSec * 1000));

    if (this.cb.onStart) this.cb.onStart(trial);
  };

  Runner.prototype.abort = function() {
    if (this.running) this.finish('aborted');
  };

  Runner.prototype.finish = function(outcome) {
    if (!this.running) return;
    this._clearTimers();
    this.recorder.mark('trial_end', { outcome: outcome });
    this.recorder.stop();
    this.running = false;
    var session = this.recorder.serialize();
    session.meta.outcome = outcome;
    if (this.cb.onFinish) this.cb.onFinish(session, this.trial);
  };

  global.VisorProtocol = {
    TRIALS: TRIALS,
    byId: byId,
    Runner: Runner,
    tiers: {
      A: 'Instrument characterization',
      B: 'Dynamic response',
      C: 'Rehabilitation behaviour',
      D: 'System constraints',
    },
  };
})(window);
