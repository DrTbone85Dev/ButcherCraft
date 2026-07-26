package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.ActorRelationship;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.goods.CommodityDefinition;
import com.butchercraft.world.goods.GoodDefinition;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.ItemMappingMetadata;
import com.butchercraft.world.goods.ProductDefinition;
import com.butchercraft.world.transaction.TransactionExecutionAuthority;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessComponent;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
            InventoryRuntime previous = runtimes.putIfAbsent(runtime.inventoryId(), runtime.snapshot());
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
        return Optional.ofNullable(runtimes.get(Objects.requireNonNull(inventoryId, "inventoryId")))
                .map(InventoryRuntime::snapshot);
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

    public synchronized InventoryFreshnessIdentity freshnessIdentityForValidation(
            Collection<InventoryChange> changes,
            Collection<GoodId> referencedGoodIds,
            Collection<ActorId> referencedActorIds
    ) {
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(referencedGoodIds, "referencedGoodIds");
        Objects.requireNonNull(referencedActorIds, "referencedActorIds");
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("Inventory freshness requires at least one Inventory change");
        }

        Set<InventoryId> inventoryScope = inventoryFreshnessScope(changes);
        Set<StorageNodeId> storageScope = storageFreshnessScope(inventoryScope);
        Set<GoodId> goodScope = goodFreshnessScope(inventoryScope, changes, referencedGoodIds);
        Set<ActorId> actorScope = actorFreshnessScope(inventoryScope, changes, referencedActorIds);

        List<InventoryFreshnessComponent> components = new ArrayList<>();
        inventoryScope.stream().sorted().map(this::runtimeFreshnessComponent).forEach(components::add);
        storageScope.stream().sorted().map(this::storageNodeFreshnessComponent).forEach(components::add);
        inventoryScope.stream().sorted().map(this::containerFreshnessComponent).forEach(components::add);
        goodScope.stream().sorted().map(this::goodFreshnessComponent).forEach(components::add);
        actorScope.stream().sorted().map(this::actorFreshnessComponent).forEach(components::add);
        return InventoryFreshnessIdentity.fromComponents("butchercraft:inventory", components);
    }

    public synchronized InventoryChangeValidation validateChanges(
            Collection<InventoryChange> changes,
            long simulationTick
    ) {
        Objects.requireNonNull(changes, "changes");
        if (changes.isEmpty()) {
            return InventoryChangeValidation.rejected(
                    InventoryChangeCode.EMPTY_CHANGE_SET,
                    "Inventory change set cannot be empty"
            );
        }
        if (simulationTick < 0L) {
            return InventoryChangeValidation.rejected(
                    InventoryChangeCode.INVALID_TICK,
                    "Inventory change simulation tick must not be negative"
            );
        }

        Map<InventoryId, List<InventoryEntry>> overrides = new LinkedHashMap<>();
        for (InventoryChange change : changes) {
            if (change == null) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.INVALID_QUANTITY,
                        "Inventory change cannot be null"
                );
            }
            InventoryRuntime runtime = runtimes.get(change.inventoryId());
            if (runtime == null) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.UNKNOWN_INVENTORY,
                        "Unknown inventory: " + change.inventoryId().value()
                );
            }
            if (simulationTick < runtime.lastSimulationTick()) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.INVALID_TICK,
                        "Inventory simulation tick must not move backward: " + change.inventoryId().value()
                );
            }
            if (change.entry().quantity() <= 0L) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.INVALID_QUANTITY,
                        "Inventory change quantity must be positive"
                );
            }
            if (change.type() == InventoryChangeType.ADD && !runtime.status().canReceive()) {
                return unavailable(change, runtime);
            }
            if (change.type() == InventoryChangeType.REMOVE && !runtime.status().canRelease()) {
                return unavailable(change, runtime);
            }

            GoodDefinition definition = registry.goodRegistry().find(change.entry().goodId()).orElse(null);
            if (definition == null) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.UNKNOWN_GOOD,
                        "Inventory change references unknown Good: " + change.entry().goodId().value()
                );
            }
            if (definition.unitOfMeasure() != change.entry().unitOfMeasure()) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.INVALID_UNIT,
                        "Inventory change unit does not match the Good definition"
                );
            }
            if (change.entry().metadata().originActorId().isPresent()
                    && !registry.actorRegistry().contains(change.entry().metadata().originActorId().orElseThrow())) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.INVALID_METADATA,
                        "Inventory change origin actor is unknown"
                );
            }

            List<InventoryEntry> current = overrides.getOrDefault(change.inventoryId(), runtime.entries());
            try {
                List<InventoryEntry> candidate = change.type() == InventoryChangeType.ADD
                        ? InventoryRuntime.entriesAfterAdding(current, change.entry())
                        : InventoryRuntime.entriesAfterRemoving(current, change.entry());
                overrides.put(change.inventoryId(), candidate);
            } catch (ArithmeticException exception) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.ARITHMETIC_OVERFLOW,
                        "Inventory quantity overflow"
                );
            } catch (IllegalArgumentException exception) {
                return InventoryChangeValidation.rejected(
                        InventoryChangeCode.INSUFFICIENT_QUANTITY,
                        exception.getMessage() == null ? "Inventory quantity is insufficient" : exception.getMessage()
                );
            }
        }

        try {
            validateWithOverrides(overrides);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return InventoryChangeValidation.rejected(
                    InventoryChangeCode.CAPACITY_EXCEEDED,
                    exception.getMessage() == null ? "Inventory capacity validation failed" : exception.getMessage()
            );
        }
        return InventoryChangeValidation.allowed();
    }

    public synchronized List<InventoryChange> applyValidatedChanges(
            TransactionExecutionAuthority authority,
            Collection<InventoryChange> changes,
            long simulationTick
    ) {
        Objects.requireNonNull(authority, "authority");
        List<InventoryChange> orderedChanges = List.copyOf(Objects.requireNonNull(changes, "changes"));
        InventoryChangeValidation validation = validateChanges(orderedChanges, simulationTick);
        if (!validation.isAllowed()) {
            throw new IllegalStateException("Inventory changes were not valid at execution: " + validation.message());
        }

        Map<InventoryId, List<InventoryEntry>> candidates = new LinkedHashMap<>();
        for (InventoryChange change : orderedChanges) {
            InventoryRuntime runtime = requireMutableRuntime(change.inventoryId());
            List<InventoryEntry> current = candidates.getOrDefault(change.inventoryId(), runtime.entries());
            List<InventoryEntry> candidate = change.type() == InventoryChangeType.ADD
                    ? InventoryRuntime.entriesAfterAdding(current, change.entry())
                    : InventoryRuntime.entriesAfterRemoving(current, change.entry());
            candidates.put(change.inventoryId(), candidate);
        }
        candidates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> requireMutableRuntime(entry.getKey()).replaceEntries(entry.getValue(), simulationTick));
        return orderedChanges;
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
                .map(InventoryRuntime::snapshot)
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
        return override != null ? override : requireMutableRuntime(inventoryId).entries();
    }

    private InventoryRuntime requireMutableRuntime(InventoryId inventoryId) {
        InventoryRuntime runtime = runtimes.get(Objects.requireNonNull(inventoryId, "inventoryId"));
        if (runtime == null) {
            throw new IllegalArgumentException("Unknown inventory runtime: " + inventoryId.value());
        }
        return runtime;
    }

    private Set<InventoryId> inventoryFreshnessScope(Collection<InventoryChange> changes) {
        Set<StorageNodeId> relevantCapacityNodes = new HashSet<>();
        for (InventoryChange change : changes) {
            InventoryContainer container = registry.find(change.inventoryId()).orElseThrow(() ->
                    new IllegalArgumentException("Unknown inventory for freshness: " + change.inventoryId().value()));
            relevantCapacityNodes.addAll(registry.ancestorsInclusive(container.storageNodeId()));
        }

        Set<InventoryId> scopedInventories = new HashSet<>();
        for (InventoryContainer container : registry.containers()) {
            boolean sharesCapacityScope = registry.ancestorsInclusive(container.storageNodeId()).stream()
                    .anyMatch(relevantCapacityNodes::contains);
            if (sharesCapacityScope) {
                scopedInventories.add(container.id());
            }
        }
        return scopedInventories;
    }

    private Set<StorageNodeId> storageFreshnessScope(Set<InventoryId> inventoryScope) {
        Set<StorageNodeId> storageScope = new HashSet<>();
        for (InventoryId inventoryId : inventoryScope) {
            InventoryContainer container = registry.find(inventoryId).orElseThrow();
            storageScope.addAll(registry.ancestorsInclusive(container.storageNodeId()));
        }
        return storageScope;
    }

    private Set<GoodId> goodFreshnessScope(
            Set<InventoryId> inventoryScope,
            Collection<InventoryChange> changes,
            Collection<GoodId> referencedGoodIds
    ) {
        Set<GoodId> goodScope = new HashSet<>(referencedGoodIds);
        changes.stream().map(change -> change.entry().goodId()).forEach(goodScope::add);
        for (InventoryId inventoryId : inventoryScope) {
            requireMutableRuntime(inventoryId).entries().stream().map(InventoryEntry::goodId).forEach(goodScope::add);
        }
        return goodScope;
    }

    private Set<ActorId> actorFreshnessScope(
            Set<InventoryId> inventoryScope,
            Collection<InventoryChange> changes,
            Collection<ActorId> referencedActorIds
    ) {
        Set<ActorId> actorScope = new HashSet<>(referencedActorIds);
        changes.stream()
                .map(change -> change.entry().metadata().originActorId())
                .flatMap(Optional::stream)
                .forEach(actorScope::add);
        for (InventoryId inventoryId : inventoryScope) {
            InventoryContainer container = registry.find(inventoryId).orElseThrow();
            actorScope.add(container.ownerActorId());
            requireMutableRuntime(inventoryId).entries().stream()
                    .map(entry -> entry.metadata().originActorId())
                    .flatMap(Optional::stream)
                    .forEach(actorScope::add);
        }
        return actorScope;
    }

    private InventoryFreshnessComponent runtimeFreshnessComponent(InventoryId inventoryId) {
        InventoryRuntime runtime = requireMutableRuntime(inventoryId);
        FreshnessDigest digest = FreshnessDigest.create("butchercraft:inventory_runtime_freshness");
        digest.add(runtime.schemaVersion())
                .add(runtime.inventoryId().value())
                .add(runtime.status().serializedName())
                .add(runtime.lastSimulationTick())
                .add(runtime.entries().size());
        for (InventoryEntry entry : runtime.entries()) {
            addInventoryEntry(digest, entry);
        }
        return InventoryFreshnessComponent.of(
                scopeId("inventory_runtime", inventoryId.value()),
                sourceId("inventory_runtime", inventoryId.value()),
                digest.finish(),
                runtime.lastSimulationTick()
        );
    }

    private InventoryFreshnessComponent containerFreshnessComponent(InventoryId inventoryId) {
        InventoryContainer container = registry.find(inventoryId).orElseThrow();
        FreshnessDigest digest = FreshnessDigest.create("butchercraft:inventory_container_freshness");
        digest.add(container.schemaVersion())
                .add(container.id().value())
                .add(container.displayName())
                .add(container.ownerActorId().value())
                .add(container.storageNodeId().value())
                .add(container.inventoryType().serializedName());
        addCapacity(digest, container.capacity());
        return InventoryFreshnessComponent.of(
                scopeId("inventory_container", inventoryId.value()),
                sourceId("inventory_container", inventoryId.value()),
                digest.finish(),
                0L
        );
    }

    private InventoryFreshnessComponent storageNodeFreshnessComponent(StorageNodeId storageNodeId) {
        StorageNode storageNode = registry.findStorageNode(storageNodeId).orElseThrow();
        FreshnessDigest digest = FreshnessDigest.create("butchercraft:inventory_storage_node_freshness");
        digest.add(storageNode.schemaVersion())
                .add(storageNode.id().value())
                .add(storageNode.displayName())
                .add(storageNode.storageRequirement().serializedName());
        addCapacity(digest, storageNode.capacity());
        addOptionalString(digest, storageNode.parentNodeId().map(StorageNodeId::value));
        return InventoryFreshnessComponent.of(
                scopeId("inventory_storage_node", storageNodeId.value()),
                sourceId("inventory_storage_node", storageNodeId.value()),
                digest.finish(),
                0L
        );
    }

    private InventoryFreshnessComponent goodFreshnessComponent(GoodId goodId) {
        GoodDefinition definition = registry.goodRegistry().find(goodId).orElseThrow(() ->
                new IllegalArgumentException("Unknown Good for freshness: " + goodId.value()));
        FreshnessDigest digest = FreshnessDigest.create("butchercraft:inventory_good_reference_freshness");
        digest.add(definition.schemaVersion())
                .add(definition.id().value())
                .add(definition.displayName())
                .add(definition.category().serializedName())
                .add(definition.industryId().value())
                .add(definition.unitOfMeasure().serializedName())
                .add(definition.stackability().serializedName())
                .add(definition.storageRequirement().serializedName())
                .add(definition.transportRequirement().serializedName());
        digest.add(definition.economicFlags().size());
        definition.economicFlags().stream().map(flag -> flag.serializedName()).sorted().forEach(digest::add);
        digest.add(definition.itemMappings().size());
        for (ItemMappingMetadata mapping : definition.itemMappings()) {
            digest.add(mapping.providerId().value()).add(mapping.itemId().value());
        }
        if (definition instanceof CommodityDefinition commodity) {
            digest.add("commodity").add(commodity.commodityType().serializedName());
        } else if (definition instanceof ProductDefinition product) {
            digest.add("product")
                    .add(product.sourceIndustryId().value())
                    .add(product.transformationStage().serializedName());
        }
        return InventoryFreshnessComponent.of(
                scopeId("inventory_good", goodId.value()),
                sourceId("inventory_good", goodId.value()),
                digest.finish(),
                0L
        );
    }

    private InventoryFreshnessComponent actorFreshnessComponent(ActorId actorId) {
        EconomicActorDefinition definition = registry.actorRegistry().find(actorId).orElseThrow(() ->
                new IllegalArgumentException("Unknown actor for freshness: " + actorId.value()));
        FreshnessDigest digest = FreshnessDigest.create("butchercraft:inventory_actor_reference_freshness");
        digest.add(definition.schemaVersion())
                .add(definition.id().value())
                .add(definition.displayName())
                .add(definition.actorType().serializedName())
                .add(definition.industryId().value())
                .add(definition.capabilities().size());
        definition.capabilities().stream().map(capability -> capability.serializedName()).sorted().forEach(digest::add);
        digest.add(definition.relationships().size());
        for (ActorRelationship relationship : definition.relationships()) {
            digest.add(relationship.schemaVersion())
                    .add(relationship.goodId().value())
                    .add(relationship.goodRole().serializedName())
                    .add(relationship.supportedIndustryIds().size());
            relationship.supportedIndustryIds().stream().map(id -> id.value()).sorted().forEach(digest::add);
            addOptionalString(digest, relationship.dependsOnActorId().map(ActorId::value));
        }
        return InventoryFreshnessComponent.of(
                scopeId("inventory_actor", actorId.value()),
                sourceId("inventory_actor", actorId.value()),
                digest.finish(),
                0L
        );
    }

    private static void addInventoryEntry(FreshnessDigest digest, InventoryEntry entry) {
        digest.add(entry.goodId().value())
                .add(entry.quantity())
                .add(entry.unitOfMeasure().serializedName());
        addEntryMetadata(digest, entry.metadata());
    }

    private static void addEntryMetadata(FreshnessDigest digest, InventoryEntryMetadata metadata) {
        addOptionalString(digest, metadata.lotNumber());
        addOptionalLong(digest, metadata.expirationSimulationTick());
        addOptionalInt(digest, metadata.qualityBasisPoints());
        addOptionalString(digest, metadata.originActorId().map(ActorId::value));
    }

    private static void addCapacity(FreshnessDigest digest, StorageCapacity capacity) {
        addCapacityLimit(digest, capacity.maximumWeight());
        addCapacityLimit(digest, capacity.maximumVolume());
        addOptionalLong(digest, capacity.maximumUnits());
        addOptionalInt(digest, capacity.maximumDistinctGoods());
    }

    private static void addCapacityLimit(
            FreshnessDigest digest,
            Optional<StorageCapacity.CapacityLimit> limit
    ) {
        digest.add(limit.isPresent());
        limit.ifPresent(value -> digest.add(value.quantity()).add(value.unitOfMeasure().serializedName()));
    }

    private static void addOptionalString(FreshnessDigest digest, Optional<String> value) {
        digest.add(value.isPresent());
        value.ifPresent(digest::add);
    }

    private static void addOptionalLong(FreshnessDigest digest, OptionalLong value) {
        digest.add(value.isPresent());
        value.ifPresent(digest::add);
    }

    private static void addOptionalInt(FreshnessDigest digest, OptionalInt value) {
        digest.add(value.isPresent());
        value.ifPresent(digest::add);
    }

    private static String scopeId(String kind, String sourceId) {
        return "butchercraft:" + kind + "/" + canonicalPath(sourceId);
    }

    private static String sourceId(String kind, String sourceId) {
        return "butchercraft:" + kind + "_source/" + canonicalPath(sourceId);
    }

    private static String canonicalPath(String value) {
        return Objects.requireNonNull(value, "value").replace(':', '/');
    }

    private static InventoryChangeValidation unavailable(InventoryChange change, InventoryRuntime runtime) {
        return InventoryChangeValidation.rejected(
                InventoryChangeCode.INVENTORY_UNAVAILABLE,
                "Inventory cannot " + change.type().name().toLowerCase(java.util.Locale.ROOT)
                        + " Goods while " + runtime.status().serializedName() + ": " + change.inventoryId().value()
        );
    }

    private static InventoryMovementValidation rejected(InventoryMovementCode code, String message) {
        return InventoryMovementValidation.rejected(code, message);
    }

    private static final class FreshnessDigest {
        private final MessageDigest digest;

        private FreshnessDigest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
            add(domain);
        }

        static FreshnessDigest create(String domain) {
            return new FreshnessDigest(domain);
        }

        FreshnessDigest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "digestValue").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 0);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(bytes);
            return this;
        }

        FreshnessDigest add(int value) {
            return add(Integer.toString(value));
        }

        FreshnessDigest add(long value) {
            return add(Long.toString(value));
        }

        FreshnessDigest add(boolean value) {
            return add(Boolean.toString(value));
        }

        String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
