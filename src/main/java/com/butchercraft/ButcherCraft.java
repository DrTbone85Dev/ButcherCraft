package com.butchercraft;

import com.butchercraft.command.ButcherCraftDiagnostics;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.registration.ModCreativeModeTabs;
import com.butchercraft.registration.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ButcherCraft.MOD_ID)
public final class ButcherCraft {
    public static final String PROJECT_NAME = "ButcherCraft";
    public static final String MOD_ID = "butchercraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static boolean commonInitializationCompleted;

    public ButcherCraft(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(ButcherCraftDiagnostics::registerCommands);

        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        commonInitializationCompleted = true;
        if (CommonConfig.LOG_FOUNDATION_INITIALIZATION.get()) {
            LOGGER.info("{} common initialization completed.", PROJECT_NAME);
        }
    }

    public static boolean commonInitializationCompleted() {
        return commonInitializationCompleted;
    }
}
