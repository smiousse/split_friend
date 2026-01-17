package com.splitfriend.repository;

import com.splitfriend.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId ORDER BY e.expenseDate DESC, e.createdAt DESC")
    List<Expense> findByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.splits s LEFT JOIN FETCH s.user WHERE e.id = :id")
    Optional<Expense> findByIdWithSplits(@Param("id") Long id);

    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.splits WHERE e.group.id = :groupId ORDER BY e.expenseDate DESC")
    List<Expense> findByGroupIdWithSplits(@Param("groupId") Long groupId);

    @Query("SELECT e FROM Expense e WHERE e.paidBy.id = :userId ORDER BY e.expenseDate DESC")
    List<Expense> findByPaidByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.group.id = :groupId")
    BigDecimal getTotalExpensesByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT e FROM Expense e WHERE e.group.id = :groupId AND e.expenseDate BETWEEN :startDate AND :endDate ORDER BY e.expenseDate DESC")
    List<Expense> findByGroupIdAndDateRange(
            @Param("groupId") Long groupId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(e) FROM Expense e")
    long countExpenses();

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e")
    BigDecimal getTotalExpensesAmount();

    @Query("SELECT e FROM Expense e JOIN e.splits s WHERE s.user.id = :userId ORDER BY e.expenseDate DESC")
    List<Expense> findExpensesInvolvingUser(@Param("userId") Long userId);
}
