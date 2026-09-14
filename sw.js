/* Two caches:
   SHELL   — the hosted files, for offline.
   LIVE    — an index.html you loaded by hand from Settings. When it holds
             something, it wins over the hosted copy for page loads.
   Escape hatch: <url>?fresh empties LIVE and falls through to the network, so a
   broken upload can always be undone from the address bar. */
const SHELL = "play-next-shell";
const LIVE = "play-next-live";
const LIVE_KEY = "live-index";
const FILES = ["./", "./index.html", "./manifest.webmanifest",
  "./logo-192.png", "./logo-512.png", "./logo-mask.png"];

self.addEventListener("install", e => {
  self.skipWaiting();
  e.waitUntil(caches.open(SHELL).then(c => c.addAll(FILES)).catch(() => {}));
});

self.addEventListener("activate", e => {
  e.waitUntil(caches.keys()
    .then(k => Promise.all(k.filter(n => n !== SHELL && n !== LIVE).map(n => caches.delete(n))))
    .then(() => self.clients.claim()));
});

self.addEventListener("message", e => {
  const m = e.data || {};
  if (m.type === "put-live") {
    e.waitUntil(caches.open(LIVE)
      .then(c => c.put(LIVE_KEY, new Response(m.html, { headers: { "Content-Type": "text/html; charset=utf-8" } })))
      .then(() => e.source && e.source.postMessage({ type: "live-ok" }))
      .catch(err => e.source && e.source.postMessage({ type: "live-fail", why: String(err) })));
  }
  if (m.type === "drop-live") {
    e.waitUntil(caches.delete(LIVE)
      .then(() => e.source && e.source.postMessage({ type: "live-dropped" })));
  }
  if (m.type === "has-live") {
    e.waitUntil(caches.open(LIVE).then(c => c.match(LIVE_KEY))
      .then(r => e.source && e.source.postMessage({ type: "live-state", on: !!r })));
  }
});

self.addEventListener("fetch", e => {
  const r = e.request;
  if (r.method !== "GET") return;
  const url = new URL(r.url);
  if (url.origin !== self.location.origin) return;   /* RAWG and Steam stay live */

  const isPage = r.mode === "navigate" ||
    url.pathname.endsWith("/") || url.pathname.endsWith("/index.html");

  if (isPage && url.searchParams.has("fresh")) {
    e.respondWith(caches.delete(LIVE).then(() => fetch(r)).catch(() => caches.match("./index.html")));
    return;
  }

  if (isPage) {
    e.respondWith(
      caches.open(LIVE).then(c => c.match(LIVE_KEY)).then(hit => {
        if (hit) return hit;
        return fetch(r).then(res => {
          const copy = res.clone();
          caches.open(SHELL).then(c => c.put(r, copy)).catch(() => {});
          return res;
        }).catch(() => caches.match(r).then(m => m || caches.match("./index.html")));
      })
    );
    return;
  }

  e.respondWith(
    fetch(r).then(res => {
      const copy = res.clone();
      caches.open(SHELL).then(c => c.put(r, copy)).catch(() => {});
      return res;
    }).catch(() => caches.match(r))
  );
});
