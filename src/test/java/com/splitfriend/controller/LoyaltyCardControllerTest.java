package com.splitfriend.controller;

import com.splitfriend.dto.LoyaltyCardForm;
import com.splitfriend.model.LoyaltyCard;
import com.splitfriend.model.LoyaltyCardLogo;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BarcodeFormat;
import com.splitfriend.security.CustomUserDetailsService.CustomUserDetails;
import com.splitfriend.service.LoyaltyCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyCardControllerTest {

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 10L;

    private LoyaltyCardService service;
    private LoyaltyCardController controller;
    private CustomUserDetails principal;

    @BeforeEach
    void setUp() {
        service = mock(LoyaltyCardService.class);
        controller = new LoyaltyCardController(service);
        principal = new CustomUserDetails(User.builder().id(USER_ID).name("Me").email("me@example.com")
                .passwordHash("x").build());
    }

    @Test
    void createRedirectsToTheNewCard() {
        when(service.create(any(), any(), any())).thenReturn(LoyaltyCard.builder().id(CARD_ID).build());
        LoyaltyCardForm form = validForm();

        String view = controller.create(principal, form, new BeanPropertyBindingResult(form, "card"),
                new MockMultipartFile("logo", new byte[]{1, 2}), new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/cards/" + CARD_ID);
        verify(service).create(eq(form), eq(new byte[]{1, 2}), any());
    }

    @Test
    void emptyLogoFieldIsTreatedAsNoLogo() {
        when(service.create(any(), any(), any())).thenReturn(LoyaltyCard.builder().id(CARD_ID).build());
        LoyaltyCardForm form = validForm();

        controller.create(principal, form, new BeanPropertyBindingResult(form, "card"),
                new MockMultipartFile("logo", new byte[0]), new ExtendedModelMap(), new RedirectAttributesModelMap());

        verify(service).create(eq(form), isNull(), any());
    }

    @Test
    void createRerendersFormWithTheServiceError() {
        when(service.create(any(), any(), any())).thenThrow(new IllegalArgumentException("EAN-13 check digit does not match"));
        LoyaltyCardForm form = validForm();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.create(principal, form, new BeanPropertyBindingResult(form, "card"), null, model,
                new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("cards/form");
        assertThat(model.get("error")).isEqualTo("EAN-13 check digit does not match");
        assertThat(model.get("formats")).isNotNull();
    }

    @Test
    void logoIsServedUncachedWithEtag() {
        when(service.findLogo(CARD_ID, USER_ID)).thenReturn(Optional.of(
                LoyaltyCardLogo.builder().cardId(CARD_ID).data(new byte[]{7}).contentType("image/png").etag("abc").build()));

        ResponseEntity<byte[]> response = controller.logo(principal, CARD_ID, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"abc\"");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody()).containsExactly(7);
    }

    @Test
    void logoAnswers304WhenUnchanged() {
        when(service.findLogo(CARD_ID, USER_ID)).thenReturn(Optional.of(
                LoyaltyCardLogo.builder().cardId(CARD_ID).data(new byte[]{7}).contentType("image/png").etag("abc").build()));

        assertThat(controller.logo(principal, CARD_ID, "\"abc\"").getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void missingLogoIs404() {
        when(service.findLogo(CARD_ID, USER_ID)).thenReturn(Optional.empty());

        assertThat(controller.logo(principal, CARD_ID, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shareErrorIsFlashedBackToTheEditPage() {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("No active user with email: x"))
                .when(service).share(CARD_ID, "x", USER_ID);
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.share(principal, CARD_ID, "x", redirect);

        assertThat(view).isEqualTo("redirect:/cards/" + CARD_ID + "/edit");
        assertThat(redirect.getFlashAttributes().get("error")).isEqualTo("No active user with email: x");
    }

    private static LoyaltyCardForm validForm() {
        LoyaltyCardForm form = new LoyaltyCardForm();
        form.setMerchantName("Metro");
        form.setCardNumber("ABC");
        form.setBarcodeFormat(BarcodeFormat.CODE128);
        return form;
    }
}
