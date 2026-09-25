/**
 * VISOR IMU Lab — capture engine
 *
 * Records inertial data from Meta Ray-Ban Display glasses at the native event
 * rate for offline characterization.
 *
 * Measurement-integrity rules this file exists to enforce:
 *   1. Unavailable readings are stored as NaN and serialized as null. They are
 *      never coerced to 0, because "sensor absent" and "sensor reading zero"
 *      are different findings and conflating them invalidates the analysis.
 *   2. Each stream keeps its own timing and counters. Streams are never
 *      throttled against a shared budget, which would let a fast stream starve
 *      a slow one and silently bias the measured sample rate.
 *   3. Every sample carries both the event's own timestamp and a
 *      performance.now() arrival stamp, so event-clock behaviour can itself be
 *      characterized rather than assumed.
 */

(function(global) {
  'use strict';

  var CHUNK = 4096;

  // Growable column of doubles. NaN is the null sentinel.
  function Column() {
    this.chunks = [new Float64Array(CHUNK)];
    this.n = 0;
  }
  Column.prototype.push = function(v) {
    var ci = (this.n / CHUNK) | 0;
    if (ci >= this.chunks.length) this.chunks.push(new Float64Array(CHUNK));
    this.chunks[ci][this.n % CHUNK] = (v === null || v === undefined) ? NaN : v;
    this.n++;
  };
  Column.prototype.toArray = function(precision) {
    var out = new Array(this.n);
    var p = precision === undefined ? 6 : precision;
    for (var i = 0; i < this.n; i++) {
      var v = this.chunks[(i / CHUNK) | 0][i % CHUNK];
      out[i] = Number.isNaN(v) ? null : Number(v.toFixed(p));
    }
    return out;
  };

  function Table(names) {
    this.names = names;
    this.cols = {};
    for (var i = 0; i < names.length; i++) this.cols[names[i]] = new Column();
    this.nullCounts = {};
    for (var j = 0; j < names.length; j++) this.nullCounts[names[j]] = 0;
  }
  Table.prototype.pushRow = function(row) {
    for (var i = 0; i < this.names.length; i++) {
      var k = this.names[i];
      var v = row[k];
      if (v === null || v === undefined || (typeof v === 'number' && Number.isNaN(v))) {
        this.nullCounts[k]++;
      }
      this.cols[k].push(typeof v === 'boolean' ? (v ? 1 : 0) : v);
    }
  };
  Table.prototype.count = function() {
    return this.cols[this.names[0]].n;
  };
  Table.prototype.serialize = function(precision) {
    var out = { n: this.count(), nullCounts: this.nullCounts, columns: {} };
    for (var i = 0; i < this.names.length; i++) {
      out.columns[this.names[i]] = this.cols[this.names[i]].toArray(precision);
    }
    return out;
  };

  // ==================== CAPABILITY PROBE ====================
  // Answers, with evidence rather than inference: which inertial APIs exist on
  // this device, which can be constructed, and which actually emit readings.

  var GENERIC_SENSORS = [
    'Accelerometer',
    'LinearAccelerationSensor',
    'GravitySensor',
    'Gyroscope',
    'Magnetometer',
    'AbsoluteOrientationSensor',
    'RelativeOrientationSensor',
  ];

  function probePermissions() {
    var names = ['accelerometer', 'gyroscope', 'magnetometer'];
    if (!global.navigator || !navigator.permissions || !navigator.permissions.query) {
      return Promise.resolve({ supported: false });
    }
    return Promise.all(names.map(function(n) {
      return navigator.permissions.query({ name: n })
        .then(function(s) { return [n, s.state]; })
        .catch(function(e) { return [n, 'query-failed: ' + (e && e.name)]; });
    })).then(function(pairs) {
      var out = { supported: true };
      pairs.forEach(function(p) { out[p[0]] = p[1]; });
      return out;
    });
  }

  function probeGenericSensor(name, windowMs) {
    return new Promise(function(resolve) {
      var result = {
        name: name,
        constructorPresent: typeof global[name] === 'function',
        constructed: false,
        started: false,
        readings: 0,
        firstReading: null,
        error: null,
      };
      if (!result.constructorPresent) return resolve(result);

      var sensor;
      try {
        sensor = new global[name]({ frequency: 60 });
        result.constructed = true;
      } catch (e) {
        result.error = e.name + ': ' + e.message;
        return resolve(result);
      }

      var done = false;
      function finish() {
        if (done) return;
        done = true;
        try { sensor.stop(); } catch (e) { /* already stopped */ }
        resolve(result);
      }

      sensor.addEventListener('reading', function() {
        result.readings++;
        if (result.firstReading === null) {
          result.firstReading = ('quaternion' in sensor)
            ? { quaternion: sensor.quaternion ? Array.prototype.slice.call(sensor.quaternion) : null }
            : { x: sensor.x, y: sensor.y, z: sensor.z };
        }
      });
      sensor.addEventListener('error', function(e) {
        result.error = e.error ? (e.error.name + ': ' + e.error.message) : 'unknown sensor error';
        finish();
      });

      try {
        sensor.start();
        result.started = true;
      } catch (e) {
        result.error = e.name + ': ' + e.message;
        return resolve(result);
      }
      setTimeout(finish, windowMs);
    });
  }

  function probeWindowEvent(eventName, windowMs, extract) {
    return new Promise(function(resolve) {
      var result = { event: eventName, fired: 0, firstSample: null, allNullFields: null };
      var nullTally = null;

      function onEvent(ev) {
        result.fired++;
        var s = extract(ev);
        if (result.firstSample === null) {
          result.firstSample = s;
          nullTally = {};
          Object.keys(s).forEach(function(k) { nullTally[k] = 0; });
        }
        Object.keys(nullTally).forEach(function(k) {
          if (s[k] === null || s[k] === undefined) nullTally[k]++;
        });
      }

      global.addEventListener(eventName, onEvent);
      setTimeout(function() {
        global.removeEventListener(eventName, onEvent);
        if (nullTally && result.fired > 0) {
          result.allNullFields = Object.keys(nullTally).filter(function(k) {
            return nullTally[k] === result.fired;
          });
        }
        resolve(result);
      }, windowMs);
    });
  }

  function motionSample(ev) {
    var a = ev.acceleration || {};
    var g = ev.accelerationIncludingGravity || {};
    var r = ev.rotationRate || {};
    return {
      ax: a.x === undefined ? null : a.x,
      ay: a.y === undefined ? null : a.y,
      az: a.z === undefined ? null : a.z,
      agx: g.x === undefined ? null : g.x,
      agy: g.y === undefined ? null : g.y,
      agz: g.z === undefined ? null : g.z,
      rrAlpha: r.alpha === undefined ? null : r.alpha,
      rrBeta: r.beta === undefined ? null : r.beta,
      rrGamma: r.gamma === undefined ? null : r.gamma,
      interval: ev.interval === undefined ? null : ev.interval,
    };
  }

  function orientationSample(ev) {
    return {
      alpha: ev.alpha === undefined ? null : ev.alpha,
      beta: ev.beta === undefined ? null : ev.beta,
      gamma: ev.gamma === undefined ? null : ev.gamma,
      absolute: ev.absolute === undefined ? null : (ev.absolute ? 1 : 0),
    };
  }

  function runProbe(windowMs) {
    var w = windowMs || 2500;
    var tasks = [
      probePermissions().then(function(r) { return ['permissions', r]; }),
      probeWindowEvent('devicemotion', w, motionSample).then(function(r) { return ['devicemotion', r]; }),
      probeWindowEvent('deviceorientation', w, orientationSample).then(function(r) { return ['deviceorientation', r]; }),
      probeWindowEvent('deviceorientationabsolute', w, orientationSample).then(function(r) { return ['deviceorientationabsolute', r]; }),
    ];
    GENERIC_SENSORS.forEach(function(n) {
      tasks.push(probeGenericSensor(n, w).then(function(r) { return ['sensor:' + n, r]; }));
    });

    return Promise.all(tasks).then(function(pairs) {
      var out = {
        probedAt: new Date().toISOString(),
        windowMs: w,
        secureContext: global.isSecureContext === true,
        results: {},
      };
      pairs.forEach(function(p) { out.results[p[0]] = p[1]; });
      out.summary = summarizeProbe(out.results, w);
      return out;
    });
  }

  function summarizeProbe(r, windowMs) {
    var secs = windowMs / 1000;
    var motion = r.devicemotion || {};
    var orient = r.deviceorientation || {};
    var orientAbs = r.deviceorientationabsolute || {};
    return {
      devicemotionHz: motion.fired ? +(motion.fired / secs).toFixed(1) : 0,
      deviceorientationHz: orient.fired ? +(orient.fired / secs).toFixed(1) : 0,
      deviceorientationabsoluteHz: orientAbs.fired ? +(orientAbs.fired / secs).toFixed(1) : 0,
      orientationVerdict: orientationVerdict(orient, orientAbs),
      genericSensorsWithReadings: GENERIC_SENSORS.filter(function(n) {
        var s = r['sensor:' + n];
        return s && s.readings > 0;
      }),
    };
  }

  // The specific question this instrument was built to settle.
  function orientationVerdict(orient, orientAbs) {
    if (!orient.fired && !orientAbs.fired) {
      return 'NO_EVENTS: neither deviceorientation nor deviceorientationabsolute fired';
    }
    if (orient.fired && orient.allNullFields && orient.allNullFields.length >= 3) {
      return 'NULL_FIELDS: deviceorientation fired but alpha/beta/gamma were null throughout ' +
        '(' + orient.allNullFields.join(',') + ')';
    }
    if (orient.fired) return 'DATA_PRESENT: deviceorientation delivered non-null values';
    return 'ABSOLUTE_ONLY: only deviceorientationabsolute fired';
  }

  // ==================== RECORDER ====================

  var MOTION_COLS = ['tPerf', 'tEvent', 'ax', 'ay', 'az', 'agx', 'agy', 'agz',
                     'rrAlpha', 'rrBeta', 'rrGamma', 'interval'];
  var ORIENT_COLS = ['tPerf', 'tEvent', 'alpha', 'beta', 'gamma', 'absolute'];

  function Recorder() {
    this.recording = false;
    this.motion = null;
    this.orientation = null;
    this.orientationAbsolute = null;
    this.marks = [];
    this.startedAt = null;
    this.t0Perf = null;
    this._handlers = null;
  }

  Recorder.prototype.start = function(meta) {
    if (this.recording) throw new Error('already recording');
    this.motion = new Table(MOTION_COLS);
    this.orientation = new Table(ORIENT_COLS);
    this.orientationAbsolute = new Table(ORIENT_COLS);
    this.marks = [];
    this.meta = meta || {};
    this.startedAt = new Date().toISOString();
    this.t0Perf = performance.now();
    this.t0Epoch = Date.now();

    var self = this;
    function onMotion(ev) {
      var s = motionSample(ev);
      s.tPerf = performance.now() - self.t0Perf;
      s.tEvent = typeof ev.timeStamp === 'number' ? ev.timeStamp : null;
      self.motion.pushRow(s);
    }
    function onOrient(ev) {
      var s = orientationSample(ev);
      s.tPerf = performance.now() - self.t0Perf;
      s.tEvent = typeof ev.timeStamp === 'number' ? ev.timeStamp : null;
      self.orientation.pushRow(s);
    }
    function onOrientAbs(ev) {
      var s = orientationSample(ev);
      s.tPerf = performance.now() - self.t0Perf;
      s.tEvent = typeof ev.timeStamp === 'number' ? ev.timeStamp : null;
      self.orientationAbsolute.pushRow(s);
    }

    this._handlers = { onMotion: onMotion, onOrient: onOrient, onOrientAbs: onOrientAbs };
    global.addEventListener('devicemotion', onMotion);
    global.addEventListener('deviceorientation', onOrient);
    global.addEventListener('deviceorientationabsolute', onOrientAbs);
    this.recording = true;
  };

  Recorder.prototype.mark = function(label, extra) {
    this.marks.push({
      t: this.recording ? performance.now() - this.t0Perf : null,
      label: label,
      extra: extra || null,
    });
  };

  Recorder.prototype.stop = function() {
    if (!this.recording) return;
    global.removeEventListener('devicemotion', this._handlers.onMotion);
    global.removeEventListener('deviceorientation', this._handlers.onOrient);
    global.removeEventListener('deviceorientationabsolute', this._handlers.onOrientAbs);
    this._handlers = null;
    this.recording = false;
    this.durationMs = performance.now() - this.t0Perf;
  };

  Recorder.prototype.liveCounts = function() {
    return {
      motion: this.motion ? this.motion.count() : 0,
      orientation: this.orientation ? this.orientation.count() : 0,
      orientationAbsolute: this.orientationAbsolute ? this.orientationAbsolute.count() : 0,
      elapsedMs: this.recording ? performance.now() - this.t0Perf : (this.durationMs || 0),
    };
  };

  Recorder.prototype.serialize = function() {
    return {
      schema: 'visor.imu.session/1',
      startedAt: this.startedAt,
      t0Epoch: this.t0Epoch,
      durationMs: this.durationMs || null,
      meta: this.meta,
      marks: this.marks,
      streams: {
        devicemotion: this.motion.serialize(),
        deviceorientation: this.orientation.serialize(),
        deviceorientationabsolute: this.orientationAbsolute.serialize(),
      },
    };
  };

  // ==================== ENVIRONMENT ====================

  function environment() {
    var n = global.navigator || {};
    var env = {
      userAgent: n.userAgent || null,
      platform: n.platform || null,
      language: n.language || null,
      hardwareConcurrency: n.hardwareConcurrency || null,
      deviceMemory: n.deviceMemory || null,
      maxTouchPoints: n.maxTouchPoints === undefined ? null : n.maxTouchPoints,
      screen: global.screen ? {
        width: screen.width, height: screen.height,
        availWidth: screen.availWidth, availHeight: screen.availHeight,
        pixelRatio: global.devicePixelRatio || null,
        orientation: (screen.orientation && screen.orientation.type) || null,
      } : null,
      viewport: { innerWidth: global.innerWidth, innerHeight: global.innerHeight },
      secureContext: global.isSecureContext === true,
      timezone: (Intl.DateTimeFormat().resolvedOptions() || {}).timeZone || null,
    };
    if (n.getBattery) {
      return n.getBattery().then(function(b) {
        env.battery = { level: b.level, charging: b.charging };
        return env;
      }).catch(function() { return env; });
    }
    return Promise.resolve(env);
  }

  global.VisorCapture = {
    Recorder: Recorder,
    runProbe: runProbe,
    environment: environment,
    GENERIC_SENSORS: GENERIC_SENSORS,
  };
})(window);
