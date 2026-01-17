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

    boolean existsByEmail(String email);

    List<User> findByRole(Role role);

    List<User> findByEnabled(Boolean enabled);

    @Query("SELECT u FROM User u WHERE u.email LIKE %:search% OR u.name LIKE %:search%")
    List<User> searchUsers(@Param("search") String search);

    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true")
    long countActiveUsers();

    @Query("SELECT u FROM User u JOIN u.groupMemberships gm WHERE gm.group.id = :groupId")
    List<User> findByGroupId(@Param("groupId") Long groupId);
}
