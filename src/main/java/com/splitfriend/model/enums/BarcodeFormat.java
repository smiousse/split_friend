package com.splitfriend.model.enums;

/**
 * Symbologies a loyalty card can be rendered in.
 *
 * {@code bwipId} is the encoder name bwip-js expects in the browser, and
 * {@code scanName} is the format name the camera scanners report (the
 * {@code BarcodeDetector} API and ZXing both use it, modulo case and
 * underscores), so a scanned card maps straight back onto a value here.
 */
public enum BarcodeFormat {

    CODE128("code128", "code_128", false),
    CODE39("code39", "code_39", false),
    EAN13("ean13", "ean_13", false),
    EAN8("ean8", "ean_8", false),
    UPC_A("upca", "upc_a", false),
    ITF("interleaved2of5", "itf", false),
    CODABAR("rationalizedCodabar", "codabar", false),
    QR("qrcode", "qr_code", true),
    PDF417("pdf417", "pdf417", true),
    AZTEC("azteccode", "aztec", true),
    DATA_MATRIX("datamatrix", "data_matrix", true),
    /** No barcode: the cashier types the number shown on screen. */
    NONE(null, null, false);

    private final String bwipId;
    private final String scanName;
    private final boolean twoDimensional;

    BarcodeFormat(String bwipId, String scanName, boolean twoDimensional) {
        this.bwipId = bwipId;
        this.scanName = scanName;
        this.twoDimensional = twoDimensional;
    }

    public String getBwipId() {
        return bwipId;
    }

    public String getScanName() {
        return scanName;
    }

    public boolean isTwoDimensional() {
        return twoDimensional;
    }
}
