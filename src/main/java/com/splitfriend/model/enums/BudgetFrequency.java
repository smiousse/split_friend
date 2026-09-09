package com.splitfriend.model.enums;

import java.math.BigDecimal;

/**
 * How often a recurring amount falls due.
 *
 * Each constant carries only one datum - how many times it occurs in a year -
 * and every conversion is derived from it rather than from a hand-written
 * factor table:
 *
 * <pre>
 *   amountInTarget = amount * from.periodsPerYear / to.periodsPerYear
 * </pre>
 *
 * The arithmetic itself lives in
 * {@link com.splitfriend.service.BudgetCalculationService}, because dividing by
 * 26 or 12 is non-terminating and so needs a rounding policy - a money-domain
 * decision that this enum should not own.
 *
 * The same type serves both an item's frequency and a budget's settlement
 * period, so switching a budget from bi-weekly to monthly is a pure re-read
 * with nothing stored to migrate.
 */
public enum BudgetFrequency {

    WEEKLY(52),
    BIWEEKLY(26),
    SEMI_MONTHLY(24),
    MONTHLY(12),
    QUARTERLY(4),
    YEARLY(1);

    private final BigDecimal periodsPerYear;

    BudgetFrequency(int periodsPerYear) {
        this.periodsPerYear = BigDecimal.valueOf(periodsPerYear);
    }

    public BigDecimal getPeriodsPerYear() {
        return periodsPerYear;
    }

    /** Message key for the localized label, e.g. {@code budget.frequency.MONTHLY}. */
    public String getMessageKey() {
        return "budget.frequency." + name();
    }
}
