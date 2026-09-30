package com.splitfriend.controller;

import com.splitfriend.dto.LoyaltyCardForm;
import com.splitfriend.model.LoyaltyCard;
import com.splitfriend.model.LoyaltyCardHolder;
import com.splitfriend.model.LoyaltyCardLogo;
import com.splitfriend.model.enums.BarcodeFormat;
import com.splitfriend.security.CustomUserDetailsService.CustomUserDetails;
import com.splitfriend.service.LogoImageProcessor;
import com.splitfriend.service.LoyaltyCardService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Optional;

/**
 * The Loyalty Cards section.
 *
 * Every lookup goes through {@link LoyaltyCardService}, which only returns
 * cards the caller holds (reads) or owns (writes); nothing here checks access
 * itself.
 */
@Controller
@RequestMapping("/cards")
public class LoyaltyCardController {

    private static final String FORM_VIEW = "cards/form";

    private final LoyaltyCardService cardService;

    public LoyaltyCardController(LoyaltyCardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("holders", cardService.findForUser(userDetails.getId()));
        model.addAttribute("currentUserId", userDetails.getId());
        return "cards/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("card", new LoyaltyCardForm());
        addFormOptions(model, null, null);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(@AuthenticationPrincipal CustomUserDetails userDetails,
                         @Valid @ModelAttribute("card") LoyaltyCardForm form,
                         BindingResult result,
                         @RequestParam(value = "logo", required = false) MultipartFile logo,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addFormOptions(model, null, null);
            return FORM_VIEW;
        }
        try {
            LoyaltyCard card = cardService.create(form, bytesOf(logo), userDetails.getUser());
            redirectAttributes.addFlashAttribute("message", "Card added");
            return "redirect:/cards/" + card.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            addFormOptions(model, null, null);
            return FORM_VIEW;
        }
    }

    /** The screen shown at the till. */
    @GetMapping("/{id}")
    public String view(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @PathVariable Long id,
                       Model model) {
        LoyaltyCardHolder holder = cardService.requireAccess(id, userDetails.getId());
        model.addAttribute("holder", holder);
        model.addAttribute("card", holder.getCard());
        model.addAttribute("isOwner", holder.getCard().isOwnedBy(userDetails.getId()));
        return "cards/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal CustomUserDetails userDetails,
                           @PathVariable Long id,
                           Model model) {
        LoyaltyCard card = cardService.requireOwner(id, userDetails.getId());
        model.addAttribute("card", LoyaltyCardForm.from(card));
        addFormOptions(model, card, userDetails.getId());
        return FORM_VIEW;
    }

    @PostMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal CustomUserDetails userDetails,
                       @PathVariable Long id,
                       @Valid @ModelAttribute("card") LoyaltyCardForm form,
                       BindingResult result,
                       @RequestParam(value = "logo", required = false) MultipartFile logo,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addFormOptions(model, cardService.requireOwner(id, userDetails.getId()), userDetails.getId());
            return FORM_VIEW;
        }
        try {
            cardService.update(id, form, bytesOf(logo), userDetails.getId());
            redirectAttributes.addFlashAttribute("message", "Card updated");
            return "redirect:/cards/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            addFormOptions(model, cardService.requireOwner(id, userDetails.getId()), userDetails.getId());
            return FORM_VIEW;
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails userDetails,
                         @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        cardService.delete(id, userDetails.getId());
        redirectAttributes.addFlashAttribute("message", "Card deleted");
        return "redirect:/cards";
    }

    @PostMapping("/{id}/pin")
    public String togglePin(@AuthenticationPrincipal CustomUserDetails userDetails,
                            @PathVariable Long id,
                            @RequestParam(value = "back", required = false) String back) {
        cardService.togglePin(id, userDetails.getId());
        // A fixed choice, never a caller-supplied URL: no open redirect.
        return "view".equals(back) ? "redirect:/cards/" + id : "redirect:/cards";
    }

    /** Sent by the card screen when it opens; drives the list ordering. */
    @PostMapping("/{id}/used")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void recordUse(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @PathVariable Long id) {
        cardService.recordUse(id, userDetails.getId());
    }

    // ---------- sharing ----------

    @PostMapping("/{id}/share")
    public String share(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Long id,
                        @RequestParam String email,
                        RedirectAttributes redirectAttributes) {
        try {
            cardService.share(id, email, userDetails.getId());
            redirectAttributes.addFlashAttribute("message", "Card shared");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            // Two quick submits both pass the duplicate check; the unique key stops the second.
            redirectAttributes.addFlashAttribute("error", "This card is already shared with that person");
        }
        return "redirect:/cards/" + id + "/edit";
    }

    @PostMapping("/{id}/holders/{userId}/remove")
    public String unshare(@AuthenticationPrincipal CustomUserDetails userDetails,
                          @PathVariable Long id,
                          @PathVariable Long userId,
                          RedirectAttributes redirectAttributes) {
        try {
            cardService.unshare(id, userId, userDetails.getId());
            redirectAttributes.addFlashAttribute("message", "Sharing removed");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cards/" + id + "/edit";
    }

    @PostMapping("/{id}/leave")
    public String leave(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable Long id,
                        RedirectAttributes redirectAttributes) {
        try {
            cardService.leave(id, userDetails.getId());
            redirectAttributes.addFlashAttribute("message", "Card removed from your list");
            return "redirect:/cards";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/cards/" + id;
        }
    }

    // ---------- logo ----------

    /**
     * {@code no-store}: the browser's HTTP cache survives logout, which would
     * leave a user's logos readable on a shared device. Offline copies are the
     * service worker's job, and its cache is cleared on logout. The ETag still
     * lets a revalidating client get a 304.
     */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> logo(@AuthenticationPrincipal CustomUserDetails userDetails,
                                       @PathVariable Long id,
                                       @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        Optional<LoyaltyCardLogo> found = cardService.findLogo(id, userDetails.getId());
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        LoyaltyCardLogo logo = found.get();
        String etag = "\"" + logo.getEtag() + "\"";
        CacheControl cache = CacheControl.noStore();

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getContentType()))
                .eTag(etag)
                .cacheControl(cache)
                .body(logo.getData());
    }

    // ---------- helpers ----------

    private void addFormOptions(Model model, LoyaltyCard existing, Long userId) {
        model.addAttribute("formats", BarcodeFormat.values());
        model.addAttribute("existing", existing);
        if (existing != null) {
            model.addAttribute("holders", cardService.findHolders(existing.getId(), userId));
        }
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > LogoImageProcessor.MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("The logo must be smaller than 2 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded logo");
        }
    }
}
