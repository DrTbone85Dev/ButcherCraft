package com.butchercraft.world.transaction;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodDefinition;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryChangeCode;
import com.butchercraft.world.inventory.InventoryChangeValidation;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;

import java.util.List;
import java.util.Objects;

public final class TransactionValidator {
    private final InventoryManager inventoryManager;

    public TransactionValidator(InventoryManager inventoryManager) {
        this.inventoryManager = Objects.requireNonNull(inventoryManager, "inventoryManager");
    }

    public TransactionValidation validateReferences(EconomicTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.quantity() <= 0L) {
            return rejected(
                    transaction,
                    TransactionFailureCode.NEGATIVE_QUANTITY,
                    "Transaction quantity must be positive"
            );
        }

        GoodDefinition good = inventoryManager.registry().goodRegistry().find(transaction.goodId()).orElse(null);
        if (good == null) {
            return rejected(transaction, TransactionFailureCode.UNKNOWN_GOOD, "Transaction references an unknown Good");
        }
        if (good.unitOfMeasure() != transaction.unitOfMeasure()) {
            return rejected(
                    transaction,
                    TransactionFailureCode.VALIDATION_FAILED,
                    "Transaction unit does not match the Good definition"
            );
        }

        TransactionValidation actorValidation = validateActors(transaction);
        if (!actorValidation.accepted()) {
            return actorValidation;
        }
        TransactionValidation inventoryValidation = validateInventories(transaction);
        if (!inventoryValidation.accepted()) {
            return inventoryValidation;
        }
        TransactionValidation changePlanValidation = validateChangePlan(transaction);
        if (!changePlanValidation.accepted()) {
            return changePlanValidation;
        }
        return validateEndpointShape(transaction);
    }

    public TransactionValidation validateForSubmission(EconomicTransaction transaction) {
        return validate(transaction, TransactionStatus.PENDING);
    }

    public TransactionValidation validateForExecution(EconomicTransaction transaction) {
        return validate(transaction, TransactionStatus.VALIDATED);
    }

    private TransactionValidation validate(EconomicTransaction transaction, TransactionStatus requiredStatus) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.status() != requiredStatus) {
            return rejected(
                    transaction,
                    TransactionFailureCode.INVALID_STATUS,
                    "Transaction status must be " + requiredStatus.serializedName()
            );
        }
        TransactionValidation references = validateReferences(transaction);
        if (!references.accepted()) {
            return references;
        }

        List<InventoryChange> changes = inventoryChanges(transaction);
        if (changes.isEmpty()) {
            return rejected(
                    transaction,
                    TransactionFailureCode.VALIDATION_FAILED,
                    "Transaction type is reserved for a future execution system: "
                            + transaction.type().serializedName()
            );
        }
        InventoryChangeValidation inventoryValidation = inventoryManager.validateChanges(
                changes,
                transaction.simulationTick()
        );
        if (!inventoryValidation.isAllowed()) {
            return rejected(
                    transaction,
                    mapFailureCode(inventoryValidation.code()),
                    inventoryValidation.message()
            );
        }
        return TransactionValidation.accepted(transaction.id(), changes);
    }

    private TransactionValidation validateActors(EconomicTransaction transaction) {
        ActorId source = transaction.sourceActorId().orElse(null);
        if (source != null && !inventoryManager.registry().actorRegistry().contains(source)) {
            return rejected(
                    transaction,
                    TransactionFailureCode.UNKNOWN_ACTOR,
                    "Transaction references an unknown actor: " + source.value()
            );
        }
        ActorId destination = transaction.destinationActorId().orElse(null);
        if (destination != null && !inventoryManager.registry().actorRegistry().contains(destination)) {
            return rejected(
                    transaction,
                    TransactionFailureCode.UNKNOWN_ACTOR,
                    "Transaction references an unknown actor: " + destination.value()
            );
        }
        return TransactionValidation.accepted(transaction.id(), List.of());
    }

    private TransactionValidation validateInventories(EconomicTransaction transaction) {
        TransactionValidation source = validateInventoryOwner(
                transaction,
                transaction.sourceInventoryId().orElse(null),
                transaction.sourceActorId().orElse(null),
                "source"
        );
        if (!source.accepted()) {
            return source;
        }
        return validateInventoryOwner(
                transaction,
                transaction.destinationInventoryId().orElse(null),
                transaction.destinationActorId().orElse(null),
                "destination"
        );
    }

    private TransactionValidation validateInventoryOwner(
            EconomicTransaction transaction,
            InventoryId inventoryId,
            ActorId actorId,
            String endpoint
    ) {
        if (inventoryId == null) {
            return TransactionValidation.accepted(transaction.id(), List.of());
        }
        InventoryContainer container = inventoryManager.find(inventoryId).orElse(null);
        if (container == null) {
            return rejected(
                    transaction,
                    TransactionFailureCode.UNKNOWN_INVENTORY,
                    "Transaction references an unknown " + endpoint + " inventory: " + inventoryId.value()
            );
        }
        if (actorId != null && !container.ownerActorId().equals(actorId)) {
            return rejected(
                    transaction,
                    TransactionFailureCode.VALIDATION_FAILED,
                    "Transaction " + endpoint + " actor does not own the " + endpoint + " inventory"
            );
        }
        return TransactionValidation.accepted(transaction.id(), List.of());
    }

    private TransactionValidation validateEndpointShape(EconomicTransaction transaction) {
        boolean hasSource = transaction.sourceInventoryId().isPresent();
        boolean hasDestination = transaction.destinationInventoryId().isPresent();
        return switch (transaction.type()) {
            case INVENTORY_ADD -> requireShape(transaction, !hasSource && hasDestination,
                    "Inventory add requires only a destination inventory");
            case INVENTORY_REMOVE -> requireShape(transaction, hasSource && !hasDestination,
                    "Inventory remove requires only a source inventory");
            case INVENTORY_TRANSFER -> requireShape(
                    transaction,
                    hasSource && hasDestination
                            && !transaction.sourceInventoryId().orElseThrow()
                            .equals(transaction.destinationInventoryId().orElseThrow()),
                    "Inventory transfer requires distinct source and destination inventories"
            );
            case INVENTORY_ADJUSTMENT -> requireShape(transaction, hasSource ^ hasDestination,
                    "Inventory adjustment requires exactly one inventory endpoint");
            default -> TransactionValidation.accepted(transaction.id(), List.of());
        };
    }

    private TransactionValidation validateChangePlan(EconomicTransaction transaction) {
        if (transaction.type() == TransactionType.PRODUCTION && transaction.inventoryChangePlan().isEmpty()) {
            return rejected(
                    transaction,
                    TransactionFailureCode.VALIDATION_FAILED,
                    "Production transactions require an atomic inventory change plan"
            );
        }
        if (transaction.type() != TransactionType.PRODUCTION && !transaction.inventoryChangePlan().isEmpty()) {
            return rejected(
                    transaction,
                    TransactionFailureCode.VALIDATION_FAILED,
                    "Only production transactions may declare an explicit inventory change plan"
            );
        }
        ActorId producer = transaction.sourceActorId().orElse(null);
        for (InventoryChange change : transaction.inventoryChangePlan()) {
            InventoryContainer container = inventoryManager.find(change.inventoryId()).orElse(null);
            if (container == null) {
                return rejected(
                        transaction,
                        TransactionFailureCode.UNKNOWN_INVENTORY,
                        "Production change plan references an unknown inventory: " + change.inventoryId().value()
                );
            }
            if (producer != null && !container.ownerActorId().equals(producer)) {
                return rejected(
                        transaction,
                        TransactionFailureCode.VALIDATION_FAILED,
                        "Production inventory is not owned by the producer actor: " + change.inventoryId().value()
                );
            }
            GoodDefinition definition = inventoryManager.registry().goodRegistry()
                    .find(change.entry().goodId())
                    .orElse(null);
            if (definition == null) {
                return rejected(
                        transaction,
                        TransactionFailureCode.UNKNOWN_GOOD,
                        "Production change plan references an unknown Good: " + change.entry().goodId().value()
                );
            }
            if (definition.unitOfMeasure() != change.entry().unitOfMeasure()) {
                return rejected(
                        transaction,
                        TransactionFailureCode.VALIDATION_FAILED,
                        "Production change plan unit does not match the Good definition"
                );
            }
        }
        return TransactionValidation.accepted(transaction.id(), List.of());
    }

    private List<InventoryChange> inventoryChanges(EconomicTransaction transaction) {
        InventoryEntry entry = new InventoryEntry(
                transaction.goodId(),
                transaction.quantity(),
                transaction.unitOfMeasure()
        );
        return switch (transaction.type()) {
            case INVENTORY_ADD -> List.of(InventoryChange.add(
                    transaction.destinationInventoryId().orElseThrow(),
                    entry
            ));
            case INVENTORY_REMOVE -> List.of(InventoryChange.remove(
                    transaction.sourceInventoryId().orElseThrow(),
                    entry
            ));
            case INVENTORY_TRANSFER -> List.of(
                    InventoryChange.remove(transaction.sourceInventoryId().orElseThrow(), entry),
                    InventoryChange.add(transaction.destinationInventoryId().orElseThrow(), entry)
            );
            case INVENTORY_ADJUSTMENT -> transaction.sourceInventoryId().isPresent()
                    ? List.of(InventoryChange.remove(transaction.sourceInventoryId().orElseThrow(), entry))
                    : List.of(InventoryChange.add(transaction.destinationInventoryId().orElseThrow(), entry));
            case PRODUCTION -> transaction.inventoryChangePlan();
            default -> List.of();
        };
    }

    static TransactionFailureCode mapFailureCode(InventoryChangeCode code) {
        return switch (code) {
            case UNKNOWN_INVENTORY -> TransactionFailureCode.UNKNOWN_INVENTORY;
            case UNKNOWN_GOOD -> TransactionFailureCode.UNKNOWN_GOOD;
            case INVALID_QUANTITY -> TransactionFailureCode.NEGATIVE_QUANTITY;
            case INSUFFICIENT_QUANTITY -> TransactionFailureCode.INSUFFICIENT_INVENTORY;
            case CAPACITY_EXCEEDED -> TransactionFailureCode.CAPACITY_EXCEEDED;
            case INVENTORY_UNAVAILABLE, INVALID_TICK -> TransactionFailureCode.INVALID_STATUS;
            case ALLOWED, EMPTY_CHANGE_SET, INVALID_UNIT, INVALID_METADATA, ARITHMETIC_OVERFLOW ->
                    TransactionFailureCode.VALIDATION_FAILED;
        };
    }

    private static TransactionValidation requireShape(
            EconomicTransaction transaction,
            boolean valid,
            String message
    ) {
        return valid
                ? TransactionValidation.accepted(transaction.id(), List.of())
                : rejected(transaction, TransactionFailureCode.VALIDATION_FAILED, message);
    }

    private static TransactionValidation rejected(
            EconomicTransaction transaction,
            TransactionFailureCode code,
            String message
    ) {
        return TransactionValidation.rejected(transaction.id(), code, message);
    }
}
