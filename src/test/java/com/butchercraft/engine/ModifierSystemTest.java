package com.butchercraft.engine;

import com.butchercraft.engine.modifier.ModifierApplication;
import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ModifierSystem;
import com.butchercraft.engine.modifier.ProcessingModifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModifierSystemTest {
    @Test
    void modifiersUseStablePriorityAndIdentityOrdering() {
        ProcessingModifier later = EngineTestFixtures.qualityModifier("later", 1, 20);
        ProcessingModifier firstByPriority = EngineTestFixtures.qualityModifier("first", 1, 10);
        ProcessingModifier secondById = EngineTestFixtures.qualityModifier("second", 1, 10);

        ModifierApplication application = ModifierSystem.apply(List.of(later, secondById, firstByPriority));

        assertEquals(List.of(firstByPriority, secondById, later), application.appliedModifiers());
    }

    @Test
    void multipleModifiersAreAppliedDeterministically() {
        List<ProcessingModifier> modifiers = List.of(
                EngineTestFixtures.qualityModifier("bonus", 30, 10),
                EngineTestFixtures.qualityModifier("penalty", -10, 20),
                EngineTestFixtures.warningModifier("warning", 5)
        );

        ModifierApplication first = ModifierSystem.apply(modifiers);
        ModifierApplication second = ModifierSystem.apply(modifiers);

        assertEquals(first, second);
        assertEquals(20, first.qualityDelta());
        assertEquals(List.of("Warning fixture warning"), first.warningReasons());
    }

    @Test
    void equalPriorityBehaviorUsesStableTieBreakers() {
        ProcessingModifier b = new ProcessingModifier(EngineId.of("b"), "Second", ModifierCategory.QUALITY, 1, 1);
        ProcessingModifier a = new ProcessingModifier(EngineId.of("a"), "First", ModifierCategory.QUALITY, 1, 1);

        assertEquals(List.of(a, b), ModifierSystem.apply(List.of(b, a)).appliedModifiers());
    }

    @Test
    void reasonsAreRetainedAndValidated() {
        ProcessingModifier modifier = new ProcessingModifier(EngineId.of("sharp_blade"), "Sharp blade", ModifierCategory.QUALITY, 25, 1);

        assertEquals("Sharp blade", ModifierSystem.apply(List.of(modifier)).appliedModifiers().getFirst().reason());
        assertThrows(IllegalArgumentException.class, () -> new ProcessingModifier(
                EngineId.of("blank_reason"),
                " ",
                ModifierCategory.QUALITY,
                1,
                1
        ));
    }
}
