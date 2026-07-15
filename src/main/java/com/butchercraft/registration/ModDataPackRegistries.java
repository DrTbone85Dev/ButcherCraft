package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.processing.definition.ProcessingOperationDefinition;
import com.butchercraft.processing.definition.ProcessingProfileDefinition;
import com.butchercraft.processing.definition.ProductDefinition;
import com.butchercraft.processing.definition.SpeciesDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public final class ModDataPackRegistries {
    public static final ResourceKey<Registry<SpeciesDefinition>> SPECIES =
            registryKey("species");
    public static final ResourceKey<Registry<ProcessingProfileDefinition>> PROCESSING_PROFILE =
            registryKey("processing_profile");
    public static final ResourceKey<Registry<ProductDefinition>> PRODUCT =
            registryKey("product");
    public static final ResourceKey<Registry<ProcessingOperationDefinition>> PROCESSING_OPERATION =
            registryKey("processing_operation");

    private ModDataPackRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModDataPackRegistries::registerDataPackRegistries);
    }

    private static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SPECIES, SpeciesDefinition.CODEC);
        event.dataPackRegistry(PROCESSING_PROFILE, ProcessingProfileDefinition.CODEC);
        event.dataPackRegistry(PRODUCT, ProductDefinition.CODEC);
        event.dataPackRegistry(PROCESSING_OPERATION, ProcessingOperationDefinition.CODEC);
    }

    private static <T> ResourceKey<Registry<T>> registryKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, path));
    }
}
