package com.butchercraft.transformation.datapack;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reload-safe holder for the active transformation registry.
 */
public final class TransformationRegistryService {
    private static final AtomicReference<TransformationRegistry> CURRENT =
            new AtomicReference<>(loadBundledRegistry());

    private TransformationRegistryService() {
    }

    public static TransformationRegistry currentRegistry() {
        return CURRENT.get();
    }

    public static void replaceRegistry(TransformationRegistry registry) {
        CURRENT.set(Objects.requireNonNull(registry, "registry"));
    }

    public static TransformationDatapackLoadResult load(Map<String, JsonElement> resources) {
        return newLoader().load(resources);
    }

    public static TransformationDatapackLoadResult replaceFromDatapack(Map<String, JsonElement> resources) {
        TransformationDatapackLoadResult result = load(resources);
        result.registry().ifPresent(TransformationRegistryService::replaceRegistry);
        return result;
    }

    public static void resetToBundledRegistry() {
        replaceRegistry(BuiltInTransformationRegistry.builtInRegistry());
    }

    public static TransformationRegistry loadBundledRegistry() {
        TransformationDatapackLoadResult result = newLoader().load(bundledResources());
        if (!result.succeeded()) {
            throw new IllegalStateException("Built-in transformation datapack resources are invalid:"
                    + System.lineSeparator()
                    + result.describeErrors());
        }
        return result.registry().orElseThrow();
    }

    public static Map<String, JsonElement> bundledResources() {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (String resourcePath : BuiltInTransformationRegistry.BUILT_IN_RESOURCE_PATHS) {
            try (var stream = TransformationRegistryService.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing built-in transformation resource " + resourcePath);
                }
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    resources.put(resourcePath, JsonParser.parseReader(reader));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read built-in transformation resource " + resourcePath, exception);
            }
        }
        return resources;
    }

    private static TransformationDatapackLoader newLoader() {
        return new TransformationDatapackLoader(BuiltInProductRegistry.builtInRegistry(), knownCapabilities());
    }

    private static Set<EngineId> knownCapabilities() {
        return Set.of(
                BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING,
                BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_BANDSAW
        );
    }
}
