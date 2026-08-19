package com.butchercraft.world.execution;

public record MachineRunGenerationAllocator(
        String workstationInstanceIdentity,
        long nextGeneration
) implements Comparable<MachineRunGenerationAllocator> {
    public MachineRunGenerationAllocator {
        workstationInstanceIdentity = ExecutionValidation.requireId(
                workstationInstanceIdentity,
                "Machine Run allocator Workstation Instance Identity"
        );
        if (nextGeneration <= 0L) {
            throw new IllegalArgumentException("Next Machine Run generation must be positive");
        }
    }

    @Override
    public int compareTo(MachineRunGenerationAllocator other) {
        return workstationInstanceIdentity.compareTo(other.workstationInstanceIdentity);
    }
}
