/* Play next helper — Cloudflare Worker.
   GET  /sgdb/...     → SteamGridDB API with the SGDB_KEY secret.
   POST /why          → free Workers AI (binding "AI"): why a game fits your ratings.
   POST /chat         → Nexi chat (Workers AI). Body {persona, memory, context, messages}. No keys in the app.
   POST /chat/memory  → rewrites Nexi's short memory of the player after a chat.
   GET  /nudge/key    → the web-push public key (made once and kept in KV).
   POST /nudge        → saves a phone's push subscription + its next few days of nudges.
   cron (every 15 min) → sends the nudges that are due, and tells every phone when a new version of the app is out.
   Needs: Workers AI binding "AI", KV binding "NUDGE", a cron trigger "*\/15 * * * *". */
const SGDB = "https://www.steamgriddb.com/api/v2";
/* tried in order; Cloudflare retires models now and then */
/* the bigger models know far more games and follow instructions better; used for chat and similar games,
   falling back down the list if one is missing or the free daily allowance runs out */
const HELPER_V = 3;
const APP_PAGE = "https://shamlawy.github.io/Play-next/index.html";
const MODELS_BIG = ["@cf/meta/llama-3.3-70b-instruct-fp8-fast", "@cf/meta/llama-4-scout-17b-16e-instruct"];
const MODELS = ["@cf/meta/llama-3.1-8b-instruct-fast", "@cf/meta/llama-3.1-8b-instruct", "@cf/meta/llama-3.2-3b-instruct", "@cf/mistral/mistral-7b-instruct-v0.1"];
const ALLOW = ["https://shamlawy.github.io", "http://localhost:8765"];

const cors = origin => ({
  "Access-Control-Allow-Origin": ALLOW.includes(origin) ? origin : ALLOW[0],
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
  "Vary": "Origin"
});
const json = (data, status, h) => new Response(JSON.stringify(data), { status, headers: { ...h, "Content-Type": "application/json" } });

export default {
  async fetch(req, env) {
    try { return await handle(req, env); }
    catch (e) { return json({ error: String(e && e.message || e) }, 500, cors(req.headers.get("Origin") || "")); }
  },
  async scheduled(ev, env, ctx) { ctx.waitUntil(Promise.all([sendDue(env), checkUpdate(env)])); }
};

async function ask(env, messages, maxTokens, big) {
  if (!env.AI) throw new Error("No Workers AI binding named AI");
  let last;
  for (const m of big ? MODELS_BIG.concat(MODELS) : MODELS) {
    try { const out = await env.AI.run(m, { max_tokens: maxTokens || 300, messages }); const t = out && out.response;
      if (t) return typeof t === "string" ? t : JSON.stringify(t); }
    catch (e) { last = e; }
  }
  throw last || new Error("AI gave an empty answer");
}

async function handle(req, env) {
  {
    const url = new URL(req.url), h = cors(req.headers.get("Origin") || "");
    if (req.method === "OPTIONS") return new Response(null, { headers: h });

    if (url.pathname.startsWith("/sgdb/")) {
      const r = await fetch(SGDB + url.pathname.slice(5) + url.search, { headers: { Authorization: "Bearer " + env.SGDB_KEY } });
      return new Response(r.body, { status: r.status, headers: { ...h, "Content-Type": "application/json", "Cache-Control": "public, max-age=86400" } });
    }

    /* open this address in a browser to check the AI works */
    if (url.pathname === "/why" && req.method === "GET") {
      const t = await ask(env, [{ role: "user", content: "Say OK." }]);
      return json({ ok: true, reply: t }, 200, h);
    }

    if (url.pathname === "/why" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const g = b && b.game, taste = Array.isArray(b && b.taste) ? b.taste.slice(0, 20) : [];
      if (!g || !g.title || taste.length < 3) return json({ error: "need a game and 3+ rated games" }, 400, h);

      const list = taste.map(t => `- ${t.title}: ${t.score}/10${t.again ? " (would replay)" : ""}` +
        (t.genres && t.genres.length ? ` [${t.genres.join(", ")}]` : "") +
        (t.liked && t.liked.length ? ` liked: ${t.liked.join(", ")}` : "") +
        (t.parts && t.parts.length ? ` scores: ${t.parts.join(", ")}` : "")).join("\n");
      const about = `${g.title}` + (g.genres && g.genres.length ? ` [${g.genres.join(", ")}]` : "") +
        (g.about ? `\n${String(g.about).slice(0, 500)}` : "") +
        (g.played ? `\nThey already played it and scored it ${g.myScore}/10 — explain why it did or didn't land for them.` : "");

      const text = await ask(env, [
          { role: "system", content: "You are a friendly games critic who knows this player's taste. Use only what their ratings show. Be specific: name games from their list. No spoilers. Short sentences, second person." },
          { role: "user", content: `My rated games (out of 10):\n${list}\n\nGame: ${about}\n\nReply in exactly this format and nothing else:\nVERDICT: <Strong fit | Good fit | Mixed | Not for you>\n- <reason tied to my ratings>\n- <reason tied to my ratings>\n- <one thing I might not like>` }
      ]);
      return json({ text: text.trim() }, 200, h);
    }

    if (url.pathname === "/chat" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const msgs = (Array.isArray(b && b.messages) ? b.messages : []).slice(-12)
        .filter(m => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
        .map(m => ({ role: m.role, content: m.content.slice(0, 800) }));
      if (!msgs.length || msgs[msgs.length - 1].role !== "user") return json({ error: "say something first" }, 400, h);
      const text = await ask(env, [{ role: "system", content: chatSystem(b) }, ...msgs], 400, true);
      return json({ text: text.trim() }, 200, h);
    }

    /* which helper this is, so the app can tell when it needs updating */
    if (url.pathname === "/version") return json({ v: HELPER_V, chat: true, similar: true, nudge: !!env.NUDGE, updates: !!env.NUDGE }, 200, h);

    /* games like one game: the AI names them (it knows games far better than tag searches do);
       the app then checks every name on RAWG and drops anything that isn't a real game */
    if (url.pathname === "/similar" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const title = String(b && b.title || "").slice(0, 120);
      if (!title) return json({ error: "need a title" }, 400, h);
      const cut = (a, n, l) => (Array.isArray(a) ? a : []).slice(0, n).map(x => String(x).slice(0, l || 80));
      const favs = cut(b.favourites, 8), skip = cut(b.exclude, 80);
      const text = await ask(env, [
        { role: "system", content: "You are a video game expert. You recommend real, existing games only (released, or officially announced with a name). You never invent titles. You answer with JSON only." },
        { role: "user", content: `Give the 12 games most similar to "${title}"${b.year ? " (" + String(b.year).slice(0, 4) + ")" : ""}${b.genres ? " [" + String(b.genres).slice(0, 80) + "]" : ""}: same kind of gameplay, structure, tone and fans. Think of what a fan of it would play next: same series and spiritual successors first, then the closest matches.
${favs.length ? "The player's favourite games (lean towards these tastes): " + favs.join("; ") + "\n" : ""}${skip.length ? "Do NOT list any of these (the player already has them): " + skip.join("; ") + "\n" : ""}Answer with ONLY a JSON array like [{"name":"Exact Official Title","why":"max 8 words why it's similar"}]. No other text.` }
      ], 700, true);
      const m = text.match(/\[[\s\S]*\]/);
      let list = [];
      try { list = JSON.parse(m ? m[0] : text); } catch (e) {
        /* a model sometimes breaks the JSON: fall back to pulling out the names */
        list = [...text.matchAll(/"name"\s*:\s*"([^"]+)"(?:[^}]*"why"\s*:\s*"([^"]*)")?/g)].map(x => ({ name: x[1], why: x[2] || "" }));
      }
      const games = (Array.isArray(list) ? list : []).filter(x => x && typeof x.name === "string" && x.name.trim())
        .slice(0, 14).map(x => ({ name: x.name.trim().slice(0, 100), why: String(x.why || "").trim().slice(0, 80) }));
      return json({ games }, 200, h);
    }

    if (url.pathname === "/chat/memory" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const convo = (Array.isArray(b && b.messages) ? b.messages : []).slice(-12)
        .map(m => (m.role === "user" ? "Player: " : "Nexi: ") + String(m.content || "").slice(0, 500)).join("\n");
      const text = await ask(env, [
        { role: "system", content: "You keep a short private memory about one video game player for their assistant. Facts only: tastes, habits, goals, platforms, likes and dislikes, what they asked for. No scores that the app already knows. Plain sentences, max 600 characters, no lists, no greeting." },
        { role: "user", content: `Current memory:\n${String(b && b.memory || "(empty)").slice(0, 800)}\n\nNew conversation:\n${convo}\n\nWrite the updated memory now (max 600 characters). Keep old facts unless the conversation changes them.` }
      ], 250);
      return json({ memory: text.trim().slice(0, 700) }, 200, h);
    }

    if (url.pathname === "/nudge/key" && req.method === "GET") {
      const k = await vapid(env);
      return json({ key: k.pub }, 200, h);
    }

    if (url.pathname === "/nudge" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      if (!env.NUDGE) return json({ error: "No KV binding named NUDGE" }, 500, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const id = String(b && b.id || "");
      if (!/^[A-Za-z0-9_-]{16,40}$/.test(id)) return json({ error: "bad id" }, 400, h);
      if (b.off) { await env.NUDGE.delete("sub:" + id); return json({ ok: true, off: true }, 200, h); }
      const sub = b.sub;
      if (!sub || typeof sub.endpoint !== "string" || !/^https:\/\//.test(sub.endpoint) || !sub.keys || !sub.keys.p256dh || !sub.keys.auth)
        return json({ error: "bad subscription" }, 400, h);
      const plan = (Array.isArray(b.plan) ? b.plan : []).slice(0, 30)
        .filter(n => n && +n.at > Date.now() - 36e5 && +n.at < Date.now() + 10 * 864e5)
        .map(n => ({ at: +n.at, title: String(n.title || "Play next").slice(0, 60), body: String(n.body || "").slice(0, 180), tag: String(n.tag || "nudge").slice(0, 20), url: String(n.url || "./").slice(0, 80) }));
      /* upd: this phone also wants a ping when a new version of the app is out (on unless it says no) */
      await env.NUDGE.put("sub:" + id, JSON.stringify({ sub: { endpoint: sub.endpoint, keys: { p256dh: sub.keys.p256dh, auth: sub.keys.auth } }, plan, upd: b.upd !== false, t: Date.now() }), { expirationTtl: 60 * 864e5 });
      return json({ ok: true, n: plan.length }, 200, h);
    }

    /* open in a browser to send yourself a test nudge now: /nudge/test?id=... */
    if (url.pathname === "/nudge/test" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const rec = env.NUDGE && await env.NUDGE.get("sub:" + String(b && b.id || ""), "json");
      if (!rec) return json({ error: "no subscription saved for this phone" }, 404, h);
      const r = await webPush(env, rec.sub, { title: "Nexi", body: "Test nudge: this is how I'll ping you 👋", tag: "test", url: "./" });
      return json({ ok: r.ok, status: r.status }, 200, h);
    }

    return json({ error: "not found" }, 404, h);
  }
}

/* ---- Nexi chat ---- */
const PERSONAS = {
  butler: "a calm, polite butler. Unhurried, precise, a little formal (\"Very good.\"), generous with practical tips. No slang, few emoji.",
  hype: "an excitable hype buddy. Energetic, warm, exclamation marks, a few emoji, celebrates every win.",
  coach: "a chill coach. Relaxed, encouraging, short sentences, focuses on small next steps and not burning out.",
  rival: "a sarcastic rival. Dry teasing and playful jabs, but never mean and always actually helpful in the end.",
  bard: "a storyteller bard. Speaks with a light fantasy flair, turns the player's games into a small saga, still gives clear advice.",
  nerd: "a stats nerd. Loves numbers and patterns from the player's data, cites them briefly, concise and friendly."
};
function chatSystem(b) {
  const p = PERSONAS[b && b.persona] || PERSONAS.hype;
  const c = b && b.context || {};
  const lines = [];
  const list = (k, n) => Array.isArray(c[k]) ? c[k].slice(0, n).map(x => String(x).slice(0, 120)) : [];
  if (c.name) lines.push("Player's name: " + String(c.name).slice(0, 30));
  if (list("ranking", 15).length) lines.push("Their ranking of played games (best first, tier + score):\n" + list("ranking", 15).join("\n"));
  if (list("queue", 15).length) lines.push("Their backlog / queue (play-next order):\n" + list("queue", 15).join("\n"));
  if (list("playing", 6).length) lines.push("Playing now: " + list("playing", 6).join("; "));
  if (list("competitive", 6).length) lines.push("Competitive games they play (no win/loss tracking): " + list("competitive", 6).join("; "));
  if (list("insights", 6).length) lines.push("What the app learned from their duels: " + list("insights", 6).join("; "));
  if (c.platform) lines.push("Main platform right now: " + String(c.platform).slice(0, 30));
  if (b && b.memory) lines.push("Your memory of them from earlier chats: " + String(b.memory).slice(0, 700));
  return `You are Nexi, the little mascot inside the game-ranking app "Play next". Your personality: ${p}
Keep the same facts and tips whatever the personality; only the voice changes.
Help with: what to play next, recommendations (say if a game is already in their list), explaining their taste from the data below, and game tips (no spoilers unless asked).
Be short: 1–4 sentences or a tiny list. Use only the data below for claims about the player; if unsure, say so. Never invent their scores.
${lines.join("\n")}`;
}

/* ---- web push (RFC 8291 aes128gcm + VAPID), no libraries ---- */
const b64u = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const unb64u = s => { s = String(s).replace(/-/g, "+").replace(/_/g, "/"); while (s.length % 4) s += "="; return Uint8Array.from(atob(s), c => c.charCodeAt(0)); };
const cat = (...a) => { const out = new Uint8Array(a.reduce((n, x) => n + x.length, 0)); let o = 0; a.forEach(x => { out.set(x, o); o += x.length; }); return out; };
const te = new TextEncoder();
async function hmac(key, data) { const k = await crypto.subtle.importKey("raw", key, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]); return new Uint8Array(await crypto.subtle.sign("HMAC", k, data)); }
/* the server's own key pair, made on first use and kept in KV (nothing to set up by hand) */
async function vapid(env) {
  if (!env.NUDGE) throw new Error("No KV binding named NUDGE");
  let k = await env.NUDGE.get("vapid", "json");
  if (!k) {
    const kp = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
    const jwk = await crypto.subtle.exportKey("jwk", kp.privateKey), raw = await crypto.subtle.exportKey("raw", kp.publicKey);
    k = { jwk, pub: b64u(raw) };
    await env.NUDGE.put("vapid", JSON.stringify(k));
  }
  return k;
}
async function vapidAuth(env, endpoint) {
  const k = await vapid(env), aud = new URL(endpoint).origin;
  const head = b64u(te.encode(JSON.stringify({ typ: "JWT", alg: "ES256" })));
  const body = b64u(te.encode(JSON.stringify({ aud, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: "https://shamlawy.github.io/Play-next/" })));
  const key = await crypto.subtle.importKey("jwk", k.jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, te.encode(head + "." + body));
  return `vapid t=${head}.${body}.${b64u(sig)}, k=${k.pub}`;
}
async function encrypt(sub, payload) {
  const ua = unb64u(sub.keys.p256dh), auth = unb64u(sub.keys.auth);
  const eph = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]);
  const asPub = new Uint8Array(await crypto.subtle.exportKey("raw", eph.publicKey));
  const uaKey = await crypto.subtle.importKey("raw", ua, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const secret = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: uaKey }, eph.privateKey, 256));
  const prkKey = await hmac(auth, secret);
  const ikm = await hmac(prkKey, cat(te.encode("WebPush: info\0"), ua, asPub, new Uint8Array([1])));
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const prk = await hmac(salt, ikm);
  const cek = (await hmac(prk, cat(te.encode("Content-Encoding: aes128gcm\0"), new Uint8Array([1])))).slice(0, 16);
  const nonce = (await hmac(prk, cat(te.encode("Content-Encoding: nonce\0"), new Uint8Array([1])))).slice(0, 12);
  const key = await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["encrypt"]);
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, key, cat(te.encode(payload), new Uint8Array([2]))));
  const rs = new Uint8Array([0, 0, 16, 0]);
  return cat(salt, rs, new Uint8Array([asPub.length]), asPub, ct);
}
async function webPush(env, sub, msg) {
  const body = await encrypt(sub, JSON.stringify(msg));
  return fetch(sub.endpoint, { method: "POST", body, headers: {
    "Content-Encoding": "aes128gcm", "Content-Type": "application/octet-stream", TTL: "43200", Urgency: "normal",
    Authorization: await vapidAuth(env, sub.endpoint) } });
}
async function sendDue(env) {
  if (!env.NUDGE) return;
  let cursor;
  do {
    const page = await env.NUDGE.list({ prefix: "sub:", cursor });
    for (const k of page.keys) {
      const rec = await env.NUDGE.get(k.name, "json"); if (!rec) continue;
      const now = Date.now(), plan = rec.plan || [], due = plan.filter(n => n.at <= now), later = plan.filter(n => n.at > now);
      if (!due.length) continue;
      /* missed a few (phone off, cron hiccup)? send at most the two newest that are still fresh, never a pile */
      const send = due.filter(n => now - n.at < 3 * 36e5).slice(-2);
      let gone = false;
      for (const n of send) {
        try { const r = await webPush(env, rec.sub, n); if (r.status === 404 || r.status === 410) { gone = true; break; } } catch (e) {}
      }
      if (gone) await env.NUDGE.delete(k.name);
      else await env.NUDGE.put(k.name, JSON.stringify({ ...rec, plan: later }), { expirationTtl: 60 * 864e5 });
    }
    cursor = page.list_complete ? null : page.cursor;
  } while (cursor);
}

/* ---- "there's a new version": the cron reads the live app's APP_V; when it changes, every phone hears about it ---- */
function whatsNew(html, v) {
  /* the first WHATS_NEW entry for this version (the headline one): its "say" line, tags stripped, first sentence or two */
  const re = new RegExp("\\{\\s*v:\\s*" + v + "\\b[\\s\\S]*?say:\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|`[^`]*`)");
  const m = re.exec(html), last = m && m[1];
  if (!last) return "";
  const t = last.slice(1, -1).replace(/\\"/g, '"').replace(/<[^>]+>/g, "").replace(/\$\{[^}]*\}/g, "").replace(/\s+/g, " ").trim();
  if (t.length <= 170) return t;
  const cut = t.slice(0, 170), dot = Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("! "));
  return dot > 60 ? cut.slice(0, dot + 1) : cut.replace(/\s+\S*$/, "") + "…";
}
async function checkUpdate(env) {
  if (!env.NUDGE) return;
  let html = "";
  try { const r = await fetch(APP_PAGE + "?t=" + Date.now(), { cf: { cacheTtl: 0 }, headers: { "Cache-Control": "no-cache" } }); if (!r.ok) return; html = await r.text(); }
  catch (e) { return; }
  const m = html.match(/const APP_V = "(\d+)"/); if (!m) return;
  const v = +m[1], seen = +(await env.NUDGE.get("appv") || 0);
  if (!seen) { await env.NUDGE.put("appv", String(v)); return; }      /* first run: just remember it */
  if (v <= seen) return;
  await env.NUDGE.put("appv", String(v));
  const msg = { title: "✨ Play next v" + v + " is here", body: whatsNew(html, v) || "Open the app and Nexi will show you what's new.", tag: "update", url: "./?nudge=update" };
  let cursor;
  do {
    const page = await env.NUDGE.list({ prefix: "sub:", cursor });
    for (const k of page.keys) {
      const rec = await env.NUDGE.get(k.name, "json"); if (!rec || rec.upd === false) continue;
      try { const r = await webPush(env, rec.sub, msg); if (r.status === 404 || r.status === 410) await env.NUDGE.delete(k.name); } catch (e) {}
    }
    cursor = page.list_complete ? null : page.cursor;
  } while (cursor);
}
