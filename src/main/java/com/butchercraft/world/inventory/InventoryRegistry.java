package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class InventoryRegistry {
    private static final Comparator<InventoryContainer> CONTAINER_ORDER = Comparator.comparing(InventoryContainer::id);
    private static final Comparator<StorageNode> STORAGE_NODE_ORDER = Comparator.comparing(StorageNode::id);

    private final GoodRegistry goodRegistry;
    private final EconomicActorRegistry actorRegistry;
    private final List<InventoryContainer> containers;
    private final Map<InventoryId, InventoryContainer> containersById;
    private final Map<ActorId, List<InventoryContainer>> containersByOwner;
    private final Map<StorageNodeId, List<InventoryContainer>> containersByStorageNode;
    private final Map<InventoryType, List<InventoryContainer>> containersByType;
    private final List<StorageNode> storageNodes;
    private final Map<StorageNodeId, StorageNode> storageNodesById;
    private final Map<StorageNodeId, List<StorageNode>> childNodesByParent;

    private InventoryRegistry(
            GoodRegistry goodRegistry,
            EconomicActorRegistry actorRegistry,
            List<InventoryContainer> containers,
            Map<InventoryId, InventoryContainer> containersById,
            Map<ActorId, List<InventoryContainer>> containersByOwner,
            Map<StorageNodeId, List<InventoryContainer>> containersByStorageNode,
            Map<InventoryType, List<InventoryContainer>> containersByType,
            List<StorageNode> storageNodes,
            Map<StorageNodeId, StorageNode> storageNodesById,
            Map<StorageNodeId, List<StorageNode>> childNodesByParent
    ) {
        this.goodRegistry = goodRegistry;
        this.actorRegistry = actorRegistry;
        this.containers = containers;
        this.containersById = containersById;
        this.containersByOwner = containersByOwner;
        this.containersByStorageNode = containersByStorageNode;
        this.containersByType = containersByType;
        this.storageNodes = storageNodes;
        this.storageNodesById = storageNodesById;
        this.childNodesByParent = childNodesByParent;
    }

    public static InventoryRegistry empty(GoodRegistry goodRegistry, EconomicActorRegistry actorRegistry) {
        return of(List.of(), List.of(), goodRegistry, actorRegistry);
    }

    public static InventoryRegistry of(
            Collection<InventoryContainer> containers,
            Collection<StorageNode> storageNodes,
            GoodRegistry goodRegistry,
            EconomicActorRegistry actorRegistry
    ) {
        Objects.requireNonNull(goodRegistry, "goodRegistry");
        Objects.requireNonNull(actorRegistry, "actorRegistry");
        if (actorRegistry.goodRegistry() != goodRegistry
                && !actorRegistry.goodRegistry().definitions().equals(goodRegistry.definitions())) {
            throw new IllegalArgumentException("Inventory Goods registry does not match Economic Actor Goods registry");
        }

        List<StorageNode> orderedStorageNodes = Objects.requireNonNull(storageNodes, "storageNodes").stream()
                .map(node -> Objects.requireNonNull(node, "storageNode"))
                .sorted(STORAGE_NODE_ORDER)
                .toList();
        rejectDuplicateStorageNodeIds(orderedStorageNodes);
        Map<StorageNodeId, StorageNode> nodesById = orderedStorageNodes.stream()
                .collect(Collectors.toUnmodifiableMap(StorageNode::id, Function.identity()));
        validateStorageHierarchy(orderedStorageNodes, nodesById);

        List<InventoryContainer> orderedContainers = Objects.requireNonNull(containers, "containers").stream()
                .map(container -> Objects.requireNonNull(container, "container"))
                .sorted(CONTAINER_ORDER)
                .toList();
        rejectDuplicateContainerIds(orderedContainers);
        validateContainers(orderedContainers, nodesById, actorRegistry);

        Map<InventoryId, InventoryContainer> containersById = orderedContainers.stream()
                .collect(Collectors.toUnmodifiableMap(InventoryContainer::id, Function.identity()));
        return new InventoryRegistry(
                goodRegistry,
                actorRegistry,
                List.copyOf(orderedContainers),
                containersById,
                immutableGroups(orderedContainers, InventoryContainer::ownerActorId),
                immutableGroups(orderedContainers, InventoryContainer::storageNodeId),
                immutableGroups(orderedContainers, InventoryContainer::inventoryType),
                List.copyOf(orderedStorageNodes),
                nodesById,
                groupChildNodes(orderedStorageNodes)
        );
    }

    public static InventoryRegistryBuilder builder(
            GoodRegistry goodRegistry,
            EconomicActorRegistry actorRegistry
    ) {
        return new InventoryRegistryBuilder(goodRegistry, actorRegistry);
    }

    public InventoryRegistry withContainer(InventoryContainer container) {
        Objects.requireNonNull(container, "container");
        if (contains(container.id())) {
            throw new IllegalArgumentException("Duplicate inventory id: " + container.id().value());
        }
        List<InventoryContainer> updated = new ArrayList<>(containers);
        updated.add(container);
        return of(updated, storageNodes, goodRegistry, actorRegistry);
    }

    public InventoryRegistry withStorageNode(StorageNode storageNode) {
        Objects.requireNonNull(storageNode, "storageNode");
        if (containsStorageNode(storageNode.id())) {
            throw new IllegalArgumentException("Duplicate storage node id: " + storageNode.id().value());
        }
        List<StorageNode> updated = new ArrayList<>(storageNodes);
        updated.add(storageNode);
        return of(containers, updated, goodRegistry, actorRegistry);
    }

    public boolean contains(InventoryId inventoryId) {
        return containersById.containsKey(Objects.requireNonNull(inventoryId, "inventoryId"));
    }

    public Optional<InventoryContainer> find(InventoryId inventoryId) {
        return Optional.ofNullable(containersById.get(Objects.requireNonNull(inventoryId, "inventoryId")));
    }

    public boolean containsStorageNode(StorageNodeId storageNodeId) {
        return storageNodesById.containsKey(Objects.requireNonNull(storageNodeId, "storageNodeId"));
    }

    public Optional<StorageNode> findStorageNode(StorageNodeId storageNodeId) {
        return Optional.ofNullable(storageNodesById.get(Objects.requireNonNull(storageNodeId, "storageNodeId")));
    }

    public int size() {
        return containers.size();
    }

    public int storageNodeCount() {
        return storageNodes.size();
    }

    public List<InventoryContainer> containers() {
        return containers;
    }

    public Stream<InventoryContainer> stream() {
        return containers.stream();
    }

    public List<StorageNode> storageNodes() {
        return storageNodes;
    }

    public Stream<StorageNode> storageNodeStream() {
        return storageNodes.stream();
    }

    public List<InventoryContainer> findByOwner(ActorId actorId) {
        return containersByOwner.getOrDefault(Objects.requireNonNull(actorId, "actorId"), List.of());
    }

    public List<InventoryContainer> findByStorageNode(StorageNodeId storageNodeId) {
        return containersByStorageNode.getOrDefault(
                Objects.requireNonNull(storageNodeId, "storageNodeId"),
                List.of()
        );
    }

    public List<InventoryContainer> findByType(InventoryType inventoryType) {
        return containersByType.getOrDefault(Objects.requireNonNull(inventoryType, "inventoryType"), List.of());
    }

    public List<StorageNode> childNodesOf(StorageNodeId storageNodeId) {
        return childNodesByParent.getOrDefault(Objects.requireNonNull(storageNodeId, "storageNodeId"), List.of());
    }

    public List<StorageNode> descendantsOf(StorageNodeId storageNodeId) {
        if (!containsStorageNode(storageNodeId)) {
            return List.of();
        }
        List<StorageNode> descendants = new ArrayList<>();
        ArrayDeque<StorageNode> pending = new ArrayDeque<>(childNodesOf(storageNodeId));
        while (!pending.isEmpty()) {
            StorageNode next = pending.removeFirst();
            descendants.add(next);
            pending.addAll(childNodesOf(next.id()));
        }
        return descendants.stream().sorted(STORAGE_NODE_ORDER).toList();
    }

    public List<StorageNodeId> ancestorsInclusive(StorageNodeId storageNodeId) {
        StorageNode current = findStorageNode(storageNodeId).orElse(null);
        if (current == null) {
            return List.of();
        }
        List<StorageNodeId> ancestors = new ArrayList<>();
        while (current != null) {
            ancestors.add(current.id());
            current = current.parentNodeId().map(storageNodesById::get).orElse(null);
        }
        return List.copyOf(ancestors);
    }

    public GoodRegistry goodRegistry() {
        return goodRegistry;
    }

    public EconomicActorRegistry actorRegistry() {
        return actorRegistry;
    }

    public void validate() {
        of(containers, storageNodes, goodRegistry, actorRegistry);
    }

    private static void rejectDuplicateContainerIds(List<InventoryContainer> containers) {
        Set<InventoryId> seen = new HashSet<>();
        Set<InventoryId> duplicates = new LinkedHashSet<>();
        for (InventoryContainer container : containers) {
            if (!seen.add(container.id())) {
                duplicates.add(container.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate inventory ids: " + duplicates);
        }
    }

    private static void rejectDuplicateStorageNodeIds(List<StorageNode> storageNodes) {
        Set<StorageNodeId> seen = new HashSet<>();
        Set<StorageNodeId> duplicates = new LinkedHashSet<>();
        for (StorageNode storageNode : storageNodes) {
            if (!seen.add(storageNode.id())) {
                duplicates.add(storageNode.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate storage node ids: " + duplicates);
        }
    }

    private static void validateContainers(
            List<InventoryContainer> containers,
            Map<StorageNodeId, StorageNode> storageNodes,
            EconomicActorRegistry actorRegistry
    ) {
        for (InventoryContainer container : containers) {
            if (!actorRegistry.contains(container.ownerActorId())) {
                throw new IllegalArgumentException("Inventory references unknown actor: "
                        + container.id().value() + "/" + container.ownerActorId().value());
            }
            StorageNode storageNode = storageNodes.get(container.storageNodeId());
            if (storageNode == null) {
                throw new IllegalArgumentException("Inventory references unknown storage node: "
                        + container.id().value() + "/" + container.storageNodeId().value());
            }
            container.capacity().validateWithin(storageNode.capacity(), "inventory " + container.id().value());
        }
    }

    private static void validateStorageHierarchy(
            List<StorageNode> storageNodes,
            Map<StorageNodeId, StorageNode> storageNodesById
    ) {
        Map<StorageNodeId, Integer> indegree = new HashMap<>();
        Map<StorageNodeId, List<StorageNodeId>> adjacency = new HashMap<>();
        for (StorageNode node : storageNodes) {
            indegree.put(node.id(), 0);
        }
        for (StorageNode node : storageNodes) {
            node.parentNodeId().ifPresent(parentId -> {
                StorageNode parent = storageNodesById.get(parentId);
                if (parent == null) {
                    throw new IllegalArgumentException("Storage node references unknown parent: "
                            + node.id().value() + "/" + parentId.value());
                }
                node.capacity().validateWithin(parent.capacity(), "storage node " + node.id().value());
                adjacency.computeIfAbsent(node.id(), ignored -> new ArrayList<>()).add(parentId);
                indegree.compute(parentId, (ignored, degree) -> degree + 1);
            });
        }

        PriorityQueue<StorageNodeId> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            StorageNodeId current = ready.remove();
            visited++;
            for (StorageNodeId parent : adjacency.getOrDefault(current, List.of())) {
                int remaining = indegree.compute(parent, (ignored, degree) -> degree - 1);
                if (remaining == 0) {
                    ready.add(parent);
                }
            }
        }
        if (visited != storageNodes.size()) {
            throw new IllegalArgumentException("Circular storage node hierarchy");
        }
    }

    private static Map<StorageNodeId, List<StorageNode>> groupChildNodes(List<StorageNode> storageNodes) {
        Map<StorageNodeId, List<StorageNode>> mutable = new LinkedHashMap<>();
        for (StorageNode node : storageNodes) {
            node.parentNodeId().ifPresent(parent -> mutable
                    .computeIfAbsent(parent, ignored -> new ArrayList<>())
                    .add(node));
        }
        Map<StorageNodeId, List<StorageNode>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }

    private static <K> Map<K, List<InventoryContainer>> immutableGroups(
            List<InventoryContainer> containers,
            Function<InventoryContainer, K> keyFunction
    ) {
        Map<K, List<InventoryContainer>> mutable = new LinkedHashMap<>();
        for (InventoryContainer container : containers) {
            mutable.computeIfAbsent(keyFunction.apply(container), ignored -> new ArrayList<>()).add(container);
        }
        Map<K, List<InventoryContainer>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }
}
