package com.butchercraft.integration.materialhandling;

import com.butchercraft.workstation.endpoint.WorkstationEndpointJournal;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalV2;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntime;
import com.butchercraft.world.materialhandling.MaterialHandlingRuntimeV2;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.persistence.MaterialHandlingStorageV2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cross-owner startup classifier. It can authorize candidates but mutates neither owner. */
public final class StackAwareEndpointMigrationGate {
    public Assessment assess(Input input) {
        Objects.requireNonNull(input, "input");
        List<Blocker> blockers = new ArrayList<>();
        for (var record : input.legacyEndpointJournal().records()) {
            if (record.state() == WorkstationEndpointJournalState.RECOVERY_REQUIRED) {
                blockers.add(Blocker.RECOVERY_REQUIRED_ENDPOINT);
            } else if (record.state() == WorkstationEndpointJournalState.UNKNOWN_OUTCOME) {
                blockers.add(Blocker.UNKNOWN_OUTCOME_ENDPOINT);
            } else if (record.state() != WorkstationEndpointJournalState.RECONCILED
                    && record.state() != WorkstationEndpointJournalState.REJECTED
                    && record.state() != WorkstationEndpointJournalState.FAILED) {
                blockers.add(Blocker.UNRESOLVED_ENDPOINT_EFFECT);
            }
        }
        for (var transfer : input.legacyMaterialHandling().transfers()) {
            if (transfer.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED) {
                blockers.add(Blocker.RECOVERY_REQUIRED_TRANSFER);
            } else if (transfer.lifecycle() == MaterialTransferLifecycle.UNKNOWN_OUTCOME) {
                blockers.add(Blocker.UNKNOWN_OUTCOME_TRANSFER);
            } else if (!transfer.lifecycle().terminal()) {
                blockers.add(Blocker.ACTIVE_SCHEMA_1_TRANSFER);
            }
        }
        if (!input.workstationProjectionReconciled()) blockers.add(Blocker.STALE_WORKSTATION_PROJECTION);
        if (input.activeWorkforceAssignments() > 0) blockers.add(Blocker.ACTIVE_WORKFORCE_ASSIGNMENT);
        if (!input.executionCompatibilityProven()) blockers.add(Blocker.INCOMPATIBLE_EXECUTION_WORK);
        if (input.evidenceConflict()) blockers.add(Blocker.EVIDENCE_CONFLICT);
        if (input.sequenceRegression()) blockers.add(Blocker.SEQUENCE_REGRESSION);
        return new Assessment(blockers.isEmpty() ? Decision.ELIGIBLE : Decision.BLOCKED,
                blockers.stream().distinct().toList());
    }

    public WorkstationEndpointJournalV2 migrateEndpointCandidate(
            WorkstationEndpointJournalV2Storage.LegacyJournal legacy,
            String stackAwareConfigurationIdentity,
            Assessment assessment
    ) {
        Objects.requireNonNull(legacy, "legacy");
        Objects.requireNonNull(assessment, "assessment");
        if (assessment.decision() != Decision.ELIGIBLE) {
            throw new IllegalStateException("Schema-2 endpoint migration is blocked: " + assessment.blockers());
        }
        return WorkstationEndpointJournalV2.migratedFromLegacy(
                legacy.journal(), legacy.immutableCanonicalJson(), stackAwareConfigurationIdentity
        );
    }

    public MaterialHandlingRuntimeV2 migrateMaterialHandlingCandidate(
            MaterialHandlingStorageV2.LegacyRuntime legacy,
            String stackAwareConfigurationIdentity,
            Assessment assessment
    ) {
        Objects.requireNonNull(legacy, "legacy");
        Objects.requireNonNull(assessment, "assessment");
        if (assessment.decision() != Decision.ELIGIBLE) {
            throw new IllegalStateException("Schema-2 Material Handling migration is blocked: "
                    + assessment.blockers());
        }
        return MaterialHandlingRuntimeV2.migratedFromLegacy(
                legacy.runtime(), legacy.immutableCanonicalJson(), stackAwareConfigurationIdentity
        );
    }

    public record Input(
            WorkstationEndpointJournal legacyEndpointJournal,
            MaterialHandlingRuntime legacyMaterialHandling,
            boolean workstationProjectionReconciled,
            int activeWorkforceAssignments,
            boolean executionCompatibilityProven,
            boolean evidenceConflict,
            boolean sequenceRegression
    ) {
        public Input {
            legacyEndpointJournal = Objects.requireNonNull(legacyEndpointJournal, "legacyEndpointJournal");
            legacyMaterialHandling = Objects.requireNonNull(legacyMaterialHandling, "legacyMaterialHandling");
            if (activeWorkforceAssignments < 0) {
                throw new IllegalArgumentException("Active Workforce assignment count must not be negative");
            }
            if (!legacyEndpointJournal.worldIdentity().equals(legacyMaterialHandling.worldIdentity())) {
                throw new IllegalArgumentException("Migration owners must bind the same World Identity");
            }
        }
    }

    public record Assessment(Decision decision, List<Blocker> blockers) {
        public Assessment {
            decision = Objects.requireNonNull(decision, "decision");
            blockers = List.copyOf(Objects.requireNonNull(blockers, "blockers"));
            if ((decision == Decision.ELIGIBLE) != blockers.isEmpty()) {
                throw new IllegalArgumentException("Migration decision must agree with its blocker set");
            }
        }
    }

    public enum Decision { ELIGIBLE, BLOCKED }

    public enum Blocker {
        ACTIVE_SCHEMA_1_TRANSFER,
        RECOVERY_REQUIRED_TRANSFER,
        UNKNOWN_OUTCOME_TRANSFER,
        UNRESOLVED_ENDPOINT_EFFECT,
        RECOVERY_REQUIRED_ENDPOINT,
        UNKNOWN_OUTCOME_ENDPOINT,
        STALE_WORKSTATION_PROJECTION,
        ACTIVE_WORKFORCE_ASSIGNMENT,
        INCOMPATIBLE_EXECUTION_WORK,
        EVIDENCE_CONFLICT,
        SEQUENCE_REGRESSION
    }
}
