/*
 * Shared helpers for the loyalty card pages. Requires bwip-js (global bwipjs).
 */
(function (global) {
    'use strict';

    /**
     * Draws a barcode onto a canvas. Returns false when the text cannot be
     * encoded in that symbology, so the caller can show an error instead.
     */
    function drawBarcode(canvas, bcid, text, twoDimensional, scale) {
        if (!canvas || !bcid || !text || typeof bwipjs === 'undefined') {
            return false;
        }
        var opts = {
            bcid: bcid,
            text: text,
            scale: scale || 4,
            includetext: false,
            backgroundcolor: 'FFFFFF',
            paddingwidth: 4,
            paddingheight: 4
        };
        if (!twoDimensional) {
            opts.height = 15; // bar height in millimetres
        }
        try {
            bwipjs.toCanvas(canvas, opts);
            return true;
        } catch (e) {
            var ctx = canvas.getContext('2d');
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            canvas.width = 0;
            canvas.height = 0;
            return false;
        }
    }

    global.LoyaltyCards = { drawBarcode: drawBarcode };
})(window);
