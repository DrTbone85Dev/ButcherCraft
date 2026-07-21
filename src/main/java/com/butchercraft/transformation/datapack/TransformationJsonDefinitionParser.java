package com.butchercraft.transformation.datapack;

import com.butchercraft.transformation.serialization.SerializedTransformationAmount;
import com.butchercraft.transformation.serialization.SerializedTransformationDefinition;
import com.butchercraft.transformation.serialization.SerializedTransformationDuration;
import com.butchercraft.transformation.serialization.SerializedTransformationInput;
import com.butchercraft.transformation.serialization.SerializedTransformationOutput;
import com.butchercraft.transformation.serialization.SerializedTransformationYield;
import com.butchercraft.transformation.serialization.TransformationSchemaVersion;
import com.butchercraft.transformation.serialization.TransformationSerializedFieldNames;
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
 * JSON parser that maps datapack files onto the canonical serialized transformation schema.
 */
public final class TransformationJsonDefinitionParser {
    public SerializedTransformationDefinition parse(JsonElement json) {
        JsonObject object = object(json, "root");
        return new SerializedTransformationDefinition(
                new TransformationSchemaVersion(requiredInt(object, TransformationSerializedFieldNames.SCHEMA_VERSION, "root")),
                requiredString(object, TransformationSerializedFieldNames.ID, "root"),
                requiredString(object, TransformationSerializedFieldNames.DISPLAY_NAME, "root"),
                optionalString(object, TransformationSerializedFieldNames.REQUIRED_CAPABILITY, "root"),
                inputs(requiredArray(object, TransformationSerializedFieldNames.INPUTS, "root")),
                outputs(requiredArray(object, TransformationSerializedFieldNames.OUTPUTS, "root")),
                duration(requiredObject(object, TransformationSerializedFieldNames.DURATION, "root")),
                yieldRatio(requiredObject(object, TransformationSerializedFieldNames.YIELD, "root")),
                metadata(optionalObject(object, TransformationSerializedFieldNames.METADATA, "root"))
        );
    }

    private static List<SerializedTransformationInput> inputs(JsonArray array) {
        List<SerializedTransformationInput> inputs = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            inputs.add(new SerializedTransformationInput(amount(object(array.get(index), "inputs[" + index + "]"))));
        }
        return inputs;
    }

    private static List<SerializedTransformationOutput> outputs(JsonArray array) {
        List<SerializedTransformationOutput> outputs = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonObject output = object(array.get(index), "outputs[" + index + "]");
            outputs.add(new SerializedTransformationOutput(
                    amount(output),
                    requiredString(output, TransformationSerializedFieldNames.CLASSIFICATION, "outputs[" + index + "]")
            ));
        }
        return outputs;
    }

    private static SerializedTransformationAmount amount(JsonObject object) {
        return new SerializedTransformationAmount(
                requiredString(object, TransformationSerializedFieldNames.PRODUCT_ID, "amount"),
                requiredLong(object, TransformationSerializedFieldNames.QUANTITY, "amount"),
                requiredString(object, TransformationSerializedFieldNames.UNIT, "amount")
        );
    }

    private static SerializedTransformationDuration duration(JsonObject object) {
        return new SerializedTransformationDuration(requiredLong(object, TransformationSerializedFieldNames.MILLISECONDS, "duration"));
    }

    private static SerializedTransformationYield yieldRatio(JsonObject object) {
        return new SerializedTransformationYield(
                requiredLong(object, TransformationSerializedFieldNames.NUMERATOR, "yield"),
                requiredLong(object, TransformationSerializedFieldNames.DENOMINATOR, "yield")
        );
    }

    private static Map<String, String> metadata(Optional<JsonObject> object) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        object.ifPresent(jsonObject -> {
            for (var entry : jsonObject.entrySet()) {
                JsonElement value = entry.getValue();
                if (!isString(value)) {
                    throw new IllegalArgumentException("metadata." + entry.getKey() + " must be a string");
                }
                metadata.put(entry.getKey(), value.getAsString());
            }
        });
        return metadata;
    }

    private static JsonObject object(JsonElement element, String path) {
        Objects.requireNonNull(element, path);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(path + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject requiredObject(JsonObject object, String field, String path) {
        JsonElement value = required(object, field, path);
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(path + "." + field + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static Optional<JsonObject> optionalObject(JsonObject object, String field, String path) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(path + "." + field + " must be an object");
        }
        return Optional.of(value.getAsJsonObject());
    }

    private static JsonArray requiredArray(JsonObject object, String field, String path) {
        JsonElement value = required(object, field, path);
        if (!value.isJsonArray()) {
            throw new IllegalArgumentException(path + "." + field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String requiredString(JsonObject object, String field, String path) {
        JsonElement value = required(object, field, path);
        if (!isString(value)) {
            throw new IllegalArgumentException(path + "." + field + " must be a string");
        }
        return value.getAsString();
    }

    private static Optional<String> optionalString(JsonObject object, String field, String path) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            return Optional.empty();
        }
        if (!isString(value)) {
            throw new IllegalArgumentException(path + "." + field + " must be a string");
        }
        return Optional.of(value.getAsString());
    }

    private static int requiredInt(JsonObject object, String field, String path) {
        long value = requiredLong(object, field, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + "." + field + " must fit in an int");
        }
        return (int) value;
    }

    private static long requiredLong(JsonObject object, String field, String path) {
        JsonElement value = required(object, field, path);
        if (!isWholeNumber(value)) {
            throw new IllegalArgumentException(path + "." + field + " must be an integer");
        }
        return value.getAsLong();
    }

    private static JsonElement required(JsonObject object, String field, String path) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException(path + "." + field + " is required");
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
