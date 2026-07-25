package com.butchercraft.world.checkpoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class CheckpointRecoveryEvaluator {
    public CheckpointIntegrityResult validateGenerationId(CheckpointGenerationId generationId) {
        Objects.requireNonNull(generationId, "generationId");
        List<CheckpointFailure> failures = new ArrayList<>();
        if (generationId.schemaVersion() != CheckpointSchema.CURRENT_VERSION) {
            failures.add(failure(
                    CheckpointFailureCode.UNSUPPORTED_SCHEMA,
                    "generationId.schemaVersion",
                    "Unsupported checkpoint generation schema version: " + generationId.schemaVersion()
            ));
        }
        if (generationId.committedSequence() <= 0L || generationId.authoritativeSimulationTick() < 0L) {
            failures.add(failure(
                    CheckpointFailureCode.INVALID_GENERATION_ID,
                    "generationId",
                    "Checkpoint generation identity must use positive sequence and non-negative tick"
            ));
        }
        return new CheckpointIntegrityResult(failures);
    }

    public CheckpointIntegrityResult validateManifest(
            CheckpointGenerationManifest manifest,
            List<CheckpointOwnerId> requiredOwners,
            WorldIdentityRootReference expectedWorldIdentityRoot,
            PlatformDeterminismManifestReference expectedPlatformDeterminismManifest
    ) {
        Objects.requireNonNull(manifest, "manifest");
        List<CheckpointFailure> failures = new ArrayList<>(validateGenerationId(manifest.generationId()).failures());
        if (manifest.schemaVersion() != CheckpointSchema.CURRENT_VERSION) {
            failures.add(failure(
                    CheckpointFailureCode.UNSUPPORTED_SCHEMA,
                    "manifest.schemaVersion",
                    "Unsupported checkpoint manifest schema version: " + manifest.schemaVersion()
            ));
        }
        if (manifest.generationId().schemaVersion() != manifest.schemaVersion()) {
            failures.add(failure(
                    CheckpointFailureCode.MIXED_GENERATION_IDENTITY,
                    "generationId.schemaVersion",
                    "Manifest schema and generation identity schema differ"
            ));
        }
        if (manifest.generationId().authoritativeSimulationTick() != manifest.authoritativeSimulationTick()) {
            failures.add(failure(
                    CheckpointFailureCode.MIXED_GENERATION_IDENTITY,
                    "authoritativeSimulationTick",
                    "Manifest tick and generation identity tick differ"
            ));
        }
        if (!manifest.digestMatches()) {
            failures.add(failure(
                    CheckpointFailureCode.MANIFEST_DIGEST_MISMATCH,
                    "manifestDigest",
                    "Checkpoint generation manifest digest does not match canonical content"
            ));
        }
        failures.addAll(validateOwnerSnapshots(manifest, requiredOwners));
        if (!manifest.worldIdentityRoot().equals(expectedWorldIdentityRoot)) {
            failures.add(failure(
                    CheckpointFailureCode.WORLD_IDENTITY_MISMATCH,
                    "worldIdentityRoot",
                    "Checkpoint generation belongs to a different World Identity root"
            ));
        }
        if (!manifest.platformDeterminismManifest().equals(expectedPlatformDeterminismManifest)) {
            failures.add(failure(
                    CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH,
                    "platformDeterminismManifest",
                    "Checkpoint generation references a different Platform Determinism Manifest"
            ));
        }
        return new CheckpointIntegrityResult(failures);
    }

    public CheckpointIntegrityResult validateHead(
            CheckpointHeadRecord head,
            Map<CheckpointGenerationId, CheckpointGenerationRecord> generations
    ) {
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(generations, "generations");
        List<CheckpointFailure> failures = new ArrayList<>();
        if (head.schemaVersion() != CheckpointSchema.CURRENT_VERSION) {
            failures.add(failure(
                    CheckpointFailureCode.UNSUPPORTED_SCHEMA,
                    "head.schemaVersion",
                    "Unsupported checkpoint head schema version: " + head.schemaVersion()
            ));
        }
        if (!head.digestMatches()) {
            failures.add(failure(
                    CheckpointFailureCode.HEAD_DIGEST_MISMATCH,
                    "headRecordDigest",
                    "Checkpoint head digest does not match canonical content"
            ));
        }
        if (head.headSequence() != head.selectedGenerationId().committedSequence()) {
            failures.add(failure(
                    CheckpointFailureCode.INVALID_HEAD_RECORD,
                    "headSequence",
                    "Checkpoint head sequence must equal the selected generation sequence"
            ));
        }
        CheckpointGenerationRecord record = generations.get(head.selectedGenerationId());
        if (record == null) {
            failures.add(failure(
                    CheckpointFailureCode.HEAD_REFERENCES_INVALID_GENERATION,
                    "selectedGenerationId",
                    "Checkpoint head references a missing generation"
            ));
            return new CheckpointIntegrityResult(failures);
        }
        if (!record.publicationState().committedHistory()) {
            failures.add(failure(
                    CheckpointFailureCode.HEAD_REFERENCES_UNCOMMITTED_GENERATION,
                    "publicationState",
                    "Checkpoint head references a generation that is not committed history"
            ));
        }
        if (!head.selectedGenerationManifestDigest().equals(record.manifest().manifestDigest())) {
            failures.add(failure(
                    CheckpointFailureCode.MANIFEST_DIGEST_MISMATCH,
                    "selectedGenerationManifestDigest",
                    "Checkpoint head references a different manifest digest than the selected generation"
            ));
        }
        if (!head.predecessorGenerationId().equals(record.manifest().predecessorGenerationId())
                || !head.predecessorManifestDigest().equals(record.manifest().predecessorManifestDigest())) {
            failures.add(failure(
                    CheckpointFailureCode.PREDECESSOR_MISMATCH,
                    "predecessor",
                    "Checkpoint head predecessor metadata does not match the selected generation"
            ));
        }
        return new CheckpointIntegrityResult(failures);
    }

    public CheckpointRecoverySelection selectLatestValidCommitted(
            CheckpointRecoverySelectionRequest request
    ) {
        Objects.requireNonNull(request, "request");
        List<CheckpointFailure> diagnostics = new ArrayList<>();
        diagnostics.addAll(validateGenerationRecordConflicts(request.generations()));
        if (!diagnostics.isEmpty()) {
            return CheckpointRecoverySelection.blocked(diagnostics);
        }
        Map<CheckpointGenerationId, CheckpointGenerationRecord> generationMap = generationMap(request.generations());
        List<CheckpointHeadRecord> heads = request.headRecords().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        Optional<CheckpointGenerationId> highestHeadedGeneration = heads.stream()
                .map(CheckpointHeadRecord::selectedGenerationId)
                .max(Comparator.naturalOrder());

        for (CheckpointHeadRecord head : heads) {
            List<CheckpointFailure> headFailures = new ArrayList<>(validateHead(head, generationMap).failures());
            if (!headFailures.isEmpty()) {
                diagnostics.addAll(headFailures);
                continue;
            }
            CheckpointGenerationRecord record = generationMap.get(head.selectedGenerationId());
            List<CheckpointFailure> chainFailures = validateGenerationChain(
                    record,
                    generationMap,
                    request.requiredOwners(),
                    request.expectedWorldIdentityRoot(),
                    request.expectedPlatformDeterminismManifest()
            ).failures();
            if (!chainFailures.isEmpty()) {
                diagnostics.addAll(chainFailures);
                continue;
            }
            CheckpointRecoveryOutcome outcome = highestHeadedGeneration
                    .filter(highest -> highest.compareTo(record.generationId()) > 0)
                    .isPresent()
                    ? CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY
                    : CheckpointRecoveryOutcome.LATEST_VALID_GENERATION_SELECTED;
            return CheckpointRecoverySelection.selected(outcome, record.manifest(), diagnostics);
        }

        if (diagnostics.isEmpty()) {
            diagnostics.add(failure(
                    CheckpointFailureCode.CHECKPOINT_NO_VALID_GENERATION,
                    "headRecords",
                    "No valid committed checkpoint generation is supported by a valid head"
            ));
        }
        return CheckpointRecoverySelection.blocked(diagnostics);
    }

    public CheckpointRollbackDecision selectRollback(
            CheckpointRollbackRequest rollbackRequest,
            CheckpointRecoverySelectionRequest recoveryContext
    ) {
        Objects.requireNonNull(rollbackRequest, "rollbackRequest");
        Objects.requireNonNull(recoveryContext, "recoveryContext");
        List<CheckpointFailure> failures = new ArrayList<>();
        if (rollbackRequest.operatorIntentId().isEmpty() || rollbackRequest.reason().isEmpty()) {
            failures.add(failure(
                    CheckpointFailureCode.ROLLBACK_OPERATOR_INTENT_MISSING,
                    "operatorIntent",
                    "Rollback selection requires explicit operator intent and reason metadata"
            ));
        }

        Map<CheckpointGenerationId, CheckpointGenerationRecord> generationMap =
                generationMap(recoveryContext.generations());
        failures.addAll(validateGenerationRecordConflicts(recoveryContext.generations()));
        CheckpointGenerationRecord target = generationMap.get(rollbackRequest.targetGenerationId());
        if (target == null) {
            failures.add(failure(
                    CheckpointFailureCode.ROLLBACK_TARGET_INVALID,
                    "targetGenerationId",
                    "Rollback target generation is not present"
            ));
        } else {
            if (!target.publicationState().committedHistory()) {
                failures.add(failure(
                        CheckpointFailureCode.ROLLBACK_TARGET_UNCOMMITTED,
                        "publicationState",
                        "Rollback target must be committed history"
                ));
            }
            failures.addAll(validateGenerationChain(
                    target,
                    generationMap,
                    recoveryContext.requiredOwners(),
                    recoveryContext.expectedWorldIdentityRoot(),
                    recoveryContext.expectedPlatformDeterminismManifest()
            ).failures());
        }

        if (!failures.isEmpty()) {
            return new CheckpointRollbackDecision(
                    CheckpointRecoveryOutcome.INVALID_ROLLBACK_REQUEST,
                    Optional.empty(),
                    Optional.empty(),
                    true,
                    false,
                    failures
            );
        }

        return new CheckpointRollbackDecision(
                CheckpointRecoveryOutcome.EXPLICIT_ROLLBACK_TARGET_ACCEPTED,
                Optional.of(target.generationId()),
                Optional.of(target.manifest().manifestDigest()),
                true,
                true,
                List.of()
        );
    }

    public CheckpointIntegrityResult validateGenerationChain(
            CheckpointGenerationRecord selected,
            Map<CheckpointGenerationId, CheckpointGenerationRecord> generations,
            List<CheckpointOwnerId> requiredOwners,
            WorldIdentityRootReference expectedWorldIdentityRoot,
            PlatformDeterminismManifestReference expectedPlatformDeterminismManifest
    ) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(generations, "generations");
        List<CheckpointFailure> failures = new ArrayList<>();
        Set<CheckpointGenerationId> visited = new HashSet<>();
        CheckpointGenerationRecord current = selected;
        while (current != null) {
            if (!visited.add(current.generationId())) {
                failures.add(failure(
                        CheckpointFailureCode.BROKEN_PREDECESSOR_CHAIN,
                        "predecessorGenerationId",
                        "Checkpoint predecessor chain contains a cycle"
                ));
                break;
            }
            failures.addAll(validateManifest(
                    current.manifest(),
                    requiredOwners,
                    expectedWorldIdentityRoot,
                    expectedPlatformDeterminismManifest
            ).failures());
            Optional<CheckpointGenerationId> predecessorId = current.manifest().predecessorGenerationId();
            if (predecessorId.isEmpty()) {
                if (current.generationId().committedSequence() != 1L) {
                    failures.add(failure(
                            CheckpointFailureCode.BROKEN_PREDECESSOR_CHAIN,
                            "predecessorGenerationId",
                            "Non-initial checkpoint generation is missing predecessor metadata"
                    ));
                }
                break;
            }
            CheckpointGenerationRecord predecessor = generations.get(predecessorId.get());
            if (predecessor == null) {
                failures.add(failure(
                        CheckpointFailureCode.BROKEN_PREDECESSOR_CHAIN,
                        "predecessorGenerationId",
                        "Checkpoint predecessor generation is missing"
                ));
                break;
            }
            validatePredecessorLink(current.manifest(), predecessor.manifest(), failures);
            current = predecessor;
        }
        return new CheckpointIntegrityResult(failures);
    }

    private void validatePredecessorLink(
            CheckpointGenerationManifest current,
            CheckpointGenerationManifest predecessor,
            List<CheckpointFailure> failures
    ) {
        if (predecessor.generationId().committedSequence() + 1L
                != current.generationId().committedSequence()) {
            failures.add(failure(
                    CheckpointFailureCode.INVALID_SEQUENCE_PROGRESSION,
                    "predecessorGenerationId",
                    "Checkpoint generation sequence must progress by one from predecessor"
            ));
        }
        if (predecessor.generationId().authoritativeSimulationTick()
                > current.generationId().authoritativeSimulationTick()) {
            failures.add(failure(
                    CheckpointFailureCode.INVALID_SIMULATION_TICK_PROGRESSION,
                    "predecessorGenerationId",
                    "Checkpoint generation tick must not move backward from predecessor"
            ));
        }
        if (!current.predecessorManifestDigest().orElse("").equals(predecessor.manifestDigest())) {
            failures.add(failure(
                    CheckpointFailureCode.PREDECESSOR_MISMATCH,
                    "predecessorManifestDigest",
                    "Checkpoint predecessor manifest digest does not match predecessor content"
            ));
        }
    }

    private List<CheckpointFailure> validateOwnerSnapshots(
            CheckpointGenerationManifest manifest,
            List<CheckpointOwnerId> requiredOwners
    ) {
        List<CheckpointFailure> failures = new ArrayList<>();
        Map<CheckpointOwnerId, List<OwnerSnapshotDescriptor>> byOwner = manifest.ownerSnapshots().stream()
                .collect(Collectors.groupingBy(
                        OwnerSnapshotDescriptor::ownerId,
                        TreeMap::new,
                        Collectors.toList()
                ));
        Map<String, List<OwnerSnapshotDescriptor>> bySnapshotIdentity = manifest.ownerSnapshots().stream()
                .collect(Collectors.groupingBy(
                        OwnerSnapshotDescriptor::snapshotIdentity,
                        TreeMap::new,
                        Collectors.toList()
                ));
        bySnapshotIdentity.forEach((snapshotIdentity, snapshots) -> {
            long ownerCount = snapshots.stream().map(OwnerSnapshotDescriptor::ownerId).distinct().count();
            if (ownerCount > 1L) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_IDENTITY_CONFLICT,
                        "ownerSnapshots",
                        "Snapshot identity is claimed by multiple owners: " + snapshotIdentity
                ));
            }
        });
        byOwner.forEach((owner, snapshots) -> {
            if (snapshots.size() > 1) {
                failures.add(failure(
                        CheckpointFailureCode.DUPLICATE_OWNER_SNAPSHOT,
                        "ownerSnapshots",
                        "Checkpoint generation contains duplicate owner snapshot for " + owner.value()
                ));
            }
            for (OwnerSnapshotDescriptor snapshot : snapshots) {
                if (!snapshot.generationId().equals(manifest.generationId())) {
                    failures.add(failure(
                            CheckpointFailureCode.MIXED_GENERATION_IDENTITY,
                            "ownerSnapshots",
                            "Owner snapshot generation identity differs from manifest generation identity"
                    ));
                }
                if (snapshot.representedSimulationTick() > manifest.authoritativeSimulationTick()) {
                    failures.add(failure(
                            CheckpointFailureCode.INVALID_SIMULATION_TICK_PROGRESSION,
                            "ownerSnapshots",
                            "Owner snapshot cannot represent a tick after the checkpoint generation tick"
                    ));
                }
                if (!snapshot.worldIdentityRoot().equals(manifest.worldIdentityRoot())) {
                    failures.add(failure(
                            CheckpointFailureCode.WORLD_IDENTITY_MISMATCH,
                            "ownerSnapshots",
                            "Owner snapshot references a different World Identity root"
                    ));
                }
            }
        });

        for (CheckpointOwnerId requiredOwner : requiredOwners) {
            if (!byOwner.containsKey(requiredOwner)) {
                failures.add(failure(
                        CheckpointFailureCode.MISSING_REQUIRED_OWNER,
                        "requiredOwners",
                        "Checkpoint generation is missing required owner snapshot " + requiredOwner.value()
                ));
            }
        }
        return failures;
    }

    private List<CheckpointFailure> validateGenerationRecordConflicts(
            List<CheckpointGenerationRecord> generations
    ) {
        Map<CheckpointGenerationId, CheckpointGenerationRecord> byGenerationId = new TreeMap<>();
        List<CheckpointFailure> failures = new ArrayList<>();
        for (CheckpointGenerationRecord generation : generations.stream().sorted().toList()) {
            CheckpointGenerationRecord existing = byGenerationId.putIfAbsent(generation.generationId(), generation);
            if (existing != null && !existing.equals(generation)) {
                failures.add(failure(
                        CheckpointFailureCode.DUPLICATE_GENERATION_RECORD,
                        "generations",
                        "Checkpoint input contains conflicting records for generation " + generation.generationId()
                ));
            }
        }
        return failures;
    }

    private Map<CheckpointGenerationId, CheckpointGenerationRecord> generationMap(
            List<CheckpointGenerationRecord> generations
    ) {
        Map<CheckpointGenerationId, CheckpointGenerationRecord> sorted = new TreeMap<>();
        generations.stream().sorted().forEach(generation -> sorted.put(generation.generationId(), generation));
        return new LinkedHashMap<>(sorted);
    }

    private CheckpointFailure failure(CheckpointFailureCode code, String field, String message) {
        return new CheckpointFailure(code, field, message);
    }
}
