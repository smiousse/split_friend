package com.splitfriend.repository;

import com.splitfriend.model.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, Long> {

    List<ExpenseSplit> findByExpenseId(Long expenseId);

    @Query("SELECT es FROM ExpenseSplit es WHERE es.user.id = :userId")
    List<ExpenseSplit> findByUserId(@Param("userId") Long userId);

    @Query("SELECT es FROM ExpenseSplit es JOIN es.expense e WHERE e.group.id = :groupId AND es.user.id = :userId")
    List<ExpenseSplit> findByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(es.amount), 0) FROM ExpenseSplit es WHERE es.user.id = :userId")
    BigDecimal getTotalOwedByUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(es.amount), 0) FROM ExpenseSplit es JOIN es.expense e WHERE e.group.id = :groupId AND es.user.id = :userId")
    BigDecimal getTotalOwedByUserInGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);
}
