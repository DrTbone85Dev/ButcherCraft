package com.butchercraft.world.workforce;

import com.butchercraft.world.business.BusinessId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.workforce.WorkforceTestFixtures.definition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkforceRegistryTest {
    @Test
    void registryLoadsDefinitionsInDeterministicBusinessAndDefinitionOrder() {
        WorkforceDefinition beta = definition("beta_market", "secondary");
        WorkforceDefinition alphaSecondary = definition("alpha_market", "secondary");
        WorkforceDefinition alphaPrimary = definition("alpha_market", "primary");

        WorkforceRegistry registry = WorkforceRegistry.of(List.of(beta, alphaSecondary, alphaPrimary));

        assertEquals(List.of(
                        "alpha_market_workforce_primary",
                        "alpha_market_workforce_secondary",
                        "beta_market_workforce_secondary"
                ),
                registry.definitions().stream().map(definition -> definition.workforceDefinitionId().value()).toList());
        assertEquals(3, registry.size());
        assertTrue(registry.contains(alphaPrimary.workforceDefinitionId()));
        assertEquals(2, registry.findByBusinessId(new BusinessId("alpha_market")).size());
    }

    @Test
    void registryRejectsDuplicateDefinitionIds() {
        WorkforceDefinition definition = definition("alpha_market", "primary");

        assertThrows(IllegalArgumentException.class, () -> WorkforceRegistry.of(List.of(definition, definition)));
    }
}
