package com.splitfriend.repository;

import com.splitfriend.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query("SELECT s FROM Settlement s WHERE s.group.id = :groupId ORDER BY s.settledAt DESC")
    List<Settlement> findByGroupId(@Param("groupId") Long groupId);

    @Query("SELECT s FROM Settlement s WHERE s.fromUser.id = :userId OR s.toUser.id = :userId ORDER BY s.settledAt DESC")
    List<Settlement> findByUserId(@Param("userId") Long userId);

    @Query("SELECT s FROM Settlement s WHERE s.group.id = :groupId AND (s.fromUser.id = :userId OR s.toUser.id = :userId) ORDER BY s.settledAt DESC")
    List<Settlement> findByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s WHERE s.group.id = :groupId AND s.fromUser.id = :fromUserId AND s.toUser.id = :toUserId")
    BigDecimal getTotalSettledBetweenUsers(
            @Param("groupId") Long groupId,
            @Param("fromUserId") Long fromUserId,
            @Param("toUserId") Long toUserId);

    @Query("SELECT COUNT(s) FROM Settlement s")
    long countSettlements();

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Settlement s")
    BigDecimal getTotalSettledAmount();
}
