const CACHE_NAME = "fvg-pwa-v8";
const CORE_ASSETS = [
  "./",
  "./index.html",
  "./styles.css",
  "./engine.js",
  "./firebase-config.js",
  "./app.js",
  "./manifest.webmanifest",
  "./assets/fvg-icon.svg",
  "./assets/fvg_logo_dark.png",
  "./assets/fvg_logo_light.png",
  "./assets/zahra_yousif_portrait.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(CORE_ASSETS)).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((res) => {
        if (res && res.status === 200 && res.type === "basic") {
          const clone = res.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return res;
      }).catch(() => caches.match("./index.html"));
    })
  );
});
