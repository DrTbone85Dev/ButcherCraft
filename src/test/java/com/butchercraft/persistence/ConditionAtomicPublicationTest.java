package com.butchercraft.persistence;

import com.butchercraft.workstation.condition.*;
import com.butchercraft.workstation.projection.*;
import com.butchercraft.world.execution.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static com.butchercraft.workstation.projection.ConditionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionAtomicPublicationTest {
    @TempDir Path directory;

    @Test
    void failureBeforeReplacementLeavesBothProductAndConditionAtPreState() throws Exception {
        verifyInterruptedPublication(false);
    }

    @Test
    void failureAfterReplacementLeavesBothProductAndConditionAtPostState() throws Exception {
        verifyInterruptedPublication(true);
    }

    private void verifyInterruptedPublication(boolean replaceBeforeFailure) throws Exception {
        var instance = instance("butchercraft:grinder", 1, 1);
        var policy = MachineConditionPolicy.create("butchercraft:grinder", "test:atomic_condition", 100,
                3, 1, 5, 10, 80, Optional.empty(), List.of());
        var state = MachineConditionState.healthy(instance.instanceId(), policy,
                ConditionInitializationEvidence.create(instance.instanceId(), policy.identity(), Optional.empty()), 0);
        var before = projection(instance, 1, 0, List.of(new ItemStack(Items.STONE), ItemStack.EMPTY),
                Optional.empty(), Optional.empty()).withCondition(2, ConditionProjection.initial(state));
        var storage = new WorkstationProjectionStorage(directory.resolve("projections"));
        var receipts = new ConditionReceiptStorage(directory.resolve("receipts"));
        var codec = new WorkstationProjectionCodec();
        storage.save(before);
        var operation = ExecutionOperationId.of("butchercraft:execution_operation/v1/atomic_condition");
        var product = ExecutionOwnerResultEvidence.of("butchercraft:workstation", "test:product_result",
                new ExecutionDomainEffectIdentity("butchercraft:execution_domain_effect/v1/atomic_condition"), "sha256:" + "4".repeat(64));
        var candidate = ConditionProcessingCandidates.prepare(before.condition().orElseThrow(),
                ConditionProcessingPreparation.of(state), operation, "sha256:" + "7".repeat(64), product, 20, 3);
        receipts.stage(candidate.receipt());
        assertEquals(before, storage.read(instance).projection().orElseThrow(), "Prepared evidence is not an effect");
        var after = projection(instance, 3, 1, List.of(ItemStack.EMPTY, new ItemStack(Items.DIRT)),
                Optional.of(operation.value()), Optional.of(candidate.ownerResult().ownerResultIdentity()))
                .withCondition(3, candidate.projection());
        AtomicInteger attempts = new AtomicInteger();
        var faultedPublisher = new AtomicFilePublication((source, target, replace) -> {
            attempts.incrementAndGet();
            assertEquals(after, codec.decode(Files.readAllBytes(source)), "Every attempted publication is a joint frozen candidate");
            if (replaceBeforeFailure) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("Injected publication interruption");
        }, ignored -> { });
        if (replaceBeforeFailure) {
            assertDoesNotThrow(() -> faultedPublisher.publishFrozen(storage.pathFor(instance.instanceId()),
                    codec.freeze(after), "joint condition fixture"), "Exact read-back proves replacement despite reported move failure");
        } else {
            assertThrows(UncheckedIOException.class, () -> faultedPublisher.publishFrozen(storage.pathFor(instance.instanceId()),
                    codec.freeze(after), "joint condition fixture"));
        }
        assertEquals(1, attempts.get());
        var restarted = new WorkstationProjectionStorage(storage.rootDirectory()).read(instance).projection().orElseThrow();
        assertEquals(replaceBeforeFailure ? after : before, restarted);
        assertEquals(replaceBeforeFailure ? 3 : 0, restarted.condition().orElseThrow().state().orElseThrow().mechanicalLoss());
        assertEquals(replaceBeforeFailure ? 1 : 0, receipts.closure(restarted.condition().orElseThrow()).size());
        assertEquals(replaceBeforeFailure, restarted.slots().getFirst().exactStack().isEmpty());
        try (var paths = Files.walk(directory)) {
            assertTrue(paths.noneMatch(path -> path.getFileName().toString().contains(".attempt-")));
        }
    }
}
