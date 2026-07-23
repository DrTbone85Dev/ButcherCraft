package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodRegistry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class InventoryRegistryBuilder {
    private final GoodRegistry goodRegistry;
    private final EconomicActorRegistry actorRegistry;
    private final Map<InventoryId, InventoryContainer> containers = new LinkedHashMap<>();
    private final Map<StorageNodeId, StorageNode> storageNodes = new LinkedHashMap<>();

    InventoryRegistryBuilder(GoodRegistry goodRegistry, EconomicActorRegistry actorRegistry) {
        this.goodRegistry = Objects.requireNonNull(goodRegistry, "goodRegistry");
        this.actorRegistry = Objects.requireNonNull(actorRegistry, "actorRegistry");
    }

    public InventoryRegistryBuilder registerContainer(InventoryContainer container) {
        Objects.requireNonNull(container, "container");
        InventoryContainer previous = containers.putIfAbsent(container.id(), container);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate inventory id: " + container.id().value());
        }
        return this;
    }

    public InventoryRegistryBuilder registerContainers(Collection<InventoryContainer> containers) {
        Objects.requireNonNull(containers, "containers").forEach(this::registerContainer);
        return this;
    }

    public InventoryRegistryBuilder registerStorageNode(StorageNode storageNode) {
        Objects.requireNonNull(storageNode, "storageNode");
        StorageNode previous = storageNodes.putIfAbsent(storageNode.id(), storageNode);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate storage node id: " + storageNode.id().value());
        }
        return this;
    }

    public InventoryRegistryBuilder registerStorageNodes(Collection<StorageNode> storageNodes) {
        Objects.requireNonNull(storageNodes, "storageNodes").forEach(this::registerStorageNode);
        return this;
    }

    public InventoryRegistry build() {
        return InventoryRegistry.of(containers.values(), storageNodes.values(), goodRegistry, actorRegistry);
    }
}
