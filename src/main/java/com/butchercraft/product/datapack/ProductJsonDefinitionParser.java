package com.butchercraft.product.datapack;

import com.butchercraft.product.serialization.ProductSchemaVersion;
import com.butchercraft.product.serialization.ProductSerializedFieldNames;
import com.butchercraft.product.serialization.SerializedProductDefinition;
import com.butchercraft.product.serialization.SerializedProductPackagingMetadata;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * JSON parser that maps datapack files onto the canonical serialized product schema.
 */
public final class ProductJsonDefinitionParser {
    public SerializedProductDefinition parse(JsonElement json) {
        JsonObject object = object(json, "root", ProductDatapackErrorCode.MALFORMED_JSON);
        return new SerializedProductDefinition(
                new ProductSchemaVersion(requiredInt(object, ProductSerializedFieldNames.SCHEMA_VERSION, "root",
                        ProductDatapackErrorCode.MALFORMED_JSON)),
                requiredString(object, ProductSerializedFieldNames.ID, "root", ProductDatapackErrorCode.MISSING_ID),
                requiredString(object, ProductSerializedFieldNames.DISPLAY_NAME, "root",
                        ProductDatapackErrorCode.MISSING_DISPLAY_NAME),
                requiredString(object, ProductSerializedFieldNames.CATEGORY, "root",
                        ProductDatapackErrorCode.UNKNOWN_CATEGORY),
                requiredString(object, ProductSerializedFieldNames.DEFAULT_QUANTITY_UNIT, "root",
                        ProductDatapackErrorCode.UNKNOWN_QUANTITY_UNIT),
                tags(requiredArray(object, ProductSerializedFieldNames.TAGS, "root",
                        ProductDatapackErrorCode.MALFORMED_TAGS)),
                packaging(optionalObject(object, ProductSerializedFieldNames.PACKAGING, "root",
                        ProductDatapackErrorCode.MALFORMED_PACKAGING_METADATA)),
                metadata(optionalObject(object, ProductSerializedFieldNames.METADATA, "root",
                        ProductDatapackErrorCode.MALFORMED_METADATA))
        );
    }

    private static Optional<SerializedProductPackagingMetadata> packaging(Optional<JsonObject> object) {
        return object.map(jsonObject -> new SerializedProductPackagingMetadata(
                requiredString(
                        jsonObject,
                        ProductSerializedFieldNames.PACKAGING_DEFINITION,
                        ProductSerializedFieldNames.PACKAGING,
                        ProductDatapackErrorCode.MALFORMED_PACKAGING_METADATA
                ),
                requiredString(
                        jsonObject,
                        ProductSerializedFieldNames.PACKAGING_SOURCE_PRODUCT,
                        ProductSerializedFieldNames.PACKAGING,
                        ProductDatapackErrorCode.MALFORMED_PACKAGING_METADATA
                )
        ));
    }

    private static List<String> tags(JsonArray array) {
        List<String> tags = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            if (!isString(value)) {
                throw new ProductDatapackParsingException(
                        ProductDatapackErrorCode.MALFORMED_TAGS,
                        "tags[" + index + "] must be a string"
                );
            }
            tags.add(value.getAsString());
        }
        return tags;
    }

    private static Map<String, String> metadata(Optional<JsonObject> object) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        object.ifPresent(jsonObject -> {
            for (var entry : jsonObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (!isString(value)) {
                    throw new ProductDatapackParsingException(
                            ProductDatapackErrorCode.MALFORMED_METADATA,
                            "metadata." + entry.getKey() + " must be a string"
                    );
                }
                metadata.put(entry.getKey(), value.getAsString());
            }
        });
        return metadata;
    }

    private static JsonObject object(JsonElement element, String path, ProductDatapackErrorCode code) {
        Objects.requireNonNull(element, path);
        if (!element.isJsonObject()) {
            throw new ProductDatapackParsingException(code, path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static Optional<JsonObject> optionalObject(
            JsonObject object,
            String field,
            String path,
            ProductDatapackErrorCode code
    ) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonObject()) {
            throw new ProductDatapackParsingException(code, path + "." + field + " must be an object");
        }
        return Optional.of(value.getAsJsonObject());
    }

    private static JsonArray requiredArray(
            JsonObject object,
            String field,
            String path,
            ProductDatapackErrorCode code
    ) {
        JsonElement value = required(object, field, path, code);
        if (!value.isJsonArray()) {
            throw new ProductDatapackParsingException(code, path + "." + field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requiredString(
            JsonObject object,
            String field,
            String path,
            ProductDatapackErrorCode code
    ) {
        JsonElement value = required(object, field, path, code);
        if (!isString(value)) {
            throw new ProductDatapackParsingException(code, path + "." + field + " must be a string");
        }
        return value.getAsString();
    }

    private static int requiredInt(
            JsonObject object,
            String field,
            String path,
            ProductDatapackErrorCode code
    ) {
        long value = requiredLong(object, field, path, code);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ProductDatapackParsingException(code, path + "." + field + " must fit in an int");
        }
        return (int) value;
    }

    private static long requiredLong(
            JsonObject object,
            String field,
            String path,
            ProductDatapackErrorCode code
    ) {
        JsonElement value = required(object, field, path, code);
        if (!isWholeNumber(value)) {
            throw new ProductDatapackParsingException(code, path + "." + field + " must be an integer");
        }
        return value.getAsLong();
    }

    private static JsonElement required(JsonObject object, String field, String path, ProductDatapackErrorCode code) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new ProductDatapackParsingException(code, path + "." + field + " is required");
        }
        return value;
    }

    private static boolean isString(JsonElement value) {
        return value instanceof JsonPrimitive primitive && primitive.isString();
    }

    private static boolean isWholeNumber(JsonElement value) {
        return value instanceof JsonPrimitive primitive && primitive.isNumber()
                && primitive.getAsString().matches("-?\\d+");
    }
}
