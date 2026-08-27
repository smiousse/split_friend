package com.splitfriend.controller.api;

import com.splitfriend.dto.ApiBalanceResponse;
import com.splitfriend.dto.PersonalBalance;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.Role;
import com.splitfriend.security.CustomUserDetailsService.CustomUserDetails;
import com.splitfriend.service.PersonalBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The balance arithmetic is covered by
 * {@link com.splitfriend.service.PersonalBalanceServiceTest}; this only checks
 * the mapping onto the published JSON shape.
 */
class BalanceApiControllerTest {

    private PersonalBalanceService personalBalanceService;
    private BalanceApiController controller;
    private User me;

    @BeforeEach
    void setUp() {
        personalBalanceService = mock(PersonalBalanceService.class);
        controller = new BalanceApiController(personalBalanceService);
        me = User.builder().id(1L).email("me@x.y").name("Me").role(Role.USER).enabled(true).build();
    }

    private ApiBalanceResponse call() {
        return controller.balances(new CustomUserDetails(me)).getBody();
    }

    private PersonalBalance.PersonShare share(Long id, String name, String amount) {
        return new PersonalBalance.PersonShare(id, name, new BigDecimal(amount));
    }

    @Test
    void mapsTheSummaryOntoTheResponse() {
        when(personalBalanceService.summarize(me)).thenReturn(new PersonalBalance(
                new BigDecimal("42.50"),
                new BigDecimal("15.00"),
                new BigDecimal("-27.50"),
                List.of(share(2L, "Alice", "42.50")),
                List.of(share(3L, "Bob", "15.00"))));

        ApiBalanceResponse response = call();

        assertThat(response.user()).isEqualTo("Me");
        assertThat(response.totalIOwe()).isEqualByComparingTo("42.50");
        assertThat(response.totalOwedToMe()).isEqualByComparingTo("15.00");
        assertThat(response.net()).isEqualByComparingTo("-27.50");
        assertThat(response.iOwe()).containsExactly(new ApiBalanceResponse.Person("Alice", new BigDecimal("42.50")));
        assertThat(response.owedToMe()).containsExactly(new ApiBalanceResponse.Person("Bob", new BigDecimal("15.00")));
    }

    @Test
    void preservesListOrder() {
        when(personalBalanceService.summarize(me)).thenReturn(new PersonalBalance(
                new BigDecimal("55.00"), BigDecimal.ZERO, new BigDecimal("-55.00"),
                List.of(share(3L, "Bob", "50.00"), share(2L, "Alice", "5.00")),
                List.of()));

        assertThat(call().iOwe()).extracting(ApiBalanceResponse.Person::name)
                .containsExactly("Bob", "Alice");
    }

    @Test
    void doesNotExposeUserIds() {
        when(personalBalanceService.summarize(me)).thenReturn(new PersonalBalance(
                BigDecimal.ZERO, new BigDecimal("15.00"), new BigDecimal("15.00"),
                List.of(), List.of(share(3L, "Bob", "15.00"))));

        // ApiBalanceResponse.Person carries name + amount only
        assertThat(ApiBalanceResponse.Person.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("name", "amount");
        assertThat(call().owedToMe()).containsExactly(
                new ApiBalanceResponse.Person("Bob", new BigDecimal("15.00")));
    }

    @Test
    void handlesASettledUser() {
        when(personalBalanceService.summarize(me)).thenReturn(new PersonalBalance(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()));

        ApiBalanceResponse response = call();

        assertThat(response.iOwe()).isEmpty();
        assertThat(response.owedToMe()).isEmpty();
        assertThat(response.net()).isEqualByComparingTo("0");
    }
}
