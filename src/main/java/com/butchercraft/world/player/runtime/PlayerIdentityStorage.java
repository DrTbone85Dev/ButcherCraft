package com.butchercraft.world.player.runtime;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.ownership.FamilyId;
import com.butchercraft.world.ownership.OwnershipEntityId;
import com.butchercraft.world.player.CareerProfile;
import com.butchercraft.world.player.PlayerIdentityId;
import com.butchercraft.world.player.StartingScenarioId;
import com.butchercraft.world.property.CommercialPropertyId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PlayerIdentityStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String PLAYER_IDENTITIES = "player_identities";
    private static final String MINECRAFT_UUID = "minecraft_uuid";
    private static final String PLAYER_IDENTITY_ID = "player_identity_id";
    private static final String STARTING_SCENARIO = "starting_scenario";
    private static final String CAREER_PROFILE = "career_profile";
    private static final String SETTLEMENT_ID = "settlement_id";
    private static final String COMMERCIAL_PROPERTY_ID = "commercial_property_id";
    private static final String BUSINESS_ID = "business_id";
    private static final String OWNERSHIP_ENTITY_ID = "ownership_entity_id";
    private static final String FAMILY_ID = "family_id";
    private static final String CREATION_TIMESTAMP = "creation_timestamp";

    private final Path filePath;

    public PlayerIdentityStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public PlayerIdentityRegistry load() {
        if (!Files.exists(filePath)) {
            return PlayerIdentityRegistry.empty();
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load player identities from " + filePath, exception);
        }
    }

    public void save(PlayerIdentityRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(registry), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save player identities to " + filePath, exception);
        }
    }

    public String serialize(PlayerIdentityRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, PlayerIdentityRuntimeSchema.CURRENT_VERSION);
        JsonArray identities = new JsonArray();
        for (PlayerIdentityRecord identity : registry.identities()) {
            identities.add(serializeIdentity(identity));
        }
        root.add(PLAYER_IDENTITIES, identities);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public PlayerIdentityRegistry deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonElement parsed = JsonParser.parseString(json);
            JsonObject root = requireObject(parsed, "player identity root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != PlayerIdentityRuntimeSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported player identity schema version: " + schemaVersion);
            }
            JsonArray identities = requireArray(root, PLAYER_IDENTITIES);
            List<PlayerIdentityRecord> records = new ArrayList<>();
            for (JsonElement element : identities) {
                records.add(deserializeIdentity(requireObject(element, "player identity record")));
            }
            return PlayerIdentityRegistry.of(records);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt player identity persistence", exception);
        }
    }

    private JsonObject serializeIdentity(PlayerIdentityRecord identity) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, identity.schemaVersion());
        object.addProperty(MINECRAFT_UUID, identity.minecraftUuid().toString());
        object.addProperty(PLAYER_IDENTITY_ID, identity.identityId().value());
        object.addProperty(STARTING_SCENARIO, identity.startingScenarioId().value());
        object.addProperty(CAREER_PROFILE, identity.careerProfile().serializedName());
        object.addProperty(SETTLEMENT_ID, identity.settlementId());
        addNullable(object, COMMERCIAL_PROPERTY_ID, identity.commercialPropertyId().map(CommercialPropertyId::value));
        addNullable(object, BUSINESS_ID, identity.businessId().map(BusinessId::value));
        addNullable(object, OWNERSHIP_ENTITY_ID, identity.ownershipEntityId().map(OwnershipEntityId::value));
        addNullable(object, FAMILY_ID, identity.familyId().map(FamilyId::value));
        object.addProperty(CREATION_TIMESTAMP, identity.creationTimestamp().toString());
        return object;
    }

    private PlayerIdentityRecord deserializeIdentity(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != PlayerIdentityRuntimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported player identity record schema version: " + schemaVersion);
        }
        return new PlayerIdentityRecord(
                parseUuid(requireString(object, MINECRAFT_UUID)),
                new PlayerIdentityId(requireString(object, PLAYER_IDENTITY_ID)),
                new StartingScenarioId(requireString(object, STARTING_SCENARIO)),
                CareerProfile.fromSerializedName(requireString(object, CAREER_PROFILE)),
                requireString(object, SETTLEMENT_ID),
                optionalString(object, COMMERCIAL_PROPERTY_ID).map(CommercialPropertyId::new),
                optionalString(object, BUSINESS_ID).map(BusinessId::new),
                optionalString(object, OWNERSHIP_ENTITY_ID).map(OwnershipEntityId::new),
                optionalString(object, FAMILY_ID).map(FamilyId::new),
                parseInstant(requireString(object, CREATION_TIMESTAMP)),
                schemaVersion
        );
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void addNullable(JsonObject object, String fieldName, Optional<String> value) {
        object.add(fieldName, value.<JsonElement>map(JsonPrimitive::new).orElse(JsonNull.INSTANCE));
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
            throw new IllegalArgumentException("Player identity field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Player identity field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Player identity field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Player identity field must not be blank: " + fieldName);
        }
        return value;
    }

    private static Optional<String> optionalString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (element.isJsonNull()) {
            return Optional.empty();
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Player identity field must be null or a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Player identity optional field must not be blank: " + fieldName);
        }
        return Optional.of(value);
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing player identity field: " + fieldName);
        }
        return element;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Minecraft UUID: " + value, exception);
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid player identity creation timestamp: " + value, exception);
        }
    }
}
