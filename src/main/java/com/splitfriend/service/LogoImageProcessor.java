package com.splitfriend.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Turns an uploaded logo into a small, safe PNG.
 *
 * The upload is decoded and re-drawn rather than stored as sent: that drops
 * EXIF metadata (phone photos carry GPS), and it means nothing the browser
 * receives back was authored by the uploader. SVG is refused outright - it is
 * a document that can carry script, not an image to be resized. Only formats
 * the JDK decodes are accepted (PNG, JPEG, GIF, BMP).
 */
@Component
public class LogoImageProcessor {

    public static final int MAX_DIMENSION = 256;
    public static final int MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

    /**
     * A few-KB PNG can declare 6000x6000 at 16 bits per channel, which decodes
     * to ~300 MB. Refuse anything above this many pixels before decoding, and
     * decode subsampled so memory stays near the output size.
     */
    private static final long MAX_SOURCE_PIXELS = 25_000_000L;
    private static final int DECODE_TARGET = MAX_DIMENSION * 2;
    private static final Set<String> ALLOWED_FORMATS = Set.of("png", "jpeg", "gif");

    public byte[] process(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new IllegalArgumentException("The logo file is empty");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The logo must be smaller than 2 MB");
        }

        BufferedImage source = decode(upload);
        return encodePng(scaleToFit(source));
    }

    /** Short content hash, used as ETag and as the cache-busting URL version. */
    public static String etagOf(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private BufferedImage decode(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                if (!ALLOWED_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw unsupported();
                }
                reader.setInput(in, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_SOURCE_PIXELS) {
                    throw new IllegalArgumentException("The logo image dimensions are too large");
                }
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / DECODE_TARGET);
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException iae) {
                throw iae;
            }
            // Malformed data surfaces from the decoders as assorted runtime exceptions.
            throw unsupported();
        }
    }

    private static BufferedImage scaleToFit(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(width, height));
        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));

        BufferedImage target = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode logo", e);
        }
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("The logo must be a PNG, JPEG or GIF image");
    }
}
