package com.butchercraft.command;

import com.butchercraft.ButcherCraft;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.engine.product.Product;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
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
    private static final ResourceLocation PRODUCT_DATA_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "product_data");
    private static final ResourceLocation BEEF_TRIM_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "beef_trim_test");
    private static final ResourceLocation GROUND_BEEF_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_beef_test");

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
        boolean productDataComponentRegistered = ModDataComponents.PRODUCT_DATA.isBound()
                && PRODUCT_DATA_COMPONENT_ID.equals(ModDataComponents.PRODUCT_DATA.getId());
        boolean beefTrimTestItemRegistered = BuiltInRegistries.ITEM.containsKey(BEEF_TRIM_TEST_ITEM_ID);
        boolean groundBeefTestItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_BEEF_TEST_ITEM_ID);
        ProductRoundTripDiagnostic productRoundTrip = verifyFreshProductStackRoundTrip();

        source.sendSuccess(() -> Component.literal("Project: " + ButcherCraft.PROJECT_NAME), false);
        source.sendSuccess(() -> Component.literal("Mod ID: " + ButcherCraft.MOD_ID), false);
        source.sendSuccess(() -> Component.literal("Mod version: " + modVersion), false);
        source.sendSuccess(() -> Component.literal("Minecraft version: " + SharedConstants.getCurrentVersion().getName()), false);
        source.sendSuccess(() -> Component.literal("NeoForge version: " + neoForgeVersion), false);
        source.sendSuccess(() -> Component.literal("Common initialization completed: " + ButcherCraft.commonInitializationCompleted()), false);
        source.sendSuccess(() -> Component.literal("Development test item registered: " + developmentItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Product data component registered: " + productDataComponentRegistered), false);
        source.sendSuccess(() -> Component.literal("Beef trim test product registered: " + beefTrimTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground beef test product registered: " + groundBeefTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Fresh product stack round trip: " + productRoundTrip.roundTripSucceeded()), false);
        source.sendSuccess(() -> Component.literal("Product quantity survives round trip: " + productRoundTrip.quantityPreserved()), false);
        source.sendSuccess(() -> Component.literal("Product quality survives round trip: " + productRoundTrip.qualityPreserved()), false);
        if (!productRoundTrip.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Product round-trip detail: " + productRoundTrip.detail()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static ProductRoundTripDiagnostic verifyFreshProductStackRoundTrip() {
        try {
            ProductDataResult<ProductStackData> originalDataResult =
                    ProductStackAdapter.readProductData(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
            if (!originalDataResult.succeeded()) {
                return ProductRoundTripDiagnostic.failed(originalDataResult.failureReason().orElseThrow().code());
            }

            ProductStackData originalData = originalDataResult.orThrow();
            ProductDataResult<Product> productResult = ProductStackAdapter.toProduct(originalData);
            if (!productResult.succeeded()) {
                return ProductRoundTripDiagnostic.failed(productResult.failureReason().orElseThrow().code());
            }

            ProductDataResult<ProductStackData> roundTripDataResult = ProductStackAdapter.fromProduct(productResult.orThrow());
            if (!roundTripDataResult.succeeded()) {
                return ProductRoundTripDiagnostic.failed(roundTripDataResult.failureReason().orElseThrow().code());
            }

            ProductStackData roundTripData = roundTripDataResult.orThrow();
            boolean quantityPreserved = originalData.quantityValue() == roundTripData.quantityValue()
                    && originalData.quantityUnitId().equals(roundTripData.quantityUnitId());
            boolean qualityPreserved = originalData.qualityScore() == roundTripData.qualityScore();
            return new ProductRoundTripDiagnostic(originalData.equals(roundTripData), quantityPreserved, qualityPreserved, "");
        } catch (RuntimeException exception) {
            return ProductRoundTripDiagnostic.failed(exception.getClass().getSimpleName());
        }
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unavailable");
    }

    private record ProductRoundTripDiagnostic(
            boolean roundTripSucceeded,
            boolean quantityPreserved,
            boolean qualityPreserved,
            String detail
    ) {
        static ProductRoundTripDiagnostic failed(String detail) {
            return new ProductRoundTripDiagnostic(false, false, false, detail);
        }
    }
}
