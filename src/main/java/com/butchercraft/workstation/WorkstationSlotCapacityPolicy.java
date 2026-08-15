package com.butchercraft.workstation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Workstation-owned, versioned slot-capacity policy. */
public final class WorkstationSlotCapacityPolicy {
    public static final int SCHEMA_VERSION = 1;
    private final int[] capacities;
    private final String configurationIdentity;

    private WorkstationSlotCapacityPolicy(int[] capacities) {
        this.capacities = capacities.clone();
        for (int capacity : this.capacities) {
            if (capacity <= 0) throw new IllegalArgumentException("Workstation slot capacity must be positive");
        }
        this.configurationIdentity = calculateIdentity(this.capacities);
    }

    public static WorkstationSlotCapacityPolicy uniform(int slotCount, int capacity) {
        if (slotCount <= 0) throw new IllegalArgumentException("Workstation slot count must be positive");
        int[] capacities = new int[slotCount];
        Arrays.fill(capacities, capacity);
        return new WorkstationSlotCapacityPolicy(capacities);
    }

    public static WorkstationSlotCapacityPolicy perSlot(int... capacities) {
        if (capacities == null || capacities.length == 0) {
            throw new IllegalArgumentException("Workstation slot capacities must not be empty");
        }
        return new WorkstationSlotCapacityPolicy(capacities);
    }

    public int slotCount() {
        return capacities.length;
    }

    public int capacity(int slot) {
        if (slot < 0 || slot >= capacities.length) {
            throw new IllegalArgumentException("Workstation slot is outside capacity policy range");
        }
        return capacities[slot];
    }

    public String configurationIdentity() {
        return configurationIdentity;
    }

    public int[] capacities() {
        return capacities.clone();
    }

    private static String calculateIdentity(int[] capacities) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("butchercraft:workstation_slot_capacity/v1".getBytes(StandardCharsets.UTF_8));
            for (int capacity : capacities) {
                digest.update((byte) (capacity >>> 24));
                digest.update((byte) (capacity >>> 16));
                digest.update((byte) (capacity >>> 8));
                digest.update((byte) capacity);
            }
            return "butchercraft:workstation_slot_capacity/v1/" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
