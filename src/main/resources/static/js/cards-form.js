/*
 * Add/edit card form: live barcode preview and camera scanning.
 *
 * Scanning uses the native BarcodeDetector where it exists (Chrome on
 * Android) and falls back to ZXing, loaded only when needed (iOS Safari,
 * Firefox). The camera requires HTTPS or localhost.
 */
(function () {
    'use strict';

    var numberInput = document.getElementById('cardNumber');
    var formatSelect = document.getElementById('barcodeFormat');
    var preview = document.getElementById('barcode-preview');
    var previewError = document.getElementById('preview-error');
    var scanBtn = document.getElementById('scan-btn');
    var scanMessage = document.getElementById('scan-message');
    var i18n = document.getElementById('card-form-i18n');
    var modalEl = document.getElementById('scan-modal');
    var video = document.getElementById('scan-video');

    // ---------- preview ----------

    function selectedOption() {
        return formatSelect.options[formatSelect.selectedIndex];
    }

    function renderPreview() {
        var opt = selectedOption();
        var bcid = opt.getAttribute('data-bcid');
        var text = numberInput.value.trim();
        if (!bcid || !text) {
            preview.classList.add('d-none');
            previewError.classList.add('d-none');
            return;
        }
        var ok = LoyaltyCards.drawBarcode(preview, bcid, text, opt.getAttribute('data-two-d') === 'true', 3);
        preview.classList.toggle('d-none', !ok);
        previewError.classList.toggle('d-none', ok);
    }

    var previewTimer = null;
    numberInput.addEventListener('input', function () {
        clearTimeout(previewTimer);
        previewTimer = setTimeout(renderPreview, 250);
    });
    formatSelect.addEventListener('change', renderPreview);
    renderPreview();

    // ---------- scanning ----------

    function formatKey(name) {
        return (name || '').toLowerCase().replace(/_/g, '');
    }

    function showScanMessage(text) {
        scanMessage.textContent = text || '';
        scanMessage.classList.toggle('d-none', !text);
    }

    /** Fills the form from a scan; the format is set only if we support it. */
    function applyScan(rawValue, formatName) {
        numberInput.value = rawValue;
        var key = formatKey(formatName);
        var match = Array.prototype.find.call(formatSelect.options, function (o) {
            return o.getAttribute('data-scan') && formatKey(o.getAttribute('data-scan')) === key;
        });
        if (match) {
            formatSelect.value = match.value;
            showScanMessage('');
        } else {
            showScanMessage(i18n.getAttribute('data-unknown-format'));
        }
        renderPreview();
    }

    var modal = null;
    var stream = null;
    var zxingReader = null;
    var detectLoop = null;
    // Incremented on every start and stop. A camera grant or decoder load that
    // resolves after the user closed the scanner sees a newer session and
    // releases what it got instead of scanning inside a hidden modal.
    var session = 0;

    function stopScanning() {
        session += 1;
        if (detectLoop) {
            clearTimeout(detectLoop);
            detectLoop = null;
        }
        if (zxingReader) {
            zxingReader.reset();
            zxingReader = null;
        }
        if (stream) {
            stream.getTracks().forEach(function (t) { t.stop(); });
            stream = null;
        }
        video.srcObject = null;
    }

    function onDetected(value, format) {
        stopScanning();
        modal.hide();
        applyScan(value, format);
    }

    function nativeDetector() {
        if (!('BarcodeDetector' in window)) {
            return Promise.resolve(null);
        }
        return BarcodeDetector.getSupportedFormats()
            .then(function (formats) { return formats.length ? new BarcodeDetector({ formats: formats }) : null; })
            .catch(function () { return null; });
    }

    function scanNative(detector) {
        function tick() {
            if (!stream) {
                return;
            }
            detector.detect(video).then(function (codes) {
                if (codes.length) {
                    onDetected(codes[0].rawValue, codes[0].format);
                } else {
                    detectLoop = setTimeout(tick, 150);
                }
            }).catch(function () {
                detectLoop = setTimeout(tick, 300);
            });
        }
        tick();
    }

    function loadZxing() {
        if (window.ZXing) {
            return Promise.resolve(window.ZXing);
        }
        return new Promise(function (resolve, reject) {
            var script = document.createElement('script');
            script.src = i18n.getAttribute('data-zxing-url');
            script.onload = function () { resolve(window.ZXing); };
            script.onerror = reject;
            document.head.appendChild(script);
        });
    }

    function scanZxing(ZXing) {
        zxingReader = new ZXing.BrowserMultiFormatReader();
        zxingReader.decodeFromStream(stream, video, function (result) {
            if (result) {
                onDetected(result.getText(), ZXing.BarcodeFormat[result.getBarcodeFormat()]);
            }
        }).catch(function () { /* stopped */ });
    }

    function startScanning() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            showScanMessage(i18n.getAttribute('data-unsupported'));
            return;
        }
        stopScanning(); // a second tap must not leak the first camera stream
        var mine = session;
        modal = modal || new bootstrap.Modal(modalEl);

        function stale() {
            return mine !== session;
        }

        // The modal opens only once the camera is granted: hiding a modal whose
        // show animation is still running is a no-op in Bootstrap, which would
        // leave it stuck open over the error message.
        navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
            .then(function (s) {
                if (stale()) {
                    s.getTracks().forEach(function (t) { t.stop(); });
                    return null;
                }
                stream = s;
                video.srcObject = s;
                modal.show();
                return video.play()
                    .then(nativeDetector)
                    .then(function (detector) {
                        if (stale()) {
                            return null;
                        }
                        if (detector) {
                            scanNative(detector);
                            return null;
                        }
                        return loadZxing().then(function (ZXing) {
                            if (!stale()) {
                                scanZxing(ZXing);
                            }
                        });
                    });
            })
            .catch(function () {
                if (stale()) {
                    return;
                }
                stopScanning();
                modal.hide();
                showScanMessage(i18n.getAttribute('data-unsupported'));
            });
    }

    scanBtn.addEventListener('click', startScanning);
    modalEl.addEventListener('hidden.bs.modal', stopScanning);
})();
