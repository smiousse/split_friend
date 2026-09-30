package com.splitfriend.service;

import com.splitfriend.dto.LoyaltyCardForm;
import com.splitfriend.model.LoyaltyCard;
import com.splitfriend.model.LoyaltyCardHolder;
import com.splitfriend.model.LoyaltyCardLogo;
import com.splitfriend.model.LoyaltyPresetLogo;
import com.splitfriend.model.User;
import com.splitfriend.repository.LoyaltyCardHolderRepository;
import com.splitfriend.repository.LoyaltyCardLogoRepository;
import com.splitfriend.repository.LoyaltyCardRepository;
import com.splitfriend.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Loyalty card CRUD and sharing, with access control built into the lookups.
 *
 * As with {@link BudgetService}, there is no unscoped "find by id" on the
 * public surface. Reads go through {@link #requireAccess} (any holder), writes
 * through {@link #requireOwner}. There is no admin bypass on either: a card
 * number is personal data with no support use.
 */
@Service
@Transactional
public class LoyaltyCardService {

    /** Pinned first, then most recently used, then by name. */
    static final Comparator<LoyaltyCardHolder> DISPLAY_ORDER = Comparator
            .comparing(LoyaltyCardHolder::isPinnedFlag).reversed()
            .thenComparing(LoyaltyCardHolder::getLastUsedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(h -> h.getCard().getMerchantName(), String.CASE_INSENSITIVE_ORDER);

    private final LoyaltyCardRepository cardRepository;
    private final LoyaltyCardHolderRepository holderRepository;
    private final LoyaltyCardLogoRepository logoRepository;
    private final UserRepository userRepository;
    private final LogoImageProcessor logoProcessor;
    private final PresetLogoService presetLogoService;

    public LoyaltyCardService(LoyaltyCardRepository cardRepository,
                              LoyaltyCardHolderRepository holderRepository,
                              LoyaltyCardLogoRepository logoRepository,
                              UserRepository userRepository,
                              LogoImageProcessor logoProcessor,
                              PresetLogoService presetLogoService) {
        this.cardRepository = cardRepository;
        this.holderRepository = holderRepository;
        this.logoRepository = logoRepository;
        this.userRepository = userRepository;
        this.logoProcessor = logoProcessor;
        this.presetLogoService = presetLogoService;
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<LoyaltyCardHolder> findForUser(Long userId) {
        return holderRepository.findByUser(userId).stream().sorted(DISPLAY_ORDER).toList();
    }

    /** The caller's pinned cards, for the dashboard strip. */
    @Transactional(readOnly = true)
    public List<LoyaltyCardHolder> findPinnedForUser(Long userId) {
        return findForUser(userId).stream().filter(LoyaltyCardHolder::isPinnedFlag).toList();
    }

    /**
     * The caller's holder row for a card, with the card and its owner loaded.
     * A card not shared with the caller is indistinguishable from one that
     * does not exist.
     */
    @Transactional(readOnly = true)
    public LoyaltyCardHolder requireAccess(Long cardId, Long userId) {
        return holderRepository.findByCardAndUser(cardId, userId)
                .orElseThrow(() -> notAccessible(cardId));
    }

    /** Loads a card the caller may change. Shared holders get nothing here. */
    @Transactional(readOnly = true)
    public LoyaltyCard requireOwner(Long cardId, Long userId) {
        return cardRepository.findByIdForOwner(cardId, userId)
                .orElseThrow(() -> notAccessible(cardId));
    }

    /** Everyone holding the card, owner first. Owner only - it lists emails. */
    @Transactional(readOnly = true)
    public List<LoyaltyCardHolder> findHolders(Long cardId, Long userId) {
        requireOwner(cardId, userId);
        return holderRepository.findByCard(cardId);
    }

    @Transactional(readOnly = true)
    public Optional<LoyaltyCardLogo> findLogo(Long cardId, Long userId) {
        requireAccess(cardId, userId);
        return logoRepository.findById(cardId);
    }

    // ---------- writes (owner) ----------

    /**
     * @param logoUpload raw upload bytes, or {@code null} for no logo (or the
     *                   chosen preset's logo, if the form names one)
     */
    public LoyaltyCard create(LoyaltyCardForm form, byte[] logoUpload, User owner) {
        String number = BarcodeValidator.normalize(form.getBarcodeFormat(), form.getCardNumber());
        byte[] logo = resolveLogo(form, logoUpload);

        LoyaltyCard card = LoyaltyCard.builder()
                .owner(owner)
                .merchantName(form.getMerchantName().strip())
                .cardNumber(number)
                .barcodeFormat(form.getBarcodeFormat())
                .color(colorOrDefault(form.getColor()))
                .note(blankToNull(form.getNote()))
                .logoEtag(logo != null ? LogoImageProcessor.etagOf(logo) : null)
                .build();
        card.getHolders().add(LoyaltyCardHolder.builder().card(card).user(owner).build());

        LoyaltyCard saved = cardRepository.save(card);
        if (logo != null) {
            storeLogo(saved.getId(), logo);
        }
        return saved;
    }

    /**
     * @param logoUpload new logo bytes; or {@code null} to use the chosen
     *                   preset's logo, else keep (or, with
     *                   {@code form.removeLogo}, drop) the current one
     */
    public LoyaltyCard update(Long cardId, LoyaltyCardForm form, byte[] logoUpload, Long userId) {
        LoyaltyCard card = requireOwner(cardId, userId);
        String number = BarcodeValidator.normalize(form.getBarcodeFormat(), form.getCardNumber());
        byte[] logo = resolveLogo(form, logoUpload);

        card.setMerchantName(form.getMerchantName().strip());
        card.setCardNumber(number);
        card.setBarcodeFormat(form.getBarcodeFormat());
        card.setColor(colorOrDefault(form.getColor()));
        card.setNote(blankToNull(form.getNote()));

        if (logo != null) {
            card.setLogoEtag(LogoImageProcessor.etagOf(logo));
            storeLogo(cardId, logo);
        } else if (form.isRemoveLogo()) {
            card.setLogoEtag(null);
            logoRepository.deleteById(cardId);
        }
        return cardRepository.save(card);
    }

    public void delete(Long cardId, Long userId) {
        LoyaltyCard card = requireOwner(cardId, userId);
        logoRepository.deleteById(cardId);
        cardRepository.delete(card);
    }

    public void share(Long cardId, String email, Long userId) {
        LoyaltyCard card = requireOwner(cardId, userId);
        String address = email == null ? "" : email.strip();

        User target = userRepository.findByEmailIgnoreCase(address)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElseThrow(() -> new IllegalArgumentException("No active user with email: " + address));
        if (target.getId().equals(userId)) {
            throw new IllegalArgumentException("You already hold this card");
        }
        boolean alreadyHolder = card.getHolders().stream()
                .anyMatch(h -> h.getUser().getId().equals(target.getId()));
        if (alreadyHolder) {
            throw new IllegalArgumentException("This card is already shared with " + target.getName());
        }

        card.getHolders().add(LoyaltyCardHolder.builder().card(card).user(target).build());
        cardRepository.save(card);
    }

    public void unshare(Long cardId, Long targetUserId, Long userId) {
        LoyaltyCard card = requireOwner(cardId, userId);
        if (targetUserId.equals(userId)) {
            throw new IllegalArgumentException("The owner cannot be removed from their own card");
        }
        card.getHolders().removeIf(h -> h.getUser().getId().equals(targetUserId));
        cardRepository.save(card);
    }

    // ---------- writes (any holder, own row only) ----------

    /** A shared holder drops the card from their own list. */
    public void leave(Long cardId, Long userId) {
        LoyaltyCardHolder holder = requireAccess(cardId, userId);
        if (holder.getCard().isOwnedBy(userId)) {
            throw new IllegalArgumentException("You own this card - delete it instead");
        }
        holderRepository.delete(holder);
    }

    public void togglePin(Long cardId, Long userId) {
        LoyaltyCardHolder holder = requireAccess(cardId, userId);
        holder.setPinned(!holder.isPinnedFlag());
        holderRepository.save(holder);
    }

    public void recordUse(Long cardId, Long userId) {
        LoyaltyCardHolder holder = requireAccess(cardId, userId);
        holder.setUseCount(holder.getUseCountValue() + 1);
        holder.setLastUsedAt(LocalDateTime.now());
        holderRepository.save(holder);
    }

    /** Called before a user is deleted, so their cards do not block it. */
    public void deleteAllForUser(Long userId) {
        for (LoyaltyCard card : cardRepository.findByOwner(userId)) {
            logoRepository.deleteById(card.getId());
            cardRepository.delete(card);
        }
        holderRepository.deleteByUser(userId);
    }

    // ---------- helpers ----------

    /**
     * An uploaded file wins; otherwise the preset's logo, which is copied so
     * the card keeps it even if the preset's logo later changes.
     */
    private byte[] resolveLogo(LoyaltyCardForm form, byte[] logoUpload) {
        if (logoUpload != null) {
            return logoProcessor.process(logoUpload);
        }
        return presetLogoService.logoFor(form.getPresetId())
                .map(LoyaltyPresetLogo::getData)
                .orElse(null);
    }

    private void storeLogo(Long cardId, byte[] logo) {
        logoRepository.save(LoyaltyCardLogo.builder()
                .cardId(cardId)
                .data(logo)
                .contentType("image/png")
                .etag(LogoImageProcessor.etagOf(logo))
                .build());
    }

    private static String colorOrDefault(String color) {
        return color != null && color.matches("^#[0-9a-fA-F]{6}$") ? color.toLowerCase() : LoyaltyCard.DEFAULT_COLOR;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static AccessDeniedException notAccessible(Long cardId) {
        return new AccessDeniedException("Loyalty card not found or not accessible: " + cardId);
    }
}
