package com.splitfriend.repository;

import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    Optional<User> findByApiToken(String apiToken);

    List<User> findByRole(Role role);

    List<User> findByEnabled(Boolean enabled);

    @Query("SELECT u FROM User u WHERE u.email LIKE %:search% OR u.name LIKE %:search%")
    List<User> searchUsers(@Param("search") String search);

    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true")
    long countActiveUsers();

    @Query("SELECT u FROM User u JOIN u.groupMemberships gm WHERE gm.group.id = :groupId")
    List<User> findByGroupId(@Param("groupId") Long groupId);

    /**
     * Reads just the budget flag, fresh from the database.
     *
     * The authenticated principal wraps a {@code User} snapshot taken at login
     * and remember-me runs for 30 days, so reading the flag off the principal
     * would keep showing the feature for a month after an admin revoked it.
     */
    @Query("SELECT u.budgetEnabled FROM User u WHERE u.id = :id")
    Optional<Boolean> findBudgetEnabledById(@Param("id") Long id);

    List<User> findByBudgetEnabledTrueOrderByNameAsc();
}
