package com.butchercraft.processing.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.modifier.ModifierCategory;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

public record StaticModifierDefinition(
        ResourceLocation id,
        String reason,
        ModifierCategory category,
        int effect,
        int priority
) {
    private static final Codec<ModifierCategory> CATEGORY_CODEC = Codec.STRING.comapFlatMap(value -> {
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        try {
            return DataResult.success(ModifierCategory.valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown modifier category: " + value);
        }
    }, category -> category.name().toLowerCase(Locale.ROOT));

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Raw::id),
            DefinitionCodecs.nonBlankString("modifier reason").fieldOf("reason").forGetter(Raw::reason),
            CATEGORY_CODEC.fieldOf("category").forGetter(Raw::category),
            Codec.INT.fieldOf("effect").forGetter(Raw::effect),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Raw::priority)
    ).apply(instance, Raw::new));

    public static final Codec<StaticModifierDefinition> CODEC =
            RAW_CODEC.comapFlatMap(StaticModifierDefinition::fromRaw, StaticModifierDefinition::toRaw);

    public StaticModifierDefinition {
        Objects.requireNonNull(id, "id");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Modifier reason cannot be blank");
        }
        Objects.requireNonNull(category, "category");
    }

    public ProcessingModifier toEngineModifier() {
        return new ProcessingModifier(EngineId.of(id.toString()), reason, category, effect, priority);
    }

    private static DataResult<StaticModifierDefinition> fromRaw(Raw raw) {
        try {
            return DataResult.success(new StaticModifierDefinition(raw.id, raw.reason, raw.category, raw.effect, raw.priority));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(id, reason, category, effect, priority);
    }

    private record Raw(ResourceLocation id, String reason, ModifierCategory category, int effect, int priority) {
    }
}
