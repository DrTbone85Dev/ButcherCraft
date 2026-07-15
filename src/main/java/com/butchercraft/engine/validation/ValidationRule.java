package com.butchercraft.engine.validation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.context.ProcessingContext;

/**
 * Focused validation contract for processing operations.
 *
 * <p>A rule evaluates a {@link ProcessingContext} and returns an explicit {@link ValidationResult}.
 * Rules must be deterministic, must not mutate the context or product, and must not call into
 * Minecraft. Rule order is the order stored on the operation.</p>
 */
public interface ValidationRule {
    EngineId id();

    /**
     * Evaluates the context without side effects.
     *
     * @param context immutable processing context
     * @return accepted, warning, or rejected result with inspectable diagnostics
     */
    ValidationResult evaluate(ProcessingContext context);
}
