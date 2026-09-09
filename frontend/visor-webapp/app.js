/**
 * VISOR IMU Web App — Meta Ray-Ban Display glasses
 * Live accelerometer / gyroscope / orientation readout.
 */

(function() {
  'use strict';

  var CONFIG = {
    appName: 'VISOR IMU',
    updateIntervalMs: 66, // ~15Hz UI refresh — well within the 10-30Hz budget
  };

  var state = {
    streaming: true,
    demoMode: false,
    lastRenderAt: 0,
    demoTimer: null,
    demoPhase: 0,
  };

  var els = {};

  function collectEls() {
    els.status = document.getElementById('status-indicator');
    els.permissionBtn = document.getElementById('permission-btn');
    els.toggleBtn = document.getElementById('toggle-btn');
    els.error = document.getElementById('error');
    els.accelX = document.getElementById('accel-x');
    els.accelY = document.getElementById('accel-y');
    els.accelZ = document.getElementById('accel-z');
    els.gyroAlpha = document.getElementById('gyro-alpha');
    els.gyroBeta = document.getElementById('gyro-beta');
    els.gyroGamma = document.getElementById('gyro-gamma');
    els.orientAlpha = document.getElementById('orient-alpha');
    els.orientBeta = document.getElementById('orient-beta');
    els.orientGamma = document.getElementById('orient-gamma');
  }

  // ==================== FOCUS (D-pad) ====================
  function focusFirst() {
    var el = document.querySelector('.focusable:not([disabled]):not(.hidden)');
    if (el) el.focus();
  }

  function moveFocus(direction) {
    var focusables = Array.from(document.querySelectorAll('.focusable:not([disabled]):not(.hidden)'));
    if (focusables.length === 0) return;
    var idx = focusables.indexOf(document.activeElement);
    if (idx === -1) { focusFirst(); return; }
    var nextIdx = (direction === 'up' || direction === 'left')
      ? (idx > 0 ? idx - 1 : focusables.length - 1)
      : (idx < focusables.length - 1 ? idx + 1 : 0);
    focusables[nextIdx].focus();
  }

  // ==================== STATUS / ERROR ====================
  function setStatus(text) {
    if (els.status) els.status.textContent = text;
  }

  function setError(message) {
    if (!els.error) return;
    els.error.classList.remove('hidden');
    var msgEl = els.error.querySelector('.error-message');
    if (msgEl) msgEl.textContent = message;
  }

  function clearError() {
    if (els.error) els.error.classList.add('hidden');
  }

  // ==================== RENDER ====================
  function render(data) {
    var now = performance.now();
    if (now - state.lastRenderAt < CONFIG.updateIntervalMs) return;
    state.lastRenderAt = now;

    if (data.accel) {
      els.accelX.textContent = data.accel.x.toFixed(2);
      els.accelY.textContent = data.accel.y.toFixed(2);
      els.accelZ.textContent = data.accel.z.toFixed(2);
    }
    if (data.gyro) {
      els.gyroAlpha.textContent = data.gyro.alpha.toFixed(2);
      els.gyroBeta.textContent = data.gyro.beta.toFixed(2);
      els.gyroGamma.textContent = data.gyro.gamma.toFixed(2);
    }
    if (data.orient) {
      els.orientAlpha.textContent = data.orient.alpha.toFixed(2);
      els.orientBeta.textContent = data.orient.beta.toFixed(2);
      els.orientGamma.textContent = data.orient.gamma.toFixed(2);
    }
  }

  // ==================== LIVE SENSORS ====================
  function onDeviceMotion(event) {
    if (!state.streaming) return;
    var acc = event.accelerationIncludingGravity || event.acceleration;
    var rot = event.rotationRate;
    render({
      accel: acc ? { x: acc.x || 0, y: acc.y || 0, z: acc.z || 0 } : null,
      gyro: rot ? { alpha: rot.alpha || 0, beta: rot.beta || 0, gamma: rot.gamma || 0 } : null,
    });
  }

  function onDeviceOrientation(event) {
    if (!state.streaming) return;
    render({
      orient: { alpha: event.alpha || 0, beta: event.beta || 0, gamma: event.gamma || 0 },
    });
  }

  function startLiveSensors() {
    window.addEventListener('devicemotion', onDeviceMotion);
    window.addEventListener('deviceorientation', onDeviceOrientation);
  }

  function stopLiveSensors() {
    window.removeEventListener('devicemotion', onDeviceMotion);
    window.removeEventListener('deviceorientation', onDeviceOrientation);
  }

  // ==================== DEMO MODE (no sensors available) ====================
  function startDemoMode() {
    state.demoMode = true;
    setStatus('Demo data');
    state.demoTimer = setInterval(function() {
      if (!state.streaming) return;
      state.demoPhase += 0.12;
      render({
        accel: {
          x: Math.sin(state.demoPhase) * 2,
          y: Math.cos(state.demoPhase * 0.7) * 2,
          z: 9.8 + Math.sin(state.demoPhase * 0.3) * 0.3,
        },
        gyro: {
          alpha: Math.sin(state.demoPhase * 1.3) * 40,
          beta: Math.cos(state.demoPhase * 0.9) * 40,
          gamma: Math.sin(state.demoPhase * 0.5) * 20,
        },
        orient: {
          alpha: (state.demoPhase * 20) % 360,
          beta: Math.sin(state.demoPhase * 0.6) * 45,
          gamma: Math.cos(state.demoPhase * 0.4) * 45,
        },
      });
    }, CONFIG.updateIntervalMs);
  }

  function stopDemoMode() {
    if (state.demoTimer) {
      clearInterval(state.demoTimer);
      state.demoTimer = null;
    }
  }

  // ==================== PERMISSION / INIT ====================
  function needsIOSPermission() {
    return typeof DeviceMotionEvent !== 'undefined' &&
      typeof DeviceMotionEvent.requestPermission === 'function';
  }

  function requestPermission() {
    if (!needsIOSPermission()) {
      // No explicit permission API (Android / desktop) — try live sensors directly.
      beginSensing();
      return;
    }

    Promise.all([
      DeviceMotionEvent.requestPermission(),
      (typeof DeviceOrientationEvent !== 'undefined' &&
        typeof DeviceOrientationEvent.requestPermission === 'function')
        ? DeviceOrientationEvent.requestPermission()
        : Promise.resolve('granted'),
    ]).then(function(results) {
      if (results.every(function(r) { return r === 'granted'; })) {
        beginSensing();
      } else {
        setError('Sensor permission denied. Showing demo data instead.');
        startDemoMode();
      }
    }).catch(function() {
      setError('Could not request sensor permission. Showing demo data instead.');
      startDemoMode();
    });
  }

  function beginSensing() {
    clearError();
    if (els.permissionBtn) els.permissionBtn.classList.add('hidden');

    var hasMotion = 'ondevicemotion' in window;
    var hasOrientation = 'ondeviceorientation' in window;

    if (!hasMotion && !hasOrientation) {
      setError('No motion sensors detected on this device. Showing demo data.');
      startDemoMode();
      return;
    }

    setStatus('Live');
    startLiveSensors();

    // If no real events arrive shortly, sensors likely exist in the API but not
    // in hardware (e.g. desktop browser) — fall back to demo data.
    setTimeout(function() {
      if (state.lastRenderAt === 0) {
        stopLiveSensors();
        setError('No live sensor data received. Showing demo data.');
        startDemoMode();
      }
    }, 2000);
  }

  // ==================== ACTIONS ====================
  function toggleStream() {
    state.streaming = !state.streaming;
    els.toggleBtn.textContent = state.streaming ? 'Pause' : 'Resume';
    setStatus(state.streaming ? (state.demoMode ? 'Demo data' : 'Live') : 'Paused');
  }

  function handleAction(action) {
    switch (action) {
      case 'request-permission':
        requestPermission();
        break;
      case 'toggle-stream':
        toggleStream();
        break;
    }
  }

  // ==================== LIFECYCLE ====================
  function handleVisibilityChange() {
    if (document.hidden) {
      stopLiveSensors();
      stopDemoMode();
    } else if (state.streaming) {
      if (state.demoMode) {
        startDemoMode();
      } else if (!needsIOSPermission()) {
        startLiveSensors();
      }
    }
  }

  function setupEvents() {
    document.addEventListener('click', function(e) {
      var actionEl = e.target.closest('[data-action]');
      if (actionEl) handleAction(actionEl.dataset.action);
    });

    document.addEventListener('keydown', function(e) {
      switch (e.key) {
        case 'ArrowUp':
        case 'ArrowLeft':
          moveFocus('left');
          e.preventDefault();
          break;
        case 'ArrowDown':
        case 'ArrowRight':
          moveFocus('right');
          e.preventDefault();
          break;
        case 'Enter':
          if (document.activeElement && document.activeElement.classList.contains('focusable')) {
            document.activeElement.click();
          }
          e.preventDefault();
          break;
      }
    });

    document.addEventListener('visibilitychange', handleVisibilityChange);
  }

  function init() {
    collectEls();
    setupEvents();
    focusFirst();

    // Android/desktop generally don't require an explicit permission prompt —
    // try live sensing immediately; iOS still needs the Enable Sensors tap.
    if (needsIOSPermission()) {
      setStatus('Tap Enable Sensors');
    } else {
      if (els.permissionBtn) els.permissionBtn.classList.add('hidden');
      beginSensing();
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
