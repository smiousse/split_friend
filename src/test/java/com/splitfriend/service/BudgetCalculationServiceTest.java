package com.splitfriend.service;

import com.splitfriend.dto.BudgetSummary;
import com.splitfriend.model.Budget;
import com.splitfriend.model.BudgetItem;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetItemType;
import com.splitfriend.model.enums.BudgetSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetCalculationServiceTest {

    private final BudgetCalculationService service = new BudgetCalculationService();

    // ---------- normalization ----------

    @Test
    @DisplayName("reproduces the reference conversions to a bi-weekly basis")
    void normalizesToBiweekly() {
        // monthly * 12/26
        assertEquals(0, service.convert(new BigDecimal("127.53"), BudgetFrequency.MONTHLY, BudgetFrequency.BIWEEKLY)
                .setScale(2, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("58.86")));
        // yearly / 26
        assertEquals(0, service.convert(new BigDecimal("362.93"), BudgetFrequency.YEARLY, BudgetFrequency.BIWEEKLY)
                .setScale(2, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("13.96")));
        // weekly * 2
        assertEquals(0, service.convert(new BigDecimal("398.43"), BudgetFrequency.WEEKLY, BudgetFrequency.BIWEEKLY)
                .compareTo(new BigDecimal("796.86")));
    }

    @Test
    void convertingToTheSamePeriodIsIdentity() {
        assertEquals(0, service.convert(new BigDecimal("204.00"), BudgetFrequency.BIWEEKLY, BudgetFrequency.BIWEEKLY)
                .compareTo(new BigDecimal("204.00")));
    }

    // ---------- the golden replay ----------

    /**
     * Replays the reference spreadsheet ("Depenses communes.xlsx", Sheet1).
     *
     * Audrey pays home insurance, the mortgage+groceries line and school taxes;
     * Stephane pays internet, streaming, groceries and Audrey's car insurance.
     * Hydro and Prime are absorbed by Stephane - the sheet zeroes their
     * normalized column and tallies them separately.
     *
     * The sheet arrives at 250.42 per two weeks, but it credits the car
     * insurance as a flat 40.00 rather than converting it: 82.00/month is
     * 37.85 bi-weekly, so the shared half is 18.92, not 40.00. Computing it
     * properly gives 271.49. The 21.07 gap is the rounding shortcut, not a
     * disagreement about the method.
     */
    @Test
    @DisplayName("golden: reproduces the reference spreadsheet's net transfer")
    void reproducesReferenceSpreadsheet() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.BIWEEKLY);

        // Paid by Audrey (side A)
        addItem(budget, "Assurances maison", "127.53", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        addItem(budget, "Maison", "398.43", BudgetFrequency.WEEKLY, BudgetSide.A, BudgetItemType.SHARED);
        addItem(budget, "Taxes scolaires", "362.93", BudgetFrequency.YEARLY, BudgetSide.A, BudgetItemType.SHARED);

        // Paid by Stephane (side B)
        addItem(budget, "Internet", "117.22", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);
        addItem(budget, "Netflix", "36.77", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);
        addItem(budget, "Spotify", "24.13", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);
        addItem(budget, "Disney plus", "68.50", BudgetFrequency.YEARLY, BudgetSide.B, BudgetItemType.SHARED);
        addItem(budget, "Epicier", "204.00", BudgetFrequency.BIWEEKLY, BudgetSide.B, BudgetItemType.SHARED);
        addItem(budget, "Assurance auto", "82.00", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);

        // Absorbed by Stephane - excluded from the split
        addItem(budget, "Hydro", "430.00", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.ABSORBED);
        addItem(budget, "Prime", "120.00", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.ABSORBED);

        BudgetSummary summary = service.summarize(budget);

        assertEquals(new BigDecimal("869.68"), summary.sideA().paidShared());
        assertEquals(new BigDecimal("326.69"), summary.sideB().paidShared());
        assertEquals(new BigDecimal("271.49"), summary.netAmount());
        assertEquals(BudgetSide.B, summary.payingSide());
        assertEquals("Stephane", summary.payingUserName());
        assertEquals("Audrey", summary.receivingUserName());

        // Absorbed items stay out of the split but are still reported.
        assertEquals(new BigDecimal("253.85"), summary.sideB().absorbed());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.sideA().absorbed());
    }

    @Test
    @DisplayName("the monthly equivalent restates the same net, it is not recomputed")
    void quotesAMonthlyEquivalent() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.BIWEEKLY);
        addItem(budget, "Rent", "1300.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);

        BudgetSummary summary = service.summarize(budget);

        assertEquals(new BigDecimal("300.00"), summary.netAmount());
        assertEquals(new BigDecimal("650.00"), summary.monthlyEquivalent());
    }

    // ---------- split behaviour ----------

    @Test
    @DisplayName("an uneven split charges each side the other's percentage")
    void appliesUnevenPercentages() {
        Budget budget = budget(new BigDecimal("70.00"), BudgetFrequency.MONTHLY);
        addItem(budget, "Rent", "1000.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);

        BudgetSummary summary = service.summarize(budget);

        // A pays 1000 and is responsible for 70% of it, so B owes the other 30%.
        assertEquals(new BigDecimal("300.00"), summary.netAmount());
        assertEquals(BudgetSide.B, summary.payingSide());
    }

    @Test
    void reportsSettledWhenTheSidesCancelOut() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.MONTHLY);
        addItem(budget, "Rent", "500.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        addItem(budget, "Hydro", "500.00", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);

        BudgetSummary summary = service.summarize(budget);

        assertTrue(summary.isSettled());
        assertNull(summary.payingSide());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.netAmount());
    }

    @Test
    @DisplayName("absorbed items never move the net, only the outlay total")
    void absorbedItemsDoNotMoveTheNet() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.MONTHLY);
        addItem(budget, "Rent", "500.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        addItem(budget, "Hydro", "500.00", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);
        addItem(budget, "Her car", "900.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.ABSORBED);

        BudgetSummary summary = service.summarize(budget);

        assertTrue(summary.isSettled());
        assertEquals(new BigDecimal("900.00"), summary.sideA().absorbed());
        assertEquals(new BigDecimal("1400.00"), summary.sideA().totalOutlay());
    }

    @Test
    void inactiveItemsAreExcludedEntirely() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.MONTHLY);
        addItem(budget, "Rent", "500.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        BudgetItem cancelled = addItem(budget, "Old gym", "60.00", BudgetFrequency.MONTHLY,
                BudgetSide.B, BudgetItemType.SHARED);
        cancelled.setActive(false);

        BudgetSummary summary = service.summarize(budget);

        assertEquals(BigDecimal.ZERO.setScale(2), summary.sideB().paidShared());
        assertEquals(new BigDecimal("250.00"), summary.netAmount());
    }

    @Test
    @DisplayName("thirty items converted through a non-terminating factor do not drift")
    void doesNotAccumulateRoundingDrift() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.BIWEEKLY);
        for (int i = 0; i < 30; i++) {
            addItem(budget, "Item " + i, "100.00", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        }

        BudgetSummary summary = service.summarize(budget);

        // 30 * 100 * 12/26 = 1384.6153..., halved = 692.31 - not 692.25 or 692.40
        assertEquals(new BigDecimal("1384.62"), summary.sideA().paidShared());
        assertEquals(new BigDecimal("692.31"), summary.netAmount());
    }

    @Test
    @DisplayName("the breakdown reconciles: displayed parts sum to the displayed totals")
    void displayedFiguresReconcile() {
        // Amounts chosen so both sides land just past a rounding boundary:
        // 21.68 and 43.35 monthly convert to 10.006... and 20.007... bi-weekly.
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.BIWEEKLY);
        addItem(budget, "A line", "21.68", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        addItem(budget, "B line", "43.35", BudgetFrequency.MONTHLY, BudgetSide.B, BudgetItemType.SHARED);

        BudgetSummary summary = service.summarize(budget);

        assertEquals(summary.sideA().paidShared().add(summary.sideB().paidShared()),
                summary.pooledTotal(),
                "pooled total must equal the two displayed side totals added up");
        assertEquals(summary.sideA().owedByOther().subtract(summary.sideA().owesOther()).abs(),
                summary.netAmount(),
                "the net must equal the difference of the displayed per-side figures");
    }

    @Test
    @DisplayName("total outlay is the sum of the displayed shared and absorbed figures")
    void outlayReconcilesWithItsParts() {
        Budget budget = budget(new BigDecimal("50.00"), BudgetFrequency.BIWEEKLY);
        addItem(budget, "Shared", "21.68", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.SHARED);
        addItem(budget, "Absorbed", "43.35", BudgetFrequency.MONTHLY, BudgetSide.A, BudgetItemType.ABSORBED);

        BudgetSummary.SideTotals sideA = service.summarize(budget).sideA();

        assertEquals(sideA.paidShared().add(sideA.absorbed()), sideA.totalOutlay());
    }

    @Test
    void handlesABudgetWithNoItems() {
        BudgetSummary summary = service.summarize(budget(new BigDecimal("50.00"), BudgetFrequency.BIWEEKLY));

        assertTrue(summary.isSettled());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.pooledTotal());
        assertTrue(summary.items().isEmpty());
    }

    // ---------- fixtures ----------

    private Budget budget(BigDecimal shareAPercent, BudgetFrequency period) {
        User audrey = User.builder().id(1L).name("Audrey").email("audrey@example.com").build();
        User stephane = User.builder().id(2L).name("Stephane").email("stephane@example.com").build();
        return Budget.builder()
                .id(10L)
                .name("Depenses communes")
                .currency("CAD")
                .userA(audrey)
                .userB(stephane)
                .shareAPercent(shareAPercent)
                .settlementPeriod(period)
                .items(new ArrayList<>())
                .build();
    }

    private BudgetItem addItem(Budget budget, String label, String amount, BudgetFrequency frequency,
                               BudgetSide side, BudgetItemType type) {
        List<BudgetItem> items = budget.getItems();
        BudgetItem item = BudgetItem.builder()
                .id((long) (items.size() + 1))
                .budget(budget)
                .label(label)
                .amount(new BigDecimal(amount))
                .frequency(frequency)
                .paidBySide(side)
                .itemType(type)
                .sortOrder(items.size())
                .active(true)
                .build();
        items.add(item);
        return item;
    }
}
