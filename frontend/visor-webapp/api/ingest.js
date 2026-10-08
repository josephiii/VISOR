/**
 * Session ingest endpoint.
 *
 * Accepts one recorded IMU trial as a gzipped JSON body and writes it to the
 * private Vercel Blob store. Bodies arrive pre-compressed from the glasses so a
 * ten-minute session stays well under the serverless request-body limit.
 */

const { put } = require('@vercel/blob');

const MAX_BYTES = 8 * 1024 * 1024;

// Header values become part of the blob pathname, so they are restricted to a
// conservative character set rather than trusted.
function sanitize(value, fallback) {
  if (typeof value !== 'string') return fallback;
  const cleaned = value.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 64);
  return cleaned.length ? cleaned : fallback;
}

function readRawBody(req) {
  if (Buffer.isBuffer(req.body)) return Promise.resolve(req.body);
  if (typeof req.body === 'string') return Promise.resolve(Buffer.from(req.body));

  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > MAX_BYTES) {
        const err = new Error('payload exceeds ' + MAX_BYTES + ' bytes');
        err.statusCode = 413;
        reject(err);
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.setHeader('Allow', 'POST');
    res.status(405).json({ error: 'method not allowed' });
    return;
  }

  // Optional shared-key deterrent against drive-by writes. Unset by default;
  // set INGEST_KEY in the project env to require it.
  const expectedKey = process.env.INGEST_KEY;
  if (expectedKey && req.headers['x-ingest-key'] !== expectedKey) {
    res.status(401).json({ error: 'invalid ingest key' });
    return;
  }

  let body;
  try {
    body = await readRawBody(req);
  } catch (err) {
    res.status(err.statusCode || 400).json({ error: err.message });
    return;
  }

  if (!body || body.length === 0) {
    res.status(400).json({ error: 'empty body' });
    return;
  }

  const participant = sanitize(req.headers['x-participant'], 'anon');
  const trialId = sanitize(req.headers['x-trial-id'], 'unknown');
  const sessionId = sanitize(req.headers['x-session-id'], 'sess' + Date.now());
  const gzipped = req.headers['x-body-encoding'] === 'gzip';
  const ext = gzipped ? '.json.gz' : '.json';

  const pathname = `sessions/${participant}/${trialId}/${sessionId}${ext}`;

  try {
    const blob = await put(pathname, body, {
      access: 'private',
      contentType: gzipped ? 'application/gzip' : 'application/json',
      addRandomSuffix: false,
      allowOverwrite: true,
    });
    res.status(200).json({
      ok: true,
      pathname: blob.pathname,
      bytes: body.length,
      gzipped: gzipped,
    });
  } catch (err) {
    res.status(500).json({ error: 'blob write failed', detail: err.message });
  }
};
