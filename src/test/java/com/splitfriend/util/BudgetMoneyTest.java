package com.splitfriend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetMoneyTest {

    @Test
    @DisplayName("dividing by 26 does not throw - the non-terminating expansion trap")
    void handlesNonTerminatingDivision() {
        BigDecimal yearlyToBiweekly = BigDecimal.ONE.divide(new BigDecimal("26"), BudgetMoney.CALC);
        assertEquals(0, yearlyToBiweekly.compareTo(new BigDecimal("0.03846153846153846")),
                "1/26 should be carried at working precision, not rounded to cents");
    }

    @Test
    @DisplayName("a split pair always sums back to the rounded total")
    void splitPairReconciles() {
        BigDecimal[] parts = BudgetMoney.splitPair(new BigDecimal("10.01"), new BigDecimal("60"));
        assertEquals(new BigDecimal("6.01"), parts[0]);
        assertEquals(new BigDecimal("4.00"), parts[1]);
        assertEquals(BudgetMoney.display(new BigDecimal("10.01")), parts[0].add(parts[1]));
    }

    @Test
    @DisplayName("reconciles for a total that rounding would otherwise strand a cent on")
    void splitPairReconcilesOnAwkwardThirds() {
        BigDecimal[] parts = BudgetMoney.splitPair(new BigDecimal("0.05"), new BigDecimal("50"));
        assertEquals(new BigDecimal("0.05"), parts[0].add(parts[1]),
                "half of 0.05 rounds to 0.03; the remainder must absorb the difference");
    }

    @Test
    void percentOfAppliesTheDivisionOnce() {
        assertEquals(0, BudgetMoney.percentOf(new BigDecimal("869.68"), new BigDecimal("50"))
                .compareTo(new BigDecimal("434.84")));
    }

    @Test
    void negligibleAmountsAreTreatedAsSettled() {
        assertTrue(BudgetMoney.isNegligible(new BigDecimal("0.004")));
        assertTrue(BudgetMoney.isNegligible(BigDecimal.ZERO));
        assertFalse(BudgetMoney.isNegligible(new BigDecimal("0.01")));
    }
}
