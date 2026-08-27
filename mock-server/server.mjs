// Hermes mock gateway — zero-dep Node HTTP server implementing the contract
// from §4 of Hermes-Companion-Plan.md. Two virtual gateways (gw-home and
// gw-cloud) multiplexed behind one process so the Android client can prove
// cross-gateway isolation over a real network.
//
// Run:  node mock-server/server.mjs
// Listens on 0.0.0.0:7800 by default.
//
// Endpoint surface (per gateway prefix /gw-<name>/...):
//   GET    /v1/capabilities
//   GET    /health
//   GET    /p/<profile>/v1/capabilities
//   GET    /api/profiles
//   GET    /api/sessions
//   POST   /api/sessions
//   GET    /api/sessions/<id>
//   GET    /api/sessions/<id>/messages
//   POST   /v1/runs                          (returns run_id)
//   POST   /api/sessions/<id>/chat         (alias of /v1/runs)
//   POST   /api/sessions/<id>/chat/stream  (SSE)
//   GET    /v1/runs/<id>/events            (SSE)
//   POST   /v1/runs/<id>/stop
//   POST   /v1/runs/<id>/approval
//   GET    /api/model/options

import http from 'node:http';
import { URL } from 'node:url';

const PORT = Number(process.env.PORT ?? 7800);
const HOST = process.env.HOST ?? '0.0.0.0';
const TAILSCALE_IP = process.env.TAILSCALE_IP ?? '100.83.141.111';

// ---------- in-memory state ----------
const gateways = new Map();

function makeGateway(id, label, profiles, baseUrl) {
  // Keyed by session id. Scanning each profile's first entry made every
  // session created after startup unreachable.
  const sessions = new Map();
  for (const p of profiles) {
    const sk = `sess-${id}-${p}-1`;
    sessions.set(sk, {
      session_id: sk,
      title: `Welcome — ${p}`,
      model_lock: null,
      run_state: 'idle',
      unread_count: 0,
      profile: p,
    });
  }
  return {
    id, label, profiles,
    base_url: baseUrl,
    capabilities: {
      'sessions.list': true,
      'sessions.create': true,
      'chat.stream': true,
      'runs.events': true,
      'approval.required': true,
      'profiles.multiplexed': true,
      'responses.stateful': true,
    },
    sessions,              // sessionId -> Session
    messages: new Map(),   // sessionId -> [Message]
    runs: new Map(),       // runId -> Run (gated or resolved)
  };
}

gateways.set('gw-home', makeGateway(
  'gw-home', 'Home',
  ['ash', 'misty'],
  'mock://gw-home',
));
gateways.set('gw-cloud', makeGateway(
  'gw-cloud', 'Cloud',
  ['ash', 'work'],
  'mock://gw-cloud',
));

function pushMessage(g, sessionId, role, text) {
  const m = {
    id: `msg-${Math.random().toString(36).slice(2, 10)}`,
    role,
    text,
    created_at: new Date().toISOString(),
    session_id: sessionId,
    profile: g.sessions.get(sessionId)?.profile ?? null,
  };
  if (!g.messages.has(sessionId)) g.messages.set(sessionId, []);
  g.messages.get(sessionId).push(m);
  return m;
}

// ---------- helpers ----------
function send(res, status, body, extraHeaders = {}) {
  const json = body === null ? '' : JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(json),
    'access-control-allow-origin': '*',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'content-type, authorization, x-hermes-gateway',
    ...extraHeaders,
  });
  res.end(json);
}

function sseHeaders(res) {
  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache',
    'connection': 'keep-alive',
    'access-control-allow-origin': '*',
    'x-accel-buffering': 'no',
  });
  res.write(':ok\n\n');
  // Periodic comment frame: lets clients set a finite read timeout and treat
  // silence as a dead socket.
  const hb = setInterval(() => {
    try { res.write(':hb\n\n'); } catch { clearInterval(hb); }
  }, 15000);
  res.on('close', () => clearInterval(hb));
}

function sseEvent(res, event, data) {
  res.write(`event: ${event}\n`);
  res.write(`data: ${JSON.stringify(data)}\n\n`);
}

function parseGatewayAndPath(pathname) {
  // /gw-home/... -> { gatewayId: 'gw-home', rest: '/...' }
  const m = pathname.match(/^\/(gw-[a-z0-9-]+)(\/.*)?$/);
  if (!m) return null;
  return { gatewayId: m[1], rest: m[2] || '/' };
}

function jsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', c => chunks.push(c));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf-8');
      if (!raw) return resolve({});
      try { resolve(JSON.parse(raw)); }
      catch (e) { reject(e); }
    });
    req.on('error', reject);
  });
}

function rid(prefix) {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}-${Date.now().toString(36)}`;
}

// ---------- mock LLM core ----------
function planRun(text) {
  // Approval gate for destructive commands.
  if (/^(deploy|send |publish|delete)\b/i.test(text)) {
    return { kind: 'approval', command: text.slice(0, 120) };
  }
  // Choose a tool or pure response.
  const lower = text.toLowerCase();
  if (/time|clock/.test(lower)) {
    return { kind: 'tool', name: 'get_time', input: '{}', output: new Date().toISOString() };
  }
  if (/calc|compute|\d+\s*[+\-*/]\s*\d+/.test(lower)) {
    const m = lower.match(/(\d+)\s*([+\-*/])\s*(\d+)/);
    if (m) {
      const a = Number(m[1]); const op = m[2]; const b = Number(m[3]);
      const r = op === '+' ? a + b : op === '-' ? a - b : op === '*' ? a * b : b !== 0 ? a / b : null;
      return { kind: 'tool', name: 'calculator', input: `${a}${op}${b}`, output: String(r) };
    }
  }
  return { kind: 'echo', name: 'echo', input: text, output: `Echoed from mock: ${text}` };
}

function replyTextFor(plan, original) {
  if (plan.kind === 'tool') {
    return `${plan.name} returned ${plan.output}.`;
  }
  return `Acknowledged: ${original}`;
}

// ---------- handlers ----------
async function handleChatStream(g, profile, sessionId, text, res) {
  sseHeaders(res);
  const runId = rid('run');
  const plan = planRun(text);

  // Persist user message immediately.
  pushMessage(g, sessionId, 'user', text);

  if (plan.kind === 'approval') {
    const request = {
      request_id: rid('apr'),
      run_id: runId,
      profile,
      gateway_id: g.id,
      command: plan.command,
      digest: 'sha256:' + Buffer.from(plan.command).toString('hex').slice(0, 16),
      options: ['once', 'session', 'always', 'deny'],
    };
    // Remember it: the stream ends here, so the decision arrives on a
    // separate request and the resumed run is replayed on /v1/runs/<id>/events.
    g.runs.set(runId, {
      run_id: runId, session_id: sessionId, profile,
      text, request, state: 'awaiting', events: [],
    });
    sseEvent(res, 'run.created', { run_id: runId, session_id: sessionId });
    sseEvent(res, 'run.approval_required', { run_id: runId, session_id: sessionId, request });
    res.end();
    return;
  }

  sseEvent(res, 'run.created', { run_id: runId, session_id: sessionId });
  await sleep(60);

  if (plan.kind === 'tool' || plan.kind === 'echo') {
    const toolRun = {
      id: rid('tool'),
      name: plan.name,
      status: 'running',
      input: plan.input,
      started_at: new Date().toISOString(),
    };
    sseEvent(res, 'tool.started', { run_id: runId, session_id: sessionId, tool_run: toolRun });
    await sleep(220);
    toolRun.status = 'completed';
    toolRun.output = plan.output;
    toolRun.completed_at = new Date().toISOString();
    sseEvent(res, 'tool.completed', { run_id: runId, session_id: sessionId, tool_run: toolRun });
  }

  const finalText = replyTextFor(plan, text);
  const tokens = chunk(finalText, 6);
  let streamed = '';
  for (const t of tokens) {
    streamed += t;
    sseEvent(res, 'assistant.delta', { run_id: runId, session_id: sessionId, delta: t });
    await sleep(35);
  }
  pushMessage(g, sessionId, 'assistant', finalText);
  sseEvent(res, 'run.completed', { run_id: runId, session_id: sessionId, final_text: finalText });
  res.end();
}

/**
 * Turns a decision into the events the run would have produced. Denial is a
 * terminal failure with a reason; approval executes and completes.
 */
function resolveRun(g, run, option) {
  if (option === 'deny') {
    run.state = 'denied';
    run.events = [
      ['run.failed', { run_id: run.run_id, session_id: run.session_id, reason: 'denied by operator' }],
    ];
    return run;
  }
  const toolRun = {
    id: rid('tool'),
    name: 'exec',
    status: 'completed',
    input: run.request.command,
    output: `executed after ${option} approval`,
    started_at: new Date().toISOString(),
    completed_at: new Date().toISOString(),
  };
  const finalText = `Approved (${option}). ${run.request.command} completed.`;
  pushMessage(g, run.session_id, 'assistant', finalText);
  run.state = 'completed';
  run.events = [
    ['tool.started', { run_id: run.run_id, session_id: run.session_id, tool_run: { ...toolRun, status: 'running', output: undefined } }],
    ['tool.completed', { run_id: run.run_id, session_id: run.session_id, tool_run: toolRun }],
    ...chunk(finalText, 6).map(t => ['assistant.delta', { run_id: run.run_id, session_id: run.session_id, delta: t }]),
    ['run.completed', { run_id: run.run_id, session_id: run.session_id, final_text: finalText }],
  ];
  return run;
}

/** Creates a run for a session, gating it when the plan needs approval. */
function createRun(g, sessionId, text) {
  const sess = g.sessions.get(sessionId);
  const profile = sess.profile;
  const runId = rid('run');
  const plan = planRun(text);
  pushMessage(g, sessionId, 'user', text);
  const run = {
    run_id: runId, session_id: sessionId, profile, text, state: 'awaiting', events: [],
    request: {
      request_id: rid('apr'), run_id: runId, profile, gateway_id: g.id,
      command: plan.kind === 'approval' ? plan.command : text,
      digest: 'sha256:' + Buffer.from(plan.kind === 'approval' ? plan.command : text).toString('hex').slice(0, 16),
      options: plan.kind === 'approval' ? ['once', 'session', 'always', 'deny'] : [],
    },
  };
  if (plan.kind !== 'approval') resolveRun(g, run, 'once');
  g.runs.set(runId, run);
  return run;
}

function chunk(s, n) {
  const out = [];
  for (let i = 0; i < s.length; i += n) out.push(s.slice(i, i + n));
  return out;
}
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

// ---------- router ----------
async function route(req, res) {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET, POST, OPTIONS',
      'access-control-allow-headers': 'content-type, authorization, x-hermes-gateway',
    });
    return res.end();
  }
  const url = new URL(req.url, `http://${req.headers.host}`);
  const path = url.pathname;
  // Gateway index for the top-level / and /health.
  if (path === '/' || path === '/health') {
    return send(res, 200, {
      status: 'ok',
      pid: process.pid,
      gateways: Array.from(gateways.values()).map(g => ({ id: g.id, label: g.label })),
      tailscale_ip: TAILSCALE_IP,
      port: PORT,
    });
  }
  if (path === '/health/detailed') {
    return send(res, 200, {
      status: 'ok',
      version: '0.1.0-poc',
      uptime_sec: Math.round(process.uptime()),
      sessions: Array.from(gateways.values()).reduce((n, g) => n + g.sessions.size, 0),
    });
  }
  // v1/capabilities at the root applies to all gateways.
  if (path === '/v1/capabilities') {
    return send(res, 200, { capabilities: aggregatedCapabilities() });
  }

  const parsed = parseGatewayAndPath(path);
  if (!parsed) return send(res, 404, { error: 'not_found', path });
  const g = gateways.get(parsed.gatewayId);
  if (!g) return send(res, 404, { error: 'unknown_gateway', gateway_id: parsed.gatewayId });
  const rest = parsed.rest;

  if (rest === '/v1/capabilities') return send(res, 200, { capabilities: g.capabilities });
  if (rest === '/api/profiles') {
    return send(res, 200, {
      profiles: g.profiles.map(p => ({
        profile_id: p,
        display_name: p[0].toUpperCase() + p.slice(1),
      })),
    });
  }
  if (rest === '/api/sessions' && req.method === 'GET') {
    return send(res, 200, { sessions: Array.from(g.sessions.values()) });
  }
  if (rest === '/api/sessions' && req.method === 'POST') {
    const body = await jsonBody(req).catch(() => ({}));
    const profile = body.profile ?? g.profiles[0];
    if (!g.profiles.includes(profile)) return send(res, 404, { error: 'unknown_profile', profile });
    const sessionId = `sess-${g.id}-${profile}-${Date.now().toString(36)}`;
    const sess = {
      session_id: sessionId, title: body.title ?? 'New chat',
      model_lock: null, run_state: 'idle', unread_count: 0, profile,
    };
    g.sessions.set(sessionId, sess);
    return send(res, 201, { session: sess });
  }

  // Per-profile multiplexer
  const pm = rest.match(/^\/p\/([^/]+)(\/.*)$/);
  if (pm) {
    const profile = pm[1];
    const sub = pm[2];
    if (!g.profiles.includes(profile)) return send(res, 404, { error: 'unknown_profile', profile });
    if (sub === '/v1/capabilities') return send(res, 200, { capabilities: g.capabilities });
  }

  const sessionMatch = rest.match(/^\/api\/sessions\/([^/]+)(\/.*)?$/);
  if (sessionMatch) {
    const sessionId = sessionMatch[1];
    const sub = sessionMatch[2] || '';
    const sess = g.sessions.get(sessionId);
    if (!sess) return send(res, 404, { error: 'unknown_session', session_id: sessionId });
    const profile = sess.profile;

    if (sub === '' || sub === '/') {
      return send(res, 200, { session: sess });
    }
    if (sub === '/messages' && req.method === 'GET') {
      return send(res, 200, { messages: g.messages.get(sessionId) ?? [] });
    }
    if (sub === '/chat' && req.method === 'POST') {
      const body = await jsonBody(req).catch(() => ({}));
      const text = String(body.text ?? '');
      if (!text) return send(res, 400, { error: 'text_required' });
      const run = createRun(g, sessionId, text);
      return send(res, 202, { run_id: run.run_id, session_id: sessionId });
    }
    if (sub === '/chat/stream' && req.method === 'POST') {
      const body = await jsonBody(req).catch(() => ({}));
      const text = String(body.text ?? '');
      if (!text) return send(res, 400, { error: 'text_required' });
      return handleChatStream(g, profile, sessionId, text, res);
    }
  }

  if (rest === '/v1/runs' && req.method === 'POST') {
    const body = await jsonBody(req).catch(() => ({}));
    const sessionId = String(body.session_id ?? '');
    const text = String(body.text ?? '');
    if (!sessionId || !text) return send(res, 400, { error: 'session_id_and_text_required' });
    if (!g.sessions.has(sessionId)) return send(res, 404, { error: 'unknown_session', session_id: sessionId });
    const run = createRun(g, sessionId, text);
    return send(res, 202, { run_id: run.run_id, session_id: sessionId, state: run.state });
  }

  const runMatch = rest.match(/^\/v1\/runs\/([^/]+)(\/.*)?$/);
  if (runMatch) {
    const runId = runMatch[1];
    const sub = runMatch[2] || '';
    if (sub === '/stop' && req.method === 'POST') {
      const run = g.runs.get(runId);
      if (run && run.state === 'awaiting') {
        run.state = 'stopped';
        run.events = [['run.failed', { run_id: runId, session_id: run.session_id, reason: 'stopped by operator' }]];
      }
      return send(res, 200, { ok: true, run_id: runId });
    }
    if (sub === '/approval' && req.method === 'POST') {
      const body = await jsonBody(req).catch(() => ({}));
      const option = String(body.option ?? '').toLowerCase();
      if (!['once', 'session', 'always', 'deny'].includes(option)) {
        return send(res, 400, { error: 'bad_option', option });
      }
      const run = g.runs.get(runId);
      if (!run) return send(res, 404, { error: 'unknown_run', run_id: runId });
      if (body.request_id && body.request_id !== run.request.request_id) {
        return send(res, 409, { error: 'stale_request', expected: run.request.request_id });
      }
      if (run.state !== 'awaiting') {
        // First decision wins; a second is not an error, just a no-op.
        return send(res, 200, { ok: true, run_id: runId, state: run.state, already_decided: true });
      }
      resolveRun(g, run, option);
      return send(res, 200, { ok: true, run_id: runId, state: run.state });
    }
    if (sub === '/events' && req.method === 'GET') {
      const run = g.runs.get(runId);
      sseHeaders(res);
      if (!run) {
        sseEvent(res, 'run.failed', { run_id: runId, session_id: null, reason: 'unknown run' });
        return res.end();
      }
      if (run.state === 'awaiting') {
        sseEvent(res, 'run.approval_required', {
          run_id: runId, session_id: run.session_id, request: run.request,
        });
        return res.end();
      }
      for (const [name, payload] of run.events) {
        sseEvent(res, name, payload);
        if (name === 'assistant.delta') await sleep(35);
      }
      return res.end();
    }
  }

  if (rest === '/api/model/options') {
    return send(res, 200, { models: ['mock-fast', 'mock-balanced'] });
  }

  return send(res, 404, { error: 'not_found', gateway: g.id, rest });
}

function aggregatedCapabilities() {
  const merged = {};
  for (const g of gateways.values()) Object.assign(merged, g.capabilities);
  merged['gateways.available'] = Array.from(gateways.keys());
  return merged;
}

// ---------- server ----------
const server = http.createServer((req, res) => {
  const start = Date.now();
  res.on('finish', () => {
    const ms = Date.now() - start;
    process.stdout.write(`${new Date().toISOString()} ${req.method} ${req.url} -> ${res.statusCode} (${ms}ms)\n`);
  });
  route(req, res).catch(err => {
    process.stderr.write(`error: ${err.message}\n${err.stack}\n`);
    if (!res.headersSent) send(res, 500, { error: 'internal', message: err.message });
    else res.end();
  });
});

server.listen(PORT, HOST, () => {
  process.stdout.write(`hermes mock gateway listening on http://${HOST}:${PORT}\n`);
  process.stdout.write(`  gateways: ${Array.from(gateways.keys()).join(', ')}\n`);
  process.stdout.write(`  tailscale IP for S22 to reach: http://${TAILSCALE_IP}:${PORT}\n`);
});

process.on('SIGINT', () => { server.close(() => process.exit(0)); });
process.on('SIGTERM', () => { server.close(() => process.exit(0)); });
