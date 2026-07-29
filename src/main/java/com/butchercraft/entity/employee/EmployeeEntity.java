package com.butchercraft.entity.employee;

import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.workforce.employee.EmployeeAnchor;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

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
    private static final int VALIDATION_INTERVAL_TICKS = 40;

    private BlockPos anchorPos = BlockPos.ZERO;
    private int anchorRadius = 8;
    private int validationTicks;

    public EmployeeEntity(EntityType<? extends EmployeeEntity> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
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
        if (anchorRadius > 0 && blockPosition().distManhattan(anchorPos) > anchorRadius + 4) {
            getNavigation().moveTo(anchorPos.getX() + 0.5D, anchorPos.getY(), anchorPos.getZ() + 0.5D, 1.0D);
        }
        updateNavigationStateFromPosition();
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

    private void updateNavigationStateFromPosition() {
        EmployeeNavigationState state;
        try {
            state = EmployeeNavigationState.fromSerializedName(navigationStateValue());
        } catch (IllegalArgumentException exception) {
            state = EmployeeNavigationState.OFF_SHIFT;
        }
        boolean inside = insideAnchorRadius();
        if (inside && state == EmployeeNavigationState.WALKING_TO_DEPARTMENT) {
            applyNavigationState(EmployeeNavigationState.PRESENT_IN_DEPARTMENT);
        } else if (inside && state == EmployeeNavigationState.PRESENT_IN_DEPARTMENT) {
            applyNavigationState(EmployeeNavigationState.IDLE);
        } else if (!inside && state == EmployeeNavigationState.IDLE) {
            applyNavigationState(EmployeeNavigationState.RETURNING_TO_ANCHOR);
        }
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
            if (employee.getRandom().nextInt(100) != 0) {
                return false;
            }
            int radius = Math.max(1, employee.anchorRadius);
            int x = employee.anchorPos.getX() + employee.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = employee.anchorPos.getZ() + employee.getRandom().nextInt(radius * 2 + 1) - radius;
            targetX = x + 0.5D;
            targetY = employee.anchorPos.getY();
            targetZ = z + 0.5D;
            return true;
        }

        @Override
        public void start() {
            employee.getNavigation().moveTo(targetX, targetY, targetZ, 0.7D);
        }
    }
}
