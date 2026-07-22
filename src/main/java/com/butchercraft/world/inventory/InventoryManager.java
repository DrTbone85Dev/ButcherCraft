package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodDefinition;
import com.butchercraft.world.goods.GoodId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InventoryManager {
    private InventoryRegistry registry;
    private final Map<InventoryId, InventoryRuntime> runtimes = new LinkedHashMap<>();

    public InventoryManager(InventoryRegistry registry) {
        this(registry, List.of());
    }

    public InventoryManager(InventoryRegistry registry, Collection<InventoryRuntime> loadedRuntimes) {
        this.registry = Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(loadedRuntimes, "loadedRuntimes");
        for (InventoryRuntime runtime : loadedRuntimes) {
            Objects.requireNonNull(runtime, "runtime");
            InventoryRuntime previous = runtimes.putIfAbsent(runtime.inventoryId(), runtime);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate inventory runtime: " + runtime.inventoryId().value());
            }
            if (!registry.contains(runtime.inventoryId())) {
                throw new IllegalArgumentException("Inventory runtime references unknown inventory: "
                        + runtime.inventoryId().value());
            }
        }
        for (InventoryContainer container : registry.containers()) {
            runtimes.putIfAbsent(container.id(), InventoryRuntime.empty(container.id()));
        }
        validate();
    }

    public synchronized InventoryContainer registerContainer(InventoryContainer container) {
        registry = registry.withContainer(container);
        runtimes.put(container.id(), InventoryRuntime.empty(container.id()));
        return container;
    }

    public synchronized StorageNode registerStorageNode(StorageNode storageNode) {
        registry = registry.withStorageNode(storageNode);
        return storageNode;
    }

    public synchronized Optional<InventoryContainer> find(InventoryId inventoryId) {
        return registry.find(inventoryId);
    }

    public synchronized Optional<StorageNode> findStorageNode(StorageNodeId storageNodeId) {
        return registry.findStorageNode(storageNodeId);
    }

    public synchronized Optional<InventoryRuntime> runtimeFor(InventoryId inventoryId) {
        return Optional.ofNullable(runtimes.get(Objects.requireNonNull(inventoryId, "inventoryId")));
    }

    public synchronized InventoryRuntime requireRuntime(InventoryId inventoryId) {
        return runtimeFor(inventoryId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown inventory runtime: " + inventoryId.value()
        ));
    }

    public synchronized List<InventoryContainer> inventoriesOwnedBy(ActorId actorId) {
        return registry.findByOwner(actorId);
    }

    public synchronized List<InventoryContainer> inventoriesAt(
            StorageNodeId storageNodeId,
            boolean includeDescendants
    ) {
        Objects.requireNonNull(storageNodeId, "storageNodeId");
        if (!includeDescendants) {
            return registry.findByStorageNode(storageNodeId);
        }
        Set<StorageNodeId> nodeIds = new HashSet<>();
        nodeIds.add(storageNodeId);
        registry.descendantsOf(storageNodeId).stream().map(StorageNode::id).forEach(nodeIds::add);
        return registry.containers().stream()
                .filter(container -> nodeIds.contains(container.storageNodeId()))
                .toList();
    }

    public synchronized long quantityIn(InventoryId inventoryId, GoodId goodId) {
        return requireRuntime(inventoryId).quantityOf(goodId);
    }

    public synchronized long quantityOwnedBy(ActorId actorId, GoodId goodId) {
        long total = 0L;
        for (InventoryContainer container : inventoriesOwnedBy(actorId)) {
            total = Math.addExact(total, requireRuntime(container.id()).quantityOf(goodId));
        }
        return total;
    }

    public synchronized long quantityAt(
            StorageNodeId storageNodeId,
            GoodId goodId,
            boolean includeDescendants
    ) {
        long total = 0L;
        for (InventoryContainer container : inventoriesAt(storageNodeId, includeDescendants)) {
            total = Math.addExact(total, requireRuntime(container.id()).quantityOf(goodId));
        }
        return total;
    }

    public synchronized void addEntry(InventoryId inventoryId, InventoryEntry entry, long simulationTick) {
        InventoryRuntime runtime = requireRuntime(inventoryId);
        if (!runtime.status().canReceive()) {
            throw new IllegalArgumentException("Inventory cannot receive Goods while "
                    + runtime.status().serializedName() + ": " + inventoryId.value());
        }
        validateEntry(entry, inventoryId);
        List<InventoryEntry> candidate = InventoryRuntime.entriesAfterAdding(runtime.entries(), entry);
        validateWithOverride(inventoryId, candidate);
        runtime.replaceEntries(candidate, simulationTick);
    }

    public synchronized void removeEntry(InventoryId inventoryId, InventoryEntry entry, long simulationTick) {
        InventoryRuntime runtime = requireRuntime(inventoryId);
        if (!runtime.status().canRelease()) {
            throw new IllegalArgumentException("Inventory cannot release Goods while "
                    + runtime.status().serializedName() + ": " + inventoryId.value());
        }
        validateEntry(entry, inventoryId);
        List<InventoryEntry> candidate = InventoryRuntime.entriesAfterRemoving(runtime.entries(), entry);
        runtime.replaceEntries(candidate, simulationTick);
    }

    public synchronized InventoryMovementValidation validateMovement(
            InventoryId sourceInventoryId,
            InventoryId targetInventoryId,
            InventoryEntry movement
    ) {
        Objects.requireNonNull(sourceInventoryId, "sourceInventoryId");
        Objects.requireNonNull(targetInventoryId, "targetInventoryId");
        Objects.requireNonNull(movement, "movement");
        InventoryRuntime source = runtimes.get(sourceInventoryId);
        if (source == null) {
            return rejected(InventoryMovementCode.UNKNOWN_SOURCE, "Unknown source inventory");
        }
        InventoryRuntime target = runtimes.get(targetInventoryId);
        if (target == null) {
            return rejected(InventoryMovementCode.UNKNOWN_TARGET, "Unknown target inventory");
        }
        if (sourceInventoryId.equals(targetInventoryId)) {
            return rejected(InventoryMovementCode.SAME_INVENTORY, "Source and target inventory are the same");
        }
        if (movement.quantity() <= 0L) {
            return rejected(InventoryMovementCode.INVALID_QUANTITY, "Movement quantity must be positive");
        }
        if (!source.status().canRelease()) {
            return rejected(InventoryMovementCode.SOURCE_UNAVAILABLE, "Source inventory cannot release Goods");
        }
        if (!target.status().canReceive()) {
            return rejected(InventoryMovementCode.TARGET_UNAVAILABLE, "Target inventory cannot receive Goods");
        }
        GoodDefinition good = registry.goodRegistry().find(movement.goodId()).orElse(null);
        if (good == null) {
            return rejected(InventoryMovementCode.UNKNOWN_GOOD, "Movement references an unknown Good");
        }
        if (good.unitOfMeasure() != movement.unitOfMeasure()) {
            return rejected(InventoryMovementCode.INVALID_UNIT, "Movement unit does not match the Good definition");
        }
        if (movement.metadata().originActorId().isPresent()
                && !registry.actorRegistry().contains(movement.metadata().originActorId().orElseThrow())) {
            return rejected(InventoryMovementCode.INVALID_METADATA, "Movement origin actor is unknown");
        }
        long available = source.entries().stream()
                .filter(entry -> entry.sameStoredGood(movement))
                .findFirst()
                .map(InventoryEntry::quantity)
                .orElse(0L);
        if (available < movement.quantity()) {
            return rejected(InventoryMovementCode.INSUFFICIENT_QUANTITY, "Source inventory lacks the requested quantity");
        }
        try {
            List<InventoryEntry> sourceCandidate = InventoryRuntime.entriesAfterRemoving(source.entries(), movement);
            List<InventoryEntry> targetCandidate = InventoryRuntime.entriesAfterAdding(target.entries(), movement);
            validateWithOverrides(Map.of(
                    sourceInventoryId, sourceCandidate,
                    targetInventoryId, targetCandidate
            ));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            String message = exception.getMessage() == null ? "Target inventory capacity validation failed" : exception.getMessage();
            return rejected(InventoryMovementCode.CAPACITY_EXCEEDED, message);
        }
        return InventoryMovementValidation.allowed();
    }

    public synchronized long entryCount() {
        return runtimes.values().stream().mapToLong(runtime -> runtime.entries().size()).sum();
    }

    public synchronized List<InventoryRuntime> runtimes() {
        return runtimes.values().stream()
                .sorted(Comparator.comparing(InventoryRuntime::inventoryId))
                .toList();
    }

    public synchronized InventoryRegistry registry() {
        return registry;
    }

    public synchronized void validate() {
        registry.validate();
        Set<InventoryId> registryIds = registry.stream()
                .map(InventoryContainer::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!runtimes.keySet().equals(registryIds)) {
            throw new IllegalArgumentException("Inventory runtime set does not match inventory registry");
        }
        validateAllEntries(Map.of());
        validateAllCapacities(Map.of());
    }

    private void validateWithOverride(InventoryId inventoryId, List<InventoryEntry> entries) {
        validateWithOverrides(Map.of(inventoryId, entries));
    }

    private void validateWithOverrides(Map<InventoryId, List<InventoryEntry>> overrides) {
        validateAllEntries(overrides);
        validateAllCapacities(overrides);
    }

    private void validateAllEntries(Map<InventoryId, List<InventoryEntry>> overrides) {
        for (InventoryContainer container : registry.containers()) {
            for (InventoryEntry entry : entriesFor(container.id(), overrides)) {
                validateEntry(entry, container.id());
            }
        }
    }

    private void validateEntry(InventoryEntry entry, InventoryId inventoryId) {
        Objects.requireNonNull(entry, "entry");
        GoodDefinition definition = registry.goodRegistry().find(entry.goodId())
                .orElseThrow(() -> new IllegalArgumentException("Inventory entry references unknown Good: "
                        + inventoryId.value() + "/" + entry.goodId().value()));
        if (definition.unitOfMeasure() != entry.unitOfMeasure()) {
            throw new IllegalArgumentException("Inventory entry unit does not match Good definition: "
                    + inventoryId.value() + "/" + entry.goodId().value());
        }
        entry.metadata().originActorId().ifPresent(actorId -> {
            if (!registry.actorRegistry().contains(actorId)) {
                throw new IllegalArgumentException("Inventory entry references unknown origin actor: "
                        + inventoryId.value() + "/" + actorId.value());
            }
        });
    }

    private void validateAllCapacities(Map<InventoryId, List<InventoryEntry>> overrides) {
        Map<StorageNodeId, List<InventoryEntry>> entriesByStorageHierarchy = new HashMap<>();
        for (InventoryContainer container : registry.containers()) {
            List<InventoryEntry> entries = entriesFor(container.id(), overrides);
            container.capacity().validateEntries(entries, "inventory " + container.id().value());
            for (StorageNodeId storageNodeId : registry.ancestorsInclusive(container.storageNodeId())) {
                entriesByStorageHierarchy.computeIfAbsent(storageNodeId, ignored -> new ArrayList<>()).addAll(entries);
            }
        }
        for (StorageNode storageNode : registry.storageNodes()) {
            storageNode.capacity().validateEntries(
                    entriesByStorageHierarchy.getOrDefault(storageNode.id(), List.of()),
                    "storage node " + storageNode.id().value()
            );
        }
    }

    private List<InventoryEntry> entriesFor(
            InventoryId inventoryId,
            Map<InventoryId, List<InventoryEntry>> overrides
    ) {
        List<InventoryEntry> override = overrides.get(inventoryId);
        return override != null ? override : requireRuntime(inventoryId).entries();
    }

    private static InventoryMovementValidation rejected(InventoryMovementCode code, String message) {
        return InventoryMovementValidation.rejected(code, message);
    }
}
