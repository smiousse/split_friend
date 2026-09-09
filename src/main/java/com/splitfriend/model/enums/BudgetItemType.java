package com.splitfriend.model.enums;

/**
 * How a budget line item participates in the split.
 */
public enum BudgetItemType {

    /** Pooled and divided between the participants by their percentages. */
    SHARED,

    /**
     * Paid entirely by the participant who owns it and excluded from the split.
     * Still reported, so the household sees its true total cost, but it never
     * moves the net transfer.
     */
    ABSORBED
}
