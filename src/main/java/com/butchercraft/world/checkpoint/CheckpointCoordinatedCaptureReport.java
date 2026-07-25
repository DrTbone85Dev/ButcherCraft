package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointCoordinatedCaptureReport(
        Optional<CheckpointPublicationRequest> publicationRequest,
        List<CheckpointCapturedOwnerSnapshot> capturedSnapshots,
        List<CheckpointFailure> failures
) {
    public CheckpointCoordinatedCaptureReport {
        publicationRequest = Objects.requireNonNull(publicationRequest, "publicationRequest");
        capturedSnapshots = Objects.requireNonNull(capturedSnapshots, "capturedSnapshots").stream()
                .map(snapshot -> Objects.requireNonNull(snapshot, "capturedSnapshot"))
                .sorted()
                .toList();
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
        if (publicationRequest.isPresent() == !failures.isEmpty()) {
            throw new IllegalArgumentException("Capture report must contain either a publication request or failures");
        }
    }

    public static CheckpointCoordinatedCaptureReport captured(
            CheckpointPublicationRequest publicationRequest,
            List<CheckpointCapturedOwnerSnapshot> snapshots
    ) {
        return new CheckpointCoordinatedCaptureReport(
                Optional.of(publicationRequest),
                snapshots,
                List.of()
        );
    }

    public static CheckpointCoordinatedCaptureReport failed(
            List<CheckpointCapturedOwnerSnapshot> snapshots,
            List<CheckpointFailure> failures
    ) {
        return new CheckpointCoordinatedCaptureReport(Optional.empty(), snapshots, failures);
    }

    public boolean successful() {
        return publicationRequest.isPresent();
    }
}
