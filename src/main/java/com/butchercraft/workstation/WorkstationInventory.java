package com.butchercraft.workstation;

import com.butchercraft.product.integration.ProductStackAdapter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class WorkstationInventory extends ItemStackHandler {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private final int inputSlotCount;
    private final int outputSlotCount;
    private final int firstInputSlot;
    private final int firstOutputSlot;
    private final int totalSlotCount;
    private final Runnable changeListener;
    private BooleanSupplier inputLocked = () -> false;
    private BooleanSupplier outputExtractionAllowed = () -> false;
    private BiPredicate<Integer, ItemStack> inputValidator =
            (slot, stack) -> ProductStackAdapter.readProductData(stack).succeeded();
    private boolean suppressChangeListener;

    public WorkstationInventory(Runnable changeListener) {
        this(1, 1, changeListener);
    }

    public WorkstationInventory(WorkstationCapability capability, Runnable changeListener) {
        this(capability.inputSlots(), capability.outputSlots(), changeListener);
    }

    public WorkstationInventory(int inputSlotCount, int outputSlotCount, Runnable changeListener) {
        super(totalSlotCount(inputSlotCount, outputSlotCount));
        this.inputSlotCount = inputSlotCount;
        this.outputSlotCount = outputSlotCount;
        this.firstInputSlot = 0;
        this.firstOutputSlot = inputSlotCount;
        this.totalSlotCount = inputSlotCount + outputSlotCount;
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    private static int totalSlotCount(int inputSlotCount, int outputSlotCount) {
        if (inputSlotCount <= 0) {
            throw new IllegalArgumentException("Workstation inventories must support at least one input slot");
        }
        if (outputSlotCount <= 0) {
            throw new IllegalArgumentException("Workstation inventories must support at least one output slot");
        }
        return inputSlotCount + outputSlotCount;
    }

    public int inputSlotCount() {
        return inputSlotCount;
    }

    public int outputSlotCount() {
        return outputSlotCount;
    }

    public int firstInputSlot() {
        return firstInputSlot;
    }

    public int firstOutputSlot() {
        return firstOutputSlot;
    }

    public int totalSlotCount() {
        return totalSlotCount;
    }

    public List<Integer> outputSlotRange() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = firstOutputSlot; slot < totalSlotCount; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    public void setInputLocked(BooleanSupplier inputLocked) {
        this.inputLocked = Objects.requireNonNull(inputLocked, "inputLocked");
    }

    public void setOutputExtractionAllowed(BooleanSupplier outputExtractionAllowed) {
        this.outputExtractionAllowed = Objects.requireNonNull(outputExtractionAllowed, "outputExtractionAllowed");
    }

    public void setInputValidator(Predicate<ItemStack> inputValidator) {
        Objects.requireNonNull(inputValidator, "inputValidator");
        this.inputValidator = (slot, stack) -> inputValidator.test(stack);
    }

    public void setInputSlotValidator(BiPredicate<Integer, ItemStack> inputValidator) {
        this.inputValidator = Objects.requireNonNull(inputValidator, "inputValidator");
    }

    public boolean isInputLocked() {
        return inputLocked.getAsBoolean();
    }

    public boolean isOutputExtractionAllowed() {
        return outputExtractionAllowed.getAsBoolean();
    }

    public ItemStack input() {
        return getStackInSlot(firstInputSlot);
    }

    public List<ItemStack> inputs() {
        List<ItemStack> inputs = new ArrayList<>();
        for (int slot = firstInputSlot; slot < firstOutputSlot; slot++) {
            inputs.add(getStackInSlot(slot));
        }
        return List.copyOf(inputs);
    }

    public ItemStack output() {
        return getStackInSlot(firstOutputSlot);
    }

    public List<ItemStack> outputs() {
        List<ItemStack> outputs = new ArrayList<>();
        for (int slot = firstOutputSlot; slot < totalSlotCount; slot++) {
            outputs.add(getStackInSlot(slot));
        }
        return List.copyOf(outputs);
    }

    public boolean outputsEmpty() {
        for (int slot = firstOutputSlot; slot < totalSlotCount; slot++) {
            if (!getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void setInputInternal(ItemStack stack) {
        setStackMuted(firstInputSlot, stack);
    }

    public void setInputInternal(int inputIndex, ItemStack stack) {
        if (inputIndex < 0 || inputIndex >= inputSlotCount) {
            throw new IllegalArgumentException("Input index is outside workstation inventory range");
        }
        setStackMuted(firstInputSlot + inputIndex, stack);
    }

    public void setOutputInternal(ItemStack stack) {
        setStackMuted(firstOutputSlot, stack);
    }

    public void setOutputsInternal(List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
        if (copiedStacks.size() > outputSlotCount) {
            throw new IllegalArgumentException("Too many output stacks for workstation inventory");
        }
        suppressChangeListener = true;
        try {
            for (int slot = firstOutputSlot; slot < totalSlotCount; slot++) {
                int outputIndex = slot - firstOutputSlot;
                setStackInSlot(slot, outputIndex < copiedStacks.size() ? copiedStacks.get(outputIndex) : ItemStack.EMPTY);
            }
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }

    public void setInputsInternal(List<ItemStack> stacks) {
        List<ItemStack> copiedStacks = List.copyOf(Objects.requireNonNull(stacks, "stacks"));
        if (copiedStacks.size() != inputSlotCount) {
            throw new IllegalArgumentException("Input snapshot size must match workstation input slot count");
        }
        suppressChangeListener = true;
        try {
            for (int inputIndex = 0; inputIndex < inputSlotCount; inputIndex++) {
                setStackInSlot(firstInputSlot + inputIndex, copiedStacks.get(inputIndex));
            }
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }

    public void clearInputInternal() {
        setStackMuted(firstInputSlot, ItemStack.EMPTY);
    }

    public void clearInputsInternal() {
        suppressChangeListener = true;
        try {
            for (int slot = firstInputSlot; slot < firstOutputSlot; slot++) {
                setStackInSlot(slot, ItemStack.EMPTY);
            }
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }

    public void clearInputSlotsInternal(List<Integer> slots) {
        List<Integer> copiedSlots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        suppressChangeListener = true;
        try {
            for (int slot : copiedSlots) {
                if (!isInputSlot(slot)) {
                    throw new IllegalArgumentException("Only input slots can be cleared by an input commit plan");
                }
                setStackInSlot(slot, ItemStack.EMPTY);
            }
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }

    public void clearOutputsInternal() {
        setOutputsInternal(List.of());
    }

    public void clearAllInternal() {
        suppressChangeListener = true;
        try {
            for (int slot = 0; slot < totalSlotCount; slot++) {
                setStackInSlot(slot, ItemStack.EMPTY);
            }
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (isOutputSlot(slot)) {
            return false;
        }
        if (!isInputSlot(slot) || stack.isEmpty()) {
            return false;
        }
        return inputValidator.test(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (isOutputSlot(slot)) {
            return stack;
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (isInputSlot(slot) && isInputLocked()) {
            return ItemStack.EMPTY;
        }
        if (isOutputSlot(slot) && !isOutputExtractionAllowed()) {
            return ItemStack.EMPTY;
        }
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!suppressChangeListener) {
            changeListener.run();
        }
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        suppressChangeListener = true;
        try {
            for (int slot = 0; slot < totalSlotCount; slot++) {
                stacks.set(slot, ItemStack.EMPTY);
            }
            ListTag tagList = nbt.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < tagList.size(); i++) {
                CompoundTag itemTags = tagList.getCompound(i);
                int slot = itemTags.getInt("Slot");
                if (slot >= 0 && slot < totalSlotCount) {
                    ItemStack.parse(provider, itemTags).ifPresent(stack -> stacks.set(slot, stack));
                }
            }
        } finally {
            suppressChangeListener = false;
        }
        onLoad();
    }

    private void setStackMuted(int slot, ItemStack stack) {
        suppressChangeListener = true;
        try {
            setStackInSlot(slot, stack);
        } finally {
            suppressChangeListener = false;
        }
        changeListener.run();
    }

    public boolean isInputSlot(int slot) {
        return slot >= firstInputSlot && slot < firstOutputSlot;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= firstOutputSlot && slot < totalSlotCount;
    }
}
