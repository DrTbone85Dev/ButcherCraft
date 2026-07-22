package com.butchercraft.world.workforce;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.workforce.WorkforceTestFixtures.CONFIGURATION;
import static com.butchercraft.world.workforce.WorkforceTestFixtures.definition;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkforceValidationTest {
    @Test
    void definitionRejectsDuplicatePositionIdsAndInvalidStaffingRules() {
        WorkforceDefinition valid = definition("alpha_market", "primary");
        WorkforcePosition position = valid.positions().getFirst();

        assertThrows(IllegalArgumentException.class, () -> new WorkforceDefinition(
                valid.businessId(),
                new WorkforceDefinitionId("duplicate_positions"),
                List.of(position, position),
                List.of(valid.shiftAssignments().getFirst()),
                new WorkforceStaffingRule(List.of(position.positionId()), List.of(), 1, 1),
                WorkforceSchema.CURRENT_VERSION
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkforceStaffingRule(
                List.of(position.positionId()),
                List.of(),
                2,
                1
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkforceDefinition(
                valid.businessId(),
                new WorkforceDefinitionId("zero_required_staffing"),
                List.of(position),
                List.of(new WorkforceShiftAssignment(position.assignedShiftId(), position.positionId(), 0, 1)),
                new WorkforceStaffingRule(List.of(position.positionId()), List.of(), 0, 1),
                WorkforceSchema.CURRENT_VERSION
        ));
    }

    @Test
    void registryRejectsUnknownBusinessReferences() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(3_210L);
        BusinessRuntimeRegistry runtimeRegistry = BusinessRuntimeRegistry.fromBusinesses(identity.businesses(), CONFIGURATION);
        WorkforceRegistry registry = WorkforceRegistry.of(List.of(definition("missing_business", "primary")));

        assertThrows(IllegalArgumentException.class, () -> registry.validateReferences(identity.businesses(), runtimeRegistry));
    }

    @Test
    void registryRejectsInvalidShiftReferences() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(4_444L);
        BusinessRuntimeRegistry runtimeRegistry = BusinessRuntimeRegistry.fromBusinesses(identity.businesses(), CONFIGURATION);
        BusinessId businessId = identity.businesses().stream()
                .filter(business -> runtimeRegistry.find(business.id()).orElseThrow().shifts().stream().anyMatch(shift -> shift.id().equals("day")))
                .findFirst()
                .orElseThrow()
                .id();
        WorkforcePosition position = new WorkforcePosition(
                new PositionId("night_manager"),
                WorkforcePositionType.MANAGER,
                "Night Manager",
                WorkforceSkillLevel.EXPERIENCED,
                List.of(CertificationType.FOOD_SAFETY),
                "night",
                true,
                1
        );
        WorkforceDefinition definition = new WorkforceDefinition(
                businessId,
                new WorkforceDefinitionId("invalid_shift_definition"),
                List.of(position),
                List.of(new WorkforceShiftAssignment("night", position.positionId(), 1, 1)),
                new WorkforceStaffingRule(List.of(position.positionId()), List.of(), 1, 1),
                WorkforceSchema.CURRENT_VERSION
        );
        WorkforceRegistry registry = WorkforceRegistry.of(List.of(definition));

        assertThrows(IllegalArgumentException.class, () -> registry.validateReferences(identity.businesses(), runtimeRegistry));
    }

    @Test
    void primitiveModelsRejectInvalidEnumsAndCertifications() {
        assertThrows(IllegalArgumentException.class, () -> WorkforcePositionType.fromSerializedName("unknown"));
        assertThrows(IllegalArgumentException.class, () -> WorkforceSkillLevel.fromSerializedName("unknown"));
        assertThrows(IllegalArgumentException.class, () -> CertificationType.fromSerializedName("unknown"));
        assertThrows(IllegalArgumentException.class, () -> new WorkforcePosition(
                new PositionId("bad_cert_position"),
                WorkforcePositionType.MANAGER,
                "Bad Certification Position",
                WorkforceSkillLevel.EXPERIENCED,
                List.of(CertificationType.NONE, CertificationType.FOOD_SAFETY),
                "day",
                true,
                1
        ));
    }
}
