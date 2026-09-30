package com.splitfriend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Fetches brand icons through a favicon service (Google's by default).
 *
 * The URL is the configured template with the preset's domain substituted in;
 * the domain comes from the shipped catalog, never from a request, so this
 * cannot be pointed at an arbitrary host. Responses are size-capped and must
 * be an image; the bytes are re-encoded by {@link LogoImageProcessor} before
 * anything is stored.
 */
@Component
public class HttpPresetLogoFetcher implements PresetLogoFetcher {

    private static final Logger log = LoggerFactory.getLogger(HttpPresetLogoFetcher.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private final String urlTemplate;
    private final HttpClient client;

    public HttpPresetLogoFetcher(@Value("${app.loyalty.preset-logo-url}") String urlTemplate) {
        this.urlTemplate = urlTemplate;
        this.client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Optional<byte[]> fetch(String domain) {
        URI uri = URI.create(urlTemplate.replace("{domain}", URLEncoder.encode(domain, StandardCharsets.UTF_8)));
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                String type = response.headers().firstValue("Content-Type").orElse("");
                if (response.statusCode() != 200 || !type.startsWith("image/")) {
                    return Optional.empty();
                }
                byte[] bytes = body.readNBytes(LogoImageProcessor.MAX_UPLOAD_BYTES + 1);
                return bytes.length > LogoImageProcessor.MAX_UPLOAD_BYTES ? Optional.empty() : Optional.of(bytes);
            }
        } catch (IOException e) {
            log.info("Could not fetch preset logo for {}: {}", domain, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
