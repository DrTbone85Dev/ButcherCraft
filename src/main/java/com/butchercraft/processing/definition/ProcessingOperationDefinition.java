package com.butchercraft.processing.definition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProcessingOperationDefinition(
        String displayNameKey,
        ResourceLocation operationCategory,
        List<ResourceLocation> requiredProcessingProfiles,
        ResourceLocation inputProduct,
        ResourceLocation requiredInputProcessingState,
        long baseDurationMilliseconds,
        QuantityDefinition minimumInputQuantity,
        int minimumCleanlinessFactor,
        int minimumEquipmentConditionFactor,
        ZeroOutputPolicy zeroOutputPolicy,
        List<ProcessingOutputDefinition> outputs,
        List<StaticModifierDefinition> staticModifiers,
        Optional<ResourceLocation> workstationCapability,
        boolean selfLoopPermitted,
        boolean crossSpeciesPermitted
) {
    private static final int MAX_OUTPUTS = 16;

    private static final MapCodec<OperationIdentity> IDENTITY_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DefinitionCodecs.nonBlankString("display_name_key").fieldOf("display_name_key").forGetter(OperationIdentity::displayNameKey),
            ResourceLocation.CODEC.fieldOf("operation_category").forGetter(OperationIdentity::operationCategory)
    ).apply(instance, OperationIdentity::new));

    private static final MapCodec<OperationTransformation> TRANSFORMATION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("input_product").forGetter(OperationTransformation::inputProduct),
            ResourceLocation.CODEC.optionalFieldOf("output_product").forGetter(OperationTransformation::outputProduct),
            ResourceLocation.CODEC.fieldOf("required_input_processing_state").forGetter(OperationTransformation::requiredInputProcessingState),
            ResourceLocation.CODEC.optionalFieldOf("output_processing_state").forGetter(OperationTransformation::outputProcessingState),
            ProcessingOutputDefinition.CODEC.listOf().optionalFieldOf("outputs").forGetter(OperationTransformation::outputs)
    ).apply(instance, OperationTransformation::new));

    private static final MapCodec<OperationExecution> EXECUTION_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DefinitionCodecs.positiveLong("base_duration_milliseconds").fieldOf("base_duration_milliseconds").forGetter(OperationExecution::baseDurationMilliseconds),
            YieldDefinition.CODEC.optionalFieldOf("base_yield").forGetter(OperationExecution::baseYield),
            DefinitionCodecs.intRange("base_quality_delta", -1000, 1000).optionalFieldOf("base_quality_delta").forGetter(OperationExecution::baseQualityDelta),
            StaticModifierDefinition.CODEC.listOf().optionalFieldOf("static_modifiers", List.of()).forGetter(OperationExecution::staticModifiers)
    ).apply(instance, OperationExecution::new));

    private static final MapCodec<OperationRequirements> REQUIREMENTS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DefinitionCodecs.markerList("required_processing_profiles").fieldOf("required_processing_profiles").forGetter(OperationRequirements::requiredProcessingProfiles),
            QuantityDefinition.CODEC.fieldOf("minimum_input_quantity").forGetter(OperationRequirements::minimumInputQuantity),
            DefinitionCodecs.intRange("minimum_cleanliness_factor", 0, 1000).fieldOf("minimum_cleanliness_factor").forGetter(OperationRequirements::minimumCleanlinessFactor),
            DefinitionCodecs.intRange("minimum_equipment_condition_factor", 0, 1000).fieldOf("minimum_equipment_condition_factor").forGetter(OperationRequirements::minimumEquipmentConditionFactor),
            ResourceLocation.CODEC.optionalFieldOf("workstation_capability").forGetter(OperationRequirements::workstationCapability)
    ).apply(instance, OperationRequirements::new));

    private static final MapCodec<OperationPolicies> POLICIES_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ZeroOutputPolicy.CODEC.fieldOf("zero_output_policy").forGetter(OperationPolicies::zeroOutputPolicy),
            Codec.BOOL.optionalFieldOf("self_loop_permitted", false).forGetter(OperationPolicies::selfLoopPermitted),
            Codec.BOOL.optionalFieldOf("cross_species_permitted", false).forGetter(OperationPolicies::crossSpeciesPermitted)
    ).apply(instance, OperationPolicies::new));

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.<Raw>mapCodec(instance -> instance.group(
            IDENTITY_CODEC.forGetter(Raw::identity),
            TRANSFORMATION_CODEC.forGetter(Raw::transformation),
            EXECUTION_CODEC.forGetter(Raw::execution),
            REQUIREMENTS_CODEC.forGetter(Raw::requirements),
            POLICIES_CODEC.forGetter(Raw::policies)
    ).apply(instance, Raw::new)).codec();

    public static final Codec<ProcessingOperationDefinition> CODEC =
            RAW_CODEC.comapFlatMap(ProcessingOperationDefinition::fromRaw, ProcessingOperationDefinition::toRaw);

    public ProcessingOperationDefinition {
        displayNameKey = Objects.requireNonNull(displayNameKey, "displayNameKey").strip();
        if (displayNameKey.isEmpty()) {
            throw new IllegalArgumentException("Operation display name key cannot be blank");
        }
        Objects.requireNonNull(operationCategory, "operationCategory");
        requiredProcessingProfiles = List.copyOf(Objects.requireNonNull(requiredProcessingProfiles, "requiredProcessingProfiles"));
        if (requiredProcessingProfiles.isEmpty()) {
            throw new IllegalArgumentException("Operation must require at least one processing profile");
        }
        if (requiredProcessingProfiles.size() > DefinitionCodecs.MAX_MARKERS) {
            throw new IllegalArgumentException("Operation profile requirements are bounded to " + DefinitionCodecs.MAX_MARKERS);
        }
        Objects.requireNonNull(inputProduct, "inputProduct");
        Objects.requireNonNull(requiredInputProcessingState, "requiredInputProcessingState");
        if (baseDurationMilliseconds <= 0) {
            throw new IllegalArgumentException("Operation duration must be positive");
        }
        Objects.requireNonNull(minimumInputQuantity, "minimumInputQuantity");
        if (minimumCleanlinessFactor < 0 || minimumCleanlinessFactor > 1000) {
            throw new IllegalArgumentException("Minimum cleanliness factor must be between 0 and 1000");
        }
        if (minimumEquipmentConditionFactor < 0 || minimumEquipmentConditionFactor > 1000) {
            throw new IllegalArgumentException("Minimum equipment condition factor must be between 0 and 1000");
        }
        Objects.requireNonNull(zeroOutputPolicy, "zeroOutputPolicy");
        outputs = List.copyOf(Objects.requireNonNull(outputs, "outputs"));
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Operation must define at least one output");
        }
        if (outputs.size() > MAX_OUTPUTS) {
            throw new IllegalArgumentException("Operation outputs are bounded to " + MAX_OUTPUTS);
        }
        staticModifiers = List.copyOf(Objects.requireNonNull(staticModifiers, "staticModifiers"));
        workstationCapability = Objects.requireNonNull(workstationCapability, "workstationCapability");
    }

    public ProcessingOperationDefinition(
            String displayNameKey,
            ResourceLocation operationCategory,
            List<ResourceLocation> requiredProcessingProfiles,
            ResourceLocation inputProduct,
            ResourceLocation outputProduct,
            ResourceLocation requiredInputProcessingState,
            ResourceLocation outputProcessingState,
            long baseDurationMilliseconds,
            YieldDefinition baseYield,
            int baseQualityDelta,
            QuantityDefinition minimumInputQuantity,
            int minimumCleanlinessFactor,
            int minimumEquipmentConditionFactor,
            ZeroOutputPolicy zeroOutputPolicy,
            List<StaticModifierDefinition> staticModifiers,
            Optional<ResourceLocation> workstationCapability,
            boolean selfLoopPermitted,
            boolean crossSpeciesPermitted
    ) {
        this(
                displayNameKey,
                operationCategory,
                requiredProcessingProfiles,
                inputProduct,
                requiredInputProcessingState,
                baseDurationMilliseconds,
                minimumInputQuantity,
                minimumCleanlinessFactor,
                minimumEquipmentConditionFactor,
                zeroOutputPolicy,
                List.of(new ProcessingOutputDefinition(
                        outputProduct,
                        outputProcessingState,
                        baseYield,
                        baseQualityDelta,
                        minimumInputQuantity.unit(),
                        zeroOutputPolicy.permitsZeroOutput()
                )),
                staticModifiers,
                workstationCapability,
                selfLoopPermitted,
                crossSpeciesPermitted
        );
    }

    public ResourceLocation outputProduct() {
        return outputs.getFirst().product();
    }

    public ResourceLocation outputProcessingState() {
        return outputs.getFirst().state();
    }

    public YieldDefinition baseYield() {
        return outputs.getFirst().yield();
    }

    public int baseQualityDelta() {
        return outputs.getFirst().qualityAdjustment();
    }

    private static DataResult<ProcessingOperationDefinition> fromRaw(Raw raw) {
        try {
            List<ProcessingOutputDefinition> decodedOutputs = raw.transformation.outputs()
                    .orElseGet(() -> legacyOutput(raw));
            return DataResult.success(new ProcessingOperationDefinition(
                    raw.identity.displayNameKey,
                    raw.identity.operationCategory,
                    raw.requirements.requiredProcessingProfiles,
                    raw.transformation.inputProduct,
                    raw.transformation.requiredInputProcessingState,
                    raw.execution.baseDurationMilliseconds,
                    raw.requirements.minimumInputQuantity,
                    raw.requirements.minimumCleanlinessFactor,
                    raw.requirements.minimumEquipmentConditionFactor,
                    raw.policies.zeroOutputPolicy,
                    decodedOutputs,
                    raw.execution.staticModifiers,
                    raw.requirements.workstationCapability,
                    raw.policies.selfLoopPermitted,
                    raw.policies.crossSpeciesPermitted
            ));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(
                new OperationIdentity(displayNameKey, operationCategory),
                new OperationTransformation(
                        inputProduct,
                        Optional.empty(),
                        requiredInputProcessingState,
                        Optional.empty(),
                        Optional.of(outputs)
                ),
                new OperationExecution(baseDurationMilliseconds, Optional.empty(), Optional.empty(), staticModifiers),
                new OperationRequirements(
                        requiredProcessingProfiles,
                        minimumInputQuantity,
                        minimumCleanlinessFactor,
                        minimumEquipmentConditionFactor,
                        workstationCapability
                ),
                new OperationPolicies(zeroOutputPolicy, selfLoopPermitted, crossSpeciesPermitted)
        );
    }

    private static List<ProcessingOutputDefinition> legacyOutput(Raw raw) {
        ResourceLocation outputProduct = raw.transformation.outputProduct()
                .orElseThrow(() -> new IllegalArgumentException("Operation must define outputs or output_product"));
        ResourceLocation outputProcessingState = raw.transformation.outputProcessingState()
                .orElseThrow(() -> new IllegalArgumentException("Operation must define outputs or output_processing_state"));
        YieldDefinition baseYield = raw.execution.baseYield()
                .orElseThrow(() -> new IllegalArgumentException("Operation must define outputs or base_yield"));
        int baseQualityDelta = raw.execution.baseQualityDelta()
                .orElseThrow(() -> new IllegalArgumentException("Operation must define outputs or base_quality_delta"));
        return List.of(new ProcessingOutputDefinition(
                outputProduct,
                outputProcessingState,
                baseYield,
                baseQualityDelta,
                raw.requirements.minimumInputQuantity().unit(),
                raw.policies.zeroOutputPolicy().permitsZeroOutput()
        ));
    }

    private record OperationIdentity(
            String displayNameKey,
            ResourceLocation operationCategory
    ) {
    }

    private record OperationTransformation(
            ResourceLocation inputProduct,
            Optional<ResourceLocation> outputProduct,
            ResourceLocation requiredInputProcessingState,
            Optional<ResourceLocation> outputProcessingState,
            Optional<List<ProcessingOutputDefinition>> outputs
    ) {
    }

    private record OperationExecution(
            long baseDurationMilliseconds,
            Optional<YieldDefinition> baseYield,
            Optional<Integer> baseQualityDelta,
            List<StaticModifierDefinition> staticModifiers
    ) {
    }

    private record OperationRequirements(
            List<ResourceLocation> requiredProcessingProfiles,
            QuantityDefinition minimumInputQuantity,
            int minimumCleanlinessFactor,
            int minimumEquipmentConditionFactor,
            Optional<ResourceLocation> workstationCapability
    ) {
    }

    private record OperationPolicies(
            ZeroOutputPolicy zeroOutputPolicy,
            boolean selfLoopPermitted,
            boolean crossSpeciesPermitted
    ) {
    }

    private record Raw(
            OperationIdentity identity,
            OperationTransformation transformation,
            OperationExecution execution,
            OperationRequirements requirements,
            OperationPolicies policies
    ) {
    }
}
