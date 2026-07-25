package com.butchercraft.command;

import com.butchercraft.config.CommonConfig;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointCaptureRequest;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointFailure;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointFailureCode;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointFormatter;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointHarness;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointOperation;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointReport;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointRequestContext;
import com.butchercraft.development.checkpoint.DevelopmentCheckpointRoots;
import com.butchercraft.development.checkpoint.DevelopmentPlatformDeterminismManifest;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class DevelopmentCheckpointCommands {
    private static final DevelopmentCheckpointHarness HARNESS = new DevelopmentCheckpointHarness();

    private DevelopmentCheckpointCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("checkpoint")
                .then(Commands.literal("capture")
                        .executes(context -> runCapture(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> runWithContext(
                                context.getSource(),
                                DevelopmentCheckpointOperation.LIST,
                                HARNESS::list
                        )))
                .then(Commands.literal("validate")
                        .executes(context -> runWithContext(
                                context.getSource(),
                                DevelopmentCheckpointOperation.VALIDATE,
                                HARNESS::validate
                        )))
                .then(Commands.literal("inspect-selected")
                        .executes(context -> runWithContext(
                                context.getSource(),
                                DevelopmentCheckpointOperation.INSPECT_SELECTED,
                                HARNESS::inspectSelected
                        )))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("generation", StringArgumentType.string())
                                .executes(context -> runWithContext(
                                        context.getSource(),
                                        DevelopmentCheckpointOperation.INSPECT_GENERATION,
                                        requestContext -> HARNESS.inspectGeneration(
                                                requestContext,
                                                StringArgumentType.getString(context, "generation")
                                        )
                                ))))
                .then(Commands.literal("restore-selected")
                        .executes(context -> runWithContext(
                                context.getSource(),
                                DevelopmentCheckpointOperation.RESTORE_SELECTED,
                                HARNESS::rejectUnsafeLiveRestore
                        )));
    }

    private static int runCapture(CommandSourceStack source) {
        ContextResult context = context(source, DevelopmentCheckpointOperation.CAPTURE);
        if (context.report().isPresent()) {
            send(source, context.report().orElseThrow());
            return 0;
        }

        SimulationClock clock;
        try {
            clock = SimulationClockService.INSTANCE.clock(source.getServer());
        } catch (RuntimeException exception) {
            send(source, DevelopmentCheckpointReport.blocked(
                    DevelopmentCheckpointOperation.CAPTURE,
                    context.context().map(DevelopmentCheckpointRequestContext::checkpointRoot),
                    new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.MISSING_CLOCK_SERVICE,
                            "simulationClock",
                            "Simulation Clock service is unavailable"
                    )
            ));
            return 0;
        }

        SimulationSchedulerManager scheduler;
        try {
            scheduler = SimulationSchedulerService.INSTANCE.managerFor(source.getServer());
        } catch (RuntimeException exception) {
            send(source, DevelopmentCheckpointReport.blocked(
                    DevelopmentCheckpointOperation.CAPTURE,
                    context.context().map(DevelopmentCheckpointRequestContext::checkpointRoot),
                    new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.MISSING_SCHEDULER_SERVICE,
                            "simulationScheduler",
                            "Simulation Scheduler service is unavailable"
                    )
            ));
            return 0;
        }

        DevelopmentCheckpointReport report = HARNESS.capture(new DevelopmentCheckpointCaptureRequest(
                context.context().orElseThrow(),
                clock,
                scheduler
        ));
        send(source, report);
        return report.successful() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int runWithContext(
            CommandSourceStack source,
            DevelopmentCheckpointOperation operation,
            Function<DevelopmentCheckpointRequestContext, DevelopmentCheckpointReport> operationRunner
    ) {
        Objects.requireNonNull(operationRunner, "operationRunner");
        ContextResult context = context(source, operation);
        DevelopmentCheckpointReport report = context.report()
                .orElseGet(() -> operationRunner.apply(context.context().orElseThrow()));
        send(source, report);
        return report.successful() ? Command.SINGLE_SUCCESS : 0;
    }

    private static ContextResult context(CommandSourceStack source, DevelopmentCheckpointOperation operation) {
        Path worldRoot;
        Path checkpointRoot;
        try {
            worldRoot = source.getServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath()
                    .normalize();
            checkpointRoot = DevelopmentCheckpointRoots.checkpointRoot(worldRoot);
        } catch (RuntimeException exception) {
            return ContextResult.failed(DevelopmentCheckpointReport.blocked(
                    operation,
                    Optional.empty(),
                    new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.NO_ACTIVE_WORLD,
                            "world",
                            "No active world root is available for development checkpoint invocation"
                    )
            ));
        }

        if (!CommonConfig.ENABLE_DEVELOPMENT_DIAGNOSTIC.get()) {
            return ContextResult.failed(DevelopmentCheckpointReport.blocked(
                    operation,
                    Optional.of(checkpointRoot),
                    new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.NOT_IN_DEVELOPMENT_ENVIRONMENT,
                            "enableDevelopmentDiagnostic",
                            "Development checkpoint invocation is disabled by common config"
                    )
            ));
        }

        Optional<WorldIdentity> identity = WorldIdentityService.INSTANCE.currentIdentity();
        if (identity.isEmpty()) {
            return ContextResult.failed(DevelopmentCheckpointReport.blocked(
                    operation,
                    Optional.of(checkpointRoot),
                    new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.MISSING_WORLD_IDENTITY,
                            "worldIdentity",
                            "No active World Identity has been initialized"
                    )
            ));
        }

        WorldIdentityRootIdentity rootIdentity = WorldIdentityRootIdentities.from(identity.orElseThrow());
        DevelopmentCheckpointRequestContext context = new DevelopmentCheckpointRequestContext(
                true,
                worldRoot,
                checkpointRoot,
                new WorldIdentityRootReference(
                        rootIdentity.identity(),
                        rootIdentity.schemaVersion(),
                        rootIdentity.rootDigest()
                ),
                DevelopmentPlatformDeterminismManifest.currentReference()
        );
        return ContextResult.available(context);
    }

    private static void send(CommandSourceStack source, DevelopmentCheckpointReport report) {
        for (String line : DevelopmentCheckpointFormatter.lines(report)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
    }

    private record ContextResult(
            Optional<DevelopmentCheckpointRequestContext> context,
            Optional<DevelopmentCheckpointReport> report
    ) {
        private ContextResult {
            context = Objects.requireNonNull(context, "context");
            report = Objects.requireNonNull(report, "report");
        }

        private static ContextResult available(DevelopmentCheckpointRequestContext context) {
            return new ContextResult(Optional.of(context), Optional.empty());
        }

        private static ContextResult failed(DevelopmentCheckpointReport report) {
            return new ContextResult(Optional.empty(), Optional.of(report));
        }
    }
}
