package com.splitfriend.repository;

import com.splitfriend.model.LoyaltyCardLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Keyed by card id. Callers must have passed a card access check first;
 * {@code LoyaltyCardService} is the only caller.
 */
@Repository
public interface LoyaltyCardLogoRepository extends JpaRepository<LoyaltyCardLogo, Long> {
}
