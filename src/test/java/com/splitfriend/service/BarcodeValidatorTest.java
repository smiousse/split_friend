package com.splitfriend.service;

import com.splitfriend.model.enums.BarcodeFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarcodeValidatorTest {

    @Test
    @DisplayName("EAN-13 with a correct check digit is kept as-is")
    void ean13WithValidCheckDigit() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.EAN13, "4006381333931")).isEqualTo("4006381333931");
    }

    @Test
    @DisplayName("EAN-13 given 12 digits gets its check digit appended")
    void ean13AppendsCheckDigit() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.EAN13, "400638133393")).isEqualTo("4006381333931");
    }

    @Test
    @DisplayName("EAN-13 with a wrong check digit is rejected - it would fail at the till")
    void ean13WrongCheckDigit() {
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.EAN13, "4006381333932"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("check digit");
    }

    @Test
    void upcAValidAndCompleted() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.UPC_A, "036000291452")).isEqualTo("036000291452");
        assertThat(BarcodeValidator.normalize(BarcodeFormat.UPC_A, "03600029145")).isEqualTo("036000291452");
    }

    @Test
    void ean8ValidAndCompleted() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.EAN8, "96385074")).isEqualTo("96385074");
        assertThat(BarcodeValidator.normalize(BarcodeFormat.EAN8, "9638507")).isEqualTo("96385074");
    }

    @ParameterizedTest
    @CsvSource({
            "EAN13, 12345",
            "EAN13, 40063813339A",
            "UPC_A, 1234567890123",
            "ITF, 12345",
            "ITF, 12a4",
            "CODE39, abc_def",
            "CODABAR, 12E45",
    })
    @DisplayName("numbers the symbology cannot encode are rejected")
    void rejectsUnencodable(BarcodeFormat format, String number) {
        assertThatThrownBy(() -> BarcodeValidator.normalize(format, number))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("whitespace around and inside numeric codes is stripped")
    void stripsSpacesFromNumericCodes() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.EAN13, " 4006 3813 3393 1 ")).isEqualTo("4006381333931");
        assertThat(BarcodeValidator.normalize(BarcodeFormat.ITF, "12 34")).isEqualTo("1234");
    }

    @Test
    @DisplayName("CODE39 is upper-cased, since the symbology has no lower case")
    void code39Uppercased() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.CODE39, "abc-123")).isEqualTo("ABC-123");
    }

    @Test
    @DisplayName("CODABAR without start/stop characters gets A...A added")
    void codabarAddsStartStop() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.CODABAR, "123456")).isEqualTo("A123456A");
        assertThat(BarcodeValidator.normalize(BarcodeFormat.CODABAR, "b123456d")).isEqualTo("B123456D");
    }

    @Test
    @DisplayName("CODE128 keeps inner spaces but rejects control characters")
    void code128() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.CODE128, " AB 12 ")).isEqualTo("AB 12");
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.CODE128, "AB\u000112"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.CODE128, "Café"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("2D codes accept free text, including unicode")
    void twoDimensionalFreeText() {
        assertThat(BarcodeValidator.normalize(BarcodeFormat.QR, "https://example.com/c?id=1 é"))
                .isEqualTo("https://example.com/c?id=1 é");
    }

    @Test
    void blankIsRejected() {
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.NONE, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.QR, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlongIsRejected() {
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.QR, "x".repeat(BarcodeValidator.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BarcodeValidator.normalize(BarcodeFormat.CODE128, "x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
