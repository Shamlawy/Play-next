/* Page is network-first so a push to GitHub shows on the next open; the cache
   is only for offline. Bump SHELL to wipe old caches (including the old
   hand-loaded "play-next-live" one). */
const SHELL = "play-next-shell-2";
/* v183: covers and art from Steam, SteamGridDB and RAWG are kept here so a card opens from the phone,
   not the network. Kept apart from SHELL so a shell wipe doesn't throw the pictures away. */
/* v189: img-1 could hold failed downloads (a no-cors reply hides its status, so a 404 was saved and shown forever).
   Renaming drops it; the activate step below deletes every cache it doesn't know. */
const IMG = "play-next-img-3", IMG_MAX = 1200;   /* v238: screenshots are kept too (5 small copies a game) */
const IMG_HOST = /(^|\.)(steamstatic\.com|steamgriddb\.com|rawg\.io|steampowered\.com|s-microsoft\.com|playstation\.com|nintendo\.com)$/;
/* v267: no picture bigger than the screen. The phone's self-test (v266) found the Queue holding 372 megapixels of
   pictures (1.5 GB once unpacked; one picture alone was 70 MP, 14 were 4K) to show 14 MP: rows went blank on a fast
   swipe and game pages got slower one after another. Every picture from another site is now fetched through the
   resize service (wsrv.nl, already the app's fallback) at most CAP pixels on its long side, without enlarging,
   and kept here under its own address, so every way the app loads a picture (markup, CSS, new Image(), decode())
   gets the screen-sized copy. A Fold unfolded is 2260 device pixels wide: 2560 still fills it sharply. Already small
   files skip the trip; if the service fails or is slow the original loads as before. img-2 (full-size copies) goes. */
const CAP = 2560, FIT_HOST = "wsrv.nl";
const FIT_SKIP = /(\/header\.jpg|\/capsule_[\w]+\.jpg|\/library_600x900\.jpg|\/logo\.png|\.svg|\.gif)(\?|$)/i;
const FILES = ["./", "./index.html", "./manifest.webmanifest",
  "./logo-192.png", "./logo-512.png", "./logo-mask.png", "./badge-96.png"];

self.addEventListener("install", e => {
  self.skipWaiting();
  e.waitUntil(caches.open(SHELL).then(c => c.addAll(FILES)).catch(() => {}));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(k => Promise.all(k.filter(n => n !== SHELL && n !== IMG && n !== "play-next-nudge").map(n => caches.delete(n))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", e => {
  const r = e.request;
  if (r.method !== "GET") return;
  const url = new URL(r.url);
  if (url.origin !== self.location.origin) {
    /* pictures: from the cache if we have them, otherwise fetch and keep a copy. API calls stay live. */
    if (r.destination === "image" && r.mode === "no-cors" && IMG_HOST.test(url.hostname)) e.respondWith(imgFirst(r));
    /* v267: pictures from any other site (and CORS-mode loads) get the same screen-sized copy */
    else if (r.destination === "image" && (r.mode === "no-cors" || r.mode === "cors") && url.protocol === "https:") e.respondWith(imgFirst(r));
    return;
  }

  const isPage = r.mode === "navigate" ||
    url.pathname.endsWith("/") || url.pathname.endsWith("/index.html");
  /* pages skip the browser's HTTP cache so a fresh push is never held back */
  const get = isPage ? fetch(r.url, { cache: "no-cache", credentials: "same-origin" }) : fetch(r);

  e.respondWith(
    get.then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(SHELL).then(c => c.put(r, copy)).catch(() => {}); }
      return res;
    }).catch(() => caches.match(r).then(m => m || (isPage ? caches.match("./index.html") : undefined)))
  );
});

/* v268: the owner after v267: "pictures and art take longer to load, sometimes I wait 3 seconds". Every new picture
   waited for the resize service (it downloads the original itself, then shrinks it), and each size a list asked for
   (layer 2 in index.html: a cover at 128, the same cover at 384 on a game page…) was another full trip. Now the phone
   does it: the original comes straight from its own site as before v267, is shrunk here (off the page's thread) only
   when it's bigger than CAP, and is kept; every sized copy layer 2 asks for (its wsrv.nl address) is cut from that kept
   copy in a few milliseconds, with no network at all. Two requests for the same picture share one download. The
   resize service is only the fallback: sites that don't allow reading their pictures, phones without OffscreenCanvas,
   or a picture the phone can't unpack. */
async function imgFirst(r) {
  const c = await caches.open(IMG);
  const hit = await c.match(r.url);
  if (hit) return hit;
  const u = new URL(r.url);
  try {
    const made = u.hostname === FIT_HOST ? await once("s|" + r.url, () => sizedCopy(u, c))
      : !FIT_SKIP.test(u.pathname) ? await once("b|" + r.url, () => baseCopy(r.url, c)) : null;
    if (made) return new Response(made, { headers: { "content-type": made.type || "image/jpeg" } });
  } catch (e) {}
  /* ask with CORS first: then the status is readable and only a good picture is kept.
     Hosts that don't allow CORS still load (no-cors), they just aren't kept. */
  let res = null;
  try { res = await fetch(r.url, { mode: "cors", credentials: "omit" }); } catch (e) { res = null; }
  if (res && res.ok) { c.put(r.url, res.clone()).then(() => trim(c)).catch(() => {}); return res; }
  if (res && res.status) return res;
  return fetch(r);
}
/* one job per address at a time: the page asking for the original and its sized copy at once downloads it once */
const FITS = new Map();
function once(k, job) {
  if (FITS.has(k)) return FITS.get(k);
  const p = job().catch(() => null).finally(() => FITS.delete(k));
  FITS.set(k, p); return p;
}
/* at most two pictures unpacked at a time (memory) */
let fitBusy = 0; const fitQ = [];
async function fitSlot(job) {
  while (fitBusy >= 2) await new Promise(r => fitQ.push(r));
  fitBusy++;
  try { return await job(); } finally { fitBusy--; const n = fitQ.shift(); if (n) n(); }
}
const keep = (c, k, blob) => c.put(k, new Response(blob, { headers: { "content-type": blob.type || "image/jpeg" } })).then(() => trim(c)).catch(() => {});
/* the kept copy of a picture: its original if it's no bigger than CAP, else shrunk to CAP; null = let the old path try */
async function baseCopy(src, c) {
  let res = null;
  try { res = await fetch(src, { mode: "cors", credentials: "omit" }); } catch (e) {}
  if (!res || !res.ok || !/^image\//.test(res.headers.get("content-type") || "image/")) return viaService(src, c);
  const blob = await res.blob(), d = picSize(new Uint8Array(await blob.slice(0, 65536).arrayBuffer()));
  let out = blob;
  if (!d || Math.max(d[0], d[1]) > CAP) {
    out = await fitSlot(() => shrink(blob, CAP, CAP, false));
    if (!out && d) return (await viaService(src, c)) || blob;   /* kept from the service next time */
    if (!out) out = blob;
  }
  keep(c, src, out);
  return out;
}
/* the resize service, given 1.5s: slower than that, the original shows now and the service's copy is kept for next time */
function viaService(src, c) {
  const job = fitFetch(src).then(f => f && f.blob()).then(b => { if (b) keep(c, src, b); return b || null; }, () => null);
  return Promise.race([job, new Promise(r => setTimeout(() => r(null), 1500))]);
}
/* layer 2's sized copy (wsrv.nl/?url=…&w=…&h=…&fit=outside), cut from the kept copy */
async function sizedCopy(u, c) {
  const p = u.searchParams, src = p.get("url"), w = +p.get("w"), h = +p.get("h");
  if (!src || !/^https:\/\//.test(src) || p.get("fit") !== "outside" || !(w > 0 && h > 0)) return null;
  const k = await c.match(src);
  const base = k ? await k.blob() : await once("b|" + src, () => baseCopy(src, c));
  if (!base) return null;
  const out = await fitSlot(() => shrink(base, w, h, true));
  if (!out) return null;
  keep(c, u.href, out);
  return out;
}
/* outside = covers w×h (a box's copy), else fits inside it; never enlarges; null when this phone can't */
async function shrink(blob, w, h, outside) {
  if (typeof createImageBitmap !== "function" || typeof OffscreenCanvas !== "function") return null;
  let bm = null;
  try {
    bm = await createImageBitmap(blob);
    const s = outside ? Math.max(w / bm.width, h / bm.height) : Math.min(w / bm.width, h / bm.height);
    if (s >= (outside ? .8 : 1)) return blob;   /* barely smaller isn't worth a new copy */
    const W = Math.max(1, Math.round(bm.width * s)), H = Math.max(1, Math.round(bm.height * s));
    const cv = new OffscreenCanvas(W, H), x = cv.getContext("2d");
    x.imageSmoothingEnabled = true; x.imageSmoothingQuality = "high";
    x.drawImage(bm, 0, 0, W, H);
    bm.close(); bm = null;
    let out = await cv.convertToBlob({ type: "image/webp", quality: .86 });
    /* Safari can't write webp: JPEG, or PNG when the picture may be see-through */
    if (out.type !== "image/webp") out = await cv.convertToBlob(/png|webp|gif/.test(blob.type) ? { type: "image/png" } : { type: "image/jpeg", quality: .88 });
    return out;
  } catch (e) { return null; } finally { if (bm && bm.close) bm.close(); }
}
/* width × height from the file's first bytes (PNG, JPEG, WebP), so small pictures are kept without unpacking them */
function picSize(b) {
  const u16 = i => (b[i] << 8) | b[i + 1], le = (i, n) => { let v = 0; for (let k = n - 1; k >= 0; k--) v = v * 256 + b[i + k]; return v; };
  if (b.length > 24 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4E && b[3] === 0x47) return [u16(16) * 65536 + u16(18), u16(20) * 65536 + u16(22)];
  if (b[0] === 0xFF && b[1] === 0xD8) {
    let i = 2;
    while (i + 9 < b.length) {
      if (b[i] !== 0xFF) { i++; continue; }
      const m = b[i + 1];
      if (m === 0xFF) { i++; continue; }
      if (m >= 0xC0 && m <= 0xCF && m !== 0xC4 && m !== 0xC8 && m !== 0xCC) return [u16(i + 7), u16(i + 5)];
      if (m === 0xD8 || m === 0x01 || (m >= 0xD0 && m <= 0xD7)) { i += 2; continue; }
      i += 2 + u16(i + 2);
    }
    return null;
  }
  const tag = String.fromCharCode(b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]);
  if (b.length > 30 && tag === "WEBPVP8 ") return [le(26, 2) & 0x3fff, le(28, 2) & 0x3fff];
  if (b.length > 30 && tag === "WEBPVP8L") return [1 + (((b[22] & 0x3f) << 8) | b[21]), 1 + (((b[24] & 0xf) << 10) | (b[23] << 2) | ((b[22] & 0xc0) >> 6))];
  if (b.length > 30 && tag === "WEBPVP8X") return [1 + le(24, 3), 1 + le(27, 3)];
  return null;
}
async function fitFetch(src) {
  const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), 9000);
  try {
    const res = await fetch("https://" + FIT_HOST + "/?url=" + encodeURIComponent(src) + "&w=" + CAP + "&h=" + CAP + "&fit=inside&we&output=webp&q=86",
      { mode: "cors", credentials: "omit", signal: ctl.signal });
    return res.ok && /^image\//.test(res.headers.get("content-type") || "") ? res : null;
  } catch (e) { return null; } finally { clearTimeout(t); }
}
let trimming = 0;
async function trim(c) {
  if (trimming) return; trimming = 1;
  try { const k = await c.keys(); if (k.length > IMG_MAX) await Promise.all(k.slice(0, k.length - IMG_MAX).map(x => c.delete(x))); }
  finally { trimming = 0; }
}

/* v185 nudges. The helper sends a push at the planned time; Android can also wake us on its own
   (periodic sync) and we show the latest due nudge from the plan the app left in the cache. */
self.addEventListener("push", e => {
  let m = {};
  try { m = e.data ? e.data.json() : {}; } catch (x) { m = { body: e.data && e.data.text() }; }
  e.waitUntil(show(m));
});
function show(m) {
  return self.registration.showNotification(m.title || "Play next", {
    body: m.body || "", tag: "nexi-" + (m.tag || "nudge"), renotify: false,
    /* badge = the small status-bar icon: Android paints it as a white silhouette of its see-through shape,
       so it must be the » on transparent (the full square logo showed as a plain white box) */
    icon: "logo-192.png", badge: "badge-96.png", data: { url: m.url || "./" }
  }).then(iconBadge);
}
/* v221: the home-screen icon shows how many Play next notifications are waiting (iPhone home-screen apps and Android
   launchers that support it); the app clears it when it opens */
function iconBadge() {
  const n = self.navigator;
  if (!n || !n.setAppBadge) return;
  return self.registration.getNotifications().then(l => l.length ? n.setAppBadge(l.length) : n.clearAppBadge()).catch(() => {});
}
self.addEventListener("notificationclick", e => {
  e.notification.close(); iconBadge();
  const url = new URL((e.notification.data && e.notification.data.url) || "./", self.registration.scope).href;
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then(list => {
    const c = list.find(x => x.url.startsWith(self.registration.scope));
    if (c) return c.focus().then(w => w && w.navigate ? w.navigate(url) : null).catch(() => self.clients.openWindow(url));
    return self.clients.openWindow(url);
  }));
});
self.addEventListener("periodicsync", e => {
  if (e.tag !== "nudge") return;
  e.waitUntil((async () => {
    /* pushes already arrive on time: this is only the fallback when push isn't set up */
    if (await self.registration.pushManager.getSubscription().catch(() => null)) return;
    const c = await caches.open("play-next-nudge"), r = await c.match("./nudge-plan.json"); if (!r) return;
    const d = await r.json(), now = Date.now();
    const due = (d.plan || []).filter(n => n.at <= now && now - n.at < 3 * 36e5 && !(d.shown || []).includes(n.at)).pop();
    if (!due) return;
    d.shown = (d.shown || []).concat(due.at).slice(-40);
    await c.put("./nudge-plan.json", new Response(JSON.stringify(d)));
    await show(due);
  })());
});
/* a notification swiped away: one fewer on the icon */
self.addEventListener("notificationclose", () => { iconBadge(); });
