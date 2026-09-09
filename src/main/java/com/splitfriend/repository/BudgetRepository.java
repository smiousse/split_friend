package com.splitfriend.repository;

import com.splitfriend.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.userA LEFT JOIN FETCH b.userB "
            + "WHERE b.userA.id = :userId OR b.userB.id = :userId ORDER BY b.createdAt DESC")
    List<Budget> findByParticipant(@Param("userId") Long userId);

    /**
     * Loads a budget only if the given user is one of its two participants, so
     * an unscoped lookup is never reachable from a request handler. Callers
     * cannot forget the access check because there is nothing to forget.
     */
    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.userA LEFT JOIN FETCH b.userB "
            + "WHERE b.id = :id AND (b.userA.id = :userId OR b.userB.id = :userId)")
    Optional<Budget> findByIdForParticipant(@Param("id") Long id, @Param("userId") Long userId);

    /** Unscoped load with participants fetched. Admin paths only. */
    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.userA LEFT JOIN FETCH b.userB WHERE b.id = :id")
    Optional<Budget> findByIdWithParticipants(@Param("id") Long id);

    @Query("SELECT COUNT(b) FROM Budget b")
    long countBudgets();
}
