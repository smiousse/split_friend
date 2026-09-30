package com.splitfriend.service;

import com.splitfriend.dto.LoyaltyPreset;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoyaltyPresetCatalogTest {

    private final LoyaltyPresetCatalog catalog =
            new LoyaltyPresetCatalog(new ClassPathResource(LoyaltyPresetCatalog.DEFAULT_LOCATION));

    @Test
    void loadsTheShippedCatalog() {
        List<LoyaltyPreset> all = catalog.all();
        assertThat(all).hasSizeGreaterThan(30);
        assertThat(catalog.findById("ikea-family")).map(LoyaltyPreset::name).contains("IKEA Family");
        assertThat(catalog.findById("no-such-store")).isEmpty();
        assertThat(catalog.findById(null)).isEmpty();
    }

    @Test
    void everyEntryIsWellFormed() {
        Set<String> ids = new HashSet<>();
        for (LoyaltyPreset p : catalog.all()) {
            assertThat(ids.add(p.id())).as("duplicate id %s", p.id()).isTrue();
            assertThat(p.id()).matches("[a-z0-9-]{1,50}");
            assertThat(p.name()).isNotBlank().hasSizeLessThanOrEqualTo(100);
            assertThat(p.color()).matches("#[0-9a-f]{6}");
            // The domain is interpolated into an outbound URL: host names only.
            assertThat(p.domain()).matches("[a-z0-9-]+(\\.[a-z0-9-]+)+");
        }
    }

    @Test
    void listIsSortedByNameForThePicker() {
        List<String> names = catalog.all().stream().map(LoyaltyPreset::name).toList();
        assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    void initials() {
        assertThat(catalog.findById("pc-optimum").orElseThrow().initials()).isEqualTo("PO");
        assertThat(catalog.findById("costco").orElseThrow().initials()).isEqualTo("C");
    }

    @Test
    void initialsColorContrastsWithTheTile() {
        assertThat(com.splitfriend.model.LoyaltyCard.initialsColorFor("#ffffff")).isEqualTo("#1f2937");
        assertThat(com.splitfriend.model.LoyaltyCard.initialsColorFor("#111111")).isEqualTo("#ffffff");
        assertThat(com.splitfriend.model.LoyaltyCard.initialsColorFor("#0058a3")).isEqualTo("#ffffff");
        assertThat(com.splitfriend.model.LoyaltyCard.initialsColorFor("bogus")).isEqualTo("#ffffff");
    }
}
