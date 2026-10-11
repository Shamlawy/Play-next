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

async function imgFirst(r) {
  const c = await caches.open(IMG);
  const hit = await c.match(r.url);
  if (hit) return hit;
  const u = new URL(r.url);
  if (u.hostname !== FIT_HOST && !FIT_SKIP.test(u.pathname)) {
    const fit = await fitFetch(r.url);
    if (fit) { c.put(r.url, fit.clone()).then(() => trim(c)).catch(() => {}); return fit; }
  }
  /* ask with CORS first: then the status is readable and only a good picture is kept.
     Hosts that don't allow CORS still load (no-cors), they just aren't kept. */
  let res = null;
  try { res = await fetch(r.url, { mode: "cors", credentials: "omit" }); } catch (e) { res = null; }
  if (res && res.ok) { c.put(r.url, res.clone()).then(() => trim(c)).catch(() => {}); return res; }
  if (res && res.status) return res;
  return fetch(r);
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
