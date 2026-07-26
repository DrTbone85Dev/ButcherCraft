package com.butchercraft.productioncontrol;

import com.butchercraft.world.production.ProductionRunId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.Optional;

public record ProductionOrderData(
        String templateId,
        Optional<String> runId
) {
    public static final String BEEF_PATTIES_TEMPLATE_ID =
            "butchercraft:production_template/beef_patties_manual_chain";

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("template_id").forGetter(Raw::templateId),
            Codec.STRING.optionalFieldOf("run_id").forGetter(Raw::runId)
    ).apply(instance, Raw::new));

    public static final Codec<ProductionOrderData> CODEC =
            RAW_CODEC.comapFlatMap(ProductionOrderData::fromRaw, ProductionOrderData::toRaw);

    public static final StreamCodec<RegistryFriendlyByteBuf, ProductionOrderData> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProductionOrderData decode(RegistryFriendlyByteBuf buffer) {
                    String templateId = buffer.readUtf();
                    Optional<String> runId = buffer.readBoolean()
                            ? Optional.of(buffer.readUtf())
                            : Optional.empty();
                    return new ProductionOrderData(templateId, runId);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, ProductionOrderData value) {
                    buffer.writeUtf(value.templateId());
                    buffer.writeBoolean(value.runId().isPresent());
                    value.runId().ifPresent(buffer::writeUtf);
                }
            };

    public ProductionOrderData {
        templateId = requireTemplate(templateId);
        runId = Objects.requireNonNull(runId, "runId")
                .map(value -> ProductionRunId.of(value).value());
    }

    public static ProductionOrderData beefPattiesOrder() {
        return new ProductionOrderData(BEEF_PATTIES_TEMPLATE_ID, Optional.empty());
    }

    public ProductionOrderData withRun(ProductionRunId id) {
        return new ProductionOrderData(templateId, Optional.of(Objects.requireNonNull(id, "id").value()));
    }

    public boolean isBeefPattiesTemplate() {
        return BEEF_PATTIES_TEMPLATE_ID.equals(templateId);
    }

    private static String requireTemplate(String value) {
        String normalized = Objects.requireNonNull(value, "templateId").strip().toLowerCase(java.util.Locale.ROOT);
        if (!BEEF_PATTIES_TEMPLATE_ID.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported Production order template: " + value);
        }
        return normalized;
    }

    private static DataResult<ProductionOrderData> fromRaw(Raw raw) {
        try {
            return DataResult.success(new ProductionOrderData(raw.templateId, raw.runId));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private Raw toRaw() {
        return new Raw(templateId, runId);
    }

    private record Raw(String templateId, Optional<String> runId) {
    }
}
