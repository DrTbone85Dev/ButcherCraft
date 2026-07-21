package com.butchercraft.packaging.datapack;

import com.butchercraft.packaging.serialization.PackagingSchemaVersion;
import com.butchercraft.packaging.serialization.PackagingSerializedFieldNames;
import com.butchercraft.packaging.serialization.SerializedPackagingDefinition;
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
 * JSON parser that maps datapack files onto the canonical serialized packaging schema.
 */
public final class PackagingJsonDefinitionParser {
    public SerializedPackagingDefinition parse(JsonElement json) {
        JsonObject object = object(json, "root", PackagingDatapackErrorCode.MALFORMED_JSON);
        return new SerializedPackagingDefinition(
                new PackagingSchemaVersion(requiredInt(object, PackagingSerializedFieldNames.SCHEMA_VERSION, "root",
                        PackagingDatapackErrorCode.MALFORMED_JSON)),
                requiredString(object, PackagingSerializedFieldNames.ID, "root", PackagingDatapackErrorCode.MISSING_ID),
                requiredString(object, PackagingSerializedFieldNames.DISPLAY_NAME, "root",
                        PackagingDatapackErrorCode.MISSING_DISPLAY_NAME),
                requiredString(object, PackagingSerializedFieldNames.FORMAT, "root",
                        PackagingDatapackErrorCode.UNKNOWN_FORMAT),
                requiredString(object, PackagingSerializedFieldNames.DEFAULT_QUANTITY_UNIT, "root",
                        PackagingDatapackErrorCode.UNKNOWN_QUANTITY_UNIT),
                strings(optionalArray(object, PackagingSerializedFieldNames.REQUIRED_SUPPLY_ITEMS, "root",
                        PackagingDatapackErrorCode.MALFORMED_REQUIRED_SUPPLIES).orElseGet(JsonArray::new),
                        "required_supply_items", PackagingDatapackErrorCode.MALFORMED_REQUIRED_SUPPLIES),
                strings(requiredArray(object, PackagingSerializedFieldNames.COMPATIBLE_CATEGORIES, "root",
                        PackagingDatapackErrorCode.MALFORMED_CATEGORIES), "compatible_categories",
                        PackagingDatapackErrorCode.MALFORMED_CATEGORIES),
                strings(requiredArray(object, PackagingSerializedFieldNames.COMPATIBLE_TAGS, "root",
                        PackagingDatapackErrorCode.MALFORMED_TAGS), "compatible_tags",
                        PackagingDatapackErrorCode.MALFORMED_TAGS),
                metadata(optionalObject(object, PackagingSerializedFieldNames.METADATA, "root",
                        PackagingDatapackErrorCode.MALFORMED_METADATA))
        );
    }

    private static List<String> strings(JsonArray array, String field, PackagingDatapackErrorCode code) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonElement value = array.get(index);
            if (!isString(value)) {
                throw new PackagingDatapackParsingException(code, field + "[" + index + "] must be a string");
            }
            values.add(value.getAsString());
        }
        return values;
    }

    private static Map<String, String> metadata(Optional<JsonObject> object) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        object.ifPresent(jsonObject -> {
            for (var entry : jsonObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (!isString(value)) {
                    throw new PackagingDatapackParsingException(
                            PackagingDatapackErrorCode.MALFORMED_METADATA,
                            "metadata." + entry.getKey() + " must be a string"
                    );
                }
                metadata.put(entry.getKey(), value.getAsString());
            }
        });
        return metadata;
    }

    private static JsonObject object(JsonElement element, String path, PackagingDatapackErrorCode code) {
        Objects.requireNonNull(element, path);
        if (!element.isJsonObject()) {
            throw new PackagingDatapackParsingException(code, path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static Optional<JsonObject> optionalObject(
            JsonObject object,
            String field,
            String path,
            PackagingDatapackErrorCode code
    ) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonObject()) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " must be an object");
        }
        return Optional.of(value.getAsJsonObject());
    }

    private static Optional<JsonArray> optionalArray(
            JsonObject object,
            String field,
            String path,
            PackagingDatapackErrorCode code
    ) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonArray()) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " must be an array");
        }
        return Optional.of(value.getAsJsonArray());
    }

    private static JsonArray requiredArray(
            JsonObject object,
            String field,
            String path,
            PackagingDatapackErrorCode code
    ) {
        JsonElement value = required(object, field, path, code);
        if (!value.isJsonArray()) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requiredString(
            JsonObject object,
            String field,
            String path,
            PackagingDatapackErrorCode code
    ) {
        JsonElement value = required(object, field, path, code);
        if (!isString(value)) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " must be a string");
        }
        return value.getAsString();
    }

    private static int requiredInt(
            JsonObject object,
            String field,
            String path,
            PackagingDatapackErrorCode code
    ) {
        long value = requiredLong(object, field, path, code);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " must fit in an int");
        }
        return (int) value;
    }

    private static long requiredLong(
            JsonObject object,
            String field,
            String path,
            PackagingDatapackErrorCode code
    ) {
        JsonElement value = required(object, field, path, code);
        if (!isWholeNumber(value)) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " must be an integer");
        }
        return value.getAsLong();
    }

    private static JsonElement required(JsonObject object, String field, String path, PackagingDatapackErrorCode code) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new PackagingDatapackParsingException(code, path + "." + field + " is required");
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
