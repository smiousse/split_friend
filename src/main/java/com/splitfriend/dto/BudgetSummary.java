package com.splitfriend.dto;

import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetSide;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the budget view needs: each side's totals, and the single net
 * transfer that settles the period.
 *
 * Produced by {@link com.splitfriend.service.BudgetCalculationService}. Every
 * amount is already normalized to {@link #period} and rounded to cents; the
 * view does no arithmetic. Carries user ids and display names rather than
 * {@code User} entities, so no e-mail address can reach a template.
 *
 * @param period          the cadence all amounts are quoted in
 * @param netAmount       always a positive magnitude; zero when settled
 * @param payingSide      who sends {@code netAmount}, or {@code null} when settled
 * @param monthlyEquivalent {@code netAmount} restated per month, for reference
 */
public record BudgetSummary(
        BudgetFrequency period,
        SideTotals sideA,
        SideTotals sideB,
        BigDecimal pooledTotal,
        BigDecimal netAmount,
        BudgetSide payingSide,
        String payingUserName,
        String receivingUserName,
        BigDecimal monthlyEquivalent,
        List<BudgetItemView> items
) {

    /**
     * One participant's position.
     *
     * @param paidShared   what they pay into the pooled total
     * @param absorbed     what they pay outside the split entirely
     * @param owedByOther  the other participant's cut of {@code paidShared}
     * @param owesOther    their own cut of the other participant's shared total
     */
    public record SideTotals(
            BudgetSide side,
            Long userId,
            String name,
            BigDecimal sharePercent,
            BigDecimal paidShared,
            BigDecimal absorbed,
            BigDecimal totalOutlay,
            BigDecimal owedByOther,
            BigDecimal owesOther
    ) {}

    /** True when neither participant owes the other anything worth moving. */
    public boolean isSettled() {
        return payingSide == null;
    }

    /** Both sides in a fixed order, so the view can loop rather than repeat itself. */
    public List<SideTotals> sides() {
        return List.of(sideA, sideB);
    }
}
