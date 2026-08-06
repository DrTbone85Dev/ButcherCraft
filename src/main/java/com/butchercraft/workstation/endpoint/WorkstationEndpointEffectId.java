package com.butchercraft.workstation.endpoint;

public record WorkstationEndpointEffectId(String value) implements Comparable<WorkstationEndpointEffectId> {
    private static final String PREFIX = "butchercraft:workstation_endpoint_effect/v1/";

    public WorkstationEndpointEffectId {
        value = WorkstationEndpointValidation.id(value, "workstation endpoint effect identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Workstation endpoint effect identity has unsupported prefix");
        }
    }

    public static WorkstationEndpointEffectId create(
            WorkstationInstanceId instanceId,
            String invocationIdentity,
            WorkstationEndpointEffectKind kind
    ) {
        WorkstationEndpointValidation.id(invocationIdentity, "endpoint invocation identity");
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_effect")
                .add(WorkstationEndpointSchema.CURRENT_VERSION)
                .add(instanceId.value())
                .add(invocationIdentity)
                .add(kind.name())
                .finish();
        return new WorkstationEndpointEffectId(PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest));
    }

    @Override
    public int compareTo(WorkstationEndpointEffectId other) {
        return value.compareTo(other.value);
    }
}
