package com.splitfriend.service;

import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.model.*;
import com.splitfriend.repository.ExpenseRepository;
import com.splitfriend.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final GroupService groupService;

    public BalanceService(ExpenseRepository expenseRepository,
                          SettlementRepository settlementRepository,
                          GroupService groupService) {
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.groupService = groupService;
    }

    public Map<Long, BigDecimal> calculateGroupBalances(Long groupId) {
        Map<Long, BigDecimal> balances = new HashMap<>();

        // Get all expenses with splits for this group
        List<Expense> expenses = expenseRepository.findByGroupIdWithSplits(groupId);

        for (Expense expense : expenses) {
            Long payerId = expense.getPaidBy().getId();
            BigDecimal totalAmount = expense.getAmount();

            // Payer gets credit for paying
            balances.merge(payerId, totalAmount, BigDecimal::add);

            // Each participant owes their share
            for (ExpenseSplit split : expense.getSplits()) {
                Long userId = split.getUser().getId();
                balances.merge(userId, split.getAmount().negate(), BigDecimal::add);
            }
        }

        // Account for settlements
        List<Settlement> settlements = settlementRepository.findByGroupId(groupId);
        for (Settlement settlement : settlements) {
            Long fromUserId = settlement.getFromUser().getId();
            Long toUserId = settlement.getToUser().getId();
            BigDecimal amount = settlement.getAmount();

            // FromUser paid money, so their balance increases (they owe less)
            balances.merge(fromUserId, amount, BigDecimal::add);
            // ToUser received money, so their balance decreases (they're owed less)
            balances.merge(toUserId, amount.negate(), BigDecimal::add);
        }

        return balances;
    }

    public List<BalanceDTO> getDetailedBalances(Long groupId) {
        Map<Long, BigDecimal> balances = calculateGroupBalances(groupId);
        List<User> members = groupService.getGroupMemberUsers(groupId);
        List<BalanceDTO> result = new ArrayList<>();

        for (User member : members) {
            BigDecimal balance = balances.getOrDefault(member.getId(), BigDecimal.ZERO);
            result.add(new BalanceDTO(member.getId(), member.getName(), member.getEmail(), balance));
        }

        result.sort((a, b) -> b.getBalance().compareTo(a.getBalance()));
        return result;
    }

    public BigDecimal getUserBalanceInGroup(Long groupId, Long userId) {
        Map<Long, BigDecimal> balances = calculateGroupBalances(groupId);
        return balances.getOrDefault(userId, BigDecimal.ZERO);
    }

    public Map<Long, BigDecimal> getUserOverallBalances(Long userId) {
        Map<Long, BigDecimal> overallBalances = new HashMap<>();
        List<Group> userGroups = groupService.findByUser(
                groupService.getGroupMemberUsers(0L).stream()
                        .filter(u -> u.getId().equals(userId))
                        .findFirst()
                        .orElse(null));

        // This would need to be implemented properly with user lookup
        return overallBalances;
    }

    public List<BalanceDTO.DebtDTO> calculateDebts(Long groupId) {
        Map<Long, BigDecimal> balances = calculateGroupBalances(groupId);
        List<User> members = groupService.getGroupMemberUsers(groupId);
        Map<Long, String> userNames = new HashMap<>();
        for (User member : members) {
            userNames.put(member.getId(), member.getName());
        }

        List<BalanceDTO.DebtDTO> debts = new ArrayList<>();

        // Separate into creditors (positive balance) and debtors (negative balance)
        List<Map.Entry<Long, BigDecimal>> creditors = new ArrayList<>();
        List<Map.Entry<Long, BigDecimal>> debtors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(entry);
            } else if (entry.getValue().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(entry);
            }
        }

        // Sort by absolute value (largest first)
        creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        debtors.sort((a, b) -> a.getValue().compareTo(b.getValue())); // Most negative first

        // Match debtors to creditors
        int i = 0, j = 0;
        while (i < creditors.size() && j < debtors.size()) {
            Map.Entry<Long, BigDecimal> creditor = creditors.get(i);
            Map.Entry<Long, BigDecimal> debtor = debtors.get(j);

            BigDecimal credit = creditor.getValue();
            BigDecimal debt = debtor.getValue().abs();

            BigDecimal payment = credit.min(debt);

            if (payment.compareTo(new BigDecimal("0.01")) >= 0) {
                debts.add(new BalanceDTO.DebtDTO(
                        debtor.getKey(),
                        userNames.get(debtor.getKey()),
                        creditor.getKey(),
                        userNames.get(creditor.getKey()),
                        payment
                ));
            }

            // Update balances
            creditor.setValue(credit.subtract(payment));
            debtor.setValue(debtor.getValue().add(payment));

            if (creditor.getValue().compareTo(new BigDecimal("0.01")) < 0) i++;
            if (debtor.getValue().abs().compareTo(new BigDecimal("0.01")) < 0) j++;
        }

        return debts;
    }
}
