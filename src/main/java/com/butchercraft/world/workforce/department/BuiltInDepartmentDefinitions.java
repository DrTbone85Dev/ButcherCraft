package com.butchercraft.world.workforce.department;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BuiltInDepartmentDefinitions {
    private static final String OVERWORLD = "minecraft:overworld";

    private BuiltInDepartmentDefinitions() {
    }

    public static DepartmentDirectory defaults(WorldIdentityRootIdentity worldIdentity) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        DepartmentAnchor processingAnchor = new DepartmentAnchor(
                OVERWORLD,
                DepartmentSchema.DEFAULT_PROCESSING_X,
                DepartmentSchema.DEFAULT_PROCESSING_Y,
                DepartmentSchema.DEFAULT_PROCESSING_Z,
                DepartmentSchema.DEFAULT_PROCESSING_RADIUS
        );
        return new DepartmentDirectory(
                DepartmentRegistry.of(List.of(
                        record(worldIdentity, DepartmentSchema.PROCESSING, "Processing", Optional.of(processingAnchor),
                                Optional.of("#8f5f3a"), Optional.of("gear")),
                        record(worldIdentity, DepartmentSchema.PACKAGING, "Packaging", Optional.empty(),
                                Optional.of("#4f8f6f"), Optional.of("box")),
                        record(worldIdentity, DepartmentSchema.SHIPPING, "Shipping", Optional.empty(),
                                Optional.of("#496f9f"), Optional.of("truck")),
                        record(worldIdentity, DepartmentSchema.OFFICE, "Office", Optional.empty(),
                                Optional.of("#8f7fb0"), Optional.of("clipboard")),
                        record(worldIdentity, DepartmentSchema.MAINTENANCE, "Maintenance", Optional.empty(),
                                Optional.of("#8f8f8f"), Optional.of("wrench"))
                )),
                Optional.empty()
        );
    }

    private static DepartmentRecord record(
            WorldIdentityRootIdentity worldIdentity,
            DepartmentId id,
            String displayName,
            Optional<DepartmentAnchor> anchor,
            Optional<String> color,
            Optional<String> icon
    ) {
        return new DepartmentRecord(
                DepartmentSchema.CURRENT_VERSION,
                id,
                worldIdentity.identity(),
                worldIdentity.rootDigest(),
                displayName,
                anchor,
                color,
                icon,
                1L
        );
    }
}
