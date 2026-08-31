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
import com.butchercraft.integration.checkpoint.LiveWorkstationCheckpointDependencyCollector;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.checkpoint.LiveCheckpointRequestResult;
import com.butchercraft.world.checkpoint.LiveCheckpointService;
import com.butchercraft.world.checkpoint.LiveCheckpointStatus;
import com.butchercraft.world.checkpoint.StartupRecoveryService;
import com.butchercraft.world.checkpoint.StartupRecoveryStatus;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import com.butchercraft.workstation.projection.WorkstationProjectionReadCode;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
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
                .then(Commands.literal("create")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> requestLiveCheckpoint(context.getSource())))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showLiveCheckpointStatus(context.getSource())))
                .then(Commands.literal("startup-status")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showStartupRecoveryStatus(context.getSource())))
                .then(Commands.literal("workstation-projections")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showWorkstationProjectionStatus(context.getSource())))
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

    private static int showWorkstationProjectionStatus(CommandSourceStack source) {
        var service = DurableWorkstationProjectionService.INSTANCE;
        var diagnostics = service.diagnostics(source.getServer());
        source.sendSuccess(() -> Component.literal("Durable Workstation projections: "
                + diagnostics.available() + " available, "
                + diagnostics.retired() + " retired, "
                + diagnostics.legacyUnavailable() + " legacy unavailable, "
                + diagnostics.blocked() + " blocked, "
                + diagnostics.persistedBytes() + " bytes"), false);
        service.enumerate(source.getServer()).stream()
                .filter(result -> result.code() != WorkstationProjectionReadCode.AVAILABLE
                        && result.code() != WorkstationProjectionReadCode.RETIRED)
                .limit(8)
                .forEach(result -> source.sendFailure(Component.literal(
                        result.instanceId().value() + ": " + result.code().name().toLowerCase()
                                + " - " + result.detail())));
        var checkpoint = WorkstationCheckpointProjectionService.capture(
                source.getServer(),
                WorkstationEndpointService.INSTANCE.instanceRegistrySnapshot(source.getServer()),
                LiveWorkstationCheckpointDependencyCollector.collect(source.getServer())
        );
        source.sendSuccess(() -> Component.literal("Checkpoint Workstation coverage: "
                + checkpoint.status().serializedName()
                + ", required=" + checkpoint.requiredProjectionCount()
                + ", available=" + checkpoint.availableProjectionCount()
                + ", loaded=" + checkpoint.loadedProjectionCount()
                + ", unloaded=" + checkpoint.unloadedProjectionCount()
                + ", participant=" + checkpoint.participantBytes() + " bytes"), false);
        checkpoint.blockers().stream().limit(8).forEach(blocker -> source.sendFailure(Component.literal(
                blocker.instanceId().value() + ": " + blocker.projectionState().name().toLowerCase()
                        + " - " + blocker.detail())));
        return checkpoint.restorable() ? Command.SINGLE_SUCCESS : 0;
    }

    private static int requestLiveCheckpoint(CommandSourceStack source) {
        LiveCheckpointRequestResult result = LiveCheckpointService.INSTANCE.requestManual(source.getServer());
        source.sendSuccess(() -> Component.literal("Checkpoint " + result.outcome().name().toLowerCase()
                + ": " + result.detail()), false);
        result.activeGeneration().ifPresent(generation -> source.sendSuccess(
                () -> Component.literal("Active generation: " + generation.canonicalValue()),
                false
        ));
        return switch (result.outcome()) {
            case ACCEPTED, COALESCED -> Command.SINGLE_SUCCESS;
            case BUSY, RECOVERY_BLOCKED, NOT_INITIALIZED -> 0;
        };
    }

    private static int showLiveCheckpointStatus(CommandSourceStack source) {
        LiveCheckpointStatus checkpoint = LiveCheckpointService.INSTANCE.status();
        source.sendSuccess(() -> Component.literal("Checkpoint status: "
                + checkpoint.state().name().toLowerCase()), false);
        checkpoint.committedGeneration().ifPresent(generation -> source.sendSuccess(
                () -> Component.literal("Committed: " + generation.canonicalValue()),
                false
        ));
        source.sendSuccess(() -> Component.literal("Tick: " + checkpoint.checkpointTick()
                + ", participants: " + checkpoint.participantCount()
                + ", next periodic tick: " + checkpoint.nextPeriodicTick()), false);
        source.sendSuccess(() -> Component.literal("Freeze: "
                + checkpoint.freezeDurationNanos() / 1_000_000.0 + " ms, publication: "
                + checkpoint.publicationDurationNanos() / 1_000_000.0 + " ms, head: "
                + checkpoint.headCommitDurationNanos() / 1_000_000.0 + " ms, size: "
                + checkpoint.checkpointSizeBytes() + " bytes"), false);
        if (!checkpoint.failures().isEmpty()) {
            var failure = checkpoint.failures().getFirst();
            source.sendFailure(Component.literal("Last failure: " + failure.code().name().toLowerCase()
                    + " - " + failure.message()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showStartupRecoveryStatus(CommandSourceStack source) {
        StartupRecoveryStatus value = StartupRecoveryService.INSTANCE.status();
        source.sendSuccess(() -> Component.literal("Startup recovery: "
                + value.state().name().toLowerCase()), false);
        source.sendSuccess(() -> Component.literal("Live coherence: "
                + value.liveCoherence().name().toLowerCase()
                + ", selected source: " + value.selectedSource().name().toLowerCase()), false);
        value.selectedGeneration().ifPresent(generation -> source.sendSuccess(
                () -> Component.literal("Selected generation: " + generation.canonicalValue()), false));
        value.previousValidGeneration().ifPresent(generation -> source.sendSuccess(
                () -> Component.literal("Previous valid generation: " + generation.canonicalValue()), false));
        value.workstationRestorabilityStatus().ifPresent(restorability -> source.sendSuccess(
                () -> Component.literal("Workstation restorability: " + restorability), false));
        value.restorationIdentity().ifPresent(identity -> source.sendSuccess(
                () -> Component.literal("Restoration identity: " + identity.value()), false));
        source.sendSuccess(() -> Component.literal("Restoration participants: "
                + value.completedParticipants() + "/17"), false);
        value.restoredSimulationTick().ifPresent(tick -> source.sendSuccess(
                () -> Component.literal("Restored tick: " + tick), false));
        source.sendSuccess(() -> Component.literal("Authority blocks: "
                + value.remainingAuthorityBlocks().size()), false);
        source.sendSuccess(() -> Component.literal("Policy B runs: "
                + (value.policyBRuns().isEmpty() ? "none" : String.join(", ", value.policyBRuns()))), false);
        source.sendSuccess(() -> Component.literal("Preserved authorized work: "
                + (value.preservedAuthorizedWork().isEmpty()
                ? "none" : String.join(", ", value.preservedAuthorizedWork()))), false);
        source.sendSuccess(() -> Component.literal("Consequential mutation: "
                + (value.consequentialMutationPermitted() ? "permitted" : "blocked")), false);
        value.fallbackReason().ifPresent(reason -> source.sendSuccess(
                () -> Component.literal("Fallback reason: " + reason), false));
        value.lastRestorationResultIdentity().ifPresent(identity -> source.sendSuccess(
                () -> Component.literal("Last restoration result: " + identity), false));
        value.failureCode().ifPresent(code -> source.sendFailure(
                Component.literal("Startup failure: " + code.name().toLowerCase())));
        value.operatorActionRequired().ifPresent(action -> source.sendFailure(
                Component.literal("Operator action: " + action)));
        source.sendSuccess(() -> Component.literal("Timing: live="
                + value.liveAnalysisNanos() / 1_000_000.0 + " ms, selection="
                + value.checkpointSelectionNanos() / 1_000_000.0 + " ms, restoration="
                + value.restorationNanos() / 1_000_000.0 + " ms, total="
                + value.totalNanos() / 1_000_000.0 + " ms"), false);
        return value.failureCode().isEmpty() ? Command.SINGLE_SUCCESS : 0;
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
