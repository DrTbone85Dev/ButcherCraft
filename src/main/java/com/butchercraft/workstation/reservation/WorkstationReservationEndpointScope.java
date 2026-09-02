package com.butchercraft.workstation.reservation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationReservationEndpointScope(
        WorkstationReservationEndpointPurpose purpose,
        WorkstationReservationEndpointDirection direction,
        Optional<String> endpointIdentity,
        WorkstationReservationConflictDomain conflictDomain
) {
    public WorkstationReservationEndpointScope {
        purpose = Objects.requireNonNull(purpose, "purpose");
        direction = Objects.requireNonNull(direction, "direction");
        endpointIdentity = Objects.requireNonNull(endpointIdentity, "endpointIdentity")
                .map(value -> WorkstationReservationValidation.requireText(value, "endpoint identity", 1024));
        conflictDomain = Objects.requireNonNull(conflictDomain, "conflictDomain");
        if (purpose == WorkstationReservationEndpointPurpose.NONE) {
            if (direction != WorkstationReservationEndpointDirection.NONE || endpointIdentity.isPresent()) {
                throw new IllegalArgumentException("Unscoped reservation cannot contain endpoint direction or identity");
            }
        } else if (direction == WorkstationReservationEndpointDirection.NONE || endpointIdentity.isEmpty()) {
            throw new IllegalArgumentException("Endpoint-scoped reservation requires direction and endpoint identity");
        }
        if (purpose == WorkstationReservationEndpointPurpose.SOURCE
                && direction != WorkstationReservationEndpointDirection.WITHDRAWAL) {
            throw new IllegalArgumentException("Source access must use withdrawal direction");
        }
        if ((purpose == WorkstationReservationEndpointPurpose.DESTINATION
                || purpose == WorkstationReservationEndpointPurpose.SOURCE_RETURN)
                && direction != WorkstationReservationEndpointDirection.DEPOSIT) {
            throw new IllegalArgumentException("Destination and source-return access must use deposit direction");
        }
    }

    public static WorkstationReservationEndpointScope none() {
        return new WorkstationReservationEndpointScope(
                WorkstationReservationEndpointPurpose.NONE,
                WorkstationReservationEndpointDirection.NONE,
                Optional.empty(),
                WorkstationReservationConflictDomain.WORKSTATION_INSTANCE
        );
    }

    public static WorkstationReservationEndpointScope source(String endpointIdentity) {
        return scoped(
                WorkstationReservationEndpointPurpose.SOURCE,
                WorkstationReservationEndpointDirection.WITHDRAWAL,
                endpointIdentity
        );
    }

    public static WorkstationReservationEndpointScope destination(String endpointIdentity) {
        return scoped(
                WorkstationReservationEndpointPurpose.DESTINATION,
                WorkstationReservationEndpointDirection.DEPOSIT,
                endpointIdentity
        );
    }

    public static WorkstationReservationEndpointScope sourceReturn(String endpointIdentity) {
        return scoped(
                WorkstationReservationEndpointPurpose.SOURCE_RETURN,
                WorkstationReservationEndpointDirection.DEPOSIT,
                endpointIdentity
        );
    }

    private static WorkstationReservationEndpointScope scoped(
            WorkstationReservationEndpointPurpose purpose,
            WorkstationReservationEndpointDirection direction,
            String endpointIdentity
    ) {
        return new WorkstationReservationEndpointScope(
                purpose,
                direction,
                Optional.of(Objects.requireNonNull(endpointIdentity, "endpointIdentity")),
                WorkstationReservationConflictDomain.WORKSTATION_INSTANCE
        );
    }
}
