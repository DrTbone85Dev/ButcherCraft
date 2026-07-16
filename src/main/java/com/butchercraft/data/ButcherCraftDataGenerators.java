package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = ButcherCraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ButcherCraftDataGenerators {
    private ButcherCraftDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var existingFileHelper = event.getExistingFileHelper();

        if (event.includeServer()) {
            event.addProvider(new ButcherCraftDefinitionData(output));
            event.addProvider(new ButcherCraftLootTableProvider(output, event.getLookupProvider()));
        }
        generator.addProvider(event.includeClient(), new ButcherCraftLanguageProvider(output));
        generator.addProvider(event.includeClient(), new ButcherCraftItemModelProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new ButcherCraftBlockStateProvider(output, existingFileHelper));
    }
}
