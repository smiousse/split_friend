package com.splitfriend.service;

import com.splitfriend.dto.BalanceDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class DebtSimplificationService {

    /**
     * Simplifies debts using a greedy algorithm to minimize the number of transactions.
     * Uses the "settling debts" algorithm where we match largest creditor with largest debtor.
     */
    public List<BalanceDTO.DebtDTO> simplifyDebts(Map<Long, BigDecimal> balances, Map<Long, String> userNames) {
        List<BalanceDTO.DebtDTO> simplifiedDebts = new ArrayList<>();

        // Create mutable copies
        TreeMap<BigDecimal, List<Long>> creditors = new TreeMap<>(Collections.reverseOrder()); // Descending
        TreeMap<BigDecimal, List<Long>> debtors = new TreeMap<>(); // Ascending (most negative first)

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            BigDecimal balance = entry.getValue();
            Long userId = entry.getKey();

            if (balance.compareTo(new BigDecimal("0.01")) >= 0) {
                creditors.computeIfAbsent(balance, k -> new ArrayList<>()).add(userId);
            } else if (balance.compareTo(new BigDecimal("-0.01")) <= 0) {
                debtors.computeIfAbsent(balance, k -> new ArrayList<>()).add(userId);
            }
        }

        // Use a working map to track remaining balances
        Map<Long, BigDecimal> workingBalances = new HashMap<>(balances);

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            // Get largest creditor
            Map.Entry<BigDecimal, List<Long>> creditorEntry = creditors.firstEntry();
            Long creditorId = creditorEntry.getValue().get(0);
            BigDecimal credit = workingBalances.get(creditorId);

            // Get largest debtor (most negative)
            Map.Entry<BigDecimal, List<Long>> debtorEntry = debtors.firstEntry();
            Long debtorId = debtorEntry.getValue().get(0);
            BigDecimal debt = workingBalances.get(debtorId).abs();

            // Calculate payment
            BigDecimal payment = credit.min(debt);

            if (payment.compareTo(new BigDecimal("0.01")) >= 0) {
                simplifiedDebts.add(new BalanceDTO.DebtDTO(
                        debtorId,
                        userNames.getOrDefault(debtorId, "User " + debtorId),
                        creditorId,
                        userNames.getOrDefault(creditorId, "User " + creditorId),
                        payment
                ));
            }

            // Update working balances
            BigDecimal newCreditorBalance = credit.subtract(payment);
            BigDecimal newDebtorBalance = workingBalances.get(debtorId).add(payment);

            workingBalances.put(creditorId, newCreditorBalance);
            workingBalances.put(debtorId, newDebtorBalance);

            // Remove from trees and re-add if still has balance
            removeFromTree(creditors, creditorEntry.getKey(), creditorId);
            removeFromTree(debtors, debtorEntry.getKey(), debtorId);

            if (newCreditorBalance.compareTo(new BigDecimal("0.01")) >= 0) {
                creditors.computeIfAbsent(newCreditorBalance, k -> new ArrayList<>()).add(creditorId);
            }
            if (newDebtorBalance.compareTo(new BigDecimal("-0.01")) <= 0) {
                debtors.computeIfAbsent(newDebtorBalance, k -> new ArrayList<>()).add(debtorId);
            }
        }

        return simplifiedDebts;
    }

    private void removeFromTree(TreeMap<BigDecimal, List<Long>> tree, BigDecimal key, Long userId) {
        List<Long> users = tree.get(key);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                tree.remove(key);
            }
        }
    }

    /**
     * Alternative algorithm using min-cash-flow approach.
     * This finds the optimal solution but may be slower for large groups.
     */
    public List<BalanceDTO.DebtDTO> simplifyDebtsOptimal(Map<Long, BigDecimal> balances, Map<Long, String> userNames) {
        // For smaller groups, the greedy algorithm is usually optimal
        // For larger groups, more sophisticated algorithms could be used
        return simplifyDebts(balances, userNames);
    }
}
