package com.butchercraft;

import com.butchercraft.command.ButcherCraftDiagnostics;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.integration.datapack.ContentDatapackReloadListener;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModCapabilities;
import com.butchercraft.registration.ModCreativeModeTabs;
import com.butchercraft.registration.ModDataPackRegistries;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.world.BusinessRuntimeService;
import com.butchercraft.world.EconomicActorService;
import com.butchercraft.world.GoodService;
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.OrderContractService;
import com.butchercraft.world.TransactionService;
import com.butchercraft.world.WorkforceService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.player.runtime.PlayerJoinInitializer;
import com.butchercraft.world.simulation.SimulationClockService;
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
        ModDataPackRegistries.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntityTypes.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        ModCapabilities.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(ButcherCraftDiagnostics::registerCommands);
        NeoForge.EVENT_BUS.addListener(ContentDatapackReloadListener::register);
        NeoForge.EVENT_BUS.addListener(WorldIdentityService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(PlayerJoinInitializer.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(SimulationClockService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(SimulationClockService.INSTANCE::advance);
        NeoForge.EVENT_BUS.addListener(SimulationClockService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(BusinessRuntimeService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(BusinessRuntimeService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(WorkforceService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(WorkforceService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(GoodService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(GoodService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(EconomicActorService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(EconomicActorService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(InventoryService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(InventoryService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(TransactionService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(TransactionService.INSTANCE::save);
        NeoForge.EVENT_BUS.addListener(OrderContractService.INSTANCE::initialize);
        NeoForge.EVENT_BUS.addListener(OrderContractService.INSTANCE::save);

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
