/* Play next helper — Cloudflare Worker.
   GET  /sgdb/...     → SteamGridDB API with the SGDB_KEY secret.
   POST /why          → free Workers AI (binding "AI"): why a game fits your ratings.
   POST /chat         → Nexi chat (Workers AI). Body {persona, memory, context, messages}. No keys in the app.
   POST /chat/memory  → rewrites Nexi's short memory of the player after a chat.
   GET  /nudge/key    → the web-push public key (made once and kept in KV).
   POST /nudge        → saves a phone's push subscription + its next few days of nudges.
   POST /announce     → tells every phone about a new version of the app now (GitHub calls it once the site is live).
   POST /bug          → the app's automatic problem reports (no game data), kept in KV as bug:<sig>.
   GET  /bugs, POST /bugs/ack → for the GitHub "Problem reports" job only (Bearer BUG_KEY), which files them as issues.
   POST /recap        → "the story so far" up to the player's own note (Workers AI, no spoilers past it).
   POST /steam        → Steam's own tags (genres players use) and "more like this" for a list of games.
   POST /reviews      → Steam's player-review summary (e.g. "Very Positive", 93% of 1,240) for a list of games (v11).
   POST /hours        → typical time to beat (main / main + extras / everything) for a list of games, from the AI (v11).
   POST /prices       → Steam + PlayStation Store + Nintendo eShop prices for a list of games; with an id it's re-checked daily and drops are pushed.
   POST /vault/put, GET /vault/list, GET /vault/get, POST /vault/del → cloud backup. The phone encrypts everything with a key
        made from its restore code (which never leaves the phone); this only keeps the sealed bytes.
   cron (every 15 min) → sends the nudges that are due, and is the backup for new-version pings.
   Needs: Workers AI binding "AI", KV binding "NUDGE", a cron trigger "*\/15 * * * *". */
const SGDB = "https://www.steamgriddb.com/api/v2";
/* tried in order; Cloudflare retires models now and then */
/* the bigger models know far more games and follow instructions better; used for chat and similar games,
   falling back down the list if one is missing or the free daily allowance runs out */
const HELPER_V = 12;
/* KV expirationTtl is in SECONDS (and must fit a 32-bit int): 60 days. It was 60 * 864e5 (milliseconds), which KV refused
   with "Value out of range", so no phone could ever sign up for nudges. */
const SUB_TTL = 60 * 86400;
const UPD_TTL = 28 * 86400;   /* seconds a "new version" push waits for an offline phone (the push services cap it at 4 weeks) */
const APP_PAGE = "https://shamlawy.github.io/Play-next/index.html";
const MODELS_BIG = ["@cf/meta/llama-3.3-70b-instruct-fp8-fast", "@cf/meta/llama-4-scout-17b-16e-instruct"];
const MODELS = ["@cf/meta/llama-3.1-8b-instruct-fast", "@cf/meta/llama-3.1-8b-instruct", "@cf/meta/llama-3.2-3b-instruct", "@cf/mistral/mistral-7b-instruct-v0.1"];
/* cloud backup: kept 400 days after the last write; a sealed copy can be up to 20 MB */
const VAULT_TTL = 400 * 86400, VAULT_MAX = 20 * 1024 * 1024;
const ALLOW = ["https://shamlawy.github.io", "http://localhost:8765"];

const cors = origin => ({
  "Access-Control-Allow-Origin": ALLOW.includes(origin) ? origin : ALLOW[0],
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Vary": "Origin"
});
const json = (data, status, h) => new Response(JSON.stringify(data), { status, headers: { ...h, "Content-Type": "application/json" } });

export default {
  async fetch(req, env) {
    try { return await handle(req, env); }
    catch (e) { return json({ error: String(e && e.message || e) }, 500, cors(req.headers.get("Origin") || "")); }
  },
  async scheduled(ev, env, ctx) { ctx.waitUntil(Promise.all([sendDue(env), checkUpdate(env), pxCron(env).catch(() => {})])); }
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

    /* GitHub calls this the moment a new version of the app is live, so phones hear about it straight away
       (the 15-minute cron is the backup). Safe to call by anyone: it only ever announces a version once. */
    if (url.pathname === "/announce" && (req.method === "POST" || req.method === "GET")) {
      if (!env.NUDGE) return json({ error: "No KV binding named NUDGE" }, 500, h);
      return json(await checkUpdate(env), 200, h);
    }

    /* which helper this is, so the app can tell when it needs updating */
    if (url.pathname === "/version") return json({ v: HELPER_V, chat: true, similar: true, nudge: !!env.NUDGE, updates: !!env.NUDGE, bugs: !!env.NUDGE, review: true, vault: !!env.NUDGE, prices: true, recap: true, eshop: true, steam: true, reviews: true, hours: true }, 200, h);
    if (url.pathname === "/prices" && req.method === "POST") return pxRoute(req, env, h);
    if (url.pathname === "/steam" && req.method === "POST") return stRoute(req, env, h);
    if (url.pathname === "/reviews" && req.method === "POST") return rvRoute(req, env, h);
    if (url.pathname === "/hours" && req.method === "POST") return hbRoute(req, env, h);
    if (url.pathname.startsWith("/vault/")) return vault(req, env, url, h);
    /* v5: Nexi's design eye. The app sends a map of one screen (boxes, sizes, colours; titles already replaced by
       ‹game›); the big model answers as a strict mobile UI designer with at most 3 concrete problems, each naming
       the element ids it means. The app keeps only confident ones and files them as "Design review" reports. */
    if (url.pathname === "/review" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 90).map(x => String(x).slice(0, 160));
      if (items.length < 3) return json({ issues: [] }, 200, h);
      const text = await ask(env, [
        { role: "system", content: "You are a strict senior mobile UI designer reviewing screens of a dark-themed game-backlog app. You only report problems a user would really notice on the phone, you never invent elements, and you answer with JSON only." },
        { role: "user", content: `Screen "${String(b.screen || "").slice(0, 40)}", viewport ${+b.w || 0}×${+b.h || 0} CSS px. Each line is one visible element: id | kind | x,y,w,h (px) | font size/weight | text colour on background (contrast) | radius | text (‹game› = a game title).
${items.join("\n")}

Find at most 3 concrete visual design problems, for example: elements misaligned with their neighbours, uneven spacing in a row or list, text too small to read on a phone, buttons of the same kind with different sizes/radii/fonts, crowded areas with no breathing room, labels that are cut or crammed, something that looks out of place or unbalanced, poor visual hierarchy (a minor thing louder than the main thing). Ignore anything that is fine. Cite the element ids. If there is nothing clearly wrong, return [].
Answer with ONLY a JSON array like [{"ids":["e3","e7"],"problem":"max 16 words, plain English","fix":"max 16 words","confidence":0.0-1.0}].` }
      ], 600, true);
      const m = text.match(/\[[\s\S]*\]/);
      let list = []; try { list = JSON.parse(m ? m[0] : text); } catch (e) { list = []; }
      const issues = (Array.isArray(list) ? list : []).filter(x => x && Array.isArray(x.ids) && x.problem).slice(0, 3)
        .map(x => ({ ids: x.ids.slice(0, 6).map(i => String(i).slice(0, 6)), problem: String(x.problem).slice(0, 160), fix: String(x.fix || "").slice(0, 160), confidence: Math.max(0, Math.min(1, +x.confidence || 0)) }));
      return json({ issues }, 200, h);
    }

    if (url.pathname === "/bug" && req.method === "POST") {
      /* only the app itself sends these (a browser always says where it's from) */
      const o = req.headers.get("Origin"); if (o && !ALLOW.includes(o)) return json({ error: "not allowed" }, 403, h);
      return json(await bugTake(env, await req.json().catch(() => ({}))), 200, h);
    }
    if (url.pathname === "/bugs" || url.pathname === "/bugs/ack") {
      if (!env.BUG_KEY || !env.NUDGE) return json({ error: "reports aren't set up (BUG_KEY / NUDGE)" }, 503, h);
      if (!sameKey(req.headers.get("Authorization") || "", "Bearer " + env.BUG_KEY)) return json({ error: "no" }, 401, h);
      if (url.pathname === "/bugs") return json({ bugs: await bugList(env) }, 200, h);
      if (req.method === "POST") return json(await bugAck(env, await req.json().catch(() => ({}))), 200, h);
    }

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

    /* v9: "the story so far" up to where the player's own note says they are (no spoilers past that point) */
    if (url.pathname === "/recap" && req.method === "POST") {
      if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
      let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
      const title = String(b && b.title || "").slice(0, 120), note = String(b && b.note || "").slice(0, 240);
      if (!title || !note) return json({ error: "need a title and a note" }, 400, h);
      const earlier = (Array.isArray(b.earlier) ? b.earlier : []).slice(-5).map(x => "- " + String(x).slice(0, 200)).join("\n");
      const text = await ask(env, [
        { role: "system", content: "You are a video game story expert and a careful, spoiler-free friend. You recap the plot of a game ONLY up to the point the player has reached, never past it. If you don't know the game's story well, or can't tell where the player's note sits in it, you say so and keep to the opening setup. You never invent characters or events. You answer with JSON only." },
        { role: "user", content: `Game: "${title}"${b.year ? " (" + String(b.year).slice(0, 4) + ")" : ""}${b.platform ? ", played on " + String(b.platform).slice(0, 30) : ""}.
${b.about ? "Official description: " + String(b.about).replace(/<[^>]+>/g, " ").slice(0, 600) + "\n" : ""}${b.hours ? "They've played about " + Math.round(+b.hours) + " hours.\n" : ""}${earlier ? "Their earlier notes (oldest first):\n" + earlier + "\n" : ""}Their latest note about where they are: "${note}"

Write "the story so far": what has happened in the plot up to that point, so they can pick the game up again. 4 to 6 short sentences, second person ("You've just..."), plain words, main characters by name, end with what they were about to do. Absolutely nothing that happens after their point.
Answer with ONLY JSON: {"recap":"...","where":"max 8 words: where they are in the story","sure":0.0-1.0 how sure you are about the story and their place in it}` }
      ], 600, true);
      const m = text.match(/\{[\s\S]*\}/);
      let o = null; try { o = JSON.parse(m ? m[0] : text); } catch (e) { const r = text.match(/"recap"\s*:\s*"([^"]+)"/); o = r ? { recap: r[1], sure: .4 } : null; }
      if (!o || typeof o.recap !== "string" || o.recap.trim().length < 20) return json({ error: "no recap" }, 502, h);
      return json({ recap: o.recap.trim().slice(0, 900), where: String(o.where || "").trim().slice(0, 80), sure: Math.max(0, Math.min(1, +o.sure || 0)) }, 200, h);
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
      await env.NUDGE.put("sub:" + id, JSON.stringify({ sub: { endpoint: sub.endpoint, keys: { p256dh: sub.keys.p256dh, auth: sub.keys.auth } }, plan, upd: b.upd !== false, t: Date.now() }), { expirationTtl: SUB_TTL });
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
/* Urgency high: on Android, "normal" pushes wait while the phone dozes (screen off) and only show up when it wakes,
   often right as the app is opened. Every push we send becomes a visible notification, so high is allowed. */
async function webPush(env, sub, msg, ttl) {
  const body = await encrypt(sub, JSON.stringify(msg));
  return fetch(sub.endpoint, { method: "POST", body, headers: {
    "Content-Encoding": "aes128gcm", "Content-Type": "application/octet-stream", TTL: String(ttl || 43200), Urgency: "high",
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
      else await env.NUDGE.put(k.name, JSON.stringify({ ...rec, plan: later }), { expirationTtl: SUB_TTL });
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
  if (!env.NUDGE) return { ok: false, why: "no kv" };
  let html = "";
  try { const r = await fetch(APP_PAGE + "?t=" + Date.now(), { cf: { cacheTtl: 0 }, headers: { "Cache-Control": "no-cache" } }); if (!r.ok) return { ok: false, why: "page " + r.status }; html = await r.text(); }
  catch (e) { return { ok: false, why: "page unreachable" }; }
  const m = html.match(/const APP_V = "(\d+)"/); if (!m) return { ok: false, why: "no APP_V" };
  const v = +m[1], seen = +(await env.NUDGE.get("appv") || 0);
  if (!seen) { await env.NUDGE.put("appv", String(v)); return { ok: true, v, sent: 0, first: true }; }   /* first run: just remember it */
  if (v <= seen) return { ok: true, v, sent: 0, already: true };
  await env.NUDGE.put("appv", String(v));
  /* every version since the last one announced gets its own ping (two releases between checks → two pings),
     each with its own tag so a newer one never replaces an older one on the phone. A version with no
     WHATS_NEW entry wasn't a real release, except the live one, which is always announced. */
  const msgs = [];
  for (let x = Math.max(seen + 1, v - 9); x <= v; x++) {
    const say = whatsNew(html, x);
    if (say || x === v) msgs.push({ title: "✨ Play next v" + x + " is here", body: say || "Open the app and Nexi will show you what's new.", tag: "update-" + x, url: "./?nudge=update" });
  }
  let cursor, sent = 0;
  do {
    const page = await env.NUDGE.list({ prefix: "sub:", cursor });
    for (const k of page.keys) {
      const rec = await env.NUDGE.get(k.name, "json"); if (!rec || rec.upd === false) continue;
      for (const msg of msgs) {
        /* UPD_TTL: a phone that's off for a while still gets it when it comes back */
        try { const r = await webPush(env, rec.sub, msg, UPD_TTL); if (r.status === 404 || r.status === 410) { await env.NUDGE.delete(k.name); break; } else if (r.ok) sent++; } catch (e) {}
      }
    }
    cursor = page.list_complete ? null : page.cursor;
  } while (cursor);
  return { ok: true, v, sent };
}

/* ---- problem reports: the app notices something broke and says so; GitHub files each one as an issue ----
   One KV record per problem (bug:<sig>): how many times, on which versions and phones, the latest sample.
   `seen` is how many the GitHub job has already filed, so it only posts what's new. */
const BUG_TTL = 45 * 86400, BUG_MAX = 300;
const cut = (v, n) => String(v == null ? "" : v).slice(0, n);
/* v6 cloud backup. vault:<id> = the latest sealed copy; vault:<id>:d0..d6 = the last copy of each of the past 7 days
   (UTC weekday), made when the first write of a new day replaces yesterday's; vault:<id>:k = the copy before a write
   the phone marked keep=1 (it had far fewer games than the last one). Every write and read carries
   "Authorization: Bearer <tok>" (from the restore code); the first write keeps its sha256 and anything after must match. */
async function sha256hex(s) { return [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)))].map(x => x.toString(16).padStart(2, "0")).join(""); }
async function vault(req, env, url, h) {
  if (!env.NUDGE) return json({ error: "No KV binding named NUDGE" }, 500, h);
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  const id = url.searchParams.get("id") || "", tok = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/, "");
  if (!/^[a-f0-9]{24}$/.test(id) || !/^[a-f0-9]{64}$/.test(tok)) return json({ error: "bad code" }, 400, h);
  const th = await sha256hex(tok), key = "vault:" + id, op = url.pathname.slice(7);
  const cur = await env.NUDGE.getWithMetadata(key, "stream"), meta = cur.metadata;
  if (cur.value) try { cur.value.cancel(); } catch (e) {}   /* only the metadata is needed here */
  if (meta && !sameKey(meta.h || "", th)) return json({ error: "wrong code" }, 403, h);
  const pub = m => ({ t: m.t, n: m.n || 0, v: m.v || "", sz: m.sz || 0 });
  if (op === "put" && req.method === "POST") {
    const body = await req.arrayBuffer();
    if (!body.byteLength) return json({ error: "empty" }, 400, h);
    if (body.byteLength > VAULT_MAX) return json({ error: "too big" }, 413, h);
    const now = Date.now();
    if (meta && now - meta.t < 20e3) return json({ error: "too soon", t: meta.t }, 429, h);
    /* first write of a new day: keep the previous copy as that day's snapshot */
    if (meta && new Date(meta.t).toISOString().slice(0, 10) !== new Date(now).toISOString().slice(0, 10)) {
      const old = await env.NUDGE.get(key, "arrayBuffer");
      if (old) await env.NUDGE.put(key + ":d" + new Date(meta.t).getUTCDay(), old, { expirationTtl: VAULT_TTL, metadata: meta });
    }
    /* the phone says this copy lost a lot of games (a wipe, a bad restore): keep the one it replaces */
    if (meta && url.searchParams.get("keep") === "1") {
      const old = await env.NUDGE.get(key, "arrayBuffer");
      if (old) await env.NUDGE.put(key + ":k", old, { expirationTtl: VAULT_TTL, metadata: meta });
    }
    const m = { h: th, t: now, n: Math.max(0, Math.min(1e5, +url.searchParams.get("n") || 0)), v: String(url.searchParams.get("v") || "").slice(0, 8), sz: body.byteLength };
    await env.NUDGE.put(key, body, { expirationTtl: VAULT_TTL, metadata: m });
    return json({ ok: true, t: now }, 200, h);
  }
  if (op === "list") {
    if (!meta) return json({ list: [] }, 200, h);
    const ks = (await env.NUDGE.list({ prefix: key + ":" })).keys.filter(k => k.metadata && sameKey(k.metadata.h || "", th));
    return json({ list: [{ k: "latest", ...pub(meta) }, ...ks.map(k => ({ k: k.name.slice(key.length + 1), ...pub(k.metadata) }))].sort((a, b) => b.t - a.t) }, 200, h);
  }
  if (op === "get") {
    const k = url.searchParams.get("k") || "latest";
    if (k !== "latest" && !/^(d[0-6]|k)$/.test(k)) return json({ error: "bad copy" }, 400, h);
    const r = await env.NUDGE.getWithMetadata(k === "latest" ? key : key + ":" + k, "arrayBuffer");
    if (!r.value || !r.metadata) return json({ error: "no backup with that code" }, 404, h);
    if (!sameKey(r.metadata.h || "", th)) return json({ error: "wrong code" }, 403, h);
    return new Response(r.value, { headers: { ...h, "Content-Type": "application/octet-stream", "Cache-Control": "no-store" } });
  }
  if (op === "del" && req.method === "POST") {
    if (meta) await Promise.all([key, key + ":k", ...[0, 1, 2, 3, 4, 5, 6].map(d => key + ":d" + d)].map(k => env.NUDGE.delete(k)));
    return json({ ok: true }, 200, h);
  }
  return json({ error: "not found" }, 404, h);
}
/* ===== v9: Steam's own tags (the genres players vote on: "Souls-like", "Metroidvania", "JRPG"…) and Steam's "More like this".
   Store search finds the app (pxSteamFind); IStoreBrowseService/GetItems gives names + weighted tag ids for many apps in one
   call (no key); IStoreService/GetTagList turns ids into names (kept in KV a week). "More like this" = the app ids on
   store.steampowered.com/recommended/morelike/app/<id>/ in Steam's order. ===== */
let ST_NAMES = null;
async function stTagNames(env) {
  if (ST_NAMES) return ST_NAMES;
  try { const c = env.NUDGE && await env.NUDGE.get("steam:tags", "json"); if (c && Date.now() - c.t < 7 * 864e5) return (ST_NAMES = c.m); } catch (e) {}
  const r = await fetch("https://api.steampowered.com/IStoreService/GetTagList/v1/?language=english", { headers: PX_UA });
  const j = await r.json().catch(() => ({})), m = {};
  for (const t of (j.response && j.response.tags) || []) m[t.tagid] = t.name;
  if (!Object.keys(m).length) throw new Error("Steam tag list empty");
  ST_NAMES = m;
  if (env.NUDGE) try { await env.NUDGE.put("steam:tags", JSON.stringify({ t: Date.now(), m }), { expirationTtl: 30 * 86400 }); } catch (e) {}
  return m;
}
async function stItems(env, ids, cc) {
  const names = await stTagNames(env), out = {};
  for (let i = 0; i < ids.length; i += 40) {
    const input = { ids: ids.slice(i, i + 40).map(appid => ({ appid })), context: { language: "english", country_code: cc.toUpperCase(), steam_realm: 1 },
      data_request: { include_basic_info: true, include_tag_count: 15 } };
    const r = await fetch("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=" + encodeURIComponent(JSON.stringify(input)), { headers: PX_UA });
    const j = await r.json().catch(() => ({}));
    for (const it of (j.response && j.response.store_items) || []) {
      if (!it.success || !it.appid) continue;
      out[it.appid] = { id: it.appid, name: it.name || "", tags: (it.tags || []).sort((a, b) => b.weight - a.weight).map(t => names[t.tagid]).filter(Boolean),
        desc: String((it.basic_info || {}).short_description || "").replace(/\s+/g, " ").slice(0, 160), type: it.type || 0 };
    }
  }
  return out;
}
async function stMore(id) {
  const r = await fetch(`https://store.steampowered.com/recommended/morelike/app/${id}/?l=english`, { headers: { ...PX_UA, Cookie: "birthtime=0; lastagecheckage=1-0-1990; mature_content=1; wants_mature_content=1" } });
  const t = await r.text();
  return [...new Set([...t.matchAll(/data-ds-appid="(\d+)"/g)].map(x => +x[1]))].filter(x => x !== +id);
}
async function stRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const cc = pxCC(b && b.cc), res = {};
  const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 30).map(x => ({ k: String(x.k || "").slice(0, 24), t: String(x.t || "").slice(0, 100), st: Number.isFinite(+x.st) ? Math.trunc(+x.st) : 0 })).filter(x => x.k && x.t);
  let n = 0;
  for (const it of items) { if (it.st !== 0) continue; if (n++ >= 8) { it.st = -2; continue; }
    try { const f = await pxSteamFind(cc, it.t); it.st = f ? f.id : -1; if (!f) res[it.k] = { none: 1 }; } catch (e) { it.st = -2; res[it.k] = { err: String(e.message || e).slice(0, 60) }; } }
  const ids = items.filter(it => it.st > 0).map(it => it.st);
  const more = Number.isFinite(+b.more) && +b.more > 0 ? Math.trunc(+b.more) : 0;
  let moreIds = []; if (more) try { moreIds = (await stMore(more)).slice(0, 18); } catch (e) {}
  const info = ids.length || moreIds.length ? await stItems(env, [...new Set(ids.concat(moreIds, more ? [more] : []))], cc) : {};
  for (const it of items) if (it.st > 0) res[it.k] = info[it.st] ? { st: it.st, name: info[it.st].name, tags: info[it.st].tags.slice(0, 15) } : { st: it.st, tags: [] };
  const out = { ok: true, res };
  if (more) out.more = { of: info[more] ? { name: info[more].name, tags: info[more].tags } : null, games: moreIds.map(x => info[x]).filter(x => x && x.type === 0 && x.name).map(x => ({ id: x.id, name: x.name, tags: x.tags.slice(0, 8), desc: x.desc })) };
  return json(out, 200, h);
}
/* ===== v11: "Reviews are in". Steam's player reviews for a list of games: {items: [{k, t, st}]} → res[k] = {st, d (Steam's
   words, e.g. "Very Positive"), p (% positive), n (reviews)} | {none: 1} (not on Steam) | {err}. A title with no Steam id is
   looked up first (≤8 a call, like /steam); ≤24 games a call (one request each, Workers allow ~50). ===== */
async function rvSteam(id) {
  const r = await fetch(`https://store.steampowered.com/appreviews/${id}?json=1&language=all&purchase_type=all&num_per_page=0&filter=summary`, { headers: PX_UA });
  const j = await r.json().catch(() => null), q = j && j.success === 1 && j.query_summary;
  if (!q) throw new Error("Steam reviews " + r.status);
  const n = +q.total_reviews || 0, pos = +q.total_positive || 0;
  return { st: +id, d: String(q.review_score_desc || "").slice(0, 40), p: n ? Math.round(pos / n * 100) : null, n };
}
async function rvRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const cc = pxCC(b && b.cc), res = {};
  const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 24).map(x => ({ k: String(x.k || "").slice(0, 24), t: String(x.t || "").slice(0, 100), st: Number.isFinite(+x.st) ? Math.trunc(+x.st) : 0 })).filter(x => x.k && x.t);
  let n = 0;
  for (const it of items) { if (it.st !== 0) continue; if (n++ >= 8) { it.st = -2; continue; }
    try { const f = await pxSteamFind(cc, it.t); it.st = f ? f.id : -1; if (!f) res[it.k] = { none: 1 }; } catch (e) { it.st = -2; res[it.k] = { err: String(e.message || e).slice(0, 60) }; } }
  await Promise.all(items.filter(it => it.st > 0).map(async it => { try { res[it.k] = await rvSteam(it.st); } catch (e) { res[it.k] = { st: it.st, err: String(e.message || e).slice(0, 60) }; } }));
  for (const it of items) if (it.st === -1 && !res[it.k]) res[it.k] = { none: 1 };
  return json({ ok: true, res }, 200, h);
}
/* ===== v11: hours to beat. {items: [{k, t, year, plat}]} (≤12) → res[k] = {m (main story), x (main + extras), c (everything),
   sure 0–1}, from the big model's knowledge of typical play times (HowLongToBeat-style averages). It's told to say
   sure: 0 for games it doesn't know or that aren't out, so the app only fills what it can trust. ===== */
async function hbRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 12).map(x => ({ k: String(x.k || "").slice(0, 24), t: String(x.t || "").replace(/["\n]/g, " ").slice(0, 100),
    y: /^\d{4}$/.test(String(x.year || "")) ? String(x.year) : "", p: String(x.plat || "").replace(/["\n]/g, " ").slice(0, 40) })).filter(x => x.k && x.t);
  if (!items.length) return json({ error: "no games" }, 400, h);
  const list = items.map((x, i) => `${i + 1}. "${x.t}"${x.y ? " (" + x.y + ")" : ""}${x.p ? " on " + x.p : ""}`).join("\n");
  const text = await ask(env, [
    { role: "system", content: "You know video games very well, including how long they take to beat (the community averages HowLongToBeat lists). You never guess wildly: if you don't know a game, or it isn't released yet, you give sure 0." },
    { role: "user", content: `For each game, the typical hours to beat it:
- m: main story only
- x: main story plus the main extras / side content
- c: everything (completionist)
- sure: 0.0-1.0, how sure you are these are right for THIS exact game (0 if you don't know it or it's unreleased)
Live-service, endless or multiplayer-only games: m, x, c = 0 and sure 0.

${list}

Answer with ONLY JSON, one entry per game in the same order: [{"n":1,"m":0,"x":0,"c":0,"sure":0.0}, ...]` }
  ], 700, true);
  const m = text.match(/\[[\s\S]*\]/);
  let arr = []; try { arr = JSON.parse(m ? m[0] : text); } catch (e) {
    arr = [...text.matchAll(/\{[^{}]*\}/g)].map(x => { try { return JSON.parse(x[0]); } catch (e) { return null; } }).filter(Boolean); }
  if (!Array.isArray(arr)) arr = [];
  const res = {}, hr = v => { v = +v; return Number.isFinite(v) && v > 0 && v < 2000 ? Math.round(v * 2) / 2 : 0; };
  arr.forEach((o, i) => { if (!o || typeof o !== "object") return; const it = items[(+o.n || i + 1) - 1]; if (!it || res[it.k]) return;
    let a = hr(o.m), x = hr(o.x), c = hr(o.c); const sure = Math.max(0, Math.min(1, +o.sure || 0));
    if (!a) { res[it.k] = { sure: 0 }; return; }
    x = Math.max(a, x || 0); c = Math.max(x, c || 0);
    res[it.k] = { m: a, x: x > a ? x : 0, c: c > x ? c : 0, sure }; });
  for (const it of items) res[it.k] ||= { sure: 0 };
  return json({ ok: true, res }, 200, h);
}
/* ===== v7: price alerts. Steam's store API (no key) and the PlayStation Store's public pages, read here because a
   browser can't (no CORS). POST /prices checks a list now; with an id (the phone's nudge id) the list is also kept as
   px:<id> and the cron re-checks it about once a day and pushes a notification when a price drops. ===== */
const PX_TTL = 60 * 86400, PX_UA = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en" };
/* v227: short names people type ("GTA 6", "FF7 Rebirth", "COD") and Roman numerals ("VI") mean the same game as the store's
   full name, so both sides are spelled out the same way before comparing, and searches also try the spelled-out title */
const PX_ABBR = { gta: "grand theft auto", ff: "final fantasy", cod: "call of duty", rdr: "red dead redemption", tlou: "the last of us",
  mgs: "metal gear solid", kh: "kingdom hearts", nfs: "need for speed", gow: "god of war", dmc: "devil may cry", smt: "shin megami tensei",
  bg: "baldurs gate", hzd: "horizon zero dawn", ac: "assassins creed", mhw: "monster hunter world", re: "resident evil" };
const PX_ROMAN = { i: 1, ii: 2, iii: 3, iv: 4, v: 5, vi: 6, vii: 7, viii: 8, ix: 9, x: 10, xi: 11, xii: 12, xiii: 13, xiv: 14, xv: 15, xvi: 16 };
/* "GTA 6" → "grand theft auto 6", "FF7" → "final fantasy 7"; only a whole first word is expanded ("re" only when a number
   follows, so "Returnal"/"Resident…" are untouched) */
const pxExpand = t => String(t || "").replace(/^\s*([A-Za-z]{2,4})(\d{0,2})(?=\b|\d)/, (all, w, n) => {
  const k = w.toLowerCase(), full = PX_ABBR[k]; if (!full || full === k) return all;
  if ((k === "re" || k === "ac" || k === "bg") && !n && !/^\s*[A-Za-z]{2,4}\s+\d/.test(t)) return all;
  return full + (n ? " " + n : ""); });
const pxNum = s => s.replace(/\b(i{1,3}|iv|vi{0,3}|ix|xi{0,3}|xiv|xv|xvi)\b/g, w => PX_ROMAN[w] != null ? String(PX_ROMAN[w]) : w);
const pxNorm0 = s => String(s || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/\bps4\s*(&|and)\s*ps5\b/g, " ").replace(/[®™©]/g, "").replace(/&/g, " and ").replace(/[^a-z0-9]+/g, " ").replace(/\b(the|edition|standard|digital|ps4|ps5|game)\b/g, " ").replace(/\s+/g, " ").trim();
const pxNorm = s => pxNum(pxNorm0(pxExpand(s).replace(/['’]/g, ""))).replace(/\s+/g, " ").trim();
/* every word of your title must be in the store's name (so "Final Fantasy VII Rebirth" never matches plain "Final Fantasy VII",
   nor "Death Stranding 2" the first game); extra words (editions, subtitles) cost a little each */
function pxSame(a, b) {
  const x = pxNorm(a), y = pxNorm(b); if (!x || !y) return 0; if (x === y) return 1;
  const A = x.split(" "), B = new Set(y.split(" ")), hit = A.filter(w => B.has(w)).length, cov = hit / A.length;
  const jac = hit / new Set([...A, ...B]).size;
  return cov < 1 ? jac * cov * cov : Math.max(jac, .8 - Math.min(.3, (B.size - A.length) * .06));
}
const pxCC = cc => /^[a-z]{2}$/.test(String(cc || "").toLowerCase()) ? String(cc).toLowerCase() : "us";
/* Steam: one call prices up to 50 apps (only the price_overview filter allows several ids) */
async function pxSteamPrices(cc, ids) {
  const out = {};
  for (let i = 0; i < ids.length; i += 50) {
    const r = await fetch(`https://store.steampowered.com/api/appdetails?appids=${ids.slice(i, i + 50).join(",")}&cc=${cc}&filters=price_overview`, { headers: PX_UA });
    const j = await r.json().catch(() => null); if (!j) continue;
    for (const id of ids.slice(i, i + 50)) {
      const d = j[id]; if (!d) continue;
      if (!d.success) { out[id] = { nosale: 1 }; continue; }   /* Steam won't sell it in this region */
      const p = d.data && d.data.price_overview;
      out[id] = p ? { cur: p.currency, base: p.initial, now: p.final, pct: p.discount_percent || 0, baseF: p.initial_formatted || p.final_formatted, nowF: p.final_formatted }
        : { nop: 1 };   /* listed with no price: not out yet, or free */
    }
  }
  return out;
}
async function pxSteamFind(cc, title) {
  const full = pxExpand(title);
  for (const q of full !== title ? [title, full] : [title]) {
    const r = await fetch(`https://store.steampowered.com/api/storesearch/?term=${encodeURIComponent(String(q).slice(0, 80))}&cc=${cc}&l=english`, { headers: PX_UA });
    const j = await r.json().catch(() => null), items = (j && j.items || []).filter(x => x.type === "app" || !x.type);
    let best = null, bs = 0;
    for (const x of items.slice(0, 10)) { const s = pxSame(title, x.name); if (s > bs) { bs = s; best = x; } }
    if (best && bs >= .55) return { id: best.id, name: best.name };
  }
  return null;
}
/* PlayStation Store (rebuilt in v214 from real responses, see .github/scripts/psprobe.mjs). Its web search is drawn in the
   browser, so: search = the store's older "tumbler" search (product ids + names); price = the store's own GraphQL, product
   → concept (metGetProductById) → the concept's pricing (metGetPricingDataByConceptId), whose GameCTAs carry the buy price,
   a sale's end time, PS Plus member prices, and "Included" for games in the PS Plus Game Catalog (tierNumber 2 = Extra,
   3 = Premium) and Premium game trials. The concept id comes back to the phone, so later checks need one call. */
const PS_ID = /[A-Z]{2}\d{4}-[A-Z]{4}\d{5}_00-[A-Z0-9]{16}/;
const PSQ = { metGetProductById: "a128042177bd93dd831164103d53b73ef790d56f51dae647064cb8f9d9fc9d1a", metGetPricingDataByConceptId: "abcb311ea830e679fe2b697a27f755764535d825b24510ab1239a4ca3092bd09",
  /* v12: the store website's own search (recorded from store.playstation.com/<locale>/search/<term> in a real browser by
     .github/scripts/pssearchprobe.mjs); the old tumbler search doesn't list some new games (GTA VI, FF VII Rebirth) */
  getSearchResults: "4df6284f982e57bec70f23c77e2c219dc792eb19af7fb3d3a81767aa3f1958aa" };
async function psGql(cc, op, vars) {
  const u = `https://web.np.playstation.com/api/graphql/v1/op?operationName=${op}&variables=${encodeURIComponent(JSON.stringify(vars))}&extensions=${encodeURIComponent(JSON.stringify({ persistedQuery: { version: 1, sha256Hash: PSQ[op] } }))}`;
  const r = await fetch(u, { headers: { ...PX_UA, "x-psn-store-locale-override": "en-" + cc.toUpperCase(), "content-type": "application/json" } });
  if (!r.ok) throw new Error("PS Store said " + r.status);
  return r.json();
}
const PS_JUNK = /bundle|soundtrack|season pass|upgrade|\bpack\b|\bdlc\b|add-?on|demo|\btrial\b|currency|coins|points|avatar|theme/i;
const PS_ED = /deluxe|complete|ultimate|gold|premium|definitive|collector|digital|special|anniversary|director'?s cut|edition/i;
async function pxPsFind(cc, title) {
  /* the website's search first (it knows every game); the old tumbler search if it fails or finds nothing */
  try { const f = await pxPsSearchWeb(cc, title) || (pxExpand(title) !== title ? await pxPsSearchWeb(cc, pxExpand(title), title) : null); if (f) return f; } catch (e) {}
  return pxPsFindOld(cc, title);
}
async function pxPsSearchWeb(cc, q, title) {
  title = title || q;
  const j = await psGql(cc, "getSearchResults", { countryCode: cc.toUpperCase(), languageCode: "en", nextCursor: "", pageOffset: 0, pageSize: 24, searchTerm: String(q).slice(0, 80) });
  const res = (((j || {}).data || {}).universalSearch || {}).results || [];
  let best = null, bs = 0;
  for (const r of res) {
    const name = r.name || r.invariantName || "", cls = String(r.localizedStoreDisplayClassification || r.storeDisplayClassification || "");
    if (!name || !r.id) continue;
    const prod = PS_ID.test(r.id), con = /^\d{4,12}$/.test(String(r.id));
    if (!prod && !con) continue;
    if (/add-?on|currency|theme|avatar|season pass|demo|soundtrack|application/i.test(cls)) continue;
    let sc = pxSame(title, name);
    if (PS_JUNK.test(name) && !PS_JUNK.test(title)) sc -= .5;
    if (PS_ED.test(name) && !PS_ED.test(title)) sc -= .08;
    if (sc > bs) { bs = sc; best = prod ? { id: r.id, name } : { cid: String(r.id), name }; }
  }
  return best && bs >= .55 ? best : null;
}
async function pxPsFindOld(cc, title) {
  const clean = String(title).replace(/[™®©]/g, "").replace(/['’]/g, "").replace(/[:\-–—]/g, " ").replace(/\s+/g, " ").trim();
  const before = String(title).split(":")[0].trim();
  const full = pxExpand(title);
  for (const q of [...new Set([String(title), full !== title ? full : "", clean !== title ? clean : before])].filter(Boolean).slice(0, 3)) {
    const f = await pxPsSearch(cc, q, title); if (f) return f;
  }
  return null;
}
async function pxPsSearch(cc, q, title) {
  const r = await fetch(`https://store.playstation.com/store/api/chihiro/00_09_000/tumbler/${cc.toUpperCase()}/en/999/${encodeURIComponent(String(q).slice(0, 80))}?suggested_size=10&mode=game`, { headers: PX_UA });
  if (!r.ok) throw new Error("PS Store said " + r.status);
  const j = await r.json().catch(() => ({})), seen = new Set();
  let best = null, bs = 0;
  for (const l of j.links || []) {
    if (l.container_type !== "product" || !PS_ID.test(l.id || "") || seen.has(l.id)) continue;
    seen.add(l.id);
    let s = pxSame(title, l.name);
    if (PS_JUNK.test(l.name) && !PS_JUNK.test(title)) s -= .5;
    if (PS_ED.test(l.name) && !PS_ED.test(title)) s -= .08;   /* the plain game before its editions, but an edition beats nothing */
    if (s > bs) { bs = s; best = l; }
  }
  return best && bs >= .55 ? { id: best.id, name: best.name } : null;
}
function psCtas(j) {
  const out = [], walk = o => { if (o && typeof o === "object") { if (o.__typename === "GameCTA") out.push(o); for (const k in o) walk(o[k]); } };
  walk(j);
  return out.map(c => { const P = (c.action || {}).param || [], v = n => ((P.find(x => x.name === n) || {}).values || [])[0];
    return { type: c.type || "", sku: v("skuId") || "", tier: +v("tierNumber") || 0, p: c.price || {} }; });
}
async function pxPsPrice(cc, id, cid) {
  let name = "";
  if (!cid) {
    const pj = await psGql(cc, "metGetProductById", { productId: id }), pr = pj && pj.data && pj.data.productRetrieve;
    if (!pr) return { nosale: 1 };   /* not sold in this region's store */
    cid = pr.concept && pr.concept.id; name = pr.name || "";
    if (!cid) return { nosale: 1 };
  }
  const cj = await psGql(cc, "metGetPricingDataByConceptId", { conceptId: cid });
  if (!(cj && cj.data && cj.data.conceptRetrieve)) return { nosale: 1 };
  const all = psCtas(cj), mine = all.filter(c => c.sku.startsWith(id)), C = mine.length ? mine : all;
  const buy = C.filter(c => !c.p.isTiedToSubscription && /ADD_TO_CART|PREORDER|DOWNLOAD/.test(c.type) && c.p.basePriceValue != null)
    .sort((a, b) => (a.p.discountedValue ?? a.p.basePriceValue) - (b.p.discountedValue ?? b.p.basePriceValue))[0];
  const cat = C.find(c => /CATALOG/.test(c.type)), trial = C.find(c => /TRIAL/.test(c.type));
  const plus = C.filter(c => c.p.isTiedToSubscription && !/CATALOG|TRIAL/.test(c.type) && c.p.discountedValue > 0).sort((a, b) => a.p.discountedValue - b.p.discountedValue)[0];
  const out = { id, cid, name, plus: cat ? (cat.tier || 2) : 0, trial: trial ? (trial.tier || 3) : 0 };
  if (buy) {
    const b = buy.p, base = +b.basePriceValue, now = b.discountedValue != null ? +b.discountedValue : base;
    if (!id) { const m = String(buy.sku || "").match(PS_ID); if (m) out.id = m[0]; }
    Object.assign(out, { cur: b.currencyCode || "", base, now, pct: base > now ? Math.round((1 - now / base) * 100) : 0,
      baseF: b.basePrice || "", nowF: /\d/.test(b.discountedPrice || "") ? b.discountedPrice : (b.basePrice || ""), end: +b.endTime || 0, pre: /PREORDER/.test(buy.type) ? 1 : 0 });
    if (b.isFree || (base === 0 && /DOWNLOAD/.test(buy.type))) { out.free = 1; out.base = out.now = 0; }
  } else if (!cat) out.nop = 1;
  if (plus) Object.assign(out, { plusNow: +plus.p.discountedValue, plusNowF: plus.p.discountedPrice || "" });
  return out;
}
/* ===== v9: Nintendo eShop. Search: Nintendo of America's store search (Algolia, the key their own site ships) for the
   Americas, Nintendo of Europe's search for Europe/UK/Australia/NZ/South Africa (their game ids work there). Prices: Nintendo's
   price service for that country (up to 50 ids a call). There's no eShop in the Gulf, so the phone sends the eShop it uses (nscc).
   Checked against the real services with .github/scripts/nsprobe.mjs. ===== */
const NS_AM = new Set(["us", "ca", "mx", "br", "ar", "cl", "co", "pe"]);
const NS_EU = new Set(["gb", "de", "fr", "it", "es", "nl", "be", "pt", "at", "ch", "ie", "pl", "se", "dk", "no", "fi", "cz", "gr", "hu", "ro", "sk", "si", "hr", "bg", "lu", "za", "au", "nz"]);
const nsCC = cc => { cc = pxCC(cc); return NS_AM.has(cc) || NS_EU.has(cc) ? cc : "us"; };
const NS_JUNK = /upgrade pack|expansion pass|season pass|\bdlc\b|bundle|soundtrack|\bbgm\b|\bset\b|costume|\bdemo\b|add-?on|\bpack\b/i;
async function pxNsFind(cc, title, sw2) {
  const want = t => { let s = pxSame(title, String(t).replace(/[–—-]\s*Nintendo Switch\s*2 Edition/i, "")); if (NS_JUNK.test(t) && !NS_JUNK.test(title)) s -= .6; return s; };
  if (NS_AM.has(cc)) {
    const r = await fetch("https://U3B6GR4UA3-dsn.algolia.net/1/indexes/store_game_en_us/query", { method: "POST",
      headers: { "X-Algolia-Application-Id": "U3B6GR4UA3", "X-Algolia-API-Key": "a29c6927638bfd8cee23993e51e721c9", "Content-Type": "application/json" },
      body: JSON.stringify({ query: String(title).replace(/[™®©]/g, "").slice(0, 80), hitsPerPage: 12 }) });
    if (!r.ok) throw new Error("eShop search said " + r.status);
    const j = await r.json().catch(() => ({}));
    let best = null, bs = 0;
    for (const x of j.hits || []) {
      if (!x.nsuid || (x.dlcType && x.dlcType !== "null")) continue;
      let sc = want(x.title); const two = /Switch 2/.test(x.platform || "") || /Switch\s*2 Edition/i.test(x.title);
      if (two !== !!sw2) sc -= .05;   /* the edition for your console first, the other one if that's all there is */
      if (sc > bs) { bs = sc; best = x; }
    }
    return best && bs >= .55 ? { id: String(best.nsuid), name: String(best.title).replace(/[™®]/g, ""), url: best.url ? "https://www.nintendo.com" + best.url : "" } : null;
  }
  const r = await fetch(`https://searching.nintendo-europe.com/en/select?q=${encodeURIComponent(String(title).replace(/[™®©]/g, "").slice(0, 80))}&fq=type:GAME%20AND%20system_type:nintendoswitch*&rows=12&wt=json`, { headers: PX_UA });
  if (!r.ok) throw new Error("eShop search said " + r.status);
  const j = await r.json().catch(() => ({}));
  let best = null, bs = 0;
  for (const d of (j.response && j.response.docs) || []) {
    const ids = d.nsuid_txt || []; if (!ids.length) continue;
    let sc = want(d.title); const two = /Switch 2/.test(String(d.system_names_txt || "")) || /Switch\s*2 Edition/i.test(d.title);
    if (two !== !!sw2) sc -= .05;
    if (sc > bs) { bs = sc; best = d; }
  }
  if (!best) return null;
  /* a Switch 2 Edition can list the upgrade's id first: the game's own id starts 7001 */
  const id = (best.nsuid_txt.find(x => /^7001/.test(x)) || best.nsuid_txt[0]);
  return bs >= .55 ? { id: String(id), name: best.title, url: best.url ? "https://www.nintendo.com" + best.url : "" } : null;
}
async function pxNsPrices(cc, ids) {
  const out = {};
  for (let i = 0; i < ids.length; i += 50) {
    const r = await fetch(`https://api.ec.nintendo.com/v1/price?country=${cc.toUpperCase()}&lang=en&ids=${ids.slice(i, i + 50).join(",")}`, { headers: PX_UA });
    const j = await r.json().catch(() => null); if (!j) continue;
    for (const p of j.prices || []) {
      const id = String(p.title_id);
      if (p.sales_status === "not_found" || p.sales_status === "sales_termination") { out[id] = { nosale: 1 }; continue; }
      const reg = p.regular_price, dis = p.discount_price;
      if (!reg) { out[id] = { nop: 1 }; continue; }   /* listed, no price yet (pre-release) */
      const base = +reg.raw_value, now = dis ? +dis.raw_value : base;
      out[id] = { cur: reg.currency, base, now, pct: base > now ? Math.round((1 - now / base) * 100) : 0, baseF: reg.amount, nowF: dis ? dis.amount : reg.amount,
        end: dis && dis.end_datetime ? Date.parse(dis.end_datetime) || 0 : 0, free: base === 0 ? 1 : 0, pre: p.sales_status === "preorder" ? 1 : 0 };
    }
  }
  return out;
}
/* check a list: [{k, t: title, st: steam app id | 0 = look it up | -1 = don't, ps: product id | "" = look it up | "-" = don't}]
   at most 8 Steam lookups and 10 PS games per call (up to 3 PS calls each the first time; Workers allow ~50 outside requests) */
/* what the phone would pay: the PS Plus member price counts when they have PS Plus */
const pxEff = (p, tier) => !p || p.now == null ? null : tier >= 1 && p.plusNow != null ? Math.min(p.now, p.plusNow) : p.now;
async function pxCheck(cc, items, nscc) {
  cc = pxCC(cc); nscc = nsCC(nscc || cc);
  const res = {}, look = { st: 0, ps: 0, ns: 0 };
  for (const it of items) res[it.k] = {};
  for (const it of items) {
    if (it.st === 0 && look.st < 8) { look.st++; try { const f = await pxSteamFind(cc, it.t); it.st = f ? f.id : -1; if (f) res[it.k].stName = f.name; else res[it.k].st = { none: 1 }; } catch (e) { res[it.k].st = { err: String(e.message || e).slice(0, 60) }; } }
  }
  const ids = items.filter(it => +it.st > 0).map(it => +it.st);
  let sp = {}; try { sp = ids.length ? await pxSteamPrices(cc, ids) : {}; } catch (e) {}
  for (const it of items) if (+it.st > 0) res[it.k].st = Object.assign({ id: +it.st, name: res[it.k].stName || "" }, sp[+it.st] || { nosale: 1 });
  for (const it of items) {
    if (it.ps === "-" || look.ps >= 8) continue;
    look.ps++;
    try {
      let id = it.ps, name = "";
      /* v227: a concept with no product id (the phone found the game's store page some other way, e.g. RAWG's link) is
         priced straight from the concept: the store's old search doesn't list some new games (GTA VI) */
      let pc = it.pc || "";
      if (!id && !pc) { const f = await pxPsFind(cc, it.t); if (!f) { res[it.k].ps = { none: 1 }; continue; } id = f.id || ""; pc = f.cid || ""; name = f.name; }
      const p = await pxPsPrice(cc, id, pc);
      if (!id) id = p.id || "";
      res[it.k].ps = Object.assign({ id }, p, { id: id || p.id, name: p.name || name });
    } catch (e) { res[it.k].ps = { err: String(e.message || e).slice(0, 60) }; }
  }
  /* Nintendo eShop: look up (≤8 a call), then price every found id in one call */
  for (const it of items) {
    if (it.ns !== "" || look.ns >= 8) continue;
    look.ns++;
    try { const f = await pxNsFind(nscc, it.t, it.sw2); if (!f) { res[it.k].ns = { none: 1 }; it.ns = "-"; continue; } it.ns = f.id; res[it.k].nsF = f; }
    catch (e) { res[it.k].ns = { err: String(e.message || e).slice(0, 60) }; it.ns = "-"; }
  }
  const nids = items.filter(it => it.ns && it.ns !== "-").map(it => it.ns);
  let np = {}; try { np = nids.length ? await pxNsPrices(nscc, nids) : {}; } catch (e) {}
  for (const it of items) if (it.ns && it.ns !== "-") { const f = res[it.k].nsF || {};
    res[it.k].ns = Object.assign({ id: it.ns, name: f.name || "", url: f.url || "", ncc: nscc }, np[it.ns] || { nosale: 1 }); }
  for (const k in res) { delete res[k].stName; delete res[k].nsF; }
  return res;
}
async function pxRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const id = String(b && b.id || ""), okId = /^[A-Za-z0-9_-]{16,40}$/.test(id);
  if (b && b.off) { if (okId && env.NUDGE) await env.NUDGE.delete("px:" + id); return json({ ok: true }, 200, h); }
  const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 40).map(x => ({ k: String(x.k || "").slice(0, 24), t: String(x.t || "").slice(0, 100),
    st: Number.isFinite(+x.st) ? Math.trunc(+x.st) : 0, ps: x.ps === "-" ? "-" : (String(x.ps || "").match(PS_ID) || [""])[0],
    pc: /^\d{4,12}$/.test(String(x.pc || "")) ? String(x.pc) : "",
    ns: x.ns === "-" || x.ns == null ? "-" : /^\d{14}$/.test(String(x.ns)) ? String(x.ns) : "", sw2: !!x.sw2 })).filter(x => x.k && x.t);
  const cc = pxCC(b.cc), nscc = nsCC(b.nscc || cc), tier = Math.max(0, Math.min(3, +b.tier || 0)), res = await pxCheck(cc, items, nscc);
  /* keep the list for the daily check (only what was found), with the prices seen now as the baseline */
  if (okId && env.NUDGE && b.watch) {
    const keep = items.map(it => { const r = res[it.k] || {}, st = r.st && r.st.id ? r.st.id : -1, ps = r.ps && r.ps.id ? r.ps.id : "-", ns = r.ns && r.ns.id ? r.ns.id : "-";
      return { k: it.k, t: it.t, st, ps, ns, pc: r.ps && r.ps.cid || "", last: { st: r.st && r.st.now != null ? r.st.now : null, ps: pxEff(r.ps, tier), ns: r.ns && r.ns.now != null ? r.ns.now : null, plus: r.ps && r.ps.plus || 0 } }; }).filter(x => x.st > 0 || x.ps !== "-" || x.ns !== "-");
    /* the phone sends its list in batches: merge this batch into what's kept, and drop games it no longer watches (b.all) */
    const old = await env.NUDGE.get("px:" + id, "json"), all = Array.isArray(b.all) ? new Set(b.all.map(String)) : null;
    const ks = new Set(keep.map(x => x.k)), was = old && old.cc === cc ? (old.items || []).filter(x => !ks.has(x.k) && (!all || all.has(x.k))) : [];
    await env.NUDGE.put("px:" + id, JSON.stringify({ cc, nscc, tier, min: Math.max(0, Math.min(90, +b.min || 0)), items: was.concat(keep).slice(0, 80), t: old && old.cc === cc ? old.t : Date.now() }), { expirationTtl: PX_TTL, metadata: { t: old && old.cc === cc && old.t ? old.t : Date.now() } });
  }
  return json({ ok: true, cc, nscc, res, at: Date.now() }, 200, h);
}
/* cron: the one watch list checked longest ago (if over 20 h), drops pushed to that phone */
async function pxCron(env) {
  if (!env.NUDGE) return;
  const keys = (await env.NUDGE.list({ prefix: "px:" })).keys.filter(k => !k.metadata || Date.now() - (k.metadata.t || 0) > 20 * 36e5)
    .sort((a, b) => ((a.metadata || {}).t || 0) - ((b.metadata || {}).t || 0));
  const k = keys[0]; if (!k) return;
  const rec = await env.NUDGE.get(k.name, "json"); if (!rec) return;
  const id = k.name.slice(3), sub = await env.NUDGE.get("sub:" + id, "json");
  const res = await pxCheck(rec.cc, rec.items.map(x => ({ k: x.k, t: x.t, st: x.st, ps: x.ps, pc: x.pc || "", ns: x.ns || "-" })), rec.nscc);
  const drops = [], joins = [], tier = rec.tier || 0;
  for (const it of rec.items) {
    const r = res[it.k] || {};
    it.last = it.last || {};
    for (const s of ["st", "ps", "ns"]) {
      const p = r[s]; if (!p) continue;
      if (s === "ps" && p.cid) it.pc = p.cid;
      if (s === "ps" && tier >= 2 && p.plus && p.plus <= tier && !(it.last.plus && it.last.plus <= tier)) joins.push({ it, p });
      if (s === "ps" && p.plus != null) it.last.plus = p.plus || 0;
      const now = s === "ps" ? pxEff(p, tier) : p.now; if (now == null) continue;
      const was = it.last[s];
      if (was != null && now < was && p.pct >= (rec.min || 0)) drops.push({ it, s, p });
      it.last[s] = now;
    }
  }
  await env.NUDGE.put(k.name, JSON.stringify({ ...rec, t: Date.now() }), { expirationTtl: PX_TTL, metadata: { t: Date.now() } });
  if (!sub || !sub.sub) return;
  const PLUS = ["", "Essential", "Extra", "Premium"];
  for (const j of joins.slice(0, 2)) {
    try { await webPush(env, sub.sub, { title: `🎮 ${j.it.t.slice(0, 40)} is in PS Plus ${PLUS[j.p.plus] || "Extra"}`, body: "It's in the Game Catalog now: play it with your subscription, nothing to buy.",
      tag: "plus-" + j.it.k, url: "./?nudge=game&g=" + j.it.k }, 2 * 86400); } catch (e) {}
  }
  /* deal-radar games ("d:<appid>", not in the library) only push at half price or better, and open Deals */
  for (const d of drops.filter(d => !String(d.it.k).startsWith("d:") || d.p.pct >= 50).slice(0, 3)) {
    const store = d.s === "st" ? "Steam" : d.s === "ns" ? "Nintendo eShop" : "PS Store";
    try { await webPush(env, sub.sub, { title: `💸 ${d.it.t.slice(0, 40)}${d.p.pct ? " is " + d.p.pct + "% off" : " got cheaper"}`,
      body: `${store}: ${d.p.nowF || ""}${d.p.baseF && d.p.pct ? " (was " + d.p.baseF + ")" : ""}${String(d.it.k).startsWith("d:") ? " · a game you'd probably love" : ""}`.trim(), tag: "deal-" + d.it.k,
      url: String(d.it.k).startsWith("d:") ? "./?nudge=deals" : "./?nudge=game&g=" + d.it.k }, 2 * 86400); } catch (e) {}
  }
}
function sameKey(a, b) { if (a.length !== b.length) return false; let d = 0; for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i); return d === 0; }
async function bugTake(env, body) {
  if (!env.NUDGE) return { ok: false, why: "no kv" };
  const list = Array.isArray(body.reports) ? body.reports.slice(0, 10) : [];
  let took = 0, count = null;
  for (const r of list) {
    const sig = cut(r.sig, 8); if (!/^[0-9a-f]{8}$/.test(sig)) continue;
    const sample = { kind: cut(r.kind, 16), msg: cut(r.msg, 600), stack: cut(r.stack, 1500), at: cut(r.at, 200), screen: cut(r.screen, 60),
      w: +r.w || 0, h: +r.h || 0, dpr: +r.dpr || 1, dev: cut(r.dev, 40), v: cut(r.v, 8), style: cut(r.style, 12), note: cut(r.note, 800),
      crumbs: (Array.isArray(r.crumbs) ? r.crumbs : []).slice(-10).map(c => cut(c, 60)), t: Date.now() };
    const key = "bug:" + sig, old = await env.NUDGE.get(key, "json");
    if (!old) {
      if (count == null) count = (await env.NUDGE.list({ prefix: "bug:", limit: 1000 })).keys.length;
      if (count >= BUG_MAX) continue;
      count++;
    }
    const add = (a, v) => (a || []).includes(v) ? a : (a || []).concat(v).slice(-8);
    const rec = old ? { ...old, n: old.n + 1, last: sample.t, vs: add(old.vs, sample.v), devs: add(old.devs, sample.dev), screens: add(old.screens, sample.screen), sample }
      : { sig, kind: sample.kind, msg: sample.msg, n: 1, seen: 0, first: sample.t, last: sample.t, vs: [sample.v], devs: [sample.dev], screens: [sample.screen], sample };
    await env.NUDGE.put(key, JSON.stringify(rec), { expirationTtl: BUG_TTL });
    took++;
  }
  return { ok: true, took };
}
async function bugList(env) {
  const out = []; let cursor;
  do {
    const page = await env.NUDGE.list({ prefix: "bug:", cursor });
    for (const k of page.keys) { const r = await env.NUDGE.get(k.name, "json"); if (r && r.n > (r.seen || 0)) out.push(r); }
    cursor = page.list_complete ? null : page.cursor;
  } while (cursor);
  return out.sort((a, b) => b.last - a.last);
}
async function bugAck(env, body) {
  /* {sigs: {sig: n}}: that many are filed now */
  let n = 0;
  for (const [sig, c] of Object.entries(body.sigs || {}).slice(0, 100)) {
    if (!/^[0-9a-f]{8}$/.test(sig)) continue;
    const key = "bug:" + sig, r = await env.NUDGE.get(key, "json"); if (!r) continue;
    r.seen = Math.min(r.n, Math.max(r.seen || 0, +c || 0));
    await env.NUDGE.put(key, JSON.stringify(r), { expirationTtl: BUG_TTL }); n++;
  }
  return { ok: true, acked: n };
}
