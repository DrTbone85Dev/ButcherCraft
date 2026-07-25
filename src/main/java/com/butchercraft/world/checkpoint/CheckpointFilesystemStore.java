package com.butchercraft.world.checkpoint;

import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class CheckpointFilesystemStore {
    private final CheckpointFilesystemLayout layout;
    private final CheckpointRecoveryEvaluator evaluator;
    private final CheckpointPublicationProbe probe;

    public CheckpointFilesystemStore(Path storeRoot) {
        this(storeRoot, CheckpointPublicationProbe.NONE);
    }

    CheckpointFilesystemStore(Path storeRoot, CheckpointPublicationProbe probe) {
        this.layout = new CheckpointFilesystemLayout(storeRoot);
        this.evaluator = new CheckpointRecoveryEvaluator();
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    public CheckpointFilesystemLayout layout() {
        return layout;
    }

    public CheckpointPublicationReport publish(CheckpointPublicationRequest request) {
        Objects.requireNonNull(request, "request");
        List<CheckpointFailure> diagnostics = new ArrayList<>();
        List<CheckpointStorageArtifact> artifacts = new ArrayList<>();
        CheckpointGenerationManifest manifest = request.toCandidate().toManifest();
        Path staging = layout.stagingGenerationDirectory(manifest.generationId());
        Path finalGeneration = layout.finalGenerationDirectory(manifest.generationId());
        boolean movedToFinal = false;

        diagnostics.addAll(ensureStoreRoot().failures());
        diagnostics.addAll(validatePublicationRequest(request, manifest));
        if (!diagnostics.isEmpty()) {
            return CheckpointPublicationReport.failed(CheckpointPublicationOutcome.FAILED, diagnostics, artifacts);
        }

        if (Files.exists(finalGeneration)) {
            return classifyExistingGeneration(finalGeneration, manifest, request);
        }
        if (Files.exists(staging)) {
            CheckpointFailure failure = failure(
                    CheckpointFailureCode.STAGING_DIRECTORY_CONFLICT,
                    "staging",
                    "Checkpoint staging directory already exists for " + manifest.generationId()
            );
            artifacts.add(artifact(CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY, staging, failure));
            return CheckpointPublicationReport.failed(
                    CheckpointPublicationOutcome.FAILED,
                    List.of(failure),
                    artifacts
            );
        }

        try {
            Files.createDirectories(staging);
            probe.reached(CheckpointPublicationPhase.BEFORE_PAYLOAD_WRITE);
            writeOwnerPayloads(staging, request.ownerSnapshots());
            probe.reached(CheckpointPublicationPhase.BEFORE_MANIFEST);
            writeNewFile(layout.generationManifest(staging), CheckpointFilesystemSerializer.generationManifestBytes(manifest));
            probe.reached(CheckpointPublicationPhase.AFTER_MANIFEST);
            diagnostics.addAll(validateStagedGeneration(staging, request).failures());
            if (!diagnostics.isEmpty()) {
                artifacts.add(artifact(
                        CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY,
                        staging,
                        diagnostics.getFirst()
                ));
                return CheckpointPublicationReport.failed(
                        CheckpointPublicationOutcome.FAILED,
                        diagnostics,
                        artifacts
                );
            }
            probe.reached(CheckpointPublicationPhase.BEFORE_FINAL_MOVE);
            moveFinalGeneration(staging, finalGeneration);
            movedToFinal = true;
            probe.reached(CheckpointPublicationPhase.AFTER_FINAL_MOVE);
            CheckpointHeadRecord head = CheckpointHeadRecord.forManifest(manifest);
            diagnostics.addAll(writeHead(head).failures());
            if (hasBlockingFailures(diagnostics)) {
                artifacts.add(artifact(
                        CheckpointStorageArtifactKind.UNCOMMITTED_GENERATION_DIRECTORY,
                        finalGeneration,
                        diagnostics.getFirst()
                ));
                return CheckpointPublicationReport.failed(
                        CheckpointPublicationOutcome.FAILED,
                        diagnostics,
                        artifacts
                );
            }
            probe.reached(CheckpointPublicationPhase.AFTER_HEAD_PUBLICATION);
            return CheckpointPublicationReport.published(manifest, head, diagnostics);
        } catch (AtomicMoveNotSupportedException exception) {
            CheckpointFailure failure = failure(
                    CheckpointFailureCode.ATOMIC_MOVE_UNSUPPORTED,
                    "generationPublication",
                    "Filesystem does not support atomic generation publication"
            );
            artifacts.add(artifact(
                    movedToFinal
                            ? CheckpointStorageArtifactKind.UNCOMMITTED_GENERATION_DIRECTORY
                            : CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY,
                    movedToFinal ? finalGeneration : staging,
                    failure
            ));
            return CheckpointPublicationReport.failed(
                    CheckpointPublicationOutcome.FAILED,
                    List.of(failure),
                    artifacts
            );
        } catch (FileAlreadyExistsException exception) {
            CheckpointFailure failure = failure(
                    CheckpointFailureCode.GENERATION_PUBLICATION_CONFLICT,
                    "generationPublication",
                    "Checkpoint generation path already exists"
            );
            artifacts.add(artifact(CheckpointStorageArtifactKind.CONFLICTING_GENERATION_DIRECTORY, finalGeneration, failure));
            return CheckpointPublicationReport.failed(
                    CheckpointPublicationOutcome.CONFLICT,
                    List.of(failure),
                    artifacts
            );
        } catch (IOException exception) {
            CheckpointFailure failure = failure(
                    movedToFinal ? CheckpointFailureCode.HEAD_WRITE_FAILURE
                            : CheckpointFailureCode.CHECKPOINT_DURABLE_WRITE_FAILED,
                    movedToFinal ? "headPublication" : "generationPublication",
                    "Checkpoint filesystem publication failed"
            );
            artifacts.add(artifact(
                    movedToFinal
                            ? CheckpointStorageArtifactKind.UNCOMMITTED_GENERATION_DIRECTORY
                            : CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY,
                    movedToFinal ? finalGeneration : staging,
                    failure
            ));
            return CheckpointPublicationReport.failed(
                    CheckpointPublicationOutcome.FAILED,
                    List.of(failure),
                    artifacts
            );
        }
    }

    public CheckpointFilesystemRecoveryReport recover(CheckpointFilesystemRecoveryRequest request) {
        Objects.requireNonNull(request, "request");
        List<CheckpointFailure> diagnostics = new ArrayList<>();
        List<CheckpointStorageArtifact> artifacts = new ArrayList<>();
        diagnostics.addAll(ensureStoreRoot().failures());
        if (!diagnostics.isEmpty()) {
            CheckpointRecoverySelection selection = CheckpointRecoverySelection.blocked(diagnostics);
            return new CheckpointFilesystemRecoveryReport(selection, List.of(), List.of(), artifacts);
        }

        artifacts.addAll(scanIncompleteArtifacts());
        List<CheckpointGenerationRecord> generations = readGenerationRecords(request, artifacts);
        List<CheckpointHeadRecord> heads = readHeadRecords(artifacts);
        if (heads.isEmpty() && !generations.isEmpty()) {
            for (CheckpointGenerationRecord generation : generations) {
                artifacts.add(artifact(
                        CheckpointStorageArtifactKind.UNCOMMITTED_GENERATION_DIRECTORY,
                        layout.finalGenerationDirectory(generation.generationId()),
                        failure(
                                CheckpointFailureCode.QUARANTINED_ARTIFACT,
                                "generations",
                                "Complete generation directory is not referenced by a valid head"
                        )
                ));
            }
        }

        CheckpointRecoverySelection selection = evaluator.selectLatestValidCommitted(
                new CheckpointRecoverySelectionRequest(
                        generations,
                        heads,
                        request.requiredOwners(),
                        request.expectedWorldIdentityRoot(),
                        request.expectedPlatformDeterminismManifest()
                )
        );
        return new CheckpointFilesystemRecoveryReport(selection, generations, heads, artifacts);
    }

    public CheckpointRecoveredGenerationReport loadSelectedGeneration(
            CheckpointFilesystemRecoveryRequest request
    ) {
        Objects.requireNonNull(request, "request");
        CheckpointFilesystemRecoveryReport recovery = recover(request);
        Optional<CheckpointGenerationId> selectedGenerationId = recovery.selection().selectedGenerationId();
        Optional<String> selectedManifestDigest = recovery.selection().selectedManifestDigest();
        if (selectedGenerationId.isEmpty() || selectedManifestDigest.isEmpty()) {
            List<CheckpointFailure> failures = new ArrayList<>(recovery.selection().diagnostics());
            recovery.artifacts().stream()
                    .map(CheckpointStorageArtifact::failure)
                    .forEach(failures::add);
            failures.add(failure(
                    CheckpointFailureCode.RECOVERY_BLOCKED_STATE,
                    "selectedGeneration",
                    "No valid checkpoint generation is available for owner restoration"
            ));
            return CheckpointRecoveredGenerationReport.failed(recovery, failures);
        }
        CheckpointGenerationRecord selected = recovery.generationRecords().stream()
                .filter(record -> record.generationId().equals(selectedGenerationId.get()))
                .filter(record -> record.manifest().manifestDigest().equals(selectedManifestDigest.get()))
                .findFirst()
                .orElse(null);
        if (selected == null) {
            return CheckpointRecoveredGenerationReport.failed(recovery, List.of(failure(
                    CheckpointFailureCode.HEAD_REFERENCES_MISSING_GENERATION,
                    "selectedGeneration",
                    "Selected checkpoint generation payloads are not available"
            )));
        }
        Path generationDirectory = layout.finalGenerationDirectory(selected.generationId());
        try {
            List<CheckpointOwnerSnapshotPayload> snapshots = readSnapshotPayloadDescriptors(
                    generationDirectory,
                    selected.manifest()
            );
            return CheckpointRecoveredGenerationReport.recovered(
                    recovery,
                    new CheckpointRecoveredGeneration(selected, snapshots)
            );
        } catch (IOException | IllegalArgumentException | JsonParseException exception) {
            return CheckpointRecoveredGenerationReport.failed(recovery, List.of(failure(
                    CheckpointFailureCode.GENERATION_CORRUPTION,
                    "selectedGeneration",
                    "Selected checkpoint generation payloads could not be read"
            )));
        }
    }

    public CheckpointRollbackDecision selectRollback(
            CheckpointRollbackRequest rollbackRequest,
            CheckpointFilesystemRecoveryRequest recoveryRequest
    ) {
        CheckpointFilesystemRecoveryReport recovery = recover(recoveryRequest);
        return evaluator.selectRollback(
                rollbackRequest,
                new CheckpointRecoverySelectionRequest(
                        recovery.generationRecords(),
                        recovery.headRecords(),
                        recoveryRequest.requiredOwners(),
                        recoveryRequest.expectedWorldIdentityRoot(),
                        recoveryRequest.expectedPlatformDeterminismManifest()
                )
        );
    }

    private CheckpointIntegrityResult ensureStoreRoot() {
        try {
            if (Files.exists(layout.storeRoot()) && !Files.isDirectory(layout.storeRoot())) {
                return CheckpointIntegrityResult.failed(List.of(failure(
                        CheckpointFailureCode.STORE_ROOT_UNAVAILABLE,
                        "storeRoot",
                        "Checkpoint store root exists but is not a directory"
                )));
            }
            Files.createDirectories(layout.generationsDirectory());
            Files.createDirectories(layout.stagingDirectory());
            Files.createDirectories(layout.quarantineDirectory());
            return CheckpointIntegrityResult.successful();
        } catch (IOException exception) {
            return CheckpointIntegrityResult.failed(List.of(failure(
                    CheckpointFailureCode.STORE_ROOT_UNAVAILABLE,
                    "storeRoot",
                    "Checkpoint store root could not be created or opened"
            )));
        }
    }

    private List<CheckpointFailure> validatePublicationRequest(
            CheckpointPublicationRequest request,
            CheckpointGenerationManifest manifest
    ) {
        List<CheckpointFailure> failures = new ArrayList<>();
        failures.addAll(evaluator.validateManifest(
                manifest,
                request.requiredOwners(),
                request.worldIdentityRoot(),
                request.platformDeterminismManifest()
        ).failures());
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> byOwner = new TreeMap<>();
        for (CheckpointOwnerSnapshotPayload snapshot : request.ownerSnapshots()) {
            byOwner.put(snapshot.descriptor().ownerId(), snapshot);
            String payloadDigest = CheckpointFilesystemDigest.sha256(snapshot.payloadBytes());
            if (!payloadDigest.equals(snapshot.expectedContentDigest())
                    || !payloadDigest.equals(snapshot.descriptor().contentDigest())) {
                failures.add(failure(
                        CheckpointFailureCode.PAYLOAD_DIGEST_MISMATCH,
                        snapshot.descriptor().ownerId().value(),
                        "Owner snapshot payload digest does not match descriptor content digest"
                ));
            }
        }
        for (CheckpointOwnerId ownerId : request.requiredOwners()) {
            if (!byOwner.containsKey(ownerId)) {
                failures.add(failure(
                        CheckpointFailureCode.MISSING_REQUIRED_OWNER_PAYLOAD,
                        ownerId.value(),
                        "Missing required owner snapshot payload"
                ));
            }
        }
        return failures;
    }

    private CheckpointPublicationReport classifyExistingGeneration(
            Path finalGeneration,
            CheckpointGenerationManifest manifest,
            CheckpointPublicationRequest request
    ) {
        try {
            CheckpointGenerationManifest existingManifest = readGenerationManifest(finalGeneration);
            if (existingManifest.equals(manifest)
                    && validateGenerationDirectory(finalGeneration, existingManifest, request).isEmpty()) {
                return CheckpointPublicationReport.duplicate(existingManifest);
            }
        } catch (IOException | IllegalArgumentException | JsonParseException ignored) {
            // A malformed existing directory still creates an identity conflict for this publication.
        }
        CheckpointFailure failure = failure(
                CheckpointFailureCode.GENERATION_PUBLICATION_CONFLICT,
                "generationId",
                "Checkpoint generation identity already exists with different or invalid content"
        );
        return CheckpointPublicationReport.failed(
                CheckpointPublicationOutcome.CONFLICT,
                List.of(failure),
                List.of(artifact(CheckpointStorageArtifactKind.CONFLICTING_GENERATION_DIRECTORY, finalGeneration, failure))
        );
    }

    private void writeOwnerPayloads(
            Path generationDirectory,
            List<CheckpointOwnerSnapshotPayload> snapshots
    ) throws IOException {
        for (int index = 0; index < snapshots.size(); index++) {
            CheckpointOwnerSnapshotPayload snapshot = snapshots.get(index);
            Path ownerDirectory = layout.ownerDirectory(generationDirectory, snapshot.descriptor().ownerId());
            Files.createDirectories(ownerDirectory);
            writeNewFile(layout.ownerPayload(generationDirectory, snapshot.descriptor().ownerId()), snapshot.payloadBytes());
            if (index == 0) {
                probe.reached(CheckpointPublicationPhase.DURING_PAYLOAD_SET);
            }
            writeNewFile(
                    layout.ownerManifest(generationDirectory, snapshot.descriptor().ownerId()),
                    CheckpointFilesystemSerializer.ownerManifestBytes(snapshot)
            );
            String storedDigest = CheckpointFilesystemDigest.sha256(
                    Files.readAllBytes(layout.ownerPayload(generationDirectory, snapshot.descriptor().ownerId()))
            );
            if (!storedDigest.equals(snapshot.expectedContentDigest())) {
                throw new IOException("Stored owner payload digest mismatch");
            }
        }
    }

    private CheckpointIntegrityResult validateStagedGeneration(
            Path generationDirectory,
            CheckpointPublicationRequest request
    ) {
        try {
            CheckpointGenerationManifest manifest = readGenerationManifest(generationDirectory);
            List<CheckpointFailure> failures = validateGenerationDirectory(generationDirectory, manifest, request);
            return CheckpointIntegrityResult.failed(failures);
        } catch (IOException | IllegalArgumentException | JsonParseException exception) {
            return CheckpointIntegrityResult.failed(List.of(failure(
                    CheckpointFailureCode.GENERATION_CORRUPTION,
                    "generationDirectory",
                    "Staged checkpoint generation could not be read back and validated"
            )));
        }
    }

    private List<CheckpointFailure> validateGenerationDirectory(
            Path generationDirectory,
            CheckpointGenerationManifest manifest,
            CheckpointPublicationRequest request
    ) throws IOException {
        List<CheckpointFailure> failures = new ArrayList<>(evaluator.validateManifest(
                manifest,
                request.requiredOwners(),
                request.worldIdentityRoot(),
                request.platformDeterminismManifest()
        ).failures());
        failures.addAll(validateOwnerPayloadFiles(generationDirectory, manifest));
        return failures;
    }

    private List<CheckpointFailure> validateOwnerPayloadFiles(
            Path generationDirectory,
            CheckpointGenerationManifest manifest
    ) throws IOException {
        List<CheckpointFailure> failures = new ArrayList<>();
        for (OwnerSnapshotDescriptor snapshot : manifest.ownerSnapshots()) {
            Path payload = layout.ownerPayload(generationDirectory, snapshot.ownerId());
            Path ownerManifest = layout.ownerManifest(generationDirectory, snapshot.ownerId());
            if (!Files.isRegularFile(payload) || !Files.isRegularFile(ownerManifest)) {
                failures.add(failure(
                        CheckpointFailureCode.MISSING_REQUIRED_OWNER_PAYLOAD,
                        snapshot.ownerId().value(),
                        "Owner payload or owner manifest is missing"
                ));
                continue;
            }
            CheckpointParsedOwnerManifest parsed = CheckpointFilesystemSerializer.parseOwnerManifest(
                    Files.readString(ownerManifest, StandardCharsets.UTF_8)
            );
            byte[] bytes = Files.readAllBytes(payload);
            String payloadDigest = CheckpointFilesystemDigest.sha256(bytes);
            if (!parsed.descriptor().equals(snapshot)
                    || parsed.payloadLength() != bytes.length
                    || !parsed.payloadDigest().equals(payloadDigest)
                    || !payloadDigest.equals(snapshot.contentDigest())) {
                failures.add(failure(
                        CheckpointFailureCode.PAYLOAD_DIGEST_MISMATCH,
                        snapshot.ownerId().value(),
                        "Owner payload or owner manifest digest does not match generation manifest"
                ));
            }
        }
        return failures;
    }

    private CheckpointIntegrityResult writeHead(CheckpointHeadRecord head) {
        List<CheckpointFailure> diagnostics = new ArrayList<>();
        Path target = layout.headForSequence(head.headSequence());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            probe.reached(CheckpointPublicationPhase.DURING_HEAD_WRITE);
            if (Files.exists(temporary)) {
                return CheckpointIntegrityResult.failed(List.of(failure(
                        CheckpointFailureCode.HEAD_WRITE_FAILURE,
                        "headTemporary",
                        "Checkpoint head temporary file already exists"
                )));
            }
            writeNewFile(temporary, CheckpointFilesystemSerializer.headRecordBytes(head));
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                diagnostics.add(failure(
                        CheckpointFailureCode.FILESYSTEM_GUARANTEE_REDUCED,
                        "headPublication",
                        "Checkpoint head replacement used reduced filesystem guarantees"
                ));
            }
            CheckpointHeadRecord parsed = readHead(target);
            if (!parsed.equals(head) || !parsed.digestMatches()) {
                diagnostics.add(failure(
                        CheckpointFailureCode.INVALID_HEAD_DIGEST,
                        "headRecordDigest",
                        "Checkpoint head record did not validate after publication"
                ));
            }
            return CheckpointIntegrityResult.failed(diagnostics);
        } catch (IOException | IllegalArgumentException | JsonParseException exception) {
            return CheckpointIntegrityResult.failed(List.of(failure(
                    CheckpointFailureCode.HEAD_WRITE_FAILURE,
                    "headPublication",
                    "Checkpoint head publication failed"
            )));
        }
    }

    private void moveFinalGeneration(Path staging, Path finalGeneration) throws IOException {
        Files.createDirectories(finalGeneration.getParent());
        Files.move(staging, finalGeneration, StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeNewFile(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes.clone());
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private List<CheckpointStorageArtifact> scanIncompleteArtifacts() {
        List<CheckpointStorageArtifact> artifacts = new ArrayList<>();
        if (Files.isDirectory(layout.stagingDirectory())) {
            try (Stream<Path> paths = Files.list(layout.stagingDirectory())) {
                paths.sorted(Comparator.comparing(Path::toString))
                        .filter(Files::isDirectory)
                        .forEach(path -> artifacts.add(artifact(
                                CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY,
                                path,
                                failure(
                                        CheckpointFailureCode.QUARANTINED_ARTIFACT,
                                        "staging",
                                        "Incomplete checkpoint staging directory is excluded from authority"
                                )
                        )));
            } catch (IOException exception) {
                artifacts.add(artifact(
                        CheckpointStorageArtifactKind.INCOMPLETE_STAGING_DIRECTORY,
                        layout.stagingDirectory(),
                        failure(
                                CheckpointFailureCode.QUARANTINE_MOVE_FAILURE,
                                "staging",
                                "Checkpoint staging directory could not be enumerated"
                        )
                ));
            }
        }
        for (Path headTemporary : List.of(
                layout.headA().resolveSibling(layout.headA().getFileName() + ".tmp"),
                layout.headB().resolveSibling(layout.headB().getFileName() + ".tmp")
        )) {
            if (Files.exists(headTemporary)) {
                artifacts.add(artifact(
                        CheckpointStorageArtifactKind.HEAD_TEMPORARY_FILE,
                        headTemporary,
                        failure(
                                CheckpointFailureCode.QUARANTINED_ARTIFACT,
                                "headTemporary",
                                "Incomplete checkpoint head temporary file is excluded from authority"
                        )
                ));
            }
        }
        return artifacts;
    }

    private List<CheckpointGenerationRecord> readGenerationRecords(
            CheckpointFilesystemRecoveryRequest request,
            List<CheckpointStorageArtifact> artifacts
    ) {
        List<CheckpointGenerationRecord> records = new ArrayList<>();
        if (!Files.isDirectory(layout.generationsDirectory())) {
            return records;
        }
        try (Stream<Path> paths = Files.list(layout.generationsDirectory())) {
            for (Path generationDirectory : paths.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (!Files.isDirectory(generationDirectory)) {
                    continue;
                }
                try {
                    CheckpointGenerationManifest manifest = readGenerationManifest(generationDirectory);
                    CheckpointPublicationRequest validationRequest = new CheckpointPublicationRequest(
                            manifest.generationId(),
                            manifest.predecessorGenerationId(),
                            manifest.predecessorManifestDigest(),
                            manifest.authoritativeSimulationTick(),
                            readSnapshotPayloadDescriptors(generationDirectory, manifest),
                            request.requiredOwners(),
                            request.expectedPlatformDeterminismManifest(),
                            request.expectedWorldIdentityRoot()
                    );
                    List<CheckpointFailure> failures = validateGenerationDirectory(
                            generationDirectory,
                            manifest,
                            validationRequest
                    );
                    if (failures.isEmpty()) {
                        records.add(new CheckpointGenerationRecord(manifest, CheckpointPublicationState.COMMITTED));
                    } else {
                        artifacts.add(artifact(
                                CheckpointStorageArtifactKind.CORRUPT_GENERATION_DIRECTORY,
                                generationDirectory,
                                failures.getFirst()
                        ));
                    }
                } catch (IOException | IllegalArgumentException | JsonParseException exception) {
                    artifacts.add(artifact(
                            CheckpointStorageArtifactKind.CORRUPT_GENERATION_DIRECTORY,
                            generationDirectory,
                            failure(
                                    CheckpointFailureCode.GENERATION_CORRUPTION,
                                    "generationDirectory",
                                    "Checkpoint generation directory could not be read or validated"
                            )
                    ));
                }
            }
        } catch (IOException exception) {
            artifacts.add(artifact(
                    CheckpointStorageArtifactKind.CORRUPT_GENERATION_DIRECTORY,
                    layout.generationsDirectory(),
                    failure(
                            CheckpointFailureCode.GENERATION_CORRUPTION,
                            "generations",
                            "Checkpoint generations directory could not be enumerated"
                    )
            ));
        }
        return records;
    }

    private boolean hasBlockingFailures(List<CheckpointFailure> diagnostics) {
        return diagnostics.stream()
                .anyMatch(failure -> failure.code() != CheckpointFailureCode.FILESYSTEM_GUARANTEE_REDUCED);
    }

    private List<CheckpointOwnerSnapshotPayload> readSnapshotPayloadDescriptors(
            Path generationDirectory,
            CheckpointGenerationManifest manifest
    ) throws IOException {
        List<CheckpointOwnerSnapshotPayload> snapshots = new ArrayList<>();
        for (OwnerSnapshotDescriptor descriptor : manifest.ownerSnapshots()) {
            byte[] bytes = Files.readAllBytes(layout.ownerPayload(generationDirectory, descriptor.ownerId()));
            snapshots.add(new CheckpointOwnerSnapshotPayload(descriptor, bytes, descriptor.contentDigest()));
        }
        return snapshots;
    }

    private List<CheckpointHeadRecord> readHeadRecords(List<CheckpointStorageArtifact> artifacts) {
        List<CheckpointHeadRecord> heads = new ArrayList<>();
        for (Path headPath : List.of(layout.headA(), layout.headB())) {
            if (!Files.isRegularFile(headPath)) {
                continue;
            }
            try {
                CheckpointHeadRecord head = readHead(headPath);
                heads.add(head);
                if (!head.digestMatches()) {
                    artifacts.add(artifact(
                            CheckpointStorageArtifactKind.INVALID_HEAD_FILE,
                            headPath,
                            failure(
                                    CheckpointFailureCode.INVALID_HEAD_DIGEST,
                                    "headRecordDigest",
                                    "Checkpoint head digest is invalid"
                            )
                    ));
                }
            } catch (IOException | IllegalArgumentException | JsonParseException exception) {
                artifacts.add(artifact(
                        CheckpointStorageArtifactKind.INVALID_HEAD_FILE,
                        headPath,
                        failure(
                                CheckpointFailureCode.INVALID_HEAD_DIGEST,
                                "headRecord",
                                "Checkpoint head file could not be read or parsed"
                        )
                ));
            }
        }
        return heads;
    }

    private CheckpointGenerationManifest readGenerationManifest(Path generationDirectory) throws IOException {
        return CheckpointFilesystemSerializer.parseGenerationManifest(
                Files.readString(layout.generationManifest(generationDirectory), StandardCharsets.UTF_8)
        );
    }

    private CheckpointHeadRecord readHead(Path headPath) throws IOException {
        return CheckpointFilesystemSerializer.parseHeadRecord(
                Files.readString(headPath, StandardCharsets.UTF_8)
        );
    }

    private CheckpointStorageArtifact artifact(
            CheckpointStorageArtifactKind kind,
            Path path,
            CheckpointFailure failure
    ) {
        return new CheckpointStorageArtifact(kind, path, failure);
    }

    private CheckpointFailure failure(CheckpointFailureCode code, String field, String message) {
        return new CheckpointFailure(code, field, message);
    }
}
