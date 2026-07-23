package com.butchercraft.world.allocation;

import java.util.Comparator;

public record AllocationProviderWarning(
        String code,
        AllocationProviderId providerId,
        String subject,
        String message,
        long simulationTick,
        int schemaVersion
) implements Comparable<AllocationProviderWarning> {
    private static final Comparator<AllocationProviderWarning> ORDER = Comparator
            .comparing(AllocationProviderWarning::providerId)
            .thenComparing(AllocationProviderWarning::code)
            .thenComparing(AllocationProviderWarning::subject)
            .thenComparing(AllocationProviderWarning::message);

    public AllocationProviderWarning {
        code = AllocationValidation.id(code, "code");
        providerId = AllocationValidation.required(providerId, "providerId");
        subject = AllocationValidation.text(
                subject,
                "subject",
                AllocationSchema.MAXIMUM_METADATA_VALUE_LENGTH
        );
        message = AllocationValidation.text(
                message,
                "message",
                AllocationSchema.MAXIMUM_RUNTIME_FAILURE_MESSAGE_LENGTH
        );
        simulationTick = AllocationValidation.tick(simulationTick, "simulationTick");
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.warning(this);
    }

    @Override
    public int compareTo(AllocationProviderWarning other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
