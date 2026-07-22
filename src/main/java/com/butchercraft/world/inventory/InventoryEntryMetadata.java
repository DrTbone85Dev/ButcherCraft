package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

public record InventoryEntryMetadata(
        Optional<String> lotNumber,
        OptionalLong expirationSimulationTick,
        OptionalInt qualityBasisPoints,
        Optional<ActorId> originActorId
) implements Comparable<InventoryEntryMetadata> {
    private static final int MAX_LOT_NUMBER_LENGTH = 128;
    private static final int MAX_QUALITY_BASIS_POINTS = 10_000;

    public InventoryEntryMetadata {
        lotNumber = Objects.requireNonNull(lotNumber, "lotNumber").map(InventoryEntryMetadata::requireLotNumber);
        expirationSimulationTick = Objects.requireNonNull(expirationSimulationTick, "expirationSimulationTick");
        qualityBasisPoints = Objects.requireNonNull(qualityBasisPoints, "qualityBasisPoints");
        originActorId = Objects.requireNonNull(originActorId, "originActorId");
        expirationSimulationTick.ifPresent(value -> {
            if (value < 0L) {
                throw new IllegalArgumentException("Inventory expiration simulation tick must not be negative: " + value);
            }
        });
        qualityBasisPoints.ifPresent(value -> {
            if (value < 0 || value > MAX_QUALITY_BASIS_POINTS) {
                throw new IllegalArgumentException("Inventory quality basis points must be between 0 and 10000: "
                        + value);
            }
        });
    }

    public static InventoryEntryMetadata empty() {
        return new InventoryEntryMetadata(
                Optional.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty()
        );
    }

    @Override
    public int compareTo(InventoryEntryMetadata other) {
        Objects.requireNonNull(other, "other");
        int lotComparison = lotNumber.orElse("").compareTo(other.lotNumber.orElse(""));
        if (lotComparison != 0) {
            return lotComparison;
        }
        int expirationComparison = Long.compare(
                expirationSimulationTick.orElse(Long.MIN_VALUE),
                other.expirationSimulationTick.orElse(Long.MIN_VALUE)
        );
        if (expirationComparison != 0) {
            return expirationComparison;
        }
        int qualityComparison = Integer.compare(
                qualityBasisPoints.orElse(Integer.MIN_VALUE),
                other.qualityBasisPoints.orElse(Integer.MIN_VALUE)
        );
        if (qualityComparison != 0) {
            return qualityComparison;
        }
        return originActorId.map(ActorId::value).orElse("")
                .compareTo(other.originActorId.map(ActorId::value).orElse(""));
    }

    private static String requireLotNumber(String lotNumber) {
        String normalized = Objects.requireNonNull(lotNumber, "lotNumber").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Inventory lot number cannot be blank");
        }
        if (normalized.length() > MAX_LOT_NUMBER_LENGTH) {
            throw new IllegalArgumentException("Inventory lot number exceeds " + MAX_LOT_NUMBER_LENGTH + " characters");
        }
        return normalized;
    }
}
