package com.splitfriend.controller.api;

import com.splitfriend.dto.ApiBalanceResponse;
import com.splitfriend.dto.BalanceDTO;
import com.splitfriend.model.Group;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import com.splitfriend.security.CustomUserDetailsService.CustomUserDetails;
import com.splitfriend.service.BalanceService;
import com.splitfriend.service.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalanceApiControllerTest {

    private static final Long ME = 1L;
    private static final Long ALICE = 2L;
    private static final Long BOB = 3L;

    private GroupService groupService;
    private BalanceService balanceService;
    private BalanceApiController controller;
    private User me;

    @BeforeEach
    void setUp() {
        groupService = mock(GroupService.class);
        balanceService = mock(BalanceService.class);
        controller = new BalanceApiController(groupService, balanceService);
        me = User.builder().id(ME).email("me@x.y").name("Me").role(Role.USER).enabled(true).build();
    }

    private ApiBalanceResponse call() {
        return controller.balances(new CustomUserDetails(me)).getBody();
    }

    private Group group(Long id, String name) {
        return Group.builder().id(id).name(name).currency("CAD").build();
    }

    private BalanceDTO.DebtDTO debt(Long from, String fromName, Long to, String toName, String amount) {
        return new BalanceDTO.DebtDTO(from, fromName, to, toName, new BigDecimal(amount));
    }

    @Test
    void listsWhoTheUserOwes() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "42.5000")));

        ApiBalanceResponse response = call();

        assertThat(response.user()).isEqualTo("Me");
        assertThat(response.iOwe()).containsExactly(new ApiBalanceResponse.Person("Alice", new BigDecimal("42.50")));
        assertThat(response.owedToMe()).isEmpty();
        assertThat(response.totalIOwe()).isEqualByComparingTo("42.50");
        assertThat(response.totalOwedToMe()).isEqualByComparingTo("0.00");
        assertThat(response.net()).isEqualByComparingTo("-42.50");
    }

    @Test
    void listsWhoOwesTheUser() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(20L, "Trip")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", ME, "Me", "20.0000"),
                debt(BOB, "Bob", ME, "Me", "10.0000")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).isEmpty();
        assertThat(response.owedToMe()).extracting(ApiBalanceResponse.Person::name)
                .containsExactly("Alice", "Bob");
        assertThat(response.totalOwedToMe()).isEqualByComparingTo("30.00");
        assertThat(response.net()).isEqualByComparingTo("30.00");
    }

    @Test
    void excludesDebtsBetweenOtherMembers() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(20L, "Trip")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", BOB, "Bob", "15.0000")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).isEmpty();
        assertThat(response.owedToMe()).isEmpty();
        assertThat(response.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void mergesTheSamePersonAcrossGroupsIntoOneEntry() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "40.0000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "10.0000")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).containsExactly(new ApiBalanceResponse.Person("Alice", new BigDecimal("50.00")));
        assertThat(response.totalIOwe()).isEqualByComparingTo("50.00");
    }

    @Test
    void netsOppositeDirectionsForTheSamePersonAcrossGroups() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "40.0000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", ME, "Me", "15.0000")));

        ApiBalanceResponse response = call();

        // 40 owed out, 15 owed back -> one net debt of 25 to Alice
        assertThat(response.iOwe()).containsExactly(new ApiBalanceResponse.Person("Alice", new BigDecimal("25.00")));
        assertThat(response.owedToMe()).isEmpty();
        assertThat(response.net()).isEqualByComparingTo("-25.00");
    }

    @Test
    void dropsPeopleWhoNetOutToZero() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "25.0000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(ALICE, "Alice", ME, "Me", "25.0000")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).isEmpty();
        assertThat(response.owedToMe()).isEmpty();
        assertThat(response.net()).isEqualByComparingTo("0.00");
    }

    @Test
    void reportsBothDirectionsWithDifferentPeople() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates"), group(20L, "Trip")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "42.5000")));
        when(balanceService.calculateDebts(20L)).thenReturn(List.of(
                debt(BOB, "Bob", ME, "Me", "15.0000")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).containsExactly(new ApiBalanceResponse.Person("Alice", new BigDecimal("42.50")));
        assertThat(response.owedToMe()).containsExactly(new ApiBalanceResponse.Person("Bob", new BigDecimal("15.00")));
        assertThat(response.totalIOwe()).isEqualByComparingTo("42.50");
        assertThat(response.totalOwedToMe()).isEqualByComparingTo("15.00");
        assertThat(response.net()).isEqualByComparingTo("-27.50");
    }

    @Test
    void sortsEachListByLargestAmountFirst() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "5.0000"),
                debt(ME, "Me", BOB, "Bob", "50.0000")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).extracting(ApiBalanceResponse.Person::name)
                .containsExactly("Bob", "Alice");
    }

    @Test
    void roundsAmountsToTwoDecimals() {
        when(groupService.findByUser(me)).thenReturn(List.of(group(10L, "Roommates")));
        when(balanceService.calculateDebts(10L)).thenReturn(List.of(
                debt(ME, "Me", ALICE, "Alice", "33.3350")));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe().get(0).amount().toPlainString()).isEqualTo("33.34");
        assertThat(response.totalIOwe().toPlainString()).isEqualTo("33.34");
    }

    @Test
    void returnsEmptyListsForAUserWithNoGroups() {
        when(groupService.findByUser(me)).thenReturn(List.of());

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).isEmpty();
        assertThat(response.owedToMe()).isEmpty();
        assertThat(response.totalIOwe().toPlainString()).isEqualTo("0.00");
        assertThat(response.totalOwedToMe().toPlainString()).isEqualTo("0.00");
        assertThat(response.net().toPlainString()).isEqualTo("0.00");
    }
}
