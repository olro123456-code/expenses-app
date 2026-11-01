var CACHE = 'expenses-v20';
var SHELL = ['./', 'index.html', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'icon-maskable-512.png'];

self.addEventListener('install', function (e) {
  e.waitUntil(caches.open(CACHE).then(function (c) { return c.addAll(SHELL); }).then(function () { return self.skipWaiting(); }));
});

self.addEventListener('activate', function (e) {
  e.waitUntil(caches.keys().then(function (keys) {
    return Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
  }).then(function () { return self.clients.claim(); }));
});

self.addEventListener('fetch', function (e) {
  var req = e.request;
  if (req.method !== 'GET') return;
  var url = new URL(req.url);
  var same = url.origin === location.origin;
  if (!same && url.hostname !== 'cdn.jsdelivr.net') return;
  if (same && url.search) {
    e.respondWith(fetch(req).catch(function () { return caches.match('index.html'); }));
    return;
  }

  function store(res) {
    if (res && (res.status === 200 || res.type === 'opaque')) {
      var copy = res.clone();
      caches.open(CACHE).then(function (c) { c.put(req, copy); });
    }
    return res;
  }

  if (same) {
    // the app itself: network first so updates arrive, cache when offline
    e.respondWith(fetch(req).then(store).catch(function () {
      return caches.match(req).then(function (hit) { return hit || caches.match('index.html'); });
    }));
  } else {
    // scanner library and language files: cache first, so they work offline
    e.respondWith(caches.match(req).then(function (hit) { return hit || fetch(req).then(store); }));
  }
});
