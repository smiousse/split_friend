package com.splitfriend.service;

import com.splitfriend.model.Group;
import com.splitfriend.model.GroupMember;
import com.splitfriend.model.User;
import com.splitfriend.repository.GroupMemberRepository;
import com.splitfriend.repository.GroupRepository;
import com.splitfriend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    public Group createGroup(String name, String description, String currency, User creator) {
        Group group = Group.builder()
                .name(name)
                .description(description)
                .currency(currency != null ? currency : "USD")
                .createdBy(creator)
                .build();

        group = groupRepository.save(group);

        // Add creator as a member
        addMember(group, creator);

        return group;
    }

    public Optional<Group> findById(Long id) {
        return groupRepository.findById(id);
    }

    public Optional<Group> findByIdWithMembers(Long id) {
        return groupRepository.findByIdWithMembersAndUsers(id);
    }

    public List<Group> findByUser(User user) {
        return groupRepository.findByUserId(user.getId());
    }

    public List<Group> findAll() {
        return groupRepository.findAll();
    }

    public Group updateGroup(Group group) {
        return groupRepository.save(group);
    }

    public void deleteGroup(Long groupId) {
        groupRepository.deleteById(groupId);
    }

    public GroupMember addMember(Group group, User user) {
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), user.getId())) {
            throw new IllegalArgumentException("User is already a member of this group");
        }

        GroupMember member = GroupMember.builder()
                .group(group)
                .user(user)
                .build();

        return groupMemberRepository.save(member);
    }

    public void addMemberByEmail(Long groupId, String email) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

        addMember(group, user);
    }

    public void removeMember(Long groupId, Long userId) {
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    public boolean isUserMember(Long groupId, Long userId) {
        return groupRepository.isUserMemberOfGroup(groupId, userId);
    }

    public List<GroupMember> getGroupMembers(Long groupId) {
        return groupMemberRepository.findByGroupIdWithUser(groupId);
    }

    public List<User> getGroupMemberUsers(Long groupId) {
        return userRepository.findByGroupId(groupId);
    }

    public long countGroups() {
        return groupRepository.countGroups();
    }

    public long countMembers(Long groupId) {
        return groupMemberRepository.countByGroupId(groupId);
    }
}
