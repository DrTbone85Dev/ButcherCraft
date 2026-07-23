package com.butchercraft.world.transaction;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;
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

public final class TransactionStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String TRANSACTIONS = "transactions";
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String SOURCE_ACTOR_ID = "source_actor_id";
    private static final String DESTINATION_ACTOR_ID = "destination_actor_id";
    private static final String SOURCE_INVENTORY_ID = "source_inventory_id";
    private static final String DESTINATION_INVENTORY_ID = "destination_inventory_id";
    private static final String GOOD_ID = "good_id";
    private static final String QUANTITY = "quantity";
    private static final String UNIT = "unit";
    private static final String SIMULATION_TICK = "simulation_tick";
    private static final String STATUS = "status";
    private static final String METADATA = "metadata";
    private static final String REASON = "reason";
    private static final String REFERENCE_ID = "reference_id";
    private static final String USER = "user";
    private static final String EXTERNAL_SYSTEM = "external_system";
    private static final String COMMENTS = "comments";

    private final Path filePath;
    private final InventoryManager inventoryManager;

    public TransactionStorage(Path filePath, InventoryManager inventoryManager) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.inventoryManager = Objects.requireNonNull(inventoryManager, "inventoryManager");
    }

    public Path filePath() {
        return filePath;
    }

    public TransactionManager load() {
        if (!Files.exists(filePath)) {
            return new TransactionManager(inventoryManager);
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load transactions from " + filePath, exception);
        }
    }

    public void save(TransactionManager manager) {
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
            throw new UncheckedIOException("Failed to save transactions to " + filePath, exception);
        }
    }

    public String serialize(TransactionManager manager) {
        Objects.requireNonNull(manager, "manager");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, TransactionSchema.CURRENT_VERSION);
        JsonArray transactions = new JsonArray();
        manager.history().forEach(transaction -> transactions.add(serializeTransaction(transaction)));
        root.add(TRANSACTIONS, transactions);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public TransactionManager deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "transaction root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != TransactionSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported transaction schema version: " + schemaVersion);
            }
            List<EconomicTransaction> transactions = new ArrayList<>();
            for (JsonElement element : requireArray(root, TRANSACTIONS)) {
                transactions.add(deserializeTransaction(requireObject(element, "transaction")));
            }
            return new TransactionManager(new TransactionRegistry(transactions), inventoryManager);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt transaction persistence", exception);
        }
    }

    private JsonObject serializeTransaction(EconomicTransaction transaction) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, transaction.schemaVersion());
        object.addProperty(ID, transaction.id().value());
        object.addProperty(TYPE, transaction.type().serializedName());
        addOptionalString(object, SOURCE_ACTOR_ID, transaction.sourceActorId().map(ActorId::value));
        addOptionalString(object, DESTINATION_ACTOR_ID, transaction.destinationActorId().map(ActorId::value));
        addOptionalString(object, SOURCE_INVENTORY_ID, transaction.sourceInventoryId().map(InventoryId::value));
        addOptionalString(
                object,
                DESTINATION_INVENTORY_ID,
                transaction.destinationInventoryId().map(InventoryId::value)
        );
        object.addProperty(GOOD_ID, transaction.goodId().value());
        object.addProperty(QUANTITY, transaction.quantity());
        object.addProperty(UNIT, transaction.unitOfMeasure().serializedName());
        object.addProperty(SIMULATION_TICK, transaction.simulationTick());
        object.addProperty(STATUS, transaction.status().serializedName());
        object.add(METADATA, serializeMetadata(transaction.metadata()));
        return object;
    }

    private EconomicTransaction deserializeTransaction(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != TransactionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported transaction record schema version: " + schemaVersion);
        }
        return new EconomicTransaction(
                TransactionId.of(requireString(object, ID)),
                TransactionType.fromSerializedName(requireString(object, TYPE)),
                optionalString(object, SOURCE_ACTOR_ID).map(ActorId::of),
                optionalString(object, DESTINATION_ACTOR_ID).map(ActorId::of),
                optionalString(object, SOURCE_INVENTORY_ID).map(InventoryId::of),
                optionalString(object, DESTINATION_INVENTORY_ID).map(InventoryId::of),
                GoodId.of(requireString(object, GOOD_ID)),
                requireLong(object, QUANTITY),
                UnitOfMeasure.fromSerializedName(requireString(object, UNIT)),
                requireLong(object, SIMULATION_TICK),
                TransactionStatus.fromSerializedName(requireString(object, STATUS)),
                deserializeMetadata(requireObject(requireField(object, METADATA), "transaction metadata")),
                schemaVersion
        );
    }

    private JsonObject serializeMetadata(TransactionMetadata metadata) {
        JsonObject object = new JsonObject();
        addOptionalString(object, REASON, metadata.reason());
        addOptionalString(object, REFERENCE_ID, metadata.referenceId());
        addOptionalString(object, USER, metadata.user());
        addOptionalString(object, EXTERNAL_SYSTEM, metadata.externalSystem());
        addOptionalString(object, COMMENTS, metadata.comments());
        return object;
    }

    private TransactionMetadata deserializeMetadata(JsonObject object) {
        return new TransactionMetadata(
                optionalString(object, REASON),
                optionalString(object, REFERENCE_ID),
                optionalString(object, USER),
                optionalString(object, EXTERNAL_SYSTEM),
                optionalString(object, COMMENTS)
        );
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void addOptionalString(JsonObject object, String fieldName, Optional<String> value) {
        if (value.isPresent()) {
            object.addProperty(fieldName, value.orElseThrow());
        } else {
            object.add(fieldName, JsonNull.INSTANCE);
        }
    }

    private static Optional<String> optionalString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        return element.isJsonNull() ? Optional.empty() : Optional.of(requireString(element, fieldName));
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
            throw new IllegalArgumentException("Transaction field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Transaction field must be a number: " + fieldName);
        }
        try {
            return element.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Transaction field must be an exact integer: " + fieldName,
                    exception
            );
        }
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Transaction field must be a number: " + fieldName);
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Transaction field must be an exact whole number: " + fieldName,
                    exception
            );
        }
    }

    private static String requireString(JsonObject object, String fieldName) {
        return requireString(requireField(object, fieldName), fieldName);
    }

    private static String requireString(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Transaction field must be a string: " + label);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Transaction field must not be blank: " + label);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing transaction field: " + fieldName);
        }
        return element;
    }
}
