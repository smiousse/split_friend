package com.splitfriend.service;

import com.splitfriend.model.enums.BarcodeFormat;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes a card number for its symbology, or refuses it.
 *
 * The barcode is drawn in the browser, so a number the symbology cannot
 * encode would only surface as a blank space at the till - the worst moment
 * to find out. Rejecting it on save moves that failure to where it can be
 * fixed.
 */
public final class BarcodeValidator {

    /** Upper bound for free-text 2D payloads; well within QR/PDF417 capacity. */
    public static final int MAX_LENGTH = 500;

    private static final int MAX_LINEAR_LENGTH = 80;

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern CODE39_CHARS = Pattern.compile("[0-9A-Z \\-.$/+%]+");
    private static final Pattern CODABAR_BODY = Pattern.compile("[0-9\\-$:/.+]+");
    private static final Pattern CODABAR_FULL = Pattern.compile("[A-D][0-9\\-$:/.+]+[A-D]");
    private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]+");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\t\\r\\n]]");

    private BarcodeValidator() {
        // Utility class
    }

    /**
     * @return the number as it should be stored and encoded
     * @throws IllegalArgumentException with a user-facing reason if it cannot be encoded
     */
    public static String normalize(BarcodeFormat format, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Card number is required");
        }
        String value = raw.strip();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Card number is too long (max " + MAX_LENGTH + " characters)");
        }

        return switch (format) {
            case EAN13 -> withCheckDigit(digitsOnly(value), 13, "EAN-13");
            case EAN8 -> withCheckDigit(digitsOnly(value), 8, "EAN-8");
            case UPC_A -> withCheckDigit(digitsOnly(value), 12, "UPC-A");
            case ITF -> itf(digitsOnly(value));
            case CODE39 -> code39(value.toUpperCase(Locale.ROOT));
            case CODABAR -> codabar(WHITESPACE.matcher(value).replaceAll("").toUpperCase(Locale.ROOT));
            case CODE128 -> code128(value);
            case QR, PDF417, AZTEC, DATA_MATRIX, NONE -> freeText(value);
        };
    }

    private static String digitsOnly(String value) {
        String compact = WHITESPACE.matcher(value).replaceAll("");
        if (!DIGITS.matcher(compact).matches()) {
            throw new IllegalArgumentException("This barcode type only accepts digits");
        }
        return compact;
    }

    /**
     * EAN/UPC: accept the full code (check digit verified) or the code
     * without its check digit (appended), since cards print it either way.
     */
    private static String withCheckDigit(String digits, int fullLength, String label) {
        if (digits.length() == fullLength - 1) {
            return digits + checkDigit(digits);
        }
        if (digits.length() != fullLength) {
            throw new IllegalArgumentException(label + " needs " + (fullLength - 1) + " or " + fullLength + " digits");
        }
        String body = digits.substring(0, fullLength - 1);
        if (digits.charAt(fullLength - 1) != checkDigit(body)) {
            throw new IllegalArgumentException(label + " check digit does not match - check the number");
        }
        return digits;
    }

    /** GS1 mod-10: weights 3,1,3,1... from the rightmost body digit. */
    static char checkDigit(String body) {
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            int digit = body.charAt(body.length() - 1 - i) - '0';
            sum += (i % 2 == 0) ? digit * 3 : digit;
        }
        return (char) ('0' + (10 - sum % 10) % 10);
    }

    private static String itf(String digits) {
        if (digits.length() % 2 != 0) {
            throw new IllegalArgumentException("ITF needs an even number of digits");
        }
        return requireLinearLength(digits);
    }

    private static String code39(String value) {
        if (!CODE39_CHARS.matcher(value).matches()) {
            throw new IllegalArgumentException("Code 39 only accepts letters, digits, space and - . $ / + %");
        }
        return requireLinearLength(value);
    }

    private static String codabar(String value) {
        if (CODABAR_FULL.matcher(value).matches()) {
            return requireLinearLength(value);
        }
        if (!CODABAR_BODY.matcher(value).matches()) {
            throw new IllegalArgumentException("Codabar only accepts digits and - $ : / . +");
        }
        return requireLinearLength("A" + value + "A");
    }

    private static String code128(String value) {
        if (!PRINTABLE_ASCII.matcher(value).matches()) {
            throw new IllegalArgumentException("Code 128 only accepts plain ASCII characters");
        }
        return requireLinearLength(value);
    }

    private static String freeText(String value) {
        if (CONTROL_CHARS.matcher(value).find()) {
            throw new IllegalArgumentException("Card number contains invalid characters");
        }
        return value;
    }

    private static String requireLinearLength(String value) {
        if (value.length() > MAX_LINEAR_LENGTH) {
            throw new IllegalArgumentException("Too long for a 1D barcode (max " + MAX_LINEAR_LENGTH + " characters)");
        }
        return value;
    }
}
