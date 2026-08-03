package com.butchercraft.entity.employee;

import com.butchercraft.integration.employee.EmployeeWorkstationOperationService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.workforce.employee.EmployeeAnchor;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
import com.butchercraft.world.workforce.employee.EmployeeWorkstationOperationState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EmployeeEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> EMPLOYEE_ID =
            SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DISPLAY_NAME =
            SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> STATUS =
            SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> PRESENCE =
            SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SHIFT =
            SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> NAVIGATION_STATE =
            SynchedEntityData.defineId(EmployeeEntity.class, EntityDataSerializers.STRING);

    private static final String TAG_EMPLOYEE_ID = "EmployeeId";
    private static final String TAG_ANCHOR_X = "AnchorX";
    private static final String TAG_ANCHOR_Y = "AnchorY";
    private static final String TAG_ANCHOR_Z = "AnchorZ";
    private static final String TAG_ANCHOR_RADIUS = "AnchorRadius";
    private static final int VALIDATION_INTERVAL_TICKS = 20;
    private static final int PATH_RESTART_COOLDOWN_TICKS = 20;
    private static final int PROGRESS_CHECK_INTERVAL_TICKS = 20;
    private static final int STALL_INTERVAL_TICKS = 80;
    private static final int MAX_RETRIES_PER_CANDIDATE = 1;
    private static final int DEPARTMENT_FAILURE_RETRY_TICKS = 200;
    private static final int MAX_DEPARTMENT_CANDIDATES = 12;
    private static final int MAX_IDLE_CANDIDATES = 16;
    private static final int PATH_SEARCH_RANGE_BLOCKS = EmployeeSchema.SCHEMA_1_MAX_NAVIGATION_RANGE_BLOCKS;
    private static final double MAX_NAVIGATION_RANGE_BLOCKS = EmployeeSchema.SCHEMA_1_MAX_NAVIGATION_RANGE_BLOCKS;
    private static final double MAX_NAVIGATION_RANGE_SQUARED =
            MAX_NAVIGATION_RANGE_BLOCKS * MAX_NAVIGATION_RANGE_BLOCKS;
    private static final int IDLE_PAUSE_MIN_TICKS = 80;
    private static final int IDLE_PAUSE_SPAN_TICKS = 80;
    private static final float PATH_SEARCH_NODE_MULTIPLIER = 4.0F;
    private static final double TRAVEL_SPEED = 1.0D;
    private static final double IDLE_SPEED = 0.65D;
    private static final double MIN_NEXT_NODE_PROGRESS_DISTANCE_SQUARED = 0.04D;
    private static final double ARRIVAL_HORIZONTAL_TOLERANCE = 1.1D;
    private static final double ARRIVAL_VERTICAL_TOLERANCE = 1.25D;

    private BlockPos anchorPos = BlockPos.ZERO;
    private int anchorRadius = 8;
    private int validationTicks;
    private BlockPos lookTargetPos;
    private List<BlockPos> workstationCandidates = List.of();
    private String workstationDestinationIdentity = "";
    private String workstationType = "";
    private TravelDestination activeDestination = TravelDestination.none();
    private int selectedCandidateIndex;
    private int retryCount;
    private long nextPathAttemptTick;
    private long lastProgressTick;
    private long lastNodeProgressTick;
    private long lastProgressCheckTick;
    private long departmentFailureRetryTick;
    private Path activePath;
    private int activePathNodeIndex = -1;
    private int activePathNodeCount;
    private double bestNextNodeDistanceSquared = Double.MAX_VALUE;
    private double distanceToNextNode = -1.0D;
    private double distanceToFinalDestination = -1.0D;
    private int pathReplacementCount;
    private String lastPathReplacementReason = "none";
    private boolean pathAvailable;
    private boolean finalFailureReported;
    private NavigationRecoveryPhase recoveryPhase = NavigationRecoveryPhase.IDLE;
    private NavigationFailureReason lastFailureReason = NavigationFailureReason.NONE;
    private int idleSequence;
    private long nextIdleMovementTick;
    private EmployeeWorkstationOperationState workstationOperationState = EmployeeWorkstationOperationState.IDLE;
    private String workstationOperationReservationKey = "";
    private String workstationOperationWorkstation = "none";
    private String workstationOperationExecutionId = "none";
    private String workstationOperationReservationState = "none";
    private String workstationOperationRecipe = "none";
    private String workstationOperationFailure = "none";
    private BlockPos workstationOperationPosition;
    private long workstationOperationStateTick;
    private boolean workstationOperationRequestConsumed;

    public EmployeeEntity(EntityType<? extends EmployeeEntity> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
        configureGroundNavigation();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, PATH_SEARCH_RANGE_BLOCKS);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new EmployeeBoundedIdleGoal(this));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EMPLOYEE_ID, "");
        builder.define(DISPLAY_NAME, "Employee");
        builder.define(STATUS, "unknown");
        builder.define(PRESENCE, "unknown");
        builder.define(SHIFT, "Unassigned");
        builder.define(NAVIGATION_STATE, EmployeeNavigationState.OFF_SHIFT.serializedName());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (level().isClientSide) {
            return;
        }
        if (validationTicks-- <= 0) {
            validationTicks = VALIDATION_INTERVAL_TICKS;
            if (!EmployeeService.INSTANCE.synchronizeEntity(this)) {
                discard();
                return;
            }
        }
        EmployeeNavigationState state = navigationStateOrDefault();
        tickNavigationController(state);
        EmployeeWorkstationOperationService.INSTANCE.tick(this);
        faceLookTarget(navigationStateOrDefault());
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            try {
                EmployeeId employeeId = new EmployeeId(employeeIdValue());
                WorkstationReservationService.INSTANCE.invalidateByEmployee(
                        serverLevel.getServer(),
                        employeeId,
                        "employee entity removed"
                );
            } catch (IllegalArgumentException ignored) {
                // Unbound entities have no workstation reservation authority to release.
            }
        }
        super.remove(reason);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            player.sendSystemMessage(Component.translatable(
                    "entity.butchercraft.employee.inspect",
                    displayNameValue(),
                    statusValue(),
                    presenceValue(),
                    shiftValue()
            ));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_EMPLOYEE_ID, employeeIdValue());
        tag.putInt(TAG_ANCHOR_X, anchorPos.getX());
        tag.putInt(TAG_ANCHOR_Y, anchorPos.getY());
        tag.putInt(TAG_ANCHOR_Z, anchorPos.getZ());
        tag.putInt(TAG_ANCHOR_RADIUS, anchorRadius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_EMPLOYEE_ID)) {
            entityData.set(EMPLOYEE_ID, tag.getString(TAG_EMPLOYEE_ID));
        }
        anchorPos = new BlockPos(
                tag.getInt(TAG_ANCHOR_X),
                tag.getInt(TAG_ANCHOR_Y),
                tag.getInt(TAG_ANCHOR_Z)
        );
        anchorRadius = Math.max(1, tag.getInt(TAG_ANCHOR_RADIUS));
    }

    public void applyEmployeeRecord(EmployeeRecord record, EmployeeAnchor anchor) {
        entityData.set(EMPLOYEE_ID, record.employeeId().value());
        entityData.set(DISPLAY_NAME, record.displayName());
        entityData.set(STATUS, record.status().serializedName());
        entityData.set(PRESENCE, record.presenceState().serializedName());
        entityData.set(SHIFT, record.assignedShift()
                .map(com.butchercraft.world.workforce.employee.EmployeeShiftAssignment::shiftDisplayName)
                .orElse("Unassigned"));
        anchorPos = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        anchorRadius = anchor.radius();
        setCustomName(Component.literal(record.displayName()));
        setCustomNameVisible(true);
    }

    public void applyEmployeeObservation(EmployeePresenceObservation observation) {
        entityData.set(PRESENCE, observation.presenceState().serializedName());
        entityData.set(SHIFT, observation.assignedShift()
                .map(com.butchercraft.world.workforce.employee.EmployeeShiftAssignment::shiftDisplayName)
                .orElse("Unassigned"));
    }

    public void applyNavigationState(EmployeeNavigationState state) {
        entityData.set(NAVIGATION_STATE, state.serializedName());
        faceLookTarget(state);
    }

    public void applyWorkstationTravelTarget(Optional<WorkstationReservationService.WorkstationNavigationTarget> target) {
        if (target.isEmpty()) {
            workstationCandidates = List.of();
            workstationDestinationIdentity = "";
            workstationType = "";
            lookTargetPos = null;
            return;
        }
        WorkstationReservationService.WorkstationNavigationTarget value = target.orElseThrow();
        workstationCandidates = List.copyOf(value.approachCandidates());
        workstationDestinationIdentity = value.reservation().workstationIdentity();
        workstationType = value.reservation().workstationType();
        lookTargetPos = value.workstationPos();
        TravelDestination destination = workstationDestination();
        if (!destination.sameIdentityAndCandidates(activeDestination)) {
            resetTravel(destination);
        }
        skipInvalidCurrentCandidates();
    }

    public String employeeIdValue() {
        return entityData.get(EMPLOYEE_ID);
    }

    public String displayNameValue() {
        return entityData.get(DISPLAY_NAME);
    }

    public String statusValue() {
        return entityData.get(STATUS);
    }

    public String presenceValue() {
        return entityData.get(PRESENCE);
    }

    public String shiftValue() {
        return entityData.get(SHIFT);
    }

    public String navigationStateValue() {
        return entityData.get(NAVIGATION_STATE);
    }

    public BlockPos anchorPos() {
        return anchorPos;
    }

    public int anchorRadius() {
        return anchorRadius;
    }

    public boolean insideAnchorRadius() {
        return blockPosition().distManhattan(anchorPos) <= anchorRadius;
    }

    private void faceLookTarget(EmployeeNavigationState state) {
        if (lookTargetPos == null
                || (state != EmployeeNavigationState.WAITING_AT_WORKSTATION
                && state != EmployeeNavigationState.WALKING_TO_WORKSTATION
                && state != EmployeeNavigationState.RECOVERING_PATH
                && state != EmployeeNavigationState.TRYING_ALTERNATE_APPROACH)) {
            return;
        }
        double deltaX = lookTargetPos.getX() + 0.5D - getX();
        double deltaZ = lookTargetPos.getZ() + 0.5D - getZ();
        if (Math.abs(deltaX) < 0.0001D && Math.abs(deltaZ) < 0.0001D) {
            return;
        }
        float targetYRot = (float) (Math.atan2(deltaZ, deltaX) * 180.0D / Math.PI) - 90.0F;
        float yRot = state == EmployeeNavigationState.WAITING_AT_WORKSTATION
                ? targetYRot
                : Mth.approachDegrees(getYRot(), targetYRot, 10.0F);
        setYRot(yRot);
        setYHeadRot(yRot);
        getLookControl().setLookAt(lookTargetPos.getX() + 0.5D, lookTargetPos.getY() + 0.5D, lookTargetPos.getZ() + 0.5D);
    }

    private EmployeeNavigationState navigationStateOrDefault() {
        try {
            return EmployeeNavigationState.fromSerializedName(navigationStateValue());
        } catch (IllegalArgumentException exception) {
            return EmployeeNavigationState.OFF_SHIFT;
        }
    }

    public NavigationDiagnostics navigationDiagnostics() {
        List<BlockPos> diagnosticCandidates = !activeDestination.candidates().isEmpty()
                ? activeDestination.candidates()
                : workstationCandidates;
        BlockPos selected = selectedCandidateIndex >= 0 && selectedCandidateIndex < diagnosticCandidates.size()
                ? diagnosticCandidates.get(selectedCandidateIndex)
                : null;
        double distance = selected == null ? -1.0D : Math.sqrt(horizontalDistanceSquared(selected));
        double finalDestinationDistance = activeDestination.type() == DestinationType.NONE
                ? distanceToFinalDestination >= 0.0D ? distanceToFinalDestination : distance
                : Math.sqrt(horizontalDistanceSquared(activeDestination.rangeTarget()));
        long gameTime = level().getGameTime();
        return new NavigationDiagnostics(
                !activeDestination.candidates().isEmpty()
                        ? activeDestination.type().serializedName()
                        : workstationDestinationIdentity.isBlank() ? DestinationType.NONE.serializedName()
                        : DestinationType.WORKSTATION.serializedName(),
                !activeDestination.candidates().isEmpty()
                        ? activeDestination.identity()
                        : workstationDestinationIdentity,
                selected,
                selectedCandidateIndex,
                diagnosticCandidates.size(),
                pathAvailable,
                distance,
                finalDestinationDistance,
                distanceToNextNode,
                activePathNodeIndex,
                activePathNodeCount,
                lastNodeProgressTick == 0L ? 0L : Math.max(0L, gameTime - lastNodeProgressTick),
                lastProgressTick == 0L ? 0L : Math.max(0L, gameTime - lastProgressTick),
                pathReplacementCount,
                lastPathReplacementReason,
                retryCount,
                MAX_NAVIGATION_RANGE_BLOCKS,
                navigationSearchRangeBlocks(),
                activeDestination.type() != DestinationType.NONE && destinationWithinRange(activeDestination),
                PATH_SEARCH_NODE_MULTIPLIER,
                recoveryPhase.serializedName(),
                lastFailureReason.reasonCode()
        );
    }

    public EmployeeOperationDiagnostics workstationOperationDiagnostics() {
        return new EmployeeOperationDiagnostics(
                workstationOperationWorkstation,
                workstationOperationExecutionId,
                workstationOperationReservationState,
                workstationOperationRecipe,
                workstationOperationState.serializedName(),
                workstationOperationFailure
        );
    }

    public EmployeeWorkstationOperationState workstationOperationState() {
        return workstationOperationState;
    }

    public String workstationOperationReservationKey() {
        return workstationOperationReservationKey;
    }

    public boolean workstationOperationRequestConsumed() {
        return workstationOperationRequestConsumed;
    }

    public Optional<BlockPos> workstationOperationPosition() {
        return Optional.ofNullable(workstationOperationPosition);
    }

    public long workstationOperationStateTick() {
        return workstationOperationStateTick;
    }

    public void beginWorkstationOperation(
            String reservationKey,
            String workstationIdentity,
            BlockPos workstationPosition,
            String reservationState,
            String recipeIdentity
    ) {
        transitionWorkstationOperation(EmployeeWorkstationOperationState.PREPARING);
        workstationOperationReservationKey = requireOperationText(reservationKey, "reservationKey");
        workstationOperationWorkstation = requireOperationText(workstationIdentity, "workstationIdentity");
        workstationOperationPosition = Objects.requireNonNull(workstationPosition, "workstationPosition").immutable();
        workstationOperationExecutionId = "none";
        workstationOperationReservationState = requireOperationText(reservationState, "reservationState");
        workstationOperationRecipe = requireOperationText(recipeIdentity, "recipeIdentity");
        workstationOperationFailure = "none";
        workstationOperationRequestConsumed = true;
    }

    public void markWorkstationOperationOperating(String executionId, String reservationState) {
        transitionWorkstationOperation(EmployeeWorkstationOperationState.OPERATING);
        workstationOperationExecutionId = requireOperationText(executionId, "executionId");
        workstationOperationReservationState = requireOperationText(reservationState, "reservationState");
    }

    public void markWorkstationOperationWaiting(String reservationState) {
        transitionWorkstationOperation(EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION);
        workstationOperationReservationState = requireOperationText(reservationState, "reservationState");
    }

    public void markWorkstationOperationComplete(String reservationState) {
        transitionWorkstationOperation(EmployeeWorkstationOperationState.OPERATION_COMPLETE);
        workstationOperationReservationState = requireOperationText(reservationState, "reservationState");
        workstationOperationFailure = "none";
    }

    public void markWorkstationOperationFailure(String reservationState, String failureReason) {
        transitionWorkstationOperation(EmployeeWorkstationOperationState.FAILURE);
        workstationOperationReservationState = requireOperationText(reservationState, "reservationState");
        workstationOperationFailure = requireOperationText(failureReason, "failureReason");
        workstationOperationRequestConsumed = true;
    }

    public void refreshWorkstationOperationReservation(String reservationState) {
        workstationOperationReservationState = requireOperationText(reservationState, "reservationState");
    }

    public void finishWorkstationOperation() {
        transitionWorkstationOperation(EmployeeWorkstationOperationState.IDLE);
    }

    public void resetWorkstationOperation() {
        if (workstationOperationState.active()) {
            throw new IllegalStateException("Active employee workstation operation cannot be reset silently");
        }
        transitionWorkstationOperation(EmployeeWorkstationOperationState.IDLE);
        workstationOperationReservationKey = "";
        workstationOperationWorkstation = "none";
        workstationOperationExecutionId = "none";
        workstationOperationReservationState = "none";
        workstationOperationRecipe = "none";
        workstationOperationFailure = "none";
        workstationOperationPosition = null;
        workstationOperationRequestConsumed = false;
    }

    private void transitionWorkstationOperation(EmployeeWorkstationOperationState next) {
        if (!workstationOperationState.canTransitionTo(next)) {
            throw new IllegalStateException("Invalid employee workstation operation transition "
                    + workstationOperationState + " -> " + next);
        }
        workstationOperationState = next;
        workstationOperationStateTick = level().getGameTime();
    }

    private static String requireOperationText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private void configureGroundNavigation() {
        if (getNavigation() instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanOpenDoors(true);
            groundNavigation.setCanPassDoors(true);
            groundNavigation.setCanWalkOverFences(false);
            groundNavigation.setMaxVisitedNodesMultiplier(PATH_SEARCH_NODE_MULTIPLIER);
        }
    }

    private void tickNavigationController(EmployeeNavigationState state) {
        Optional<TravelDestination> destination = destinationFor(state);
        if (destination.isEmpty()) {
            if (isActiveTravelState(state)) {
                applyNavigationState(EmployeeNavigationState.NAVIGATION_FAILED);
            }
            if (state == EmployeeNavigationState.OFF_SHIFT) {
                stopTravel();
            }
            return;
        }
        TravelDestination nextDestination = destination.orElseThrow();
        if (!nextDestination.sameIdentityAndCandidates(activeDestination)) {
            resetTravel(nextDestination);
        } else {
            activeDestination = nextDestination;
        }
        if (arrivedAt(activeDestination)) {
            handleArrival();
            return;
        }
        if (state == EmployeeNavigationState.WAITING_AT_WORKSTATION) {
            applyNavigationState(EmployeeNavigationState.WALKING_TO_WORKSTATION);
        }
        if (recoveryPhase == NavigationRecoveryPhase.SAFE_FAILURE) {
            if (activeDestination.type() == DestinationType.DEPARTMENT
                    && level().getGameTime() >= departmentFailureRetryTick) {
                resetTravel(activeDestination);
            }
            return;
        }
        if (level().getGameTime() < nextPathAttemptTick) {
            return;
        }
        Optional<BlockPos> candidate = currentCandidate();
        if (candidate.isEmpty()) {
            failAllCandidates(candidateExhaustionReason());
            return;
        }
        BlockPos target = candidate.orElseThrow();
        Optional<NavigationFailureReason> candidateFailure = travelCandidateFailureReason(target, activeDestination);
        if (candidateFailure.isPresent()) {
            advanceRecovery(candidateFailure.orElseThrow());
            return;
        }
        if (!pathAvailable || getNavigation().isDone()) {
            if (getNavigation().isDone() && pathAvailable && !arrivedAt(activeDestination)) {
                advanceRecovery(NavigationFailureReason.NO_PATH);
                return;
            }
            startPath(target);
            return;
        }
        monitorProgress(target);
    }

    private Optional<TravelDestination> destinationFor(EmployeeNavigationState state) {
        if (state == EmployeeNavigationState.RECOVERING_PATH
                || state == EmployeeNavigationState.TRYING_ALTERNATE_APPROACH) {
            if (activeDestination.type() == DestinationType.WORKSTATION
                    && !workstationCandidates.isEmpty()
                    && !workstationDestinationIdentity.isBlank()) {
                return Optional.of(workstationDestination());
            }
            if (activeDestination.type() == DestinationType.DEPARTMENT) {
                return departmentDestination();
            }
            return Optional.empty();
        }
        if (state == EmployeeNavigationState.WALKING_TO_WORKSTATION
                || state == EmployeeNavigationState.WAITING_AT_WORKSTATION) {
            if (workstationCandidates.isEmpty() || workstationDestinationIdentity.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(workstationDestination());
        }
        if (state == EmployeeNavigationState.WALKING_TO_DEPARTMENT
                || state == EmployeeNavigationState.RETURNING_TO_DEPARTMENT
                || state == EmployeeNavigationState.RETURNING_TO_ANCHOR) {
            return departmentDestination();
        }
        if (state == EmployeeNavigationState.IDLE || state == EmployeeNavigationState.PRESENT_IN_DEPARTMENT) {
            if (!insideAnchorRadius()) {
                return departmentDestination();
            }
        }
        return Optional.empty();
    }

    private Optional<TravelDestination> departmentDestination() {
        List<BlockPos> candidates = departmentCandidates();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TravelDestination(
                DestinationType.DEPARTMENT,
                "department/" + anchorPos.getX() + "/" + anchorPos.getY() + "/" + anchorPos.getZ(),
                "department",
                candidates,
                anchorPos,
                Math.max(1.0D, Math.min(anchorRadius, 2)),
                ARRIVAL_VERTICAL_TOLERANCE,
                Optional.empty()
        ));
    }

    private List<BlockPos> departmentCandidates() {
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(anchorPos.immutable());
        int radius = Math.max(1, anchorRadius);
        for (int ring = 1; ring <= radius && candidates.size() < MAX_DEPARTMENT_CANDIDATES; ring++) {
            addDepartmentCandidate(candidates, anchorPos.offset(ring, 0, 0));
            addDepartmentCandidate(candidates, anchorPos.offset(-ring, 0, 0));
            addDepartmentCandidate(candidates, anchorPos.offset(0, 0, ring));
            addDepartmentCandidate(candidates, anchorPos.offset(0, 0, -ring));
            addDepartmentCandidate(candidates, anchorPos.offset(ring, 0, ring));
            addDepartmentCandidate(candidates, anchorPos.offset(ring, 0, -ring));
            addDepartmentCandidate(candidates, anchorPos.offset(-ring, 0, ring));
            addDepartmentCandidate(candidates, anchorPos.offset(-ring, 0, -ring));
        }
        return candidates.stream().distinct().toList();
    }

    private static void addDepartmentCandidate(List<BlockPos> candidates, BlockPos candidate) {
        if (candidates.size() < MAX_DEPARTMENT_CANDIDATES) {
            candidates.add(candidate.immutable());
        }
    }

    private Optional<BlockPos> currentCandidate() {
        if (selectedCandidateIndex < 0 || selectedCandidateIndex >= activeDestination.candidates().size()) {
            return Optional.empty();
        }
        return Optional.of(activeDestination.candidates().get(selectedCandidateIndex));
    }

    private TravelDestination workstationDestination() {
        BlockPos rangeTarget = lookTargetPos == null
                ? workstationCandidates.getFirst()
                : lookTargetPos;
        return new TravelDestination(
                DestinationType.WORKSTATION,
                workstationDestinationIdentity,
                workstationType,
                workstationCandidates,
                rangeTarget,
                ARRIVAL_HORIZONTAL_TOLERANCE,
                ARRIVAL_VERTICAL_TOLERANCE,
                Optional.ofNullable(lookTargetPos)
        );
    }

    private void resetTravel(TravelDestination destination) {
        activeDestination = destination;
        selectedCandidateIndex = 0;
        retryCount = 0;
        pathAvailable = false;
        finalFailureReported = false;
        recoveryPhase = NavigationRecoveryPhase.INITIAL_PATH;
        resetPathTelemetry();
        long gameTime = level().getGameTime();
        lastProgressTick = gameTime;
        lastNodeProgressTick = gameTime;
        lastProgressCheckTick = gameTime;
        nextPathAttemptTick = gameTime;
        getNavigation().stop();
    }

    private void stopTravel() {
        activeDestination = TravelDestination.none();
        selectedCandidateIndex = 0;
        retryCount = 0;
        pathAvailable = false;
        recoveryPhase = NavigationRecoveryPhase.IDLE;
        resetPathTelemetry();
        getNavigation().stop();
    }

    private void startPath(BlockPos target) {
        Optional<PathSelection> selection = reachablePathSelection(target);
        if (selection.isEmpty()) {
            pathAvailable = false;
            clearActivePathTelemetry();
            NavigationFailureReason reason = lastFailureReason == NavigationFailureReason.NONE
                    ? NavigationFailureReason.NO_PATH
                    : lastFailureReason;
            advanceRecovery(reason);
            return;
        }
        PathSelection selectedPath = selection.orElseThrow();
        selectedCandidateIndex = selectedPath.candidateIndex();
        Path path = selectedPath.path();
        boolean started = getNavigation().moveTo(path, TRAVEL_SPEED);
        pathAvailable = started;
        if (!started) {
            advanceRecovery(NavigationFailureReason.NO_PATH);
            return;
        }
        long gameTime = level().getGameTime();
        activePath = getNavigation().getPath();
        pathReplacementCount++;
        lastPathReplacementReason = recoveryPhase.serializedName();
        updatePathProgressTelemetry(activePath, selectedPath.target(), gameTime, true);
        lastProgressTick = gameTime;
        nextPathAttemptTick = gameTime + PATH_RESTART_COOLDOWN_TICKS;
    }

    private Optional<PathSelection> reachablePathSelection(BlockPos fallbackTarget) {
        lastPathReplacementReason = "path_creation_failed";
        NavigationFailureReason pathSelectionFailure = NavigationFailureReason.NO_PATH;
        for (int index = selectedCandidateIndex; index < activeDestination.candidates().size(); index++) {
            BlockPos candidate = activeDestination.candidates().get(index);
            Optional<NavigationFailureReason> candidateFailure = travelCandidateFailureReason(candidate, activeDestination);
            if (candidateFailure.isPresent()) {
                NavigationFailureReason reason = candidateFailure.orElseThrow();
                pathSelectionFailure = reason;
                lastPathReplacementReason = reason.reasonCode();
                continue;
            }
            Path path = getNavigation().createPath(candidate, pathSearchAccuracy(activeDestination));
            if (path == null) {
                pathSelectionFailure = NavigationFailureReason.NO_PATH;
                lastPathReplacementReason = "path_creation_failed";
                continue;
            }
            if (path.getNodeCount() == 0) {
                pathSelectionFailure = NavigationFailureReason.NO_PATH;
                lastPathReplacementReason = "empty_path_rejected";
                continue;
            }
            if (!usablePathEndpoint(path, candidate, activeDestination)) {
                pathSelectionFailure = NavigationFailureReason.NO_PATH;
                lastPathReplacementReason = "partial_path_rejected";
                continue;
            }
            return Optional.of(new PathSelection(path, candidate, index));
        }
        lastFailureReason = pathSelectionFailure;
        if (!fallbackTarget.equals(currentCandidate().orElse(fallbackTarget))) {
            lastPathReplacementReason = "path_creation_failed";
        }
        return Optional.empty();
    }

    private boolean usablePathEndpoint(Path path, BlockPos target, TravelDestination destination) {
        if (path.getEndNode() == null) {
            return false;
        }
        BlockPos endpoint = path.getEndNode().asBlockPos();
        return validStandingLocation(endpoint)
                && Math.abs(endpoint.getY() - target.getY()) <= destination.verticalTolerance()
                && blockHorizontalDistanceSquared(endpoint, target)
                <= destination.horizontalTolerance() * destination.horizontalTolerance();
    }

    private static int pathSearchAccuracy(TravelDestination destination) {
        return Math.max(1, (int)Math.floor(destination.horizontalTolerance()));
    }

    private void skipInvalidCurrentCandidates() {
        if (activeDestination.type() != DestinationType.WORKSTATION) {
            return;
        }
        while (selectedCandidateIndex < activeDestination.candidates().size()) {
            Optional<NavigationFailureReason> candidateFailure = travelCandidateFailureReason(
                    activeDestination.candidates().get(selectedCandidateIndex),
                    activeDestination
            );
            if (candidateFailure.isEmpty()) {
                break;
            }
            lastFailureReason = candidateFailure.orElseThrow();
            selectedCandidateIndex++;
            retryCount = 0;
            recoveryPhase = selectedCandidateIndex >= activeDestination.candidates().size() - 1
                    ? NavigationRecoveryPhase.TRY_FINAL_FALLBACK
                    : NavigationRecoveryPhase.TRY_NEXT_CANDIDATE;
        }
        if (selectedCandidateIndex >= activeDestination.candidates().size()) {
            lastFailureReason = terminalFailureReason(lastFailureReason);
            recoveryPhase = NavigationRecoveryPhase.SAFE_FAILURE;
            handleSafeFailure();
        }
    }

    private void monitorProgress(BlockPos target) {
        long gameTime = level().getGameTime();
        Path currentPath = getNavigation().getPath();
        if (currentPath == null || currentPath.getNodeCount() == 0 || currentPath.isDone()) {
            pathAvailable = false;
            advanceRecovery(NavigationFailureReason.NO_PATH);
            return;
        }
        if (activePath == null || !currentPath.sameAs(activePath)) {
            activePath = currentPath;
            pathReplacementCount++;
            lastPathReplacementReason = "navigation_path_replaced";
            updatePathProgressTelemetry(currentPath, target, gameTime, true);
            return;
        }
        if (gameTime - lastProgressCheckTick < PROGRESS_CHECK_INTERVAL_TICKS) {
            return;
        }
        lastProgressCheckTick = gameTime;
        if (updatePathProgressTelemetry(currentPath, target, gameTime, false)) {
            lastProgressTick = gameTime;
            return;
        }
        if (gameTime - lastNodeProgressTick >= STALL_INTERVAL_TICKS || getNavigation().isStuck()) {
            advanceRecovery(NavigationFailureReason.PROGRESS_STALLED);
        }
    }

    private void resetPathTelemetry() {
        clearActivePathTelemetry();
        distanceToFinalDestination = -1.0D;
        pathReplacementCount = 0;
        lastPathReplacementReason = "none";
    }

    private void clearActivePathTelemetry() {
        activePath = null;
        activePathNodeIndex = -1;
        activePathNodeCount = 0;
        bestNextNodeDistanceSquared = Double.MAX_VALUE;
        distanceToNextNode = -1.0D;
    }

    private boolean updatePathProgressTelemetry(Path path, BlockPos target, long gameTime, boolean forceProgress) {
        if (path == null || path.getNodeCount() == 0 || path.isDone()) {
            activePathNodeIndex = -1;
            activePathNodeCount = path == null ? 0 : path.getNodeCount();
            distanceToNextNode = -1.0D;
            distanceToFinalDestination = Math.sqrt(horizontalDistanceSquared(target));
            return false;
        }
        int previousNodeIndex = activePathNodeIndex;
        activePathNodeIndex = path.getNextNodeIndex();
        activePathNodeCount = path.getNodeCount();
        double nextNodeDistanceSquared = distanceToNextPathNodeSquared(path);
        distanceToNextNode = nextNodeDistanceSquared < 0.0D ? -1.0D : Math.sqrt(nextNodeDistanceSquared);
        distanceToFinalDestination = Math.sqrt(horizontalDistanceSquared(target));

        boolean advancedNode = previousNodeIndex >= 0 && activePathNodeIndex > previousNodeIndex;
        boolean movedTowardNextNode = bestNextNodeDistanceSquared - nextNodeDistanceSquared
                >= MIN_NEXT_NODE_PROGRESS_DISTANCE_SQUARED;
        if (forceProgress || advancedNode || movedTowardNextNode) {
            bestNextNodeDistanceSquared = nextNodeDistanceSquared;
            lastNodeProgressTick = gameTime;
            lastProgressCheckTick = gameTime;
            return true;
        }
        if (activePathNodeIndex != previousNodeIndex) {
            bestNextNodeDistanceSquared = nextNodeDistanceSquared;
        }
        return false;
    }

    private double distanceToNextPathNodeSquared(Path path) {
        int nodeIndex = path.getNextNodeIndex();
        if (nodeIndex < 0 || nodeIndex >= path.getNodeCount()) {
            return -1.0D;
        }
        Vec3 nextNode = path.getNextEntityPos(this);
        double dx = nextNode.x - getX();
        double dy = nextNode.y - getY();
        double dz = nextNode.z - getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void advanceRecovery(NavigationFailureReason reason) {
        lastFailureReason = reason;
        pathAvailable = false;
        clearActivePathTelemetry();
        getNavigation().stop();
        long gameTime = level().getGameTime();
        if (retryCount < MAX_RETRIES_PER_CANDIDATE) {
            retryCount++;
            recoveryPhase = selectedCandidateIndex == 0
                    ? NavigationRecoveryPhase.REPATH_PRIMARY
                    : NavigationRecoveryPhase.TRY_NEXT_CANDIDATE;
            applyNavigationState(EmployeeNavigationState.RECOVERING_PATH);
            nextPathAttemptTick = gameTime + PATH_RESTART_COOLDOWN_TICKS;
            return;
        }
        if (selectedCandidateIndex + 1 < activeDestination.candidates().size()) {
            selectedCandidateIndex++;
            retryCount = 0;
            recoveryPhase = selectedCandidateIndex == activeDestination.candidates().size() - 1
                    ? NavigationRecoveryPhase.TRY_FINAL_FALLBACK
                    : NavigationRecoveryPhase.TRY_NEXT_CANDIDATE;
            applyNavigationState(EmployeeNavigationState.TRYING_ALTERNATE_APPROACH);
            nextPathAttemptTick = gameTime + PATH_RESTART_COOLDOWN_TICKS;
            return;
        }
        failAllCandidates(reason);
    }

    private void failAllCandidates(NavigationFailureReason reason) {
        lastFailureReason = terminalFailureReason(reason);
        pathAvailable = false;
        clearActivePathTelemetry();
        recoveryPhase = NavigationRecoveryPhase.SAFE_FAILURE;
        getNavigation().stop();
        applyNavigationState(EmployeeNavigationState.NAVIGATION_FAILED);
        handleSafeFailure();
    }

    private void handleArrival() {
        pathAvailable = false;
        clearActivePathTelemetry();
        recoveryPhase = NavigationRecoveryPhase.ARRIVED;
        getNavigation().stop();
        if (activeDestination.type() == DestinationType.WORKSTATION) {
            lastFailureReason = NavigationFailureReason.NONE;
            applyNavigationState(EmployeeNavigationState.WAITING_AT_WORKSTATION);
            faceLookTarget(EmployeeNavigationState.WAITING_AT_WORKSTATION);
            return;
        }
        applyNavigationState(EmployeeNavigationState.IDLE);
    }

    private void handleSafeFailure() {
        if (finalFailureReported) {
            return;
        }
        finalFailureReported = true;
        if (activeDestination.type() == DestinationType.WORKSTATION) {
            EmployeeService.INSTANCE.handleNavigationFailure(this, lastFailureReason.reasonCode());
            validationTicks = 0;
            return;
        }
        if (lastFailureReason != NavigationFailureReason.DESTINATION_OUT_OF_RANGE
                && lastFailureReason != NavigationFailureReason.DESTINATION_UNAVAILABLE) {
            lastFailureReason = NavigationFailureReason.DEPARTMENT_UNREACHABLE;
        }
        departmentFailureRetryTick = level().getGameTime() + DEPARTMENT_FAILURE_RETRY_TICKS;
    }

    private boolean arrivedAt(TravelDestination destination) {
        Optional<BlockPos> candidate = currentCandidate();
        if (candidate.isEmpty()) {
            return false;
        }
        BlockPos selected = candidate.orElseThrow();
        return validTravelCandidate(selected, destination)
                && Math.abs(getY() - selected.getY()) <= destination.verticalTolerance()
                && horizontalDistanceSquared(selected) <= destination.horizontalTolerance() * destination.horizontalTolerance()
                && validStandingLocation(blockPosition());
    }

    private boolean validTravelCandidate(BlockPos candidate, TravelDestination destination) {
        return travelCandidateFailureReason(candidate, destination).isEmpty();
    }

    private Optional<NavigationFailureReason> travelCandidateFailureReason(
            BlockPos candidate,
            TravelDestination destination
    ) {
        if (!destinationWithinRange(destination)) {
            return Optional.of(NavigationFailureReason.DESTINATION_OUT_OF_RANGE);
        }
        if (destination.facingTarget().isPresent() && destination.facingTarget().orElseThrow().equals(candidate)) {
            return Optional.of(NavigationFailureReason.DESTINATION_INVALID);
        }
        if (!level().getWorldBorder().isWithinBounds(destination.rangeTarget())
                || !level().isLoaded(destination.rangeTarget())) {
            return Optional.of(NavigationFailureReason.DESTINATION_UNAVAILABLE);
        }
        if (!level().getWorldBorder().isWithinBounds(candidate) || !level().isLoaded(candidate)) {
            return Optional.of(NavigationFailureReason.DESTINATION_UNAVAILABLE);
        }
        if (Math.abs(candidate.getY() - blockPosition().getY()) > 4) {
            return Optional.of(NavigationFailureReason.DESTINATION_INVALID);
        }
        return validStandingLocation(candidate)
                ? Optional.empty()
                : Optional.of(NavigationFailureReason.CANDIDATE_BLOCKED);
    }

    private boolean destinationWithinRange(TravelDestination destination) {
        return horizontalDistanceSquared(destination.rangeTarget()) <= MAX_NAVIGATION_RANGE_SQUARED;
    }

    private double navigationSearchRangeBlocks() {
        return getAttributeValue(Attributes.FOLLOW_RANGE);
    }

    private static NavigationFailureReason terminalFailureReason(NavigationFailureReason reason) {
        return switch (reason) {
            case DESTINATION_OUT_OF_RANGE, DESTINATION_UNAVAILABLE -> reason;
            default -> NavigationFailureReason.ALL_CANDIDATES_EXHAUSTED;
        };
    }

    private NavigationFailureReason candidateExhaustionReason() {
        return switch (lastFailureReason) {
            case DESTINATION_OUT_OF_RANGE, DESTINATION_UNAVAILABLE -> lastFailureReason;
            default -> NavigationFailureReason.ALL_CANDIDATES_EXHAUSTED;
        };
    }

    private boolean validStandingLocation(BlockPos pos) {
        if (!level().getWorldBorder().isWithinBounds(pos) || !level().isLoaded(pos)) {
            return false;
        }
        BlockState feet = level().getBlockState(pos);
        BlockState head = level().getBlockState(pos.above());
        if (!feet.getCollisionShape(level(), pos).isEmpty()
                || !head.getCollisionShape(level(), pos.above()).isEmpty()) {
            return false;
        }
        BlockPos supportPos = pos.below();
        BlockState support = level().getBlockState(supportPos);
        return support.isFaceSturdy(level(), supportPos, Direction.UP)
                || !support.getCollisionShape(level(), supportPos).isEmpty();
    }

    private boolean isActiveTravelState(EmployeeNavigationState state) {
        return state == EmployeeNavigationState.WALKING_TO_DEPARTMENT
                || state == EmployeeNavigationState.RETURNING_TO_DEPARTMENT
                || state == EmployeeNavigationState.RETURNING_TO_ANCHOR
                || state == EmployeeNavigationState.WALKING_TO_WORKSTATION
                || state == EmployeeNavigationState.RECOVERING_PATH
                || state == EmployeeNavigationState.TRYING_ALTERNATE_APPROACH;
    }

    private boolean idleMayUseNavigation() {
        EmployeeNavigationState state = navigationStateOrDefault();
        return state == EmployeeNavigationState.IDLE || state == EmployeeNavigationState.PRESENT_IN_DEPARTMENT;
    }

    private double horizontalDistanceSquared(BlockPos pos) {
        double dx = pos.getX() + 0.5D - getX();
        double dz = pos.getZ() + 0.5D - getZ();
        return dx * dx + dz * dz;
    }

    private static double blockHorizontalDistanceSquared(BlockPos first, BlockPos second) {
        double dx = first.getX() + 0.5D - (second.getX() + 0.5D);
        double dz = first.getZ() + 0.5D - (second.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private static final class EmployeeBoundedIdleGoal extends Goal {
        private final EmployeeEntity employee;
        private double targetX;
        private double targetY;
        private double targetZ;

        private EmployeeBoundedIdleGoal(EmployeeEntity employee) {
            this.employee = employee;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!employee.idleMayUseNavigation()) {
                return false;
            }
            long gameTime = employee.level().getGameTime();
            if (gameTime < employee.nextIdleMovementTick) {
                return false;
            }
            Optional<BlockPos> target = employee.nextIdleTarget();
            if (target.isEmpty()) {
                employee.nextIdleMovementTick = gameTime + IDLE_PAUSE_MIN_TICKS;
                return false;
            }
            BlockPos pos = target.orElseThrow();
            targetX = pos.getX() + 0.5D;
            targetY = pos.getY();
            targetZ = pos.getZ() + 0.5D;
            employee.nextIdleMovementTick = gameTime + IDLE_PAUSE_MIN_TICKS
                    + Math.floorMod(employee.employeeIdValue().hashCode() + employee.idleSequence, IDLE_PAUSE_SPAN_TICKS);
            return true;
        }

        @Override
        public void start() {
            employee.getNavigation().moveTo(targetX, targetY, targetZ, IDLE_SPEED);
        }
    }

    private Optional<BlockPos> nextIdleTarget() {
        int radius = Math.max(1, anchorRadius);
        int base = Math.floorMod(Objects.hash(employeeIdValue(), idleSequence++), radius * radius * 8 + 1);
        for (int attempt = 0; attempt < MAX_IDLE_CANDIDATES; attempt++) {
            int value = base + attempt * 7;
            int span = radius * 2 + 1;
            int xOffset = Math.floorMod(value, span) - radius;
            int zOffset = Math.floorMod(value / span, span) - radius;
            BlockPos candidate = anchorPos.offset(xOffset, 0, zOffset);
            if (candidate.distManhattan(anchorPos) <= radius
                    && validStandingLocation(candidate)
                    && !occupiedByEmployee(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean occupiedByEmployee(BlockPos candidate) {
        return level().getEntitiesOfClass(EmployeeEntity.class, getBoundingBox().inflate(anchorRadius + 2.0D))
                .stream()
                .filter(other -> other != this)
                .anyMatch(other -> other.blockPosition().equals(candidate));
    }

    public record NavigationDiagnostics(
            String destinationType,
            String destinationIdentity,
            BlockPos currentDestination,
            int candidateIndex,
            int candidateCount,
            boolean pathAvailable,
            double distanceToTarget,
            double distanceToFinalDestination,
            double distanceToNextNode,
            int activePathNodeIndex,
            int activePathNodeCount,
            long ticksSinceNodeProgress,
            long ticksSinceMeaningfulProgress,
            int pathReplacementCount,
            String lastPathReplacementReason,
            int retryCount,
            double configuredMaximumNavigationRange,
            double pathSearchRange,
            boolean destinationWithinRange,
            float visitedNodeMultiplier,
            String recoveryPhase,
            String lastFailureReason
    ) {
        public NavigationDiagnostics {
            destinationType = Objects.requireNonNull(destinationType, "destinationType");
            destinationIdentity = Objects.requireNonNull(destinationIdentity, "destinationIdentity");
            currentDestination = currentDestination == null ? null : currentDestination.immutable();
            lastPathReplacementReason = Objects.requireNonNull(lastPathReplacementReason, "lastPathReplacementReason");
            recoveryPhase = Objects.requireNonNull(recoveryPhase, "recoveryPhase");
            lastFailureReason = Objects.requireNonNull(lastFailureReason, "lastFailureReason");
        }
    }

    public record EmployeeOperationDiagnostics(
            String workstation,
            String executionId,
            String reservation,
            String recipe,
            String state,
            String failure
    ) {
        public EmployeeOperationDiagnostics {
            workstation = Objects.requireNonNull(workstation, "workstation");
            executionId = Objects.requireNonNull(executionId, "executionId");
            reservation = Objects.requireNonNull(reservation, "reservation");
            recipe = Objects.requireNonNull(recipe, "recipe");
            state = Objects.requireNonNull(state, "state");
            failure = Objects.requireNonNull(failure, "failure");
        }
    }

    private record TravelDestination(
            DestinationType type,
            String identity,
            String displayType,
            List<BlockPos> candidates,
            BlockPos rangeTarget,
            double horizontalTolerance,
            double verticalTolerance,
            Optional<BlockPos> facingTarget
    ) {
        private TravelDestination {
            type = Objects.requireNonNull(type, "type");
            identity = Objects.requireNonNull(identity, "identity");
            displayType = Objects.requireNonNull(displayType, "displayType");
            candidates = Objects.requireNonNull(candidates, "candidates").stream()
                    .map(BlockPos::immutable)
                    .toList();
            rangeTarget = Objects.requireNonNull(rangeTarget, "rangeTarget").immutable();
            facingTarget = Objects.requireNonNull(facingTarget, "facingTarget")
                    .map(BlockPos::immutable);
        }

        static TravelDestination none() {
            return new TravelDestination(
                    DestinationType.NONE,
                    "",
                    "",
                    List.of(),
                    BlockPos.ZERO,
                    0.0D,
                    0.0D,
                    Optional.empty()
            );
        }

        boolean sameIdentityAndCandidates(TravelDestination other) {
            return type == other.type
                    && identity.equals(other.identity)
                    && candidates.equals(other.candidates)
                    && rangeTarget.equals(other.rangeTarget);
        }
    }

    private record PathSelection(Path path, BlockPos target, int candidateIndex) {
        private PathSelection {
            path = Objects.requireNonNull(path, "path");
            target = Objects.requireNonNull(target, "target").immutable();
        }
    }

    private enum DestinationType {
        NONE("none"),
        DEPARTMENT("department"),
        WORKSTATION("workstation");

        private final String serializedName;

        DestinationType(String serializedName) {
            this.serializedName = serializedName;
        }

        String serializedName() {
            return serializedName;
        }
    }

    private enum NavigationRecoveryPhase {
        IDLE("idle"),
        INITIAL_PATH("initial_path"),
        REPATH_PRIMARY("repath_primary"),
        TRY_NEXT_CANDIDATE("try_next_candidate"),
        TRY_FINAL_FALLBACK("try_final_fallback"),
        SAFE_FAILURE("safe_failure"),
        ARRIVED("arrived");

        private final String serializedName;

        NavigationRecoveryPhase(String serializedName) {
            this.serializedName = serializedName;
        }

        String serializedName() {
            return serializedName;
        }
    }

    public enum NavigationFailureReason {
        NONE("none"),
        NO_PATH("no_path"),
        PROGRESS_STALLED("progress_stalled"),
        CANDIDATE_BLOCKED("candidate_blocked"),
        DESTINATION_INVALID("destination_invalid"),
        DESTINATION_OUT_OF_RANGE("destination_out_of_range"),
        DESTINATION_UNAVAILABLE("destination_unavailable"),
        WORKSTATION_REMOVED("workstation_removed"),
        RESERVATION_INVALID("reservation_invalid"),
        ALL_CANDIDATES_EXHAUSTED("all_candidates_exhausted"),
        DEPARTMENT_UNREACHABLE("department_unreachable");

        private final String reasonCode;

        NavigationFailureReason(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        public String reasonCode() {
            return reasonCode;
        }
    }
}
