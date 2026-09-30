package com.splitfriend.controller;

import com.splitfriend.model.LoyaltyPresetLogo;
import com.splitfriend.service.PresetLogoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Duration;
import java.util.Optional;

/**
 * Brand logos for the preset picker. Not personal data - the same image for
 * every user - so, unlike card logos, browsers may keep them for a while.
 */
@Controller
@RequestMapping("/cards/presets")
public class LoyaltyPresetController {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofDays(7)).cachePrivate();

    private final PresetLogoService presetLogoService;

    public LoyaltyPresetController(PresetLogoService presetLogoService) {
        this.presetLogoService = presetLogoService;
    }

    /** 404 when the preset is unknown or has no usable logo; the picker then shows initials. */
    @GetMapping("/{presetId}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable String presetId,
                                       @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        Optional<LoyaltyPresetLogo> found = presetLogoService.logoFor(presetId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String etag = "\"" + found.get().getEtag() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(CACHE).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .eTag(etag)
                .cacheControl(CACHE)
                .body(found.get().getData());
    }
}
