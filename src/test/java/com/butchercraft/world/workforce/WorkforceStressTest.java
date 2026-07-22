package com.butchercraft.world.workforce;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.butchercraft.world.workforce.WorkforceTestFixtures.definition;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkforceStressTest {
    @Test
    void tenThousandBusinessesWithMultipleDefinitionsLoadDeterministically() {
        List<WorkforceDefinition> definitions = IntStream.range(0, 10_000)
                .boxed()
                .flatMap(index -> List.of(
                        definition("business_" + index, "primary"),
                        definition("business_" + index, "secondary")
                ).stream())
                .toList();

        WorkforceRegistry first = WorkforceRegistry.of(definitions);
        WorkforceRegistry second = WorkforceRegistry.of(definitions.reversed());

        assertEquals(first.definitions(), second.definitions());
        assertEquals(20_000, first.size());

        Set<WorkforceDefinitionId> definitionIds = new HashSet<>(first.definitions().stream()
                .map(WorkforceDefinition::workforceDefinitionId)
                .toList());
        assertEquals(20_000, definitionIds.size());
        assertEquals(2, first.findByBusinessId(new com.butchercraft.world.business.BusinessId("business_9999")).size());
        first.definitions().forEach(definition -> {
            assertEquals(2, definition.positions().size());
            assertEquals(3, definition.staffingRule().minimumStaffing());
            assertEquals(3, definition.staffingRule().maximumStaffing());
        });
    }
}
