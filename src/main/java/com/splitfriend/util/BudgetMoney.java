package com.splitfriend.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Money arithmetic for the budget domain.
 *
 * Two rules matter here, and both exist because the figures are checked
 * against a spreadsheet:
 *
 * <ol>
 *   <li><b>Every division goes through {@link #CALC}.</b> Converting to a
 *       bi-weekly basis divides by 26, and monthly by 12; both are
 *       non-terminating in decimal, so a bare
 *       {@code BigDecimal.divide(BigDecimal)} throws
 *       {@code ArithmeticException: Non-terminating decimal expansion}.</li>
 *   <li><b>Round once, at the end.</b> Rounding each line item as it is
 *       converted lets thirty items drift by dimes. Accumulate at full
 *       working precision and call {@link #display} only when building a DTO.</li>
 * </ol>
 */
public final class BudgetMoney {

    /** Working precision for intermediate conversions and sums. */
    public static final MathContext CALC = new MathContext(16, RoundingMode.HALF_UP);

    /** Scale money is presented at, matching the rest of the app. */
    public static final int SCALE_DISPLAY = 2;

    /** Amounts below this are rounding noise, not a real transfer. */
    public static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private BudgetMoney() {
        // Utility class
    }

    /** Rounds to presentation scale. The only place rounding is allowed. */
    public static BigDecimal display(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount)
                .setScale(SCALE_DISPLAY, RoundingMode.HALF_UP);
    }

    public static BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    /** {@code amount * percent / 100}, at working precision. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return zeroIfNull(amount).multiply(zeroIfNull(percent), CALC).divide(ONE_HUNDRED, CALC);
    }

    /**
     * Splits a total into two parts by percentage without losing a cent.
     *
     * The first part is rounded and the second is taken as the remainder, so
     * the pair always sums back to the rounded total. Rounding both
     * independently does not: 60/40 of 10.01 rounds to 6.01 and 4.00, which
     * add up to 10.01 only by luck.
     *
     * @return {@code [firstPart, remainder]}, both at display scale
     */
    public static BigDecimal[] splitPair(BigDecimal total, BigDecimal firstPercent) {
        BigDecimal roundedTotal = display(total);
        BigDecimal first = display(percentOf(total, firstPercent));
        return new BigDecimal[]{first, roundedTotal.subtract(first)};
    }

    /** True when an amount is too small to be worth transferring. */
    public static boolean isNegligible(BigDecimal amount) {
        return zeroIfNull(amount).abs().compareTo(MIN_AMOUNT) < 0;
    }
}
