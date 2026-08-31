package com.butchercraft.development.checkpoint;

import com.butchercraft.machine.cuttingtable.CuttingTableWorkstation;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.machine.pattyformer.PattyFormerWorkstation;
import com.butchercraft.workstation.WorkstationSlotCapacityPolicy;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointDependency;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAnalysis;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAnalyzer;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionAuthorization;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionPublicationReport;
import com.butchercraft.workstation.projection.LegacyWorkstationProjectionPublicationService;
import com.butchercraft.workstation.projection.OfflineWorkstationChunkEvidenceReader;
import com.butchercraft.world.checkpoint.RecoveryOperatorEvidence;
import net.minecraft.core.HolderLookup;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit development/operator boundary. It is not connected to startup or gameplay. */
public final class LegacyWorkstationProjectionAdminTool {
    private final LegacyWorkstationProjectionAnalyzer analyzer;
    private final LegacyWorkstationProjectionPublicationService publication;

    public LegacyWorkstationProjectionAdminTool() {
        this(supportedAnalyzer());
    }

    private LegacyWorkstationProjectionAdminTool(LegacyWorkstationProjectionAnalyzer analyzer) {
        this(analyzer, new LegacyWorkstationProjectionPublicationService(analyzer));
    }

    LegacyWorkstationProjectionAdminTool(
            LegacyWorkstationProjectionAnalyzer analyzer,
            LegacyWorkstationProjectionPublicationService publication
    ) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    public LegacyWorkstationProjectionAnalysis analyzeReadOnly(
            Path worldRoot,
            HolderLookup.Provider registries,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        return analyzer.analyze(worldRoot, registries, dependencies);
    }

    public LegacyWorkstationProjectionAuthorization authorizeExact(
            LegacyWorkstationProjectionAnalysis analysis,
            RecoveryOperatorEvidence operatorEvidence
    ) {
        return LegacyWorkstationProjectionAuthorization.authorize(
                analysis,
                LegacyWorkstationProjectionAuthorization.Disposition.AUTHORIZE_PROOF_COMPLETE_PUBLICATION,
                operatorEvidence
        );
    }

    public LegacyWorkstationProjectionPublicationReport publishExact(
            Path worldRoot,
            HolderLookup.Provider registries,
            List<WorkstationCheckpointDependency> dependencies,
            LegacyWorkstationProjectionAuthorization authorization
    ) {
        return publication.publish(worldRoot, registries, dependencies, authorization);
    }

    private static LegacyWorkstationProjectionAnalyzer supportedAnalyzer() {
        Map<String, WorkstationSlotCapacityPolicy> policies = Map.of(
                CuttingTableWorkstation.ID.toString(), CuttingTableWorkstation.slotCapacityPolicy(),
                GrinderWorkstation.ID.toString(), GrinderWorkstation.slotCapacityPolicy(),
                PattyFormerWorkstation.ID.toString(), PattyFormerWorkstation.slotCapacityPolicy()
        );
        return new LegacyWorkstationProjectionAnalyzer(
                new OfflineWorkstationChunkEvidenceReader(),
                typeIdentity -> Optional.ofNullable(policies.get(typeIdentity))
        );
    }
}
