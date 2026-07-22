package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.BusinessType;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationTime;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public record BusinessRuntimeState(
        BusinessId businessId,
        BusinessOperationalStatus operationalStatus,
        boolean open,
        Optional<String> activeShiftId,
        int workforceCapacity,
        int activeWorkforce,
        boolean maintenance,
        long lastStateChangeSimulationTick,
        BusinessHours businessHours,
        List<BusinessShift> shifts,
        int schemaVersion
) {
    private static final Pattern VALID_SHIFT_ID = Pattern.compile("^[a-z0-9_]+$");

    public BusinessRuntimeState {
        businessId = Objects.requireNonNull(businessId, "businessId");
        operationalStatus = Objects.requireNonNull(operationalStatus, "operationalStatus");
        activeShiftId = Objects.requireNonNull(activeShiftId, "activeShiftId").map(BusinessRuntimeState::requireShiftId);
        if (workforceCapacity < 0) {
            throw new IllegalArgumentException("Business workforce capacity must not be negative: " + workforceCapacity);
        }
        if (activeWorkforce < 0) {
            throw new IllegalArgumentException("Business active workforce must not be negative: " + activeWorkforce);
        }
        if (activeWorkforce > workforceCapacity) {
            throw new IllegalArgumentException("Business active workforce must not exceed capacity: " + businessId.value());
        }
        if (lastStateChangeSimulationTick < 0L) {
            throw new IllegalArgumentException("Business last state change must not be negative: " + lastStateChangeSimulationTick);
        }
        businessHours = Objects.requireNonNull(businessHours, "businessHours");
        shifts = List.copyOf(Objects.requireNonNull(shifts, "shifts"));
        rejectDuplicateShiftIds(shifts);
        if (schemaVersion != BusinessRuntimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported business runtime schema version: " + schemaVersion);
        }
        validateStatusConsistency(businessId, operationalStatus, open, activeShiftId, activeWorkforce, maintenance);
    }

    public static BusinessRuntimeState defaultFor(Business business, SimulationConfiguration configuration) {
        Objects.requireNonNull(business, "business");
        Objects.requireNonNull(configuration, "configuration");
        BusinessHours hours = BusinessHours.weekdays(8, 0, 17, 0, configuration);
        if (!business.status().hasActiveOccupancy()) {
            return suspended(business.id(), hours, List.of(), 0, 0L);
        }
        int capacity = defaultCapacity(business.businessType());
        int expectedWorkforce = Math.max(1, Math.min(capacity, Math.max(1, capacity / 2)));
        List<BusinessShift> shifts = List.of(new BusinessShift("day", 8, 0, 17, 0, expectedWorkforce, true));
        return closed(business.id(), hours, shifts, capacity, 0L);
    }

    public static BusinessRuntimeState closed(
            BusinessId businessId,
            BusinessHours businessHours,
            List<BusinessShift> shifts,
            int workforceCapacity,
            long simulationTick
    ) {
        return new BusinessRuntimeState(
                businessId,
                BusinessOperationalStatus.CLOSED,
                false,
                Optional.empty(),
                workforceCapacity,
                0,
                false,
                simulationTick,
                businessHours,
                shifts,
                BusinessRuntimeSchema.CURRENT_VERSION
        );
    }

    public static BusinessRuntimeState suspended(
            BusinessId businessId,
            BusinessHours businessHours,
            List<BusinessShift> shifts,
            int workforceCapacity,
            long simulationTick
    ) {
        return new BusinessRuntimeState(
                businessId,
                BusinessOperationalStatus.SUSPENDED,
                false,
                Optional.empty(),
                workforceCapacity,
                0,
                false,
                simulationTick,
                businessHours,
                shifts,
                BusinessRuntimeSchema.CURRENT_VERSION
        );
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        businessHours.validate(configuration);
        Set<String> shiftIds = new HashSet<>();
        for (BusinessShift shift : shifts) {
            shift.validate(configuration);
            shiftIds.add(shift.id());
            if (shift.expectedWorkforce() > workforceCapacity) {
                throw new IllegalArgumentException("Business shift expected workforce exceeds capacity: "
                        + businessId.value() + "/" + shift.id());
            }
        }
        activeShiftId.ifPresent(shiftId -> {
            BusinessShift shift = shifts.stream()
                    .filter(candidate -> candidate.id().equals(shiftId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Business active shift is missing: "
                            + businessId.value() + "/" + shiftId));
            if (!shift.active()) {
                throw new IllegalArgumentException("Business active shift is disabled: " + businessId.value() + "/" + shiftId);
            }
            if (activeWorkforce > shift.expectedWorkforce()) {
                throw new IllegalArgumentException("Business active workforce exceeds active shift expectation: "
                        + businessId.value() + "/" + shiftId);
            }
        });
        if (operationalStatus == BusinessOperationalStatus.OPERATING && activeShiftId.isEmpty()) {
            throw new IllegalArgumentException("Operating businesses must have an active shift: " + businessId.value());
        }
        if (open && workforceCapacity == 0) {
            throw new IllegalArgumentException("Open businesses must have workforce capacity: " + businessId.value());
        }
    }

    public Optional<BusinessShift> activeShiftAt(SimulationTime time, SimulationConfiguration configuration) {
        Objects.requireNonNull(time, "time").validate(configuration);
        validate(configuration);
        return shifts.stream()
                .filter(shift -> shift.contains(time, configuration))
                .findFirst();
    }

    public BusinessRuntimeState operatingAt(String shiftId, int workforce, long simulationTick) {
        return transition(
                BusinessOperationalStatus.OPERATING,
                true,
                Optional.of(shiftId),
                workforce,
                false,
                simulationTick
        );
    }

    public BusinessRuntimeState shiftChangeAt(long simulationTick) {
        return transition(
                BusinessOperationalStatus.SHIFT_CHANGE,
                true,
                Optional.empty(),
                0,
                false,
                simulationTick
        );
    }

    public BusinessRuntimeState closedAt(long simulationTick) {
        return transition(
                BusinessOperationalStatus.CLOSED,
                false,
                Optional.empty(),
                0,
                false,
                simulationTick
        );
    }

    public BusinessRuntimeState maintenanceAt(long simulationTick) {
        return transition(
                BusinessOperationalStatus.MAINTENANCE,
                false,
                Optional.empty(),
                0,
                true,
                simulationTick
        );
    }

    public BusinessRuntimeState suspendedAt(long simulationTick) {
        return transition(
                BusinessOperationalStatus.SUSPENDED,
                false,
                Optional.empty(),
                0,
                false,
                simulationTick
        );
    }

    public BusinessRuntimeState replaceSchedule(
            BusinessHours hours,
            List<BusinessShift> shifts,
            int workforceCapacity,
            long simulationTick
    ) {
        return new BusinessRuntimeState(
                businessId,
                BusinessOperationalStatus.CLOSED,
                false,
                Optional.empty(),
                workforceCapacity,
                0,
                false,
                simulationTick,
                hours,
                shifts,
                schemaVersion
        );
    }

    private BusinessRuntimeState transition(
            BusinessOperationalStatus status,
            boolean nextOpen,
            Optional<String> nextActiveShiftId,
            int nextActiveWorkforce,
            boolean nextMaintenance,
            long simulationTick
    ) {
        if (operationalStatus == status
                && open == nextOpen
                && activeShiftId.equals(nextActiveShiftId)
                && activeWorkforce == nextActiveWorkforce
                && maintenance == nextMaintenance) {
            return this;
        }
        return new BusinessRuntimeState(
                businessId,
                status,
                nextOpen,
                nextActiveShiftId,
                workforceCapacity,
                nextActiveWorkforce,
                nextMaintenance,
                simulationTick,
                businessHours,
                shifts,
                schemaVersion
        );
    }

    private static int defaultCapacity(BusinessType businessType) {
        return switch (Objects.requireNonNull(businessType, "businessType")) {
            case FAMILY_BUTCHER_SHOP -> 4;
            case RETAIL_MEAT_MARKET -> 5;
            case CUSTOM_PROCESSOR -> 6;
            case REGIONAL_PROCESSING_COMPANY -> 16;
            case LOCKER_PLANT -> 7;
            case COLD_STORAGE_COMPANY -> 8;
            case FOOD_DISTRIBUTION_COMPANY -> 12;
            case WHOLESALE_SUPPLIER -> 8;
        };
    }

    private static void validateStatusConsistency(
            BusinessId businessId,
            BusinessOperationalStatus status,
            boolean open,
            Optional<String> activeShiftId,
            int activeWorkforce,
            boolean maintenance
    ) {
        if (maintenance && status != BusinessOperationalStatus.MAINTENANCE) {
            throw new IllegalArgumentException("Business maintenance flag requires maintenance status: " + businessId.value());
        }
        if (status == BusinessOperationalStatus.MAINTENANCE && !maintenance) {
            throw new IllegalArgumentException("Business maintenance status requires maintenance flag: " + businessId.value());
        }
        if (status.isOpenStatus() != open) {
            throw new IllegalArgumentException("Business open flag is inconsistent with status: " + businessId.value());
        }
        if (!open && (activeShiftId.isPresent() || activeWorkforce != 0)) {
            throw new IllegalArgumentException("Closed business runtime state must not keep active workforce: " + businessId.value());
        }
        if (status == BusinessOperationalStatus.SUSPENDED && maintenance) {
            throw new IllegalArgumentException("Suspended business runtime state must not also be maintenance: " + businessId.value());
        }
    }

    private static void rejectDuplicateShiftIds(List<BusinessShift> shifts) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (BusinessShift shift : shifts) {
            Objects.requireNonNull(shift, "shift");
            if (!seen.add(shift.id())) {
                duplicates.add(shift.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate business shift ids: " + duplicates);
        }
    }

    private static String requireShiftId(String shiftId) {
        Objects.requireNonNull(shiftId, "shiftId");
        shiftId = shiftId.toLowerCase(Locale.ROOT);
        if (!VALID_SHIFT_ID.matcher(shiftId).matches()) {
            throw new IllegalArgumentException("Business active shift id must use lowercase snake case: " + shiftId);
        }
        return shiftId;
    }
}
