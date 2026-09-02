package com.butchercraft.workstation.reservation;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorkstationReservationDirectory(
        int schemaVersion,
        WorldIdentityRootIdentity worldIdentity,
        long nextSequence,
        long ownerRevision,
        List<WorkstationReservationRecord> records
) {
    public WorkstationReservationDirectory {
        schemaVersion = WorkstationReservationValidation.requireSchema(schemaVersion, "workstation reservation directory");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        if (nextSequence <= 0L) throw new IllegalArgumentException("Next reservation sequence must be positive");
        if (ownerRevision < 0L) throw new IllegalArgumentException("Reservation owner revision must not be negative");
        records = Objects.requireNonNull(records, "records").stream()
                .map(record -> Objects.requireNonNull(record, "record"))
                .sorted()
                .toList();
        validate(records, worldIdentity, nextSequence, ownerRevision);
    }

    public static WorkstationReservationDirectory empty(WorldIdentityRootIdentity worldIdentity) {
        return new WorkstationReservationDirectory(
                WorkstationReservationSchema.CURRENT_VERSION,
                worldIdentity,
                1L,
                0L,
                List.of()
        );
    }

    public static WorkstationReservationDirectory of(
            WorldIdentityRootIdentity worldIdentity,
            long nextSequence,
            long ownerRevision,
            Collection<WorkstationReservationRecord> records
    ) {
        return new WorkstationReservationDirectory(
                WorkstationReservationSchema.CURRENT_VERSION,
                worldIdentity,
                nextSequence,
                ownerRevision,
                List.copyOf(records)
        );
    }

    private static void validate(
            List<WorkstationReservationRecord> records,
            WorldIdentityRootIdentity worldIdentity,
            long nextSequence,
            long ownerRevision
    ) {
        Set<WorkstationReservationId> ids = new HashSet<>();
        Set<String> requests = new HashSet<>();
        Set<Long> sequences = new HashSet<>();
        Set<String> activeEmployees = new HashSet<>();
        Set<String> activeOperators = new HashSet<>();
        Set<String> activeHandlers = new HashSet<>();
        Set<String> activeLegacyLocations = new HashSet<>();
        Map<String, String> activeModernInstancesByLocation = new HashMap<>();
        long maximumSequence = 0L;
        for (WorkstationReservationRecord record : records) {
            if (!record.worldIdentity().equals(worldIdentity)) {
                throw new IllegalArgumentException("Reservation belongs to a different World Identity");
            }
            if (!ids.add(record.reservationId()) || !requests.add(record.requestIdentity())
                    || !sequences.add(record.sequence())) {
                throw new IllegalArgumentException("Duplicate reservation identity, request, or sequence");
            }
            maximumSequence = Math.max(maximumSequence, record.sequence());
            if (record.lastUpdateRevision() > ownerRevision) {
                throw new IllegalArgumentException("Reservation revision exceeds directory owner revision");
            }
            if (!record.active()) continue;
            if (!activeEmployees.add(record.employeeIdentity())) {
                throw new IllegalArgumentException("Employee has more than one active workstation reservation");
            }
            String instance = record.workstationIdentity();
            String location = record.locationIdentity();
            if (record.role() == WorkstationReservationRole.MACHINE_OPERATOR && !activeOperators.add(instance)) {
                throw new IllegalArgumentException("Workstation has more than one active MACHINE_OPERATOR");
            }
            if (record.role() == WorkstationReservationRole.MATERIAL_HANDLER && !activeHandlers.add(instance)) {
                throw new IllegalArgumentException("Workstation has more than one active MATERIAL_HANDLER");
            }
            if (record.role() == WorkstationReservationRole.LEGACY_EXCLUSIVE) {
                if (!activeLegacyLocations.add(location) || activeModernInstancesByLocation.containsKey(location)) {
                    throw new IllegalArgumentException("LEGACY_EXCLUSIVE conflicts at workstation location");
                }
            } else {
                if (activeLegacyLocations.contains(location)) {
                    throw new IllegalArgumentException("Modern reservation conflicts with LEGACY_EXCLUSIVE");
                }
                String existingInstance = activeModernInstancesByLocation.putIfAbsent(location, instance);
                if (existingInstance != null && !existingInstance.equals(instance)) {
                    throw new IllegalArgumentException(
                            "One workstation location contains multiple active instance identities");
                }
            }
        }
        if (nextSequence <= maximumSequence) {
            throw new IllegalArgumentException("Next reservation sequence must exceed all allocated sequences");
        }
    }
}
