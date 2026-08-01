package com.butchercraft.world.workforce.department;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DepartmentManager {
    private DepartmentDirectory directory;

    public DepartmentManager(DepartmentDirectory directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public synchronized DepartmentRegistry registry() {
        return directory.registry();
    }

    public synchronized DepartmentDirectory directory() {
        return directory;
    }

    public synchronized Optional<DepartmentRecord> find(DepartmentId departmentId) {
        return directory.registry().find(Objects.requireNonNull(departmentId, "departmentId"));
    }

    public synchronized boolean contains(DepartmentId departmentId) {
        return directory.registry().contains(Objects.requireNonNull(departmentId, "departmentId"));
    }

    public synchronized DepartmentRecord createDepartment(DepartmentRecord record) {
        if (directory.registry().contains(Objects.requireNonNull(record, "record").departmentId())) {
            throw new IllegalArgumentException("Department already exists: " + record.departmentId().value());
        }
        directory = new DepartmentDirectory(directory.registry().with(record), directory.plantEntranceAnchor());
        return record;
    }

    public synchronized DepartmentRecord assignAnchor(DepartmentId departmentId, DepartmentAnchor anchor) {
        DepartmentRecord record = requireDepartment(departmentId);
        DepartmentRecord updated = record.withAnchor(anchor);
        directory = new DepartmentDirectory(directory.registry().with(updated), directory.plantEntranceAnchor());
        return updated;
    }

    public synchronized DepartmentRecord assignRadius(DepartmentId departmentId, int radius) {
        DepartmentRecord record = requireDepartment(departmentId);
        DepartmentRecord updated = record.withRadius(radius);
        directory = new DepartmentDirectory(directory.registry().with(updated), directory.plantEntranceAnchor());
        return updated;
    }

    public synchronized void assignPlantEntranceAnchor(Optional<DepartmentAnchor> anchor) {
        directory = new DepartmentDirectory(directory.registry(), Objects.requireNonNull(anchor, "anchor"));
    }

    public synchronized void validateCanonicalDefinitions(WorldIdentityRootIdentity worldIdentity) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        for (DepartmentId departmentId : canonicalDepartmentIds()) {
            if (!directory.registry().contains(departmentId)) {
                throw new IllegalStateException("Missing canonical department: " + departmentId.value());
            }
        }
        for (DepartmentRecord record : directory.registry().records()) {
            if (!record.worldIdentityRoot().equals(worldIdentity.identity())
                    || !record.worldIdentityRootDigest().equals(worldIdentity.rootDigest())) {
                throw new IllegalStateException("Department belongs to a different World Identity root: "
                        + record.departmentId().value());
            }
        }
    }

    private static List<DepartmentId> canonicalDepartmentIds() {
        return List.of(
                DepartmentSchema.PROCESSING,
                DepartmentSchema.PACKAGING,
                DepartmentSchema.SHIPPING,
                DepartmentSchema.OFFICE,
                DepartmentSchema.MAINTENANCE
        );
    }

    private DepartmentRecord requireDepartment(DepartmentId departmentId) {
        return directory.registry().find(Objects.requireNonNull(departmentId, "departmentId"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown department: " + departmentId.value()));
    }
}
