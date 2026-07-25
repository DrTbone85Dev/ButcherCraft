package com.butchercraft.world.transaction;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.binding.AuthoritativeTransactionResultEvidence;
import com.butchercraft.world.transaction.binding.ResultingInventoryFreshnessEvidence;
import com.butchercraft.world.transaction.binding.TransactionProposalIdentity;
import com.butchercraft.world.transaction.binding.TransactionTerminalResult;
import com.butchercraft.world.transaction.binding.TransactionValidationBinding;
import com.butchercraft.world.transaction.binding.TransactionValidationPlan;
import com.butchercraft.world.transaction.binding.ValidationInputIdentity;
import com.butchercraft.world.transaction.binding.ValidationPlanPrecondition;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class TransactionBindingFactory {
    private TransactionBindingFactory() {
    }

    static TransactionProposalIdentity proposalIdentity(EconomicTransaction transaction) {
        return TransactionProposalIdentity.from(transaction);
    }

    static InventoryFreshnessIdentity inventoryFreshnessIdentity(
            InventoryManager inventoryManager,
            EconomicTransaction transaction,
            List<InventoryChange> changes
    ) {
        Objects.requireNonNull(inventoryManager, "inventoryManager");
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(changes, "changes");
        return inventoryManager.freshnessIdentityForValidation(
                changes,
                referencedGoodIds(transaction, changes),
                referencedActorIds(transaction, changes)
        );
    }

    static TransactionValidationPlan validationPlan(
            TransactionProposalIdentity proposalIdentity,
            InventoryFreshnessIdentity inventoryFreshnessIdentity,
            List<InventoryChange> changes
    ) {
        Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        Objects.requireNonNull(inventoryFreshnessIdentity, "inventoryFreshnessIdentity");
        return TransactionValidationPlan.fromInventoryChanges(
                changes,
                List.of(
                        ValidationPlanPrecondition.of(
                                scopeId("transaction_proposal", proposalIdentity.transactionId().value()),
                                proposalIdentity.proposalDigest()
                        ),
                        ValidationPlanPrecondition.of(
                                "butchercraft:inventory_freshness/live_validation",
                                inventoryFreshnessIdentity.identityDigest()
                        )
                )
        );
    }

    static TransactionValidationBinding validationBinding(
            EconomicTransaction transaction,
            TransactionProposalIdentity proposalIdentity,
            InventoryFreshnessIdentity inventoryFreshnessIdentity,
            TransactionValidationPlan validationPlan
    ) {
        return TransactionValidationBinding.of(
                transaction.id(),
                proposalIdentity,
                inventoryFreshnessIdentity,
                validationPlan.identity(),
                List.of()
        );
    }

    static AuthoritativeTransactionResultEvidence resultEvidence(
            TransactionValidationBinding binding,
            TransactionTerminalResult terminalResult,
            InventoryFreshnessIdentity resultingFreshnessIdentity
    ) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(resultingFreshnessIdentity, "resultingFreshnessIdentity");
        return AuthoritativeTransactionResultEvidence.forBinding(
                binding,
                terminalResult,
                List.of(new ResultingInventoryFreshnessEvidence(
                        scopeId("transaction_result_inventory", binding.transactionId().value()),
                        resultingFreshnessIdentity
                ))
        );
    }

    static List<ValidationInputIdentity> validationInputIdentities() {
        return List.of();
    }

    static String authorityScopeIdentity(TransactionId transactionId) {
        return scopeId("transaction_manager/live_validation", transactionId.value());
    }

    private static Set<GoodId> referencedGoodIds(
            EconomicTransaction transaction,
            List<InventoryChange> changes
    ) {
        Set<GoodId> ids = new HashSet<>();
        ids.add(transaction.goodId());
        changes.stream().map(change -> change.entry().goodId()).forEach(ids::add);
        return ids;
    }

    private static Set<ActorId> referencedActorIds(
            EconomicTransaction transaction,
            List<InventoryChange> changes
    ) {
        Set<ActorId> ids = new HashSet<>();
        transaction.sourceActorId().ifPresent(ids::add);
        transaction.destinationActorId().ifPresent(ids::add);
        changes.stream()
                .map(change -> change.entry().metadata().originActorId())
                .flatMap(java.util.Optional::stream)
                .forEach(ids::add);
        return ids;
    }

    private static String scopeId(String kind, String value) {
        return "butchercraft:" + kind + "/" + Objects.requireNonNull(value, "value").replace(':', '/');
    }
}
