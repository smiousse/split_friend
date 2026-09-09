package com.splitfriend.model.enums;

/**
 * Which of a budget's two participants a line item belongs to.
 *
 * A budget is between exactly two people, so an item's payer is stored as a
 * side rather than as a {@code User} reference. That makes it impossible to
 * attribute an item to somebody who is not a participant - an item that
 * referenced an outsider would fall into neither side's total and silently
 * vanish from the net transfer while still rendering in the item list.
 *
 * Resolve a side back to a person with {@link com.splitfriend.model.Budget#userFor}.
 */
public enum BudgetSide {
    A,
    B;

    /** The other side. Used to charge one participant the other's percentage. */
    public BudgetSide opposite() {
        return this == A ? B : A;
    }
}
