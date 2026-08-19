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
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
        try {
            return Files.readString(Objects.requireNonNull(path, "path"), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read " + label + " from " + path, exception);
        }
    }

    public static void requireNoInterruptedPublication(Path path, String label) {
        Path temporary = temporaryPath(path);
        if (!Files.exists(path) && Files.exists(temporary)) {
            throw new IllegalStateException("Interrupted " + label + " publication requires recovery: " + temporary);
        }
    }

    public static void publish(Path path, String json, String label) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(json, "json");
        Path temporary = temporaryPath(path);
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic replacement is required for " + path, exception);
            }
            if (!Files.readString(path, StandardCharsets.UTF_8).equals(json)) {
                throw new IOException("Published " + label + " failed byte-for-byte read-back verification");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed strict publication of " + label + " to " + path, exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A retained temporary file is visible recovery evidence.
            }
        }
    }

    public static IllegalArgumentException corrupt(String label, RuntimeException cause) {
        return new IllegalArgumentException("Corrupt " + label, Objects.requireNonNull(cause, "cause"));
    }

    private static Path temporaryPath(Path path) {
        return path.resolveSibling(path.getFileName() + ".tmp");
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
