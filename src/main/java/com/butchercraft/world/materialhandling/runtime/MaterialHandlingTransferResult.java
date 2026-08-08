package com.butchercraft.world.materialhandling.runtime;

import com.butchercraft.world.materialhandling.MaterialTransferRecord;

import java.util.Objects;
import java.util.Optional;

public record MaterialHandlingTransferResult(
        boolean succeeded,
        Optional<MaterialTransferRecord> transfer,
        String detail
) {
    public MaterialHandlingTransferResult {
        transfer = Objects.requireNonNull(transfer, "transfer");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static MaterialHandlingTransferResult succeeded(MaterialTransferRecord transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Transfer completed");
    }

    public static MaterialHandlingTransferResult requested(MaterialTransferRecord transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Transfer requested");
    }

    public static MaterialHandlingTransferResult custodyAccepted(MaterialTransferRecord transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Handling custody accepted");
    }

    public static MaterialHandlingTransferResult cancelled(MaterialTransferRecord transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Transfer cancelled");
    }

    public static MaterialHandlingTransferResult failed(Optional<MaterialTransferRecord> transfer, String detail) {
        return new MaterialHandlingTransferResult(false, transfer, detail);
    }
}
