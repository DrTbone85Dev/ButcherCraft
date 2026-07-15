package com.butchercraft.engine.modifier;

/**
 * Category for deterministic modifier effects.
 *
 * <p>Quality modifiers adjust the quality score directly. Yield modifiers are additive basis-point
 * adjustments to the operation yield. Warning modifiers create inspectable non-fatal warnings.
 * The enum has no Minecraft dependency.</p>
 */
public enum ModifierCategory {
    QUALITY,
    YIELD,
    WARNING
}
