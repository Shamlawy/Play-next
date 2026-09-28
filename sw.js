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
    .then(k => Promise.all(k.filter(n => n !== SHELL && n !== IMG).map(n => caches.delete(n))))
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
