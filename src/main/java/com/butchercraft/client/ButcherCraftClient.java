package com.butchercraft.client;

import com.butchercraft.ButcherCraft;
import com.butchercraft.client.screen.BandsawScreen;
import com.butchercraft.client.screen.GrinderScreen;
import com.butchercraft.client.screen.PackagingTableScreen;
import com.butchercraft.client.screen.ProcessingWorkstationScreen;
import com.butchercraft.registration.ModClientRegistrationStatus;
import com.butchercraft.registration.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ButcherCraft.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ButcherCraftClient {
    private ButcherCraftClient() {
    }

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.get(), ProcessingWorkstationScreen::new);
        event.register(ModMenuTypes.GRINDER.get(), GrinderScreen::new);
        event.register(ModMenuTypes.BANDSAW.get(), BandsawScreen::new);
        event.register(ModMenuTypes.PACKAGING_TABLE.get(), PackagingTableScreen::new);
        ModClientRegistrationStatus.markDevelopmentWorkstationScreenRegistered();
        ModClientRegistrationStatus.markGrinderScreenRegistered();
        ModClientRegistrationStatus.markBandsawScreenRegistered();
        ModClientRegistrationStatus.markPackagingTableScreenRegistered();
    }
}
