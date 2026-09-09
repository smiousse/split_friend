package com.splitfriend.model;

import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetItemType;
import com.splitfriend.model.enums.BudgetSide;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One recurring cost in a budget: rent, internet, a streaming subscription.
 *
 * {@code amount} is always a positive magnitude and {@code paidBySide} carries
 * the direction, mirroring {@link Expense}. Allowing a signed amount as well
 * as a side would make the same fact expressible two ways and invite
 * double-negation bugs.
 */
@Entity
@Table(name = "budget_items", indexes = {
        @Index(name = "idx_budget_item_budget", columnList = "budget_id, sort_order")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Budget budget;

    @NotBlank
    @Column(length = 255, nullable = false)
    private String label;

    /** Gross cost for one occurrence of {@link #frequency}. */
    @NotNull
    @Positive
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BudgetFrequency frequency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "paid_by_side", nullable = false, length = 1)
    private BudgetSide paidBySide;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    @Builder.Default
    private BudgetItemType itemType = BudgetItemType.SHARED;

    /** Display order, so the list can mirror the household's own ordering. */
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    /** Soft-disable, so a cancelled subscription keeps its history. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    @Transient
    public boolean isShared() {
        return itemType == BudgetItemType.SHARED;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
