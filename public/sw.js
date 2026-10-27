// Small service-worker platform shim: no private data is read or cached.
const CACHE = 'cadence-shell-v2';
const SHELL = [
  './', './index.html', './styles.css', './manifest.webmanifest',
  './icons/icon.svg', './icons/icon-192.png', './icons/icon-512.png',
  './js/cadence/app.mjs', './js/cadence/model.mjs',
  './js/squint-cljs/core.js', './js/squint-cljs/string.js'
];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;
  event.respondWith(caches.match(req).then(cached => cached || fetch(req).then(res => {
    if (res.ok) {const copy = res.clone(); caches.open(CACHE).then(cache => cache.put(req, copy));}
    return res;
  })));
});
