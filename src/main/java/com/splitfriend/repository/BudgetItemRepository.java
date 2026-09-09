package com.splitfriend.repository;

import com.splitfriend.model.BudgetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {

    List<BudgetItem> findByBudgetIdOrderBySortOrderAscIdAsc(Long budgetId);

    @Query("SELECT COALESCE(MAX(i.sortOrder), -1) FROM BudgetItem i WHERE i.budget.id = :budgetId")
    int findMaxSortOrder(@Param("budgetId") Long budgetId);

    void deleteByBudgetId(Long budgetId);
}
