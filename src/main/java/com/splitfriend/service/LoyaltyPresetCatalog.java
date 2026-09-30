package com.splitfriend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitfriend.dto.LoyaltyPreset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The known loyalty programs, loaded once from {@code loyalty-presets.json}.
 *
 * Brand logos are deliberately not in the repository (it is public, and the
 * artwork is not ours to redistribute); {@link PresetLogoService} fetches
 * each one on first use and keeps it in the database.
 */
@Component
public class LoyaltyPresetCatalog {

    static final String DEFAULT_LOCATION = "loyalty-presets.json";

    private final List<LoyaltyPreset> presets;
    private final Map<String, LoyaltyPreset> byId;

    @Autowired
    public LoyaltyPresetCatalog() {
        this(new ClassPathResource(DEFAULT_LOCATION));
    }

    LoyaltyPresetCatalog(Resource source) {
        try (InputStream in = source.getInputStream()) {
            this.presets = new ObjectMapper().readValue(in, new TypeReference<List<LoyaltyPreset>>() { })
                    .stream()
                    .sorted(Comparator.comparing(LoyaltyPreset::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read loyalty presets from " + source, e);
        }
        this.byId = presets.stream().collect(Collectors.toUnmodifiableMap(LoyaltyPreset::id, Function.identity()));
    }

    public List<LoyaltyPreset> all() {
        return presets;
    }

    public Optional<LoyaltyPreset> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }
}
