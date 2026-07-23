package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

public final class InventoryStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String STORAGE_NODES = "storage_nodes";
    private static final String INVENTORY_CONTAINERS = "inventory_containers";
    private static final String INVENTORY_RUNTIMES = "inventory_runtimes";
    private static final String ID = "id";
    private static final String INVENTORY_ID = "inventory_id";
    private static final String DISPLAY_NAME = "display_name";
    private static final String OWNER_ACTOR_ID = "owner_actor_id";
    private static final String STORAGE_NODE_ID = "storage_node_id";
    private static final String INVENTORY_TYPE = "inventory_type";
    private static final String STORAGE_REQUIREMENT = "storage_requirement";
    private static final String PARENT_NODE_ID = "parent_node_id";
    private static final String CAPACITY = "capacity";
    private static final String MAXIMUM_WEIGHT = "maximum_weight";
    private static final String MAXIMUM_VOLUME = "maximum_volume";
    private static final String MAXIMUM_UNITS = "maximum_units";
    private static final String MAXIMUM_DISTINCT_GOODS = "maximum_distinct_goods";
    private static final String QUANTITY = "quantity";
    private static final String UNIT = "unit";
    private static final String STATUS = "status";
    private static final String LAST_SIMULATION_TICK = "last_simulation_tick";
    private static final String ENTRIES = "entries";
    private static final String GOOD_ID = "good_id";
    private static final String METADATA = "metadata";
    private static final String LOT_NUMBER = "lot_number";
    private static final String EXPIRATION_SIMULATION_TICK = "expiration_simulation_tick";
    private static final String QUALITY_BASIS_POINTS = "quality_basis_points";
    private static final String ORIGIN_ACTOR_ID = "origin_actor_id";

    private final Path filePath;
    private final GoodRegistry goodRegistry;
    private final EconomicActorRegistry actorRegistry;

    public InventoryStorage(
            Path filePath,
            GoodRegistry goodRegistry,
            EconomicActorRegistry actorRegistry
    ) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.goodRegistry = Objects.requireNonNull(goodRegistry, "goodRegistry");
        this.actorRegistry = Objects.requireNonNull(actorRegistry, "actorRegistry");
    }

    public Path filePath() {
        return filePath;
    }

    public InventoryManager load() {
        if (!Files.exists(filePath)) {
            return new InventoryManager(InventoryRegistry.empty(goodRegistry, actorRegistry));
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load inventory from " + filePath, exception);
        }
    }

    public void save(InventoryManager manager) {
        Objects.requireNonNull(manager, "manager");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(manager), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save inventory to " + filePath, exception);
        }
    }

    public String serialize(InventoryManager manager) {
        Objects.requireNonNull(manager, "manager");
        manager.validate();
        validateCatalogs(manager.registry());

        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, InventorySchema.CURRENT_VERSION);

        JsonArray storageNodes = new JsonArray();
        manager.registry().storageNodes().forEach(node -> storageNodes.add(serializeStorageNode(node)));
        root.add(STORAGE_NODES, storageNodes);

        JsonArray containers = new JsonArray();
        manager.registry().containers().forEach(container -> containers.add(serializeContainer(container)));
        root.add(INVENTORY_CONTAINERS, containers);

        JsonArray runtimes = new JsonArray();
        manager.runtimes().forEach(runtime -> runtimes.add(serializeRuntime(runtime)));
        root.add(INVENTORY_RUNTIMES, runtimes);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public InventoryManager deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "inventory root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != InventorySchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported inventory schema version: " + schemaVersion);
            }

            InventoryRegistryBuilder builder = InventoryRegistry.builder(goodRegistry, actorRegistry);
            for (JsonElement element : requireArray(root, STORAGE_NODES)) {
                builder.registerStorageNode(deserializeStorageNode(requireObject(element, "storage node")));
            }
            for (JsonElement element : requireArray(root, INVENTORY_CONTAINERS)) {
                builder.registerContainer(deserializeContainer(requireObject(element, "inventory container")));
            }
            InventoryRegistry registry = builder.build();

            List<InventoryRuntime> runtimes = new ArrayList<>();
            for (JsonElement element : requireArray(root, INVENTORY_RUNTIMES)) {
                runtimes.add(deserializeRuntime(requireObject(element, "inventory runtime")));
            }
            Set<InventoryId> runtimeIds = runtimes.stream()
                    .map(InventoryRuntime::inventoryId)
                    .collect(Collectors.toSet());
            Set<InventoryId> containerIds = registry.stream()
                    .map(InventoryContainer::id)
                    .collect(Collectors.toSet());
            if (!runtimeIds.equals(containerIds)) {
                throw new IllegalArgumentException("Inventory persistence runtime set does not match containers");
            }
            return new InventoryManager(registry, runtimes);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt inventory persistence", exception);
        }
    }

    private JsonObject serializeStorageNode(StorageNode node) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, node.schemaVersion());
        object.addProperty(ID, node.id().value());
        object.addProperty(DISPLAY_NAME, node.displayName());
        object.addProperty(STORAGE_REQUIREMENT, node.storageRequirement().serializedName());
        object.add(CAPACITY, serializeCapacity(node.capacity()));
        addOptionalString(object, PARENT_NODE_ID, node.parentNodeId().map(StorageNodeId::value));
        return object;
    }

    private StorageNode deserializeStorageNode(JsonObject object) {
        int schemaVersion = requireSupportedRecordSchema(object, "storage node");
        return new StorageNode(
                StorageNodeId.of(requireString(object, ID)),
                requireString(object, DISPLAY_NAME),
                StorageRequirement.fromSerializedName(requireString(object, STORAGE_REQUIREMENT)),
                deserializeCapacity(requireObject(requireField(object, CAPACITY), "storage capacity")),
                optionalString(object, PARENT_NODE_ID).map(StorageNodeId::of),
                schemaVersion
        );
    }

    private JsonObject serializeContainer(InventoryContainer container) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, container.schemaVersion());
        object.addProperty(ID, container.id().value());
        object.addProperty(DISPLAY_NAME, container.displayName());
        object.addProperty(OWNER_ACTOR_ID, container.ownerActorId().value());
        object.addProperty(STORAGE_NODE_ID, container.storageNodeId().value());
        object.addProperty(INVENTORY_TYPE, container.inventoryType().serializedName());
        object.add(CAPACITY, serializeCapacity(container.capacity()));
        return object;
    }

    private InventoryContainer deserializeContainer(JsonObject object) {
        int schemaVersion = requireSupportedRecordSchema(object, "inventory container");
        return new InventoryContainer(
                InventoryId.of(requireString(object, ID)),
                requireString(object, DISPLAY_NAME),
                ActorId.of(requireString(object, OWNER_ACTOR_ID)),
                StorageNodeId.of(requireString(object, STORAGE_NODE_ID)),
                InventoryType.fromSerializedName(requireString(object, INVENTORY_TYPE)),
                deserializeCapacity(requireObject(requireField(object, CAPACITY), "storage capacity")),
                schemaVersion
        );
    }

    private JsonObject serializeRuntime(InventoryRuntime runtime) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, runtime.schemaVersion());
        object.addProperty(INVENTORY_ID, runtime.inventoryId().value());
        object.addProperty(STATUS, runtime.status().serializedName());
        object.addProperty(LAST_SIMULATION_TICK, runtime.lastSimulationTick());
        JsonArray entries = new JsonArray();
        runtime.entries().forEach(entry -> entries.add(serializeEntry(entry)));
        object.add(ENTRIES, entries);
        return object;
    }

    private InventoryRuntime deserializeRuntime(JsonObject object) {
        int schemaVersion = requireSupportedRecordSchema(object, "inventory runtime");
        List<InventoryEntry> entries = new ArrayList<>();
        for (JsonElement element : requireArray(object, ENTRIES)) {
            entries.add(deserializeEntry(requireObject(element, "inventory entry")));
        }
        return new InventoryRuntime(
                InventoryId.of(requireString(object, INVENTORY_ID)),
                InventoryStatus.fromSerializedName(requireString(object, STATUS)),
                entries,
                requireLong(object, LAST_SIMULATION_TICK),
                schemaVersion
        );
    }

    private JsonObject serializeEntry(InventoryEntry entry) {
        JsonObject object = new JsonObject();
        object.addProperty(GOOD_ID, entry.goodId().value());
        object.addProperty(QUANTITY, entry.quantity());
        object.addProperty(UNIT, entry.unitOfMeasure().serializedName());
        object.add(METADATA, serializeMetadata(entry.metadata()));
        return object;
    }

    private InventoryEntry deserializeEntry(JsonObject object) {
        return new InventoryEntry(
                GoodId.of(requireString(object, GOOD_ID)),
                requireLong(object, QUANTITY),
                UnitOfMeasure.fromSerializedName(requireString(object, UNIT)),
                deserializeMetadata(requireObject(requireField(object, METADATA), "inventory entry metadata"))
        );
    }

    private JsonObject serializeMetadata(InventoryEntryMetadata metadata) {
        JsonObject object = new JsonObject();
        addOptionalString(object, LOT_NUMBER, metadata.lotNumber());
        addOptionalLong(object, EXPIRATION_SIMULATION_TICK, metadata.expirationSimulationTick());
        addOptionalInt(object, QUALITY_BASIS_POINTS, metadata.qualityBasisPoints());
        addOptionalString(object, ORIGIN_ACTOR_ID, metadata.originActorId().map(ActorId::value));
        return object;
    }

    private InventoryEntryMetadata deserializeMetadata(JsonObject object) {
        return new InventoryEntryMetadata(
                optionalString(object, LOT_NUMBER),
                optionalLong(object, EXPIRATION_SIMULATION_TICK),
                optionalInt(object, QUALITY_BASIS_POINTS),
                optionalString(object, ORIGIN_ACTOR_ID).map(ActorId::of)
        );
    }

    private JsonObject serializeCapacity(StorageCapacity capacity) {
        JsonObject object = new JsonObject();
        object.add(MAXIMUM_WEIGHT, capacity.maximumWeight()
                .<JsonElement>map(this::serializeCapacityLimit)
                .orElse(JsonNull.INSTANCE));
        object.add(MAXIMUM_VOLUME, capacity.maximumVolume()
                .<JsonElement>map(this::serializeCapacityLimit)
                .orElse(JsonNull.INSTANCE));
        addOptionalLong(object, MAXIMUM_UNITS, capacity.maximumUnits());
        addOptionalInt(object, MAXIMUM_DISTINCT_GOODS, capacity.maximumDistinctGoods());
        return object;
    }

    private StorageCapacity deserializeCapacity(JsonObject object) {
        Optional<StorageCapacity.CapacityLimit> maximumWeight = optionalObject(object, MAXIMUM_WEIGHT)
                .map(this::deserializeCapacityLimit);
        Optional<StorageCapacity.CapacityLimit> maximumVolume = optionalObject(object, MAXIMUM_VOLUME)
                .map(this::deserializeCapacityLimit);
        return new StorageCapacity(
                maximumWeight,
                maximumVolume,
                optionalLong(object, MAXIMUM_UNITS),
                optionalInt(object, MAXIMUM_DISTINCT_GOODS)
        );
    }

    private JsonObject serializeCapacityLimit(StorageCapacity.CapacityLimit limit) {
        JsonObject object = new JsonObject();
        object.addProperty(QUANTITY, limit.quantity());
        object.addProperty(UNIT, limit.unitOfMeasure().serializedName());
        return object;
    }

    private StorageCapacity.CapacityLimit deserializeCapacityLimit(JsonObject object) {
        return new StorageCapacity.CapacityLimit(
                requireLong(object, QUANTITY),
                UnitOfMeasure.fromSerializedName(requireString(object, UNIT))
        );
    }

    private void validateCatalogs(InventoryRegistry registry) {
        if (!registry.goodRegistry().definitions().equals(goodRegistry.definitions())) {
            throw new IllegalArgumentException("Inventory manager Goods registry does not match storage Goods registry");
        }
        if (!registry.actorRegistry().definitions().equals(actorRegistry.definitions())) {
            throw new IllegalArgumentException("Inventory manager actor registry does not match storage actor registry");
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int requireSupportedRecordSchema(JsonObject object, String label) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != InventorySchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema version: " + schemaVersion);
        }
        return schemaVersion;
    }

    private static void addOptionalString(JsonObject object, String fieldName, Optional<String> value) {
        if (value.isPresent()) {
            object.addProperty(fieldName, value.orElseThrow());
        } else {
            object.add(fieldName, JsonNull.INSTANCE);
        }
    }

    private static void addOptionalLong(JsonObject object, String fieldName, OptionalLong value) {
        if (value.isPresent()) {
            object.addProperty(fieldName, value.getAsLong());
        } else {
            object.add(fieldName, JsonNull.INSTANCE);
        }
    }

    private static void addOptionalInt(JsonObject object, String fieldName, OptionalInt value) {
        if (value.isPresent()) {
            object.addProperty(fieldName, value.getAsInt());
        } else {
            object.add(fieldName, JsonNull.INSTANCE);
        }
    }

    private static Optional<String> optionalString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        return element.isJsonNull() ? Optional.empty() : Optional.of(requireString(element, fieldName));
    }

    private static OptionalLong optionalLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        return element.isJsonNull() ? OptionalLong.empty() : OptionalLong.of(requireLong(element, fieldName));
    }

    private static OptionalInt optionalInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        return element.isJsonNull() ? OptionalInt.empty() : OptionalInt.of(requireInt(element, fieldName));
    }

    private static Optional<JsonObject> optionalObject(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        return element.isJsonNull() ? Optional.empty() : Optional.of(requireObject(element, fieldName));
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Inventory field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        return requireInt(requireField(object, fieldName), fieldName);
    }

    private static int requireInt(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Inventory field must be a number: " + label);
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Inventory field must be an exact integer: " + label, exception);
        }
    }

    private static long requireLong(JsonObject object, String fieldName) {
        return requireLong(requireField(object, fieldName), fieldName);
    }

    private static long requireLong(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Inventory field must be a number: " + label);
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Inventory field must be an exact whole number: " + label, exception);
        }
    }

    private static String requireString(JsonObject object, String fieldName) {
        return requireString(requireField(object, fieldName), fieldName);
    }

    private static String requireString(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Inventory field must be a string: " + label);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Inventory field must not be blank: " + label);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing inventory field: " + fieldName);
        }
        return element;
    }
}
