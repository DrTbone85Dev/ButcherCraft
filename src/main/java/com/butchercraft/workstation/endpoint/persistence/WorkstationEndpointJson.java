package com.butchercraft.workstation.endpoint.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Optional;

final class WorkstationEndpointJson {
    private WorkstationEndpointJson() {
    }

    static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return element.getAsJsonObject();
    }

    static JsonArray array(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonArray()) throw new IllegalArgumentException(field + " must be an array");
        return element.getAsJsonArray();
    }

    static String string(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsString();
    }

    static Optional<String> optionalString(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) return Optional.empty();
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException(field + " must be a string");
        return Optional.of(element.getAsString());
    }

    static int integer(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsInt();
    }

    static long longValue(JsonObject object, String field) {
        JsonElement element = object.get(field);
        if (element == null || !element.isJsonPrimitive()) throw new IllegalArgumentException(field + " is required");
        return element.getAsLong();
    }
}
