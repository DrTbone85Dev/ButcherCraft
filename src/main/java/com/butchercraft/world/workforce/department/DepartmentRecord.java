package com.butchercraft.world.workforce.department;

import java.util.Objects;
import java.util.Optional;

public record DepartmentRecord(
        int schemaVersion,
        DepartmentId departmentId,
        String worldIdentityRoot,
        String worldIdentityRootDigest,
        String displayName,
        Optional<DepartmentAnchor> anchor,
        Optional<String> color,
        Optional<String> icon,
        long recordRevision
) {
    public DepartmentRecord {
        if (schemaVersion != DepartmentSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported department schema version: " + schemaVersion);
        }
        departmentId = Objects.requireNonNull(departmentId, "departmentId");
        worldIdentityRoot = DepartmentValidation.requireIdentity(worldIdentityRoot, "worldIdentityRoot");
        worldIdentityRootDigest = DepartmentValidation.requireDigest(worldIdentityRootDigest, "worldIdentityRootDigest");
        displayName = DepartmentValidation.requireText(displayName, "displayName");
        anchor = Objects.requireNonNull(anchor, "anchor");
        color = Objects.requireNonNull(color, "color")
                .map(value -> DepartmentValidation.requireText(value, "color"));
        icon = Objects.requireNonNull(icon, "icon")
                .map(value -> DepartmentValidation.requireText(value, "icon"));
        if (recordRevision < 0L) {
            throw new IllegalArgumentException("Department record revision must not be negative: " + recordRevision);
        }
    }

    public DepartmentRecord withAnchor(DepartmentAnchor nextAnchor) {
        return new DepartmentRecord(
                schemaVersion,
                departmentId,
                worldIdentityRoot,
                worldIdentityRootDigest,
                displayName,
                Optional.of(Objects.requireNonNull(nextAnchor, "nextAnchor")),
                color,
                icon,
                recordRevision + 1L
        );
    }

    public DepartmentRecord withRadius(int radius) {
        DepartmentAnchor current = anchor.orElseThrow(() ->
                new IllegalStateException("Department has no anchor: " + departmentId.value()));
        return withAnchor(new DepartmentAnchor(current.dimensionIdentity(), current.x(), current.y(), current.z(), radius));
    }
}
