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
 *   - Input filter ghost slots:  9x2 = 18 slots at (80, 47)
 *   - Output filter ghost slots: 3x7 = 21 slots at (13, 65)
 *   - Upgrade slots:             1x2 = 2 slots  at (49, 211)  [manual only]
 *   - Output slots:              3x3 = 9 slots  at (258, 171) [extract only]
 *   - Machine input inventory:   9x3 = 27 slots at (80, 105)  [hopper can insert]
 *   - Player inventory:          9x3 = 27 slots at (80, 171)
 *   - Player hotbar:             9x1 = 9 slots  at (80, 229)
 */
public class ImprovedPatternCrafterMenu extends AbstractContainerMenu {

    // Slot counts
    public static final int INPUT_FILTER_SLOTS = 18;    // 9x2
    public static final int OUTPUT_FILTER_SLOTS = 21;   // 3x7
    public static final int UPGRADE_SLOTS = 2;          // 1x2
    public static final int OUTPUT_SLOTS = 9;           // 3x3
    public static final int INPUT_SLOTS = 27;           // 9x3 machine input
    public static final int PLAYER_INV_SLOTS = 27;      // 9x3
    public static final int PLAYER_HOTBAR_SLOTS = 9;    // 9x1

    // Slot index ranges
    public static final int INPUT_FILTER_START = 0;
    public static final int INPUT_FILTER_END = INPUT_FILTER_START + INPUT_FILTER_SLOTS;         // 18
    public static final int OUTPUT_FILTER_START = INPUT_FILTER_END;
    public static final int OUTPUT_FILTER_END = OUTPUT_FILTER_START + OUTPUT_FILTER_SLOTS;      // 39
    public static final int GHOST_SLOTS_END = OUTPUT_FILTER_END;                                // 39
    public static final int UPGRADE_START = GHOST_SLOTS_END;
    public static final int UPGRADE_END = UPGRADE_START + UPGRADE_SLOTS;                        // 41
    public static final int OUTPUT_START = UPGRADE_END;
    public static final int OUTPUT_END = OUTPUT_START + OUTPUT_SLOTS;                           // 50
    public static final int INPUT_START = OUTPUT_END;
    public static final int INPUT_END = INPUT_START + INPUT_SLOTS;                              // 77
    public static final int PLAYER_INV_START = INPUT_END;
    public static final int PLAYER_INV_END = PLAYER_INV_START + PLAYER_INV_SLOTS;               // 104
    public static final int PLAYER_HOTBAR_START = PLAYER_INV_END;
    public static final int PLAYER_HOTBAR_END = PLAYER_HOTBAR_START + PLAYER_HOTBAR_SLOTS;      // 113

    // ContainerData indices for synced data
    // [0]    currentPatternIndex
    // [1]    totalPatterns
    // [2-10] grid cells (9)
    // [11-28] filter letters (18)
    // [29]   energyStored
    // [30]   maxEnergyStored
    private static final int DATA_CURRENT_PATTERN = 0;
    private static final int DATA_TOTAL_PATTERNS = 1;
    private static final int DATA_GRID_START = 2;
    private static final int DATA_FILTER_LETTERS_START = 2 + PatternData.GRID_SIZE; // 11
    private static final int DATA_ENERGY_STORED = DATA_FILTER_LETTERS_START + INPUT_FILTER_SLOTS; // 29
    private static final int DATA_MAX_ENERGY = DATA_ENERGY_STORED + 1; // 30
    private static final int DATA_CRAFTING_MODE = DATA_MAX_ENERGY + 1; // 31
    private static final int DATA_REDSTONE_MODE = DATA_CRAFTING_MODE + 1; // 32
    private static final int DATA_CRAFTING_TIMER = DATA_REDSTONE_MODE + 1; // 33
    private static final int DATA_CRAFTING_INTERVAL = DATA_CRAFTING_TIMER + 1; // 34
    private static final int CONTAINER_DATA_SIZE = DATA_CRAFTING_INTERVAL + 1; // 35

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
            addInputFilterSlots(pcbe.getInputFilterHandler());
            addOutputFilterSlots(pcbe.getOutputFilterHandler());
            addUpgradeSlots(pcbe.getUpgradeHandler());
            addOutputSlots(pcbe.getOutputHandler());
            addInputSlots(pcbe.getInputHandler());

            if (isClientSide) {
                // Client: SimpleContainerData receives synced values via set()
                this.patternContainerData = new SimpleContainerData(CONTAINER_DATA_SIZE);
            } else {
                // Server: ContainerData reads live data from BlockEntity
                this.patternContainerData = new ContainerData() {
                    @Override
                    public int get(int index) {
                        if (index == DATA_CURRENT_PATTERN) return pcbe.getCurrentPatternIndex();
                        if (index == DATA_TOTAL_PATTERNS) return pcbe.getPatternCount();
                        if (index == DATA_ENERGY_STORED) return pcbe.getEnergyStorage().getEnergyStored();
                        if (index == DATA_MAX_ENERGY) return pcbe.getEnergyStorage().getMaxEnergyStored();
                        if (index == DATA_CRAFTING_MODE) return pcbe.getCraftingMode();
                        if (index == DATA_REDSTONE_MODE) return pcbe.getRedstoneMode();
                        if (index == DATA_CRAFTING_TIMER) return pcbe.getCraftingTimer();
                        if (index == DATA_CRAFTING_INTERVAL) return pcbe.getEffectiveCraftingInterval();
                        if (index >= DATA_FILTER_LETTERS_START && index < DATA_ENERGY_STORED) {
                            return pcbe.getFilterLetter(index - DATA_FILTER_LETTERS_START);
                        }
                        // Grid cells
                        PatternData pattern = pcbe.getCurrentPattern();
                        return pattern != null ? pattern.getCell(index - DATA_GRID_START) : 0;
                    }

                    @Override
                    public void set(int index, int value) {
                        // Server-side: data is set via packets, not via ContainerData sync
                    }

                    @Override
                    public int getCount() {
                        return CONTAINER_DATA_SIZE;
                    }
                };
            }
        } else {
            this.blockEntity = null;
            // Fallback with empty handlers
            addInputFilterSlots(new ItemStackHandler(INPUT_FILTER_SLOTS));
            addOutputFilterSlots(new ItemStackHandler(OUTPUT_FILTER_SLOTS));
            addUpgradeSlots(new ItemStackHandler(UPGRADE_SLOTS));
            addOutputSlots(new ItemStackHandler(OUTPUT_SLOTS));
            addInputSlots(new ItemStackHandler(INPUT_SLOTS));
            this.patternContainerData = new SimpleContainerData(CONTAINER_DATA_SIZE);
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
     * Returns the letter assigned to an input filter slot (0=disabled, 1-18=A-R).
     */
    public int getFilterLetter(int index) {
        if (index < 0 || index >= INPUT_FILTER_SLOTS) return 0;
        return patternContainerData.get(DATA_FILTER_LETTERS_START + index);
    }

    // ===== Energy Data Accessors =====

    public int getEnergyStored() {
        return patternContainerData.get(DATA_ENERGY_STORED);
    }

    public int getMaxEnergyStored() {
        return patternContainerData.get(DATA_MAX_ENERGY);
    }

    /** 0 = Shaped+Shapeless, 1 = Only Shaped, 2 = Only Shapeless */
    public int getCraftingMode() {
        return patternContainerData.get(DATA_CRAFTING_MODE);
    }

    public int getRedstoneMode() {
        return patternContainerData.get(DATA_REDSTONE_MODE);
    }

    public int getCraftingTimer() {
        return patternContainerData.get(DATA_CRAFTING_TIMER);
    }

    public int getCraftingInterval() {
        return patternContainerData.get(DATA_CRAFTING_INTERVAL);
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
        if (slotId >= INPUT_FILTER_START && slotId < INPUT_FILTER_END) {
            int filterIndex = slotId - INPUT_FILTER_START;
            // Block interaction if the filter slot's letter is empty (disabled)
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
        if (slotId >= OUTPUT_FILTER_START && slotId < OUTPUT_FILTER_END) {
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

    // Input filter: 9 columns x 2 rows at (80, 47)
    private void addInputFilterSlots(ItemStackHandler handler) {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new GhostSlot(handler, row * 9 + col,
                        80 + col * 18, 47 + row * 18));
            }
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

    // Output: 3 columns x 3 rows at (258, 171) - extract only, no insertion
    private void addOutputSlots(ItemStackHandler handler) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new OutputSlot(handler, row * 3 + col,
                        258 + col * 18, 171 + row * 18));
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
        if (index < GHOST_SLOTS_END) {
            return result;
        }

        ItemStack stackInSlot = slot.getItem();
        result = stackInSlot.copy();

        if (index < PLAYER_INV_START) {
            // From machine slots (upgrade/output/input) -> to player inventory/hotbar
            if (!moveItemStackTo(stackInSlot, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_HOTBAR_START) {
            // From player inventory -> try machine input first, then upgrade, then hotbar
            if (!moveItemStackTo(stackInSlot, INPUT_START, INPUT_END, false)) {
                if (!moveItemStackTo(stackInSlot, UPGRADE_START, UPGRADE_END, false)) {
                    if (!moveItemStackTo(stackInSlot, PLAYER_HOTBAR_START, PLAYER_HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        } else {
            // From hotbar -> try machine input first, then upgrade, then player inventory
            if (!moveItemStackTo(stackInSlot, INPUT_START, INPUT_END, false)) {
                if (!moveItemStackTo(stackInSlot, UPGRADE_START, UPGRADE_END, false)) {
                    if (!moveItemStackTo(stackInSlot, PLAYER_INV_START, PLAYER_INV_END, false)) {
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
}
