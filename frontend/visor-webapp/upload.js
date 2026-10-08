/**
 * VISOR IMU Lab — durable session queue and upload.
 *
 * Completed trials are written to IndexedDB before any network call is
 * attempted, and only removed once the server confirms the write. A Wi-Fi
 * dropout mid-session therefore costs a retry, never the trial data — which
 * matters because several trials in the battery take five to ten minutes to
 * re-run.
 */

(function(global) {
  'use strict';

  var DB_NAME = 'visor-imu-lab';
  var DB_VERSION = 1;
  var STORE = 'queue';
  var ENDPOINT = '/api/ingest';

  function openDb() {
    return new Promise(function(resolve, reject) {
      var req = indexedDB.open(DB_NAME, DB_VERSION);
      req.onupgradeneeded = function() {
        var db = req.result;
        if (!db.objectStoreNames.contains(STORE)) {
          db.createObjectStore(STORE, { keyPath: 'id' });
        }
      };
      req.onsuccess = function() { resolve(req.result); };
      req.onerror = function() { reject(req.error); };
    });
  }

  function tx(db, mode, fn) {
    return new Promise(function(resolve, reject) {
      var t = db.transaction(STORE, mode);
      var store = t.objectStore(STORE);
      var out = fn(store);
      t.oncomplete = function() { resolve(out && out.result !== undefined ? out.result : out); };
      t.onerror = function() { reject(t.error); };
      t.onabort = function() { reject(t.error); };
    });
  }

  function enqueue(session) {
    var id = session.meta.sessionId;
    var record = {
      id: id,
      trialId: session.meta.trialId || 'unknown',
      participant: session.meta.participant || 'anon',
      createdAt: Date.now(),
      attempts: 0,
      lastError: null,
      json: JSON.stringify(session),
    };
    return openDb().then(function(db) {
      return tx(db, 'readwrite', function(store) { store.put(record); })
        .then(function() { db.close(); return record; });
    });
  }

  function listQueue() {
    return openDb().then(function(db) {
      return new Promise(function(resolve, reject) {
        var t = db.transaction(STORE, 'readonly');
        var req = t.objectStore(STORE).getAll();
        req.onsuccess = function() {
          db.close();
          // Strip payloads — callers only need the manifest for display.
          resolve(req.result.map(function(r) {
            return {
              id: r.id, trialId: r.trialId, participant: r.participant,
              createdAt: r.createdAt, attempts: r.attempts,
              lastError: r.lastError, bytes: r.json.length,
            };
          }));
        };
        req.onerror = function() { db.close(); reject(req.error); };
      });
    });
  }

  function removeFromQueue(id) {
    return openDb().then(function(db) {
      return tx(db, 'readwrite', function(store) { store.delete(id); })
        .then(function() { db.close(); });
    });
  }

  function updateRecord(id, patch) {
    return openDb().then(function(db) {
      return new Promise(function(resolve, reject) {
        var t = db.transaction(STORE, 'readwrite');
        var store = t.objectStore(STORE);
        var req = store.get(id);
        req.onsuccess = function() {
          var rec = req.result;
          if (rec) {
            Object.keys(patch).forEach(function(k) { rec[k] = patch[k]; });
            store.put(rec);
          }
        };
        t.oncomplete = function() { db.close(); resolve(); };
        t.onerror = function() { db.close(); reject(t.error); };
      });
    });
  }

  function gzipSupported() {
    return typeof global.CompressionStream === 'function';
  }

  function gzip(text) {
    var stream = new Blob([text]).stream().pipeThrough(new CompressionStream('gzip'));
    return new Response(stream).arrayBuffer();
  }

  function postRecord(record, ingestKey) {
    var prepare = gzipSupported()
      ? gzip(record.json).then(function(buf) { return { body: buf, gzipped: true }; })
      : Promise.resolve({ body: record.json, gzipped: false });

    return prepare.then(function(prepared) {
      var headers = {
        'Content-Type': 'application/octet-stream',
        'X-Session-Id': record.id,
        'X-Trial-Id': record.trialId,
        'X-Participant': record.participant,
        'X-Body-Encoding': prepared.gzipped ? 'gzip' : 'identity',
      };
      if (ingestKey) headers['X-Ingest-Key'] = ingestKey;

      return fetch(ENDPOINT, { method: 'POST', headers: headers, body: prepared.body })
        .then(function(res) {
          return res.json().catch(function() { return {}; }).then(function(data) {
            if (!res.ok) {
              throw new Error('HTTP ' + res.status + ': ' + (data.error || res.statusText));
            }
            return data;
          });
        });
    });
  }

  /**
   * Attempts every queued record once. Resolves with a per-record outcome list
   * rather than rejecting, so one bad record cannot stall the rest of the queue.
   */
  function flush(ingestKey, onProgress) {
    return openDb().then(function(db) {
      return new Promise(function(resolve, reject) {
        var t = db.transaction(STORE, 'readonly');
        var req = t.objectStore(STORE).getAll();
        req.onsuccess = function() { db.close(); resolve(req.result); };
        req.onerror = function() { db.close(); reject(req.error); };
      });
    }).then(function(records) {
      var results = [];
      var chain = Promise.resolve();
      records.forEach(function(record) {
        chain = chain.then(function() {
          if (onProgress) onProgress(record.id, 'uploading');
          return postRecord(record, ingestKey)
            .then(function(data) {
              return removeFromQueue(record.id).then(function() {
                results.push({ id: record.id, ok: true, pathname: data.pathname, bytes: data.bytes });
                if (onProgress) onProgress(record.id, 'done');
              });
            })
            .catch(function(err) {
              return updateRecord(record.id, {
                attempts: record.attempts + 1,
                lastError: String(err.message || err),
              }).then(function() {
                results.push({ id: record.id, ok: false, error: String(err.message || err) });
                if (onProgress) onProgress(record.id, 'failed');
              });
            });
        });
      });
      return chain.then(function() { return results; });
    });
  }

  global.VisorUpload = {
    enqueue: enqueue,
    listQueue: listQueue,
    flush: flush,
    removeFromQueue: removeFromQueue,
    gzipSupported: gzipSupported,
    ENDPOINT: ENDPOINT,
  };
})(window);
