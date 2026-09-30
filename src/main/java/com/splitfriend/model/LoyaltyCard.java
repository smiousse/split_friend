package com.splitfriend.model;

import com.splitfriend.model.enums.BarcodeFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A merchant loyalty card: a number, the symbology it is printed in, and an
 * optional logo.
 *
 * The barcode itself is never stored - it is drawn in the browser from
 * {@code cardNumber}, which keeps it sharp at any size and available offline.
 *
 * Everyone who can see the card, the owner included, has a
 * {@link LoyaltyCardHolder} row; that is what list and access queries join
 * on. Only the owner may change the card or who it is shared with.
 *
 * The logo lives in {@link LoyaltyCardLogo}, a separate table, so listing
 * cards never loads image bytes. {@code logoEtag} is non-null exactly when a
 * logo exists and doubles as the cache-busting version in its URL.
 */
@Entity
@Table(name = "loyalty_cards", indexes = {
        @Index(name = "idx_loyalty_card_owner", columnList = "owner_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyCard {

    public static final String DEFAULT_COLOR = "#475569";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User owner;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "card_number", nullable = false, length = 500)
    private String cardNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "barcode_format", nullable = false, length = 20)
    @Builder.Default
    private BarcodeFormat barcodeFormat = BarcodeFormat.CODE128;

    /** Tile background, {@code #rrggbb}. Shown behind the initials when there is no logo. */
    @Column(length = 7)
    @Builder.Default
    private String color = DEFAULT_COLOR;

    @Column(length = 500)
    private String note;

    @Column(name = "logo_etag", length = 16)
    private String logoEtag;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<LoyaltyCardHolder> holders = new ArrayList<>();

    @Transient
    public boolean hasLogo() {
        return logoEtag != null;
    }

    @Transient
    public boolean isOwnedBy(Long userId) {
        return owner != null && userId != null && userId.equals(owner.getId());
    }

    /** Up to two letters for the logo-less tile. */
    @Transient
    public String getInitials() {
        return initialsOf(merchantName);
    }

    /** Dark initials on light tiles, white on dark ones. */
    @Transient
    public String getInitialsColor() {
        return initialsColorFor(color);
    }

    /** Relative luminance (sRGB, approximated) above which white text stops reading. */
    public static String initialsColorFor(String hex) {
        if (hex == null || !hex.matches("^#[0-9a-fA-F]{6}$")) {
            return "#ffffff";
        }
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
        return luminance > 0.6 ? "#1f2937" : "#ffffff";
    }

    public static String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] words = java.util.Arrays.stream(name.strip().split("[\\s-]+"))
                .filter(w -> !w.isEmpty())
                .toArray(String[]::new);
        if (words.length == 0) {
            return "?";
        }
        String first = words[0].substring(0, 1);
        String second = words.length > 1 ? words[1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
