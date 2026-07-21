package com.butchercraft.workstation;

import com.butchercraft.engine.EngineId;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record WorkstationCapability(
        ResourceLocation id,
        Set<ResourceLocation> supportedOperationCategories,
        Set<ResourceLocation> supportedWorkstationCapabilities,
        Set<ResourceLocation> allowedProcessingProfiles,
        long maxInputQuantity,
        boolean manualPlayerOperationAllowed,
        boolean automatedEmployeeOperationMayBeSupportedLater,
        int inputSlots,
        int outputSlots
) {
    public WorkstationCapability {
        Objects.requireNonNull(id, "id");
        supportedOperationCategories = Set.copyOf(Objects.requireNonNull(supportedOperationCategories, "supportedOperationCategories"));
        supportedWorkstationCapabilities = Set.copyOf(Objects.requireNonNull(supportedWorkstationCapabilities, "supportedWorkstationCapabilities"));
        allowedProcessingProfiles = Set.copyOf(Objects.requireNonNull(allowedProcessingProfiles, "allowedProcessingProfiles"));
        if (maxInputQuantity <= 0) {
            throw new IllegalArgumentException("Maximum input quantity must be positive");
        }
        if (inputSlots <= 0) {
            throw new IllegalArgumentException("Workstations must support at least one input slot");
        }
        if (outputSlots <= 0) {
            throw new IllegalArgumentException("Workstations must support at least one output slot");
        }
    }

    public boolean allowsProcessingProfile(ResourceLocation processingProfile) {
        return allowedProcessingProfiles.isEmpty() || allowedProcessingProfiles.contains(processingProfile);
    }

    public boolean supportsOperationCategory(ResourceLocation operationCategory) {
        return supportedOperationCategories.contains(operationCategory);
    }

    public boolean supportsWorkstationCapability(ResourceLocation workstationCapability) {
        return supportedWorkstationCapabilities.contains(workstationCapability);
    }

    public com.butchercraft.transformation.WorkstationCapability toTransformationCapability() {
        return new com.butchercraft.transformation.WorkstationCapability(
                EngineId.of(id.toString()),
                supportedWorkstationCapabilities.stream()
                        .map(ResourceLocation::toString)
                        .map(EngineId::of)
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
}
