package com.splitfriend.controller;

import com.splitfriend.dto.ExpenseDTO;
import com.splitfriend.model.Group;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.SplitMode;
import com.splitfriend.model.enums.SplitType;
import com.splitfriend.security.CustomUserDetailsService.CustomUserDetails;
import com.splitfriend.service.ExpenseService;
import com.splitfriend.service.ExportService;
import com.splitfriend.service.GroupService;
import com.splitfriend.service.SplitModeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExpenseControllerSplitModeTest {

    private static final Long GROUP_ID = 2L;
    private static final Long ALICE = 1L;
    private static final Long BOB = 2L;
    private static final Long CAROL = 3L;

    private ExpenseService expenseService;
    private GroupService groupService;
    private ExpenseController controller;
    private RedirectAttributes redirectAttributes;

    @BeforeEach
    void setUp() {
        expenseService = mock(ExpenseService.class);
        groupService = mock(GroupService.class);
        controller = new ExpenseController(expenseService, groupService,
                mock(ExportService.class), new SplitModeResolver());
        redirectAttributes = new RedirectAttributesModelMap();
    }

    @Test
    void splitModeUsesSelectedParticipantsAndChosenSplitType() {
        withMembers(user(ALICE), user(BOB), user(CAROL));

        String view = post(dto(SplitMode.SPLIT, SplitType.SHARES, ALICE),
                List.of(ALICE, BOB), Map.of("shares_1", "2", "shares_2", "1"));

        assertThat(view).isEqualTo("redirect:/groups/2");
        assertThat(capturedSplitType()).isEqualTo(SplitType.SHARES);
        assertThat(capturedParticipantIds()).containsExactly(ALICE, BOB);
    }

    @Test
    void iOweAllChargesTheCurrentUserOnly() {
        withMembers(user(ALICE), user(BOB), user(CAROL));

        String view = post(dto(SplitMode.I_OWE_ALL, SplitType.EXACT, BOB), List.of(ALICE, BOB, CAROL), Map.of());

        assertThat(view).isEqualTo("redirect:/groups/2");
        assertThat(capturedSplitType()).isEqualTo(SplitType.EQUAL);
        assertThat(capturedParticipantIds()).containsExactly(ALICE);
    }

    @Test
    void theyOweAllExcludesThePayer() {
        withMembers(user(ALICE), user(BOB), user(CAROL));

        post(dto(SplitMode.THEY_OWE_ALL, SplitType.EQUAL, ALICE), List.of(ALICE, BOB, CAROL), Map.of());

        assertThat(capturedSplitType()).isEqualTo(SplitType.EQUAL);
        assertThat(capturedParticipantIds()).containsExactly(BOB, CAROL);
    }

    @Test
    void theyOweAllInTwoMemberGroupChargesTheOtherMember() {
        withMembers(user(ALICE), user(BOB));

        post(dto(SplitMode.THEY_OWE_ALL, SplitType.EQUAL, ALICE), List.of(ALICE, BOB), Map.of());

        assertThat(capturedParticipantIds()).containsExactly(BOB);
    }

    @Test
    void theyOweAllInSoloGroupIsRejected() {
        withMembers(user(ALICE));

        String view = post(dto(SplitMode.THEY_OWE_ALL, SplitType.EQUAL, ALICE), List.of(ALICE), Map.of());

        assertThat(view).isEqualTo("redirect:/expenses/add?groupId=2");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("error");
        verifyNoInteractions(expenseService);
    }

    @Test
    void missingSplitModeBehavesLikeSplit() {
        withMembers(user(ALICE), user(BOB), user(CAROL));

        post(dto(null, SplitType.EQUAL, ALICE), null, Map.of());

        assertThat(capturedSplitType()).isEqualTo(SplitType.EQUAL);
        assertThat(capturedParticipantIds()).containsExactly(ALICE, BOB, CAROL);
    }

    private void withMembers(User... members) {
        List<User> memberList = List.of(members);
        Group group = Group.builder().id(GROUP_ID).name("Trip").currency("$").build();

        when(groupService.isUserMember(GROUP_ID, ALICE)).thenReturn(true);
        when(groupService.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupService.findByIdWithMembers(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupService.getGroupMemberUsers(GROUP_ID)).thenReturn(memberList);
    }

    private String post(ExpenseDTO dto, List<Long> participantIds, Map<String, String> params) {
        BindingResult result = new BeanPropertyBindingResult(dto, "expense");
        return controller.addExpense(new CustomUserDetails(user(ALICE)), dto, result, null,
                participantIds, params, redirectAttributes, new ExtendedModelMap());
    }

    private ExpenseDTO dto(SplitMode mode, SplitType splitType, Long payerId) {
        return ExpenseDTO.builder()
                .groupId(GROUP_ID)
                .description("Dinner")
                .amount(new BigDecimal("30.00"))
                .splitType(splitType)
                .splitMode(mode)
                .paidById(payerId)
                .expenseDate(LocalDate.of(2026, 8, 27))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedParticipantIds() {
        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseService).createExpense(any(), any(), any(), any(), any(), any(),
                anyMap(), anyMap(), anyMap(), captor.capture(), eq(null));
        return captor.getValue().stream().map(User::getId).toList();
    }

    private SplitType capturedSplitType() {
        ArgumentCaptor<SplitType> captor = ArgumentCaptor.forClass(SplitType.class);
        verify(expenseService).createExpense(any(), any(), any(), any(), captor.capture(), any(),
                anyMap(), anyMap(), anyMap(), anyList(), eq(null));
        return captor.getValue();
    }

    private User user(Long id) {
        return User.builder().id(id).name("user" + id).email("user" + id + "@example.com").build();
    }
}
