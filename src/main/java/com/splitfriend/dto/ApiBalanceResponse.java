package com.splitfriend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload of {@code GET /api/v1/balances}: two flat lists - who the caller owes,
 * and who owes the caller. Amounts are netted across all of the caller's groups,
 * so each person appears in at most one list.
 *
 * Deliberately a dedicated record tree rather than {@link BalanceDTO}, which
 * would expose member e-mail addresses and Lombok-derived helper getters.
 *
 * @param totalIOwe     sum of {@code iOwe} amounts
 * @param totalOwedToMe sum of {@code owedToMe} amounts
 * @param net           {@code totalOwedToMe - totalIOwe}; negative means the caller is behind
 */
public record ApiBalanceResponse(
        String user,
        BigDecimal totalIOwe,
        BigDecimal totalOwedToMe,
        BigDecimal net,
        List<Person> iOwe,
        List<Person> owedToMe
) {

    public record Person(String name, BigDecimal amount) {}
}
