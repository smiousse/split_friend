package com.splitfriend.repository;

import com.splitfriend.model.LoyaltyPresetLogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoyaltyPresetLogoRepository extends JpaRepository<LoyaltyPresetLogo, String> {
}
