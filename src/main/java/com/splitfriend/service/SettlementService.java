package com.splitfriend.service;

import com.splitfriend.model.Group;
import com.splitfriend.model.Settlement;
import com.splitfriend.model.User;
import com.splitfriend.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class SettlementService {

    private final SettlementRepository settlementRepository;

    public SettlementService(SettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    public Settlement createSettlement(Group group, User fromUser, User toUser, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }

        if (fromUser.getId().equals(toUser.getId())) {
            throw new IllegalArgumentException("Cannot settle with yourself");
        }

        Settlement settlement = Settlement.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(amount)
                .build();

        return settlementRepository.save(settlement);
    }

    public Optional<Settlement> findById(Long id) {
        return settlementRepository.findById(id);
    }

    public List<Settlement> findByGroupId(Long groupId) {
        return settlementRepository.findByGroupId(groupId);
    }

    public List<Settlement> findByUserId(Long userId) {
        return settlementRepository.findByUserId(userId);
    }

    public List<Settlement> findByGroupIdAndUserId(Long groupId, Long userId) {
        return settlementRepository.findByGroupIdAndUserId(groupId, userId);
    }

    public void deleteSettlement(Long settlementId) {
        settlementRepository.deleteById(settlementId);
    }

    public BigDecimal getTotalSettledBetweenUsers(Long groupId, Long fromUserId, Long toUserId) {
        return settlementRepository.getTotalSettledBetweenUsers(groupId, fromUserId, toUserId);
    }

    public long countSettlements() {
        return settlementRepository.countSettlements();
    }

    public BigDecimal getTotalSettledAmount() {
        return settlementRepository.getTotalSettledAmount();
    }
}
