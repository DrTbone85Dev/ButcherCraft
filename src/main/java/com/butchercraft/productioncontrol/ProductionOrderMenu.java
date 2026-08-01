package com.butchercraft.productioncontrol;

import com.butchercraft.registration.ModItems;
import com.butchercraft.registration.ModMenuTypes;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.world.production.ProductionChainStepStatus;
import com.butchercraft.world.production.ProductionDeadlineStatus;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionWorkstationChainStatus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

public final class ProductionOrderMenu extends AbstractContainerMenu {
    private static final int HAS_RUN = 0;
    private static final int STALE_REFERENCE = 1;
    private static final int RUN_STATUS = 2;
    private static final int CHAIN_STATUS = 3;
    private static final int GRINDER_STEP_STATUS = 4;
    private static final int PATTY_FORMER_STEP_STATUS = 5;
    private static final int GRINDER_ASSIGNED = 6;
    private static final int PATTY_FORMER_ASSIGNED = 7;
    private static final int GRINDER_MISSING = 8;
    private static final int PATTY_FORMER_MISSING = 9;
    private static final int GRINDER_PROGRESS = 10;
    private static final int PATTY_FORMER_PROGRESS = 11;
    private static final int GRINDER_WORKSTATION_STATE = 12;
    private static final int PATTY_FORMER_WORKSTATION_STATE = 13;
    private static final int CAN_CANCEL = 14;
    private static final int FAILURE_CODE = 15;
    private static final int NEXT_ACTION = 16;
    private static final int BUSINESS_OBSERVED = 17;
    private static final int PLANT_OPEN = 18;
    private static final int BUSINESS_DAY = 19;
    private static final int BUSINESS_HOUR = 20;
    private static final int BUSINESS_MINUTE = 21;
    private static final int ACTIVE_SHIFT = 22;
    private static final int NEXT_SHIFT = 23;
    private static final int HAS_DEADLINE = 24;
    private static final int DEADLINE_STATUS = 25;
    private static final int DEADLINE_DAY = 26;
    private static final int DEADLINE_HOUR = 27;
    private static final int DEADLINE_MINUTE = 28;
    private static final int DEADLINE_DELTA_MINUTES = 29;
    private static final int DATA_COUNT = 30;

    private final ContainerData data;
    private final ServerPlayer serverPlayer;
    private final InteractionHand hand;

    public ProductionOrderMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf ignoredExtraData) {
        this(containerId, playerInventory, new SimpleContainerData(DATA_COUNT), null, null);
    }

    public ProductionOrderMenu(int containerId, Inventory playerInventory, ServerPlayer serverPlayer, InteractionHand hand) {
        this(containerId, playerInventory, new ServerData(serverPlayer, hand), serverPlayer, hand);
    }

    private ProductionOrderMenu(
            int containerId,
            Inventory playerInventory,
            ContainerData data,
            ServerPlayer serverPlayer,
            InteractionHand hand
    ) {
        super(ModMenuTypes.PRODUCTION_ORDER.get(), containerId);
        Objects.requireNonNull(playerInventory, "playerInventory");
        this.data = Objects.requireNonNull(data, "data");
        this.serverPlayer = serverPlayer;
        this.hand = hand;
        addDataSlots(data);
    }

    public boolean hasRun() {
        return flag(HAS_RUN);
    }

    public boolean staleReference() {
        return flag(STALE_REFERENCE);
    }

    public ProductionRunStatus runStatus() {
        return enumValue(ProductionRunStatus.values(), data.get(RUN_STATUS), ProductionRunStatus.PLANNED);
    }

    public ProductionWorkstationChainStatus chainStatus() {
        return enumValue(
                ProductionWorkstationChainStatus.values(),
                data.get(CHAIN_STATUS),
                ProductionWorkstationChainStatus.UNKNOWN_OUTCOME
        );
    }

    public ProductionChainStepStatus grinderStepStatus() {
        return enumValue(
                ProductionChainStepStatus.values(),
                data.get(GRINDER_STEP_STATUS),
                ProductionChainStepStatus.UNKNOWN_OUTCOME
        );
    }

    public ProductionChainStepStatus pattyFormerStepStatus() {
        return enumValue(
                ProductionChainStepStatus.values(),
                data.get(PATTY_FORMER_STEP_STATUS),
                ProductionChainStepStatus.UNKNOWN_OUTCOME
        );
    }

    public boolean grinderAssigned() {
        return flag(GRINDER_ASSIGNED);
    }

    public boolean pattyFormerAssigned() {
        return flag(PATTY_FORMER_ASSIGNED);
    }

    public boolean grinderMissing() {
        return flag(GRINDER_MISSING);
    }

    public boolean pattyFormerMissing() {
        return flag(PATTY_FORMER_MISSING);
    }

    public int grinderProgressPercent() {
        return data.get(GRINDER_PROGRESS);
    }

    public int pattyFormerProgressPercent() {
        return data.get(PATTY_FORMER_PROGRESS);
    }

    public WorkstationState grinderWorkstationState() {
        return enumValue(WorkstationState.values(), data.get(GRINDER_WORKSTATION_STATE), WorkstationState.ERROR);
    }

    public WorkstationState pattyFormerWorkstationState() {
        return enumValue(WorkstationState.values(), data.get(PATTY_FORMER_WORKSTATION_STATE), WorkstationState.ERROR);
    }

    public boolean canCancel() {
        return flag(CAN_CANCEL);
    }

    public ProductionFailureCode failureCode() {
        int value = data.get(FAILURE_CODE);
        if (value < 0 || value >= ProductionFailureCode.values().length) {
            return null;
        }
        return ProductionFailureCode.values()[value];
    }

    public ProductionOrderNextAction nextAction() {
        return enumValue(ProductionOrderNextAction.values(), data.get(NEXT_ACTION), ProductionOrderNextAction.UNKNOWN_OUTCOME);
    }

    public Component nextActionComponent() {
        return Component.translatable(nextAction().translationKey());
    }

    public Component plantStatusComponent() {
        if (!flag(BUSINESS_OBSERVED)) {
            return Component.translatable("screen.butchercraft.production_order.plant.unavailable");
        }
        return Component.translatable(flag(PLANT_OPEN)
                ? "screen.butchercraft.production_order.plant.open"
                : "screen.butchercraft.production_order.plant.closed");
    }

    public Component businessTimeComponent() {
        if (!flag(BUSINESS_OBSERVED)) {
            return Component.translatable("screen.butchercraft.production_order.time.unavailable");
        }
        return Component.translatable(
                "screen.butchercraft.production_order.time",
                dayName(data.get(BUSINESS_DAY)),
                "%02d:%02d".formatted(data.get(BUSINESS_HOUR), data.get(BUSINESS_MINUTE))
        );
    }

    public Component shiftComponent() {
        if (!flag(BUSINESS_OBSERVED)) {
            return Component.translatable("screen.butchercraft.production_order.shift.unavailable");
        }
        int active = data.get(ACTIVE_SHIFT);
        if (active > 0) {
            return Component.translatable("screen.butchercraft.production_order.shift.active",
                    shiftName(active));
        }
        int next = data.get(NEXT_SHIFT);
        if (next > 0) {
            return Component.translatable("screen.butchercraft.production_order.shift.next",
                    shiftName(next));
        }
        return Component.translatable("screen.butchercraft.production_order.shift.none");
    }

    public Component deadlineComponent() {
        if (!flag(HAS_DEADLINE)) {
            return Component.translatable("screen.butchercraft.production_order.deadline.none");
        }
        return Component.translatable(
                "screen.butchercraft.production_order.deadline",
                dayName(data.get(DEADLINE_DAY)),
                "%02d:%02d".formatted(data.get(DEADLINE_HOUR), data.get(DEADLINE_MINUTE))
        );
    }

    public Component deadlineStatusComponent() {
        ProductionDeadlineStatus status = enumValue(
                ProductionDeadlineStatus.values(),
                data.get(DEADLINE_STATUS),
                ProductionDeadlineStatus.NO_DEADLINE
        );
        if (status == ProductionDeadlineStatus.NO_DEADLINE) {
            return Component.translatable("screen.butchercraft.production_order.deadline.status.no_deadline");
        }
        if (status == ProductionDeadlineStatus.UPCOMING) {
            return Component.translatable(
                    "screen.butchercraft.production_order.deadline.status.upcoming",
                    durationText(Math.max(0, data.get(DEADLINE_DELTA_MINUTES)))
            );
        }
        if (status == ProductionDeadlineStatus.OVERDUE) {
            return Component.translatable(
                    "screen.butchercraft.production_order.deadline.status.overdue",
                    durationText(Math.abs(data.get(DEADLINE_DELTA_MINUTES)))
            );
        }
        return Component.translatable("screen.butchercraft.production_order.deadline.status."
                + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    public Component chainStatusComponent() {
        if (staleReference()) {
            return Component.translatable("screen.butchercraft.production_order.status.stale");
        }
        ProductionFailureCode failure = failureCode();
        if (failure != null) {
            return Component.translatable("screen.butchercraft.production_order.status.failure");
        }
        return Component.translatable("screen.butchercraft.production_order.status." + chainStatus().name().toLowerCase(java.util.Locale.ROOT));
    }

    public Component grinderStatusComponent() {
        return stepStatusComponent("grinder", grinderAssigned(), grinderMissing(), grinderStepStatus(), grinderWorkstationState());
    }

    public Component pattyFormerStatusComponent() {
        return stepStatusComponent(
                "patty_former",
                pattyFormerAssigned(),
                pattyFormerMissing(),
                pattyFormerStepStatus(),
                pattyFormerWorkstationState()
        );
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == ProductionOrderControl.cancelButtonId() && player instanceof ServerPlayer && hand != null) {
            return ProductionOrderControl.cancel((ServerPlayer) player, player.getItemInHand(hand));
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) {
            return true;
        }
        if (hand == null) {
            return false;
        }
        return player.getItemInHand(hand).is(ModItems.PRODUCTION_ORDER.get());
    }

    private boolean flag(int index) {
        return data.get(index) != 0;
    }

    private static Component stepStatusComponent(
            String step,
            boolean assigned,
            boolean missing,
            ProductionChainStepStatus stepStatus,
            WorkstationState workstationState
    ) {
        if (!assigned) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".unassigned");
        }
        if (missing) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".missing");
        }
        if (stepStatus == ProductionChainStepStatus.COMPLETE || workstationState == WorkstationState.COMPLETE) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".complete");
        }
        if (stepStatus == ProductionChainStepStatus.RUNNING || workstationState == WorkstationState.PROCESSING) {
            return Component.translatable("screen.butchercraft.production_order.step." + step + ".running");
        }
        return Component.translatable("screen.butchercraft.production_order.step." + step + ".ready");
    }

    private static Component dayName(int ordinal) {
        return switch (ordinal) {
            case 0 -> Component.translatable("screen.butchercraft.production_order.day.monday");
            case 1 -> Component.translatable("screen.butchercraft.production_order.day.tuesday");
            case 2 -> Component.translatable("screen.butchercraft.production_order.day.wednesday");
            case 3 -> Component.translatable("screen.butchercraft.production_order.day.thursday");
            case 4 -> Component.translatable("screen.butchercraft.production_order.day.friday");
            case 5 -> Component.translatable("screen.butchercraft.production_order.day.saturday");
            case 6 -> Component.translatable("screen.butchercraft.production_order.day.sunday");
            default -> Component.translatable("screen.butchercraft.production_order.day.unknown");
        };
    }

    private static Component shiftName(int code) {
        return switch (code) {
            case 1 -> Component.translatable("screen.butchercraft.production_order.shift.day");
            case 2 -> Component.translatable("screen.butchercraft.production_order.shift.evening");
            default -> Component.translatable("screen.butchercraft.production_order.shift.configured");
        };
    }

    private static Component durationText(int minutes) {
        int days = minutes / 1_440;
        int hours = (minutes % 1_440) / 60;
        int remainder = minutes % 60;
        if (days > 0) {
            return Component.translatable("screen.butchercraft.production_order.duration.days_hours", days, hours);
        }
        if (hours > 0) {
            return Component.translatable("screen.butchercraft.production_order.duration.hours_minutes", hours, remainder);
        }
        return Component.translatable("screen.butchercraft.production_order.duration.minutes", remainder);
    }

    private static <T extends Enum<T>> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private static final class ServerData implements ContainerData {
        private final ServerPlayer player;
        private final InteractionHand hand;
        private long lastGameTime = Long.MIN_VALUE;
        private ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.empty();

        private ServerData(ServerPlayer player, InteractionHand hand) {
            this.player = Objects.requireNonNull(player, "player");
            this.hand = Objects.requireNonNull(hand, "hand");
        }

        @Override
        public int get(int index) {
            ProductionOrderStatusSnapshot current = snapshot();
            return switch (index) {
                case HAS_RUN -> current.hasRun() ? 1 : 0;
                case STALE_REFERENCE -> current.staleReference() ? 1 : 0;
                case RUN_STATUS -> current.runStatus().ordinal();
                case CHAIN_STATUS -> current.chainStatus().ordinal();
                case GRINDER_STEP_STATUS -> current.grinderStepStatus().ordinal();
                case PATTY_FORMER_STEP_STATUS -> current.pattyFormerStepStatus().ordinal();
                case GRINDER_ASSIGNED -> current.grinderAssigned() ? 1 : 0;
                case PATTY_FORMER_ASSIGNED -> current.pattyFormerAssigned() ? 1 : 0;
                case GRINDER_MISSING -> current.grinderMissing() ? 1 : 0;
                case PATTY_FORMER_MISSING -> current.pattyFormerMissing() ? 1 : 0;
                case GRINDER_PROGRESS -> current.grinderProgressPercent();
                case PATTY_FORMER_PROGRESS -> current.pattyFormerProgressPercent();
                case GRINDER_WORKSTATION_STATE -> current.grinderWorkstationState().ordinal();
                case PATTY_FORMER_WORKSTATION_STATE -> current.pattyFormerWorkstationState().ordinal();
                case CAN_CANCEL -> current.canCancel() ? 1 : 0;
                case FAILURE_CODE -> current.failureCode().map(Enum::ordinal).orElse(-1);
                case NEXT_ACTION -> current.nextAction().ordinal();
                case BUSINESS_OBSERVED -> current.businessObserved() ? 1 : 0;
                case PLANT_OPEN -> current.plantOpen() ? 1 : 0;
                case BUSINESS_DAY -> current.businessDayOfWeekOrdinal();
                case BUSINESS_HOUR -> current.businessHour();
                case BUSINESS_MINUTE -> current.businessMinute();
                case ACTIVE_SHIFT -> current.activeShiftDisplayCode();
                case NEXT_SHIFT -> current.nextShiftDisplayCode();
                case HAS_DEADLINE -> current.hasDeadline() ? 1 : 0;
                case DEADLINE_STATUS -> current.deadlineStatus().ordinal();
                case DEADLINE_DAY -> current.deadlineDayOfWeekOrdinal();
                case DEADLINE_HOUR -> current.deadlineHour();
                case DEADLINE_MINUTE -> current.deadlineMinute();
                case DEADLINE_DELTA_MINUTES -> current.deadlineDeltaMinutes();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }

        private ProductionOrderStatusSnapshot snapshot() {
            long gameTime = player.serverLevel().getGameTime();
            if (gameTime != lastGameTime) {
                lastGameTime = gameTime;
                snapshot = ProductionOrderControl.refreshStatus(
                        player,
                        ProductionOrderControl.dataOrDefault(player.getItemInHand(hand)),
                        true
                );
            }
            return snapshot;
        }
    }
}
