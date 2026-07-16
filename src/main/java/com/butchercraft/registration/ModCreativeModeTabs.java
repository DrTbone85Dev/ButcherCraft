package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeModeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ButcherCraft.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BUTCHERCRAFT_TAB =
            CREATIVE_MODE_TABS.register("butchercraft", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.butchercraft"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> ModItems.DEVELOPMENT_TEST_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DEVELOPMENT_TEST_ITEM.get());
                        output.accept(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
                        output.accept(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());
                        output.accept(ModItems.PORK_TRIM_TEST.get().getDefaultInstance());
                        output.accept(ModItems.GROUND_PORK_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BISON_TRIM_TEST.get().getDefaultInstance());
                        output.accept(ModItems.GROUND_BISON_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_CHUCK_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_RIB_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_PACKER_BRISKET_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_PLATE_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_SHANK_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_FAT_TEST.get().getDefaultInstance());
                        output.accept(ModItems.BEEF_BONE_TEST.get().getDefaultInstance());
                        output.accept(ModItems.GRINDER.get());
                        output.accept(ModItems.BANDSAW.get());
                        output.accept(ModItems.DEVELOPMENT_PROCESSING_WORKSTATION.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
