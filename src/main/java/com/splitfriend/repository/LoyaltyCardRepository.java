package com.splitfriend.repository;

import com.splitfriend.model.LoyaltyCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyCardRepository extends JpaRepository<LoyaltyCard, Long> {

    /**
     * Loads a card only if the given user owns it. The only lookup a write
     * path uses, so a holder the card was merely shared with cannot edit it.
     */
    @Query("SELECT c FROM LoyaltyCard c JOIN FETCH c.owner WHERE c.id = :id AND c.owner.id = :userId")
    Optional<LoyaltyCard> findByIdForOwner(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT c FROM LoyaltyCard c WHERE c.owner.id = :userId")
    List<LoyaltyCard> findByOwner(@Param("userId") Long userId);
}
