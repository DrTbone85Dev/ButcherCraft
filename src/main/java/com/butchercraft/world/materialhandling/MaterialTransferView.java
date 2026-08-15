package com.butchercraft.world.materialhandling;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;

import java.util.Optional;

/** Read-only transfer view shared by immutable schema-1 history and live schema-2 records. */
public interface MaterialTransferView {
    String transferIdentity();

    default MaterialTransferId transferReference() {
        return new MaterialTransferId(transferIdentity());
    }

    WorkstationEndpointReference source();

    WorkstationEndpointReference destination();

    String materialIdentity();

    Optional<String> employeeReference();

    MaterialTransferLifecycle lifecycle();

    Optional<MaterialCustodyLocation> custodyLocation();

    Optional<String> terminalDetail();

    boolean hasProvenMaterialHandlingCustody();
}
