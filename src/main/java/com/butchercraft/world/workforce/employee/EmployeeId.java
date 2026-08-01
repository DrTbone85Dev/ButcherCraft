package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Objects;
import java.util.regex.Pattern;

public record EmployeeId(String value) implements Comparable<EmployeeId> {
    private static final Pattern CANONICAL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public EmployeeId {
        value = Objects.requireNonNull(value, "value").strip();
        if (!CANONICAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Employee identity must be canonical: " + value);
        }
    }

    public static EmployeeId from(
            WorldIdentityRootIdentity worldIdentity,
            BusinessId businessId,
            long sequence,
            String creationSourceIdentity
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(businessId, "businessId");
        if (sequence < 0L) {
            throw new IllegalArgumentException("Employee sequence must not be negative: " + sequence);
        }
        String source = EmployeeValidation.requireIdentity(creationSourceIdentity, "creationSourceIdentity");
        String canonical = new StringBuilder("schema_version=")
                .append(EmployeeSchema.CURRENT_VERSION)
                .append('\n')
                .append("world_identity=")
                .append(worldIdentity.identity())
                .append('\n')
                .append("world_identity_digest=")
                .append(worldIdentity.rootDigest())
                .append('\n')
                .append("business_id=")
                .append(businessId.value())
                .append('\n')
                .append("sequence=")
                .append(sequence)
                .append('\n')
                .append("source=")
                .append(source)
                .append('\n')
                .toString();
        return new EmployeeId("butchercraft:employee/v1/" + EmployeeDigest.sha256(canonical));
    }

    @Override
    public int compareTo(EmployeeId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
