package com.splitfriend.dto;

import com.splitfriend.model.LoyaltyCard;
import com.splitfriend.model.enums.BarcodeFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Add/edit form. {@code cardNumber} is validated for its symbology by
 * {@code BarcodeValidator} in the service, not here, since the rule depends
 * on {@code barcodeFormat}.
 */
@Data
public class LoyaltyCardForm {

    @NotBlank
    @Size(max = 100)
    private String merchantName;

    @NotBlank
    @Size(max = 500)
    private String cardNumber;

    @NotNull
    private BarcodeFormat barcodeFormat = BarcodeFormat.CODE128;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    private String color = LoyaltyCard.DEFAULT_COLOR;

    @Size(max = 500)
    private String note;

    /** Edit only: drop the current logo. Ignored when a new one is uploaded. */
    private boolean removeLogo;

    public static LoyaltyCardForm from(LoyaltyCard card) {
        LoyaltyCardForm form = new LoyaltyCardForm();
        form.setMerchantName(card.getMerchantName());
        form.setCardNumber(card.getCardNumber());
        form.setBarcodeFormat(card.getBarcodeFormat());
        form.setColor(card.getColor());
        form.setNote(card.getNote());
        return form;
    }
}
