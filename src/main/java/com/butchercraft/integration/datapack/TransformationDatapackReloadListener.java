package com.butchercraft.integration.datapack;

import com.butchercraft.ButcherCraft;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.datapack.TransformationDatapackLoadResult;
import com.butchercraft.transformation.datapack.TransformationRegistryService;
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
 * Minecraft datapack reload bridge for transformation definitions.
 */
public final class TransformationDatapackReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public TransformationDatapackReloadListener() {
        super(GSON, BuiltInTransformationRegistry.DATAPACK_DIRECTORY);
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new TransformationDatapackReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager resourceManager, ProfilerFiller profiler) {
        LinkedHashMap<String, JsonElement> orderedResources = new LinkedHashMap<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> orderedResources.put(entry.getKey().toString(), entry.getValue()));

        TransformationDatapackLoadResult result = TransformationRegistryService.replaceFromDatapack(orderedResources);
        if (!result.succeeded()) {
            String message = "Unable to load ButcherCraft transformation datapack resources:"
                    + System.lineSeparator()
                    + result.describeErrors();
            ButcherCraft.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        ButcherCraft.LOGGER.info("Loaded {} ButcherCraft transformation definitions.", result.registry().orElseThrow().size());
    }
}
