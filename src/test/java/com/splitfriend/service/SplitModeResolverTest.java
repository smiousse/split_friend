package com.splitfriend.service;

import com.splitfriend.dto.SplitResolution;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.SplitMode;
import com.splitfriend.model.enums.SplitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitModeResolverTest {

    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;
    private static final Long CAROL = 3L;

    private SplitModeResolver resolver;
    private List<User> members;

    @BeforeEach
    void setUp() {
        resolver = new SplitModeResolver();
        members = List.of(user(ALICE), user(BOB), user(CAROL));
    }

    @Test
    void splitModeKeepsSelectedParticipantsAndSplitType() {
        List<User> selected = List.of(user(ALICE), user(BOB));

        SplitResolution resolution = resolver.resolve(
                SplitMode.SPLIT, SplitType.PERCENTAGE, members, selected, ALICE, ALICE);

        assertThat(resolution.splitType()).isEqualTo(SplitType.PERCENTAGE);
        assertThat(ids(resolution)).containsExactly(ALICE, BOB);
    }

    @Test
    void missingModeFallsBackToSplit() {
        SplitResolution resolution = resolver.resolve(
                null, SplitType.SHARES, members, members, ALICE, ALICE);

        assertThat(resolution.splitType()).isEqualTo(SplitType.SHARES);
        assertThat(ids(resolution)).containsExactly(ALICE, BOB, CAROL);
    }

    @Test
    void iOweAllChargesOnlyTheCurrentUser() {
        SplitResolution resolution = resolver.resolve(
                SplitMode.I_OWE_ALL, SplitType.EXACT, members, members, BOB, CAROL);

        assertThat(resolution.splitType()).isEqualTo(SplitType.EQUAL);
        assertThat(ids(resolution)).containsExactly(CAROL);
    }

    @Test
    void iOweAllRejectsNonMember() {
        assertThatThrownBy(() -> resolver.resolve(
                SplitMode.I_OWE_ALL, SplitType.EQUAL, members, members, BOB, 99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theyOweAllExcludesThePayer() {
        SplitResolution resolution = resolver.resolve(
                SplitMode.THEY_OWE_ALL, SplitType.EQUAL, members, members, ALICE, ALICE);

        assertThat(resolution.splitType()).isEqualTo(SplitType.EQUAL);
        assertThat(ids(resolution)).containsExactly(BOB, CAROL);
    }

    @Test
    void theyOweAllInTwoMemberGroupChargesTheOtherMember() {
        List<User> pair = List.of(user(ALICE), user(BOB));

        SplitResolution resolution = resolver.resolve(
                SplitMode.THEY_OWE_ALL, SplitType.EQUAL, pair, pair, ALICE, ALICE);

        assertThat(ids(resolution)).containsExactly(BOB);
    }

    @Test
    void theyOweAllFallsBackToAllMembersWhenOnlyThePayerIsSelected() {
        SplitResolution resolution = resolver.resolve(
                SplitMode.THEY_OWE_ALL, SplitType.EQUAL, members, List.of(user(ALICE)), ALICE, ALICE);

        assertThat(ids(resolution)).containsExactly(BOB, CAROL);
    }

    @Test
    void theyOweAllRejectsSoloGroup() {
        List<User> solo = List.of(user(ALICE));

        assertThatThrownBy(() -> resolver.resolve(
                SplitMode.THEY_OWE_ALL, SplitType.EQUAL, solo, solo, ALICE, ALICE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitModeRejectsEmptySelection() {
        assertThatThrownBy(() -> resolver.resolve(
                SplitMode.SPLIT, SplitType.EQUAL, members, List.of(), ALICE, ALICE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Long> ids(SplitResolution resolution) {
        return resolution.participants().stream().map(User::getId).toList();
    }

    private User user(Long id) {
        return User.builder().id(id).name("user" + id).email("user" + id + "@example.com").build();
    }
}
