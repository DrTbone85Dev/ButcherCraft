package com.butchercraft.engine.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic modifier application service.
 *
 * <p>The service sorts modifiers by priority and stable identity before applying them. It is pure,
 * inspectable, and Minecraft-independent. Future gameplay systems should supply causes as
 * modifiers rather than embedding hidden quality logic inside stations or entities.</p>
 */
public final class ModifierSystem {
    private ModifierSystem() {
    }

    /**
     * Applies modifiers in deterministic order.
     *
     * @param modifiers immutable or mutable input collection; it is copied before use
     * @return ordered applied modifiers, total quality delta, total yield basis-point delta, and warnings
     * @throws ArithmeticException when accumulated effects overflow
     */
    public static ModifierApplication apply(List<ProcessingModifier> modifiers) {
        Objects.requireNonNull(modifiers, "modifiers");
        List<ProcessingModifier> ordered = modifiers.stream().sorted().toList();
        int qualityDelta = 0;
        int yieldBasisPointsDelta = 0;
        List<String> warnings = new ArrayList<>();

        for (ProcessingModifier modifier : ordered) {
            if (modifier.category() == ModifierCategory.QUALITY) {
                qualityDelta = Math.addExact(qualityDelta, modifier.effect());
            } else if (modifier.category() == ModifierCategory.YIELD) {
                yieldBasisPointsDelta = Math.addExact(yieldBasisPointsDelta, modifier.effect());
            } else if (modifier.category() == ModifierCategory.WARNING) {
                warnings.add(modifier.reason());
            }
        }

        return new ModifierApplication(ordered, qualityDelta, yieldBasisPointsDelta, warnings);
    }
}
