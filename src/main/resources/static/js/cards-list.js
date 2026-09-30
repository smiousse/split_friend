/*
 * Card grid: client-side search, and asking the service worker to save every
 * card screen and logo so they open without a connection.
 */
(function () {
    'use strict';

    var search = document.getElementById('card-search');
    var tiles = Array.prototype.slice.call(document.querySelectorAll('.card-tile-col'));

    if (search) {
        search.addEventListener('input', function () {
            var q = search.value.trim().toLowerCase();
            tiles.forEach(function (tile) {
                var match = !q || tile.getAttribute('data-name').indexOf(q) !== -1;
                tile.classList.toggle('d-none', !match);
            });
        });
    }

    if (navigator.onLine && 'serviceWorker' in navigator) {
        navigator.serviceWorker.ready.then(function (registration) {
            if (!registration.active) {
                return;
            }
            var urls = [];
            tiles.forEach(function (tile) {
                urls.push(tile.getAttribute('data-card-url'));
                var logo = tile.getAttribute('data-logo-url');
                if (logo) {
                    urls.push(logo);
                }
            });
            registration.active.postMessage({ type: 'cache-cards', urls: urls });
        });
    }
})();
