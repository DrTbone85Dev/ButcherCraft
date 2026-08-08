package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

public record WorkstationEndpointOwnerResult(
        String evidenceIdentity,
        String contentDigest,
        int schemaVersion,
        long journalSequence,
        WorkstationEndpointEffectId effectId,
        WorkstationInstanceId instanceId,
        String invocationIdentity,
        WorkstationEndpointEffectKind effectKind,
        WorkstationEndpointResultCode resultCode,
        WorkstationEndpointStackPayload exactStack,
        long preInventoryRevision,
        long postInventoryRevision,
        long preEndpointEffectRevision,
        long endpointEffectRevision,
        WorkstationEndpointFreshnessIdentity preFreshnessIdentity,
        WorkstationEndpointFreshnessIdentity postFreshnessIdentity,
        String endpointConfigurationIdentity,
        Optional<String> failureDetail
) {
    private static final String EVIDENCE_PREFIX = "butchercraft:workstation_endpoint_result/v1/";

    public WorkstationEndpointOwnerResult {
        evidenceIdentity = WorkstationEndpointValidation.id(evidenceIdentity, "endpoint result evidence identity");
        if (!evidenceIdentity.startsWith(EVIDENCE_PREFIX)) {
            throw new IllegalArgumentException("Endpoint result evidence identity has unsupported prefix");
        }
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "endpoint result content digest");
        schemaVersion = WorkstationEndpointValidation.schema(schemaVersion, "endpoint result schema version");
        journalSequence = WorkstationEndpointValidation.positive(journalSequence, "endpoint journal sequence");
        effectId = Objects.requireNonNull(effectId, "effectId");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        invocationIdentity = WorkstationEndpointValidation.id(invocationIdentity, "endpoint invocation identity");
        effectKind = Objects.requireNonNull(effectKind, "effectKind");
        resultCode = Objects.requireNonNull(resultCode, "resultCode");
        exactStack = Objects.requireNonNull(exactStack, "exactStack");
        preInventoryRevision = WorkstationEndpointValidation.nonNegative(preInventoryRevision, "pre-inventory revision");
        postInventoryRevision = WorkstationEndpointValidation.nonNegative(postInventoryRevision, "post-inventory revision");
        preEndpointEffectRevision = WorkstationEndpointValidation.nonNegative(
                preEndpointEffectRevision,
                "pre-endpoint effect revision"
        );
        endpointEffectRevision = WorkstationEndpointValidation.positive(endpointEffectRevision, "endpoint effect revision");
        preFreshnessIdentity = Objects.requireNonNull(preFreshnessIdentity, "preFreshnessIdentity");
        postFreshnessIdentity = Objects.requireNonNull(postFreshnessIdentity, "postFreshnessIdentity");
        endpointConfigurationIdentity = WorkstationEndpointValidation.id(
                endpointConfigurationIdentity,
                "endpoint configuration identity"
        );
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail")
                .map(value -> WorkstationEndpointValidation.text(value, "endpoint result failure detail"));
        if (postInventoryRevision != Math.addExact(preInventoryRevision, 1L)
                || endpointEffectRevision != Math.addExact(preEndpointEffectRevision, 1L)) {
            throw new IllegalArgumentException("Applied endpoint result must bind the exact next revisions");
        }
        String expectedDigest = calculateDigest(
                journalSequence,
                schemaVersion,
                effectId,
                instanceId,
                invocationIdentity,
                effectKind,
                resultCode,
                exactStack,
                preInventoryRevision,
                postInventoryRevision,
                preEndpointEffectRevision,
                endpointEffectRevision,
                preFreshnessIdentity,
                postFreshnessIdentity,
                endpointConfigurationIdentity,
                failureDetail
        );
        if (!expectedDigest.equals(contentDigest)
                || !(EVIDENCE_PREFIX + WorkstationEndpointCanonicalDigest.suffix(expectedDigest)).equals(evidenceIdentity)) {
            throw new IllegalArgumentException("Endpoint owner result evidence is not canonical");
        }
    }

    public static WorkstationEndpointOwnerResult create(
            WorkstationEndpointJournalRecord record,
            WorkstationEndpointResultCode resultCode,
            Optional<String> failureDetail
    ) {
        String digest = calculateDigest(
                record.journalSequence(),
                WorkstationEndpointSchema.CURRENT_VERSION,
                record.effectId(),
                record.instanceId(),
                record.invocationIdentity(),
                record.effectKind(),
                resultCode,
                record.exactStack(),
                record.expectedInventoryRevision(),
                record.postInventoryRevision(),
                record.expectedEndpointEffectRevision(),
                record.endpointEffectRevision(),
                record.preFreshnessIdentity(),
                record.postFreshnessIdentity(),
                record.endpointConfigurationIdentity(),
                failureDetail
        );
        return new WorkstationEndpointOwnerResult(
                EVIDENCE_PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest),
                digest,
                WorkstationEndpointSchema.CURRENT_VERSION,
                record.journalSequence(),
                record.effectId(),
                record.instanceId(),
                record.invocationIdentity(),
                record.effectKind(),
                resultCode,
                record.exactStack(),
                record.expectedInventoryRevision(),
                record.postInventoryRevision(),
                record.expectedEndpointEffectRevision(),
                record.endpointEffectRevision(),
                record.preFreshnessIdentity(),
                record.postFreshnessIdentity(),
                record.endpointConfigurationIdentity(),
                failureDetail
        );
    }

    private static String calculateDigest(
            long journalSequence,
            int schemaVersion,
            WorkstationEndpointEffectId effectId,
            WorkstationInstanceId instanceId,
            String invocationIdentity,
            WorkstationEndpointEffectKind effectKind,
            WorkstationEndpointResultCode resultCode,
            WorkstationEndpointStackPayload exactStack,
            long preInventoryRevision,
            long postInventoryRevision,
            long preEndpointEffectRevision,
            long endpointEffectRevision,
            WorkstationEndpointFreshnessIdentity preFreshnessIdentity,
            WorkstationEndpointFreshnessIdentity postFreshnessIdentity,
            String endpointConfigurationIdentity,
            Optional<String> failureDetail
    ) {
        return WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_result")
                .add(schemaVersion)
                .add(journalSequence)
                .add(effectId.value())
                .add(instanceId.value())
                .add(invocationIdentity)
                .add(effectKind.name())
                .add(resultCode.name())
                .add(exactStack.contentDigest())
                .add(preInventoryRevision)
                .add(postInventoryRevision)
                .add(preEndpointEffectRevision)
                .add(endpointEffectRevision)
                .add(preFreshnessIdentity.value())
                .add(postFreshnessIdentity.value())
                .add(endpointConfigurationIdentity)
                .add(failureDetail.orElse(""))
                .finish();
    }
}
