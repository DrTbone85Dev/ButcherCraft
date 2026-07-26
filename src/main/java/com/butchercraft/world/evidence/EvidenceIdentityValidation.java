package com.butchercraft.world.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record EvidenceIdentityValidation(
        Optional<EvidenceIdentity> identity,
        List<EvidenceLifecycleFailure> failures
) {
    public EvidenceIdentityValidation {
        identity = Objects.requireNonNull(identity, "identity");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        failures.forEach(failure -> Objects.requireNonNull(failure, "failure"));
        if (identity.isPresent() != failures.isEmpty()) {
            throw new IllegalArgumentException("Identity validation must contain either an identity or failures");
        }
    }

    public static EvidenceIdentityValidation successful(EvidenceIdentity identity) {
        return new EvidenceIdentityValidation(Optional.of(identity), List.of());
    }

    public static EvidenceIdentityValidation failed(List<EvidenceLifecycleFailure> failures) {
        return new EvidenceIdentityValidation(Optional.empty(), failures);
    }

    public boolean successful() {
        return identity.isPresent();
    }
}
