package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessRuntimeConfigurationIdentity;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record ProductionDeadlineIdentity(String value) {
    public ProductionDeadlineIdentity {
        value = ProductionValidation.requireExternalIdentity(value, "Production deadline identity");
    }

    static ProductionDeadlineIdentity from(
            ProductionRunId runId,
            ProductionDeadlineType type,
            long businessDayIndex,
            BusinessTimeOfDay deadlineTime,
            BusinessRuntimeConfigurationIdentity configurationIdentity,
            String sourceWorldDayIdentity,
            String sourceDimensionIdentity,
            String sourceIdentity
    ) {
        String canonical = "schema_version=" + ProductionSchema.CURRENT_VERSION + "\n"
                + "run_id=" + Objects.requireNonNull(runId, "runId").value() + "\n"
                + "type=" + Objects.requireNonNull(type, "type").name().toLowerCase(java.util.Locale.ROOT) + "\n"
                + "business_day_index=" + businessDayIndex + "\n"
                + "business_time=" + Objects.requireNonNull(deadlineTime, "deadlineTime").displayText() + "\n"
                + "business_runtime_configuration_identity="
                + Objects.requireNonNull(configurationIdentity, "configurationIdentity").value() + "\n"
                + "source_world_day_identity="
                + ProductionValidation.requireText(sourceWorldDayIdentity, "Deadline source world-day identity", 256)
                + "\n"
                + "source_dimension_identity="
                + ProductionValidation.requireExternalIdentity(sourceDimensionIdentity, "Deadline source dimension identity")
                + "\n"
                + "source_identity="
                + ProductionValidation.requireExternalIdentity(sourceIdentity, "Deadline source identity")
                + "\n";
        return new ProductionDeadlineIdentity("butchercraft:production_deadline/v1/" + sha256(canonical));
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
