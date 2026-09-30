package com.splitfriend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A card's logo, already re-encoded as a small PNG.
 *
 * Stored in the database rather than under {@code ./uploads}: the backup is a
 * SQL script, so files on disk would not survive a restore, and
 * {@code /uploads/**} is served to any signed-in user without an ownership
 * check. Keyed by the card id; there is no JPA relation, so nothing loads the
 * bytes except the logo endpoint.
 */
@Entity
@Table(name = "loyalty_card_logos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyCardLogo {

    @Id
    @Column(name = "card_id")
    private Long cardId;

    @Lob
    @Column(name = "data", nullable = false)
    @ToString.Exclude
    private byte[] data;

    @Column(name = "content_type", nullable = false, length = 50)
    @Builder.Default
    private String contentType = "image/png";

    @Column(name = "etag", nullable = false, length = 16)
    private String etag;
}
