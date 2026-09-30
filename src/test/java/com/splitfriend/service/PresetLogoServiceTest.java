package com.splitfriend.service;

import com.splitfriend.model.LoyaltyPresetLogo;
import com.splitfriend.repository.LoyaltyPresetLogoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PresetLogoServiceTest {

    private final Map<String, LoyaltyPresetLogo> table = new HashMap<>();
    private final AtomicInteger fetches = new AtomicInteger();
    private LoyaltyPresetLogoRepository repository;
    private byte[] served;
    private Instant now = Instant.parse("2026-09-30T12:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(LoyaltyPresetLogoRepository.class);
        when(repository.findById(any())).thenAnswer(inv -> Optional.ofNullable(table.get(inv.<String>getArgument(0))));
        when(repository.save(any())).thenAnswer(inv -> {
            LoyaltyPresetLogo row = inv.getArgument(0);
            table.put(row.getPresetId(), row);
            return row;
        });
    }

    private PresetLogoService service(boolean enabled) {
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        PresetLogoFetcher fetcher = domain -> {
            fetches.incrementAndGet();
            return Optional.ofNullable(served);
        };
        return new PresetLogoService(new LoyaltyPresetCatalog(new ClassPathResource(LoyaltyPresetCatalog.DEFAULT_LOCATION)),
                repository, fetcher, new LogoImageProcessor(), clock, enabled);
    }

    @Test
    @DisplayName("the first request fetches and stores the logo; later ones are served from the table")
    void fetchesOnceThenCaches() throws IOException {
        served = png(180);
        PresetLogoService service = service(true);

        Optional<LoyaltyPresetLogo> first = service.logoFor("ikea-family");
        Optional<LoyaltyPresetLogo> second = service.logoFor("ikea-family");

        assertThat(first).isPresent();
        assertThat(second.get().getEtag()).isEqualTo(first.get().getEtag());
        assertThat(fetches).hasValue(1);
    }

    @Test
    @DisplayName("a failed fetch is remembered, and retried only after the retry window")
    void negativeCache() throws IOException {
        served = null;
        PresetLogoService service = service(true);

        assertThat(service.logoFor("costco")).isEmpty();
        assertThat(service.logoFor("costco")).isEmpty();
        assertThat(fetches).hasValue(1);

        now = now.plus(PresetLogoService.RETRY_AFTER).plus(Duration.ofMinutes(1));
        served = png(180);
        assertThat(service.logoFor("costco")).isPresent();
        assertThat(fetches).hasValue(2);
    }

    @Test
    @DisplayName("an icon too small to look sharp on a card is treated as no logo")
    void rejectsTinyIcons() throws IOException {
        served = png(16);
        assertThat(service(true).logoFor("tim-hortons")).isEmpty();
        assertThat(table.get("tim-hortons").getData()).isNull();
    }

    @Test
    @DisplayName("garbage from the icon service is treated as no logo, not an error")
    void rejectsGarbage() {
        served = "<html>not an image</html>".getBytes();
        assertThat(service(true).logoFor("iga")).isEmpty();
    }

    @Test
    @DisplayName("unknown preset ids never reach the fetcher")
    void unknownPreset() {
        assertThat(service(true).logoFor("../../etc/passwd")).isEmpty();
        assertThat(fetches).hasValue(0);
    }

    @Test
    @DisplayName("with fetching disabled, only logos already stored are served")
    void disabled() throws IOException {
        served = png(180);
        assertThat(service(false).logoFor("ikea-family")).isEmpty();
        assertThat(fetches).hasValue(0);

        table.put("saq-inspire", LoyaltyPresetLogo.builder().presetId("saq-inspire").data(new byte[]{1})
                .etag("e").fetchedAt(LocalDateTime.now()).build());
        assertThat(service(false).logoFor("saq-inspire")).isPresent();
    }

    private static byte[] png(int size) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }
}
