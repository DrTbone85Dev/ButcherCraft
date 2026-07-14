package com.butchercraft.engine.modifier;

import com.butchercraft.engine.EngineId;

import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable, inspectable modifier applied by an operation.
 *
 * <p>A modifier records its stable id, human-readable reason, category, numeric effect, and
 * priority. It has no hidden randomness and no Minecraft dependency. Future station, employee,
 * cleanliness, or refrigeration systems can provide modifiers without becoming part of the
 * engine model.</p>
 */
public record ProcessingModifier(
        EngineId id,
        String reason,
        ModifierCategory category,
        int effect,
        int priority
) implements Comparable<ProcessingModifier> {
    private static final Comparator<ProcessingModifier> ORDERING = Comparator
            .comparingInt(ProcessingModifier::priority)
            .thenComparing(modifier -> modifier.id().value())
            .thenComparing(ProcessingModifier::category)
            .thenComparing(ProcessingModifier::reason)
            .thenComparingInt(ProcessingModifier::effect);

    public ProcessingModifier {
        Objects.requireNonNull(id, "id");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Modifier reason cannot be blank");
        }
        Objects.requireNonNull(category, "category");
    }

    @Override
    public int compareTo(ProcessingModifier other) {
        return ORDERING.compare(this, other);
    }
}
