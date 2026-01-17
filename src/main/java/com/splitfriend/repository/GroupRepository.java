package com.splitfriend.repository;

import com.splitfriend.model.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("SELECT g FROM Group g JOIN g.members m WHERE m.user.id = :userId ORDER BY g.createdAt DESC")
    List<Group> findByUserId(@Param("userId") Long userId);

    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.members WHERE g.id = :id")
    Optional<Group> findByIdWithMembers(@Param("id") Long id);

    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.members m LEFT JOIN FETCH m.user WHERE g.id = :id")
    Optional<Group> findByIdWithMembersAndUsers(@Param("id") Long id);

    @Query("SELECT COUNT(g) FROM Group g")
    long countGroups();

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM GroupMember m WHERE m.group.id = :groupId AND m.user.id = :userId")
    boolean isUserMemberOfGroup(@Param("groupId") Long groupId, @Param("userId") Long userId);

    List<Group> findByCreatedById(Long userId);
}
