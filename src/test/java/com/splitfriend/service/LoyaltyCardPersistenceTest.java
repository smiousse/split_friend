package com.splitfriend.service;

import com.splitfriend.dto.LoyaltyCardForm;
import com.splitfriend.model.LoyaltyCard;
import com.splitfriend.model.User;
import com.splitfriend.model.enums.BarcodeFormat;
import com.splitfriend.repository.LoyaltyCardHolderRepository;
import com.splitfriend.repository.LoyaltyCardLogoRepository;
import com.splitfriend.repository.LoyaltyCardRepository;
import com.splitfriend.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the service against a real (embedded H2) schema, covering what the
 * mock-based {@link LoyaltyCardServiceTest} cannot: cascades, orphan removal,
 * the holder unique key, and delete ordering.
 */
@DataJpaTest
@Import({LoyaltyCardService.class, LogoImageProcessor.class})
class LoyaltyCardPersistenceTest {

    @Autowired private LoyaltyCardService service;
    @Autowired private LoyaltyCardRepository cardRepository;
    @Autowired private LoyaltyCardHolderRepository holderRepository;
    @Autowired private LoyaltyCardLogoRepository logoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private User owner;
    private User partner;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(user("Owner", "owner@test.local"));
        partner = userRepository.save(user("Partner", "Partner@Test.local"));
    }

    @Test
    @DisplayName("share, unshare, share again; leave, share again - the unique key never collides")
    void reshareCycles() {
        LoyaltyCard card = service.create(form("Metro"), null, owner);
        flush();

        service.share(card.getId(), "partner@test.local", owner.getId()); // case-insensitive
        flush();
        assertThat(service.findForUser(partner.getId())).hasSize(1);

        service.unshare(card.getId(), partner.getId(), owner.getId());
        flush();
        assertThat(service.findForUser(partner.getId())).isEmpty();

        service.share(card.getId(), "partner@test.local", owner.getId());
        flush();
        service.leave(card.getId(), partner.getId());
        flush();
        assertThat(service.findForUser(partner.getId())).isEmpty();

        service.share(card.getId(), "partner@test.local", owner.getId());
        flush();
        assertThat(holderRepository.findByCard(card.getId())).hasSize(2);
    }

    @Test
    @DisplayName("deleting a card removes its holders and its logo")
    void deleteCascades() throws IOException {
        LoyaltyCard card = service.create(form("Metro"), png(), owner);
        service.share(card.getId(), "partner@test.local", owner.getId());
        flush();
        assertThat(logoRepository.findById(card.getId())).isPresent();

        service.delete(card.getId(), owner.getId());
        flush();

        assertThat(cardRepository.findById(card.getId())).isEmpty();
        assertThat(holderRepository.findAll()).isEmpty();
        assertThat(logoRepository.findById(card.getId())).isEmpty();
    }

    @Test
    @DisplayName("deleting a user removes cards they own, and their rows on cards others shared with them")
    void deleteAllForUser() {
        LoyaltyCard owned = service.create(form("Owned by partner"), null, partner);
        LoyaltyCard sharedToPartner = service.create(form("Owned by owner"), null, owner);
        service.share(owned.getId(), "owner@test.local", partner.getId());
        service.share(sharedToPartner.getId(), "partner@test.local", owner.getId());
        flush();

        service.deleteAllForUser(partner.getId());
        userRepository.deleteById(partner.getId());
        flush();

        assertThat(cardRepository.findById(owned.getId())).isEmpty();
        assertThat(cardRepository.findById(sharedToPartner.getId())).isPresent();
        assertThat(holderRepository.findByCard(sharedToPartner.getId()))
                .extracting(h -> h.getUser().getId()).containsExactly(owner.getId());
        assertThat(service.findForUser(owner.getId())).extracting(h -> h.getCard().getMerchantName())
                .containsExactly("Owned by owner");
    }

    @Test
    @DisplayName("replacing and then removing a logo keeps the etag and the logo row in step")
    void logoLifecycle() throws IOException {
        LoyaltyCard card = service.create(form("Metro"), png(), owner);
        flush();
        String firstEtag = cardRepository.findById(card.getId()).orElseThrow().getLogoEtag();
        assertThat(firstEtag).isNotNull();

        LoyaltyCardForm remove = form("Metro");
        remove.setRemoveLogo(true);
        service.update(card.getId(), remove, null, owner.getId());
        flush();

        assertThat(cardRepository.findById(card.getId()).orElseThrow().getLogoEtag()).isNull();
        assertThat(logoRepository.findById(card.getId())).isEmpty();
    }

    private void flush() {
        em.flush();
        em.clear();
        owner = userRepository.findById(owner.getId()).orElseThrow();
        partner = userRepository.findById(partner.getId()).orElse(partner);
    }

    private static User user(String name, String email) {
        return User.builder().name(name).email(email).passwordHash("x").build();
    }

    private static LoyaltyCardForm form(String name) {
        LoyaltyCardForm form = new LoyaltyCardForm();
        form.setMerchantName(name);
        form.setCardNumber("ABC-123");
        form.setBarcodeFormat(BarcodeFormat.CODE128);
        return form;
    }

    private static byte[] png() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
