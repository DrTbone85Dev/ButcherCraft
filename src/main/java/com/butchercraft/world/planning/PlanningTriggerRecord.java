package com.butchercraft.world.planning;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public record PlanningTriggerRecord(
        int schemaVersion,
        String triggerIdentity,
        String triggerContentIdentity,
        String sourceOwner,
        long authoritativeSimulationTick,
        PlanningTriggerType triggerType,
        String sourceReference,
        String sourceFreshnessIdentity,
        Map<String, String> payloadMetadata
) implements Comparable<PlanningTriggerRecord> {
    static final Comparator<PlanningTriggerRecord> ORDERING = Comparator
            .comparingLong(PlanningTriggerRecord::authoritativeSimulationTick)
            .thenComparing(PlanningTriggerRecord::sourceOwner)
            .thenComparing(value -> value.triggerType().name())
            .thenComparing(PlanningTriggerRecord::sourceReference)
            .thenComparing(PlanningTriggerRecord::sourceFreshnessIdentity)
            .thenComparing(PlanningTriggerRecord::triggerContentIdentity)
            .thenComparing(PlanningTriggerRecord::triggerIdentity);

    public PlanningTriggerRecord {
        schemaVersion = PlanningValidation.schema(schemaVersion);
        triggerIdentity = PlanningValidation.id(triggerIdentity, "Planning Trigger Identity");
        triggerContentIdentity = PlanningValidation.id(
                triggerContentIdentity,
                "Planning Trigger Content Identity"
        );
        sourceOwner = PlanningValidation.id(sourceOwner, "Planning trigger source owner");
        authoritativeSimulationTick = PlanningValidation.tick(authoritativeSimulationTick);
        triggerType = Objects.requireNonNull(triggerType, "triggerType");
        sourceReference = PlanningValidation.id(sourceReference, "Planning trigger source reference");
        sourceFreshnessIdentity = PlanningValidation.id(
                sourceFreshnessIdentity,
                "Planning trigger source freshness identity"
        );
        payloadMetadata = PlanningValidation.metadata(payloadMetadata);
    }

    public static PlanningTriggerRecord sourceOwned(
            String sourceOwner,
            long authoritativeSimulationTick,
            PlanningTriggerType triggerType,
            String sourceReference,
            String sourceFreshnessIdentity,
            Map<String, String> payloadMetadata
    ) {
        Map<String, String> metadata = PlanningValidation.metadata(payloadMetadata);
        String owner = PlanningValidation.id(sourceOwner, "Planning trigger source owner");
        String reference = PlanningValidation.id(sourceReference, "Planning trigger source reference");
        String freshness = PlanningValidation.id(
                sourceFreshnessIdentity,
                "Planning trigger source freshness identity"
        );
        long tick = PlanningValidation.tick(authoritativeSimulationTick);
        String type = Objects.requireNonNull(triggerType, "triggerType").name();
        String canonicalPayload = PlanningValidation.canonicalMap(metadata);
        String contentIdentity = PlanningValidation.derivedId(
                "planning_trigger_content",
                Integer.toString(PlanningValidation.SCHEMA_VERSION),
                owner,
                Long.toString(tick),
                type,
                reference,
                freshness,
                canonicalPayload
        );
        String triggerIdentity = PlanningValidation.derivedId(
                "planning_trigger",
                Integer.toString(PlanningValidation.SCHEMA_VERSION),
                owner,
                Long.toString(tick),
                type,
                reference,
                freshness,
                canonicalPayload,
                contentIdentity
        );
        return new PlanningTriggerRecord(
                PlanningValidation.SCHEMA_VERSION,
                triggerIdentity,
                contentIdentity,
                owner,
                tick,
                triggerType,
                reference,
                freshness,
                metadata
        );
    }

    boolean sameContentAs(PlanningTriggerRecord other) {
        Objects.requireNonNull(other, "other");
        return triggerContentIdentity.equals(other.triggerContentIdentity)
                && sourceOwner.equals(other.sourceOwner)
                && authoritativeSimulationTick == other.authoritativeSimulationTick
                && triggerType == other.triggerType
                && sourceReference.equals(other.sourceReference)
                && sourceFreshnessIdentity.equals(other.sourceFreshnessIdentity)
                && payloadMetadata.equals(other.payloadMetadata);
    }

    @Override
    public int compareTo(PlanningTriggerRecord other) {
        return ORDERING.compare(this, other);
    }
}
