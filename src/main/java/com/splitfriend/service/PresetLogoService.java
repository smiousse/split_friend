package com.splitfriend.service;

import com.splitfriend.dto.LoyaltyPreset;
import com.splitfriend.model.LoyaltyPresetLogo;
import com.splitfriend.repository.LoyaltyPresetLogoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Brand logos for the presets: fetched on first use, then kept in the
 * database so the icon service is contacted once per brand, not per view.
 *
 * Only the brand's domain leaves the server - nothing about the user or
 * their cards. With {@code app.loyalty.preset-logos-enabled=false} nothing is
 * fetched and presets fall back to initials on the brand colour.
 */
@Service
public class PresetLogoService {

    /** How long a "nothing usable" result is trusted before trying again. */
    static final Duration RETRY_AFTER = Duration.ofDays(7);

    /** Below this the icon looks blurry at card size; initials read better. */
    static final int MIN_SOURCE_DIMENSION = 48;

    private final LoyaltyPresetCatalog catalog;
    private final LoyaltyPresetLogoRepository repository;
    private final PresetLogoFetcher fetcher;
    private final LogoImageProcessor processor;
    private final Clock clock;
    private final boolean fetchingEnabled;

    @Autowired
    public PresetLogoService(LoyaltyPresetCatalog catalog,
                             LoyaltyPresetLogoRepository repository,
                             PresetLogoFetcher fetcher,
                             LogoImageProcessor processor,
                             @Value("${app.loyalty.preset-logos-enabled:true}") boolean fetchingEnabled) {
        this(catalog, repository, fetcher, processor, Clock.systemDefaultZone(), fetchingEnabled);
    }

    PresetLogoService(LoyaltyPresetCatalog catalog,
                      LoyaltyPresetLogoRepository repository,
                      PresetLogoFetcher fetcher,
                      LogoImageProcessor processor,
                      Clock clock,
                      boolean fetchingEnabled) {
        this.catalog = catalog;
        this.repository = repository;
        this.fetcher = fetcher;
        this.processor = processor;
        this.clock = clock;
        this.fetchingEnabled = fetchingEnabled;
    }

    /** The preset's logo, or empty if it is unknown or has no usable logo. */
    public Optional<LoyaltyPresetLogo> logoFor(String presetId) {
        Optional<LoyaltyPreset> preset = catalog.findById(presetId);
        if (preset.isEmpty()) {
            return Optional.empty();
        }

        Optional<LoyaltyPresetLogo> stored = repository.findById(presetId);
        if (stored.isPresent() && (stored.get().hasLogo() || !retryDue(stored.get()))) {
            return stored.filter(LoyaltyPresetLogo::hasLogo);
        }
        if (!fetchingEnabled) {
            return stored.filter(LoyaltyPresetLogo::hasLogo);
        }

        byte[] logo = fetcher.fetch(preset.get().domain()).map(this::toLogo).orElse(null);
        LoyaltyPresetLogo row = LoyaltyPresetLogo.builder()
                .presetId(presetId)
                .data(logo)
                .etag(logo != null ? LogoImageProcessor.etagOf(logo) : null)
                .fetchedAt(LocalDateTime.now(clock))
                .build();
        try {
            repository.save(row);
        } catch (DataIntegrityViolationException e) {
            // The picker loads every preset logo at once, so two requests can
            // fetch the same brand; the other one stored it first.
            return repository.findById(presetId).filter(LoyaltyPresetLogo::hasLogo);
        }
        return Optional.of(row).filter(LoyaltyPresetLogo::hasLogo);
    }

    private byte[] toLogo(byte[] raw) {
        try {
            return processor.process(raw, MIN_SOURCE_DIMENSION);
        } catch (IllegalArgumentException e) {
            return null; // not an image, or too small to look right
        }
    }

    private boolean retryDue(LoyaltyPresetLogo row) {
        return row.getFetchedAt().plus(RETRY_AFTER).isBefore(LocalDateTime.now(clock));
    }
}
