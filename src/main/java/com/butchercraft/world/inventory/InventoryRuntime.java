package com.butchercraft.world.inventory;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class InventoryRuntime {
    private final InventoryId inventoryId;
    private InventoryStatus status;
    private List<InventoryEntry> entries;
    private long lastSimulationTick;
    private final int schemaVersion;

    public InventoryRuntime(
            InventoryId inventoryId,
            InventoryStatus status,
            Collection<InventoryEntry> entries,
            long lastSimulationTick,
            int schemaVersion
    ) {
        this.inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        this.status = Objects.requireNonNull(status, "status");
        this.entries = copyEntries(entries);
        this.lastSimulationTick = requireTick(lastSimulationTick);
        if (schemaVersion != InventorySchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported inventory runtime schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
    }

    public static InventoryRuntime empty(InventoryId inventoryId) {
        return new InventoryRuntime(
                inventoryId,
                InventoryStatus.ACTIVE,
                List.of(),
                0L,
                InventorySchema.CURRENT_VERSION
        );
    }

    public synchronized InventoryId inventoryId() {
        return inventoryId;
    }

    public synchronized InventoryStatus status() {
        return status;
    }

    public synchronized List<InventoryEntry> entries() {
        return entries;
    }

    public synchronized long lastSimulationTick() {
        return lastSimulationTick;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public synchronized long quantityOf(GoodId goodId) {
        Objects.requireNonNull(goodId, "goodId");
        long total = 0L;
        for (InventoryEntry entry : entries) {
            if (entry.goodId().equals(goodId)) {
                total = Math.addExact(total, entry.quantity());
            }
        }
        return total;
    }

    public synchronized long quantityOf(GoodId goodId, UnitOfMeasure unit) {
        Objects.requireNonNull(goodId, "goodId");
        Objects.requireNonNull(unit, "unit");
        long total = 0L;
        for (InventoryEntry entry : entries) {
            if (entry.goodId().equals(goodId) && entry.unitOfMeasure() == unit) {
                total = Math.addExact(total, entry.quantity());
            }
        }
        return total;
    }

    public synchronized void transitionTo(InventoryStatus nextStatus, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        status = Objects.requireNonNull(nextStatus, "nextStatus");
        lastSimulationTick = simulationTick;
    }

    public synchronized void addEntry(InventoryEntry addition, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        entries = entriesAfterAdding(entries, addition);
        lastSimulationTick = simulationTick;
    }

    public synchronized void removeEntry(InventoryEntry removal, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        entries = entriesAfterRemoving(entries, removal);
        lastSimulationTick = simulationTick;
    }

    public synchronized void replaceEntries(Collection<InventoryEntry> nextEntries, long simulationTick) {
        requireCurrentOrFutureTick(simulationTick);
        entries = copyEntries(nextEntries);
        lastSimulationTick = simulationTick;
    }

    static List<InventoryEntry> entriesAfterAdding(List<InventoryEntry> current, InventoryEntry addition) {
        Objects.requireNonNull(addition, "addition");
        List<InventoryEntry> updated = new ArrayList<>(current.size() + 1);
        boolean merged = false;
        for (InventoryEntry existing : current) {
            if (existing.sameStoredGood(addition)) {
                updated.add(existing.withQuantity(Math.addExact(existing.quantity(), addition.quantity())));
                merged = true;
            } else {
                updated.add(existing);
            }
        }
        if (!merged) {
            updated.add(addition);
        }
        return copyEntries(updated);
    }

    static List<InventoryEntry> entriesAfterRemoving(List<InventoryEntry> current, InventoryEntry removal) {
        Objects.requireNonNull(removal, "removal");
        List<InventoryEntry> updated = new ArrayList<>(current.size());
        boolean found = false;
        for (InventoryEntry existing : current) {
            if (existing.sameStoredGood(removal)) {
                found = true;
                if (removal.quantity() > existing.quantity()) {
                    throw new IllegalArgumentException("Inventory removal exceeds available quantity: "
                            + removal.goodId().value());
                }
                long remaining = existing.quantity() - removal.quantity();
                if (remaining > 0L) {
                    updated.add(existing.withQuantity(remaining));
                }
            } else {
                updated.add(existing);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Inventory removal references a missing entry: "
                    + removal.goodId().value());
        }
        return copyEntries(updated);
    }

    private static List<InventoryEntry> copyEntries(Collection<InventoryEntry> source) {
        Objects.requireNonNull(source, "entries");
        List<InventoryEntry> copied = source.stream()
                .map(entry -> Objects.requireNonNull(entry, "entry"))
                .sorted()
                .toList();
        for (int index = 1; index < copied.size(); index++) {
            if (copied.get(index - 1).sameStoredGood(copied.get(index))) {
                throw new IllegalArgumentException("Inventory runtime contains duplicate entries: "
                        + copied.get(index).goodId().value());
            }
        }
        return List.copyOf(copied);
    }

    private void requireCurrentOrFutureTick(long simulationTick) {
        requireTick(simulationTick);
        if (simulationTick < lastSimulationTick) {
            throw new IllegalArgumentException("Inventory simulation tick must not move backward: "
                    + inventoryId.value());
        }
    }

    private static long requireTick(long simulationTick) {
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Inventory simulation tick must not be negative: " + simulationTick);
        }
        return simulationTick;
    }
}
