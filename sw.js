/* Page is network-first so a push to GitHub shows on the next open; the cache
   is only for offline. Bump SHELL to wipe old caches (including the old
   hand-loaded "play-next-live" one). */
const SHELL = "play-next-shell-2";
/* v183: covers and art from Steam, SteamGridDB and RAWG are kept here so a card opens from the phone,
   not the network. Kept apart from SHELL so a shell wipe doesn't throw the pictures away. */
const IMG = "play-next-img-1", IMG_MAX = 350;
const IMG_HOST = /(^|\.)(steamstatic\.com|steamgriddb\.com|rawg\.io|steampowered\.com)$/;
const FILES = ["./", "./index.html", "./manifest.webmanifest",
  "./logo-192.png", "./logo-512.png", "./logo-mask.png"];

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
  const res = await fetch(r);
  if (res && (res.ok || res.type === "opaque")) {
    c.put(r.url, res.clone()).then(() => trim(c)).catch(() => {});
  }
  return res;
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
    icon: "logo-192.png", badge: "logo-192.png", data: { url: m.url || "./" }
  });
}
self.addEventListener("notificationclick", e => {
  e.notification.close();
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
