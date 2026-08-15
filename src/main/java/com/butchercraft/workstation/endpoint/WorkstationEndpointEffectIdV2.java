package com.butchercraft.workstation.endpoint;

import java.util.Objects;

public record WorkstationEndpointEffectIdV2(String value) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_effect/v2/";

    public WorkstationEndpointEffectIdV2 {
        value = WorkstationEndpointValidation.id(value, "schema-2 endpoint effect identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported schema-2 endpoint Effect Identity prefix");
        }
    }

    public static WorkstationEndpointEffectIdV2 create(
            WorkstationInstanceId instanceId,
            String invocationIdentity,
            WorkstationEndpointEffectKind effectKind
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        invocationIdentity = WorkstationEndpointValidation.id(invocationIdentity, "endpoint invocation identity");
        Objects.requireNonNull(effectKind, "effectKind");
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_effect")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION)
                .add(instanceId.value())
                .add(invocationIdentity)
                .add(effectKind.name())
                .finish();
        return new WorkstationEndpointEffectIdV2(PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest));
    }
}
