/**
 * VISOR IMU Lab — UI controller.
 *
 * Screen navigation, D-pad focus, and the glue between the capture engine,
 * protocol runner and upload queue.
 */

(function() {
  'use strict';

  var STORAGE_KEY = 'visor_imu_lab_settings';

  var state = {
    screen: 'home',
    history: [],
    settings: { participant: 'anon', ingestKey: '' },
    recorder: new VisorCapture.Recorder(),
    runner: null,
    lastSession: null,
    readout: null,
    environment: null,
  };

  var screens = {};

  // ==================== UTIL ====================

  // Null must render as an explicit dash. Rendering it as 0.00 is what made the
  // original readout unable to distinguish "no sensor" from "reading zero".
  function fmt(v, digits) {
    if (v === null || v === undefined || (typeof v === 'number' && Number.isNaN(v))) return '—';
    return Number(v).toFixed(digits === undefined ? 2 : digits);
  }

  function el(id) { return document.getElementById(id); }

  function setText(id, text) {
    var node = el(id);
    if (node) node.textContent = text;
  }

  function sanitizeId(s, fallback) {
    var cleaned = String(s || '').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 64);
    return cleaned.length ? cleaned : fallback;
  }

  function loadSettings() {
    try {
      var raw = localStorage.getItem(STORAGE_KEY);
      if (raw) Object.assign(state.settings, JSON.parse(raw));
    } catch (e) { /* first run, or storage blocked */ }
  }

  function saveSettings() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state.settings)); }
    catch (e) { /* storage blocked; settings stay session-local */ }
  }

  // ==================== NAVIGATION ====================

  function collectScreens() {
    document.querySelectorAll('.screen').forEach(function(s) {
      screens[s.id.replace('screen-', '')] = s;
    });
  }

  function navigateTo(name, addToHistory) {
    if (!screens[name]) return;
    if (addToHistory !== false && state.screen && state.screen !== name) {
      state.history.push(state.screen);
    }
    Object.keys(screens).forEach(function(k) { screens[k].classList.add('hidden'); });
    screens[name].classList.remove('hidden');
    state.screen = name;
    onScreenEnter(name);
    focusFirst(screens[name]);
  }

  function navigateBack() {
    if (state.screen === 'run') return; // abort explicitly, never by back gesture
    if (state.history.length) navigateTo(state.history.pop(), false);
    else navigateTo('home', false);
  }

  function focusFirst(container) {
    var node = container.querySelector('.focusable:not([disabled]):not(.hidden)');
    if (node) node.focus();
  }

  function moveFocus(direction) {
    var container = screens[state.screen];
    if (!container) return;
    var items = Array.prototype.slice.call(
      container.querySelectorAll('.focusable:not([disabled]):not(.hidden)'));
    if (!items.length) return;
    var idx = items.indexOf(document.activeElement);
    if (idx === -1) { items[0].focus(); return; }
    var next = (direction === 'prev')
      ? (idx > 0 ? idx - 1 : items.length - 1)
      : (idx < items.length - 1 ? idx + 1 : 0);
    items[next].focus();
    items[next].scrollIntoView({ block: 'nearest' });
  }

  // ==================== LIVE READOUT ====================
  // Each stream carries its own render budget. A shared budget lets whichever
  // stream fires more often consume every slot and starve the other.

  function startReadout() {
    stopReadout();
    var r = {
      lastMotionRender: 0,
      lastOrientRender: 0,
      motionTicks: [],
      orientTicks: [],
      intervalMs: 66,
    };

    r.onMotion = function(ev) {
      var now = performance.now();
      r.motionTicks.push(now);
      if (now - r.lastMotionRender < r.intervalMs) return;
      r.lastMotionRender = now;
      var g = ev.accelerationIncludingGravity || {};
      var rr = ev.rotationRate || {};
      setText('accel-x', fmt(g.x)); setText('accel-y', fmt(g.y)); setText('accel-z', fmt(g.z));
      setText('gyro-alpha', fmt(rr.alpha)); setText('gyro-beta', fmt(rr.beta));
      setText('gyro-gamma', fmt(rr.gamma));
    };

    r.onOrient = function(ev) {
      var now = performance.now();
      r.orientTicks.push(now);
      if (now - r.lastOrientRender < r.intervalMs) return;
      r.lastOrientRender = now;
      setText('orient-alpha', fmt(ev.alpha)); setText('orient-beta', fmt(ev.beta));
      setText('orient-gamma', fmt(ev.gamma));
    };

    r.rateTimer = setInterval(function() {
      var cutoff = performance.now() - 1000;
      r.motionTicks = r.motionTicks.filter(function(t) { return t > cutoff; });
      r.orientTicks = r.orientTicks.filter(function(t) { return t > cutoff; });
      setText('rate-motion', r.motionTicks.length + ' Hz');
      setText('rate-orient', r.orientTicks.length + ' Hz');
      setText('readout-status', r.orientTicks.length ? 'both streams live' : 'motion only');
    }, 1000);

    window.addEventListener('devicemotion', r.onMotion);
    window.addEventListener('deviceorientation', r.onOrient);
    state.readout = r;
  }

  function stopReadout() {
    var r = state.readout;
    if (!r) return;
    window.removeEventListener('devicemotion', r.onMotion);
    window.removeEventListener('deviceorientation', r.onOrient);
    clearInterval(r.rateTimer);
    state.readout = null;
  }

  // ==================== CAPABILITY PROBE ====================

  function runProbe() {
    setText('probe-status', 'probing…');
    var container = el('probe-results');
    container.innerHTML = '<div class="loading-container"><div class="loading-spinner"></div>' +
      '<div class="loading-text">Testing sensor APIs…</div></div>';

    VisorCapture.runProbe(2500).then(function(probe) {
      state.lastProbe = probe;
      setText('probe-status', 'done');
      renderProbe(probe, container);
    }).catch(function(err) {
      setText('probe-status', 'error');
      container.innerHTML = '<div class="error-container"><div class="error-icon">⚠</div>' +
        '<div class="error-message">' + String(err.message || err) + '</div></div>';
    });
  }

  function renderProbe(probe, container) {
    var s = probe.summary;
    var html = '';

    html += '<div class="card"><div class="card-title">Verdict</div>' +
      '<div class="verdict">' + s.orientationVerdict + '</div></div>';

    html += '<div class="card"><div class="card-title">Event rates</div>' +
      '<div class="kv"><span>devicemotion</span><b>' + s.devicemotionHz + ' Hz</b></div>' +
      '<div class="kv"><span>deviceorientation</span><b>' + s.deviceorientationHz + ' Hz</b></div>' +
      '<div class="kv"><span>deviceorientationabsolute</span><b>' + s.deviceorientationabsoluteHz + ' Hz</b></div>' +
      '</div>';

    var motion = probe.results.devicemotion;
    if (motion && motion.allNullFields && motion.allNullFields.length) {
      html += '<div class="card"><div class="card-title">devicemotion null fields</div>' +
        '<div class="card-subtitle">' + motion.allNullFields.join(', ') + '</div></div>';
    }

    html += '<div class="card"><div class="card-title">Generic Sensor API</div>';
    VisorCapture.GENERIC_SENSORS.forEach(function(name) {
      var r = probe.results['sensor:' + name] || {};
      var verdict = r.readings > 0 ? (r.readings + ' readings')
        : (r.error ? r.error : (r.constructorPresent ? 'no readings' : 'not present'));
      var cls = r.readings > 0 ? 'ok' : 'bad';
      html += '<div class="kv"><span>' + name + '</span><b class="' + cls + '">' + verdict + '</b></div>';
    });
    html += '</div>';

    var perms = probe.results.permissions || {};
    if (perms.supported) {
      html += '<div class="card"><div class="card-title">Permissions</div>';
      ['accelerometer', 'gyroscope', 'magnetometer'].forEach(function(k) {
        html += '<div class="kv"><span>' + k + '</span><b>' + (perms[k] || '—') + '</b></div>';
      });
      html += '</div>';
    }

    container.innerHTML = html;
  }

  // ==================== TRIALS ====================

  function renderTrialList() {
    var container = el('trial-list');
    var tiers = VisorProtocol.tiers;
    var html = '';
    var lastTier = null;

    VisorProtocol.TRIALS.forEach(function(t) {
      if (t.tier !== lastTier) {
        html += '<div class="tier-head">' + t.tier + ' · ' + tiers[t.tier] + '</div>';
        lastTier = t.tier;
      }
      html += '<button class="list-item focusable" data-action="pick-trial" data-trial="' + t.id + '">' +
        '<span class="list-item-content">' +
        '<span class="list-item-title">' + t.title + '</span>' +
        '<span class="list-item-meta">' + formatDuration(t.durationSec) + '</span>' +
        '</span></button>';
    });
    container.innerHTML = html;
    setText('trials-status', VisorProtocol.TRIALS.length + ' trials');
  }

  function formatDuration(sec) {
    if (sec < 60) return sec + 's';
    var m = Math.floor(sec / 60), s = sec % 60;
    return s ? (m + 'm ' + s + 's') : (m + 'm');
  }

  function showBrief(trialId) {
    var t = VisorProtocol.byId(trialId);
    if (!t) return;
    state.pendingTrial = t;
    setText('brief-title', t.title);
    setText('brief-duration', formatDuration(t.durationSec));
    setText('brief-setup', t.setup);
    setText('brief-purpose', t.purpose);
    navigateTo('brief');
  }

  function startTrial() {
    var t = state.pendingTrial;
    if (!t) return;

    var sessionId = sanitizeId(t.id + '_' + Date.now(), 'sess' + Date.now());
    var meta = {
      sessionId: sessionId,
      participant: state.settings.participant || 'anon',
      // Consent status is carried from the start so the schema is IRB-ready
      // before any participant data is ever collected.
      consent: state.settings.participant === 'anon' ? 'bench-test-no-human-subject' : 'recorded-externally',
      environment: state.environment,
      probe: state.lastProbe ? state.lastProbe.summary : null,
      appVersion: 'imu-lab/1',
    };

    setText('run-trial', t.title);
    setText('run-cue', t.cue ? 'GET READY' : 'RECORDING');
    setText('run-clock', '0 / ' + t.durationSec + ' s');
    el('run-progress').style.width = '0%';
    navigateTo('run');

    state.runner = new VisorProtocol.Runner(state.recorder, {
      onCue: function(label) { setText('run-cue', label); },
      onTick: function(elapsed, total, counts) {
        setText('run-clock', Math.floor(elapsed) + ' / ' + total + ' s');
        el('run-progress').style.width = ((elapsed / total) * 100).toFixed(1) + '%';
        setText('run-counts', 'motion ' + counts.motion + ' · orient ' + counts.orientation);
      },
      onFinish: onTrialFinish,
    });

    try {
      state.runner.start(t.id, meta);
    } catch (err) {
      setText('run-cue', 'ERROR');
      setText('run-counts', String(err.message || err));
    }
  }

  function onTrialFinish(session, trial) {
    state.lastSession = session;
    setText('done-outcome', session.meta.outcome);

    var dm = session.streams.devicemotion;
    var doo = session.streams.deviceorientation;
    var durSec = (session.durationMs || 0) / 1000;

    var html = '';
    html += '<div class="card"><div class="card-title">' + trial.title + '</div>' +
      '<div class="kv"><span>duration</span><b>' + durSec.toFixed(1) + ' s</b></div>' +
      '<div class="kv"><span>devicemotion samples</span><b>' + dm.n + '</b></div>' +
      '<div class="kv"><span>effective rate</span><b>' +
        (durSec > 0 ? (dm.n / durSec).toFixed(1) : '0') + ' Hz</b></div>' +
      '<div class="kv"><span>orientation samples</span><b>' + doo.n + '</b></div>' +
      '<div class="kv"><span>cue marks</span><b>' + session.marks.length + '</b></div>' +
      '</div>';

    var nullNote = Object.keys(dm.nullCounts).filter(function(k) {
      return dm.nullCounts[k] === dm.n && dm.n > 0;
    });
    if (nullNote.length) {
      html += '<div class="card"><div class="card-title">All-null fields</div>' +
        '<div class="card-subtitle">' + nullNote.join(', ') + '</div></div>';
    }

    el('done-summary').innerHTML = html;

    VisorUpload.enqueue(session).then(function() {
      refreshQueueBadge();
      navigateTo('done', false);
    }).catch(function(err) {
      html += '<div class="card"><div class="card-title text-danger">Queue failed</div>' +
        '<div class="card-subtitle">' + String(err.message || err) + '</div></div>';
      el('done-summary').innerHTML = html;
      navigateTo('done', false);
    });
  }

  // ==================== QUEUE ====================

  function refreshQueueBadge() {
    return VisorUpload.listQueue().then(function(items) {
      setText('queue-badge', items.length
        ? items.length + ' session' + (items.length === 1 ? '' : 's') + ' pending'
        : 'No pending sessions');
      return items;
    }).catch(function() { return []; });
  }

  function renderQueue() {
    var container = el('queue-list');
    container.innerHTML = '<div class="loading-text">Loading…</div>';
    VisorUpload.listQueue().then(function(items) {
      if (!items.length) {
        container.innerHTML = '<div class="card"><div class="card-subtitle">' +
          'Queue is empty. Completed trials appear here until uploaded.</div></div>';
        setText('queue-status', 'empty');
        return;
      }
      setText('queue-status', items.length + ' pending');
      container.innerHTML = items.map(function(it) {
        return '<div class="card">' +
          '<div class="card-title">' + it.trialId + '</div>' +
          '<div class="kv"><span>size</span><b>' + (it.bytes / 1024).toFixed(0) + ' KB</b></div>' +
          '<div class="kv"><span>attempts</span><b>' + it.attempts + '</b></div>' +
          (it.lastError ? '<div class="card-subtitle text-danger">' + it.lastError + '</div>' : '') +
          '</div>';
      }).join('');
    }).catch(function(err) {
      // If storage is unavailable the operator must be told, not left looking
      // at a spinner while assuming recorded trials are safely queued.
      setText('queue-status', 'storage error');
      container.innerHTML = '<div class="error-container"><div class="error-icon">⚠</div>' +
        '<div class="error-message">Could not read the local session queue: ' +
        String((err && err.message) || err) +
        '. Recorded trials may not be recoverable on this device.</div></div>';
    });
  }

  function uploadNow() {
    setText('queue-status', 'uploading…');
    setText('done-outcome', 'uploading…');
    VisorUpload.flush(state.settings.ingestKey, function(id, status) {
      setText('queue-status', status + ' ' + id);
    }).then(function(results) {
      var ok = results.filter(function(r) { return r.ok; }).length;
      var failed = results.length - ok;
      var msg = ok + ' uploaded' + (failed ? ', ' + failed + ' failed' : '');
      setText('queue-status', msg);
      setText('done-outcome', msg);
      refreshQueueBadge();
      if (state.screen === 'queue') renderQueue();
    }).catch(function(err) {
      setText('queue-status', 'error: ' + String(err.message || err));
    });
  }

  // ==================== SCREEN HOOKS ====================

  function onScreenEnter(name) {
    if (name !== 'readout') stopReadout();

    switch (name) {
      case 'home': refreshQueueBadge(); break;
      case 'readout': startReadout(); break;
      case 'trials': renderTrialList(); break;
      case 'queue': renderQueue(); break;
      case 'settings':
        el('input-participant').value = state.settings.participant === 'anon' ? '' : state.settings.participant;
        el('input-key').value = state.settings.ingestKey || '';
        break;
    }
  }

  // ==================== ACTIONS ====================

  function handleAction(action, node) {
    switch (action) {
      case 'go-home': navigateTo('home'); break;
      case 'go-probe': navigateTo('probe'); break;
      case 'go-readout': navigateTo('readout'); break;
      case 'go-trials': navigateTo('trials'); break;
      case 'go-queue': navigateTo('queue'); break;
      case 'go-settings': navigateTo('settings'); break;
      case 'run-probe': runProbe(); break;
      case 'pick-trial': showBrief(node.dataset.trial); break;
      case 'start-trial': startTrial(); break;
      case 'abort-trial':
        if (state.runner) state.runner.abort();
        break;
      case 'upload-now': uploadNow(); break;
      case 'save-settings':
        state.settings.participant = sanitizeId(el('input-participant').value, 'anon');
        state.settings.ingestKey = el('input-key').value.trim();
        saveSettings();
        setText('settings-badge', 'Participant: ' + state.settings.participant);
        navigateTo('home');
        break;
    }
  }

  // ==================== EVENTS ====================

  function setupEvents() {
    document.addEventListener('click', function(e) {
      var node = e.target.closest('[data-action]');
      if (node) handleAction(node.dataset.action, node);
    });

    document.addEventListener('keydown', function(e) {
      var active = document.activeElement;
      var isInput = active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA');
      if (isInput && e.key !== 'Escape' && e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;

      switch (e.key) {
        case 'ArrowUp':
        case 'ArrowLeft':
          moveFocus('prev'); e.preventDefault(); break;
        case 'ArrowDown':
        case 'ArrowRight':
          moveFocus('next'); e.preventDefault(); break;
        case 'Enter':
          if (active && active.classList.contains('focusable') && active.tagName !== 'INPUT') {
            active.click();
          }
          e.preventDefault();
          break;
        case 'Escape':
          navigateBack(); e.preventDefault(); break;
      }
    });

    // A trial must not keep recording once the app is backgrounded: the sample
    // stream pauses there, which would silently corrupt the rate statistics.
    document.addEventListener('visibilitychange', function() {
      if (document.hidden) {
        stopReadout();
        if (state.runner && state.runner.running) state.runner.abort();
      } else if (state.screen === 'readout') {
        startReadout();
      }
    });
  }

  // ==================== INIT ====================

  function init() {
    collectScreens();
    loadSettings();
    setupEvents();
    setText('settings-badge', 'Participant: ' + state.settings.participant);

    VisorCapture.environment().then(function(env) {
      state.environment = env;
      setText('home-status', VisorUpload.gzipSupported() ? 'ready · gzip' : 'ready');
    });

    refreshQueueBadge();
    navigateTo('home', false);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
