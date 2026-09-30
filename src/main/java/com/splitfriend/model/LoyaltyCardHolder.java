package com.splitfriend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * One person's access to a {@link LoyaltyCard}, owner included.
 *
 * Pinning and usage are per holder rather than on the card, so two people
 * sharing a card each get their own ordering. {@code pinned} and
 * {@code useCount} are nullable because {@code ddl-auto: update} does not
 * backfill; read them through the accessors below.
 */
@Entity
@Table(name = "loyalty_card_holders",
        uniqueConstraints = @UniqueConstraint(name = "uk_loyalty_holder_card_user", columnNames = {"card_id", "user_id"}),
        indexes = @Index(name = "idx_loyalty_holder_user", columnList = "user_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyCardHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private LoyaltyCard card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "pinned")
    @Builder.Default
    private Boolean pinned = false;

    @Column(name = "use_count")
    @Builder.Default
    private Integer useCount = 0;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "added_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();

    @Transient
    public boolean isPinnedFlag() {
        return Boolean.TRUE.equals(pinned);
    }

    @Transient
    public int getUseCountValue() {
        return useCount != null ? useCount : 0;
    }
}
