package com.splitfriend.model;

import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetSide;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A household's recurring shared costs, between exactly two people.
 *
 * Deliberately unrelated to {@link Group} and {@link Expense}: those model
 * one-off spending among any number of people, this models a standing
 * arrangement between two. A budget never contributes to group balances or to
 * the dashboard totals - a forward recurring transfer is not a settled
 * position.
 *
 * The two participants are two columns rather than a child collection so that
 * "exactly two" is structural. The schema is managed by
 * {@code ddl-auto: update}, which never adds CHECK constraints, so an
 * invariant that is not expressible in the mapping would have to live in
 * service code forever - and its failure mode here is a wrong dollar figure
 * rather than an exception. For the same reason only {@code shareAPercent} is
 * stored: the two shares then sum to exactly 100 by construction.
 */
@Entity
@Table(name = "budgets", indexes = {
        // Separate, not composite: "budgets I am in" is an OR across the two
        // columns, and a composite index cannot serve its second leg.
        @Index(name = "idx_budget_user_a", columnList = "user_a_id"),
        @Index(name = "idx_budget_user_b", columnList = "user_b_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 1, max = 255)
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 3)
    @Builder.Default
    private String currency = "CAD";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    /**
     * Participant A's cut of the pooled total, 0-100. B's is derived, never
     * stored, so the pair cannot drift out of sum-to-100.
     */
    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(name = "share_a_percent", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal shareAPercent = new BigDecimal("50.00");

    /** The cadence the net transfer is quoted in. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_period", nullable = false, length = 20)
    @Builder.Default
    private BudgetFrequency settlementPeriod = BudgetFrequency.BIWEEKLY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BudgetItem> items = new ArrayList<>();

    /** Participant B's cut. Always {@code 100 - shareAPercent}. */
    @Transient
    public BigDecimal getShareBPercent() {
        BigDecimal shareA = shareAPercent != null ? shareAPercent : BigDecimal.ZERO;
        return ONE_HUNDRED.subtract(shareA);
    }

    public BigDecimal shareOf(BudgetSide side) {
        return side == BudgetSide.A ? shareAPercent : getShareBPercent();
    }

    /** The person on the given side. Never null for a persisted budget. */
    public User userFor(BudgetSide side) {
        return side == BudgetSide.A ? userA : userB;
    }

    /**
     * Which side the given user sits on, or {@code null} if they are not a
     * participant. Callers that need access control must not rely on this -
     * use {@code BudgetService.findForUser} instead.
     */
    public BudgetSide sideOf(Long userId) {
        if (userId == null) {
            return null;
        }
        if (userA != null && userId.equals(userA.getId())) {
            return BudgetSide.A;
        }
        if (userB != null && userId.equals(userB.getId())) {
            return BudgetSide.B;
        }
        return null;
    }

    public boolean hasParticipant(Long userId) {
        return sideOf(userId) != null;
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
