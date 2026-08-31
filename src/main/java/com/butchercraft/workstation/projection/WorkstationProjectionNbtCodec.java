package com.butchercraft.workstation.projection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class WorkstationProjectionNbtCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private WorkstationProjectionNbtCodec() {
    }

    static String encode(CompoundTag tag) {
        return GSON.toJson(encodeTag(Objects.requireNonNull(tag, "tag")));
    }

    static CompoundTag decode(String json) {
        JsonElement root;
        try {
            root = com.google.gson.JsonParser.parseString(Objects.requireNonNull(json, "json"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Durable Workstation block-entity projection is invalid JSON", exception);
        }
        Tag decoded = decodeTag(requireObject(root, "block-entity projection"));
        if (!(decoded instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Durable Workstation block-entity projection is not a compound tag");
        }
        if (!encode(compound).equals(json)) {
            throw new IllegalArgumentException("Durable Workstation block-entity projection is not canonical");
        }
        return compound;
    }

    static JsonElement encodedElement(String json) {
        decode(json);
        return com.google.gson.JsonParser.parseString(json);
    }

    private static JsonElement encodeTag(Tag tag) {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("tag_type", tag.getId());
        if (tag instanceof CompoundTag compound) {
            JsonObject values = new JsonObject();
            compound.getAllKeys().stream().sorted().forEach(key ->
                    values.add(key, encodeTag(Objects.requireNonNull(compound.get(key), "compoundTagValue"))));
            encoded.add("value", values);
        } else if (tag instanceof CollectionTag<?> collection) {
            JsonArray values = new JsonArray();
            for (Tag value : collection) values.add(encodeTag(value));
            encoded.add("value", values);
        } else {
            encoded.addProperty("snbt", tag.toString());
        }
        return encoded;
    }

    private static Tag decodeTag(JsonObject encoded) {
        int type = require(encoded, "tag_type").getAsInt();
        if (type == Tag.TAG_COMPOUND) {
            CompoundTag compound = new CompoundTag();
            requireObject(require(encoded, "value"), "compound value").entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> compound.put(
                            entry.getKey(), decodeTag(requireObject(entry.getValue(), "compound member"))));
            return compound;
        }
        if (type == Tag.TAG_LIST) {
            ListTag list = new ListTag();
            requireArray(require(encoded, "value"), "list value")
                    .forEach(value -> list.add(decodeTag(requireObject(value, "list member"))));
            return list;
        }
        if (type == Tag.TAG_BYTE_ARRAY) {
            List<Byte> values = new ArrayList<>();
            requireArray(require(encoded, "value"), "byte-array value").forEach(value ->
                    values.add(((ByteTag) decodeTag(requireObject(value, "byte-array member"))).getAsByte()));
            byte[] array = new byte[values.size()];
            for (int index = 0; index < values.size(); index++) array[index] = values.get(index);
            return new ByteArrayTag(array);
        }
        if (type == Tag.TAG_INT_ARRAY) {
            List<Integer> values = new ArrayList<>();
            requireArray(require(encoded, "value"), "int-array value").forEach(value ->
                    values.add(((IntTag) decodeTag(requireObject(value, "int-array member"))).getAsInt()));
            return new IntArrayTag(values);
        }
        if (type == Tag.TAG_LONG_ARRAY) {
            List<Long> values = new ArrayList<>();
            requireArray(require(encoded, "value"), "long-array value").forEach(value ->
                    values.add(((LongTag) decodeTag(requireObject(value, "long-array member"))).getAsLong()));
            return new LongArrayTag(values);
        }
        try {
            CompoundTag wrapper = TagParser.parseTag("{value:" + require(encoded, "snbt").getAsString() + "}");
            Tag value = wrapper.get("value");
            if (value == null || value.getId() != type) {
                throw new IllegalArgumentException("Durable Workstation projection NBT type mismatch");
            }
            return value;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid durable Workstation projection NBT", exception);
        }
    }

    private static JsonElement require(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Durable Workstation projection is missing " + key);
        }
        return value;
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Durable Workstation " + label + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String label) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Durable Workstation " + label + " must be an array");
        }
        return element.getAsJsonArray();
    }
}
