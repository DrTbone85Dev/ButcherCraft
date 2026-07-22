package com.butchercraft.world.workforce;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record WorkforcePosition(
        PositionId positionId,
        WorkforcePositionType positionType,
        String displayName,
        WorkforceSkillLevel requiredSkillLevel,
        List<CertificationType> requiredCertifications,
        String assignedShiftId,
        boolean required,
        int maximumWorkers
) {
    private static final Pattern VALID_SHIFT_ID = Pattern.compile("^[a-z0-9_]+$");

    public WorkforcePosition {
        positionId = Objects.requireNonNull(positionId, "positionId");
        positionType = Objects.requireNonNull(positionType, "positionType");
        displayName = requireNonBlank(displayName, "displayName");
        requiredSkillLevel = Objects.requireNonNull(requiredSkillLevel, "requiredSkillLevel");
        requiredCertifications = copyCertifications(requiredCertifications);
        assignedShiftId = requireShiftId(assignedShiftId);
        if (maximumWorkers <= 0) {
            throw new IllegalArgumentException("Workforce position maximum workers must be positive: " + positionId.value());
        }
    }

    private static List<CertificationType> copyCertifications(List<CertificationType> certifications) {
        Objects.requireNonNull(certifications, "requiredCertifications");
        if (certifications.isEmpty()) {
            throw new IllegalArgumentException("Workforce position certifications must not be empty");
        }
        Set<CertificationType> copied = new LinkedHashSet<>();
        for (CertificationType certification : certifications) {
            copied.add(Objects.requireNonNull(certification, "certification"));
        }
        if (copied.size() != certifications.size()) {
            throw new IllegalArgumentException("Workforce position certifications must not contain duplicates");
        }
        if (copied.contains(CertificationType.NONE) && copied.size() > 1) {
            throw new IllegalArgumentException("Workforce certification none must not be combined with other certifications");
        }
        return List.copyOf(copied);
    }

    static String requireShiftId(String shiftId) {
        Objects.requireNonNull(shiftId, "shiftId");
        shiftId = shiftId.toLowerCase(Locale.ROOT);
        if (!VALID_SHIFT_ID.matcher(shiftId).matches()) {
            throw new IllegalArgumentException("Workforce shift id must use lowercase snake case: " + shiftId);
        }
        return shiftId;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Workforce position " + fieldName + " must not be blank");
        }
        return value;
    }
}
