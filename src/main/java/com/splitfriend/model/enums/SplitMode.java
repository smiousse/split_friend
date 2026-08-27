package com.splitfriend.model.enums;

/**
 * How the user chose to share an expense in the add-expense form.
 * Resolved server-side into a {@link SplitType} plus a participant list;
 * not persisted.
 */
public enum SplitMode {
    /** Share the expense between the selected participants. */
    SPLIT,
    /** The current user owes the whole amount. */
    I_OWE_ALL,
    /** Everyone but the payer owes the whole amount. */
    THEY_OWE_ALL
}
