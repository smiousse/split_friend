package com.splitfriend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * A preset's brand logo, fetched once and kept, already re-encoded as a small
 * PNG. {@code data} is null when the last fetch found nothing usable; the row
 * then records when to try again instead of fetching on every page view.
 */
@Entity
@Table(name = "loyalty_preset_logos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyPresetLogo {

    @Id
    @Column(name = "preset_id", length = 50)
    private String presetId;

    @Lob
    @Column(name = "data")
    @ToString.Exclude
    private byte[] data;

    @Column(name = "etag", length = 16)
    private String etag;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Transient
    public boolean hasLogo() {
        return data != null;
    }
}
