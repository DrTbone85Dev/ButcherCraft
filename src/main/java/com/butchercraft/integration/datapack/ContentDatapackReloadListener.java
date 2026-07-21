package com.butchercraft.integration.datapack;

import com.butchercraft.ButcherCraft;
import com.butchercraft.content.ContentSnapshotLoadResult;
import com.butchercraft.content.ContentSnapshotService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minecraft datapack reload bridge for product and transformation definitions.
 */
public final class ContentDatapackReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String ROOT_DIRECTORY = "butchercraft";
    private static final String PRODUCT_PREFIX = "product/";
    private static final String TRANSFORMATION_PREFIX = "transformation/";

    public ContentDatapackReloadListener() {
        super(GSON, ROOT_DIRECTORY);
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new ContentDatapackReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<String, JsonElement> productResources = new LinkedHashMap<>();
        LinkedHashMap<String, JsonElement> transformationResources = new LinkedHashMap<>();

        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> route(entry.getKey(), entry.getValue(), productResources, transformationResources));

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(productResources, transformationResources);
        if (!result.succeeded()) {
            String message = "Unable to load ButcherCraft content datapack resources:"
                    + System.lineSeparator()
                    + result.describeErrors();
            ButcherCraft.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        var snapshot = result.snapshot().orElseThrow();
        ButcherCraft.LOGGER.info(
                "Loaded {} ButcherCraft product definitions and {} transformation definitions.",
                snapshot.products().size(),
                snapshot.transformations().size()
        );
    }

    private static void route(
            ResourceLocation id,
            JsonElement json,
            Map<String, JsonElement> productResources,
            Map<String, JsonElement> transformationResources
    ) {
        String path = id.getPath();
        if (path.startsWith(PRODUCT_PREFIX)) {
            productResources.put(id.toString(), json);
        } else if (path.startsWith(TRANSFORMATION_PREFIX)) {
            transformationResources.put(id.toString(), json);
        } else if (path.startsWith(ROOT_DIRECTORY + "/" + PRODUCT_PREFIX)) {
            productResources.put(id.toString(), json);
        } else if (path.startsWith(ROOT_DIRECTORY + "/" + TRANSFORMATION_PREFIX)) {
            transformationResources.put(id.toString(), json);
        }
    }
}
