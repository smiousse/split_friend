package com.splitfriend.controller;

import com.splitfriend.model.LoyaltyPresetLogo;
import com.splitfriend.service.PresetLogoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoyaltyPresetControllerTest {

    private final PresetLogoService service = mock(PresetLogoService.class);
    private final LoyaltyPresetController controller = new LoyaltyPresetController(service);

    @Test
    void servesStoredLogoAsPng() {
        when(service.logoFor("saq-inspire")).thenReturn(Optional.of(
                LoyaltyPresetLogo.builder().presetId("saq-inspire").data(new byte[]{1, 2}).etag("abc").build()));

        ResponseEntity<byte[]> response = controller.logo("saq-inspire", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).containsExactly(1, 2);
        assertThat(controller.logo("saq-inspire", "\"abc\"").getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    }

    @Test
    void missingLogoIs404() {
        when(service.logoFor("costco")).thenReturn(Optional.empty());

        assertThat(controller.logo("costco", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
