/*
 * The card screen shown at the till: draws the barcode, keeps the screen
 * awake, offers a full-screen focus mode, and records the use for ordering.
 */
(function () {
    'use strict';

    var panel = document.getElementById('card-screen');
    if (!panel) {
        return;
    }

    var bcid = panel.getAttribute('data-bcid');
    var text = panel.getAttribute('data-text');
    var twoD = panel.getAttribute('data-two-d') === 'true';
    var focus = document.getElementById('card-focus');

    LoyaltyCards.drawBarcode(document.getElementById('card-barcode'), bcid, text, twoD, 4);

    // ---- focus mode: tap to enlarge, tap again to close ----
    function openFocus() {
        if (!bcid) {
            return;
        }
        LoyaltyCards.drawBarcode(document.getElementById('card-focus-barcode'), bcid, text, twoD, 6);
        focus.classList.remove('d-none');
        focus.focus();
    }

    function closeFocus() {
        focus.classList.add('d-none');
    }

    panel.addEventListener('click', openFocus);
    panel.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            openFocus();
        }
    });
    focus.addEventListener('click', closeFocus);
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            closeFocus();
        }
    });

    // ---- keep the screen on while the card is shown ----
    var wakeLock = null;

    function requestWakeLock() {
        if (!('wakeLock' in navigator) || document.visibilityState !== 'visible') {
            return;
        }
        navigator.wakeLock.request('screen')
            .then(function (lock) { wakeLock = lock; })
            .catch(function () { /* denied or unsupported: nothing to do */ });
    }

    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible' && wakeLock === null) {
            requestWakeLock();
        } else if (document.visibilityState !== 'visible') {
            wakeLock = null; // the browser releases it when hidden
        }
    });
    requestWakeLock();

    // ---- record the use; its failure is also how we know this is the saved copy ----
    // navigator.onLine stays true on a weak signal, so it cannot tell us that.
    function showOffline() {
        document.getElementById('card-offline').classList.remove('d-none');
    }

    var headers = {};
    headers[panel.getAttribute('data-csrf-header')] = panel.getAttribute('data-csrf-token');
    fetch(panel.getAttribute('data-used-url'), {
        method: 'POST',
        credentials: 'same-origin',
        headers: headers
    }).then(function (response) {
        if (!response.ok) {
            showOffline(); // e.g. a stale CSRF token in a saved copy
        }
    }).catch(showOffline);
})();
