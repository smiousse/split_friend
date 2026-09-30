package com.splitfriend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogoImageProcessorTest {

    private final LogoImageProcessor processor = new LogoImageProcessor();

    @Test
    @DisplayName("a large JPEG is scaled down to fit 256px, keeping its aspect ratio, and re-encoded as PNG")
    void scalesDownAndReencodes() throws IOException {
        byte[] result = processor.process(encode(800, 400, "jpg"));

        BufferedImage out = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(out.getWidth()).isEqualTo(LogoImageProcessor.MAX_DIMENSION);
        assertThat(out.getHeight()).isEqualTo(LogoImageProcessor.MAX_DIMENSION / 2);
        assertThat(isPng(result)).isTrue();
    }

    @Test
    @DisplayName("a small image is not enlarged")
    void doesNotUpscale() throws IOException {
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(processor.process(encode(64, 32, "png"))));
        assertThat(out.getWidth()).isEqualTo(64);
        assertThat(out.getHeight()).isEqualTo(32);
    }

    @Test
    @DisplayName("SVG is refused - it can carry script and is not re-encodable")
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> processor.process(svg)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("bytes that are not an image are refused whatever the claimed content type")
    void rejectsNonImage() {
        assertThatThrownBy(() -> processor.process("not an image".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedUpload() {
        assertThatThrownBy(() -> processor.process(new byte[LogoImageProcessor.MAX_UPLOAD_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MB");
    }

    @Test
    @DisplayName("a tiny file declaring huge dimensions is refused before it is decoded")
    void rejectsDecompressionBomb() throws IOException {
        byte[] bomb = encode(6000, 6000, "png"); // solid black: compresses to a few KB
        assertThat(bomb.length).isLessThan(LogoImageProcessor.MAX_UPLOAD_BYTES);
        assertThatThrownBy(() -> processor.process(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("formats outside PNG/JPEG/GIF are refused even if the JDK can read them")
    void rejectsOtherFormats() throws IOException {
        assertThatThrownBy(() -> processor.process(encode(10, 10, "bmp")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a truncated PNG gets the friendly error, not a 500")
    void rejectsTruncatedImage() throws IOException {
        byte[] png = encode(300, 300, "png");
        byte[] truncated = java.util.Arrays.copyOf(png, 60);
        assertThatThrownBy(() -> processor.process(truncated)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> processor.process(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the etag is stable for the same bytes and changes with them")
    void etag() {
        assertThat(LogoImageProcessor.etagOf(new byte[]{1, 2, 3})).isEqualTo(LogoImageProcessor.etagOf(new byte[]{1, 2, 3}));
        assertThat(LogoImageProcessor.etagOf(new byte[]{1, 2, 3})).isNotEqualTo(LogoImageProcessor.etagOf(new byte[]{1, 2, 4}));
        assertThat(LogoImageProcessor.etagOf(new byte[]{1})).matches("[0-9a-f]{16}");
    }

    private static byte[] encode(int width, int height, String format) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
    }
}
