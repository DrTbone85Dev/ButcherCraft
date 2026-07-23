package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.Optional;

public record ExternalReference(
        String referenceTypeId,
        String stableExternalId,
        String authoritativeSubsystemId,
        Optional<String> roleId
) implements Comparable<ExternalReference> {
    private static final Comparator<ExternalReference> ORDER = Comparator
            .comparing(ExternalReference::referenceTypeId)
            .thenComparing(ExternalReference::stableExternalId)
            .thenComparing(ExternalReference::authoritativeSubsystemId)
            .thenComparing(reference -> reference.roleId().orElse(""));

    public ExternalReference {
        referenceTypeId = AllocationValidation.id(referenceTypeId, "referenceTypeId");
        stableExternalId = AllocationValidation.id(stableExternalId, "stableExternalId");
        authoritativeSubsystemId = AllocationValidation.id(
                authoritativeSubsystemId,
                "authoritativeSubsystemId"
        );
        roleId = AllocationValidation.required(roleId, "roleId")
                .map(value -> AllocationValidation.id(value, "roleId"));
    }

    public static ExternalReference of(
            String referenceTypeId,
            String stableExternalId,
            String authoritativeSubsystemId
    ) {
        return new ExternalReference(
                referenceTypeId,
                stableExternalId,
                authoritativeSubsystemId,
                Optional.empty()
        );
    }

    public ExternalReference withRole(String roleId) {
        return new ExternalReference(
                referenceTypeId,
                stableExternalId,
                authoritativeSubsystemId,
                Optional.of(roleId)
        );
    }

    public String canonicalKey() {
        return referenceTypeId + "|" + stableExternalId + "|"
                + authoritativeSubsystemId + "|" + roleId.orElse("");
    }

    @Override
    public int compareTo(ExternalReference other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
