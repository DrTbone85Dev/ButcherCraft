package com.butchercraft.content;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.datapack.ProductDatapackLoadResult;
import com.butchercraft.product.datapack.ProductDatapackLoader;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.product.definition.ProductRegistry;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationRegistry;
import com.butchercraft.transformation.datapack.TransformationDatapackLoadResult;
import com.butchercraft.transformation.datapack.TransformationDatapackLoader;
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
 * Reload-safe holder for the active product and transformation registries.
 */
public final class ContentSnapshotService {
    private static final AtomicReference<ContentSnapshot> CURRENT =
            new AtomicReference<>(loadBundledSnapshot());

    private ContentSnapshotService() {
    }

    public static ContentSnapshot currentSnapshot() {
        return CURRENT.get();
    }

    public static ProductRegistry currentProductRegistry() {
        return currentSnapshot().products();
    }

    public static TransformationRegistry currentTransformationRegistry() {
        return currentSnapshot().transformations();
    }

    public static void replaceSnapshot(ContentSnapshot snapshot) {
        CURRENT.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public static ContentSnapshotLoadResult load(
            Map<String, JsonElement> productResources,
            Map<String, JsonElement> transformationResources
    ) {
        Objects.requireNonNull(productResources, "productResources");
        Objects.requireNonNull(transformationResources, "transformationResources");

        ProductDatapackLoadResult productResult = newProductLoader().load(productResources);
        if (!productResult.succeeded()) {
            return ContentSnapshotLoadResult.failure(productResult.errors(), java.util.List.of());
        }

        ProductRegistry candidateProducts = productResult.registry().orElseThrow();
        TransformationDatapackLoadResult transformationResult =
                new TransformationDatapackLoader(candidateProducts, knownCapabilities()).load(transformationResources);
        if (!transformationResult.succeeded()) {
            return ContentSnapshotLoadResult.failure(java.util.List.of(), transformationResult.errors());
        }

        return ContentSnapshotLoadResult.success(new ContentSnapshot(
                candidateProducts,
                transformationResult.registry().orElseThrow()
        ));
    }

    public static ContentSnapshotLoadResult replaceFromDatapack(
            Map<String, JsonElement> productResources,
            Map<String, JsonElement> transformationResources
    ) {
        ContentSnapshotLoadResult result = load(productResources, transformationResources);
        result.snapshot().ifPresent(ContentSnapshotService::replaceSnapshot);
        return result;
    }

    public static void resetToBundledSnapshot() {
        replaceSnapshot(loadBundledSnapshot());
    }

    public static ContentSnapshot loadBundledSnapshot() {
        ContentSnapshotLoadResult result = load(bundledProductResources(), bundledTransformationResources());
        if (!result.succeeded()) {
            throw new IllegalStateException("Built-in ButcherCraft datapack resources are invalid:"
                    + System.lineSeparator()
                    + result.describeErrors());
        }
        return result.snapshot().orElseThrow();
    }

    public static ProductRegistry loadBundledProductRegistry() {
        ProductDatapackLoadResult result = newProductLoader().load(bundledProductResources());
        if (!result.succeeded()) {
            throw new IllegalStateException("Built-in product datapack resources are invalid:"
                    + System.lineSeparator()
                    + result.describeErrors());
        }
        return result.registry().orElseThrow();
    }

    public static TransformationRegistry loadBundledTransformationRegistry() {
        TransformationDatapackLoadResult result = new TransformationDatapackLoader(
                loadBundledProductRegistry(),
                knownCapabilities()
        ).load(bundledTransformationResources());
        if (!result.succeeded()) {
            throw new IllegalStateException("Built-in transformation datapack resources are invalid:"
                    + System.lineSeparator()
                    + result.describeErrors());
        }
        return result.registry().orElseThrow();
    }

    public static Map<String, JsonElement> bundledProductResources() {
        return bundledResources(BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS);
    }

    public static Map<String, JsonElement> bundledTransformationResources() {
        return bundledResources(BuiltInTransformationRegistry.BUILT_IN_RESOURCE_PATHS);
    }

    public static ProductDatapackLoader newProductLoader() {
        return new ProductDatapackLoader(knownProductCategories());
    }

    public static Set<EngineId> knownProductCategories() {
        return Set.of(
                BuiltInProductRegistry.CATEGORY_BEEF,
                BuiltInProductRegistry.CATEGORY_PORK,
                BuiltInProductRegistry.CATEGORY_BISON
        );
    }

    public static Set<EngineId> knownCapabilities() {
        return Set.of(
                BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING,
                BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_BANDSAW
        );
    }

    private static Map<String, JsonElement> bundledResources(Iterable<String> resourcePaths) {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (String resourcePath : resourcePaths) {
            try (var stream = ContentSnapshotService.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing built-in datapack resource " + resourcePath);
                }
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    resources.put(resourcePath, JsonParser.parseReader(reader));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read built-in datapack resource " + resourcePath, exception);
            }
        }
        return resources;
    }
}
