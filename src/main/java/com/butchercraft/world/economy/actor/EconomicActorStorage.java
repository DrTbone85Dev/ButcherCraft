package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.IndustryId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class EconomicActorStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String ACTORS = "actors";
    private static final String ID = "id";
    private static final String DISPLAY_NAME = "display_name";
    private static final String ACTOR_TYPE = "actor_type";
    private static final String INDUSTRY_ID = "industry_id";
    private static final String CAPABILITIES = "capabilities";
    private static final String RELATIONSHIPS = "relationships";
    private static final String GOOD_ID = "good_id";
    private static final String GOOD_ROLE = "good_role";
    private static final String SUPPORTED_INDUSTRY_IDS = "supported_industry_ids";
    private static final String DEPENDS_ON_ACTOR_ID = "depends_on_actor_id";

    private final Path filePath;
    private final GoodRegistry goodRegistry;
    private final Set<IndustryId> knownIndustries;

    public EconomicActorStorage(
            Path filePath,
            GoodRegistry goodRegistry,
            Collection<IndustryId> knownIndustries
    ) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.goodRegistry = Objects.requireNonNull(goodRegistry, "goodRegistry");
        this.knownIndustries = Set.copyOf(Objects.requireNonNull(knownIndustries, "knownIndustries"));
    }

    public Path filePath() {
        return filePath;
    }

    public EconomicActorRegistry load() {
        if (!Files.exists(filePath)) {
            return EconomicActorRegistry.empty(goodRegistry, knownIndustries);
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load economic actors from " + filePath, exception);
        }
    }

    public void save(EconomicActorRegistry registry) {
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
            throw new UncheckedIOException("Failed to save economic actors to " + filePath, exception);
        }
    }

    public String serialize(EconomicActorRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        EconomicActorRegistry validated = EconomicActorRegistry.of(
                registry.definitions(),
                goodRegistry,
                knownIndustries
        );
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, EconomicActorSchema.CURRENT_VERSION);
        JsonArray actors = new JsonArray();
        validated.definitions().forEach(definition -> actors.add(serializeDefinition(definition)));
        root.add(ACTORS, actors);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public EconomicActorRegistry deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "economic actor root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != EconomicActorSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported economic actor schema version: " + schemaVersion);
            }

            EconomicActorRegistryBuilder builder = EconomicActorRegistry.builder(goodRegistry, knownIndustries);
            for (JsonElement element : requireArray(root, ACTORS)) {
                builder.register(deserializeDefinition(requireObject(element, "economic actor definition")));
            }
            return builder.build();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt economic actor persistence", exception);
        }
    }

    private JsonObject serializeDefinition(EconomicActorDefinition definition) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, definition.schemaVersion());
        object.addProperty(ID, definition.id().value());
        object.addProperty(DISPLAY_NAME, definition.displayName());
        object.addProperty(ACTOR_TYPE, definition.actorType().serializedName());
        object.addProperty(INDUSTRY_ID, definition.industryId().value());

        JsonArray capabilities = new JsonArray();
        definition.capabilities().stream()
                .sorted(Comparator.comparing(ActorCapability::serializedName))
                .forEach(capability -> capabilities.add(capability.serializedName()));
        object.add(CAPABILITIES, capabilities);

        JsonArray relationships = new JsonArray();
        definition.relationships().forEach(relationship -> relationships.add(serializeRelationship(relationship)));
        object.add(RELATIONSHIPS, relationships);
        return object;
    }

    private JsonObject serializeRelationship(ActorRelationship relationship) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, relationship.schemaVersion());
        object.addProperty(GOOD_ID, relationship.goodId().value());
        object.addProperty(GOOD_ROLE, relationship.goodRole().serializedName());
        JsonArray supportedIndustries = new JsonArray();
        relationship.supportedIndustryIds().forEach(industryId -> supportedIndustries.add(industryId.value()));
        object.add(SUPPORTED_INDUSTRY_IDS, supportedIndustries);
        relationship.dependsOnActorId().ifPresent(actorId -> object.addProperty(DEPENDS_ON_ACTOR_ID, actorId.value()));
        return object;
    }

    private EconomicActorDefinition deserializeDefinition(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != EconomicActorSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported economic actor definition schema version: " + schemaVersion);
        }

        Set<ActorCapability> capabilities = deserializeCapabilities(requireArray(object, CAPABILITIES));
        List<ActorRelationship> relationships = requireArray(object, RELATIONSHIPS).asList().stream()
                .map(element -> deserializeRelationship(requireObject(element, "actor relationship")))
                .toList();
        return new EconomicActorDefinition(
                ActorId.of(requireString(object, ID)),
                requireString(object, DISPLAY_NAME),
                ActorType.fromSerializedName(requireString(object, ACTOR_TYPE)),
                IndustryId.of(requireString(object, INDUSTRY_ID)),
                capabilities,
                relationships,
                schemaVersion
        );
    }

    private Set<ActorCapability> deserializeCapabilities(JsonArray array) {
        Set<ActorCapability> capabilities = EnumSet.noneOf(ActorCapability.class);
        for (JsonElement element : array) {
            ActorCapability capability = ActorCapability.fromSerializedName(
                    requireString(element, "actor capability")
            );
            if (!capabilities.add(capability)) {
                throw new IllegalArgumentException("Duplicate economic actor capability: "
                        + capability.serializedName());
            }
        }
        return capabilities;
    }

    private ActorRelationship deserializeRelationship(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != EconomicActorSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported actor relationship schema version: " + schemaVersion);
        }
        JsonElement dependencyElement = object.get(DEPENDS_ON_ACTOR_ID);
        Optional<ActorId> dependency = dependencyElement == null
                ? Optional.empty()
                : Optional.of(ActorId.of(requireString(dependencyElement, DEPENDS_ON_ACTOR_ID)));
        Set<IndustryId> supportedIndustries = new LinkedHashSet<>();
        for (JsonElement element : requireArray(object, SUPPORTED_INDUSTRY_IDS)) {
            IndustryId industryId = IndustryId.of(requireString(element, "supported industry id"));
            if (!supportedIndustries.add(industryId)) {
                throw new IllegalArgumentException("Duplicate supported industry id: " + industryId.value());
            }
        }
        return new ActorRelationship(
                GoodId.of(requireString(object, GOOD_ID)),
                GoodRole.fromSerializedName(requireString(object, GOOD_ROLE)),
                supportedIndustries,
                dependency,
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

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Economic actor field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Economic actor field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static String requireString(JsonObject object, String fieldName) {
        return requireString(requireField(object, fieldName), fieldName);
    }

    private static String requireString(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Economic actor field must be a string: " + label);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Economic actor field must not be blank: " + label);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing economic actor field: " + fieldName);
        }
        return element;
    }
}
