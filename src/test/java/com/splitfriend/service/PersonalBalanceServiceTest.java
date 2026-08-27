package com.splitfriend.service;

import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.dto.PersonalBalance;
import com.splitfriend.model.Group;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PersonalBalanceServiceTest {

    private static final Long ME = 1L;
    private static final Long ALICE = 2L;
    private static final Long BOB = 3L;

    private GroupService groupService;
    private BalanceService balanceService;
    private PersonalBalanceService service;
    private User me;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        balanceService = mock(BalanceService.class);
        service = new PersonalBalanceService(groupService, balanceService);
        me = User.builder().id(ME).email("me@x.y").name("Me").role(Role.USER).enabled(true).build();
    }

    private Group group(Long id, String name) {
        return Group.builder().id(id).name(name).currency("CAD").build();
    }

    private BalanceDTO.DebtDTO debt(Long from, String fromName, Long to, String toName, String amount) {
        return new BalanceDTO.DebtDTO(from, fromName, to, toName, new BigDecimal(amount));
    }

    // ----- across all groups -----

    @Test
    void listsWhoTheUserOwes() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "42.5000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe()).containsExactly(
                new PersonalBalance.PersonShare(ALICE, "Alice", new BigDecimal("42.50")));
        assertThat(summary.owedToMe()).isEmpty();
        assertThat(summary.totalIOwe()).isEqualByComparingTo("42.50");
        assertThat(summary.totalOwedToMe()).isEqualByComparingTo("0.00");
        assertThat(summary.net()).isEqualByComparingTo("-42.50");
        assertThat(summary.isSettled()).isFalse();
    }

    @Test
    void listsWhoOwesTheUser() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(20L, "Trip")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", ME, "Me", "20.0000"),
                debt(BOB, "Bob", ME, "Me", "10.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe()).isEmpty();
        assertThat(summary.owedToMe()).extracting(PersonalBalance.PersonShare::name)
                .containsExactly("Alice", "Bob");
        assertThat(summary.totalOwedToMe()).isEqualByComparingTo("30.00");
        assertThat(summary.net()).isEqualByComparingTo("30.00");
    }

    @Test
    void excludesDebtsBetweenOtherMembers() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(20L, "Trip")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", BOB, "Bob", "15.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.isSettled()).isTrue();
        assertThat(summary.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void mergesTheSamePersonAcrossGroupsIntoOneEntry() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "40.0000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "10.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe()).containsExactly(
                new PersonalBalance.PersonShare(ALICE, "Alice", new BigDecimal("50.00")));
        assertThat(summary.totalIOwe()).isEqualByComparingTo("50.00");
    }

    @Test
    void netsOppositeDirectionsForTheSamePersonAcrossGroups() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "40.0000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", ME, "Me", "15.0000")));

        PersonalBalance summary = service.summarize(me);

        // 40 owed out, 15 owed back -> one net debt of 25 to Alice
        assertThat(summary.iOwe()).containsExactly(
                new PersonalBalance.PersonShare(ALICE, "Alice", new BigDecimal("25.00")));
        assertThat(summary.owedToMe()).isEmpty();
        assertThat(summary.net()).isEqualByComparingTo("-25.00");
    }

    @Test
    void dropsPeopleWhoNetOutToZero() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "25.0000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", ME, "Me", "25.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.isSettled()).isTrue();
        assertThat(summary.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void reportsBothDirectionsWithDifferentPeople() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "42.5000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(BOB, "Bob", ME, "Me", "15.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe()).extracting(PersonalBalance.PersonShare::name).containsExactly("Alice");
        assertThat(summary.owedToMe()).extracting(PersonalBalance.PersonShare::name).containsExactly("Bob");
        assertThat(summary.totalIOwe()).isEqualByComparingTo("42.50");
        assertThat(summary.totalOwedToMe()).isEqualByComparingTo("15.00");
        assertThat(summary.net()).isEqualByComparingTo("-27.50");
    }

    @Test
    void sortsEachListByLargestAmountFirst() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "5.0000"),
                debt(ME, "Me", BOB, "Bob", "50.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe()).extracting(PersonalBalance.PersonShare::name)
                .containsExactly("Bob", "Alice");
    }

    @Test
    void roundsAmountsToTwoDecimals() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "33.3350")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe().get(0).amount().toPlainString()).isEqualTo("33.34");
        assertThat(summary.totalIOwe().toPlainString()).isEqualTo("33.34");
    }

    @Test
    void returnsEmptyListsForAUserWithNoGroups() {
        when(groupService.findByUser(me)).thenReturn(List.of());

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.isSettled()).isTrue();
        assertThat(summary.totalIOwe().toPlainString()).isEqualTo("0.00");
        assertThat(summary.totalOwedToMe().toPlainString()).isEqualTo("0.00");
        assertThat(summary.net().toPlainString()).isEqualTo("0.00");
        verifyNoInteractions(balanceService);
    }

    @Test
    void carriesTheCounterpartyUserIdForAvatarColouring() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "10.0000"),
                debt(BOB, "Bob", ME, "Me", "10.0000")));

        PersonalBalance summary = service.summarize(me);

        assertThat(summary.iOwe().get(0).userId()).isEqualTo(ALICE);
        assertThat(summary.owedToMe().get(0).userId()).isEqualTo(BOB);
    }

    // ----- single group -----

    @Test
    void summarizeInGroupOnlyLooksAtThatGroup() {
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "42.5000")));

        PersonalBalance summary = service.summarizeInGroup(me, 10L);

        assertThat(summary.iOwe()).containsExactly(
                new PersonalBalance.PersonShare(ALICE, "Alice", new BigDecimal("42.50")));
        verify(balanceService, times(1)).calculateDebts(10L);
        verify(groupService, never()).findByUser(me);
    }

    @Test
    void summarizeInGroupOrientsBothDirections() {
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "40.0000"),
                debt(BOB, "Bob", ME, "Me", "15.0000")));

        PersonalBalance summary = service.summarizeInGroup(me, 10L);

        assertThat(summary.iOwe()).extracting(PersonalBalance.PersonShare::name).containsExactly("Alice");
        assertThat(summary.owedToMe()).extracting(PersonalBalance.PersonShare::name).containsExactly("Bob");
        assertThat(summary.net()).isEqualByComparingTo("-25.00");
    }

    @Test
    void summarizeInGroupIsSettledWhenTheUserHasNoDebtsThere() {
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ALICE, "Alice", BOB, "Bob", "15.0000")));

        PersonalBalance summary = service.summarizeInGroup(me, 10L);

        assertThat(summary.isSettled()).isTrue();
        assertThat(summary.net()).isEqualByComparingTo("0.00");
    }
}
