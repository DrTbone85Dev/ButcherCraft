package com.butchercraft.data;

import com.butchercraft.registration.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class ButcherCraftLootTableProvider extends LootTableProvider {
    ButcherCraftLootTableProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(
                output,
                Set.of(),
                List.of(new SubProviderEntry(ButcherCraftBlockLootProvider::new, LootContextParamSets.BLOCK)),
                lookupProvider
        );
    }

    private static final class ButcherCraftBlockLootProvider extends BlockLootSubProvider {
        private ButcherCraftBlockLootProvider(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            dropSelf(ModBlocks.GRINDER.get());
            dropSelf(ModBlocks.BANDSAW.get());
            dropSelf(ModBlocks.PACKAGING_TABLE.get());
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return List.of(ModBlocks.GRINDER.get(), ModBlocks.BANDSAW.get(), ModBlocks.PACKAGING_TABLE.get());
        }
    }
}
