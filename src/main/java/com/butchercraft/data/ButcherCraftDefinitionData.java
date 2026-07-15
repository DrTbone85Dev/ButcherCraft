package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.processing.definition.ProcessingOperationDefinition;
import com.butchercraft.processing.definition.ProcessingProfileDefinition;
import com.butchercraft.processing.definition.ProductDefinition;
import com.butchercraft.processing.definition.SpeciesDefinition;
import com.butchercraft.registration.ModDataPackRegistries;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ButcherCraftDefinitionData implements DataProvider {
    private final Path outputRoot;

    public ButcherCraftDefinitionData(PackOutput output) {
        this.outputRoot = output.getOutputFolder();
    }

    public static RegistrySetBuilder registrySetBuilder() {
        return new RegistrySetBuilder()
                .add(ModDataPackRegistries.SPECIES, BuiltInProcessingDefinitions::bootstrapSpecies)
                .add(ModDataPackRegistries.PROCESSING_PROFILE, BuiltInProcessingDefinitions::bootstrapProcessingProfiles)
                .add(ModDataPackRegistries.PRODUCT, BuiltInProcessingDefinitions::bootstrapProducts)
                .add(ModDataPackRegistries.PROCESSING_OPERATION, BuiltInProcessingDefinitions::bootstrapOperations);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        var definitions = BuiltInProcessingDefinitions.builtInView();
        writeAll(futures, output, ModDataPackRegistries.SPECIES, SpeciesDefinition.CODEC, definitions.species());
        writeAll(futures, output, ModDataPackRegistries.PROCESSING_PROFILE, ProcessingProfileDefinition.CODEC, definitions.processingProfiles());
        writeAll(futures, output, ModDataPackRegistries.PRODUCT, ProductDefinition.CODEC, definitions.products());
        writeAll(futures, output, ModDataPackRegistries.PROCESSING_OPERATION, ProcessingOperationDefinition.CODEC, definitions.operations());
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ButcherCraft Definitions";
    }

    private <T> void writeAll(
            List<CompletableFuture<?>> futures,
            CachedOutput output,
            ResourceKey<Registry<T>> registryKey,
            Codec<T> codec,
            Map<ResourceLocation, T> values
    ) {
        values.forEach((id, value) -> {
            if (!ButcherCraft.MOD_ID.equals(id.getNamespace())) {
                throw new IllegalStateException("ButcherCraft definition provider cannot emit foreign namespace definition " + id);
            }
            futures.add(DataProvider.saveStable(output, encode(codec, id, value), definitionPath(registryKey, id)));
        });
    }

    private <T> JsonElement encode(Codec<T> codec, ResourceLocation id, T value) {
        DataResult<JsonElement> result = codec.encodeStart(JsonOps.INSTANCE, value);
        return result.result().orElseThrow(() -> new IllegalStateException(
                "Unable to encode ButcherCraft definition " + id + ": " + result.error().map(Object::toString).orElse("unknown error")
        ));
    }

    private <T> Path definitionPath(ResourceKey<Registry<T>> registryKey, ResourceLocation id) {
        ResourceLocation registryId = registryKey.location();
        return outputRoot
                .resolve("data")
                .resolve(id.getNamespace())
                .resolve(registryId.getNamespace())
                .resolve(registryId.getPath())
                .resolve(id.getPath() + ".json");
    }
}
