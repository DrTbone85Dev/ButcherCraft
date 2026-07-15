package com.butchercraft.engine.quantity;

import java.util.Arrays;

/**
 * Exact quantity unit used by the engine.
 *
 * <p>Milestone 1B uses grams for processing weight and includes pieces only to prove
 * compatibility validation. The enum keeps a unit identity so packages, cases, or carcasses can
 * be introduced deliberately without silent conversion. It has no Minecraft dependency.</p>
 */
public enum QuantityUnit {
    GRAM("gram"),
    PIECE("piece");

    private final String id;

    QuantityUnit(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static QuantityUnit fromId(String id) {
        return Arrays.stream(values())
                .filter(unit -> unit.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported quantity unit id: " + id));
    }
}
