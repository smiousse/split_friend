package com.splitfriend.dto;

import com.splitfriend.model.enums.BudgetFrequency;
import com.splitfriend.model.enums.BudgetItemType;
import com.splitfriend.model.enums.BudgetSide;

import java.math.BigDecimal;

/**
 * A budget line item as rendered: its own gross amount alongside the same
 * amount restated in the budget's settlement period, so the table can show
 * "117.22 / month" and "54.10 / 2 weeks" side by side.
 *
 * @param normalizedAmount {@code amount} converted to the budget's period
 */
public record BudgetItemView(
        Long id,
        String label,
        BigDecimal amount,
        BudgetFrequency frequency,
        BudgetSide paidBySide,
        String paidByName,
        BudgetItemType itemType,
        BigDecimal normalizedAmount,
        boolean active,
        String notes
) {

    public boolean isAbsorbed() {
        return itemType == BudgetItemType.ABSORBED;
    }
}
