package com.splitfriend.dto;

import com.splitfriend.model.LoyaltyCard;

/**
 * A known loyalty program offered as a starting point when adding a card.
 *
 * {@code domain} is only ever used to look up the brand's icon; it comes from
 * the catalog shipped with the app, never from a request.
 */
public record LoyaltyPreset(String id, String name, String domain, String color) {

    public String initials() {
        return LoyaltyCard.initialsOf(name);
    }

    public String initialsColor() {
        return LoyaltyCard.initialsColorFor(color);
    }
}
