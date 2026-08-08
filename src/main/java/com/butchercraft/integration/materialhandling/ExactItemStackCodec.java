package com.butchercraft.integration.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExactItemStackCodec {
    public static final String ENCODING_IDENTITY = "butchercraft:item_stack_codec/v1/registry_aware_json";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public WorkstationEndpointStackPayload encode(HolderLookup.Provider registries, ItemStack stack) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot encode an empty transfer stack");
        DataResult<JsonElement> result = ItemStack.CODEC.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE),
                stack
        );
        JsonElement encoded = result.result().orElseThrow(() -> new IllegalArgumentException(
                "Failed to encode exact ItemStack: " + errorMessage(result)
        ));
        byte[] bytes = GSON.toJson(canonicalize(encoded)).getBytes(StandardCharsets.UTF_8);
        return WorkstationEndpointStackPayload.create(
                ENCODING_IDENTITY,
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getCount(),
                bytes
        );
    }

    public ItemStack decode(HolderLookup.Provider registries, WorkstationEndpointStackPayload payload) {
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(payload, "payload");
        if (!ENCODING_IDENTITY.equals(payload.encodingIdentity())) {
            throw new IllegalArgumentException("Unsupported exact ItemStack encoding: " + payload.encodingIdentity());
        }
        JsonElement json;
        try {
            json = JsonParser.parseString(new String(payload.decodedStack(), StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Exact ItemStack payload is not valid JSON", exception);
        }
        DataResult<ItemStack> result = ItemStack.CODEC.parse(
                registries.createSerializationContext(JsonOps.INSTANCE),
                json
        );
        ItemStack stack = result.result().orElseThrow(() -> new IllegalArgumentException(
                "Failed to decode exact ItemStack: " + errorMessage(result)
        ));
        if (stack.isEmpty()
                || stack.getCount() != payload.count()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(payload.itemIdentity())) {
            throw new IllegalArgumentException("Decoded ItemStack does not match persisted stack metadata");
        }
        WorkstationEndpointStackPayload verified = encode(registries, stack);
        if (!verified.equals(payload)) {
            throw new IllegalArgumentException("Decoded ItemStack failed canonical round-trip verification");
        }
        return stack;
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) array.add(canonicalize(child));
            return array;
        }
        if (element.isJsonObject()) {
            JsonObject object = new JsonObject();
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(element.getAsJsonObject().entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            entries.forEach(entry -> object.add(entry.getKey(), canonicalize(entry.getValue())));
            return object;
        }
        return element.deepCopy();
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error().map(error -> error.message()).orElse("unknown codec failure");
    }
}
