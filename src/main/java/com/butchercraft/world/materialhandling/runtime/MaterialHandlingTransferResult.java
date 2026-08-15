package com.butchercraft.world.materialhandling.runtime;

import com.butchercraft.world.materialhandling.MaterialTransferView;

import java.util.Objects;
import java.util.Optional;

public final class MaterialHandlingTransferResult {
    private final boolean succeeded;
    private final Optional<MaterialTransferView> transfer;
    private final String detail;

    public MaterialHandlingTransferResult(
            boolean succeeded,
            Optional<? extends MaterialTransferView> transfer,
            String detail
    ) {
        this.succeeded = succeeded;
        this.transfer = Objects.requireNonNull(transfer, "transfer")
                .map(value -> (MaterialTransferView) value);
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean succeeded() {
        return succeeded;
    }

    public Optional<MaterialTransferView> transfer() {
        return transfer;
    }

    public String detail() {
        return detail;
    }

    public static MaterialHandlingTransferResult succeeded(MaterialTransferView transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Transfer completed");
    }

    public static MaterialHandlingTransferResult requested(MaterialTransferView transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Transfer requested");
    }

    public static MaterialHandlingTransferResult custodyAccepted(MaterialTransferView transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Handling custody accepted");
    }

    public static MaterialHandlingTransferResult cancelled(MaterialTransferView transfer) {
        return new MaterialHandlingTransferResult(true, Optional.of(transfer), "Material Transfer cancelled");
    }

    public static MaterialHandlingTransferResult failed(
            Optional<? extends MaterialTransferView> transfer,
            String detail
    ) {
        return new MaterialHandlingTransferResult(false, transfer, detail);
    }
}
