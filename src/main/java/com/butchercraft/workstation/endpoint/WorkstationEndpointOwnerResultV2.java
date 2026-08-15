package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointOwnerResultV2(
        String evidenceIdentity,
        String contentDigest,
        int protocolVersion,
        WorkstationEndpointPreparationV2 preparation,
        WorkstationEndpointResultCode resultCode,
        Optional<String> failureDetail
) {
    private static final String PREFIX = "butchercraft:workstation_endpoint_result/v2/";

    public WorkstationEndpointOwnerResultV2 {
        evidenceIdentity = WorkstationEndpointValidation.id(evidenceIdentity, "result evidence identity");
        if (!evidenceIdentity.startsWith(PREFIX)) throw new IllegalArgumentException("Unsupported result prefix");
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "result content digest");
        if (protocolVersion != WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION) {
            throw new IllegalArgumentException("Unsupported stack-aware endpoint protocol version");
        }
        preparation = Objects.requireNonNull(preparation, "preparation");
        resultCode = Objects.requireNonNull(resultCode, "resultCode");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail")
                .map(value -> WorkstationEndpointValidation.text(value, "result failure detail"));
        String expectedDigest = digest(preparation, resultCode, failureDetail);
        if (!expectedDigest.equals(contentDigest)
                || !(PREFIX + WorkstationEndpointCanonicalDigest.suffix(expectedDigest)).equals(evidenceIdentity)) {
            throw new IllegalArgumentException("Schema-2 owner result is not canonical");
        }
    }

    public static WorkstationEndpointOwnerResultV2 applied(WorkstationEndpointPreparationV2 preparation) {
        return create(preparation, WorkstationEndpointResultCode.APPLIED, Optional.empty());
    }

    public static WorkstationEndpointOwnerResultV2 create(
            WorkstationEndpointPreparationV2 preparation,
            WorkstationEndpointResultCode resultCode,
            Optional<String> failureDetail
    ) {
        String digest = digest(preparation, resultCode, failureDetail);
        return new WorkstationEndpointOwnerResultV2(
                PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest), digest,
                WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION, preparation, resultCode,
                failureDetail
        );
    }

    public WorkstationInstanceId instanceId() {
        return preparation.observation().instanceId();
    }

    public WorkstationEndpointEffectIdV2 effectId() {
        return preparation.effectId();
    }

    public WorkstationEndpointStackStateV2 preStack() {
        return preparation.observation().preStack();
    }

    public WorkstationEndpointStackStateV2 transferStack() {
        return preparation.observation().transferStack();
    }

    public WorkstationEndpointStackStateV2 remainderStack() {
        return preparation.observation().remainderStack();
    }

    public WorkstationEndpointStackStateV2 postStack() {
        return preparation.observation().postStack();
    }

    public long resultingInventoryRevision() {
        return preparation.postInventoryRevision();
    }

    private static String digest(
            WorkstationEndpointPreparationV2 preparation,
            WorkstationEndpointResultCode resultCode,
            Optional<String> failureDetail
    ) {
        return WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_result")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION)
                .add(preparation.evidenceIdentity()).add(resultCode.name()).add(failureDetail.orElse(""))
                .finish();
    }
}
