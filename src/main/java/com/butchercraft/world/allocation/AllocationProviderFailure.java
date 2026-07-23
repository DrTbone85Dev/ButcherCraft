package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalLong;

public record AllocationProviderFailure(
        AllocationProviderFailureCode code,
        AllocationProviderFailureScope scope,
        Optional<AllocationProviderId> providerId,
        String subject,
        String message,
        OptionalLong simulationTick,
        int schemaVersion
) implements Comparable<AllocationProviderFailure> {
    private static final Comparator<AllocationProviderFailure> ORDER = Comparator
            .comparing(AllocationProviderFailure::scope)
            .thenComparing(failure -> failure.providerId().orElse(null),
                    Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(AllocationProviderFailure::code)
            .thenComparing(AllocationProviderFailure::subject)
            .thenComparing(AllocationProviderFailure::message);

    public AllocationProviderFailure {
        code = AllocationValidation.required(code, "code");
        scope = AllocationValidation.required(scope, "scope");
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
        simulationTick = AllocationValidation.optionalTick(simulationTick, "simulationTick");
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }

    public static AllocationProviderFailure provider(
            AllocationProviderFailureCode code,
            AllocationProviderFailureScope scope,
            AllocationProviderId providerId,
            String subject,
            String message,
            long simulationTick
    ) {
        return new AllocationProviderFailure(
                code,
                scope,
                Optional.of(AllocationValidation.required(providerId, "providerId")),
                subject,
                message,
                OptionalLong.of(AllocationValidation.tick(simulationTick, "simulationTick")),
                AllocationSchema.CURRENT_VERSION
        );
    }

    public static AllocationProviderFailure global(
            AllocationProviderFailureCode code,
            AllocationProviderFailureScope scope,
            String subject,
            String message,
            OptionalLong simulationTick
    ) {
        return new AllocationProviderFailure(
                code,
                scope,
                Optional.empty(),
                subject,
                message,
                simulationTick,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.failure(this);
    }

    @Override
    public int compareTo(AllocationProviderFailure other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
