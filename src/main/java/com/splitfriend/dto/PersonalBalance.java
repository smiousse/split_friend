package com.splitfriend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One user's position against every other person: who they owe, and who owes
 * them. Produced by
 * {@link com.splitfriend.service.PersonalBalanceService}, and rendered both by
 * the balance panels and by the read-only balance API.
 *
 * Carries only user ids and display names - never a {@code User} entity - so no
 * e-mail address can leak into a view or an API response.
 *
 * @param totalIOwe     sum of {@code iOwe} amounts, as a positive magnitude
 * @param totalOwedToMe sum of {@code owedToMe} amounts, as a positive magnitude
 * @param net           {@code totalOwedToMe - totalIOwe}; negative means the user is behind
 */
public record PersonalBalance(
        BigDecimal totalIOwe,
        BigDecimal totalOwedToMe,
        BigDecimal net,
        List<PersonShare> iOwe,
        List<PersonShare> owedToMe
) {

    /**
     * @param amount always a positive magnitude; the direction is carried by
     *               which list this share sits in
     */
    public record PersonShare(Long userId, String name, BigDecimal amount) {}

    /** True when nobody owes anybody - drives the panels' empty state. */
    public boolean isSettled() {
        return iOwe.isEmpty() && owedToMe.isEmpty();
    }
}
