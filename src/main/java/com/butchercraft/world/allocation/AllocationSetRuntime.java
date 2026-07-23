package com.butchercraft.world.allocation;

import java.util.Optional;
import java.util.OptionalLong;

public final class AllocationSetRuntime {
    private AllocationRuntimeView state;

    private AllocationSetRuntime(AllocationRuntimeView state) {
        this.state = AllocationValidation.required(state, "state");
    }

    static AllocationSetRuntime requested(
            AllocationSetDefinition definition,
            AllocationMetadata metadata
    ) {
        AllocationSetDefinition set = AllocationValidation.required(definition, "definition");
        return new AllocationSetRuntime(new AllocationRuntimeView(
                set.id(),
                AllocationRuntimeStatus.REQUESTED,
                set.creationSimulationTick(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                set.expirationSimulationTick(),
                set.creationSimulationTick(),
                java.util.List.of(),
                Optional.empty(),
                Optional.empty(),
                AllocationValidation.required(metadata, "metadata"),
                0L,
                AllocationSchema.CURRENT_VERSION
        ));
    }

    static AllocationSetRuntime loaded(AllocationRuntimeView view) {
        return new AllocationSetRuntime(view);
    }

    public synchronized AllocationRuntimeView snapshot() {
        return state;
    }

    synchronized AllocationRuntimeView transition(
            AllocationRuntimeTransitionRequest request
    ) {
        AllocationRuntimeTransitionRequest transition = AllocationValidation.required(
                request,
                "request"
        );
        if (!state.allocationSetId().equals(transition.allocationSetId())) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.UNKNOWN_SET,
                    transition.allocationSetId().value(),
                    "Transition request does not match this AllocationSet runtime"
            );
        }
        if (state.status().isTerminal()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.TERMINAL_RUNTIME,
                    state.allocationSetId().value(),
                    "Terminal Allocation runtime cannot transition"
            );
        }
        if (!state.status().allowedNextStatuses().contains(transition.targetStatus())) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_TRANSITION,
                    state.allocationSetId().value(),
                    "Invalid Allocation runtime transition: " + state.status()
                            + " -> " + transition.targetStatus()
            );
        }
        if (transition.transitionSimulationTick() < state.lastUpdatedSimulationTick()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_SIMULATION_TICK,
                    state.allocationSetId().value(),
                    "Allocation runtime transition tick cannot move backward"
            );
        }

        OptionalLong waiting = state.waitingSimulationTick();
        OptionalLong allocated = state.allocatedSimulationTick();
        OptionalLong activated = state.activatedSimulationTick();
        OptionalLong released = state.releasedSimulationTick();
        java.util.List<AllocationCommitmentId> commitments = state.commitmentIds();
        Optional<AllocationRuntimeFailureCode> failureCode = Optional.empty();
        Optional<String> failureMessage = Optional.empty();
        switch (transition.targetStatus()) {
            case WAITING -> {
                waiting = OptionalLong.of(transition.transitionSimulationTick());
                failureCode = transition.failureCode();
                failureMessage = transition.failureMessage();
            }
            case ALLOCATED -> {
                allocated = OptionalLong.of(transition.transitionSimulationTick());
                commitments = transition.commitmentIds();
            }
            case ACTIVE -> activated = OptionalLong.of(transition.transitionSimulationTick());
            case RELEASED -> released = OptionalLong.of(transition.transitionSimulationTick());
            case FAILED, EXPIRED -> {
                failureCode = transition.failureCode();
                failureMessage = transition.failureMessage();
            }
            case REQUESTED -> throw new IllegalStateException(
                    "REQUESTED transition passed request validation"
            );
        }
        state = new AllocationRuntimeView(
                state.allocationSetId(),
                transition.targetStatus(),
                state.createdSimulationTick(),
                waiting,
                allocated,
                activated,
                released,
                state.expirationSimulationTick(),
                transition.transitionSimulationTick(),
                commitments,
                failureCode,
                failureMessage,
                state.metadata(),
                AllocationRuntimeValidation.incrementRevision(state.revision()),
                state.schemaVersion()
        );
        return state;
    }
}
