package com.butchercraft.world.goods;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class GoodStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String GOODS = "goods";
    private static final String TRANSFORMATIONS = "transformations";
    private static final String ID = "id";
    private static final String DISPLAY_NAME = "display_name";
    private static final String CATEGORY = "category";
    private static final String INDUSTRY_ID = "industry_id";
    private static final String SOURCE_INDUSTRY_ID = "source_industry_id";
    private static final String UNIT_OF_MEASURE = "unit_of_measure";
    private static final String STACKABILITY = "stackability";
    private static final String ECONOMIC_FLAGS = "economic_flags";
    private static final String STORAGE_REQUIREMENT = "storage_requirement";
    private static final String TRANSPORT_REQUIREMENT = "transport_requirement";
    private static final String ITEM_MAPPINGS = "item_mappings";
    private static final String PROVIDER_ID = "provider_id";
    private static final String ITEM_ID = "item_id";
    private static final String COMMODITY_TYPE = "commodity_type";
    private static final String PRODUCT_STAGE = "product_stage";
    private static final String INPUT_GOOD_ID = "input_good_id";
    private static final String OUTPUT_GOOD_ID = "output_good_id";
    private static final String YIELD_NUMERATOR = "yield_numerator";
    private static final String YIELD_DENOMINATOR = "yield_denominator";
    private static final String OWNING_INDUSTRY_ID = "owning_industry_id";

    private final Path filePath;
    private final Set<IndustryId> knownIndustries;

    public GoodStorage(Path filePath, Collection<IndustryId> knownIndustries) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.knownIndustries = Set.copyOf(Objects.requireNonNull(knownIndustries, "knownIndustries"));
    }

    public Path filePath() {
        return filePath;
    }

    public GoodRegistry load() {
        if (!Files.exists(filePath)) {
            return GoodRegistry.empty(knownIndustries);
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load goods from " + filePath, exception);
        }
    }

    public void save(GoodRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (!registry.knownIndustries().equals(knownIndustries)) {
            throw new IllegalArgumentException("Good registry industry catalog does not match storage catalog");
        }
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(registry), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save goods to " + filePath, exception);
        }
    }

    public String serialize(GoodRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        if (!registry.knownIndustries().equals(knownIndustries)) {
            throw new IllegalArgumentException("Good registry industry catalog does not match storage catalog");
        }
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, GoodSchema.CURRENT_VERSION);

        JsonArray goods = new JsonArray();
        for (GoodDefinition definition : registry.definitions()) {
            goods.add(serializeDefinition(definition));
        }
        root.add(GOODS, goods);

        JsonArray transformations = new JsonArray();
        for (GoodTransformation transformation : registry.transformations()) {
            transformations.add(serializeTransformation(transformation));
        }
        root.add(TRANSFORMATIONS, transformations);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public GoodRegistry deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "goods root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != GoodSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported goods schema version: " + schemaVersion);
            }

            GoodRegistryBuilder builder = GoodRegistry.builder(knownIndustries);
            for (JsonElement element : requireArray(root, GOODS)) {
                builder.register(deserializeDefinition(requireObject(element, "good definition")));
            }
            for (JsonElement element : requireArray(root, TRANSFORMATIONS)) {
                builder.registerTransformation(deserializeTransformation(requireObject(element, "good transformation")));
            }
            return builder.build();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt goods persistence", exception);
        }
    }

    private JsonObject serializeDefinition(GoodDefinition definition) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, definition.schemaVersion());
        object.addProperty(ID, definition.id().value());
        object.addProperty(DISPLAY_NAME, definition.displayName());
        object.addProperty(CATEGORY, definition.category().serializedName());
        object.addProperty(INDUSTRY_ID, definition.industryId().value());
        object.addProperty(UNIT_OF_MEASURE, definition.unitOfMeasure().serializedName());
        object.addProperty(STACKABILITY, definition.stackability().serializedName());

        JsonArray economicFlags = new JsonArray();
        definition.economicFlags().stream()
                .sorted(Comparator.comparing(EconomicFlag::serializedName))
                .forEach(flag -> economicFlags.add(flag.serializedName()));
        object.add(ECONOMIC_FLAGS, economicFlags);
        object.addProperty(STORAGE_REQUIREMENT, definition.storageRequirement().serializedName());
        object.addProperty(TRANSPORT_REQUIREMENT, definition.transportRequirement().serializedName());

        JsonArray itemMappings = new JsonArray();
        for (ItemMappingMetadata mapping : definition.itemMappings()) {
            JsonObject mappingObject = new JsonObject();
            mappingObject.addProperty(PROVIDER_ID, mapping.providerId().value());
            mappingObject.addProperty(ITEM_ID, mapping.itemId().value());
            itemMappings.add(mappingObject);
        }
        object.add(ITEM_MAPPINGS, itemMappings);

        if (definition instanceof CommodityDefinition commodity) {
            object.addProperty(COMMODITY_TYPE, commodity.commodityType().serializedName());
        } else if (definition instanceof ProductDefinition product) {
            object.addProperty(SOURCE_INDUSTRY_ID, product.sourceIndustryId().value());
            object.addProperty(PRODUCT_STAGE, product.transformationStage().serializedName());
        } else {
            throw new IllegalArgumentException("Unsupported good definition type: " + definition.getClass().getName());
        }
        return object;
    }

    private GoodDefinition deserializeDefinition(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != GoodSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported good definition schema version: " + schemaVersion);
        }

        GoodId id = GoodId.of(requireString(object, ID));
        String displayName = requireString(object, DISPLAY_NAME);
        GoodCategory category = GoodCategory.fromSerializedName(requireString(object, CATEGORY));
        IndustryId industryId = IndustryId.of(requireString(object, INDUSTRY_ID));
        UnitOfMeasure unit = UnitOfMeasure.fromSerializedName(requireString(object, UNIT_OF_MEASURE));
        Stackability stackability = Stackability.fromSerializedName(requireString(object, STACKABILITY));
        Set<EconomicFlag> economicFlags = deserializeEconomicFlags(requireArray(object, ECONOMIC_FLAGS));
        StorageRequirement storage = StorageRequirement.fromSerializedName(requireString(object, STORAGE_REQUIREMENT));
        TransportRequirement transport = TransportRequirement.fromSerializedName(requireString(object, TRANSPORT_REQUIREMENT));
        Set<ItemMappingMetadata> itemMappings = deserializeItemMappings(requireArray(object, ITEM_MAPPINGS));

        return switch (category) {
            case COMMODITY -> new CommodityDefinition(
                    id,
                    displayName,
                    industryId,
                    unit,
                    stackability,
                    economicFlags,
                    storage,
                    transport,
                    itemMappings,
                    schemaVersion,
                    CommodityType.fromSerializedName(requireString(object, COMMODITY_TYPE))
            );
            case PRODUCT -> new ProductDefinition(
                    id,
                    displayName,
                    industryId,
                    unit,
                    stackability,
                    economicFlags,
                    storage,
                    transport,
                    itemMappings,
                    schemaVersion,
                    IndustryId.of(requireString(object, SOURCE_INDUSTRY_ID)),
                    ProductStage.fromSerializedName(requireString(object, PRODUCT_STAGE))
            );
        };
    }

    private Set<EconomicFlag> deserializeEconomicFlags(JsonArray array) {
        Set<EconomicFlag> flags = new LinkedHashSet<>();
        for (JsonElement element : array) {
            EconomicFlag flag = EconomicFlag.fromSerializedName(requireString(element, "economic flag"));
            if (!flags.add(flag)) {
                throw new IllegalArgumentException("Duplicate economic flag: " + flag.serializedName());
            }
        }
        return flags;
    }

    private Set<ItemMappingMetadata> deserializeItemMappings(JsonArray array) {
        Set<ItemMappingMetadata> mappings = new LinkedHashSet<>();
        for (JsonElement element : array) {
            JsonObject object = requireObject(element, "item mapping");
            ItemMappingMetadata mapping = ItemMappingMetadata.of(
                    requireString(object, PROVIDER_ID),
                    requireString(object, ITEM_ID)
            );
            if (!mappings.add(mapping)) {
                throw new IllegalArgumentException("Duplicate item mapping: " + mapping);
            }
        }
        return mappings;
    }

    private JsonObject serializeTransformation(GoodTransformation transformation) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, transformation.schemaVersion());
        object.addProperty(INPUT_GOOD_ID, transformation.inputGoodId().value());
        object.addProperty(OUTPUT_GOOD_ID, transformation.outputGoodId().value());
        object.addProperty(YIELD_NUMERATOR, transformation.yieldRatio().numerator());
        object.addProperty(YIELD_DENOMINATOR, transformation.yieldRatio().denominator());
        object.addProperty(OWNING_INDUSTRY_ID, transformation.owningIndustryId().value());
        return object;
    }

    private GoodTransformation deserializeTransformation(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != GoodSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported good transformation schema version: " + schemaVersion);
        }
        return new GoodTransformation(
                GoodId.of(requireString(object, INPUT_GOOD_ID)),
                GoodId.of(requireString(object, OUTPUT_GOOD_ID)),
                new GoodYieldRatio(
                        requireLong(object, YIELD_NUMERATOR),
                        requireLong(object, YIELD_DENOMINATOR)
                ),
                IndustryId.of(requireString(object, OWNING_INDUSTRY_ID)),
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
            throw new IllegalArgumentException("Goods field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Goods field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static long requireLong(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Goods field must be a number: " + fieldName);
        }
        return element.getAsLong();
    }

    private static String requireString(JsonObject object, String fieldName) {
        return requireString(requireField(object, fieldName), fieldName);
    }

    private static String requireString(JsonElement element, String label) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Goods field must be a string: " + label);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Goods field must not be blank: " + label);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing goods field: " + fieldName);
        }
        return element;
    }
}
