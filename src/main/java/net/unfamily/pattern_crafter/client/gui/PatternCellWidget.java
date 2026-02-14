package net.unfamily.pattern_crafter.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.unfamily.pattern_crafter.pattern.PatternColors;
import net.unfamily.pattern_crafter.pattern.PatternData;

import java.util.function.Consumer;

/**
 * Custom widget for a pattern grid cell.
 * Displays a colored square with a letter (A-R) or empty.
 * Left-click cycles forward, right-click cycles backward.
 */
public class PatternCellWidget extends AbstractWidget {
    private int value = PatternData.EMPTY;
    private int maxLetter = PatternData.MAX_LETTER; // from BE getMaxKeyInputs(); cycle uses this
    private final int cellIndex;
    private final Consumer<PatternCellWidget> onPress;

    public PatternCellWidget(int x, int y, int width, int height, int cellIndex, Consumer<PatternCellWidget> onPress) {
        super(x, y, width, height, Component.empty());
        this.cellIndex = cellIndex;
        this.onPress = onPress;
    }

    public int getValue() {
        return value;
    }

    public int getCellIndex() {
        return cellIndex;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setMaxLetter(int maxLetter) {
        this.maxLetter = Math.max(1, Math.min(PatternData.MAX_LETTER, maxLetter));
    }

    /**
     * Cycles the value forward: empty -> 1 -> ... -> maxLetter -> empty
     */
    public int cycleForward() {
        value = value >= maxLetter ? PatternData.EMPTY : value + 1;
        return value;
    }

    /**
     * Cycles the value backward: empty -> maxLetter -> ... -> 1 -> empty
     */
    public int cycleBackward() {
        value = value <= PatternData.EMPTY ? maxLetter : value - 1;
        return value;
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1; // Accept left and right click
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.active && this.visible && this.isValidClickButton(button)) {
            if (this.clicked(mouseX, mouseY)) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                if (Screen.hasShiftDown()) {
                    // Shift+click: reset to empty
                    value = PatternData.EMPTY;
                } else if (button == 0) {
                    cycleForward();
                } else if (button == 1) {
                    cycleBackward();
                }
                onPress.accept(this);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bgColor = PatternColors.getColor(value);

        // Draw filled background (inside border)
        guiGraphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bgColor);

        // Draw border
        int borderColor = isHovered ? 0xFFFFFFFF : 0xFF303030;
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);                // top
        guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor); // bottom
        guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);                // left
        guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor); // right

        // Draw letter (or value) centered; 1-26 = A-Z, 27+ = number string
        if (value > PatternData.EMPTY) {
            String label = PatternData.letterValueToDisplayString(value);
            int textColor = PatternColors.getTextColor(value);
            int labelW = Minecraft.getInstance().font.width(label);
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    label,
                    getX() + (width - labelW) / 2,
                    getY() + (height - 8) / 2,
                    textColor,
                    false
            );
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
