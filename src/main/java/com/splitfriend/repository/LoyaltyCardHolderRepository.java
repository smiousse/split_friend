package com.splitfriend.repository;

import com.splitfriend.model.LoyaltyCardHolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Every read of a card by a non-owner path goes through a holder row, so
 * "can this user see this card" is answered by the join itself rather than by
 * a check a handler might forget.
 */
@Repository
public interface LoyaltyCardHolderRepository extends JpaRepository<LoyaltyCardHolder, Long> {

    @Query("SELECT h FROM LoyaltyCardHolder h JOIN FETCH h.card c JOIN FETCH c.owner WHERE h.user.id = :userId")
    List<LoyaltyCardHolder> findByUser(@Param("userId") Long userId);

    @Query("SELECT h FROM LoyaltyCardHolder h JOIN FETCH h.card c JOIN FETCH c.owner "
            + "WHERE c.id = :cardId AND h.user.id = :userId")
    Optional<LoyaltyCardHolder> findByCardAndUser(@Param("cardId") Long cardId, @Param("userId") Long userId);

    @Query("SELECT h FROM LoyaltyCardHolder h JOIN FETCH h.user WHERE h.card.id = :cardId ORDER BY h.addedAt ASC")
    List<LoyaltyCardHolder> findByCard(@Param("cardId") Long cardId);

    @Modifying
    @Query("DELETE FROM LoyaltyCardHolder h WHERE h.user.id = :userId")
    void deleteByUser(@Param("userId") Long userId);
}
