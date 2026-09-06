package net.unfamily.pattern_crafter.client.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.unfamily.pattern_crafter.block.entity.ImprovedPatternCrafterBlockEntity;
import net.unfamily.pattern_crafter.pattern.PatternData;

/**
 * Menu/Container for the Improved Pattern Crafter.
 *
 * Slot layout (all coordinates are +1 from frame border = item render position):
 *   - Input filter ghost slots:  9 x N rows (N from BE getMaxKeyInputs: 18 or 36) at (80, 47)
 *   - Output filter ghost slots: 3x7 = 21 slots at (13, 65)
 *   - Upgrade slots:             1x2 = 2 slots  at (49, 211)  [manual only]
 *   - Output slots:              3x3 = 9 slots  at (258, 171) [extract only]
 *   - Machine input inventory:   9x3 = 27 slots at (80, 105)  [hopper can insert]
 *   - Player inventory:          9x3 = 27 slots at (80, 171)
 *   - Player hotbar:             9x1 = 9 slots  at (80, 229)
 */
public class ImprovedPatternCrafterMenu extends AbstractContainerMenu {

    // Slot counts (input filter count is dynamic from BE)
    public static final int OUTPUT_FILTER_SLOTS = 21;   // 3x7
    public static final int UPGRADE_SLOTS = 2;          // 1x2
    public static final int OUTPUT_SLOTS = 9;           // 3x3
    public static final int INPUT_SLOTS = 27;           // 9x3 machine input
    public static final int PLAYER_INV_SLOTS = 27;      // 9x3
    public static final int PLAYER_HOTBAR_SLOTS = 9;    // 9x1

    // Slot index ranges (INPUT_FILTER_END is dynamic)
    public static final int INPUT_FILTER_START = 0;
    private final int inputFilterSlotCount; // total in BE (18 or 36)
    /** When > 18 we show 18 at a time (paginated); this is how many slots are in the menu. */
    private final int inputFilterMenuSlotCount;
    /** When paginated, view over BE handler so menu slots 0–17 show BE slots [offset..offset+17]. */
    private final InputFilterViewHandler inputFilterViewHandler;

    public int getInputFilterSlotCount() {
        return inputFilterSlotCount;
    }

    public int getInputFilterMenuSlotCount() {
        return inputFilterMenuSlotCount;
    }

    public InputFilterViewHandler getInputFilterViewHandler() {
        return inputFilterViewHandler;
    }

    /** Call from screen when page changes (paginated only). */
    public void setInputFilterViewOffset(int offset) {
        if (inputFilterViewHandler != null) inputFilterViewHandler.setOffset(offset);
    }

    public int getInputFilterEnd() {
        return INPUT_FILTER_START + inputFilterMenuSlotCount;
    }

    public int getOutputFilterStart() { return getInputFilterEnd(); }
    public int getOutputFilterEnd() { return getOutputFilterStart() + OUTPUT_FILTER_SLOTS; }
    public int getUpgradeStart() { return getOutputFilterEnd(); }
    public int getUpgradeEnd() { return getUpgradeStart() + UPGRADE_SLOTS; }
    public int getOutputStart() { return getUpgradeEnd(); }
    public int getOutputEnd() { return getOutputStart() + OUTPUT_SLOTS; }
    public int getInputStart() { return getOutputEnd(); }
    public int getInputEnd() { return getInputStart() + INPUT_SLOTS; }
    public int getPlayerInvStart() { return getInputEnd(); }
    public int getPlayerInvEnd() { return getPlayerInvStart() + PLAYER_INV_SLOTS; }
    public int getPlayerHotbarStart() { return getPlayerInvEnd(); }
    public int getPlayerHotbarEnd() { return getPlayerHotbarStart() + PLAYER_HOTBAR_SLOTS; }

    // ContainerData indices for synced data (energy etc. depend on inputFilterSlotCount)
    private static final int DATA_CURRENT_PATTERN = 0;
    private static final int DATA_TOTAL_PATTERNS = 1;
    private static final int DATA_GRID_START = 2;
    private static final int DATA_FILTER_LETTERS_START = 2 + PatternData.GRID_SIZE; // 11

    private int getDataEnergyStoredIndex() {
        return DATA_FILTER_LETTERS_START + inputFilterSlotCount;
    }

    private int getContainerDataSize() {
        return getDataRemainderRoutingModeIndex() + 1;
    }

    private int getDataMaxEnergyIndex() { return getDataEnergyStoredIndex() + 1; }
    private int getDataCraftingModeIndex() { return getDataEnergyStoredIndex() + 2; }
    private int getDataRedstoneModeIndex() { return getDataEnergyStoredIndex() + 3; }
    private int getDataCraftingTimerIndex() { return getDataEnergyStoredIndex() + 4; }
    private int getDataCraftingIntervalIndex() { return getDataEnergyStoredIndex() + 5; }
    private int getDataFilterPageIndex() { return getDataCraftingIntervalIndex() + 1; }
    private int getDataRecursiveOutputModeIndex() { return getDataFilterPageIndex() + 1; }
    private int getDataRemainderRoutingModeIndex() { return getDataFilterPageIndex() + 2; }

    private final ImprovedPatternCrafterBlockEntity blockEntity;
    private final ContainerData patternContainerData;

    /**
     * Client-side constructor - reads BlockPos from network buffer.
     * Uses SimpleContainerData so synced values from server are stored correctly.
     */
    public ImprovedPatternCrafterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                playerInventory.player.level().getBlockEntity(extraData.readBlockPos()),
                true);
    }

    /**
     * Server-side constructor.
     * Uses BlockEntity-backed ContainerData for live pattern data reads.
     */
    public ImprovedPatternCrafterMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity, false);
    }

    /**
     * Unified constructor.
     * @param isClientSide true when called from the client (FriendlyByteBuf) constructor
     */
    private ImprovedPatternCrafterMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity, boolean isClientSide) {
        super(ModMenuTypes.IMPROVED_PATTERN_CRAFTER_MENU.get(), containerId);

        if (blockEntity instanceof ImprovedPatternCrafterBlockEntity pcbe) {
            this.blockEntity = pcbe;
            this.inputFilterSlotCount = pcbe.getInputFilterHandler().getSlots();
            if (this.inputFilterSlotCount > 18) {
                this.inputFilterMenuSlotCount = 18;
                this.inputFilterViewHandler = new InputFilterViewHandler(pcbe.getInputFilterHandler());
                this.inputFilterViewHandler.setOffset(pcbe.getGuiFilterPage() * 18);
                addInputFilterSlots(this.inputFilterViewHandler);
            } else {
                this.inputFilterMenuSlotCount = this.inputFilterSlotCount;
                this.inputFilterViewHandler = null;
                addInputFilterSlots(pcbe.getInputFilterHandler());
            }
            addOutputFilterSlots(pcbe.getOutputFilterHandler());
            addUpgradeSlots(pcbe.getUpgradeHandler());
            addOutputSlots(pcbe.getOutputHandler());
            addInputSlots(pcbe.getInputHandler());

            if (isClientSide) {
                this.patternContainerData = new SimpleContainerData(getContainerDataSize());
            } else {
                final int dataEnergy = getDataEnergyStoredIndex();
                final int dataMaxEnergy = getDataMaxEnergyIndex();
                final int dataMode = getDataCraftingModeIndex();
                final int dataRedstone = getDataRedstoneModeIndex();
                final int dataTimer = getDataCraftingTimerIndex();
                final int dataInterval = getDataCraftingIntervalIndex();
                final int dataFilterPage = getDataFilterPageIndex();
                final int dataRecursiveOutput = getDataRecursiveOutputModeIndex();
                final int dataRemainderRouting = getDataRemainderRoutingModeIndex();
                this.patternContainerData = new ContainerData() {
                    @Override
                    public int get(int index) {
                        if (index == DATA_CURRENT_PATTERN) return pcbe.getCurrentPatternIndex();
                        if (index == DATA_TOTAL_PATTERNS) return pcbe.getPatternCount();
                        if (index == dataEnergy) return pcbe.getEnergyStorage().getEnergyStored();
                        if (index == dataMaxEnergy) return pcbe.getEnergyStorage().getMaxEnergyStored();
                        if (index == dataMode) return pcbe.getCraftingMode();
                        if (index == dataRedstone) return pcbe.getRedstoneMode();
                        if (index == dataTimer) return pcbe.getCraftingTimer();
                        if (index == dataInterval) return pcbe.getEffectiveCraftingInterval();
                        if (index == dataFilterPage) return pcbe.getGuiFilterPage();
                        if (index == dataRecursiveOutput) return pcbe.getRecursiveOutputMode();
                        if (index == dataRemainderRouting) return pcbe.getRemainderRoutingMode();
                        if (index >= DATA_FILTER_LETTERS_START && index < dataEnergy) {
                            return pcbe.getFilterLetter(index - DATA_FILTER_LETTERS_START);
                        }
                        PatternData pattern = pcbe.getCurrentPattern();
                        return pattern != null ? pattern.getCell(index - DATA_GRID_START) : 0;
                    }

                    @Override
                    public void set(int index, int value) {}

                    @Override
                    public int getCount() {
                        return getContainerDataSize();
                    }
                };
            }
        } else {
            this.blockEntity = null;
            this.inputFilterSlotCount = 18;
            this.inputFilterMenuSlotCount = 18;
            this.inputFilterViewHandler = null;
            addInputFilterSlots(new ItemStackHandler(18));
            addOutputFilterSlots(new ItemStackHandler(OUTPUT_FILTER_SLOTS));
            addUpgradeSlots(new ItemStackHandler(UPGRADE_SLOTS));
            addOutputSlots(new ItemStackHandler(OUTPUT_SLOTS));
            addInputSlots(new ItemStackHandler(INPUT_SLOTS));
            this.patternContainerData = new SimpleContainerData(getContainerDataSize());
        }

        addDataSlots(patternContainerData);
        addPlayerInventory(playerInventory);
        addPlayerHotbar(playerInventory);
    }

    // ===== Pattern Data Accessors (read from synced ContainerData) =====

    public int getCurrentPatternIndex() {
        return patternContainerData.get(DATA_CURRENT_PATTERN);
    }

    public int getTotalPatterns() {
        return patternContainerData.get(DATA_TOTAL_PATTERNS);
    }

    public int getGridCell(int index) {
        if (index < 0 || index >= PatternData.GRID_SIZE) return 0;
        return patternContainerData.get(DATA_GRID_START + index);
    }

    /**
     * Returns the letter assigned to an input filter slot (0=disabled, 1..N = letters).
     */
    public int getFilterLetter(int index) {
        if (index < 0 || index >= inputFilterSlotCount) return 0;
        return patternContainerData.get(DATA_FILTER_LETTERS_START + index);
    }

    // ===== Energy Data Accessors =====

    public int getEnergyStored() {
        return patternContainerData.get(getDataEnergyStoredIndex());
    }

    public int getMaxEnergyStored() {
        return patternContainerData.get(getDataMaxEnergyIndex());
    }

    /** 0 = Shaped+Shapeless, 1 = Only Shaped, 2 = Only Shapeless */
    public int getCraftingMode() {
        return patternContainerData.get(getDataCraftingModeIndex());
    }

    public int getRedstoneMode() {
        return patternContainerData.get(getDataRedstoneModeIndex());
    }

    public int getCraftingTimer() {
        return patternContainerData.get(getDataCraftingTimerIndex());
    }

    public int getCraftingInterval() {
        return patternContainerData.get(getDataCraftingIntervalIndex());
    }

    /** Current filter page (0-based) synced from server; only meaningful when inputFilterSlotCount > 18. */
    public int getSyncedFilterPage() {
        return patternContainerData.get(getDataFilterPageIndex());
    }

    /** 1–3: recursive output routing mode (synced). */
    public int getSyncedRecursiveOutputMode() {
        return patternContainerData.get(getDataRecursiveOutputModeIndex());
    }

    /** 1–2: remainder routing mode (synced). */
    public int getSyncedRemainderRoutingMode() {
        return patternContainerData.get(getDataRemainderRoutingModeIndex());
    }

    // ===== Ghost Slot Click Handling =====

    /**
     * Ghost slot click handling.
     * For input filter slots: blocked if the slot's letter is empty (disabled).
     * Copies the carried item as a filter reference (count=1).
     * Does NOT consume the carried item - it stays on the cursor.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Input filter ghost slots: check if letter is assigned before allowing interaction
        if (slotId >= INPUT_FILTER_START && slotId < getInputFilterEnd()) {
            int localIndex = slotId - INPUT_FILTER_START;
            // When paginated, menu slot 0-17 maps to BE slots (offset..offset+17); use real BE index for letter check
            int filterIndex = (inputFilterViewHandler != null)
                    ? inputFilterViewHandler.getOffset() + localIndex
                    : localIndex;
            if (blockEntity != null && blockEntity.getFilterLetter(filterIndex) == 0) {
                return;
            }
            ItemStack carried = getCarried();
            ItemStack toSet = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
            this.slots.get(slotId).set(toSet);
            if (!player.level().isClientSide()) {
                broadcastFullState();
            }
            return;
        }
        // Output filter ghost slots: always allowed
        if (slotId >= getOutputFilterStart() && slotId < getOutputFilterEnd()) {
            ItemStack carried = getCarried();
            ItemStack toSet = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
            this.slots.get(slotId).set(toSet);
            if (!player.level().isClientSide()) {
                broadcastFullState();
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    // ===== Slot Setup Methods =====

    // Input filter: 9 columns x N rows at (80, 47), N = handler.getSlots()
    private void addInputFilterSlots(net.neoforged.neoforge.items.IItemHandler handler) {
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            int row = i / 9;
            int col = i % 9;
            this.addSlot(new GhostSlot(handler, i, 80 + col * 18, 47 + row * 18));
        }
    }

    // Output filter: 3 columns x 7 rows at (13, 65)
    private void addOutputFilterSlots(ItemStackHandler handler) {
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new GhostSlot(handler, row * 3 + col,
                        13 + col * 18, 65 + row * 18));
            }
        }
    }

    // Upgrade: 1 column x 2 rows at (49, 211) - slot 0 = logic module, slot 1 = speed modules (iska_utils)
    private void addUpgradeSlots(ItemStackHandler handler) {
        for (int row = 0; row < 2; row++) {
            this.addSlot(new UpgradeSlot(handler, row, 49, 211 + row * 18));
        }
    }

    // Output: 3 columns x 3 rows at (259, 171) - extract only, no insertion
    private void addOutputSlots(ItemStackHandler handler) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new OutputSlot(handler, row * 3 + col,
                        259 + col * 18, 171 + row * 18));
            }
        }
    }

    // Machine input inventory: 9 columns x 3 rows at (80, 105)
    private void addInputSlots(ItemStackHandler handler) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new SlotItemHandler(handler, row * 9 + col,
                        80 + col * 18, 105 + row * 18));
            }
        }
    }

    // Player inventory: 9 columns x 3 rows at (80, 171)
    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        80 + col * 18, 171 + row * 18));
            }
        }
    }

    // Player hotbar: 9 columns x 1 row at (80, 229)
    private void addPlayerHotbar(Inventory playerInventory) {
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    80 + col * 18, 229));
        }
    }

    // ===== Quick Move (Shift-Click) =====

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot == null || !slot.hasItem()) {
            return result;
        }

        // Ghost slots: no shift-click behavior
        if (index < getOutputFilterEnd()) {
            return result;
        }

        ItemStack stackInSlot = slot.getItem();
        result = stackInSlot.copy();

        if (index < getPlayerInvStart()) {
            // From machine slots (upgrade/output/input) -> to player inventory/hotbar
            if (!moveItemStackTo(stackInSlot, getPlayerInvStart(), getPlayerHotbarEnd(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < getPlayerHotbarStart()) {
            // From player inventory -> try machine input first, then upgrade, then hotbar
            if (!moveItemStackTo(stackInSlot, getInputStart(), getInputEnd(), false)) {
                if (!moveItemStackTo(stackInSlot, getUpgradeStart(), getUpgradeEnd(), false)) {
                    if (!moveItemStackTo(stackInSlot, getPlayerHotbarStart(), getPlayerHotbarEnd(), false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        } else {
            // From hotbar -> try machine input first, then upgrade, then player inventory
            if (!moveItemStackTo(stackInSlot, getInputStart(), getInputEnd(), false)) {
                if (!moveItemStackTo(stackInSlot, getUpgradeStart(), getUpgradeEnd(), false)) {
                    if (!moveItemStackTo(stackInSlot, getPlayerInvStart(), getPlayerInvEnd(), false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.getLevel() == null) {
            return false;
        }
        return player.distanceToSqr(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    public ImprovedPatternCrafterBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** Slot-dedication (mark input) filter for the given input slot index (0..26). For GUI ghost display. */
    public ItemStack getMarkInputFilter(int slot) {
        return blockEntity != null ? blockEntity.getMarkInputFilter(slot) : ItemStack.EMPTY;
    }

    /** True if the given input slot has a mark-input filter. */
    public boolean hasMarkInputFilter(int slot) {
        return blockEntity != null && blockEntity.hasMarkInputFilter(slot);
    }
}
