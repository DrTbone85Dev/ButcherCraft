package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntryMetadata;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionMetadata;

import java.util.Objects;
import java.util.Optional;

public record TransactionProposalIdentity(
        int schemaVersion,
        TransactionId transactionId,
        String proposalDigest
) {
    public TransactionProposalIdentity {
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        proposalDigest = TransactionBindingValidation.digest(proposalDigest, "proposalDigest");
    }

    public static TransactionProposalIdentity from(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        return new TransactionProposalIdentity(
                TransactionBindingSchema.CURRENT_VERSION,
                transaction.id(),
                calculateDigest(transaction)
        );
    }

    public static String calculateDigest(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransactionBindingCanonicalDigest digest = TransactionBindingCanonicalDigest.create(
                "butchercraft:transaction_proposal_identity"
        );
        digest.add(TransactionBindingSchema.CURRENT_VERSION)
                .add(transaction.schemaVersion())
                .add(transaction.id().value())
                .add(transaction.simulationTick())
                .add(transaction.type().serializedName());
        addOptional(digest, transaction.sourceActorId().map(actor -> actor.value()));
        addOptional(digest, transaction.destinationActorId().map(actor -> actor.value()));
        addOptional(digest, transaction.sourceInventoryId().map(inventory -> inventory.value()));
        addOptional(digest, transaction.destinationInventoryId().map(inventory -> inventory.value()));
        digest.add(transaction.goodId().value())
                .add(transaction.quantity())
                .add(transaction.unitOfMeasure().serializedName());
        addMetadata(digest, transaction.metadata());
        digest.add(transaction.inventoryChangePlan().size());
        for (InventoryChange change : transaction.inventoryChangePlan()) {
            addInventoryChange(digest, change);
        }
        return digest.finish();
    }

    private static void addMetadata(TransactionBindingCanonicalDigest digest, TransactionMetadata metadata) {
        addOptional(digest, metadata.reason());
        addOptional(digest, metadata.referenceId());
        addOptional(digest, metadata.user());
        addOptional(digest, metadata.externalSystem());
        addOptional(digest, metadata.comments());
    }

    static void addInventoryChange(TransactionBindingCanonicalDigest digest, InventoryChange change) {
        digest.add(change.inventoryId().value())
                .add(change.type().name())
                .add(change.entry().goodId().value())
                .add(change.entry().quantity())
                .add(change.entry().unitOfMeasure().serializedName());
        addInventoryMetadata(digest, change.entry().metadata());
    }

    static void addInventoryMetadata(TransactionBindingCanonicalDigest digest, InventoryEntryMetadata metadata) {
        addOptional(digest, metadata.lotNumber());
        digest.add(metadata.expirationSimulationTick().isPresent());
        metadata.expirationSimulationTick().ifPresent(digest::add);
        digest.add(metadata.qualityBasisPoints().isPresent());
        metadata.qualityBasisPoints().ifPresent(digest::add);
        addOptional(digest, metadata.originActorId().map(actor -> actor.value()));
    }

    static void addOptional(TransactionBindingCanonicalDigest digest, Optional<String> value) {
        digest.add(value.isPresent());
        value.ifPresent(digest::add);
    }
}
