package com.butchercraft.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class StrictJsonPersistence {
    private StrictJsonPersistence() {
    }

    public static Gson gson() {
        return new GsonBuilder()
                .disableHtmlEscaping()
                .serializeNulls()
                .setPrettyPrinting()
                .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
                .create();
    }

    public static String read(Path path, String label) {
        return AtomicFilePublication.readUtf8(Objects.requireNonNull(path, "path"), label);
    }

    public static void requireNoInterruptedPublication(Path path, String label) {
        AtomicFilePublication.requireNoInterruptedPublication(path, label);
    }

    public static void publish(Path path, String json, String label) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(json, "json");
        AtomicFilePublication.publishUtf8(path, json, label);
    }

    public static IllegalArgumentException corrupt(String label, RuntimeException cause) {
        return new IllegalArgumentException("Corrupt " + label, Objects.requireNonNull(cause, "cause"));
    }

    private static final class OptionalTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Optional.class) return null;
            Type genericType = type.getType();
            if (!(genericType instanceof ParameterizedType parameterized)) {
                throw new JsonParseException("Optional persistence fields must retain generic type information");
            }
            TypeAdapter<?> elementAdapter = gson.getAdapter(TypeToken.get(parameterized.getActualTypeArguments()[0]));
            return optionalAdapter(elementAdapter);
        }

        @SuppressWarnings("unchecked")
        private static <T> TypeAdapter<T> optionalAdapter(TypeAdapter<?> rawElementAdapter) {
            TypeAdapter<Object> elementAdapter = (TypeAdapter<Object>) rawElementAdapter;
            return (TypeAdapter<T>) new TypeAdapter<Optional<?>>() {
                @Override
                public void write(JsonWriter out, Optional<?> value) throws IOException {
                    if (value == null || value.isEmpty()) {
                        out.nullValue();
                    } else {
                        elementAdapter.write(out, value.orElseThrow());
                    }
                }

                @Override
                public Optional<?> read(JsonReader in) throws IOException {
                    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                        in.nextNull();
                        return Optional.empty();
                    }
                    return Optional.ofNullable(elementAdapter.read(in));
                }
            };
        }
    }
}
