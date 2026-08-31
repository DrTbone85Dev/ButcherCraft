package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.LegacySplitRecoveryDryRunPreview;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationReport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPublicationService;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPreviewRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryStatusSnapshot;
import com.butchercraft.world.checkpoint.RecoveryOperatorAuthorization;
import com.butchercraft.world.checkpoint.RecoveryOperatorEvidence;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Explicit administrator service boundary. It is intentionally not wired into gameplay or startup. */
public final class LegacySplitRecoveryAdminTool {
    private final LegacySplitRecoveryPublicationService service;

    public LegacySplitRecoveryAdminTool() {
        this(new LegacySplitRecoveryPublicationService());
    }

    public LegacySplitRecoveryAdminTool(LegacySplitRecoveryPublicationService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public LegacySplitRecoveryDryRunPreview analyze(LegacySplitRecoveryPreviewRequest request) {
        return service.preview(request);
    }

    public RecoveryOperatorAuthorization authorizeExact(
            LegacySplitRecoveryDryRunPreview preview,
            RecoveryOperatorAuthorization.Disposition disposition,
            RecoveryOperatorEvidence operatorEvidence,
            Optional<String> timestampMetadata
    ) {
        Objects.requireNonNull(preview, "preview");
        if (!preview.publicationEligible()) {
            throw new IllegalStateException("Recovery preview is not eligible for authorization");
        }
        return RecoveryOperatorAuthorization.authorize(
                preview.plan(),
                disposition,
                operatorEvidence,
                timestampMetadata
        );
    }

    public LegacySplitRecoveryPublicationReport publishExact(
            LegacySplitRecoveryPublicationRequest request
    ) {
        return service.publish(request);
    }

    public LegacySplitRecoveryStatusSnapshot status(
            com.butchercraft.world.checkpoint.LegacySplitRecoveryAnalysisSource source,
            Path checkpointRoot
    ) {
        return service.status(source, checkpointRoot);
    }
}
