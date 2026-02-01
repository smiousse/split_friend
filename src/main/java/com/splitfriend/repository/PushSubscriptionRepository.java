package com.splitfriend.repository;

import com.splitfriend.model.PushSubscription;
import com.splitfriend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUser(User user);

    List<PushSubscription> findByUserId(Long userId);

    @Query("SELECT ps FROM PushSubscription ps WHERE ps.user.id IN :userIds")
    List<PushSubscription> findByUserIdIn(@Param("userIds") List<Long> userIds);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByUserAndEndpoint(User user, String endpoint);

    void deleteByEndpoint(String endpoint);

    void deleteByUser(User user);

    boolean existsByUserAndEndpoint(User user, String endpoint);
}
