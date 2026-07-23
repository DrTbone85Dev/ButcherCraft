package com.butchercraft.world.production;

import com.butchercraft.world.workforce.CertificationType;
import com.butchercraft.world.workforce.PositionId;
import com.butchercraft.world.workforce.WorkforceDefinitionId;
import com.butchercraft.world.workforce.WorkforceSkillLevel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record ProductionWorkforceRequirement(
        Optional<WorkforceDefinitionId> workforceDefinitionId,
        Set<PositionId> requiredPositions,
        int minimumActiveWorkers,
        Set<CertificationType> requiredCertifications,
        Optional<WorkforceSkillLevel> minimumSkillLevel,
        boolean requiredThroughoutExecution
) {
    private static final ProductionWorkforceRequirement NONE = new ProductionWorkforceRequirement(
            Optional.empty(), Set.of(), 0, Set.of(), Optional.empty(), false
    );

    public ProductionWorkforceRequirement {
        workforceDefinitionId = Objects.requireNonNull(workforceDefinitionId, "workforceDefinitionId");
        TreeSet<PositionId> positions = new TreeSet<>(java.util.Comparator.comparing(PositionId::value));
        Objects.requireNonNull(requiredPositions, "requiredPositions")
                .forEach(position -> positions.add(Objects.requireNonNull(position, "position")));
        requiredPositions = Collections.unmodifiableSet(positions);
        if (minimumActiveWorkers < 0) {
            throw new IllegalArgumentException("Minimum active production workforce must not be negative");
        }
        EnumSet<CertificationType> certifications = EnumSet.noneOf(CertificationType.class);
        Objects.requireNonNull(requiredCertifications, "requiredCertifications")
                .forEach(certification -> certifications.add(Objects.requireNonNull(certification, "certification")));
        if (certifications.contains(CertificationType.NONE) && certifications.size() > 1) {
            throw new IllegalArgumentException("Certification NONE cannot be combined with other requirements");
        }
        requiredCertifications = Collections.unmodifiableSet(certifications);
        minimumSkillLevel = Objects.requireNonNull(minimumSkillLevel, "minimumSkillLevel");
    }

    public static ProductionWorkforceRequirement none() {
        return NONE;
    }

    public boolean required() {
        return workforceDefinitionId.isPresent()
                || !requiredPositions.isEmpty()
                || minimumActiveWorkers > 0
                || !requiredCertifications.isEmpty()
                || minimumSkillLevel.isPresent();
    }
}
