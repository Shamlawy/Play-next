/* Page is network-first so a push to GitHub shows on the next open; the cache
   is only for offline. Bump SHELL to wipe old caches (including the old
   hand-loaded "play-next-live" one). */
const SHELL = "play-next-shell-2";
const FILES = ["./", "./index.html", "./manifest.webmanifest",
  "./logo-192.png", "./logo-512.png", "./logo-mask.png"];

self.addEventListener("install", e => {
  self.skipWaiting();
  e.waitUntil(caches.open(SHELL).then(c => c.addAll(FILES)).catch(() => {}));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(k => Promise.all(k.filter(n => n !== SHELL).map(n => caches.delete(n))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", e => {
  const r = e.request;
  if (r.method !== "GET") return;
  const url = new URL(r.url);
  if (url.origin !== self.location.origin) return;   /* RAWG and Steam stay live */

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
