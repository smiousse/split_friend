package com.splitfriend.service;

import com.splitfriend.dto.BudgetForm;
import com.splitfriend.dto.BudgetItemForm;
import com.splitfriend.model.Budget;
import com.splitfriend.model.BudgetItem;
import com.splitfriend.model.User;
import com.splitfriend.repository.BudgetItemRepository;
import com.splitfriend.repository.BudgetRepository;
import com.splitfriend.repository.UserRepository;
import com.splitfriend.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Budget CRUD, with access control built into the lookups.
 *
 * There is deliberately no unscoped "find by id" on this class's public
 * surface: every read goes through {@link #requireAccess}, so a new handler
 * cannot forget the check. The rest of the app hand-writes that guard in each
 * controller method, which works only for as long as nobody adds an endpoint
 * and forgets - and the thing leaking here would be a household's finances.
 */
@Service
@Transactional
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    private final BudgetRepository budgetRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final UserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         BudgetItemRepository budgetItemRepository,
                         UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.budgetItemRepository = budgetItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<Budget> findForUser(Long userId) {
        return budgetRepository.findByParticipant(userId);
    }

    /**
     * Loads a budget the user is allowed to <em>read</em>, with its items.
     *
     * Admins may read any budget so they can support the feature; for everyone
     * else a budget they do not participate in is indistinguishable from one
     * that does not exist. An admin reading a household they are not part of
     * is logged - it is the only way this feature's figures can reach someone
     * outside the couple, so it is the one event worth having a record of.
     *
     * Read access only. Mutations go through {@link #requireWriteAccess}.
     *
     * @throws AccessDeniedException if the budget exists but is not theirs
     */
    @Transactional(readOnly = true)
    public Budget requireAccess(Long budgetId, Long userId) {
        Budget found = SecurityUtils.isCurrentUserAdmin()
                ? loadAsAdmin(budgetId, userId)
                : budgetRepository.findByIdForParticipant(budgetId, userId)
                        .orElseThrow(() -> notAccessible(budgetId));

        // Touch the collection inside the transaction so the view can render it.
        found.getItems().size();
        return found;
    }

    /**
     * Loads a budget the user may <em>change</em>.
     *
     * Unlike {@link #requireAccess} this grants admins nothing: an
     * administrator has no business rewriting or deleting another household's
     * figures, and letting the read bypass double as a write bypass would make
     * that possible without a trace.
     */
    @Transactional(readOnly = true)
    public Budget requireWriteAccess(Long budgetId, Long userId) {
        Budget found = budgetRepository.findByIdForParticipant(budgetId, userId)
                .orElseThrow(() -> notAccessible(budgetId));
        found.getItems().size();
        return found;
    }

    private Budget loadAsAdmin(Long budgetId, Long userId) {
        Budget found = budgetRepository.findByIdWithParticipants(budgetId)
                .orElseThrow(() -> notAccessible(budgetId));

        if (!found.hasParticipant(userId)) {
            log.warn("Admin user {} read budget {} they do not participate in", userId, budgetId);
        }
        return found;
    }

    private AccessDeniedException notAccessible(Long budgetId) {
        return new AccessDeniedException("Budget not found or not accessible: " + budgetId);
    }

    public Budget create(BudgetForm form, User creator) {
        User partner = userRepository.findById(form.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Selected partner does not exist"));

        if (partner.getId().equals(creator.getId())) {
            throw new IllegalArgumentException("A budget needs two different people");
        }
        if (!Boolean.TRUE.equals(partner.getBudgetEnabled())) {
            throw new IllegalArgumentException("The selected partner does not have the budget feature enabled");
        }

        Budget budget = Budget.builder()
                .name(form.getName())
                .description(form.getDescription())
                .currency(form.getCurrency() != null ? form.getCurrency() : "CAD")
                .userA(creator)
                .userB(partner)
                .shareAPercent(requireValidShare(form.getShareAPercent()))
                .settlementPeriod(form.getSettlementPeriod())
                .createdBy(creator)
                .build();

        return budgetRepository.save(budget);
    }

    public Budget update(Long budgetId, BudgetForm form, Long userId) {
        Budget budget = requireWriteAccess(budgetId, userId);
        budget.setName(form.getName());
        budget.setDescription(form.getDescription());
        budget.setCurrency(form.getCurrency());
        budget.setShareAPercent(requireValidShare(form.getShareAPercent()));
        budget.setSettlementPeriod(form.getSettlementPeriod());
        return budgetRepository.save(budget);
    }

    public void delete(Long budgetId, Long userId) {
        Budget budget = requireWriteAccess(budgetId, userId);
        budgetRepository.delete(budget);
    }

    // ---------- items ----------

    public BudgetItem addItem(Long budgetId, BudgetItemForm form, Long userId) {
        Budget budget = requireWriteAccess(budgetId, userId);

        BudgetItem item = BudgetItem.builder()
                .budget(budget)
                .label(form.getLabel())
                .amount(form.getAmount())
                .frequency(form.getFrequency())
                .paidBySide(form.getPaidBySide())
                .itemType(form.getItemType())
                .sortOrder(budgetItemRepository.findMaxSortOrder(budgetId) + 1)
                .active(form.isActive())
                .notes(form.getNotes())
                .build();

        return budgetItemRepository.save(item);
    }

    public BudgetItem updateItem(Long budgetId, Long itemId, BudgetItemForm form, Long userId) {
        BudgetItem item = requireItem(budgetId, itemId, userId);
        item.setLabel(form.getLabel());
        item.setAmount(form.getAmount());
        item.setFrequency(form.getFrequency());
        item.setPaidBySide(form.getPaidBySide());
        item.setItemType(form.getItemType());
        item.setActive(form.isActive());
        item.setNotes(form.getNotes());
        return budgetItemRepository.save(item);
    }

    public void deleteItem(Long budgetId, Long itemId, Long userId) {
        budgetItemRepository.delete(requireItem(budgetId, itemId, userId));
    }

    public void toggleItem(Long budgetId, Long itemId, Long userId) {
        BudgetItem item = requireItem(budgetId, itemId, userId);
        item.setActive(!item.isActive());
        budgetItemRepository.save(item);
    }

    /**
     * Loads an item, checking both that the caller may see the budget and that
     * the item actually belongs to it - so an item id from another household
     * cannot be edited by pairing it with a budget id the caller does own.
     */
    private BudgetItem requireItem(Long budgetId, Long itemId, Long userId) {
        requireWriteAccess(budgetId, userId);
        BudgetItem item = budgetItemRepository.findById(itemId)
                .orElseThrow(() -> new AccessDeniedException("Budget item not found: " + itemId));

        if (item.getBudget() == null || !budgetId.equals(item.getBudget().getId())) {
            throw new AccessDeniedException("Budget item " + itemId + " does not belong to budget " + budgetId);
        }
        return item;
    }

    private BigDecimal requireValidShare(BigDecimal shareAPercent) {
        if (shareAPercent == null
                || shareAPercent.compareTo(BigDecimal.ZERO) < 0
                || shareAPercent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("Share must be between 0 and 100");
        }
        return shareAPercent;
    }
}
