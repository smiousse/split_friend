package com.splitfriend.service;

import com.splitfriend.model.*;
import com.splitfriend.model.enums.SplitType;
import com.splitfriend.repository.ExpenseRepository;
import com.splitfriend.repository.ExpenseSplitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final PushNotificationService pushNotificationService;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseSplitRepository expenseSplitRepository,
                          PushNotificationService pushNotificationService) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.pushNotificationService = pushNotificationService;
    }

    public Expense createExpense(Group group, User paidBy, String description,
                                  BigDecimal amount, SplitType splitType,
                                  LocalDate expenseDate, Map<Long, BigDecimal> splitAmounts,
                                  Map<Long, BigDecimal> percentages, Map<Long, Integer> shares,
                                  List<User> participants, MultipartFile bill) {

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .description(description)
                .amount(amount)
                .splitType(splitType)
                .expenseDate(expenseDate != null ? expenseDate : LocalDate.now())
                .build();

        // Handle bill upload
        if (bill != null && !bill.isEmpty()) {
            String billPath = saveBill(bill, group.getId());
            expense.setBillPath(billPath);
        }

        expense = expenseRepository.save(expense);

        // Create splits based on split type
        List<ExpenseSplit> splits = createSplits(expense, splitType, amount,
                splitAmounts, percentages, shares, participants);
        expense.setSplits(splits);

        // Send push notifications to participants (excluding payer)
        pushNotificationService.notifyExpenseParticipants(expense, paidBy, participants);

        return expense;
    }

    private List<ExpenseSplit> createSplits(Expense expense, SplitType splitType,
                                            BigDecimal totalAmount, Map<Long, BigDecimal> splitAmounts,
                                            Map<Long, BigDecimal> percentages, Map<Long, Integer> shares,
                                            List<User> participants) {
        List<ExpenseSplit> splits = new ArrayList<>();

        switch (splitType) {
            case EQUAL:
                splits = createEqualSplits(expense, totalAmount, participants);
                break;
            case EXACT:
                splits = createExactSplits(expense, splitAmounts, participants);
                break;
            case PERCENTAGE:
                splits = createPercentageSplits(expense, totalAmount, percentages, participants);
                break;
            case SHARES:
                splits = createSharesSplits(expense, totalAmount, shares, participants);
                break;
        }

        return expenseSplitRepository.saveAll(splits);
    }

    private List<ExpenseSplit> createEqualSplits(Expense expense, BigDecimal totalAmount, List<User> participants) {
        List<ExpenseSplit> splits = new ArrayList<>();
        int count = participants.size();

        if (count == 0) return splits;

        BigDecimal baseAmount = totalAmount.divide(BigDecimal.valueOf(count), 4, RoundingMode.DOWN);
        BigDecimal remainder = totalAmount.subtract(baseAmount.multiply(BigDecimal.valueOf(count)));

        // Randomly distribute remainder
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < count; i++) indices.add(i);
        Collections.shuffle(indices);

        BigDecimal centsRemainder = remainder.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);
        int remainderCents = centsRemainder.intValue();

        for (int i = 0; i < count; i++) {
            User user = participants.get(i);
            BigDecimal splitAmount = baseAmount;

            if (indices.indexOf(i) < remainderCents) {
                splitAmount = splitAmount.add(new BigDecimal("0.01"));
            }

            ExpenseSplit split = ExpenseSplit.builder()
                    .expense(expense)
                    .user(user)
                    .amount(splitAmount)
                    .build();
            splits.add(split);
        }

        return splits;
    }

    private List<ExpenseSplit> createExactSplits(Expense expense, Map<Long, BigDecimal> splitAmounts,
                                                  List<User> participants) {
        List<ExpenseSplit> splits = new ArrayList<>();

        for (User user : participants) {
            BigDecimal amount = splitAmounts.getOrDefault(user.getId(), BigDecimal.ZERO);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                ExpenseSplit split = ExpenseSplit.builder()
                        .expense(expense)
                        .user(user)
                        .amount(amount)
                        .build();
                splits.add(split);
            }
        }

        return splits;
    }

    private List<ExpenseSplit> createPercentageSplits(Expense expense, BigDecimal totalAmount,
                                                       Map<Long, BigDecimal> percentages, List<User> participants) {
        List<ExpenseSplit> splits = new ArrayList<>();
        BigDecimal totalAssigned = BigDecimal.ZERO;

        for (int i = 0; i < participants.size(); i++) {
            User user = participants.get(i);
            BigDecimal percentage = percentages.getOrDefault(user.getId(), BigDecimal.ZERO);
            BigDecimal amount;

            if (i == participants.size() - 1) {
                // Last person gets remainder to avoid rounding issues
                amount = totalAmount.subtract(totalAssigned);
            } else {
                amount = totalAmount.multiply(percentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                totalAssigned = totalAssigned.add(amount);
            }

            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                ExpenseSplit split = ExpenseSplit.builder()
                        .expense(expense)
                        .user(user)
                        .amount(amount)
                        .percentage(percentage)
                        .build();
                splits.add(split);
            }
        }

        return splits;
    }

    private List<ExpenseSplit> createSharesSplits(Expense expense, BigDecimal totalAmount,
                                                   Map<Long, Integer> shares, List<User> participants) {
        List<ExpenseSplit> splits = new ArrayList<>();

        int totalShares = shares.values().stream().mapToInt(Integer::intValue).sum();
        if (totalShares == 0) return splits;

        BigDecimal shareValue = totalAmount.divide(BigDecimal.valueOf(totalShares), 4, RoundingMode.DOWN);
        BigDecimal totalAssigned = BigDecimal.ZERO;

        List<User> sortedParticipants = new ArrayList<>(participants);

        for (int i = 0; i < sortedParticipants.size(); i++) {
            User user = sortedParticipants.get(i);
            int userShares = shares.getOrDefault(user.getId(), 0);
            BigDecimal amount;

            if (i == sortedParticipants.size() - 1) {
                amount = totalAmount.subtract(totalAssigned);
            } else {
                amount = shareValue.multiply(BigDecimal.valueOf(userShares));
                totalAssigned = totalAssigned.add(amount);
            }

            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                ExpenseSplit split = ExpenseSplit.builder()
                        .expense(expense)
                        .user(user)
                        .amount(amount)
                        .shares(userShares)
                        .build();
                splits.add(split);
            }
        }

        return splits;
    }

    private String saveBill(MultipartFile file, Long groupId) {
        try {
            Path uploadDir = Paths.get(uploadPath, "bills", groupId.toString());
            Files.createDirectories(uploadDir);

            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = uploadDir.resolve(filename);
            Files.copy(file.getInputStream(), filePath);

            return "bills/" + groupId + "/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save bill", e);
        }
    }

    public Optional<Expense> findById(Long id) {
        return expenseRepository.findById(id);
    }

    public Optional<Expense> findByIdWithSplits(Long id) {
        return expenseRepository.findByIdWithSplits(id);
    }

    public List<Expense> findByGroupId(Long groupId) {
        return expenseRepository.findByGroupId(groupId);
    }

    public List<Expense> findByGroupIdWithSplits(Long groupId) {
        return expenseRepository.findByGroupIdWithSplits(groupId);
    }

    public void deleteExpense(Long expenseId) {
        expenseRepository.deleteById(expenseId);
    }

    public BigDecimal getTotalExpensesByGroup(Long groupId) {
        return expenseRepository.getTotalExpensesByGroupId(groupId);
    }

    public long countExpenses() {
        return expenseRepository.countExpenses();
    }

    public BigDecimal getTotalExpensesAmount() {
        return expenseRepository.getTotalExpensesAmount();
    }

    public List<Expense> findExpensesByDateRange(Long groupId, LocalDate startDate, LocalDate endDate) {
        return expenseRepository.findByGroupIdAndDateRange(groupId, startDate, endDate);
    }
}
