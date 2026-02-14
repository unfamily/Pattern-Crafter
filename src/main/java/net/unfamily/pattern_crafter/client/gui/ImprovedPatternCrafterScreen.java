package net.unfamily.pattern_crafter.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.unfamily.pattern_crafter.PatternCrafter;
import net.unfamily.pattern_crafter.network.packet.CraftingModeSwitchC2SPacket;
import net.unfamily.pattern_crafter.network.packet.FilterLetterUpdateC2SPacket;
import net.unfamily.pattern_crafter.network.packet.FilterPageC2SPacket;
import net.unfamily.pattern_crafter.network.packet.MarkInputC2SPacket;
import net.unfamily.pattern_crafter.network.packet.PatternCellUpdateC2SPacket;
import net.unfamily.pattern_crafter.network.packet.PatternSwitchC2SPacket;
import net.unfamily.pattern_crafter.pattern.PatternData;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for the Improved Pattern Crafter.
 * Renders the custom 320x256 GUI background, pattern navigation buttons,
 * a 3x3 pattern grid, filter letter labels, and disabled slot overlays.
 */
public class ImprovedPatternCrafterScreen extends AbstractContainerScreen<ImprovedPatternCrafterMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PatternCrafter.MODID,
                    "textures/gui/backgrounds/pattern_crafter.png");

    private static final ResourceLocation ENERGY_BAR =
            ResourceLocation.fromNamespaceAndPath(PatternCrafter.MODID,
                    "textures/gui/energy_bar.png");

    // Upgrade slot placeholder textures (semi-transparent when slot empty)
    private static final ResourceLocation UPGRADE_LOGIC_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PatternCrafter.MODID,
                    "textures/item/logic_module.png");
    private static final ResourceLocation UPGRADE_SPEED_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PatternCrafter.MODID,
                    "textures/item/fast_module.png");

    /** Redstone button: same graphics as iskandert_utilities. Copy medium_buttons.png, redstone_gui.png from iska_utils into pattern_crafter/textures/gui. */
    private static final ResourceLocation MEDIUM_BUTTONS =
            ResourceLocation.fromNamespaceAndPath(PatternCrafter.MODID, "textures/gui/medium_buttons.png");
    private static final ResourceLocation REDSTONE_GUI =
            ResourceLocation.fromNamespaceAndPath(PatternCrafter.MODID, "textures/gui/redstone_gui.png");

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 256;

    // Energy bar dimensions (from energy_bar.png: 16x32, first 8px = charged, next 8px = empty)
    private static final int ENERGY_BAR_WIDTH = 8;
    private static final int ENERGY_BAR_HEIGHT = 32;

    // Filter label dimensions and positioning
    private static final int FILTER_LABEL_WIDTH = 16;
    private static final int FILTER_LABEL_HEIGHT = 10;
    private static final int FILTER_LABEL_GAP = 1; // Gap between label and slot edge

    // Close button (X) and redstone mode button
    private static final int CLOSE_BUTTON_SIZE = 12;
    private static final int CLOSE_BUTTON_X = GUI_WIDTH - CLOSE_BUTTON_SIZE - 5;
    private static final int CLOSE_BUTTON_Y = 5;
    private static final int REDSTONE_BUTTON_SIZE = 16;
    private static final int REDSTONE_ICON_SIZE = 12;
    private Button closeButton;
    private int redstoneButtonX;
    private int redstoneButtonY;

    // Mark Input button (above upgrades, same behaviour as Structure Placer Filter)
    private Button markInputButton;

    // Crafting mode button (above pattern nav)
    private Button craftingModeButton;

    // Pattern navigation buttons
    private Button prevPatternButton;
    private Button patternLabelButton;
    private Button nextPatternButton;
    private Button savePatternButton;

    /** Filter page: ">" at end of first row of filter slots, "<" below it (only when slot count > 18). */
    private Button prevFilterPageButton;
    private Button nextFilterPageButton;

    /** Pending pattern edits (not sent until Save). null = no pending; applies to pattern at pendingPatternIndex. */
    private int[] pendingGridCells;
    private int pendingPatternIndex = -1;

    // 3x3 pattern grid cells
    private final PatternCellWidget[][] gridCells = new PatternCellWidget[3][3];

    // Filter letter labels: when slot count > 18 we show 18 at a time (pagination); else all
    private PatternCellWidget[] filterLabels = new PatternCellWidget[0];
    /** Current page of input filter slots (0-based); only used when slot count > 18. */
    private int currentFilterPage = 0;
    /** Cooldown after page change to avoid rapid clicks mixing data (ticks). */
    private int filterPageChangeCooldownTicks = 0;
    private static final int FILTER_PAGE_COOLDOWN_TICKS = 3;

    public ImprovedPatternCrafterScreen(ImprovedPatternCrafterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        pendingGridCells = null;
        pendingPatternIndex = -1;

        // Close button (X) - top right, like Fan
        closeButton = Button.builder(Component.literal("✕"),
                        btn -> { if (minecraft != null) minecraft.player.closeContainer(); })
                .bounds(this.leftPos + CLOSE_BUTTON_X, this.topPos + CLOSE_BUTTON_Y, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE)
                .build();
        addRenderableWidget(closeButton);

        // Mark Input button: same width as forbidden outputs (3*18 = 54), 1px left of that area, above upgrades
        int forbiddenOutputsX = 12;
        int forbiddenOutputsW = 3 * 18; // 54
        int markInputButtonH = 12;
        int upgradeFirstY = 211;
        int markInputButtonY = upgradeFirstY - markInputButtonH - 4;
        markInputButton = Button.builder(
                        Component.translatable("gui.pattern_crafter.mark_input"),
                        btn -> onMarkInputPressed())
                .bounds(this.leftPos + forbiddenOutputsX, this.topPos + markInputButtonY, forbiddenOutputsW, markInputButtonH)
                .build();
        addRenderableWidget(markInputButton);

        // Redstone mode button - left of energy bar (same graphics as iskandert_utilities; drawn in render, click in mouseClicked)
        int energyBarX = 49 - ENERGY_BAR_WIDTH - 4;
        int redstoneX = energyBarX - REDSTONE_BUTTON_SIZE - 4;
        int redstoneY = 211 + (36 - ENERGY_BAR_HEIGHT) / 2;
        redstoneButtonX = this.leftPos + redstoneX;
        redstoneButtonY = this.topPos + redstoneY;

        // ===== Pattern navigation and grid: RIGHT side, aligned with output (x=258) =====
        int gridStartX = this.leftPos + 258;
        int gridStartY = this.topPos + 105;
        int gridWidth = 3 * 18; // 54px

        // Order from top: pattern nav [< 1/4 >], then crafting mode (shaped/shapeless for this pattern), then Save
        int navY = gridStartY - 14 - 2 - 14 - 2 - 14 - 2; // 3 rows of 14px + 2px gaps
        int arrowWidth = 12;
        int labelWidth = gridWidth - arrowWidth * 2; // 30px for label

        prevPatternButton = Button.builder(Component.literal("<"),
                        btn -> switchPattern(-1))
                .bounds(gridStartX, navY, arrowWidth, 14)
                .build();
        addRenderableWidget(prevPatternButton);

        patternLabelButton = Button.builder(Component.literal("1/4"),
                        btn -> resetCurrentPattern())
                .bounds(gridStartX + arrowWidth, navY, labelWidth, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.pattern_crafter.reset_pattern_tooltip")))
                .build();
        addRenderableWidget(patternLabelButton);

        nextPatternButton = Button.builder(Component.literal(">"),
                        btn -> switchPattern(1))
                .bounds(gridStartX + arrowWidth + labelWidth, navY, arrowWidth, 14)
                .build();
        addRenderableWidget(nextPatternButton);

        // Crafting mode for current pattern: Shaped/Shapeless (below nav)
        int modeButtonY = navY + 14 + 2;
        craftingModeButton = Button.builder(Component.translatable("gui.pattern_crafter.crafting_mode.both"),
                        btn -> cycleCraftingMode())
                .bounds(gridStartX, modeButtonY, gridWidth, 14)
                .build();
        addRenderableWidget(craftingModeButton);

        // Save button: apply pending pattern edits (below crafting mode)
        int saveButtonY = modeButtonY + 14 + 2;
        savePatternButton = Button.builder(Component.translatable("gui.pattern_crafter.save_pattern"),
                        btn -> savePendingPattern())
                .bounds(gridStartX, saveButtonY, gridWidth, 14)
                .build();
        addRenderableWidget(savePatternButton);

        // 3x3 pattern grid with tooltip
        int cellSize = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cellIndex = row * 3 + col;
                gridCells[row][col] = new PatternCellWidget(
                        gridStartX + col * cellSize,
                        gridStartY + row * cellSize,
                        cellSize, cellSize,
                        cellIndex,
                        this::onGridCellClick
                );
                gridCells[row][col].setTooltip(Tooltip.create(
                        Component.translatable("gui.pattern_crafter.shift_click_clear")
                ));
                addRenderableWidget(gridCells[row][col]);
            }
        }

        // ===== Filter letter labels: when n > 18 we show 18 per page (2 rows); else all n =====
        int filterX = this.leftPos + 79;
        int labelXOffset = (18 - FILTER_LABEL_WIDTH) / 2;
        int n = menu.getInputFilterSlotCount();
        int labelCount = n > 18 ? 18 : n;
        filterLabels = new PatternCellWidget[labelCount];
        for (int i = 0; i < labelCount; i++) {
            int row = i / 9;
            int col = i % 9;
            int labelY = row == 0
                    ? this.topPos + 46 - 1 - FILTER_LABEL_GAP - FILTER_LABEL_HEIGHT
                    : this.topPos + 47 + (row + 1) * 18 + FILTER_LABEL_GAP;
            filterLabels[i] = new PatternCellWidget(
                    filterX + col * 18 + labelXOffset, labelY,
                    FILTER_LABEL_WIDTH, FILTER_LABEL_HEIGHT,
                    i,
                    this::onFilterLabelClick
            );
            addRenderableWidget(filterLabels[i]);
        }

        // Filter page: ">" at end of first row of filter slots, "<" directly below
        if (n > 18) {
            int filterRow1X = this.leftPos + 79;
            int filterRow1Y = this.topPos + 47;
            int slotRowW = 9 * 18; // 162
            int btnW = 12;
            int btnH = 14;
            int btnX = filterRow1X + slotRowW + 2; // 2px after last slot of row 1
            nextFilterPageButton = Button.builder(Component.literal(">"), btn -> setFilterPageFromButton(currentFilterPage + 1))
                    .bounds(btnX, filterRow1Y, btnW, btnH)
                    .build();
            addRenderableWidget(nextFilterPageButton);
            prevFilterPageButton = Button.builder(Component.literal("<"), btn -> setFilterPageFromButton(currentFilterPage - 1))
                    .bounds(btnX, filterRow1Y + 18, btnW, btnH)
                    .build();
            addRenderableWidget(prevFilterPageButton);
        } else {
            prevFilterPageButton = null;
            nextFilterPageButton = null;
        }
    }

    /** Number of filter pages: 18 slots per page; remainder > 0 => +1 page. */
    private int getFilterPageCount() {
        int n = menu.getInputFilterSlotCount();
        if (n <= 18) return 1;
        return (n % 18 == 0) ? (n / 18) : (n / 18 + 1);
    }

    private void setFilterPage(int page) {
        if (filterPageChangeCooldownTicks > 0) return;
        int maxPage = getFilterPageCount() - 1;
        int newPage = Math.max(0, Math.min(maxPage, page));
        if (newPage == currentFilterPage) return;
        // Block writes on the view so container sync (possibly from previous page) doesn't overwrite the new page's slots
        var viewHandler = menu.getInputFilterViewHandler();
        if (viewHandler != null) viewHandler.setAcceptWrites(false);
        currentFilterPage = newPage;
        filterPageChangeCooldownTicks = FILTER_PAGE_COOLDOWN_TICKS;
        menu.setInputFilterViewOffset(currentFilterPage * 18);
        if (menu.getBlockEntity() != null) {
            PacketDistributor.sendToServer(new FilterPageC2SPacket(menu.getBlockEntity().getBlockPos(), currentFilterPage));
        }
    }

    /** Called by filter page prev/next buttons; updates page and plays sound. */
    private void setFilterPageFromButton(int page) {
        int maxPage = getFilterPageCount() - 1;
        int newPage = Math.max(0, Math.min(maxPage, page));
        if (newPage != currentFilterPage) {
            setFilterPage(newPage);
            playButtonSound();
        }
    }

    // ===== Pattern Actions =====

    private void switchPattern(int direction) {
        if (menu.getBlockEntity() == null) return;
        flushPendingPatternToServer();
        PacketDistributor.sendToServer(
                new PatternSwitchC2SPacket(menu.getBlockEntity().getBlockPos(), direction)
        );
    }

    private void resetCurrentPattern() {
        if (!Screen.hasShiftDown()) return;
        if (menu.getBlockEntity() == null) return;
        flushPendingPatternToServer();
        PacketDistributor.sendToServer(
                new PatternSwitchC2SPacket(menu.getBlockEntity().getBlockPos(), 0)
        );
    }

    /** Sends pending grid to server (if any) and clears pending state. */
    private void flushPendingPatternToServer() {
        if (menu.getBlockEntity() == null || pendingGridCells == null) return;
        var pos = menu.getBlockEntity().getBlockPos();
        for (int cell = 0; cell < 9; cell++) {
            PacketDistributor.sendToServer(
                    new PatternCellUpdateC2SPacket(pos, pendingPatternIndex, cell, pendingGridCells[cell])
            );
        }
        pendingGridCells = null;
        pendingPatternIndex = -1;
    }

    private void savePendingPattern() {
        flushPendingPatternToServer();
    }

    private void cycleCraftingMode() {
        if (menu.getBlockEntity() != null) {
            PacketDistributor.sendToServer(
                    new CraftingModeSwitchC2SPacket(menu.getBlockEntity().getBlockPos())
            );
        }
    }

    private void cycleRedstoneMode() {
        if (menu.getBlockEntity() != null) {
            PacketDistributor.sendToServer(
                    new net.unfamily.pattern_crafter.network.packet.RedstoneModeC2SPacket(menu.getBlockEntity().getBlockPos())
            );
        }
    }

    private void onMarkInputPressed() {
        if (menu.getBlockEntity() == null) return;
        int mode = MarkInputC2SPacket.MODE_NORMAL;
        if (Screen.hasShiftDown()) {
            mode = MarkInputC2SPacket.MODE_SHIFT;
        } else if (Screen.hasControlDown() || Screen.hasAltDown()) {
            mode = MarkInputC2SPacket.MODE_CTRL;
        }
        PacketDistributor.sendToServer(new MarkInputC2SPacket(menu.getBlockEntity().getBlockPos(), mode));
    }

    private void onGridCellClick(PatternCellWidget widget) {
        // Store edit in pending; only sent to server when Save is clicked (or when switching pattern)
        int patternIndex = menu.getCurrentPatternIndex();
        if (pendingGridCells == null || pendingPatternIndex != patternIndex) {
            pendingGridCells = new int[9];
            for (int i = 0; i < 9; i++) pendingGridCells[i] = menu.getGridCell(i);
            pendingPatternIndex = patternIndex;
        }
        pendingGridCells[widget.getCellIndex()] = widget.getValue();
    }

    // ===== Filter Label Actions =====

    private void onFilterLabelClick(PatternCellWidget widget) {
        if (menu.getBlockEntity() == null) return;
        int n = menu.getInputFilterSlotCount();
        int filterIndex = n > 18 ? currentFilterPage * 18 + widget.getCellIndex() : widget.getCellIndex();
        if (filterIndex >= n) return;
        PacketDistributor.sendToServer(
                new FilterLetterUpdateC2SPacket(
                        menu.getBlockEntity().getBlockPos(),
                        filterIndex,
                        widget.getValue()
                )
        );
    }

    // ===== Sync from ContainerData =====

    @Override
    protected void containerTick() {
        super.containerTick();

        // Update crafting mode button text
        int mode = menu.getCraftingMode();
        String modeKey = switch (mode) {
            case 1 -> "gui.pattern_crafter.crafting_mode.shaped_only";
            case 2 -> "gui.pattern_crafter.crafting_mode.shapeless_only";
            default -> "gui.pattern_crafter.crafting_mode.both";
        };
        craftingModeButton.setMessage(Component.translatable(modeKey));

        // Update pattern label button text
        int idx = menu.getCurrentPatternIndex();
        int total = menu.getTotalPatterns();
        patternLabelButton.setMessage(Component.literal((idx + 1) + "/" + total));


        // Update grid cells: show pending edits if any for current pattern, else synced data
        int currentIdx = menu.getCurrentPatternIndex();
        boolean usePending = pendingGridCells != null && pendingPatternIndex == currentIdx;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cellIndex = row * 3 + col;
                int value = usePending ? pendingGridCells[cellIndex] : menu.getGridCell(cellIndex);
                gridCells[row][col].setValue(value);
            }
        }

        // Letters A-Z only (26); no limit by slot count
        int maxLetter = PatternData.MAX_LETTER;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                gridCells[row][col].setMaxLetter(maxLetter);
            }
        }
        for (int i = 0; i < filterLabels.length; i++) {
            filterLabels[i].setMaxLetter(maxLetter);
        }

        // When paginated, the menu’s 18 slots are a view over BE; set offset so they show the current page
        int filterCount = menu.getInputFilterSlotCount();
        boolean paginated = filterCount > 18;
        if (filterPageChangeCooldownTicks > 0) {
            filterPageChangeCooldownTicks--;
            if (filterPageChangeCooldownTicks == 0) {
                var viewHandler = menu.getInputFilterViewHandler();
                if (viewHandler != null) viewHandler.setAcceptWrites(true);
            }
        }
        if (paginated) {
            int pageCount = getFilterPageCount();
            currentFilterPage = Math.max(0, Math.min(menu.getSyncedFilterPage(), pageCount - 1));
            menu.setInputFilterViewOffset(currentFilterPage * 18);
        }
        for (int i = 0; i < filterLabels.length; i++) {
            int slotIndex = paginated ? currentFilterPage * 18 + i : i;
            if (slotIndex >= filterCount) {
                filterLabels[i].setValue(0);
                filterLabels[i].setTooltip(null);
                filterLabels[i].visible = false;
                continue;
            }
            if (paginated) filterLabels[i].visible = true;
            int letterValue = menu.getFilterLetter(slotIndex);
            filterLabels[i].setValue(letterValue);

            if (letterValue == 0) {
                filterLabels[i].setTooltip(null);
            } else {
                // Menu has 18 slots (0–17) showing current page via view; slot i = BE slot slotIndex
                ItemStack filterItem = menu.getSlot(ImprovedPatternCrafterMenu.INPUT_FILTER_START + i).getItem();
                if (filterItem.isEmpty()) {
                    filterLabels[i].setTooltip(Tooltip.create(
                            Component.translatable("gui.pattern_crafter.shift_click_clear")
                                    .append(Component.literal("\n"))
                                    .append(Component.translatable("gui.pattern_crafter.filter_any_item"))
                                    .append(Component.literal("\n"))
                                    .append(Component.translatable("gui.pattern_crafter.filter_excluding_others"))
                    ));
                } else {
                    filterLabels[i].setTooltip(Tooltip.create(
                            Component.translatable("gui.pattern_crafter.shift_click_clear")
                                    .append(Component.literal("\n"))
                                    .append(Component.translatable("gui.pattern_crafter.filter_allowed_item",
                                            filterItem.getHoverName()))
                    ));
                }
            }
        }
    }

    // ===== Rendering =====

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, GUI_WIDTH, GUI_HEIGHT);
    }

    /** Input filter slots are already in the background texture; do not draw slot backgrounds. Items drawn in renderInputFilterItemsFromBackend. */
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (slot.index >= ImprovedPatternCrafterMenu.INPUT_FILTER_START && slot.index < menu.getInputFilterEnd()) {
            return;
        }
        super.renderSlot(guiGraphics, slot);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Single visualization: input filter slots are drawn without item in renderSlot; we draw items only from backend here.
        renderInputFilterItemsFromBackend(guiGraphics);

        // Upgrade slots: icons when hasUpgrades(), else dim like disabled filters
        if (menu.getBlockEntity() != null && menu.getBlockEntity().hasUpgrades()) {
            renderUpgradeSlotOverlays(guiGraphics);
        } else if (menu.getBlockEntity() != null) {
            renderDisabledUpgradeOverlays(guiGraphics);
        }

        // Draw dark overlay on disabled input filter slots (letter == 0)
        renderDisabledFilterOverlays(guiGraphics);
        // When paginated with irregular count, obscure slots beyond the actual count (no labels, no interaction)
        renderMissingFilterSlotOverlays(guiGraphics);

        // Ghost items in input slots when empty but have mark-input filter (like Structure Placer)
        renderMarkInputGhosts(guiGraphics);

        // Energy bar: only when machine has energy (capacity > 0; if either capacity or perCraft 0, both 0)
        if (this.menu.getMaxEnergyStored() > 0) {
            renderEnergyBar(guiGraphics);
        }


        // Redstone mode button (same style as iskandert_utilities)
        renderRedstoneModeButton(guiGraphics, mouseX, mouseY);

        if (this.menu.getMaxEnergyStored() > 0) {
            renderEnergyTooltip(guiGraphics, mouseX, mouseY);
        }

        // Redstone button tooltip
        renderRedstoneTooltip(guiGraphics, mouseX, mouseY);

        // Mark Input button tooltip (same style as Structure Placer set_inventory)
        renderMarkInputTooltip(guiGraphics, mouseX, mouseY);

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Renders input filter slot items by reading from the block entity at backend index
     * (currentFilterPage*18 + i when paginated). This avoids desync: like iskandert_utilities
     * DeepDrawerExtractor scrollbar, the visible content is derived from scroll/page + backend,
     * not from container slot sync timing.
     */
    private void renderInputFilterItemsFromBackend(GuiGraphics guiGraphics) {
        var be = menu.getBlockEntity();
        if (be == null) return;
        int n = menu.getInputFilterSlotCount();
        int visible = menu.getInputFilterMenuSlotCount();
        int startX = leftPos + 80;
        int startY = topPos + 47;
        for (int i = 0; i < visible; i++) {
            int backendIndex = (n > 18) ? (currentFilterPage * 18 + i) : i;
            if (backendIndex >= n) continue;
            ItemStack stack = be.getInputFilterHandler().getStackInSlot(backendIndex);
            int col = i % 9;
            int row = i / 9;
            int x = startX + col * 18;
            int y = startY + row * 18;
            guiGraphics.renderItem(stack, x, y);
            guiGraphics.renderItemDecorations(this.font, stack, x, y);
        }
    }

    /** Slight darkening overlay when slot is empty (like iska_utils modular fan). */
    private static final int UPGRADE_EMPTY_OVERLAY = 0x40000000; // ~25% black
    /** Dark overlay when upgrades disabled (max logic/speed both 0), like disabled filter slots. */
    private static final int UPGRADE_DISABLED_OVERLAY = 0xC0101010;

    /**
     * When hasUpgrades() is false: dim the two upgrade slots (no icons).
     */
    private void renderDisabledUpgradeOverlays(GuiGraphics guiGraphics) {
        int slotX = this.leftPos + 49;
        int slotY0 = this.topPos + 211;
        int slotY1 = this.topPos + 229;
        guiGraphics.fill(slotX, slotY0, slotX + 16, slotY0 + 16, UPGRADE_DISABLED_OVERLAY);
        guiGraphics.fill(slotX, slotY1, slotX + 16, slotY1 + 16, UPGRADE_DISABLED_OVERLAY);
    }

    /**
     * Renders placeholder textures in upgrade slots when empty.
     * Position: 1px left and 1px up from slot item area. Applies slight darkening when missing.
     */
    private void renderUpgradeSlotOverlays(GuiGraphics guiGraphics) {
        // Slot item area is at (50, 212) and (50, 230); user asked +1 left and +1 up → (49, 211) and (49, 229)
        int slotX = 49;
        int slotY0 = 211;
        int slotY1 = 229;

        if (menu.getSlot(menu.getUpgradeStart()).getItem().isEmpty()) {
            renderUpgradeGhost(guiGraphics, UPGRADE_LOGIC_TEXTURE, slotX, slotY0);
        }
        if (menu.getSlot(menu.getUpgradeStart() + 1).getItem().isEmpty()) {
            renderUpgradeGhost(guiGraphics, UPGRADE_SPEED_TEXTURE, slotX, slotY1);
        }
    }

    /**
     * Draws placeholder texture at (x,y) then applies slight dark overlay (like FanScreen ghost items).
     */
    private void renderUpgradeGhost(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos + x, this.topPos + y, 0);
        guiGraphics.blit(texture, 0, 0, 0, 0, 16, 16, 16, 16);
        guiGraphics.fill(0, 0, 16, 16, UPGRADE_EMPTY_OVERLAY);
        guiGraphics.pose().popPose();
    }

    /**
     * Renders a semi-transparent dark overlay on input filter slots
     * whose letter assignment is empty (disabled).
     * When slot count > 18, the same 18 positions (first two rows) are recycled for each page.
     */
    private void renderDisabledFilterOverlays(GuiGraphics guiGraphics) {
        int overlayColor = 0xC0101010; // Dark semi-transparent
        int n = menu.getInputFilterSlotCount();
        int start = (n > 18) ? currentFilterPage * 18 : 0;
        int end = (n > 18) ? Math.min(start + 18, n) : n;
        for (int i = start; i < end; i++) {
            if (menu.getFilterLetter(i) == 0) {
                // Always use the same 18-cell zone (rows 0–1): local index 0–17
                int localRow = (i - start) / 9;
                int localCol = (i - start) % 9;
                int slotX = this.leftPos + 80 + localCol * 18;
                int slotY = this.topPos + 47 + localRow * 18;
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, overlayColor);
            }
        }
    }

    /**
     * When paginated with irregular slot count (not a multiple of 18), the last page has "missing" slots.
     * Those positions are fully obscured (no letter labels, no interaction) so they look disabled.
     */
    private void renderMissingFilterSlotOverlays(GuiGraphics guiGraphics) {
        int filterCount = menu.getInputFilterSlotCount();
        if (filterCount <= 18) return;
        int start = currentFilterPage * 18;
        if (start >= filterCount) return;
        int endOnPage = Math.min(start + 18, filterCount);
        if (endOnPage >= start + 18) return; // Full page, no missing slots
        int missingColor = 0xE0101010; // Stronger dark overlay for missing/invalid slots
        for (int localIndex = endOnPage - start; localIndex < 18; localIndex++) {
            int localRow = localIndex / 9;
            int localCol = localIndex % 9;
            int slotX = this.leftPos + 80 + localCol * 18;
            int slotY = this.topPos + 47 + localRow * 18;
            guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, missingColor);
        }
    }

    /**
     * Renders ghost items (semi-transparent) in the 27 input slots when the slot has a mark-input
     * filter but is empty. Same structure as Structure Placer Machine renderGhostItems.
     */
    private void renderMarkInputGhosts(GuiGraphics guiGraphics) {
        for (int slot = 0; slot < 27; slot++) {
            if (menu.hasMarkInputFilter(slot)) {
                net.minecraft.world.inventory.Slot guiSlot = menu.getSlot(menu.getInputStart() + slot);
                if (guiSlot.getItem().isEmpty()) {
                    ItemStack ghostFilter = menu.getMarkInputFilter(slot);
                    if (!ghostFilter.isEmpty()) {
                        renderMarkInputGhostItem(guiGraphics, ghostFilter, guiSlot.x, guiSlot.y);
                    }
                }
            }
        }
    }

    /**
     * Renders a single ghost item (semi-transparent) at the specified slot position.
     * Same as Structure Placer Machine renderGhostItem.
     */
    private void renderMarkInputGhostItem(GuiGraphics guiGraphics, ItemStack itemStack, int x, int y) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.leftPos + x, this.topPos + y, 0);
        guiGraphics.renderItem(itemStack, 0, 0);
        guiGraphics.fill(0, 0, 16, 16, 0x80000000); // 50% transparent black overlay
        guiGraphics.pose().popPose();
    }

    /**
     * Renders the energy bar to the left of the upgrade slots.
     * Texture layout: 16x32 total, left 8px = charged, right 8px = empty.
     * Fills from bottom to top based on current energy percentage.
     */
    private void renderEnergyBar(GuiGraphics guiGraphics) {
        // Position: to the left of upgrade slots (x=49), centered vertically with upgrades (y=211, 2 slots = 36px)
        int energyBarX = this.leftPos + 49 - ENERGY_BAR_WIDTH - 4; // 4px gap left of upgrade frame
        int energyBarY = this.topPos + 211 + (36 - ENERGY_BAR_HEIGHT) / 2; // Vertically centered with upgrades

        // Draw empty bar background (right half of texture: x=8)
        guiGraphics.blit(ENERGY_BAR, energyBarX, energyBarY,
                8, 0,
                ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT,
                16, 32);

        // Draw filled bar from bottom up (left half of texture: x=0)
        int energy = this.menu.getEnergyStored();
        int maxEnergy = this.menu.getMaxEnergyStored();

        if (energy > 0 && maxEnergy > 0) {
            int energyHeight = (energy * ENERGY_BAR_HEIGHT) / maxEnergy;
            int energyY = energyBarY + (ENERGY_BAR_HEIGHT - energyHeight);

            guiGraphics.blit(ENERGY_BAR, energyBarX, energyY,
                    0, ENERGY_BAR_HEIGHT - energyHeight,
                    ENERGY_BAR_WIDTH, energyHeight,
                    16, 32);
        }
    }

    /**
     * Renders the energy tooltip when hovering over the energy bar.
     */
    private void renderEnergyTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int energyBarX = this.leftPos + 49 - ENERGY_BAR_WIDTH - 4;
        int energyBarY = this.topPos + 211 + (36 - ENERGY_BAR_HEIGHT) / 2;

        if (mouseX >= energyBarX && mouseX < energyBarX + ENERGY_BAR_WIDTH
                && mouseY >= energyBarY && mouseY < energyBarY + ENERGY_BAR_HEIGHT) {
            int energy = this.menu.getEnergyStored();
            int maxEnergy = this.menu.getMaxEnergyStored();
            guiGraphics.renderTooltip(this.font,
                    Component.literal(String.format("%,d / %,d RF", energy, maxEnergy)),
                    mouseX, mouseY);
        }
    }

    private void renderRedstoneModeButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean isHovered = mouseX >= redstoneButtonX && mouseX < redstoneButtonX + REDSTONE_BUTTON_SIZE
                && mouseY >= redstoneButtonY && mouseY < redstoneButtonY + REDSTONE_BUTTON_SIZE;
        int textureY = isHovered ? 16 : 0;
        guiGraphics.blit(MEDIUM_BUTTONS, redstoneButtonX, redstoneButtonY,
                0, textureY, REDSTONE_BUTTON_SIZE, REDSTONE_BUTTON_SIZE, 96, 96);

        int iconX = redstoneButtonX + 2;
        int iconY = redstoneButtonY + 2;
        int mode = menu.getRedstoneMode();
        switch (mode) {
            case 0 -> renderScaledItem(guiGraphics, new ItemStack(Items.GUNPOWDER), iconX, iconY, REDSTONE_ICON_SIZE);
            case 1 -> renderScaledItem(guiGraphics, new ItemStack(Items.REDSTONE), iconX, iconY, REDSTONE_ICON_SIZE);
            case 2 -> renderScaledTexture(guiGraphics, REDSTONE_GUI, iconX, iconY, REDSTONE_ICON_SIZE);
            case 3 -> renderScaledItem(guiGraphics, new ItemStack(Items.REPEATER), iconX, iconY, REDSTONE_ICON_SIZE);
            case 4 -> renderScaledItem(guiGraphics, new ItemStack(Items.BARRIER), iconX, iconY, REDSTONE_ICON_SIZE);
            default -> {}
        }
    }

    private void renderScaledItem(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int size) {
        guiGraphics.pose().pushPose();
        float scale = (float) size / 16.0f;
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.renderItem(stack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private void renderScaledTexture(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int size) {
        guiGraphics.pose().pushPose();
        float scale = (float) size / 16.0f;
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.blit(texture, 0, 0, 0, 0, 16, 16, 16, 16);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= redstoneButtonX && mouseX < redstoneButtonX + REDSTONE_BUTTON_SIZE
                && mouseY >= redstoneButtonY && mouseY < redstoneButtonY + REDSTONE_BUTTON_SIZE) {
            playButtonSound();
            cycleRedstoneMode();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playButtonSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void renderRedstoneTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (mouseX >= redstoneButtonX && mouseX < redstoneButtonX + REDSTONE_BUTTON_SIZE
                && mouseY >= redstoneButtonY && mouseY < redstoneButtonY + REDSTONE_BUTTON_SIZE) {
            guiGraphics.renderTooltip(this.font,
                    Component.translatable("gui.pattern_crafter.redstone_mode." + menu.getRedstoneMode()),
                    mouseX, mouseY);
        }
    }

    private void renderMarkInputTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (markInputButton != null && markInputButton.isHovered()) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.pattern_crafter.mark_input.tooltip.line1"));
            lines.add(Component.translatable("gui.pattern_crafter.mark_input.tooltip.line2"));
            lines.add(Component.translatable("gui.pattern_crafter.mark_input.tooltip.line3"));
            guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title centered at top of GUI
        int titleW = this.font.width(this.title);
        int titleX = (this.imageWidth - titleW) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, 6, 4210752, false);

        // "Forbidden Outputs" above output filter at 70% scale
        Component forbiddenLabel = Component.translatable("gui.pattern_crafter.forbidden_outputs");
        int labelW = this.font.width(forbiddenLabel);
        int centerX = 13 + (3 * 18) / 2;
        int labelX = centerX - (int) (labelW * 0.35f); // scaled width is labelW*0.7, so left = center - (labelW*0.7)/2
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(labelX, 53, 0);
        guiGraphics.pose().scale(0.7f, 0.7f, 1f);
        guiGraphics.drawString(this.font, forbiddenLabel, 0, 0, 4210752, false);
        guiGraphics.pose().popPose();
    }
}
