package com.butchercraft.world.production;

import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.goods.IndustryId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ProductionProcessDefinition(
        ProductionProcessId id,
        String displayName,
        IndustryId owningIndustryId,
        ActorCapability requiredActorCapability,
        Set<ActorCapability> additionalRequiredCapabilities,
        List<ProductionInputDefinition> inputs,
        List<ProductionOutputDefinition> outputs,
        List<ProductionTransformationReference> transformationReferences,
        ProductionDuration duration,
        ProductionBatchPolicy batchPolicy,
        ProductionWorkforceRequirement workforceRequirement,
        ProductionBusinessRequirement businessRequirement,
        ProductionExecutionPolicy executionPolicy,
        ProductionMetadata metadata,
        int schemaVersion
) {
    public ProductionProcessDefinition {
        id = Objects.requireNonNull(id, "id");
        displayName = ProductionValidation.requireText(displayName, "Production process display name", 256);
        owningIndustryId = Objects.requireNonNull(owningIndustryId, "owningIndustryId");
        requiredActorCapability = Objects.requireNonNull(requiredActorCapability, "requiredActorCapability");
        EnumSet<ActorCapability> capabilities = EnumSet.noneOf(ActorCapability.class);
        Objects.requireNonNull(additionalRequiredCapabilities, "additionalRequiredCapabilities")
                .forEach(capability -> capabilities.add(Objects.requireNonNull(capability, "capability")));
        if (capabilities.contains(requiredActorCapability)) {
            throw new IllegalArgumentException("Required capability must not be duplicated");
        }
        additionalRequiredCapabilities = Collections.unmodifiableSet(capabilities);
        inputs = copyInputs(inputs);
        outputs = copyOutputs(outputs);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalArgumentException("Production process requires at least one input and one output");
        }
        rejectDuplicateLines(inputs, outputs);
        transformationReferences = Objects.requireNonNull(transformationReferences, "transformationReferences")
                .stream().map(reference -> Objects.requireNonNull(reference, "transformationReference"))
                .sorted().distinct().toList();
        duration = Objects.requireNonNull(duration, "duration");
        batchPolicy = Objects.requireNonNull(batchPolicy, "batchPolicy");
        workforceRequirement = Objects.requireNonNull(workforceRequirement, "workforceRequirement");
        businessRequirement = Objects.requireNonNull(businessRequirement, "businessRequirement");
        executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy");
        metadata = Objects.requireNonNull(metadata, "metadata");
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production process");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<ActorCapability> allRequiredCapabilities() {
        EnumSet<ActorCapability> capabilities = EnumSet.noneOf(ActorCapability.class);
        capabilities.addAll(additionalRequiredCapabilities);
        capabilities.add(requiredActorCapability);
        return Collections.unmodifiableSet(capabilities);
    }

    private static List<ProductionInputDefinition> copyInputs(List<ProductionInputDefinition> source) {
        List<ProductionInputDefinition> copied = Objects.requireNonNull(source, "inputs").stream()
                .map(input -> Objects.requireNonNull(input, "input")).sorted(java.util.Comparator.comparing(
                        ProductionInputDefinition::id)).toList();
        if (copied.size() > ProductionSchema.MAXIMUM_INPUT_LINES) {
            throw new IllegalArgumentException("Production process has too many input lines");
        }
        return List.copyOf(copied);
    }

    private static List<ProductionOutputDefinition> copyOutputs(List<ProductionOutputDefinition> source) {
        List<ProductionOutputDefinition> copied = Objects.requireNonNull(source, "outputs").stream()
                .map(output -> Objects.requireNonNull(output, "output")).sorted(java.util.Comparator.comparing(
                        ProductionOutputDefinition::id)).toList();
        if (copied.size() > ProductionSchema.MAXIMUM_OUTPUT_LINES) {
            throw new IllegalArgumentException("Production process has too many output lines");
        }
        return List.copyOf(copied);
    }

    private static void rejectDuplicateLines(
            List<ProductionInputDefinition> inputs,
            List<ProductionOutputDefinition> outputs
    ) {
        Set<ProductionLineId> ids = new HashSet<>();
        for (ProductionInputDefinition input : inputs) {
            if (!ids.add(input.id())) throw new IllegalArgumentException("Duplicate production line id: " + input.id());
        }
        for (ProductionOutputDefinition output : outputs) {
            if (!ids.add(output.id())) throw new IllegalArgumentException("Duplicate production line id: " + output.id());
        }
    }

    public static final class Builder {
        private ProductionProcessId id;
        private String displayName;
        private IndustryId owningIndustryId;
        private ActorCapability requiredActorCapability = ActorCapability.TRANSFORM;
        private final Set<ActorCapability> additionalCapabilities = EnumSet.noneOf(ActorCapability.class);
        private final List<ProductionInputDefinition> inputs = new ArrayList<>();
        private final List<ProductionOutputDefinition> outputs = new ArrayList<>();
        private final List<ProductionTransformationReference> transformations = new ArrayList<>();
        private ProductionDuration duration;
        private ProductionBatchPolicy batchPolicy;
        private ProductionWorkforceRequirement workforceRequirement = ProductionWorkforceRequirement.none();
        private ProductionBusinessRequirement businessRequirement = ProductionBusinessRequirement.none();
        private ProductionExecutionPolicy executionPolicy = ProductionExecutionPolicy.standard();
        private ProductionMetadata metadata = ProductionMetadata.empty();
        private int schemaVersion = ProductionSchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(ProductionProcessId value) { id = value; return this; }
        public Builder id(String value) { return id(ProductionProcessId.of(value)); }
        public Builder displayName(String value) { displayName = value; return this; }
        public Builder owningIndustryId(IndustryId value) { owningIndustryId = value; return this; }
        public Builder requiredActorCapability(ActorCapability value) { requiredActorCapability = value; return this; }
        public Builder additionalCapability(ActorCapability value) { additionalCapabilities.add(value); return this; }
        public Builder input(ProductionInputDefinition value) { inputs.add(value); return this; }
        public Builder output(ProductionOutputDefinition value) { outputs.add(value); return this; }
        public Builder transformationReference(ProductionTransformationReference value) {
            transformations.add(value); return this;
        }
        public Builder duration(ProductionDuration value) { duration = value; return this; }
        public Builder batchPolicy(ProductionBatchPolicy value) { batchPolicy = value; return this; }
        public Builder workforceRequirement(ProductionWorkforceRequirement value) {
            workforceRequirement = value; return this;
        }
        public Builder businessRequirement(ProductionBusinessRequirement value) {
            businessRequirement = value; return this;
        }
        public Builder executionPolicy(ProductionExecutionPolicy value) { executionPolicy = value; return this; }
        public Builder metadata(ProductionMetadata value) { metadata = value; return this; }
        public Builder schemaVersion(int value) { schemaVersion = value; return this; }

        public ProductionProcessDefinition build() {
            return new ProductionProcessDefinition(
                    id, displayName, owningIndustryId, requiredActorCapability, additionalCapabilities,
                    inputs, outputs, transformations, duration, batchPolicy, workforceRequirement,
                    businessRequirement, executionPolicy, metadata, schemaVersion
            );
        }
    }
}
