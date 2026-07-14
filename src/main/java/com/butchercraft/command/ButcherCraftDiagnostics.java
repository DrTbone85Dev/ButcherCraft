package com.butchercraft.command;

import com.butchercraft.ButcherCraft;
import com.butchercraft.config.CommonConfig;
import com.mojang.brigadier.Command;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ButcherCraftDiagnostics {
    private static final ResourceLocation DEVELOPMENT_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "development_test_item");

    private ButcherCraftDiagnostics() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ButcherCraft.MOD_ID)
                .then(Commands.literal("diagnostic")
                        .executes(context -> runDiagnostic(context.getSource()))));
    }

    private static int runDiagnostic(net.minecraft.commands.CommandSourceStack source) {
        if (!CommonConfig.ENABLE_DEVELOPMENT_DIAGNOSTIC.get()) {
            source.sendSuccess(() -> Component.literal("ButcherCraft diagnostic is disabled by common config."), false);
            return 0;
        }

        String modVersion = modVersion(ButcherCraft.MOD_ID);
        String neoForgeVersion = modVersion("neoforge");
        boolean developmentItemRegistered = BuiltInRegistries.ITEM.containsKey(DEVELOPMENT_TEST_ITEM_ID);

        source.sendSuccess(() -> Component.literal("Project: " + ButcherCraft.PROJECT_NAME), false);
        source.sendSuccess(() -> Component.literal("Mod ID: " + ButcherCraft.MOD_ID), false);
        source.sendSuccess(() -> Component.literal("Mod version: " + modVersion), false);
        source.sendSuccess(() -> Component.literal("Minecraft version: " + SharedConstants.getCurrentVersion().getName()), false);
        source.sendSuccess(() -> Component.literal("NeoForge version: " + neoForgeVersion), false);
        source.sendSuccess(() -> Component.literal("Common initialization completed: " + ButcherCraft.commonInitializationCompleted()), false);
        source.sendSuccess(() -> Component.literal("Development test item registered: " + developmentItemRegistered), false);
        return Command.SINGLE_SUCCESS;
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unavailable");
    }
}
