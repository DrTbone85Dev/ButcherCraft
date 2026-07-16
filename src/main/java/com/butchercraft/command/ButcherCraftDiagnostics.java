package com.butchercraft.command;

import com.butchercraft.ButcherCraft;
import com.butchercraft.config.CommonConfig;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.evaluation.ProcessingEvaluator;
import com.butchercraft.machine.bandsaw.BandsawWorkstation;
import com.butchercraft.machine.grinder.GrinderWorkstation;
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
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModBlockEntityTypes;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModClientRegistrationStatus;
import com.butchercraft.registration.ModMenuTypes;
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
    private static final ResourceLocation PORK_TRIM_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "pork_trim_test");
    private static final ResourceLocation GROUND_PORK_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_pork_test");
    private static final ResourceLocation BISON_TRIM_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bison_trim_test");
    private static final ResourceLocation GROUND_BISON_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_bison_test");
    private static final ResourceLocation BEEF_FOREQUARTER_TEST_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "beef_forequarter_test");
    private static final ResourceLocation DEVELOPMENT_WORKSTATION_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "development_processing_workstation");
    private static final ResourceLocation GRINDER_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "grinder");
    private static final ResourceLocation BANDSAW_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bandsaw");

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
        boolean porkTrimTestItemRegistered = BuiltInRegistries.ITEM.containsKey(PORK_TRIM_TEST_ITEM_ID);
        boolean groundPorkTestItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_PORK_TEST_ITEM_ID);
        boolean bisonTrimTestItemRegistered = BuiltInRegistries.ITEM.containsKey(BISON_TRIM_TEST_ITEM_ID);
        boolean groundBisonTestItemRegistered = BuiltInRegistries.ITEM.containsKey(GROUND_BISON_TEST_ITEM_ID);
        boolean beefForequarterTestItemRegistered = BuiltInRegistries.ITEM.containsKey(BEEF_FOREQUARTER_TEST_ITEM_ID);
        ProductRoundTripDiagnostic productRoundTrip = verifyFreshProductStackRoundTrip();
        DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(source.registryAccess());
        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(definitionRegistries.view());
        DefinitionResolution<SpeciesDefinition> beefDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.BEEF);
        DefinitionResolution<SpeciesDefinition> porkDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.PORK);
        DefinitionResolution<SpeciesDefinition> bisonDefinition = resolver.resolveSpecies(BuiltInDefinitionIds.BISON);
        DefinitionResolution<ProcessingProfileDefinition> redMeatProfile = resolver.resolveProcessingProfile(BuiltInDefinitionIds.RED_MEAT);
        DefinitionResolution<ProductDefinition> beefTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_TRIM);
        DefinitionResolution<ProductDefinition> groundBeefDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_BEEF);
        DefinitionResolution<ProductDefinition> porkTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.PORK_TRIM);
        DefinitionResolution<ProductDefinition> groundPorkDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_PORK);
        DefinitionResolution<ProductDefinition> bisonTrimDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BISON_TRIM);
        DefinitionResolution<ProductDefinition> groundBisonDefinition = resolver.resolveProduct(BuiltInDefinitionIds.GROUND_BISON);
        DefinitionResolution<ProductDefinition> beefForequarterDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_FOREQUARTER);
        DefinitionResolution<ProductDefinition> beefChuckDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_CHUCK);
        DefinitionResolution<ProductDefinition> beefBoneDefinition = resolver.resolveProduct(BuiltInDefinitionIds.BEEF_BONE);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindBeefOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_BEEF);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindPorkOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_PORK);
        DefinitionResolution<ResolvedProcessingOperationDefinition> grindBisonOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.GRIND_BISON);
        DefinitionResolution<ResolvedProcessingOperationDefinition> breakBeefForequarterOperation =
                resolver.resolveOperation(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER);
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(definitionRegistries.view());
        DefinitionValidationReport definitionReport = definitionRegistries.report()
                .plus(resolver.validateAll())
                .plus(graph.validationReport());
        boolean initialGraphValid = definitionRegistries.allRegistriesAvailable() && !definitionReport.hasErrors();
        boolean beefTrimToGroundBeefExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.GROUND_BEEF);
        boolean porkTrimToGroundPorkExists = graph.hasDirectTransformation(BuiltInDefinitionIds.PORK_TRIM, BuiltInDefinitionIds.GROUND_PORK);
        boolean bisonTrimToGroundBisonExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BISON_TRIM, BuiltInDefinitionIds.GROUND_BISON);
        boolean beefForequarterToChuckExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_FOREQUARTER, BuiltInDefinitionIds.BEEF_CHUCK);
        boolean beefForequarterToBoneExists = graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_FOREQUARTER, BuiltInDefinitionIds.BEEF_BONE);
        WorkstationDiagnostic workstationDiagnostic = verifyWorkstation(source.registryAccess());
        GrinderDiagnostic grinderDiagnostic = verifyGrinder(source.registryAccess());
        BandsawDiagnostic bandsawDiagnostic = verifyBandsaw(source.registryAccess());

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
        source.sendSuccess(() -> Component.literal("Pork trim test product registered: " + porkTrimTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground pork test product registered: " + groundPorkTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Bison trim test product registered: " + bisonTrimTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Ground bison test product registered: " + groundBisonTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Beef forequarter test product registered: " + beefForequarterTestItemRegistered), false);
        source.sendSuccess(() -> Component.literal("Fresh product stack round trip: " + productRoundTrip.roundTripSucceeded()), false);
        source.sendSuccess(() -> Component.literal("Product quantity survives round trip: " + productRoundTrip.quantityPreserved()), false);
        source.sendSuccess(() -> Component.literal("Product quality survives round trip: " + productRoundTrip.qualityPreserved()), false);
        source.sendSuccess(() -> Component.literal("Species registry available: " + definitionRegistries.speciesRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Processing-profile registry available: " + definitionRegistries.processingProfileRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Product registry available: " + definitionRegistries.productRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Processing-operation registry available: " + definitionRegistries.processingOperationRegistryAvailable()), false);
        source.sendSuccess(() -> Component.literal("Beef definition resolved: " + beefDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Pork definition resolved: " + porkDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Bison definition resolved: " + bisonDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Red-meat profile resolved: " + redMeatProfile.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef trim definition resolved: " + beefTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground beef definition resolved: " + groundBeefDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Pork trim definition resolved: " + porkTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground pork definition resolved: " + groundPorkDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Bison trim definition resolved: " + bisonTrimDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Ground bison definition resolved: " + groundBisonDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef forequarter definition resolved: " + beefForequarterDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef chuck definition resolved: " + beefChuckDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Beef bone definition resolved: " + beefBoneDefinition.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-beef operation resolved: " + grindBeefOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-pork operation resolved: " + grindPorkOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Grind-bison operation resolved: " + grindBisonOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Break-beef-forequarter operation resolved: " + breakBeefForequarterOperation.succeeded()), false);
        source.sendSuccess(() -> Component.literal("Initial processing graph validates: " + initialGraphValid), false);
        source.sendSuccess(() -> Component.literal("Beef Trim -> Ground Beef direct transformation exists: " + beefTrimToGroundBeefExists), false);
        source.sendSuccess(() -> Component.literal("Pork Trim -> Ground Pork direct transformation exists: " + porkTrimToGroundPorkExists), false);
        source.sendSuccess(() -> Component.literal("Bison Trim -> Ground Bison direct transformation exists: " + bisonTrimToGroundBisonExists), false);
        source.sendSuccess(() -> Component.literal("Beef Forequarter -> Beef Chuck direct transformation exists: " + beefForequarterToChuckExists), false);
        source.sendSuccess(() -> Component.literal("Beef Forequarter -> Beef Bone direct transformation exists: " + beefForequarterToBoneExists), false);
        source.sendSuccess(() -> Component.literal("Development workstation block registered: " + workstationDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation block entity registered: " + workstationDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation menu registered: " + workstationDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation screen binding observed: " + ModClientRegistrationStatus.developmentWorkstationScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Development workstation capability available: " + workstationDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Beef Trim resolves to grind_beef for development station: " + workstationDiagnostic.beefTrimResolvesToGrindBeef()), false);
        source.sendSuccess(() -> Component.literal("grind_beef duration resolves to 60 ticks: " + workstationDiagnostic.grindBeefDurationIs60Ticks()), false);
        source.sendSuccess(() -> Component.literal("Prototype processing context validates: " + workstationDiagnostic.prototypeContextValidates()), false);
        source.sendSuccess(() -> Component.literal("Output mapping resolves to Ground Beef Test Product: " + workstationDiagnostic.outputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Grinder block registered: " + grinderDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder block entity registered: " + grinderDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder menu registered: " + grinderDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder screen binding observed: " + ModClientRegistrationStatus.grinderScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Grinder capability available: " + grinderDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_beef supports Grinder capability: " + grinderDiagnostic.grindBeefSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_pork supports Grinder capability: " + grinderDiagnostic.grindPorkSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Built-in grind_bison supports Grinder capability: " + grinderDiagnostic.grindBisonSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Beef Trim resolves to grind_beef for Grinder: " + grinderDiagnostic.beefTrimResolvesToGrindBeef()), false);
        source.sendSuccess(() -> Component.literal("Pork Trim resolves to grind_pork for Grinder: " + grinderDiagnostic.porkTrimResolvesToGrindPork()), false);
        source.sendSuccess(() -> Component.literal("Bison Trim resolves to grind_bison for Grinder: " + grinderDiagnostic.bisonTrimResolvesToGrindBison()), false);
        source.sendSuccess(() -> Component.literal("Grinder grind_beef duration resolves to 60 ticks: " + grinderDiagnostic.grindBeefDurationIs60Ticks()), false);
        source.sendSuccess(() -> Component.literal("Ground Beef output mapping resolves for Grinder: " + grinderDiagnostic.outputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Pork output mapping resolves for Grinder: " + grinderDiagnostic.groundPorkOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Ground Bison output mapping resolves for Grinder: " + grinderDiagnostic.groundBisonOutputMappingResolves()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw block registered: " + bandsawDiagnostic.blockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw upper block registered: " + bandsawDiagnostic.upperBlockRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw block entity registered: " + bandsawDiagnostic.blockEntityRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw menu registered: " + bandsawDiagnostic.menuRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw screen binding observed: " + ModClientRegistrationStatus.bandsawScreenRegistered()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw capability available: " + bandsawDiagnostic.capabilityAvailable()), false);
        source.sendSuccess(() -> Component.literal("Built-in break_beef_forequarter supports Bandsaw capability: " + bandsawDiagnostic.breakForequarterSupportsCapability()), false);
        source.sendSuccess(() -> Component.literal("Beef Forequarter resolves to break_beef_forequarter for Bandsaw: " + bandsawDiagnostic.forequarterResolvesToBreakdown()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw break_beef_forequarter duration resolves to 120 ticks: " + bandsawDiagnostic.breakdownDurationIs120Ticks()), false);
        source.sendSuccess(() -> Component.literal("Bandsaw output mappings resolve: " + bandsawDiagnostic.outputMappingsResolve()), false);
        source.sendSuccess(() -> Component.literal("Development workstation remains available: " + (
                workstationDiagnostic.blockRegistered()
                        && workstationDiagnostic.blockEntityRegistered()
                        && workstationDiagnostic.menuRegistered()
        )), false);
        if (!productRoundTrip.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Product round-trip detail: " + productRoundTrip.detail()), false);
        }
        if (!workstationDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Workstation diagnostic detail: " + workstationDiagnostic.detail()), false);
        }
        if (!grinderDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Grinder diagnostic detail: " + grinderDiagnostic.detail()), false);
        }
        if (!bandsawDiagnostic.detail().isBlank()) {
            source.sendSuccess(() -> Component.literal("Bandsaw diagnostic detail: " + bandsawDiagnostic.detail()), false);
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
        boolean outputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BEEF);
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

    private static GrinderDiagnostic verifyGrinder(net.minecraft.core.RegistryAccess registryAccess) {
        boolean blockRegistered = BuiltInRegistries.BLOCK.containsKey(GRINDER_BLOCK_ID);
        boolean blockEntityRegistered = ModBlockEntityTypes.GRINDER.isBound()
                && GRINDER_BLOCK_ID.equals(ModBlockEntityTypes.GRINDER.getId());
        boolean menuRegistered = ModMenuTypes.GRINDER.isBound()
                && GRINDER_BLOCK_ID.equals(ModMenuTypes.GRINDER.getId());
        boolean capabilityAvailable = GrinderWorkstation.capability().supportsWorkstationCapability(GrinderWorkstation.CAPABILITY_ID);
        boolean grindBeefSupportsCapability = false;
        boolean grindPorkSupportsCapability = false;
        boolean grindBisonSupportsCapability = false;
        boolean beefTrimResolvesToGrindBeef = false;
        boolean porkTrimResolvesToGrindPork = false;
        boolean bisonTrimResolvesToGrindBison = false;
        boolean durationIs60Ticks = false;
        boolean outputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BEEF);
        boolean groundPorkOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_PORK);
        boolean groundBisonOutputMappingResolves = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.GROUND_BISON);
        String detail = "";

        try {
            DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(registryAccess);
            ProcessingDefinitionResolver definitionResolver = new ProcessingDefinitionResolver(definitionRegistries.view());
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedBeefOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_BEEF);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedPorkOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_PORK);
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedBisonOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.GRIND_BISON);
            grindBeefSupportsCapability = resolvedBeefOperation.succeeded()
                    && resolvedBeefOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindPorkSupportsCapability = resolvedPorkOperation.succeeded()
                    && resolvedPorkOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();
            grindBisonSupportsCapability = resolvedBisonOperation.succeeded()
                    && resolvedBisonOperation.orThrow().operation().workstationCapability()
                    .filter(GrinderWorkstation.CAPABILITY_ID::equals)
                    .isPresent();

            WorkstationOperationResolution resolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.BEEF_TRIM_TEST.get().getDefaultInstance()
            );
            if (resolution.succeeded()) {
                var operation = resolution.operation().orElseThrow();
                beefTrimResolvesToGrindBeef = BuiltInDefinitionIds.GRIND_BEEF.equals(operation.operationId());
                durationIs60Ticks = operation.totalTicks() == WorkstationDuration.millisecondsToTicks(3_000);
            } else {
                detail = resolution.failure().orElseThrow().code().reasonCode();
            }
            WorkstationOperationResolution porkResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.PORK_TRIM_TEST.get().getDefaultInstance()
            );
            if (porkResolution.succeeded()) {
                porkTrimResolvesToGrindPork = BuiltInDefinitionIds.GRIND_PORK.equals(porkResolution.operation().orElseThrow().operationId());
            }
            WorkstationOperationResolution bisonResolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    GrinderWorkstation.capability(),
                    ModItems.BISON_TRIM_TEST.get().getDefaultInstance()
            );
            if (bisonResolution.succeeded()) {
                bisonTrimResolvesToGrindBison = BuiltInDefinitionIds.GRIND_BISON.equals(bisonResolution.operation().orElseThrow().operationId());
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName();
        }

        return new GrinderDiagnostic(
                blockRegistered,
                blockEntityRegistered,
                menuRegistered,
                capabilityAvailable,
                grindBeefSupportsCapability,
                grindPorkSupportsCapability,
                grindBisonSupportsCapability,
                beefTrimResolvesToGrindBeef,
                porkTrimResolvesToGrindPork,
                bisonTrimResolvesToGrindBison,
                durationIs60Ticks,
                outputMappingResolves,
                groundPorkOutputMappingResolves,
                groundBisonOutputMappingResolves,
                detail
        );
    }

    private static BandsawDiagnostic verifyBandsaw(net.minecraft.core.RegistryAccess registryAccess) {
        boolean blockRegistered = BuiltInRegistries.BLOCK.containsKey(BANDSAW_BLOCK_ID);
        boolean upperBlockRegistered = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "bandsaw_upper"));
        boolean blockEntityRegistered = ModBlockEntityTypes.BANDSAW.isBound()
                && BANDSAW_BLOCK_ID.equals(ModBlockEntityTypes.BANDSAW.getId());
        boolean menuRegistered = ModMenuTypes.BANDSAW.isBound()
                && BANDSAW_BLOCK_ID.equals(ModMenuTypes.BANDSAW.getId());
        boolean capabilityAvailable = BandsawWorkstation.capability().supportsWorkstationCapability(BandsawWorkstation.CAPABILITY_ID);
        boolean breakForequarterSupportsCapability = false;
        boolean forequarterResolvesToBreakdown = false;
        boolean durationIs120Ticks = false;
        boolean outputMappingsResolve = DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_CHUCK)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_RIB)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_BRISKET)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_PLATE)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_SHANK)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_TRIM)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_FAT)
                && DevelopmentProductItemMappings.fixtureMapping().canCreate(BuiltInDefinitionIds.BEEF_BONE);
        String detail = "";

        try {
            DefinitionRegistryLoadResult definitionRegistries = DefinitionRegistryView.fromRegistryAccess(registryAccess);
            ProcessingDefinitionResolver definitionResolver = new ProcessingDefinitionResolver(definitionRegistries.view());
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolvedOperation =
                    definitionResolver.resolveOperation(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER);
            breakForequarterSupportsCapability = resolvedOperation.succeeded()
                    && resolvedOperation.orThrow().operation().workstationCapability()
                    .filter(BandsawWorkstation.CAPABILITY_ID::equals)
                    .isPresent();

            WorkstationOperationResolution resolution = new WorkstationOperationResolver().resolve(
                    registryAccess,
                    BandsawWorkstation.capability(),
                    ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance()
            );
            if (resolution.succeeded()) {
                var operation = resolution.operation().orElseThrow();
                forequarterResolvesToBreakdown = BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER.equals(operation.operationId());
                durationIs120Ticks = operation.totalTicks() == WorkstationDuration.millisecondsToTicks(6_000);
            } else {
                detail = resolution.failure().orElseThrow().code().reasonCode();
            }
        } catch (RuntimeException exception) {
            detail = exception.getClass().getSimpleName();
        }

        return new BandsawDiagnostic(
                blockRegistered,
                upperBlockRegistered,
                blockEntityRegistered,
                menuRegistered,
                capabilityAvailable,
                breakForequarterSupportsCapability,
                forequarterResolvesToBreakdown,
                durationIs120Ticks,
                outputMappingsResolve,
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

    private record GrinderDiagnostic(
            boolean blockRegistered,
            boolean blockEntityRegistered,
            boolean menuRegistered,
            boolean capabilityAvailable,
            boolean grindBeefSupportsCapability,
            boolean grindPorkSupportsCapability,
            boolean grindBisonSupportsCapability,
            boolean beefTrimResolvesToGrindBeef,
            boolean porkTrimResolvesToGrindPork,
            boolean bisonTrimResolvesToGrindBison,
            boolean grindBeefDurationIs60Ticks,
            boolean outputMappingResolves,
            boolean groundPorkOutputMappingResolves,
            boolean groundBisonOutputMappingResolves,
            String detail
    ) {
    }

    private record BandsawDiagnostic(
            boolean blockRegistered,
            boolean upperBlockRegistered,
            boolean blockEntityRegistered,
            boolean menuRegistered,
            boolean capabilityAvailable,
            boolean breakForequarterSupportsCapability,
            boolean forequarterResolvesToBreakdown,
            boolean breakdownDurationIs120Ticks,
            boolean outputMappingsResolve,
            String detail
    ) {
    }
}
