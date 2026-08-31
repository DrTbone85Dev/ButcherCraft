package com.butchercraft.integration.machine;

import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointProjection;
import com.butchercraft.world.execution.ExecutionAuthorization;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.function.Function;

/** Machine-specific recipe authorization at the generic continuous Run boundary. */
public interface PoweredProcessingMachineRunAdapter<M extends AbstractProcessingWorkstationBlockEntity> {
    String displayName();

    String identityPath();

    String controlOwner();

    WorkstationEndpointProjection endpointProjection(M machine);

    boolean hasConflictingReservation(ServerLevel level, M machine);

    WorkstationProductionRequestResult requestRunProcessing(
            M machine,
            WorkstationTickContext tickContext,
            List<String> childBindingIdentities,
            Function<ExecutionAuthorization, MachineRunCoordinationResult> admission
    );
}
