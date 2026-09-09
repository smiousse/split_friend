package com.splitfriend.service;

import com.splitfriend.dto.BudgetItemView;
import com.splitfriend.dto.BudgetSummary;
import com.splitfriend.model.Budget;
import com.splitfriend.model.BudgetItem;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetSide;
import com.splitfriend.util.BudgetMoney;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a budget's line items into the one number that settles the period.
 *
 * A pure transformation over a fully-loaded {@link Budget}: no repositories,
 * no transaction, no Spring context needed to test it - mirroring how
 * {@link PersonalBalanceService} layers over {@link BalanceService}.
 *
 * <h2>The formula</h2>
 * Every active shared item is converted to the budget's settlement period and
 * pooled by payer. Each participant is responsible for their percentage of the
 * pool, so what one owes the other is the difference between what they paid in
 * and what they were responsible for:
 *
 * <pre>
 *   net = (paidA * shareB - paidB * shareA) / 100
 * </pre>
 *
 * A positive net means A paid more than their share, so B transfers it.
 *
 * <h2>Rounding</h2>
 * Conversion and accumulation run at {@link BudgetMoney#CALC} precision, and
 * each <em>side's</em> totals are rounded exactly once. The division by 26
 * (bi-weekly) and by 12 (monthly) are both non-terminating, so rounding per
 * item would let a long list drift by dimes.
 *
 * Every aggregate is then derived from those already-rounded figures rather
 * than from the raw sums, so the breakdown a reader adds up by hand matches
 * the totals printed beside it. Rounding two side totals and adding them costs
 * at most a cent against the true value and does not accumulate - which is a
 * better trade than a column that visibly fails to sum.
 */
@Service
public class BudgetCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * Restates an amount from one cadence to another, via an annual basis.
     *
     * <pre>amountInTarget = amount * from.periodsPerYear / to.periodsPerYear</pre>
     *
     * Deriving it this way rather than from a table of factors means a budget
     * can be re-quoted in any period without a second set of constants.
     *
     * @return the converted amount at working precision, deliberately unrounded
     */
    public BigDecimal convert(BigDecimal amount, BudgetFrequency from, BudgetFrequency to) {
        if (amount == null || from == null || to == null) {
            return BigDecimal.ZERO;
        }
        if (from == to) {
            return amount;
        }
        return amount.multiply(from.getPeriodsPerYear(), BudgetMoney.CALC)
                .divide(to.getPeriodsPerYear(), BudgetMoney.CALC);
    }

    /** The full picture for one budget, with every amount already rounded. */
    public BudgetSummary summarize(Budget budget) {
        BudgetFrequency period = budget.getSettlementPeriod();
        List<BudgetItem> items = budget.getItems() != null ? budget.getItems() : List.of();

        Totals a = new Totals();
        Totals b = new Totals();
        List<BudgetItemView> views = new ArrayList<>();

        for (BudgetItem item : items) {
            BigDecimal normalized = convert(item.getAmount(), item.getFrequency(), period);
            views.add(toView(budget, item, normalized));

            if (!item.isActive()) {
                continue;
            }
            Totals side = item.getPaidBySide() == BudgetSide.A ? a : b;
            side.add(normalized, item.isShared());
        }

        return build(budget, period, a, b, views);
    }

    private BudgetSummary build(Budget budget, BudgetFrequency period,
                                Totals a, Totals b, List<BudgetItemView> views) {
        BigDecimal shareA = BudgetMoney.zeroIfNull(budget.getShareAPercent());
        BigDecimal shareB = budget.getShareBPercent();

        // Round each side once, then build every aggregate from these figures
        // so the breakdown reconciles on screen.
        BigDecimal paidA = BudgetMoney.display(a.shared);
        BigDecimal paidB = BudgetMoney.display(b.shared);
        BigDecimal owedToA = BudgetMoney.display(BudgetMoney.percentOf(a.shared, shareB));
        BigDecimal owedToB = BudgetMoney.display(BudgetMoney.percentOf(b.shared, shareA));

        BigDecimal net = owedToA.subtract(owedToB);
        BudgetSide payingSide = BudgetMoney.isNegligible(net)
                ? null
                : (net.signum() > 0 ? BudgetSide.B : BudgetSide.A);
        BigDecimal netAmount = BudgetMoney.display(net.abs());

        return new BudgetSummary(
                period,
                totalsFor(budget, BudgetSide.A, shareA, a, paidA, owedToA, owedToB),
                totalsFor(budget, BudgetSide.B, shareB, b, paidB, owedToB, owedToA),
                paidA.add(paidB),
                netAmount,
                payingSide,
                payingSide == null ? null : nameOf(budget, payingSide),
                payingSide == null ? null : nameOf(budget, payingSide.opposite()),
                BudgetMoney.display(convert(netAmount, period, BudgetFrequency.MONTHLY)),
                views);
    }

    private BudgetSummary.SideTotals totalsFor(Budget budget, BudgetSide side, BigDecimal sharePercent,
                                               Totals totals, BigDecimal paidShared,
                                               BigDecimal owedByOther, BigDecimal owesOther) {
        User user = budget.userFor(side);
        BigDecimal absorbed = BudgetMoney.display(totals.absorbed);
        return new BudgetSummary.SideTotals(
                side,
                user != null ? user.getId() : null,
                nameOf(budget, side),
                sharePercent,
                paidShared,
                absorbed,
                paidShared.add(absorbed),
                owedByOther,
                owesOther);
    }

    private BudgetItemView toView(Budget budget, BudgetItem item, BigDecimal normalized) {
        return new BudgetItemView(
                item.getId(),
                item.getLabel(),
                item.getAmount(),
                item.getFrequency(),
                item.getPaidBySide(),
                nameOf(budget, item.getPaidBySide()),
                item.getItemType(),
                BudgetMoney.display(normalized),
                item.isActive(),
                item.getNotes());
    }

    private String nameOf(Budget budget, BudgetSide side) {
        User user = budget.userFor(side);
        return user != null ? user.getName() : null;
    }

    /** One side's running totals, carried unrounded. */
    private static final class Totals {
        private BigDecimal shared = BigDecimal.ZERO;
        private BigDecimal absorbed = BigDecimal.ZERO;

        void add(BigDecimal normalized, boolean isShared) {
            if (isShared) {
                shared = shared.add(normalized, BudgetMoney.CALC);
            } else {
                absorbed = absorbed.add(normalized, BudgetMoney.CALC);
            }
        }
    }
}
