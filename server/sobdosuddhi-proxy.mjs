import http from 'node:http';
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PORT = process.env.NGINX_PORT
  ? Number(process.env.DEFAULT_APP_PORT || 3000)
  : Number(process.env.PORT || 3000);

const GEMINI_BASE_URL = 'https://generativelanguage.googleapis.com/v1beta/models';
const FALLBACK_PUBLIC_MODEL = 'gemini-2.5-flash';
const ALLOWED_MODELS = new Set([
  'gemini-2.5-flash',
  'gemini-2.5-flash-lite',
  'gemini-3.1-flash-lite',
  'gemini-4-hydrogen',
  'gemini-4-neon',
  'gemini-4-carbon',
  'gemini-4-argon',
]);

const SERVER_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(SERVER_DIR, '..');

const APK_CANDIDATES = [
  path.join(ROOT_DIR, 'app/build/outputs/apk/debug/app-debug.apk'),
  path.join(ROOT_DIR, '.build-outputs/app-debug.apk'),
  '/app/applet/app/build/outputs/apk/debug/app-debug.apk',
  '/app/applet/.build-outputs/app-debug.apk',
];

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, HEAD, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Accept',
};

let cachedApkMeta = null;

function getServerApiKey() {
  const raw = (
    process.env.GEMINI_API_KEY ||
    process.env.API_KEY ||
    process.env.GOOGLE_API_KEY ||
    ''
  ).trim();
  if (!raw || raw === 'MY_GEMINI_API_KEY') return '';
  return raw;
}

function findApkPath() {
  for (const candidate of APK_CANDIDATES) {
    try {
      if (fs.existsSync(candidate)) return candidate;
    } catch {
      // ignore inaccessible candidate paths
    }
  }
  return null;
}

function getApkMeta(apkPath) {
  const stat = fs.statSync(apkPath);
  if (
    cachedApkMeta &&
    cachedApkMeta.apkPath === apkPath &&
    cachedApkMeta.sizeBytes === stat.size &&
    cachedApkMeta.mtimeMs === stat.mtimeMs
  ) {
    return cachedApkMeta;
  }
  const hash = crypto.createHash('sha256');
  hash.update(fs.readFileSync(apkPath));
  cachedApkMeta = {
    apkPath,
    sizeBytes: stat.size,
    sizeMb: (stat.size / (1024 * 1024)).toFixed(2),
    mtimeMs: stat.mtimeMs,
    mtime: stat.mtime.toISOString(),
    sha256: hash.digest('hex'),
  };
  return cachedApkMeta;
}

function readJsonBody(req, maxBytes = 8 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(Object.assign(new Error('Payload too large'), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf8');
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(Object.assign(new Error('Invalid JSON payload'), { statusCode: 400 }));
      }
    });
    req.on('error', reject);
  });
}

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    ...CORS_HEADERS,
  });
  res.end(body);
}

async function callGeminiWithFallback(preferredModel, apiKey, upstreamPayload) {
  const modelsToTry = preferredModel === FALLBACK_PUBLIC_MODEL
    ? [FALLBACK_PUBLIC_MODEL]
    : [preferredModel, FALLBACK_PUBLIC_MODEL];

  let lastRes = null;
  let lastRawText = '';
  let usedModel = preferredModel;

  for (const candidateModel of modelsToTry) {
    const payloadToSend = candidateModel === FALLBACK_PUBLIC_MODEL && upstreamPayload.generationConfig?.thinkingConfig
      ? {
          ...upstreamPayload,
          generationConfig: {
            ...upstreamPayload.generationConfig,
            thinkingConfig: undefined,
          },
        }
      : upstreamPayload;

    const upstreamRes = await fetch(`${GEMINI_BASE_URL}/${candidateModel}:generateContent`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': apiKey,
        'User-Agent': 'sobdosuddhi-proxy/1.0',
      },
      body: JSON.stringify(payloadToSend),
    });

    const rawText = await upstreamRes.text();
    lastRes = upstreamRes;
    lastRawText = rawText;
    usedModel = candidateModel;

    if (upstreamRes.ok) {
      return { ok: true, status: upstreamRes.status, rawText, model: usedModel };
    }
    // Retry on fallback model only if the requested preview model is unavailable (404/400)
    if (upstreamRes.status !== 404 && upstreamRes.status !== 400) {
      break;
    }
  }

  return {
    ok: false,
    status: lastRes?.status || 502,
    rawText: lastRawText,
    model: usedModel,
  };
}

async function handleGeminiGenerate(req, res) {
  const apiKey = getServerApiKey();
  if (!apiKey) {
    return sendJson(res, 503, {
      error: 'SERVER_AI_NOT_CONFIGURED',
      message: 'সার্ভারে Gemini API কী নির্ধারিত নেই। অনুগ্রহ করে Cloud Run Environment Variable-এ GEMINI_API_KEY যুক্ত করুন।',
    });
  }

  let body;
  try {
    body = await readJsonBody(req);
  } catch (err) {
    return sendJson(res, err.statusCode || 400, { error: 'BAD_REQUEST', message: err.message });
  }

  const model = ALLOWED_MODELS.has(body.model) ? body.model : FALLBACK_PUBLIC_MODEL;
  const systemInstruction = String(body.systemInstruction || '');
  const userPrompt = String(body.userPrompt || '');
  const thinkingLevel = body.thinkingLevel ? String(body.thinkingLevel) : null;

  if (!userPrompt.trim()) {
    return sendJson(res, 400, { error: 'EMPTY_PROMPT', message: 'বিশ্লেষণের জন্য বাংলা পাঠ্য প্রয়োজন।' });
  }

  const generationConfig = { responseMimeType: 'application/json' };
  if (thinkingLevel) {
    generationConfig.thinkingConfig = { thinkingLevel };
  }

  const upstreamPayload = {
    system_instruction: { parts: [{ text: systemInstruction }] },
    contents: [{ role: 'user', parts: [{ text: userPrompt }] }],
    generationConfig,
  };

  try {
    const result = await callGeminiWithFallback(model, apiKey, upstreamPayload);
    if (!result.ok) {
      return sendJson(res, result.status, {
        error: 'UPSTREAM_GEMINI_ERROR',
        upstreamStatus: result.status,
        message: 'সার্ভার AI অনুরোধ প্রক্রিয়াকরণে ত্রুটি হয়েছে।',
      });
    }

    const parsed = JSON.parse(result.rawText);
    const parts = parsed?.candidates?.[0]?.content?.parts || [];
    const text = parts.map((p) => p.text || '').join('').trim();
    if (!text) {
      return sendJson(res, 502, {
        error: 'EMPTY_RESPONSE',
        message: 'AI সার্ভার থেকে খালি ফলাফল পাওয়া গেছে।',
      });
    }
    return sendJson(res, 200, { text, model: result.model });
  } catch {
    return sendJson(res, 502, {
      error: 'UPSTREAM_NETWORK_ERROR',
      message: 'AI সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি।',
    });
  }
}

async function handleGeminiOcr(req, res) {
  const apiKey = getServerApiKey();
  if (!apiKey) {
    return sendJson(res, 503, {
      error: 'SERVER_AI_NOT_CONFIGURED',
      message: 'সার্ভারে Gemini API কী নির্ধারিত নেই। অনুগ্রহ করে Cloud Run Environment Variable-এ GEMINI_API_KEY যুক্ত করুন।',
    });
  }

  let body;
  try {
    body = await readJsonBody(req, 12 * 1024 * 1024);
  } catch (err) {
    return sendJson(res, err.statusCode || 400, { error: 'BAD_REQUEST', message: err.message });
  }

  const imageBase64 = String(body.imageBase64 || '');
  const mimeType = String(body.mimeType || 'image/png');
  if (!imageBase64) {
    return sendJson(res, 400, { error: 'MISSING_IMAGE', message: 'OCR-এর জন্য পৃষ্ঠার ছবি প্রয়োজন।' });
  }

  const upstreamPayload = {
    system_instruction: {
      parts: [{
        text: 'আপনি একজন নির্ভুল বাংলা OCR বিশেষজ্ঞ। প্রদত্ত পৃষ্ঠার ছবি থেকে সমস্ত বাংলা ও ইংরেজি লেখা হুবহু ইউনিকোডে উদ্ধার করুন। অনুচ্ছেদ, শিরোনাম এবং কবিতার পঙ্ক্তির বিন্যাস অক্ষুণ্ণ রাখুন। কোনো অতিরিক্ত ব্যাখ্যা যুক্ত করবেন না।',
      }],
    },
    contents: [{
      role: 'user',
      parts: [
        { inline_data: { mime_type: mimeType, data: imageBase64 } },
        { text: 'এই পৃষ্ঠার সমস্ত বাংলা পাঠ্য হুবহু ইউনিকোডে উদ্ধার করুন।' },
      ],
    }],
  };

  try {
    const result = await callGeminiWithFallback(FALLBACK_PUBLIC_MODEL, apiKey, upstreamPayload);
    if (!result.ok) {
      return sendJson(res, result.status, {
        error: 'UPSTREAM_OCR_ERROR',
        upstreamStatus: result.status,
        message: 'স্ক্যানকৃত PDF OCR প্রক্রিয়াকরণে ত্রুটি হয়েছে।',
      });
    }

    const parsed = JSON.parse(result.rawText);
    const parts = parsed?.candidates?.[0]?.content?.parts || [];
    const text = parts.map((p) => p.text || '').join('').trim();
    return sendJson(res, 200, { text });
  } catch {
    return sendJson(res, 502, {
      error: 'UPSTREAM_NETWORK_ERROR',
      message: 'OCR সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি।',
    });
  }
}

function renderPortalHtml(apkMeta, aiConfigured) {
  const sizeInfo = apkMeta
    ? `${apkMeta.sizeMb} MB (${apkMeta.sizeBytes.toLocaleString('en-US')} bytes)`
    : null;
  const shaShort = apkMeta ? apkMeta.sha256.slice(0, 16) + '…' : null;
  const aiStatusText = aiConfigured
    ? 'সক্রিয় (অনলাইন Gemini AI প্রস্তুত)'
    : 'সার্ভার চালু (GEMINI_API_KEY যুক্ত করলে AI চালু হবে)';

  const apkSection = apkMeta
    ? `
    <div class="meta">
      <div><span class="k">প্যাকেজ আইডি</span><span class="v">com.sobdosuddhi.app</span></div>
      <div><span class="k">সংস্করণ</span><span class="v">1.0.0 (Debug Signed)</span></div>
      <div><span class="k">ফাইলের আকার</span><span class="v">${sizeInfo}</span></div>
      <div><span class="k">SHA-256</span><span class="v"><code>${shaShort}</code></span></div>
      <div><span class="k">অনলাইন AI সার্ভার</span><span class="v">${aiStatusText}</span></div>
    </div>
    <a class="btn" href="/download/Sobdosuddhi-1.0.0-debug.apk" download="Sobdosuddhi-1.0.0-debug.apk">
      Debug APK ডাউনলোড করুন (Sobdosuddhi-1.0.0-debug.apk)
    </a>`
    : `
    <div class="meta">
      <div><span class="k">সার্ভারের অবস্থা</span><span class="v">${aiStatusText}</span></div>
      <div><span class="k">সার্ভার URL</span><span class="v"><code id="server-url">লোড হচ্ছে...</code></span></div>
      <div><span class="k">API কী নিরাপত্তা</span><span class="v">শুধুমাত্র সার্ভার-সাইডে সংরক্ষিত</span></div>
    </div>
    <button class="btn" type="button" onclick="copyServerUrl()">
      অ্যাপের সেটিংসে বসানোর জন্য এই সার্ভার URL কপি করুন
    </button>
    <p id="copy-msg" class="copy-msg"></p>`;

  return `<!DOCTYPE html>
<html lang="bn">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>শব্দশুদ্ধি ১.০ — অনলাইন AI সার্ভার ও Android APK</title>
  <style>
    :root { color-scheme: light; }
    body {
      margin: 0; font-family: system-ui, -apple-system, 'Noto Sans Bengali', sans-serif;
      background: #FAF8F3; color: #1B2420; min-height: 100vh;
      display: flex; align-items: center; justify-content: center; padding: 24px;
    }
    .card {
      max-width: 560px; width: 100%; background: #FFFFFF;
      border: 1px solid #E5DFD2; border-radius: 20px; padding: 32px;
      box-shadow: 0 10px 30px rgba(27, 67, 50, 0.06);
    }
    .badge {
      display: inline-block; font-size: 12px; font-weight: 600;
      color: #1B4332; background: #E9F3EE; padding: 5px 12px; border-radius: 999px;
      margin-bottom: 12px;
    }
    h1 { margin: 0 0 8px; font-size: 28px; color: #1B4332; }
    p.sub { margin: 0 0 24px; color: #526058; line-height: 1.6; font-size: 15px; }
    .meta {
      background: #F6F3EB; border: 1px solid #EAE3D5; border-radius: 14px;
      padding: 16px; margin-bottom: 24px; font-size: 14px; line-height: 1.8;
    }
    .meta div { display: flex; justify-content: space-between; gap: 12px; }
    .meta span.k { color: #5B665F; }
    .meta span.v { font-weight: 600; color: #1B2420; text-align: right; word-break: break-all; }
    a.btn, button.btn {
      display: block; width: 100%; box-sizing: border-box; border: 0; cursor: pointer;
      text-align: center; text-decoration: none;
      background: #1B4332; color: #FFFFFF; font-weight: 600; font-size: 16px;
      padding: 15px 20px; border-radius: 12px; transition: background 0.15s;
    }
    a.btn:hover, button.btn:hover { background: #143326; }
    .copy-msg { margin: 10px 0 0; font-size: 13px; color: #1B4332; text-align: center; min-height: 18px; }
    .steps { margin-top: 24px; font-size: 13.5px; color: #4A5750; line-height: 1.7; }
    .steps strong { color: #1B2420; }
    code { background: #F0ECE2; padding: 2px 6px; border-radius: 5px; font-size: 12.5px; }
  </style>
</head>
<body>
  <div class="card">
    <div class="badge">শব্দশুদ্ধি ১.০ • সার্ভার-সাইড নিরাপদ AI প্রোক্সি</div>
    <h1>শব্দশুদ্ধি ১.০ (Sobdosuddhi)</h1>
    <p class="sub">বাংলা পান্ডুলিপি প্রুফরিডিং, অফলাইন বানান পরীক্ষা, AI লেখা শনাক্তকরণ, মানবিক ভাষায় রূপান্তর এবং সম্পূর্ণ পান্ডুলিপি ব্যবস্থাপনা।</p>
    ${apkSection}
    <div class="steps">
      <strong>অ্যান্ড্রয়েড অ্যাপে অনলাইন AI সার্ভার যুক্ত করার নিয়ম:</strong><br/>
      ১. এই পেজের উপরের HTTPS ঠিকানা (<code>https://...run.app</code>) কপি করুন।<br/>
      ২. ফোনে <strong>শব্দশুদ্ধি ১.০</strong> খুলে <strong>সেটিংস → অনলাইন AI সার্ভার সংযোগ</strong> অংশে URL-টি পেস্ট করে <strong>সংরক্ষণ</strong> চাপুন।<br/>
      ৩. <strong>সংযোগ পরীক্ষা করুন</strong> চাপলেই অনলাইন Gemini AI সক্রিয় হয়ে যাবে।
    </div>
  </div>
  <script>
    const el = document.getElementById('server-url');
    if (el) el.textContent = window.location.origin;
    function copyServerUrl() {
      const url = window.location.origin;
      const msg = document.getElementById('copy-msg');
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(() => {
          if (msg) msg.textContent = 'URL কপি হয়েছে: ' + url;
        }).catch(() => {
          if (msg) msg.textContent = 'ম্যানুয়ালি কপি করুন: ' + url;
        });
      } else if (msg) {
        msg.textContent = 'ম্যানুয়ালি কপি করুন: ' + url;
      }
    }
  </script>
</body>
</html>`;
}

const server = http.createServer(async (req, res) => {
  const urlPath = (req.url || '/').split('?')[0];

  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS_HEADERS);
    res.end();
    return;
  }

  if (req.method === 'GET' && (urlPath === '/health' || urlPath === '/api/health')) {
    const apkPath = findApkPath();
    return sendJson(res, 200, {
      ok: true,
      service: 'sobdosuddhi-server-proxy',
      apkReady: Boolean(apkPath),
      aiConfigured: Boolean(getServerApiKey()),
    });
  }

  if (req.method === 'POST' && urlPath === '/api/gemini/generate') {
    return handleGeminiGenerate(req, res);
  }

  if (req.method === 'POST' && urlPath === '/api/gemini/ocr') {
    return handleGeminiOcr(req, res);
  }

  if ((req.method === 'GET' || req.method === 'HEAD') &&
      (urlPath === '/download/Sobdosuddhi-1.0.0-debug.apk' || urlPath === '/app-debug.apk')) {
    const apkPath = findApkPath();
    if (!apkPath) {
      return sendJson(res, 404, { error: 'APK_NOT_FOUND', message: 'Debug APK file not found on this host.' });
    }
    const stat = fs.statSync(apkPath);
    res.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Disposition': 'attachment; filename="Sobdosuddhi-1.0.0-debug.apk"',
      'Content-Length': stat.size,
      'Cache-Control': 'no-cache',
      ...CORS_HEADERS,
    });
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    fs.createReadStream(apkPath).pipe(res);
    return;
  }

  if (req.method === 'GET' || req.method === 'HEAD') {
    const apkPath = findApkPath();
    const html = renderPortalHtml(apkPath ? getApkMeta(apkPath) : null, Boolean(getServerApiKey()));
    res.writeHead(200, {
      'Content-Type': 'text/html; charset=utf-8',
      'Cache-Control': 'no-cache',
      ...CORS_HEADERS,
    });
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    res.end(html);
    return;
  }

  sendJson(res, 405, { error: 'METHOD_NOT_ALLOWED' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Sobdosuddhi server-side AI proxy & APK portal listening on 0.0.0.0:${PORT}`);
});

function shutdown() {
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}

process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
