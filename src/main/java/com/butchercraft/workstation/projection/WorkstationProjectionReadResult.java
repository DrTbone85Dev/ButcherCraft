package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.util.Objects;
import java.util.Optional;

public record WorkstationProjectionReadResult(
        WorkstationInstanceId instanceId,
        WorkstationProjectionReadCode code,
        Optional<DurableWorkstationProjection> projection,
        String detail
) implements Comparable<WorkstationProjectionReadResult> {
    public WorkstationProjectionReadResult {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        code = Objects.requireNonNull(code, "code");
        projection = Objects.requireNonNull(projection, "projection");
        detail = Objects.requireNonNull(detail, "detail");
        boolean projectionExpected = code == WorkstationProjectionReadCode.AVAILABLE
                || code == WorkstationProjectionReadCode.RETIRED;
        if (projectionExpected != projection.isPresent()) {
            throw new IllegalArgumentException("Projection presence does not match read result code");
        }
    }

    public static WorkstationProjectionReadResult available(DurableWorkstationProjection projection) {
        WorkstationProjectionReadCode code = projection.status() == WorkstationProjectionStatus.TOMBSTONED
                ? WorkstationProjectionReadCode.RETIRED : WorkstationProjectionReadCode.AVAILABLE;
        return new WorkstationProjectionReadResult(
                projection.instanceId(), code, Optional.of(projection),
                code == WorkstationProjectionReadCode.RETIRED
                        ? "Durable Workstation projection is tombstoned"
                        : "Durable Workstation projection is available"
        );
    }

    public static WorkstationProjectionReadResult unavailable(
            WorkstationInstanceId instanceId,
            WorkstationProjectionReadCode code,
            String detail
    ) {
        return new WorkstationProjectionReadResult(instanceId, code, Optional.empty(), detail);
    }

    @Override
    public int compareTo(WorkstationProjectionReadResult other) {
        return instanceId.compareTo(other.instanceId);
    }
}
