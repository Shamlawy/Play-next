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
   POST /art          → game pictures straight from Steam, the Xbox store, the PlayStation Store and the Nintendo eShop, each with its size (v15).
   POST /hours        → time to beat (main / main + extras / everything) for a list of games, from HowLongToBeat (v17; the AI's guesses before).
   POST /news         → the latest official announcements (patches, DLC, demos, launches) from Steam for a list of Steam ids (v18).
   POST /cal/put, GET /cal/<id>.ics → release days as a calendar feed the phone's calendar subscribes to (v18).
   POST /prices       → Steam + PlayStation Store + Nintendo eShop prices for a list of games; with an id it's re-checked daily and drops are pushed.
   POST /vault/put, GET /vault/list, GET /vault/get, POST /vault/del → cloud backup. The phone encrypts everything with a key
        made from its restore code (which never leaves the phone); this only keeps the sealed bytes.
   cron (every 15 min) → sends the nudges that are due, and is the backup for new-version pings.
   Needs: Workers AI binding "AI", KV binding "NUDGE", a cron trigger "*\/15 * * * *". */
const SGDB = "https://www.steamgriddb.com/api/v2";
/* tried in order; Cloudflare retires models now and then */
/* the bigger models know far more games and follow instructions better; used for chat and similar games,
   falling back down the list if one is missing or the free daily allowance runs out */
const HELPER_V = 18;
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
    if (url.pathname === "/version") return json({ v: HELPER_V, chat: true, similar: true, nudge: !!env.NUDGE, updates: !!env.NUDGE, bugs: !!env.NUDGE, review: true, vault: !!env.NUDGE, prices: true, recap: true, eshop: true, steam: true, reviews: true, hours: true, fx: true, art: true, news: true, cal: !!env.NUDGE }, 200, h);
    if (url.pathname === "/prices" && req.method === "POST") return pxRoute(req, env, h);
    if (url.pathname === "/fx") return json(await fxRates(env), 200, h);
    if (url.pathname === "/steam" && req.method === "POST") return stRoute(req, env, h);
    if (url.pathname === "/reviews" && req.method === "POST") return rvRoute(req, env, h);
    if (url.pathname === "/hours" && req.method === "POST") return hbRoute(req, env, h);
    if (url.pathname === "/art" && req.method === "POST") return artRoute(req, env, h);
    if (url.pathname.startsWith("/vault/")) return vault(req, env, url, h);
    if (url.pathname === "/news" && req.method === "POST") return nwRoute(req, env, h);
    if (url.pathname === "/cal/put" && req.method === "POST") return calPut(req, env, h);
    if (url.pathname.startsWith("/cal/") && req.method === "GET") return calGet(env, url);
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
  /* v14: everything else the app knows right now (deals, PS Plus, releases, finish forecasts, notes, predictions, habits) */
  if (list("facts", 20).length) lines.push("What the app knows right now:\n- " + list("facts", 20).join("\n- "));
  if (b && b.memory) lines.push("Your memory of them from earlier chats: " + String(b.memory).slice(0, 700));
  const nm = c.name ? String(c.name).slice(0, 30) : "";
  return `You are Nexi, the little mascot inside the game-ranking app "Play next", and the player's sharp, well-informed gaming companion. Your personality: ${p}
Keep the same facts and tips whatever the personality; only the voice changes.
Help with: what to play next, recommendations (say if a game is already in their list), explaining their taste from the data below, deals and prices, release dates, how long games take, and game tips (no spoilers unless asked).
Be smart and specific: point at concrete games, numbers and dates from the data below, give the reason behind every suggestion, and end with one clear next step when it helps. Prefer one confident answer over a vague list.
${nm ? `Call them ${nm} naturally: use their name in most replies (at the start or end, not every sentence).` : ""}
Be short: 1–4 sentences or a tiny list. Use only the data below for claims about the player; if unsure, say so. Never invent their scores or prices.
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
/* ===== v15: game pictures from the stores themselves, for Edit → Look → Browse and choose (and auto-pick when SteamGridDB has
   nothing). {t (title), st (Steam id, optional), cc, pscc, sw2} → {found: {st|xb|ps|ns: {id, name}}, pics: [{s (store), r (cover |
   hero | art | shot), u (full picture), th (small copy), w, h, k (what the store calls it)}]}. Shapes recorded from the real stores by
   .github/scripts/artprobe.mjs:
   - Steam: IStoreBrowseService/GetItems include_assets gives each picture's real path (newer games keep them under a hash folder,
     which is why guessing the address failed), include_screenshots the screenshots in their original size.
   - Xbox / Microsoft Store: displaycatalog autosuggest (productFamilyNames=Games is required) → products?bigIds= lists every
     picture with its size: SuperHeroArt (4K backdrop), Poster (2:3 cover, 1440×2160), BoxArt (square), Logo, Screenshot (often 4K).
   - PlayStation Store: the website's search results carry the media: MASTER (key art), GAMEHUB_COVER_ART, BACKGROUND,
     PORTRAIT_BANNER, LOGO, SCREENSHOT…
   - Nintendo: Nintendo of America's store search (Algolia) productImage (a Cloudinary id) and productImageSquare.
   Pictures without a known size are measured from their first bytes (artDim), which also drops addresses that don't exist. ===== */
const ST_IMG = "https://shared.akamai.steamstatic.com/store_item_assets/";
/* stricter than the price match: a store name with a word your title doesn't have (other than edition words) is another game
   ("Hollow Knight" is not "Hollow Knight: Silksong"), and a picture of the wrong game is worse than none */
const ART_ED = new Set("deluxe complete ultimate gold premium definitive collector collectors digital special anniversary director directors cut edition standard bundle goty year of the remastered remaster hd ps4 ps5 switch nintendo xbox series one pc windows and for launch cross gen legendary enhanced".split(" ").map(w => pxStem(w)));
/* v16: what to search a store for. Its search can find nothing for the whole title (Steam had nothing for "Apothecary Diaries: The False
   Imperial Brothers", only for "Apothecary Diaries"), so the part before the colon is tried next; results are always scored against the full title */
function artQs(title) {
  const t = String(title).replace(/[™®©]/g, "").trim(), head = t.split(/[:–—]| - /)[0].trim();
  return [...new Set([t, pxExpand(t), head.length >= 4 && head !== t ? head : ""].filter(Boolean))].map(q => q.slice(0, 80));
}
function artSame(title, name) {
  const s = pxSame(title, name), T = pxNorm(title), A = new Set(T.split(" "));
  /* a numbered title with the store's subtitle after a colon is the same game ("The Witcher 3" = "The Witcher 3: Wild Hunt") */
  if (/\d$/.test(T) && pxNorm(String(name).split(/[:–—]| - /)[0]) === T) return s;
  return pxNorm(name).split(" ").some(w => w && !A.has(w) && !ART_ED.has(w)) ? s - .3 : s;
}
function artDim(b) {
  const u16 = i => (b[i] << 8) | b[i + 1], l16 = i => b[i] | (b[i + 1] << 8), u32 = i => ((b[i] << 24) >>> 0) + (b[i + 1] << 16) + (b[i + 2] << 8) + b[i + 3];
  if (b.length > 24 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return { w: u32(16), h: u32(20) };
  if (b.length > 10 && b[0] === 0x47 && b[1] === 0x49 && b[2] === 0x46) return { w: l16(6), h: l16(8) };
  if (b.length > 30 && b[0] === 0x52 && b[1] === 0x49 && b[2] === 0x46 && b[3] === 0x46 && b[8] === 0x57 && b[9] === 0x45) {
    const t = String.fromCharCode(b[12], b[13], b[14], b[15]);
    if (t === "VP8 ") return { w: l16(26) & 0x3fff, h: l16(28) & 0x3fff };
    if (t === "VP8L") { const n = b[21] | (b[22] << 8) | (b[23] << 16) | (b[24] << 24); return { w: (n & 0x3fff) + 1, h: ((n >> 14) & 0x3fff) + 1 }; }
    if (t === "VP8X") return { w: 1 + (b[24] | (b[25] << 8) | (b[26] << 16)), h: 1 + (b[27] | (b[28] << 8) | (b[29] << 16)) };
  }
  if (b.length > 4 && b[0] === 0xff && b[1] === 0xd8) {
    let i = 2;
    while (i + 9 < b.length) {
      if (b[i] !== 0xff) { i++; continue; }
      const m = b[i + 1];
      if (m === 0xff) { i++; continue; }
      if (m >= 0xc0 && m <= 0xcf && m !== 0xc4 && m !== 0xc8 && m !== 0xcc) return { h: u16(i + 5), w: u16(i + 7) };
      if (m === 0xd8 || (m >= 0xd0 && m <= 0xd7) || m === 0x01) { i += 2; continue; }
      i += 2 + u16(i + 2);
    }
  }
  return null;
}
/* the size of a picture from its first 64 KB (a Range request; a server that ignores it is cut off after 64 KB). null = no picture there */
async function artSize(u) {
  try {
    const r = await fetch(u, { headers: { ...PX_UA, Range: "bytes=0-65535" }, signal: AbortSignal.timeout(6000) });
    if (!r.ok || !r.body || !/^image\//.test(r.headers.get("content-type") || "image/")) { try { r.body && r.body.cancel(); } catch (e) {} return null; }
    const rd = r.body.getReader(), parts = []; let n = 0;
    while (n < 65536) { const { done, value } = await rd.read(); if (done) break; parts.push(value); n += value.length; }
    try { rd.cancel(); } catch (e) {}
    return artDim(cat(...parts));
  } catch (e) { return null; }
}
/* Steam's store search ranked by artSame ("The Witcher 3" finds Wild Hunt, not "The Witcher 3 REDkit", which the price match takes) */
async function artSteamFind(cc, title) {
  for (const q of artQs(title)) {
    const r = await fetch(`https://store.steampowered.com/api/storesearch/?term=${encodeURIComponent(q)}&cc=${cc}&l=english`, { headers: PX_UA });
    const j = await r.json().catch(() => null);
    let best = null, bs = 0;
    for (const x of ((j && j.items) || []).filter(x => x.type === "app" || !x.type).slice(0, 10)) {
      let sc = artSame(title, x.name); if (PS_JUNK.test(x.name) && !PS_JUNK.test(title)) sc -= .5;
      if (sc > bs) { bs = sc; best = x; }
    }
    if (best && bs >= .55) return { id: best.id, name: best.name };
  }
  return null;
}
async function artSteam(cc, title, id) {
  let name = "";
  if (!(id > 0)) { const f = await artSteamFind(cc, title) || (cc !== "us" ? await artSteamFind("us", title) : null); if (!f) return null; id = f.id; name = f.name; }
  const input = { ids: [{ appid: id }], context: { language: "english", country_code: "US", steam_realm: 1 }, data_request: { include_assets: true, include_screenshots: true } };
  const j = await (await fetch("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json=" + encodeURIComponent(JSON.stringify(input)), { headers: PX_UA })).json().catch(() => ({}));
  const it = ((j.response || {}).store_items || [])[0];
  if (!it || !it.success) return null;
  const A = it.assets || {}, fmt = A.asset_url_format || `steam/apps/${id}/\${FILENAME}`, at = f => ST_IMG + fmt.replace("${FILENAME}", f);
  const pics = [], add = (f, r, k, th) => { if (f) pics.push({ s: "st", r, u: at(f), th: th ? at(th) : "", k }); };
  add(A.library_capsule_2x, "cover", "Box art", A.library_capsule);
  add(A.library_hero_2x, "hero", "Library hero (2x)");
  add(A.library_hero, "hero", "Library hero");
  /* the transparent logo isn't in the list: it sits next to the library pictures */
  pics.push({ s: "st", r: "art", u: ST_IMG + `steam/apps/${id}/logo_2x.png`, k: "Logo (2x)" }, { s: "st", r: "art", u: ST_IMG + `steam/apps/${id}/logo.png`, k: "Logo" });
  /* newer games keep the logo in a hash folder: the box art's or the library hero's (v16) */
  for (const dir of new Set([A.library_capsule, A.library_hero].map(f => String(f || "").includes("/") ? f.split("/")[0] + "/" : "").filter(Boolean)))
    pics.push({ s: "st", r: "art", u: at(dir + "logo_2x.png"), k: "Logo (2x)" }, { s: "st", r: "art", u: at(dir + "logo.png"), k: "Logo" });
  add(A.header_2x || A.header, "hero", "Store header", A.header);
  add(A.main_capsule_2x || A.main_capsule, "hero", "Store capsule", A.main_capsule);
  add(A.raw_page_background, "hero", "Store page background");
  for (const s of ((it.screenshots || {}).all_ages_screenshots || []).sort((a, b) => a.ordinal - b.ordinal).slice(0, 12)) {
    const f = String(s.filename || ""); if (!f) continue;
    const u = /^https?:/.test(f) ? f : ST_IMG + f;
    pics.push({ s: "st", r: "shot", u, th: u.replace(/(ss_[0-9a-f]+)\.jpg/, "$1.600x338.jpg"), k: "Screenshot" });
  }
  return { id, name: it.name || name, pics };
}
const XB_ROLE = { SuperHeroArt: ["hero", "4K backdrop"], TitledHeroArt: ["hero", "Backdrop with title"], Poster: ["cover", "Poster"], BoxArt: ["cover", "Box art (square)"],
  BrandedKeyArt: ["cover", "Key art"], Logo: ["art", "Logo"], Screenshot: ["shot", "Screenshot"] };
async function artXbox(title) {
  let best = null, bs = 0;
  for (const q of artQs(title)) {
  if (best && bs >= .55) break;
  const j = await (await fetch(`https://displaycatalog.mp.microsoft.com/v7.0/productFamilies/autosuggest?market=US&languages=en-US&query=${encodeURIComponent(q)}&productFamilyNames=Games`, { headers: PX_UA })).json().catch(() => ({}));
  for (const g of j.Results || []) for (const p of g.Products || []) {
    if (!p.ProductId || (p.Type && p.Type !== "Game")) continue;
    let sc = artSame(title, p.Title || ""); if (PS_JUNK.test(p.Title || "") && !PS_JUNK.test(title)) sc -= .5; if (PS_ED.test(p.Title || "") && !PS_ED.test(title)) sc -= .08;
    if (sc > bs) { bs = sc; best = p; }
  }
  }
  if (!best || bs < .55) return null;
  const d = await (await fetch(`https://displaycatalog.mp.microsoft.com/v7.0/products?bigIds=${best.ProductId}&market=US&languages=en-US`, { headers: PX_UA })).json().catch(() => ({}));
  const L = ((((d.Products || [])[0] || {}).LocalizedProperties) || [])[0] || {}, pics = [], seen = new Set();
  for (const im of L.Images || []) {
    const ro = XB_ROLE[im.ImagePurpose]; if (!ro || !im.Uri) continue;
    const u = (/^\/\//.test(im.Uri) ? "https:" : "") + im.Uri; if (seen.has(u)) continue; seen.add(u);
    const w = +im.Width || 0, h = +im.Height || 0, wide = w >= h;
    pics.push({ s: "xb", r: ro[0], u, th: u + (ro[0] === "art" ? "?w=300" : wide ? "?w=480&h=270&q=80" : "?w=300&h=450&q=80"), w, h, k: ro[1] });
  }
  return { id: best.ProductId, name: L.ProductTitle || best.Title || "", pics };
}
/* sizes seen: MASTER 1024² (the square tile), EDITION_KEY_ART / GAMEHUB_COVER_ART 3840×2160, BACKGROUND 1920–3840 wide, PORTRAIT_BANNER 1440×2160 */
const PSA_ROLE = { EDITION_KEY_ART: ["hero", "Key art"], GAMEHUB_COVER_ART: ["hero", "Game hub art"], BACKGROUND: ["hero", "Background"], FOUR_BY_THREE_BANNER: ["hero", "Banner"],
  PORTRAIT_BANNER: ["cover", "Portrait art"], MASTER: ["cover", "Square art"], LOGO: ["art", "Logo"], SCREENSHOT: ["shot", "Screenshot"] };
async function artPs(cc, title) {
  let best = null, bs = 0;
  for (const q of artQs(title)) {
  if (best && bs >= .55) break;
  const vars = { countryCode: cc.toUpperCase(), languageCode: "en", nextCursor: "", pageOffset: 0, pageSize: 24, searchTerm: q };
  /* the store now and then answers 403 to a Cloudflare request: one more try */
  const j = await psGql(cc, "getSearchResults", vars).catch(() => new Promise(r => setTimeout(r, 700)).then(() => psGql(cc, "getSearchResults", vars)));
  for (const r of (((j || {}).data || {}).universalSearch || {}).results || []) {
    const name = r.name || r.invariantName || "", cls = String(r.localizedStoreDisplayClassification || r.storeDisplayClassification || "");
    if (!name || !(r.media || []).length) continue;
    if (PS_ID.test(r.id || "") && cls && !/full game|bundle|edition|^game$/i.test(cls)) continue;
    let sc = artSame(title, name); if (PS_JUNK.test(name) && !PS_JUNK.test(title)) sc -= .5; if (PS_ED.test(name) && !PS_ED.test(title)) sc -= .08;
    if (sc > bs) { bs = sc; best = r; }
  }
  }
  if (!best || bs < .55) return null;
  const pics = [], seen = new Set();
  for (const m of best.media) {
    const ro = PSA_ROLE[m.role]; if (!ro || m.type !== "IMAGE" || !m.url || seen.has(m.url)) continue; seen.add(m.url);
    /* ?w=&h= fits the picture inside that box (keeps its shape); ?w= alone is ignored */
    pics.push({ s: "ps", r: ro[0], u: m.url, th: m.url + "?w=480&h=480", k: ro[1] });
  }
  return { id: String(best.id), name: String(best.name || "").replace(/\s*PS4\s*(&|and)\s*PS5\s*$/i, ""), pics };
}
async function artNs(title, sw2) {
  let best = null, bs = 0;
  for (const q of artQs(title)) {
  if (best && bs >= .55) break;
  const r = await fetch("https://U3B6GR4UA3-dsn.algolia.net/1/indexes/store_game_en_us/query", { method: "POST",
    headers: { "X-Algolia-Application-Id": "U3B6GR4UA3", "X-Algolia-API-Key": "a29c6927638bfd8cee23993e51e721c9", "Content-Type": "application/json" },
    body: JSON.stringify({ query: q, hitsPerPage: 12 }) });
  const j = await r.json().catch(() => ({}));
  for (const x of j.hits || []) {
    if (x.dlcType && x.dlcType !== "null") continue;
    let sc = artSame(title, String(x.title || "").replace(/[–—-]\s*Nintendo Switch\s*2 Edition/i, "")); if (NS_JUNK.test(x.title || "") && !NS_JUNK.test(title)) sc -= .6;
    if ((/Switch 2/.test(x.platform || "") || /Switch\s*2 Edition/i.test(x.title || "")) !== !!sw2) sc -= .05;
    if (sc > bs) { bs = sc; best = x; }
  }
  }
  if (!best || bs < .55) return null;
  const pics = [];
  if (best.productImage) { const id = String(best.productImage).replace(/^https?:\/\/[^/]+\/image\/upload\//, "");
    pics.push({ s: "ns", r: "hero", u: /^https?:/.test(best.productImage) ? best.productImage : "https://assets.nintendo.com/image/upload/" + id, th: "https://assets.nintendo.com/image/upload/c_scale,w_480/" + id, k: "Store art" }); }
  if (best.productImageSquare) pics.push({ s: "ns", r: "cover", u: best.productImageSquare, k: "Square art" });
  return { id: String(best.nsuid || best.objectID || ""), name: String(best.title || "").replace(/[™®]/g, ""), pics };
}
async function artRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const t = String((b && b.t) || "").replace(/\s+/g, " ").trim().slice(0, 100);
  if (!t) return json({ error: "no title" }, 400, h);
  const cc = pxCC(b.cc), st = Number.isFinite(+b.st) && +b.st > 0 ? Math.trunc(+b.st) : 0;
  const want = new Set(Array.isArray(b.want) && b.want.length ? b.want : ["st", "xb", "ps", "ns"]);
  const run = (k, f) => want.has(k) ? f().catch(e => ({ err: String(e.message || e).slice(0, 60) })) : Promise.resolve(null);
  const [S1, X, P, N] = await Promise.all([run("st", () => artSteam(cc, t, st)), run("xb", () => artXbox(t)), run("ps", () => artPs(psCC(cc, b.pscc), t)), run("ns", () => artNs(t, !!b.sw2))]);
  const found = {}, pics = [];
  [["st", S1], ["xb", X], ["ps", P], ["ns", N]].forEach(([k, r]) => { if (!r) return; if (r.err) { found[k] = { err: r.err }; return; }
    found[k] = { id: r.id, name: r.name }; pics.push(...r.pics); });
  /* measure what has no size: covers, backdrops and logos first, then a few screenshots per store (≤ 24, the Workers request budget) */
  const need = pics.filter(p => !p.w), order = need.filter(p => p.r !== "shot").concat(need.filter(p => p.r === "shot")), per = {};
  const pick = order.filter(p => p.r !== "shot" || (per[p.s] = (per[p.s] || 0) + 1) <= 4).slice(0, 24);
  await Promise.all(pick.map(async p => { const d = await artSize(p.u); if (d && d.w && d.h) { p.w = d.w; p.h = d.h; } else p.gone = 1; }));
  /* a measured picture that isn't there is dropped (the guessed Steam logo addresses); the rest are kept unmeasured */
  const out = pics.filter(p => !p.gone && !(p.s === "st" && p.r === "art" && !p.w));
  /* Steam's logo can be found under two or three addresses (1x, 2x, hash folder): keep the sharpest */
  const logo = out.filter(p => p.s === "st" && p.r === "art").sort((a, b) => b.w * b.h - a.w * a.h)[0];
  const keep = out.filter(p => !(p.s === "st" && p.r === "art") || p === logo);
  /* a picture shaped differently from its usual job does the job its shape fits (a portrait "background" is a cover) */
  for (const p of keep) if (p.w && p.h && p.r !== "art" && p.r !== "shot") p.r = p.h > p.w * 1.15 ? "cover" : p.w > p.h * 1.3 ? "hero" : p.r;
  return json({ ok: true, found, pics: keep }, 200, h);
}
/* ===== v17: hours to beat from HowLongToBeat (v11 asked the AI, whose guesses were often far off). {items: [{k, t, year}]}
   (≤10) → {src: "hltb", res[k] = {id, n (HLTB's name), m (main story), x (main + extras), c (completionist), cnt (players
   counted), sure: 1}} | {none: 1, sure: 0[, id, n]} (not on HLTB, or no times yet) | {err, sure: 0}. HowLongToBeat has no
   public API: its website asks GET /api/search/site/init?t=<ms> for a token (bound to the caller's IP and User-Agent, so both
   calls use the same UA) and then POST /api/search/site with header x-auth-token; times come in seconds. Found with
   .github/scripts/hltbprobe.mjs (hltb-probe.yml records the site's own requests in a headless browser): if hours stop
   coming back, run that workflow and compare. ===== */
const HLTB = "https://howlongtobeat.com", HLTB_H = { "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36", "Accept-Language": "en-US", Referer: HLTB + "/", Origin: HLTB };
let hltbTok = null;   /* {t, at}: reused by later requests in the same Worker for 5 minutes */
async function hltbToken(fresh) {
  if (!fresh && hltbTok && Date.now() - hltbTok.at < 5 * 60e3) return hltbTok.t;
  const r = await fetch(`${HLTB}/api/search/site/init?t=${Date.now()}`, { headers: HLTB_H });
  if (!r.ok) throw new Error("HowLongToBeat init " + r.status);
  const j = await r.json(); if (!j || !j.token) throw new Error("HowLongToBeat gave no token");
  hltbTok = { t: j.token, at: Date.now() }; return j.token;
}
/* the live helper's searches were refused (403) right after a fresh token and accepted a moment later with the same one: the
   site seems to reject a token used too soon after it was issued (a browser or GitHub takes longer between the two calls than
   a Worker does), so a new token waits until it's ~1.2s old (longer on a retry) */
async function hltbTokenAged(fresh, min) {
  const t = await hltbToken(fresh), age = Date.now() - hltbTok.at;
  if (age < min) await new Promise(r => setTimeout(r, min - age));
  return t;
}
async function hltbSearch(q) {
  const body = JSON.stringify({ searchType: "games", searchTerms: q.split(/\s+/).filter(Boolean), searchPage: 1, size: 20,
    searchOptions: { games: { userId: 0, platform: { mode: "include", values: [] }, sortCategory: "popular", rangeCategory: "main", rangeTime: { min: null, max: null },
      gameplay: { perspective: { mode: "include", values: [] }, flow: { mode: "include", values: [] }, genre: { mode: "include", values: [] } },
      year: { mode: "include", values: [] }, modifier: "" }, users: { sortCategory: "postcount" }, lists: { sortCategory: "follows" }, filter: "", sort: 0, randomizer: 0 }, useCache: true });
  /* the token is bound to the outgoing IP, and a Worker's two calls can leave from different IPs (the live helper got a 403
     on its first search): up to 3 fresh tokens */
  for (let i = 0; i < 3; i++) {
    const tok = await hltbTokenAged(i > 0, i ? 2500 : 1200);
    const r = await fetch(`${HLTB}/api/search/site`, { method: "POST", headers: { ...HLTB_H, "Content-Type": "application/json", "x-auth-token": tok }, body });
    if ((r.status === 401 || r.status === 403) && i < 2) continue;
    if (!r.ok) throw new Error("HowLongToBeat search " + r.status);
    const j = await r.json(); return Array.isArray(j && j.data) ? j.data : [];
  }
  return [];
}
const hltbH = s => { const h = (+s || 0) / 3600; return h <= 0 ? 0 : h < 10 ? Math.round(h * 2) / 2 : Math.round(h); };
/* the best entry for a title: same name (or alias) by the art matcher's rules (different numbers = another game, extra words
   cost), then the release year, then how many players logged it */
function hltbPick(title, year, list) {
  let best = null, bs = 0;
  for (const d of list) {
    const names = [d.game_name, ...String(d.game_alias || "").split(/,\s*/)].filter(Boolean);
    let s = Math.max(...names.map(n => artSame(title, n)));
    if (s < .55) continue;
    if (year && d.release_world) s += Math.abs(+year - +d.release_world) <= 1 ? .08 : -.08;
    if (d.game_type && d.game_type !== "game") s -= .03;
    s += Math.min(.04, (+d.comp_all_count || 0) / 50000);
    if (s > bs) { bs = s; best = d; }
  }
  return best;
}
export async function hltbFind(env, title, year) {
  let seen = false;
  for (const q of artQs(title)) {
    const q2 = q.replace(/[:–—!?,.]/g, " ").replace(/\s+/g, " ").trim(); if (!q2) continue;
    const d = hltbPick(title, year, await hltbSearch(q2)); seen = true;
    if (!d) continue;
    const m = hltbH(d.comp_main) || hltbH(d.comp_all), x = hltbH(d.comp_plus), c = hltbH(d.comp_100);
    if (!m) return { none: 1, sure: 0, id: d.game_id, n: d.game_name };
    return { id: d.game_id, n: d.game_name, m, x: x > m ? x : 0, c: c > Math.max(m, x) ? c : 0, cnt: (+d.comp_all_count || 0), sure: 1 };
  }
  return seen ? { none: 1, sure: 0 } : { err: "no search", sure: 0 };
}
async function hbRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 10).map(x => ({ k: String(x.k || "").slice(0, 24), t: String(x.t || "").replace(/["\n]/g, " ").slice(0, 100),
    y: /^\d{4}$/.test(String(x.year || "")) ? String(x.year) : "" })).filter(x => x.k && x.t);
  if (!items.length) return json({ error: "no games" }, 400, h);
  const res = {};
  for (const it of items) { try { res[it.k] = await hltbFind(env, it.t, it.y); } catch (e) { res[it.k] = { err: String(e && e.message || e).slice(0, 80), sure: 0 }; } }
  return json({ ok: true, src: "hltb", res }, 200, h);
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
/* v16: singular = plural ("The False Imperial Brothers" is the store's "The False Imperial Brother") */
function pxStem(w) { return w.length > 3 && /[^s]s$/.test(w) ? w.slice(0, -1) : w; }   /* a function: ART_ED (earlier in the file) uses it at load */
const pxNorm = s => pxNum(pxNorm0(pxExpand(s).replace(/['’]/g, "").replace(/[®™©]/g, " "))).replace(/\s+/g, " ").trim().split(" ").map(pxStem).join(" ");   /* v13: "Persona®5" = "Persona 5" */
/* every word of your title must be in the store's name (so "Final Fantasy VII Rebirth" never matches plain "Final Fantasy VII",
   nor "Death Stranding 2" the first game); extra words (editions, subtitles) cost a little each */
function pxSame(a, b) {
  const x = pxNorm(a), y = pxNorm(b); if (!x || !y) return 0; if (x === y) return 1;
  const A = x.split(" "), B = new Set(y.split(" ")), hit = A.filter(w => B.has(w)).length, cov = hit / A.length;
  const jac = hit / new Set([...A, ...B]).size;
  /* v14: a number the store's name has and yours doesn't is another game in the series ("The Caligula Effect" isn't
     "The Caligula Effect 2", "Hades" isn't "Hades II") */
  const As = new Set(A), seq = [...B].some(w => /^\d{1,2}$/.test(w) && !As.has(w));
  return cov < 1 ? jac * cov * cov : Math.max(jac, .8 - Math.min(.3, (B.size - A.length) * .06)) - (seq ? .5 : 0);
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
  /* v13: listed with no price means "not out yet", "free" or "not sold in this region" (STEINS;GATE ELITE in the UAE is out
     since 2019 with no price): a single-app call (filters only work for one app) tells them apart, ≤3 per call */
  let n = 0;
  for (const id of ids) {
    if (!out[id] || !out[id].nop || n++ >= 3) continue;
    try { const d = (await (await fetch(`https://store.steampowered.com/api/appdetails?appids=${id}&cc=${cc}&filters=basic,release_date`, { headers: PX_UA })).json())[id];
      const x = d && d.success && d.data; if (!x) continue;
      if (x.is_free) out[id] = { free: 1 };
      else if (x.release_date && x.release_date.coming_soon === false) out[id] = { nosale: 1 };
    } catch (e) {}
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
  let f = null;
  try { f = await pxPsSearchWeb(cc, title) || (pxExpand(title) !== title ? await pxPsSearchWeb(cc, pxExpand(title), title) : null)
    /* "13 Sentinels: Aegis Rim": the search sometimes only finds a game by the part before the colon */
    || (/:/.test(title) && title.split(":")[0].trim().length > 3 ? await pxPsSearchWeb(cc, title.split(":")[0].trim(), title) : null); } catch (e) {}
  /* only an edition found ("Ghost of Yōtei Complete Edition") while you named the plain game: the old search may have the plain one */
  if (f && PS_ED.test(f.name) && !PS_ED.test(title)) { const o = await pxPsFindOld(cc, title).catch(() => null); if (o && !PS_ED.test(o.name) && pxSame(title, o.name) >= .9) return o; }
  return f || pxPsFindOld(cc, title);
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
    /* v13: only games (seen: Full Game, Game Bundle, Premium Edition; concepts have none). Add-ons come as Add-On Pack, Add-on,
       Episode, Item, Costume, Character, Track, Level, Map, Demo… ("Stellar Blade x NieR:Automata" is an Episode) */
    if (prod && cls && !/full game|bundle|edition|^game$/i.test(cls)) continue;
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
/* ===== v14: exchange rates. The UAE PlayStation Store and the US eShop (used in the Gulf) sell in US dollars, Steam in
   Egypt too, so the phone shows every price in its own currency. Rates per 1 USD from open.er-api.com (daily, no key), the
   fawazahmed0 currency API as the backup; kept in KV for 12 h. ===== */
async function fxFetch() {
  try { const j = await (await fetch("https://open.er-api.com/v6/latest/USD", { headers: PX_UA })).json();
    if (j && j.result === "success" && j.rates && j.rates.AED) return { r: j.rates, t: (+j.time_last_update_unix || 0) * 1000 || Date.now(), src: "er-api" }; } catch (e) {}
  for (const u of ["https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json", "https://latest.currency-api.pages.dev/v1/currencies/usd.json"]) {
    try { const j = await (await fetch(u, { headers: PX_UA })).json(), r = {};
      for (const k in (j && j.usd) || {}) if (/^[a-z]{3}$/.test(k) && +j.usd[k] > 0) r[k.toUpperCase()] = +j.usd[k];
      if (r.AED) return { r, t: Date.parse(j.date) || Date.now(), src: "currency-api" }; } catch (e) {}
  }
  return null;
}
async function fxRates(env) {
  const kv = env && env.NUDGE;
  let old = null; try { old = kv ? await kv.get("fx", "json") : null; } catch (e) {}
  if (old && Date.now() - (old.at || 0) < 12 * 36e5) return { ok: true, base: "USD", r: old.r, t: old.t, at: old.at };
  const f = await fxFetch();
  if (!f) return old ? { ok: true, base: "USD", r: old.r, t: old.t, at: old.at, stale: 1 } : { ok: false };
  const rec = { r: f.r, t: f.t, at: Date.now() };
  try { if (kv) await kv.put("fx", JSON.stringify(rec), { expirationTtl: 7 * 86400 }); } catch (e) {}
  return { ok: true, base: "USD", r: rec.r, t: rec.t, at: rec.at };
}
/* v14: countries with no PlayStation Store of their own (Egypt: the store's search returns nothing there). People there
   use another country's store; the phone can say which (pscc), else the UAE store for the Arab world, the US one otherwise. */
/* a store's price as a plain amount: Steam and the PS Store send minor units (cents), the eShop the amount itself */
const FX_ZD = new Set(["JPY", "KRW", "CLP", "VND", "IDR", "HUF", "TWD", "COP"]);
const pxMaj = (s, cur, v) => v == null ? null : s === "ns" ? +v : s === "ps" && FX_ZD.has(cur) ? +v : +v / 100;
function fxShow(fx, s, p, v, to) {
  const from = p && p.cur, a = pxMaj(s, from, v);
  if (!fx || !fx.r || !to || !from || to === from || a == null || !fx.r[from] || !fx.r[to]) return "";
  const x = a / fx.r[from] * fx.r[to];
  try { return new Intl.NumberFormat("en", { style: "currency", currency: to, maximumFractionDigits: x >= 100 ? 0 : 2, minimumFractionDigits: x >= 100 ? 0 : 2 }).format(x); } catch (e) { return ""; }
}
/* checked with the price probe (Oct 2026): no store in eg, ma, dz, jo, iq, pk, ng, ph; stores in lb, tr, in, za and the Gulf */
const PS_NONE = new Set(["eg", "ma", "dz", "tn", "ly", "iq", "jo", "sy", "ye", "sd", "pk", "ng", "ph", "ir", "af"]);
const PS_ARAB = new Set(["eg", "ma", "dz", "tn", "ly", "iq", "jo", "sy", "ye", "sd"]);
const psCC = (cc, pick) => { pick = String(pick || "").toLowerCase(); if (/^[a-z]{2}$/.test(pick) && !PS_NONE.has(pick)) return pick; cc = pxCC(cc); return PS_NONE.has(cc) ? (PS_ARAB.has(cc) ? "ae" : "us") : cc; };
/* check a list: [{k, t: title, st: steam app id | 0 = look it up | -1 = don't, ps: product id | "" = look it up | "-" = don't}]
   at most 8 Steam lookups and 10 PS games per call (up to 3 PS calls each the first time; Workers allow ~50 outside requests) */
/* what the phone would pay: the PS Plus member price counts when they have PS Plus */
const pxEff = (p, tier) => !p || p.now == null ? null : tier >= 1 && p.plusNow != null ? Math.min(p.now, p.plusNow) : p.now;
async function pxCheck(cc, items, nscc, pscc) {
  cc = pxCC(cc); nscc = nsCC(nscc || cc); const pcc = psCC(cc, pscc);
  const res = {}, look = { st: 0, ps: 0, ns: 0 };
  for (const it of items) res[it.k] = {};
  for (const it of items) {
    if (it.st === 0 && look.st < 8) { look.st++; try { const f = await pxSteamFind(cc, it.t) || (it.n2 && it.n2 !== it.t ? await pxSteamFind(cc, it.n2) : null); it.st = f ? f.id : -1; if (f) res[it.k].stName = f.name; else res[it.k].st = { none: 1 }; } catch (e) { res[it.k].st = { err: String(e.message || e).slice(0, 60) }; } }
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
      let pc = it.pc || "", found = false;
      /* v14: not found by your title: try the name Steam (or another store) knows it by ("Yu-Gi-Oh GX: Tag Force" is
         "Yu-Gi-Oh! TAG FORCE GX" on the stores) */
      const alt = [res[it.k].stName, it.n2].filter(n => n && pxNorm(n) !== pxNorm(it.t));
      if (!id && !pc) { let f = await pxPsFind(pcc, it.t); for (const n of alt) { if (f) break; f = await pxPsFind(pcc, n); }
        if (!f) { res[it.k].ps = { none: 1 }; continue; } id = f.id || ""; pc = f.cid || ""; name = f.name; found = true; }
      let p = await pxPsPrice(pcc, id, pc);
      /* the website's search can pick another region's edition (Persona 3 Reload in the UAE): then try the old search's pick */
      if (found && p.nosale) { const f2 = await pxPsFindOld(pcc, it.t).catch(() => null);
        if (f2 && f2.id !== id) { const p2 = await pxPsPrice(pcc, f2.id, ""); if (!p2.nosale) { id = f2.id; name = f2.name; p = p2; } } }
      if (!id) id = p.id || "";
      res[it.k].ps = Object.assign({ id }, p, { id: id || p.id, name: p.name || name, pcc });
    } catch (e) { res[it.k].ps = { err: String(e.message || e).slice(0, 60) }; }
  }
  /* Nintendo eShop: look up (≤8 a call), then price every found id in one call */
  for (const it of items) {
    if (it.ns !== "" || look.ns >= 8) continue;
    look.ns++;
    try { let f = await pxNsFind(nscc, it.t, it.sw2); for (const n of [res[it.k].stName, it.n2]) { if (f || !n || pxNorm(n) === pxNorm(it.t)) continue; f = await pxNsFind(nscc, n, it.sw2); }
      if (!f) { res[it.k].ns = { none: 1 }; it.ns = "-"; continue; } it.ns = f.id; res[it.k].nsF = f; }
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
    ns: x.ns === "-" || x.ns == null ? "-" : /^\d{14}$/.test(String(x.ns)) ? String(x.ns) : "", sw2: !!x.sw2, n2: String(x.n2 || "").slice(0, 100) })).filter(x => x.k && x.t);
  const cc = pxCC(b.cc), nscc = nsCC(b.nscc || cc), pscc = psCC(cc, b.pscc), tier = Math.max(0, Math.min(3, +b.tier || 0)), res = await pxCheck(cc, items, nscc, pscc);
  /* keep the list for the daily check (only what was found), with the prices seen now as the baseline */
  if (okId && env.NUDGE && b.watch) {
    const keep = items.map(it => { const r = res[it.k] || {}, st = r.st && r.st.id ? r.st.id : -1, ps = r.ps && r.ps.id ? r.ps.id : "-", ns = r.ns && r.ns.id ? r.ns.id : "-";
      return { k: it.k, t: it.t, st, ps, ns, pc: r.ps && r.ps.cid || "", last: { st: r.st && r.st.now != null ? r.st.now : null, ps: pxEff(r.ps, tier), ns: r.ns && r.ns.now != null ? r.ns.now : null, plus: r.ps && r.ps.plus || 0 } }; }).filter(x => x.st > 0 || x.ps !== "-" || x.ns !== "-");
    /* the phone sends its list in batches: merge this batch into what's kept, and drop games it no longer watches (b.all) */
    const old = await env.NUDGE.get("px:" + id, "json"), all = Array.isArray(b.all) ? new Set(b.all.map(String)) : null;
    const ks = new Set(keep.map(x => x.k)), was = old && old.cc === cc ? (old.items || []).filter(x => !ks.has(x.k) && (!all || all.has(x.k))) : [];
    await env.NUDGE.put("px:" + id, JSON.stringify({ cc, nscc, pscc, mc: /^[A-Z]{3}$/.test(String(b.mc || "")) ? b.mc : "", tier, min: Math.max(0, Math.min(90, +b.min || 0)), items: was.concat(keep).slice(0, 80), t: old && old.cc === cc ? old.t : Date.now() }), { expirationTtl: PX_TTL, metadata: { t: old && old.cc === cc && old.t ? old.t : Date.now() } });
  }
  /* v14: today's exchange rates ride along, so the phone can show every price in its own currency */
  let fx = null; if (b.fx) try { fx = await fxRates(env); } catch (e) {}
  return json({ ok: true, cc, nscc, pscc, res, at: Date.now(), ...(fx && fx.ok ? { fx } : {}) }, 200, h);
}
/* cron: the one watch list checked longest ago (if over 20 h), drops pushed to that phone */
async function pxCron(env) {
  if (!env.NUDGE) return;
  const keys = (await env.NUDGE.list({ prefix: "px:" })).keys.filter(k => !k.metadata || Date.now() - (k.metadata.t || 0) > 20 * 36e5)
    .sort((a, b) => ((a.metadata || {}).t || 0) - ((b.metadata || {}).t || 0));
  const k = keys[0]; if (!k) return;
  const rec = await env.NUDGE.get(k.name, "json"); if (!rec) return;
  const id = k.name.slice(3), sub = await env.NUDGE.get("sub:" + id, "json");
  const res = await pxCheck(rec.cc, rec.items.map(x => ({ k: x.k, t: x.t, st: x.st, ps: x.ps, pc: x.pc || "", ns: x.ns || "-" })), rec.nscc, rec.pscc);
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
  /* v14: in the phone's own currency when the store sells in another one (the store's price in brackets) */
  let fx = null; if (rec.mc && drops.length) try { fx = await fxRates(env); } catch (e) {}
  for (const d of drops.filter(d => !String(d.it.k).startsWith("d:") || d.p.pct >= 50).slice(0, 3)) {
    const store = d.s === "st" ? "Steam" : d.s === "ns" ? "Nintendo eShop" : "PS Store", mine = fxShow(fx, d.s, d.p, d.p.now, rec.mc), wasM = d.p.pct ? fxShow(fx, d.s, d.p, d.p.base, rec.mc) : "";
    try { await webPush(env, sub.sub, { title: `💸 ${d.it.t.slice(0, 40)}${d.p.pct ? " is " + d.p.pct + "% off" : " got cheaper"}`,
      body: `${store}: ${mine ? "≈ " + mine + " (" + (d.p.nowF || "") + ")" : d.p.nowF || ""}${d.p.pct && (wasM || d.p.baseF) ? " · was " + (wasM || d.p.baseF) : ""}${String(d.it.k).startsWith("d:") ? " · a game you'd probably love" : ""}`.trim(), tag: "deal-" + d.it.k,
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

/* ===== v18: game news. Steam's own news API (no key) with feeds=steam_community_announcements keeps only what the developers
   posted on the game's Steam page (patch notes, DLC, demos, launch posts), not the gaming-site articles Steam also lists.
   {items: [{k, st}]} (st = Steam id) → {res: {k: {n: [{id, t, u, d (seconds), k (patch|dlc|demo|launch|trailer|news), x}]}}}. ===== */
const NW_KINDS = [["dlc", /\b(dlc|expansion|season pass|add-?on)\b/i], ["demo", /\bdemo\b/i], ["patch", /\b(patch|hotfix)\b/i],
  ["launch", /\b(out now|available now|now available|launch(es|ed)?|release date|early access)\b/i], ["trailer", /\btrailer\b/i],
  ["news", /\b(community|dev(eloper)?) (update|diary|blog)\b/i], ["patch", /\b(update|v?\d+\.\d+(\.\d+)?)\b/i]];
const nwKind = (t, tags) => (tags || []).includes("patchnotes") ? "patch" : (NW_KINDS.find(([, re]) => re.test(t)) || ["news"])[0];
const nwText = s => String(s || "").replace(/\[\/?[a-z0-9*]+(=[^\]]*)?\]/gi, " ").replace(/<[^>]*>/g, " ").replace(/{STEAM_CLAN_IMAGE}\S*/g, " ").replace(/https?:\/\/\S+/g, " ").replace(/\s+/g, " ").trim();
async function nwSteam(st) {
  const r = await fetch(`https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=${st}&count=5&maxlength=400&format=json&feeds=steam_community_announcements`, { headers: PX_UA });
  const j = await r.json().catch(() => null), items = j && j.appnews && j.appnews.newsitems;
  if (!Array.isArray(items)) throw new Error("Steam news " + r.status);
  return items.map(it => ({ id: String(it.gid || ""), t: nwText(it.title).slice(0, 140), u: String(it.url || "").slice(0, 400), d: +it.date || 0,
    k: nwKind(String(it.title || ""), it.tags), x: nwText(it.contents).slice(0, 160) })).filter(x => x.id && x.t && /^https:\/\//.test(x.u));
}
async function nwRoute(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const items = (Array.isArray(b && b.items) ? b.items : []).slice(0, 24).map(x => ({ k: String(x.k || "").slice(0, 24), st: Math.trunc(+x.st || 0) })).filter(x => x.k && x.st > 0);
  const res = {};
  await Promise.all(items.map(async it => { try { res[it.k] = { n: await nwSteam(it.st) }; } catch (e) { res[it.k] = { err: String(e.message || e).slice(0, 60) }; } }));
  return json({ ok: true, res }, 200, h);
}
/* ===== v18: release days as a calendar feed. The app keeps its list here under a random id (POST /cal/put {id, ev: [{k, t, d
   (YYYY-MM-DD), p (platforms), r (1 = rumoured date)}]}) whenever a date changes; the phone's calendar subscribes once to
   GET /cal/<id>.ics and refreshes it by itself (Google every few hours, iPhone as set). Kept 400 days after the last update. ===== */
const CAL_ID = /^[A-Za-z0-9_-]{20,40}$/;
async function calPut(req, env, h) {
  if (!ALLOW.includes(req.headers.get("Origin") || "")) return json({ error: "origin" }, 403, h);
  if (!env.NUDGE) return json({ error: "no storage" }, 500, h);
  let b; try { b = await req.json(); } catch (e) { return json({ error: "bad json" }, 400, h); }
  const id = String(b && b.id || ""); if (!CAL_ID.test(id)) return json({ error: "bad id" }, 400, h);
  if (b.off) { await env.NUDGE.delete("cal:" + id); return json({ ok: true, off: true }, 200, h); }
  const ev = (Array.isArray(b.ev) ? b.ev : []).slice(0, 300).map(e => ({ k: String(e.k || "").slice(0, 24), t: String(e.t || "").slice(0, 120),
    d: String(e.d || ""), p: String(e.p || "").slice(0, 80), r: e.r ? 1 : 0 })).filter(e => e.k && e.t && /^\d{4}-\d{2}-\d{2}$/.test(e.d));
  await env.NUDGE.put("cal:" + id, JSON.stringify({ ev, t: Date.now() }), { expirationTtl: VAULT_TTL });
  return json({ ok: true, n: ev.length }, 200, h);
}
const icsEsc = s => String(s).replace(/\\/g, "\\\\").replace(/([,;])/g, "\\$1").replace(/\r?\n/g, "\\n");
/* lines longer than 75 bytes are folded (a new line starting with a space), never inside a character */
function icsFold(line) {
  const out = []; let cur = "", n = 0;
  for (const ch of line) { const b = new TextEncoder().encode(ch).length; if (n + b > (out.length ? 74 : 75)) { out.push(cur); cur = ""; n = 0; } cur += ch; n += b; }
  out.push(cur); return out.join("\r\n ");
}
function calIcs(ev, t) {
  const stamp = new Date(t || Date.now()).toISOString().replace(/[-:]/g, "").replace(/\.\d+/, "");
  const day = d => d.replace(/-/g, ""), next = d => { const x = new Date(d + "T00:00:00Z"); x.setUTCDate(x.getUTCDate() + 1); return x.toISOString().slice(0, 10).replace(/-/g, ""); };
  const L = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Play next//Release days//EN", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
    "X-WR-CALNAME:Play next · release days", "X-WR-CALDESC:Games you're waiting for, on the day they come out", "REFRESH-INTERVAL;VALUE=DURATION:PT6H", "X-PUBLISHED-TTL:PT6H"];
  for (const e of ev) L.push("BEGIN:VEVENT", "UID:" + icsEsc(e.k) + "@playnext", "DTSTAMP:" + stamp, "DTSTART;VALUE=DATE:" + day(e.d), "DTEND;VALUE=DATE:" + next(e.d),
    "SUMMARY:" + icsEsc("🎮 " + e.t + (e.r ? " (date not confirmed)" : " comes out")), "DESCRIPTION:" + icsEsc((e.p ? e.p + "\n" : "") + "From Play next"),
    "URL:" + APP_PAGE + "?nudge=game&g=" + encodeURIComponent(e.k), "TRANSP:TRANSPARENT", "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:PT9H",
    "DESCRIPTION:" + icsEsc(e.t + " is out today"), "END:VALARM", "END:VEVENT");
  L.push("END:VCALENDAR");
  return L.map(icsFold).join("\r\n") + "\r\n";
}
async function calGet(env, url) {
  const id = url.pathname.slice(5).replace(/\.ics$/, "");
  const rec = CAL_ID.test(id) && env.NUDGE ? await env.NUDGE.get("cal:" + id, "json") : null;
  if (!rec) return new Response("Not found", { status: 404, headers: { "Content-Type": "text/plain" } });
  return new Response(calIcs(rec.ev || [], rec.t), { status: 200, headers: { "Content-Type": "text/calendar; charset=utf-8", "Cache-Control": "max-age=1800",
    "Content-Disposition": 'inline; filename="play-next.ics"', "Access-Control-Allow-Origin": "*" } });
}
