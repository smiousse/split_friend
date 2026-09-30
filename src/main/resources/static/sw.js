const CACHE_NAME = 'splitfriend-v2';

// Loyalty card screens are opened at the till, often on poor signal: the
// network gets this long before the saved copy is shown instead.
const CARD_NETWORK_TIMEOUT_MS = 3000;
const CARD_PAGE = /^\/cards(\/\d+)?\/?$/;
const CARD_SCREEN = /^\/cards\/\d+\/?$/;
const CARD_LOGO = /^\/cards\/\d+\/logo$/;
const MAX_PRECACHE_URLS = 300;
const CARD_ASSETS = [
  '/webjars/bwip-js/4.5.1/dist/bwip-js-min.js',
  '/js/cards-common.js',
  '/js/cards-view.js',
  '/js/cards-list.js'
];

// Bumped by a purge (logout, login page). A fetch started before the purge
// must not write its result back afterwards, so writes carry the generation
// they started in and are dropped if it has moved on.
let generation = 0;

// A redirect is usually the login page answering for an expired session;
// saving it under the original URL would replace the real page offline.
function isCacheable(response) {
  return response && response.ok && !response.redirected && response.type === 'basic';
}

// Access to a card was revoked (unshared, left, deleted) or the session is
// gone: the saved copy must go too, or it keeps showing the card offline.
function isRevoked(response) {
  return response && (response.status === 401 || response.status === 403 || response.status === 404
    || response.redirected || response.type === 'opaqueredirect');
}

function putInCache(request, response, startedIn) {
  if (startedIn !== generation || !isCacheable(response)) {
    return Promise.resolve();
  }
  const copy = response.clone();
  return caches.open(CACHE_NAME).then((cache) => (startedIn === generation ? cache.put(request, copy) : undefined));
}

/** Caches a card page or logo response, or forgets it if access is gone. */
function syncCardEntry(request, response, startedIn) {
  if (isRevoked(response)) {
    return caches.open(CACHE_NAME).then((cache) => cache.delete(request));
  }
  return putInCache(request, response, startedIn);
}

function purgeAll() {
  generation += 1;
  return caches.keys().then((names) => Promise.all(names.map((name) => caches.delete(name))));
}

const STATIC_ASSETS = [
  '/css/style.css',
  '/js/app.js',
  '/manifest.json',
  '/icons/icon-192x192.png',
  '/icons/icon-512x512.png'
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
  console.log('[SW] Installing service worker...');
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => {
        console.log('[SW] Caching static assets');
        return cache.addAll(STATIC_ASSETS);
      })
      .then(() => self.skipWaiting())
  );
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
  console.log('[SW] Activating service worker...');
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames
          .filter((name) => name !== CACHE_NAME)
          .map((name) => {
            console.log('[SW] Deleting old cache:', name);
            return caches.delete(name);
          })
      );
    }).then(() => self.clients.claim())
  );
});

// Fetch event - network first, fallback to cache
self.addEventListener('fetch', (event) => {
  // Skip non-GET requests
  if (event.request.method !== 'GET') {
    return;
  }

  // Skip cross-origin requests
  if (!event.request.url.startsWith(self.location.origin)) {
    return;
  }

  const url = new URL(event.request.url);

  // Card logos: URLs are versioned (?v=etag), so a saved copy is never stale.
  if (CARD_LOGO.test(url.pathname)) {
    const startedIn = generation;
    event.respondWith(
      caches.match(event.request).then((cached) => cached || fetch(event.request).then((response) => {
        syncCardEntry(event.request, response, startedIn);
        return response;
      }))
    );
    return;
  }

  // Card list and card screens: network first, but fall back to the saved
  // copy quickly. The network request keeps running after the timeout, so
  // its answer still refreshes (or revokes) the saved copy.
  if (event.request.mode === 'navigate' && CARD_PAGE.test(url.pathname)) {
    const startedIn = generation;
    const network = fetch(event.request);
    network.then((response) => syncCardEntry(event.request, response, startedIn)).catch(() => {});

    const timeout = new Promise((resolve) => setTimeout(resolve, CARD_NETWORK_TIMEOUT_MS));
    event.respondWith(
      Promise.race([network, timeout.then(() => null)])
        .catch(() => null)
        .then((response) => response || caches.match(event.request)
          .then((cached) => cached || network)
          .catch(() => caches.match('/offline.html')))
    );
    return;
  }

  // Other pages are never saved: they carry balances, API tokens and admin
  // data that must not be readable offline by the next person on the device.
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request).catch(() => caches.match('/offline.html'))
    );
    return;
  }

  // For static assets, use cache-first strategy
  if (event.request.url.match(/\.(css|js|png|jpg|jpeg|gif|svg|ico|woff|woff2)$/)) {
    event.respondWith(
      caches.match(event.request).then((cachedResponse) => {
        if (cachedResponse) {
          // Return cached version and update cache in background
          fetch(event.request).then((response) => {
            caches.open(CACHE_NAME).then((cache) => {
              cache.put(event.request, response);
            });
          }).catch(() => {});
          return cachedResponse;
        }
        return fetch(event.request).then((response) => {
          const responseClone = response.clone();
          caches.open(CACHE_NAME).then((cache) => {
            cache.put(event.request, responseClone);
          });
          return response;
        });
      })
    );
    return;
  }

  // For API requests, use network-first
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        return response;
      })
      .catch(() => {
        return caches.match(event.request);
      })
  );
});

// Push notification event - display notification
self.addEventListener('push', (event) => {
  console.log('[SW] Push received');

  let data = {
    title: 'SplitFriend',
    body: 'You have a new notification',
    url: '/dashboard'
  };

  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      console.error('[SW] Error parsing push data:', e);
    }
  }

  const options = {
    body: data.body,
    icon: '/icons/icon-192x192.png',
    badge: '/icons/icon-192x192.png',
    vibrate: [100, 50, 100],
    data: {
      url: data.url || '/dashboard'
    },
    actions: [
      { action: 'open', title: 'Open' },
      { action: 'dismiss', title: 'Dismiss' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(data.title, options)
  );
});

// Notification click event - open app at specified URL
self.addEventListener('notificationclick', (event) => {
  console.log('[SW] Notification click received');

  event.notification.close();

  if (event.action === 'dismiss') {
    return;
  }

  const urlToOpen = event.notification.data?.url || '/dashboard';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((windowClients) => {
        // Check if there's already a window open
        for (const client of windowClients) {
          if (client.url.includes(self.location.origin) && 'focus' in client) {
            client.navigate(urlToOpen);
            return client.focus();
          }
        }
        // Open a new window if none exists
        if (clients.openWindow) {
          return clients.openWindow(urlToOpen);
        }
      })
  );
});

// The card list asks for every card screen and logo to be saved, so a card
// never opened on this device still works offline at the till. The list is
// the complete set: saved cards missing from it were unshared or deleted.
self.addEventListener('message', (event) => {
  const data = event.data || {};

  if (data.type === 'purge') {
    event.waitUntil(purgeAll());
    return;
  }
  if (data.type !== 'cache-cards' || !Array.isArray(data.urls)) {
    return;
  }

  const startedIn = generation;
  const wanted = new Set();
  data.urls.concat(CARD_ASSETS).slice(0, MAX_PRECACHE_URLS).forEach((u) => {
    if (typeof u !== 'string') {
      return;
    }
    try {
      const parsed = new URL(u, self.location.origin);
      if (parsed.origin === self.location.origin
        && (CARD_PAGE.test(parsed.pathname) || CARD_LOGO.test(parsed.pathname) || CARD_ASSETS.includes(parsed.pathname))) {
        wanted.add(parsed.href);
      }
    } catch (e) {
      // not a URL: ignore
    }
  });

  const prune = caches.open(CACHE_NAME).then((cache) => cache.keys().then((requests) => Promise.all(
    requests
      .filter((r) => {
        const path = new URL(r.url).pathname;
        return (CARD_SCREEN.test(path) || CARD_LOGO.test(path)) && !wanted.has(r.url);
      })
      .map((r) => cache.delete(r))
  )));

  const fetches = Array.from(wanted).map((href) =>
    fetch(href, { credentials: 'same-origin' })
      .then((response) => syncCardEntry(href, response, startedIn))
      .catch(() => {}));

  event.waitUntil(Promise.all([prune].concat(fetches)));
});
