package com.splitfriend.service;

import com.splitfriend.dto.LoyaltyCardForm;
import com.splitfriend.model.LoyaltyCard;
import com.splitfriend.model.LoyaltyCardHolder;
import com.splitfriend.model.LoyaltyCardLogo;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BarcodeFormat;
import com.splitfriend.repository.LoyaltyCardHolderRepository;
import com.splitfriend.repository.LoyaltyCardLogoRepository;
import com.splitfriend.repository.LoyaltyCardRepository;
import com.splitfriend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyCardServiceTest {

    private static final Long OWNER = 1L;
    private static final Long PARTNER = 2L;
    private static final Long OUTSIDER = 99L;
    private static final Long CARD_ID = 10L;

    private LoyaltyCardRepository cardRepository;
    private LoyaltyCardHolderRepository holderRepository;
    private LoyaltyCardLogoRepository logoRepository;
    private UserRepository userRepository;
    private LogoImageProcessor logoProcessor;
    private LoyaltyCardService service;

    @BeforeEach
    void setUp() {
        cardRepository = mock(LoyaltyCardRepository.class);
        holderRepository = mock(LoyaltyCardHolderRepository.class);
        logoRepository = mock(LoyaltyCardLogoRepository.class);
        userRepository = mock(UserRepository.class);
        logoProcessor = mock(LogoImageProcessor.class);
        service = new LoyaltyCardService(cardRepository, holderRepository, logoRepository, userRepository, logoProcessor);
        when(cardRepository.save(any(LoyaltyCard.class))).thenAnswer(inv -> {
            LoyaltyCard c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(CARD_ID);
            }
            return c;
        });
    }

    // ---------- create ----------

    @Test
    @DisplayName("creating a card gives the owner a holder row and normalizes the number")
    void createAddsOwnerHolder() {
        LoyaltyCard card = service.create(form("Metro", "400638133393", BarcodeFormat.EAN13), null, user(OWNER));

        assertThat(card.getCardNumber()).isEqualTo("4006381333931");
        assertThat(card.getHolders()).hasSize(1);
        assertThat(card.getHolders().get(0).getUser().getId()).isEqualTo(OWNER);
        assertThat(card.hasLogo()).isFalse();
    }

    @Test
    @DisplayName("an unencodable number is refused before anything is saved")
    void createRejectsInvalidNumber() {
        assertThatThrownBy(() -> service.create(form("Metro", "123", BarcodeFormat.EAN13), null, user(OWNER)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("a logo upload is processed and stored under the card id, with its etag on the card")
    void createStoresLogo() {
        byte[] processed = {9, 9, 9};
        when(logoProcessor.process(any())).thenReturn(processed);

        LoyaltyCard card = service.create(form("Metro", "ABC", BarcodeFormat.CODE128), new byte[]{1}, user(OWNER));

        ArgumentCaptor<LoyaltyCardLogo> saved = ArgumentCaptor.forClass(LoyaltyCardLogo.class);
        verify(logoRepository).save(saved.capture());
        assertThat(saved.getValue().getCardId()).isEqualTo(CARD_ID);
        assertThat(saved.getValue().getData()).isEqualTo(processed);
        assertThat(card.getLogoEtag()).isEqualTo(LogoImageProcessor.etagOf(processed));
    }

    // ---------- read access ----------

    @Test
    @DisplayName("a holder the card was shared with can read it")
    void sharedHolderCanRead() {
        LoyaltyCard card = card();
        when(holderRepository.findByCardAndUser(CARD_ID, PARTNER)).thenReturn(Optional.of(holder(card, PARTNER)));

        assertThat(service.requireAccess(CARD_ID, PARTNER).getCard().getId()).isEqualTo(CARD_ID);
    }

    @Test
    @DisplayName("a non-holder is refused")
    void outsiderCannotRead() {
        when(holderRepository.findByCardAndUser(CARD_ID, OUTSIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireAccess(CARD_ID, OUTSIDER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("the logo is only returned to a holder - the logo table is never read otherwise")
    void logoRequiresAccess() {
        when(holderRepository.findByCardAndUser(CARD_ID, OUTSIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findLogo(CARD_ID, OUTSIDER)).isInstanceOf(AccessDeniedException.class);
        verify(logoRepository, never()).findById(anyLong());
    }

    // ---------- write access ----------

    @Test
    @DisplayName("a shared holder cannot edit, delete or re-share the card")
    void sharedHolderCannotWrite() {
        when(cardRepository.findByIdForOwner(CARD_ID, PARTNER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(CARD_ID, form("X", "Y", BarcodeFormat.CODE128), null, PARTNER))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(CARD_ID, PARTNER)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.share(CARD_ID, "x@example.com", PARTNER)).isInstanceOf(AccessDeniedException.class);
        verify(cardRepository, never()).save(any());
        verify(cardRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleting a card also deletes its logo")
    void deleteRemovesLogo() {
        LoyaltyCard card = card();
        when(cardRepository.findByIdForOwner(CARD_ID, OWNER)).thenReturn(Optional.of(card));

        service.delete(CARD_ID, OWNER);

        verify(logoRepository).deleteById(CARD_ID);
        verify(cardRepository).delete(card);
    }

    @Test
    @DisplayName("update with removeLogo clears the etag and the stored logo")
    void updateRemovesLogo() {
        LoyaltyCard card = card();
        card.setLogoEtag("abc");
        when(cardRepository.findByIdForOwner(CARD_ID, OWNER)).thenReturn(Optional.of(card));
        LoyaltyCardForm form = form("Metro", "ABC", BarcodeFormat.CODE128);
        form.setRemoveLogo(true);

        service.update(CARD_ID, form, null, OWNER);

        assertThat(card.getLogoEtag()).isNull();
        verify(logoRepository).deleteById(CARD_ID);
    }

    // ---------- sharing ----------

    @Test
    @DisplayName("the owner can share with another user by email")
    void shareAddsHolder() {
        LoyaltyCard card = card();
        when(cardRepository.findByIdForOwner(CARD_ID, OWNER)).thenReturn(Optional.of(card));
        when(userRepository.findByEmailIgnoreCase("partner@example.com")).thenReturn(Optional.of(user(PARTNER)));

        service.share(CARD_ID, " partner@example.com ", OWNER);  // trimmed; case handled by the query

        assertThat(card.getHolders()).extracting(h -> h.getUser().getId()).containsExactly(OWNER, PARTNER);
    }

    @Test
    void shareRejectsUnknownSelfDuplicateAndDisabled() {
        LoyaltyCard card = card();
        when(cardRepository.findByIdForOwner(CARD_ID, OWNER)).thenReturn(Optional.of(card));
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(user(OWNER)));
        User disabled = user(3L);
        disabled.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("disabled@example.com")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.share(CARD_ID, "nobody@example.com", OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.share(CARD_ID, "owner@example.com", OWNER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.share(CARD_ID, "disabled@example.com", OWNER)).isInstanceOf(IllegalArgumentException.class);

        when(userRepository.findByEmailIgnoreCase("partner@example.com")).thenReturn(Optional.of(user(PARTNER)));
        service.share(CARD_ID, "partner@example.com", OWNER);
        assertThatThrownBy(() -> service.share(CARD_ID, "partner@example.com", OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the owner's own holder row cannot be removed by unsharing")
    void unshareCannotRemoveOwner() {
        when(cardRepository.findByIdForOwner(CARD_ID, OWNER)).thenReturn(Optional.of(card()));

        assertThatThrownBy(() -> service.unshare(CARD_ID, OWNER, OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unshareRemovesHolder() {
        LoyaltyCard card = card();
        card.getHolders().add(holder(card, PARTNER));
        when(cardRepository.findByIdForOwner(CARD_ID, OWNER)).thenReturn(Optional.of(card));

        service.unshare(CARD_ID, PARTNER, OWNER);

        assertThat(card.getHolders()).extracting(h -> h.getUser().getId()).containsExactly(OWNER);
    }

    @Test
    @DisplayName("a shared holder can leave; the owner cannot leave their own card")
    void leave() {
        LoyaltyCard card = card();
        LoyaltyCardHolder partnerRow = holder(card, PARTNER);
        when(holderRepository.findByCardAndUser(CARD_ID, PARTNER)).thenReturn(Optional.of(partnerRow));
        when(holderRepository.findByCardAndUser(CARD_ID, OWNER)).thenReturn(Optional.of(card.getHolders().get(0)));

        service.leave(CARD_ID, PARTNER);
        verify(holderRepository).delete(partnerRow);

        assertThatThrownBy(() -> service.leave(CARD_ID, OWNER)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- per-holder state ----------

    @Test
    @DisplayName("pin and usage are recorded on the caller's own holder row")
    void pinAndUseArePerHolder() {
        LoyaltyCard card = card();
        LoyaltyCardHolder partnerRow = holder(card, PARTNER);
        when(holderRepository.findByCardAndUser(CARD_ID, PARTNER)).thenReturn(Optional.of(partnerRow));

        service.togglePin(CARD_ID, PARTNER);
        service.recordUse(CARD_ID, PARTNER);

        assertThat(partnerRow.isPinnedFlag()).isTrue();
        assertThat(partnerRow.getUseCountValue()).isEqualTo(1);
        assertThat(partnerRow.getLastUsedAt()).isNotNull();
        assertThat(card.getHolders().get(0).isPinnedFlag()).isFalse();
    }

    @Test
    @DisplayName("list order: pinned first, then most recently used, then never-used by name")
    void ordering() {
        LoyaltyCard card = card();
        LoyaltyCardHolder zeller = named(card, "Zeller", false, null);
        LoyaltyCardHolder aldo = named(card, "aldo", false, null);
        LoyaltyCardHolder recent = named(card, "Recent", false, LocalDateTime.now());
        LoyaltyCardHolder older = named(card, "Older", false, LocalDateTime.now().minusDays(3));
        LoyaltyCardHolder pinned = named(card, "Pinned", true, null);

        List<LoyaltyCardHolder> sorted = new ArrayList<>(List.of(zeller, aldo, older, recent, pinned));
        sorted.sort(LoyaltyCardService.DISPLAY_ORDER);

        assertThat(sorted).containsExactly(pinned, recent, older, aldo, zeller);
    }

    @Test
    @DisplayName("pinned cards for the dashboard: only the caller's pinned rows, in display order")
    void pinnedForUser() {
        LoyaltyCard card = card();
        LoyaltyCardHolder recent = named(card, "Recent", true, LocalDateTime.now());
        LoyaltyCardHolder older = named(card, "Older", true, LocalDateTime.now().minusDays(1));
        LoyaltyCardHolder unpinned = named(card, "Unpinned", false, LocalDateTime.now());
        when(holderRepository.findByUser(OWNER)).thenReturn(List.of(older, unpinned, recent));

        assertThat(service.findPinnedForUser(OWNER)).containsExactly(recent, older);
    }

    @Test
    @DisplayName("deleting a user removes the cards they own and their holder rows elsewhere")
    void deleteAllForUser() {
        LoyaltyCard owned = card();
        when(cardRepository.findByOwner(OWNER)).thenReturn(List.of(owned));

        service.deleteAllForUser(OWNER);

        verify(logoRepository).deleteById(CARD_ID);
        verify(cardRepository).delete(owned);
        verify(holderRepository).deleteByUser(OWNER);
    }

    // ---------- fixtures ----------

    private static LoyaltyCardForm form(String name, String number, BarcodeFormat format) {
        LoyaltyCardForm f = new LoyaltyCardForm();
        f.setMerchantName(name);
        f.setCardNumber(number);
        f.setBarcodeFormat(format);
        return f;
    }

    private static User user(Long id) {
        return User.builder().id(id).name("User " + id).email("u" + id + "@example.com").passwordHash("x").build();
    }

    private static LoyaltyCard card() {
        LoyaltyCard card = LoyaltyCard.builder()
                .id(CARD_ID).owner(user(OWNER)).merchantName("Metro").cardNumber("ABC")
                .barcodeFormat(BarcodeFormat.CODE128).build();
        card.getHolders().add(holder(card, OWNER));
        return card;
    }

    private static LoyaltyCardHolder holder(LoyaltyCard card, Long userId) {
        return LoyaltyCardHolder.builder().card(card).user(user(userId)).build();
    }

    private static LoyaltyCardHolder named(LoyaltyCard template, String name, boolean pinned, LocalDateTime lastUsed) {
        LoyaltyCard c = LoyaltyCard.builder().id(template.getId()).owner(template.getOwner()).merchantName(name)
                .cardNumber("1").build();
        return LoyaltyCardHolder.builder().card(c).user(user(OWNER)).pinned(pinned).lastUsedAt(lastUsed).build();
    }
}
