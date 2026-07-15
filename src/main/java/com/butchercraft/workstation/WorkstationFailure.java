package com.butchercraft.workstation;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record WorkstationFailure(
        WorkstationFailureCode code,
        List<ResourceLocation> relevantIds,
        String developerExplanation
) {
    public WorkstationFailure {
        Objects.requireNonNull(code, "code");
        relevantIds = List.copyOf(Objects.requireNonNull(relevantIds, "relevantIds"));
        developerExplanation = Objects.requireNonNull(developerExplanation, "developerExplanation").strip();
    }

    public static WorkstationFailure of(WorkstationFailureCode code, String developerExplanation) {
        return new WorkstationFailure(code, List.of(), developerExplanation);
    }

    public static WorkstationFailure of(
            WorkstationFailureCode code,
            ResourceLocation relevantId,
            String developerExplanation
    ) {
        return new WorkstationFailure(code, List.of(Objects.requireNonNull(relevantId, "relevantId")), developerExplanation);
    }

    public String messageKey() {
        return code.messageKey();
    }
}
