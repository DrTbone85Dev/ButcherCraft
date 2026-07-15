package com.butchercraft.engine.modifier;

import java.util.List;
import java.util.Objects;

/**
 * Immutable summary of applying a modifier set.
 *
 * <p>The ordered modifier list and warning reasons make the calculation inspectable. Integration
 * code can expose this summary in diagnostics or tooltips without the engine depending on
 * Minecraft UI classes.</p>
 */
public record ModifierApplication(
        List<ProcessingModifier> appliedModifiers,
        int qualityDelta,
        int yieldBasisPointsDelta,
        List<String> warningReasons
) {
    public ModifierApplication {
        appliedModifiers = List.copyOf(Objects.requireNonNull(appliedModifiers, "appliedModifiers"));
        warningReasons = List.copyOf(Objects.requireNonNull(warningReasons, "warningReasons"));
        warningReasons.forEach(reason -> {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Warning reason cannot be blank");
            }
        });
    }
}
