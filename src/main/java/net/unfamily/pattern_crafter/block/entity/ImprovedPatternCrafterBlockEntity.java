package net.unfamily.pattern_crafter.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.unfamily.pattern_crafter.Config;
import net.unfamily.pattern_crafter.pattern.PatternData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BlockEntity for the Improved Pattern Crafter.
 * Stores ghost filter items, machine input/output inventories, upgrade items,
 * and crafting patterns.
 */
public class ImprovedPatternCrafterBlockEntity extends BlockEntity {

    // Input filter ghost slots: count = getMaxKeyInputs() (config: improved 36, normal 18)
    private ItemStackHandler inputFilterHandler;

    // 3 columns x 7 rows = 21 ghost slots for output filters
    private final ItemStackHandler outputFilterHandler = new ItemStackHandler(21) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // 1 column x 2 rows = 2 real slots for upgrades (manual only). Slot limits from config (like Modular Fan).
    private final ItemStackHandler upgradeHandler = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return switch (slot) {
                case 0 -> getMaxLogicModules();
                case 1 -> getMaxSpeedModules();
                default -> super.getSlotLimit(slot);
            };
        }
    };

    // 3 columns x 3 rows = 9 output slots (extract only, filled by machine)
    private final ItemStackHandler outputHandler = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /**
     * Slot dedication filters for the 27 input slots (like ghostFilters in Structure Placer).
     * "Mark Input" saves here only; used for isItemValid and GUI ghost display. Not used for crafting.
     */
    private final List<ItemStack> markInputFilters = new ArrayList<>();

    // 9 columns x 3 rows = 27 input slots (machine internal inventory, hopper can insert)
    private final ItemStackHandler inputHandler = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot >= 0 && slot < markInputFilters.size()) {
                ItemStack filter = markInputFilters.get(slot);
                if (!filter.isEmpty()) {
                    return ItemStack.isSameItemSameComponents(stack, filter);
                }
            }
            return true;
        }
    };

    // Pattern system
    private final List<PatternData> patterns = new ArrayList<>();
    private int currentPatternIndex = 0;

    // Filter letter assignments: 0=empty(disabled), 1..N = letters (N = getMaxKeyInputs())
    private int[] filterLetters;

    // Crafting system
    private int craftingTimer = 0;
    private int craftingPatternIndex = 0; // Round-robin index, separate from GUI currentPatternIndex

    // Energy storage (RF/FE)
    private final EnergyStorageImpl energyStorage = new EnergyStorageImpl(getEnergyCapacity());

    // Redstone mode: 0 = ignore, 1 = low, 2 = high, 3 = pulse (once per rising edge), 4 = disabled
    private int redstoneMode = 0;
    private boolean previousRedstoneState = false;
    private int pulseIgnoreTimer = 0;
    private static final int PULSE_IGNORE_INTERVAL = 10;
    /** In PULSE mode: one craft is scheduled and will run after the normal crafting interval (not instant). */
    private boolean pulseCraftPending = false;

    /** Current input filter page when GUI is paginated (0-based). Synced to client via ContainerData; not persisted. */
    private int guiFilterPage = 0;

    public int getGuiFilterPage() {
        return guiFilterPage;
    }

    public void setGuiFilterPage(int page) {
        this.guiFilterPage = Math.max(0, page);
    }

    /**
     * Combined handler for automation (hoppers, tubes, etc.).
     * Slots 0-26:  input  (insert only via automation)
     * Slots 27-35: output (extract only via automation)
     * Upgrades and ghost filters are NOT exposed.
     */
    private final IItemHandler automationHandler = new IItemHandler() {
        private static final int INPUT_SIZE = 27;
        private static final int OUTPUT_SIZE = 9;

        @Override
        public int getSlots() {
            return INPUT_SIZE + OUTPUT_SIZE;
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            if (slot < INPUT_SIZE) return inputHandler.getStackInSlot(slot);
            return outputHandler.getStackInSlot(slot - INPUT_SIZE);
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (slot < INPUT_SIZE) return inputHandler.insertItem(slot, stack, simulate);
            return stack; // Output: no insertion
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < INPUT_SIZE) return ItemStack.EMPTY; // Input: no extraction via automation
            return outputHandler.extractItem(slot - INPUT_SIZE, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < INPUT_SIZE) return inputHandler.getSlotLimit(slot);
            return outputHandler.getSlotLimit(slot - INPUT_SIZE);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot < INPUT_SIZE) return inputHandler.isItemValid(slot, stack);
            return false; // Output: no insertion
        }
    };

    public ImprovedPatternCrafterBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.IMPROVED_PATTERN_CRAFTER_BE.get(), pos, state);
    }

    /** For subclasses (e.g. PatternCrafterBlockEntity) that use a different BlockEntityType. */
    protected ImprovedPatternCrafterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        int maxKeys = getMaxKeyInputs();
        this.inputFilterHandler = new ItemStackHandler(maxKeys) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        this.filterLetters = new int[maxKeys];
        for (int i = 0; i < 27; i++) {
            markInputFilters.add(ItemStack.EMPTY);
        }
        initPatterns();
    }

    /** Max key inputs (filter slots / letters) for this machine. Improved: config 36, Normal: config 18. */
    protected int getMaxKeyInputs() {
        try {
            return Math.max(1, Math.min(256, Config.IMPROVED_MAX_KEY_INPUTS.get()));
        } catch (Exception e) {
            return 36;
        }
    }

    private void initPatterns() {
        int maxPatterns = getMaxPatterns();
        patterns.clear();
        for (int i = 0; i < maxPatterns; i++) {
            patterns.add(new PatternData());
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.onDataPacket(net, pkt, registries);
        if (pkt.getTag() != null) {
            loadAdditional(pkt.getTag(), registries);
        }
    }

    // ===== Item Handler Getters =====

    public ItemStackHandler getInputFilterHandler() {
        return inputFilterHandler;
    }

    public ItemStackHandler getOutputFilterHandler() {
        return outputFilterHandler;
    }

    public ItemStackHandler getUpgradeHandler() {
        return upgradeHandler;
    }

    public ItemStackHandler getOutputHandler() {
        return outputHandler;
    }

    public ItemStackHandler getInputHandler() {
        return inputHandler;
    }

    public IItemHandler getAutomationHandler() {
        return automationHandler;
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    /** Overridable for Normal (0 when no energy). */
    protected int getEnergyCapacity() {
        try {
            return Config.ENERGY_CAPACITY.get();
        } catch (Exception ignored) {
            return 1000;
        }
    }

    /** Overridable for Normal (0 when no energy). */
    protected int getEnergyPerCraft() {
        try {
            return Config.ENERGY_PER_CRAFT.get();
        } catch (Exception ignored) {
            return 5;
        }
    }

    /** Overridable for Normal. */
    protected int getMaxLogicModules() {
        try {
            return Config.MAX_LOGIC_MODULES.get();
        } catch (Exception ignored) {
            return 3;
        }
    }

    /** Overridable for Normal. */
    protected int getMaxSpeedModules() {
        try {
            return Config.MAX_SPEED_MODULES.get();
        } catch (Exception ignored) {
            return 1;
        }
    }

    /** True if any upgrade slot is allowed (used to show/dim upgrade area in GUI). */
    public boolean hasUpgrades() {
        return getMaxLogicModules() > 0 || getMaxSpeedModules() > 0;
    }

    // ===== Upgrade effects (logic = extra patterns, speed = interval multiplier) =====

    private static final String ISKA_UTILS = "iska_utils";
    private static final ResourceLocation LOGIC_MODULE_ID = ResourceLocation.fromNamespaceAndPath(ISKA_UTILS, "logic_module");
    private static final ResourceLocation[] SPEED_MODULE_IDS = new ResourceLocation[]{
            ResourceLocation.fromNamespaceAndPath(ISKA_UTILS, "slow_module"),
            ResourceLocation.fromNamespaceAndPath(ISKA_UTILS, "moderate_module"),
            ResourceLocation.fromNamespaceAndPath(ISKA_UTILS, "fast_module"),
            ResourceLocation.fromNamespaceAndPath(ISKA_UTILS, "extreme_module"),
            ResourceLocation.fromNamespaceAndPath(ISKA_UTILS, "ultra_module")
    };

    /** Number of logic modules in the top upgrade slot (each adds one pattern). */
    private int getLogicModuleCount() {
        ItemStack stack = upgradeHandler.getStackInSlot(0);
        if (stack.isEmpty()) return 0;
        Item logic = BuiltInRegistries.ITEM.get(LOGIC_MODULE_ID);
        if (logic == null || logic == net.minecraft.world.item.Items.AIR) return 0;
        if (!stack.is(logic)) return 0;
        return stack.getCount();
    }

    /** Speed multiplier from the speed upgrade slot (one module only). 1.0 = no change. */
    private double getSpeedMultiplier() {
        ItemStack stack = upgradeHandler.getStackInSlot(1);
        if (stack.isEmpty()) return 1.0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        try {
            if (id.equals(SPEED_MODULE_IDS[0])) return Config.SPEED_MULTIPLIER_SLOW.get();
            if (id.equals(SPEED_MODULE_IDS[1])) return Config.SPEED_MULTIPLIER_MODERATE.get();
            if (id.equals(SPEED_MODULE_IDS[2])) return Config.SPEED_MULTIPLIER_FAST.get();
            if (id.equals(SPEED_MODULE_IDS[3])) return Config.SPEED_MULTIPLIER_EXTREME.get();
            if (id.equals(SPEED_MODULE_IDS[4])) return Config.SPEED_MULTIPLIER_ULTRA.get();
        } catch (Exception ignored) {}
        return 1.0;
    }

    /** Overridable for Normal. */
    protected int getBasePatterns() {
        try {
            return Config.BASE_PATTERNS.get();
        } catch (Exception ignored) {
            return 4;
        }
    }

    /** Overridable for Normal. */
    protected int getMaxPatterns() {
        try {
            return Config.MAX_PATTERNS.get();
        } catch (Exception ignored) {
            return 16;
        }
    }

    /** Effective number of patterns (base + logic modules), capped by max. */
    public int getEffectivePatternCount() {
        int base = getBasePatterns();
        int max = getMaxPatterns();
        int count = base + getLogicModuleCount();
        return Math.min(max, Math.max(1, count));
    }

    /** Overridable for Normal. */
    protected int getCraftingInterval() {
        try {
            return Config.CRAFTING_INTERVAL.get();
        } catch (Exception ignored) {
            return 20;
        }
    }

    /** Effective crafting interval in ticks (base interval * speed multiplier). */
    public int getEffectiveCraftingInterval() {
        int base = getCraftingInterval();
        double mult = getSpeedMultiplier();
        int interval = (int) Math.round(base * mult);
        return Math.max(1, interval);
    }

    public int getCraftingTimer() {
        return craftingTimer;
    }

    // ===== Crafting Mode (per pattern) =====

    /** Returns the crafting mode for the currently selected pattern (GUI). 0 = both, 1 = only shaped, 2 = only shapeless */
    public int getCraftingMode() {
        if (currentPatternIndex < 0 || currentPatternIndex >= patterns.size()) return 0;
        return patterns.get(currentPatternIndex).getCraftingMode();
    }

    /** Cycles the crafting mode for the currently selected pattern */
    public void cycleCraftingMode() {
        if (currentPatternIndex < 0 || currentPatternIndex >= patterns.size()) return;
        patterns.get(currentPatternIndex).cycleCraftingMode();
        setChanged();
    }

    public int getRedstoneMode() {
        return redstoneMode;
    }

    public void cycleRedstoneMode() {
        redstoneMode = (redstoneMode + 1) % 5; // 0 -> 1 -> 2 -> 3 (pulse) -> 4 (disabled) -> 0
        setChanged();
        if (redstoneMode != 3) {
            pulseIgnoreTimer = 0;
        }
    }

    // ===== Filter Letter System =====

    public int getFilterLetter(int index) {
        if (index < 0 || index >= filterLetters.length) return 0;
        return filterLetters[index];
    }

    public void setFilterLetter(int index, int value) {
        if (index < 0 || index >= filterLetters.length) return;
        // A-Z only (1-26); ignore slot-count limit
        if (value < 0 || value > PatternData.MAX_LETTER) value = 0;
        filterLetters[index] = value;
        setChanged();
    }

    /** True if at least one input filter slot has an active letter (enabled). No crafting when all are disabled. */
    public boolean hasAnyInputFilterActive() {
        for (int i = 0; i < filterLetters.length; i++) {
            if (filterLetters[i] > 0) return true;
        }
        return false;
    }

    /**
     * Saves slot-dedication filters from current machine input slots (1:1, 27 slots).
     * Identical to Structure Placer setInventoryFilters: only slots with an item get their
     * markInputFilters entry set; empty input slots are left unchanged. Does not touch
     * inputFilterHandler or filterLetters (those are for crafting only).
     */
    public void setInputFilters() {
        for (int slot = 0; slot < inputHandler.getSlots(); slot++) {
            ItemStack currentStack = inputHandler.getStackInSlot(slot);
            if (!currentStack.isEmpty()) {
                ItemStack filter = currentStack.copyWithCount(1);
                markInputFilters.set(slot, filter);
            }
            // Empty slots remain unchanged (same as iskandert_utilities)
        }
        setChanged();
    }

    /** Clear all slot-dedication filters (Shift+Click on Mark Input). Does not touch inputFilterHandler. */
    public void clearAllInputFilters() {
        for (int i = 0; i < markInputFilters.size(); i++) {
            markInputFilters.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** Clear slot-dedication filters where the input slot no longer has the matching item (Ctrl/Alt+Click). */
    public void clearEmptyInputFilters() {
        for (int slot = 0; slot < markInputFilters.size(); slot++) {
            ItemStack filter = markInputFilters.get(slot);
            if (!filter.isEmpty()) {
                ItemStack currentStack = inputHandler.getStackInSlot(slot);
                if (currentStack.isEmpty() || !ItemStack.isSameItemSameComponents(currentStack, filter)) {
                    markInputFilters.set(slot, ItemStack.EMPTY);
                }
            }
        }
        setChanged();
    }

    /** Returns the slot-dedication filter for the given input slot (for GUI ghost display). */
    public ItemStack getMarkInputFilter(int slot) {
        if (slot >= 0 && slot < markInputFilters.size()) {
            return markInputFilters.get(slot);
        }
        return ItemStack.EMPTY;
    }

    /** Returns true if the given input slot has a mark-input (slot dedication) filter. */
    public boolean hasMarkInputFilter(int slot) {
        return getMarkInputFilter(slot).isEmpty() == false;
    }

    // ===== Pattern System =====

    public int getCurrentPatternIndex() {
        return currentPatternIndex;
    }

    public int getPatternCount() {
        return getEffectivePatternCount();
    }

    public PatternData getCurrentPattern() {
        int n = getEffectivePatternCount();
        if (currentPatternIndex >= 0 && currentPatternIndex < n) {
            return patterns.get(currentPatternIndex);
        }
        return null;
    }

    public PatternData getPattern(int index) {
        int n = getEffectivePatternCount();
        if (index >= 0 && index < n) {
            return patterns.get(index);
        }
        return null;
    }

    public void setCurrentPatternIndex(int index) {
        int n = getEffectivePatternCount();
        if (index >= 0 && index < n) {
            this.currentPatternIndex = index;
            setChanged();
        }
    }

    // ===== Crafting Logic =====

    /**
     * Called every tick on the server side by the BlockEntityTicker.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ImprovedPatternCrafterBlockEntity entity) {
        // When logic modules are removed, effective pattern count can drop: reset to first pattern (1) if current is out of range
        int effectiveCount = entity.getEffectivePatternCount();
        if (entity.currentPatternIndex >= effectiveCount) {
            entity.currentPatternIndex = 0;
            entity.setChanged();
        }

        // Do not run any crafting when no input filter is active (all letters = 0)
        if (!entity.hasAnyInputFilterActive()) {
            return;
        }

        int signal = entity.level != null ? entity.level.getBestNeighborSignal(entity.worldPosition) : 0;
        boolean hasSignal = signal > 0;

        if (entity.redstoneMode == 3) {
            // PULSE: schedule one craft on rising edge; it runs after the normal crafting interval (not instant)
            if (entity.pulseIgnoreTimer > 0) {
                entity.pulseIgnoreTimer--;
            }
            if (entity.pulseIgnoreTimer == 0 && hasSignal && !entity.previousRedstoneState) {
                entity.pulseIgnoreTimer = PULSE_IGNORE_INTERVAL;
                entity.pulseCraftPending = true;
                entity.craftingTimer = 0;
            }
            entity.previousRedstoneState = hasSignal;

            if (entity.pulseCraftPending) {
                entity.craftingTimer++;
                int interval = entity.getEffectiveCraftingInterval();
                if (entity.craftingTimer >= interval) {
                    entity.craftingTimer = 0;
                    entity.pulseCraftPending = false;
                    entity.attemptCraft();
                }
            }
            return;
        }

        if (!entity.isRedstoneAllowed()) {
            return;
        }

        entity.craftingTimer++;
        int interval = entity.getEffectiveCraftingInterval();

        if (entity.craftingTimer >= interval) {
            entity.craftingTimer = 0;
            entity.attemptCraft();
        }
    }

    /** True when redstone mode allows crafting this tick (not used for PULSE). */
    private boolean isRedstoneAllowed() {
        if (level == null) return true;
        int signal = level.getBestNeighborSignal(worldPosition);
        return switch (redstoneMode) {
            case 0 -> true;   // Ignore: always
            case 1 -> signal == 0;  // Low: craft when no signal
            case 2 -> signal > 0;   // High: craft when signal
            case 4 -> false;  // Disabled: never
            default -> true;
        };
    }

    /**
     * Attempts to craft: for each input slot (0, 1, 2, ...) try all patterns in sequence.
     * Only when all patterns have been tried for the current slot do we move to the next slot.
     * Ensures a valid key in filters (non-empty pattern) before trying.
     */
    private void attemptCraft() {
        if (level == null || level.isClientSide()) return;

        int totalPatterns = getEffectivePatternCount();
        if (totalPatterns == 0) return;

        int totalSlots = inputHandler.getSlots();
        craftingPatternIndex = craftingPatternIndex % totalPatterns;

        // Outer loop: input slots (0, 1, 2, ...)
        for (int prioritySlot = 0; prioritySlot < totalSlots; prioritySlot++) {
            // Inner loop: all patterns for this slot
            for (int patternAttempt = 0; patternAttempt < totalPatterns; patternAttempt++) {
                int patternIndex = (craftingPatternIndex + patternAttempt) % totalPatterns;
                PatternData pattern = patterns.get(patternIndex);
                if (isPatternEmpty(pattern)) continue;

                if (tryCraftPattern(pattern, prioritySlot)) {
                    craftingPatternIndex = (patternIndex + 1) % totalPatterns;
                    return; // Success!
                }
            }
        }
    }

    /**
     * Tries to craft using a specific pattern, preferring the given input slot when resolving items.
     * If a result is blacklisted or no valid recipe is found, excludes the item types
     * that led to the failure and retries with different items from input.
     * @param pattern the pattern to use
     * @param prioritySlot input slot to try first when resolving the grid (then slot+1, slot+2, ...)
     * @return true if crafting succeeded
     */
    private boolean tryCraftPattern(PatternData pattern, int prioritySlot) {
        // Base exclusion: specific filter items (for wildcard matching)
        List<ItemStack> specificItems = collectSpecificFilterItems();
        // Additional exclusions from blacklisted results (grows on each retry)
        List<ItemStack> craftExclusions = new ArrayList<>();

        // Retry loop: when a result is blacklisted, exclude those items and try again
        int maxRetries = inputHandler.getSlots(); // can't have more distinct items than slots
        for (int retry = 0; retry <= maxRetries; retry++) {

            // Build combined exclusion list for wildcard matching
            List<ItemStack> wildcardExclusions = new ArrayList<>(specificItems);
            wildcardExclusions.addAll(craftExclusions);

            // Track available item counts per input slot
            int[] availableCounts = new int[inputHandler.getSlots()];
            for (int i = 0; i < availableCounts.length; i++) {
                availableCounts[i] = inputHandler.getStackInSlot(i).getCount();
            }

            // Resolve each grid cell to an actual item from inputHandler.
            // Key rule: all cells with the SAME letter must use the SAME item type.
            // This prevents mixed grids (e.g., 1 iron + 8 redstone) that don't form valid recipes.
            Map<Integer, ItemStack> letterItemDecision = new HashMap<>();
            List<ItemStack> craftingGrid = new ArrayList<>(9);
            int[] reservedSlotForCell = new int[9];
            for (int i = 0; i < 9; i++) reservedSlotForCell[i] = -1;
            boolean resolutionFailed = false;

            for (int cell = 0; cell < PatternData.GRID_SIZE; cell++) {
                int letterValue = pattern.getCell(cell);
                if (letterValue == PatternData.EMPTY) {
                    craftingGrid.add(ItemStack.EMPTY);
                    continue;
                }

                int foundSlot;

                if (letterItemDecision.containsKey(letterValue)) {
                    // Already decided which item type for this letter - find more of the same
                    ItemStack decidedItem = letterItemDecision.get(letterValue);
                    foundSlot = findExactItem(decidedItem, availableCounts, prioritySlot);
                } else {
                    // First cell with this letter - pick from this letter's filter, or wildcard if unassigned
                    List<ItemStack> acceptedItems = getAcceptedItemsForLetter(letterValue);

                    if (acceptedItems.isEmpty()) {
                        // Letter has no filter assigned: accept any item NOT assigned by other filter slots
                        foundSlot = findWildcardItem(wildcardExclusions, availableCounts, prioritySlot);
                    } else {
                        foundSlot = findSpecificItem(acceptedItems, availableCounts, craftExclusions, prioritySlot);
                    }

                    // Commit this item type for all cells with this letter
                    if (foundSlot >= 0) {
                        letterItemDecision.put(letterValue,
                                inputHandler.getStackInSlot(foundSlot).copyWithCount(1));
                    }
                }

                if (foundSlot == -1) {
                    resolutionFailed = true;
                    break;
                }

                availableCounts[foundSlot]--;
                reservedSlotForCell[cell] = foundSlot;
                craftingGrid.add(inputHandler.getStackInSlot(foundSlot).copyWithCount(1));
            }

            if (resolutionFailed) {
                // No items found for this pattern (e.g. first cell has no valid slot): skip retries and try next pattern
                if (letterItemDecision.isEmpty()) {
                    return false;
                }
                // Exclude decided items and retry with different item choices
                for (ItemStack decided : letterItemDecision.values()) {
                    addDistinctItems(craftExclusions, List.of(decided));
                }
                continue;
            }

            // Create CraftingInput and check vanilla recipe
            CraftingInput craftingInput = CraftingInput.of(3, 3, craftingGrid);
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, craftingInput, level);

            if (recipe.isEmpty()) {
                // No recipe found - exclude these items and retry with different ones
                addDistinctItems(craftExclusions, craftingGrid);
                continue;
            }

            // Filter by this pattern's crafting mode: 0=both, 1=only shaped, 2=only shapeless
            int mode = pattern.getCraftingMode();
            CraftingRecipe recipeValue = recipe.get().value();
            boolean isShaped = recipeValue instanceof ShapedRecipe;
            if (mode == 1 && !isShaped) {
                addDistinctItems(craftExclusions, craftingGrid);
                continue;
            }
            if (mode == 2 && isShaped) {
                addDistinctItems(craftExclusions, craftingGrid);
                continue;
            }

            ItemStack result = recipe.get().value().assemble(craftingInput, level.registryAccess());
            if (result.isEmpty()) {
                addDistinctItems(craftExclusions, craftingGrid);
                continue;
            }

            // Check output filter blacklist
            if (isInOutputFilter(result)) {
                // Blacklisted! Exclude the items that produced this result and retry
                addDistinctItems(craftExclusions, craftingGrid);
                continue;
            }

            // Check if there's space in the output handler
            if (!canInsertIntoOutput(result)) return false; // No space = stop entirely

            // Check if there's enough energy (when capacity is 0, never require or consume - e.g. normal Pattern Crafter)
            int energyCost = getEnergyPerCraft();
            if (getEnergyCapacity() == 0) energyCost = 0;
            if (energyCost > 0 && energyStorage.getEnergyStored() < energyCost) return false;

            // === All checks passed - execute the craft (never consume energy on failure) ===

            // Consume energy only on successful craft when machine has energy (config; 0 for normal)
            if (energyCost > 0) {
                energyStorage.consumeEnergy(energyCost);
            }

            // Consume one item from each reserved input slot
            for (int cell = 0; cell < PatternData.GRID_SIZE; cell++) {
                if (reservedSlotForCell[cell] >= 0) {
                    inputHandler.extractItem(reservedSlotForCell[cell], 1, false);
                }
            }

            // Insert the crafted result into output slots
            insertIntoOutput(result);

            // Handle remainder items (e.g., empty buckets from water bucket recipes)
            NonNullList<ItemStack> remainingItems = recipe.get().value().getRemainingItems(craftingInput);
            for (int cell = 0; cell < remainingItems.size(); cell++) {
                ItemStack remainder = remainingItems.get(cell);
                if (!remainder.isEmpty()) {
                    insertRemainderItem(remainder);
                }
            }

            setChanged();
            return true;
        }
        return false;
    }

    /**
     * Adds all distinct non-empty item types from the crafting grid to the exclusion list.
     */
    private void addDistinctItems(List<ItemStack> exclusions, List<ItemStack> craftingGrid) {
        for (ItemStack gridItem : craftingGrid) {
            if (gridItem.isEmpty()) continue;
            boolean alreadyPresent = false;
            for (ItemStack existing : exclusions) {
                if (ItemStack.isSameItemSameComponents(existing, gridItem)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                exclusions.add(gridItem.copy());
            }
        }
    }

    // ===== Crafting Helpers =====

    /**
     * Checks if a pattern has all grid cells empty.
     */
    private boolean isPatternEmpty(PatternData pattern) {
        for (int i = 0; i < PatternData.GRID_SIZE; i++) {
            if (pattern.getCell(i) != PatternData.EMPTY) return false;
        }
        return true;
    }

    /**
     * Collects all specific (non-wildcard) items from the input filter.
     * These are items in ghost slots that have a letter assigned.
     * Used to build the exclusion set for wildcard matching.
     */
    private List<ItemStack> collectSpecificFilterItems() {
        List<ItemStack> specific = new ArrayList<>();
        for (int i = 0; i < filterLetters.length; i++) {
            if (filterLetters[i] > 0) {
                ItemStack filterItem = inputFilterHandler.getStackInSlot(i);
                if (!filterItem.isEmpty()) {
                    boolean alreadyAdded = false;
                    for (ItemStack existing : specific) {
                        if (ItemStack.isSameItemSameComponents(existing, filterItem)) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) {
                        specific.add(filterItem.copy());
                    }
                }
            }
        }
        return specific;
    }

    /**
     * Gets the list of accepted items for a specific letter.
     * Scans all filter slots with the matching letter that have a ghost item.
     * If the list is empty, it means this letter is a wildcard.
     */
    private List<ItemStack> getAcceptedItemsForLetter(int letterValue) {
        List<ItemStack> accepted = new ArrayList<>();
        for (int i = 0; i < filterLetters.length; i++) {
            if (filterLetters[i] == letterValue) {
                ItemStack filterItem = inputFilterHandler.getStackInSlot(i);
                if (!filterItem.isEmpty()) {
                    accepted.add(filterItem);
                }
            }
        }
        return accepted;
    }

    /**
     * Finds an exact item match in the inputHandler, starting from the given slot (then wrapping).
     * Used when a letter's item type has already been decided.
     * @return slot index, or -1 if not found
     */
    private int findExactItem(ItemStack target, int[] availableCounts, int startSlot) {
        int n = inputHandler.getSlots();
        for (int i = 0; i < n; i++) {
            int slot = (startSlot + i) % n;
            if (availableCounts[slot] <= 0) continue;
            ItemStack slotItem = inputHandler.getStackInSlot(slot);
            if (!slotItem.isEmpty() && ItemStack.isSameItemSameComponents(slotItem, target)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Finds an item in the inputHandler that matches any of the accepted items, starting from startSlot.
     * Skips slots with no available items left, and items in the craft exclusion list.
     * @return slot index, or -1 if not found
     */
    private int findSpecificItem(List<ItemStack> acceptedItems, int[] availableCounts,
                                 List<ItemStack> craftExclusions, int startSlot) {
        int n = inputHandler.getSlots();
        for (int i = 0; i < n; i++) {
            int slot = (startSlot + i) % n;
            if (availableCounts[slot] <= 0) continue;
            ItemStack slotItem = inputHandler.getStackInSlot(slot);
            if (slotItem.isEmpty()) continue;
            // Skip items excluded due to blacklisted results
            if (isItemInList(slotItem, craftExclusions)) continue;
            for (ItemStack accepted : acceptedItems) {
                if (ItemStack.isSameItemSameComponents(slotItem, accepted)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /**
     * Finds any item in the inputHandler that is NOT in the exclusion list, starting from startSlot.
     * Used for wildcard letter matching: "any item except specifically assigned ones
     * and items that led to blacklisted results".
     * @return slot index, or -1 if not found
     */
    private int findWildcardItem(List<ItemStack> excludedItems, int[] availableCounts, int startSlot) {
        int n = inputHandler.getSlots();
        for (int i = 0; i < n; i++) {
            int slot = (startSlot + i) % n;
            if (availableCounts[slot] <= 0) continue;
            ItemStack slotItem = inputHandler.getStackInSlot(slot);
            if (slotItem.isEmpty()) continue;
            if (!isItemInList(slotItem, excludedItems)) return slot;
        }
        return -1;
    }

    /**
     * Checks if an item matches any item in the given list.
     */
    private boolean isItemInList(ItemStack item, List<ItemStack> list) {
        for (ItemStack entry : list) {
            if (ItemStack.isSameItemSameComponents(item, entry)) return true;
        }
        return false;
    }

    /**
     * Checks if the crafted result matches any item in the output filter (blacklist).
     */
    private boolean isInOutputFilter(ItemStack result) {
        for (int i = 0; i < outputFilterHandler.getSlots(); i++) {
            ItemStack filterItem = outputFilterHandler.getStackInSlot(i);
            if (!filterItem.isEmpty() && ItemStack.isSameItemSameComponents(result, filterItem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the result item can be inserted into the output handler (simulate).
     */
    private boolean canInsertIntoOutput(ItemStack result) {
        ItemStack toInsert = result.copy();
        for (int i = 0; i < outputHandler.getSlots(); i++) {
            toInsert = outputHandler.insertItem(i, toInsert, true);
            if (toInsert.isEmpty()) return true;
        }
        return false;
    }

    /**
     * Actually inserts the result item into the output handler.
     */
    private void insertIntoOutput(ItemStack result) {
        ItemStack toInsert = result.copy();
        for (int i = 0; i < outputHandler.getSlots(); i++) {
            toInsert = outputHandler.insertItem(i, toInsert, false);
            if (toInsert.isEmpty()) break;
        }
    }

    /**
     * Inserts a remainder item (e.g., empty bucket) back into the machine.
     * Tries inputHandler first, then outputHandler, then drops on ground.
     */
    private void insertRemainderItem(ItemStack remainder) {
        // Try input handler first
        for (int i = 0; i < inputHandler.getSlots(); i++) {
            remainder = inputHandler.insertItem(i, remainder, false);
            if (remainder.isEmpty()) return;
        }
        // Try output handler
        for (int i = 0; i < outputHandler.getSlots(); i++) {
            remainder = outputHandler.insertItem(i, remainder, false);
            if (remainder.isEmpty()) return;
        }
        // Last resort: drop on ground
        if (!remainder.isEmpty() && level != null) {
            Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5,
                    remainder);
        }
    }

    // ===== NBT Save/Load =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Save input filter slots as item+letter per slot to reduce desync (one atomic unit per slot)
        ListTag inputFilterSlotsTag = new ListTag();
        int filterSlots = inputFilterHandler.getSlots();
        for (int i = 0; i < filterSlots; i++) {
            CompoundTag slotTag = new CompoundTag();
            ItemStack stack = inputFilterHandler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                        .result().ifPresent(nbt -> slotTag.put("item", (CompoundTag) nbt));
            }
            slotTag.putInt("letter", filterLetters[i]);
            inputFilterSlotsTag.add(slotTag);
        }
        tag.put("inputFilterSlots", inputFilterSlotsTag);

        tag.put("outputFilter", outputFilterHandler.serializeNBT(registries));
        tag.put("upgrades", upgradeHandler.serializeNBT(registries));
        tag.put("output", outputHandler.serializeNBT(registries));
        tag.put("input", inputHandler.serializeNBT(registries));

        // Save mark input (slot dedication) filters, same format as iskandert_utilities ghostFilters
        CompoundTag markInputTag = new CompoundTag();
        for (int i = 0; i < markInputFilters.size(); i++) {
            final int slot = i;
            ItemStack filter = markInputFilters.get(slot);
            if (!filter.isEmpty()) {
                ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), filter)
                        .result().ifPresent(nbt -> markInputTag.put("slot" + slot, (CompoundTag) nbt));
            }
        }
        tag.put("markInputFilters", markInputTag);

        // Save energy
        tag.putInt("Energy", energyStorage.getEnergyStored());

        // Save crafting state (crafting mode is per-pattern, saved inside each pattern)
        tag.putInt("craftingTimer", craftingTimer);
        tag.putInt("craftingPatternIndex", craftingPatternIndex);
        tag.putInt("redstoneMode", redstoneMode);
        tag.putBoolean("previousRedstoneState", previousRedstoneState);
        tag.putInt("pulseIgnoreTimer", pulseIgnoreTimer);
        tag.putBoolean("pulseCraftPending", pulseCraftPending);

        // Save patterns
        tag.putInt("currentPattern", currentPatternIndex);
        CompoundTag patternsTag = new CompoundTag();
        patternsTag.putInt("count", patterns.size());
        for (int i = 0; i < patterns.size(); i++) {
            patternsTag.put("pattern_" + i, patterns.get(i).save());
        }
        tag.put("patterns", patternsTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Load input filter slots (item+letter together per slot); fallback to legacy separate tags
        if (tag.contains("inputFilterSlots", Tag.TAG_LIST)) {
            ListTag inputFilterSlotsTag = tag.getList("inputFilterSlots", Tag.TAG_COMPOUND);
            int n = Math.min(inputFilterSlotsTag.size(), Math.min(inputFilterHandler.getSlots(), filterLetters.length));
            for (int i = 0; i < n; i++) {
                CompoundTag slotTag = inputFilterSlotsTag.getCompound(i);
                ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(
                                registries.createSerializationContext(NbtOps.INSTANCE),
                                slotTag.get("item"))
                        .result().orElse(ItemStack.EMPTY);
                inputFilterHandler.setStackInSlot(i, stack);
                if (slotTag.contains("letter")) {
                    filterLetters[i] = slotTag.getInt("letter");
                }
            }
        } else {
            if (tag.contains("inputFilter")) {
                inputFilterHandler.deserializeNBT(registries, tag.getCompound("inputFilter"));
            }
            if (tag.contains("filterLetters")) {
                int[] saved = tag.getIntArray("filterLetters");
                System.arraycopy(saved, 0, filterLetters, 0, Math.min(saved.length, filterLetters.length));
            }
        }
        if (tag.contains("outputFilter")) {
            outputFilterHandler.deserializeNBT(registries, tag.getCompound("outputFilter"));
        }
        if (tag.contains("upgrades")) {
            upgradeHandler.deserializeNBT(registries, tag.getCompound("upgrades"));
        }
        if (tag.contains("output")) {
            outputHandler.deserializeNBT(registries, tag.getCompound("output"));
        }
        if (tag.contains("input")) {
            inputHandler.deserializeNBT(registries, tag.getCompound("input"));
        }

        // Load mark input (slot dedication) filters
        if (tag.contains("markInputFilters")) {
            CompoundTag markInputTag = tag.getCompound("markInputFilters");
            for (int i = 0; i < markInputFilters.size(); i++) {
                String slotKey = "slot" + i;
                if (markInputTag.contains(slotKey)) {
                    ItemStack filter = ItemStack.OPTIONAL_CODEC.parse(
                                    registries.createSerializationContext(NbtOps.INSTANCE),
                                    markInputTag.get(slotKey))
                            .result().orElse(ItemStack.EMPTY);
                    markInputFilters.set(i, filter);
                }
            }
        }

        // Load energy
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }

        // Load crafting state
        if (tag.contains("craftingTimer")) {
            craftingTimer = tag.getInt("craftingTimer");
        }
        if (tag.contains("craftingPatternIndex")) {
            craftingPatternIndex = tag.getInt("craftingPatternIndex");
        }
        if (tag.contains("redstoneMode")) {
            int saved = tag.getInt("redstoneMode");
            redstoneMode = Math.max(0, Math.min(4, saved));
            // Migration: old saves had 3 = disabled; new 3 = pulse, 4 = disabled. If no pulse fields, 3 was disabled.
            if (saved == 3 && !tag.contains("pulseIgnoreTimer")) {
                redstoneMode = 4;
            }
        }
        if (tag.contains("previousRedstoneState")) {
            previousRedstoneState = tag.getBoolean("previousRedstoneState");
        }
        if (tag.contains("pulseIgnoreTimer")) {
            pulseIgnoreTimer = tag.getInt("pulseIgnoreTimer");
        }
        if (tag.contains("pulseCraftPending")) {
            pulseCraftPending = tag.getBoolean("pulseCraftPending");
        }

        // Load patterns
        if (tag.contains("currentPattern")) {
            currentPatternIndex = tag.getInt("currentPattern");
        }
        if (tag.contains("patterns")) {
            CompoundTag patternsTag = tag.getCompound("patterns");
            int count = patternsTag.getInt("count");
            patterns.clear();
            for (int i = 0; i < count; i++) {
                if (patternsTag.contains("pattern_" + i)) {
                    patterns.add(PatternData.load(patternsTag.getCompound("pattern_" + i)));
                } else {
                    patterns.add(new PatternData());
                }
            }
        }
        // Ensure minimum pattern count from config
        int maxPatterns = getMaxPatterns();
        while (patterns.size() < maxPatterns) {
            patterns.add(new PatternData());
        }
        // Clamp indices to valid range (effective count depends on upgrades, clamp to list size here)
        if (currentPatternIndex >= patterns.size()) {
            currentPatternIndex = 0;
        }
        if (craftingPatternIndex >= patterns.size()) {
            craftingPatternIndex = 0;
        }
        // Migration: old saves had a single "craftingMode" at block level; apply to all patterns
        if (tag.contains("craftingMode")) {
            int legacyMode = Math.max(0, Math.min(2, tag.getInt("craftingMode")));
            for (PatternData p : patterns) {
                p.setCraftingMode(legacyMode);
            }
        }
    }

    // ===== Energy Storage Implementation =====

    /**
     * Custom EnergyStorage that exposes setEnergy() for NBT loading.
     */
    public static class EnergyStorageImpl extends EnergyStorage {
        public EnergyStorageImpl(int capacity) {
            super(capacity, capacity, capacity); // maxReceive = maxExtract = capacity
        }

        public void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(energy, capacity));
        }

        /** Consumes exactly the given amount of RF (used when crafting). */
        public void consumeEnergy(int amount) {
            if (amount <= 0) return;
            this.energy = Math.max(0, this.energy - amount);
        }
    }
}
