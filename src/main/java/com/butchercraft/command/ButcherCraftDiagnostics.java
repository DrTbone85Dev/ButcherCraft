package com.butchercraft.command;

import com.butchercraft.ButcherCraft;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.DefinitionRegistryLoadResult;
import com.butchercraft.processing.definition.DefinitionRegistryView;
import com.butchercraft.processing.definition.DefinitionResolution;
import com.butchercraft.processing.definition.DefinitionValidationReport;
import com.butchercraft.processing.definition.ProcessingDefinitionResolver;
import com.butchercraft.processing.definition.ProcessingGraph;
import com.butchercraft.processing.definition.ProcessingProfileDefinition;
import com.butchercraft.processing.definition.ProductDefinition;
import com.butchercraft.processing.definition.ResolvedProcessingOperationDefinition;
import com.butchercraft.processing.definition.SpeciesDefinition;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModClientRegistrationStatus;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import com.butchercraft.workstation.DevelopmentWorkstationFixtures;
import com.butchercraft.workstation.PrototypeProcessingContextValues;
import com.butchercraft.workstation.WorkstationDuration;
import com.butchercraft.workstation.WorkstationOperationResolution;
import com.butchercraft.workstation.WorkstationOperationResolver;
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
    private static final ResourceLocation DEVELOPMENT_WORKSTATION_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "development_processing_workstation");

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
        DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(source.registryAccess());
        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(definitionRegistries.view());
        DefinitionResolution<SpeciesDefinition> beefDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.BEEF);
        DefinitionResolution<ProcessingProfileDefinition> redMeatProfile = resolver.resolveProcessingProfile(BuiltInDefinitionIds.RED_MEAT);
        DefinitionResolution<ProductDefinition> beefTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_TRIM);
        DefinitionResolution<ProductDefinition> groundBeefDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_BEEF);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindBeefOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_BEEF);
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(definitionRegistries.view());
        DefinitionValidationReport definitionReport = definitionRegistries.report()
                .plus(resolver.validateAll())
                .plus(graph.validationReport());
        boolean initialGraphValid = definitionRegistries.allRegistriesAvailable() && !definitionReport.hasErrors();
        boolean beefTrimToGroundBeefExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.GROUND_BEEF);
        WorkstationDiagnostic workstationDiagnostic = verifyWorkstation(source.registryAccess());

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
        source.sendSuccess(() -> Component.literal("Species registry available: " + definitionRegistries.speciesRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Processing-profile registry available: " + definitionRegistries.processingProfileRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Product registry available: " + definitionRegistries.productRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Processing-operation registry available: " + definitionRegistries.processingOperationRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Beef definition resolved: " + beefDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Red-meat profile resolved: " + redMeatProfile.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef trim definition resolved: " + beefTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground beef definition resolved: " + groundBeefDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-beef operation resolved: " + grindBeefOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Initial processing graph validates: " + initialGraphValid), false);
        source.sendSuccess(() -> Component.literal("Beef Trim -> Ground Beef direct transformation exists: " + beefTrimToGroundBeefExists), false);
        source.sendSuccess(() -> Component.literal("Development workstation block registered: " + workstationDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation block entity registered: " + workstationDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation menu registered: " + workstationDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation screen binding observed: " + ModClientRegistrationStatus.developmentWorkstationScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation capability available: " + workstationDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Beef Trim resolves to grind_beef for development station: " + workstationDiagnostic.beefTrimResolvesToGrindBeef()), false);
        source.sendSuccess(() -> Component.literal("grind_beef duration resolves to 60 ticks: " + workstationDiagnostic.grindBeefDurationIs60Ticks()), false);
        source.sendSuccess(() -> Component.literal("Prototype processing context validates: " + workstationDiagnostic.prototypeContextValidates()), false);
        source.sendSuccess(() -> Component.literal("Output mapping resolves to Ground Beef Test Product: " + workstationDiagnostic.outputMappingResolves()), false);
        if (!productRoundTrip.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Product round-trip detail: " + productRoundTrip.detail()), false);
        }
        if (!workstationDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Workstation diagnostic detail: " + workstationDiagnostic.detail()), false);
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

    private static WorkstationDiagnostic verifyWorkstation(net.minecraft.core.RegistryAccess registryAccess) {
        boolean blockRegistered = BuiltInRegistries.BLOCK.containsKey(DEVELOPMENT_WORKSTATION_BLOCK_ID);
        boolean blockEntityRegistered = ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.isBound()
                && DEVELOPMENT_WORKSTATION_BLOCK_ID.equals(ModBlockEntityTypes.DEVELOPMENT_PROCESSING_WORKSTATION.getId());
        boolean menuRegistered = ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.isBound()
                && DEVELOPMENT_WORKSTATION_BLOCK_ID.equals(ModMenuTypes.DEVELOPMENT_PROCESSING_WORKSTATION.getId());
        boolean capabilityAvailable = DevelopmentWorkstationFixtures.capability()
                .supportsWorkstationCapability(BuiltInDefinitionIds.WORKSTATION_CAPABILITY_DEVELOPMENT_PROCESSING);
        boolean beefTrimResolvesToGrindBeef = false;
        boolean durationIs60Ticks = false;
        boolean contextValidates = false;
        boolean outputMappingResolves = DevelopmentProductItemMapping.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BEEF);
        String detail = "";

        try {
            WorkstationOperationResolution resolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    DevelopmentWorkstationFixtures.capability(),
                    ModItems.BEEF_TRIM_TEST.get().getDefaultInstance()
            );
            if (resolution.succeeded()) {
                var operation = resolution.operation().orElseThrow();
                beefTrimResolvesToGrindBeef = BuiltInDefinitionIds.GRIND_BEEF.equals(operation.operationId());
                durationIs60Ticks = operation.totalTicks() == WorkstationDuration.millisecondsToTicks(3_000);
                contextValidates = ProcessingEvaluator.validate(
                        operation.engineOperation(),
                        PrototypeProcessingContextValues.context(operation.inputProduct(), operation.engineOperation())
                ).accepted();
            } else {
                detail = resolution.failure().orElseThrow().code().reasonCode();
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName();
        }

        return new WorkstationDiagnostic(
                blockRegistered,
                blockEntityRegistered,
                menuRegistered,
                capabilityAvailable,
                beefTrimResolvesToGrindBeef,
                durationIs60Ticks,
                contextValidates,
                outputMappingResolves,
                detail
        );
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

    private record WorkstationDiagnostic(
            boolean blockRegistered,
            boolean blockEntityRegistered,
            boolean menuRegistered,
            boolean capabilityAvailable,
            boolean beefTrimResolvesToGrindBeef,
            boolean grindBeefDurationIs60Ticks,
            boolean prototypeContextValidates,
            boolean outputMappingResolves,
            String detail
    ) {
    }
}
