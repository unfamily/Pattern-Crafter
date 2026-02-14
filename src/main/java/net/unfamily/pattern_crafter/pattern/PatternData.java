package net.unfamily.pattern_crafter.pattern;

import net.minecraft.nbt.CompoundTag;

/**
 * Represents a single crafting pattern - a 3x3 grid where each cell
 * can be empty (0) or assigned a letter value 1..N (N = max key inputs from config).
 * The effective max is the number of slots (limit by slot); we don't cap below config range.
 * Display: 1-26 = A-Z, 27-52 = a-z, 53-62 = 0-9; beyond that we show the number.
 * Each pattern has its own crafting mode: 0=both, 1=shaped only, 2=shapeless only.
 */
public class PatternData {
    public static final int GRID_SIZE = 9; // 3x3
    public static final int EMPTY = 0;
    public static final int MIN_LETTER = 1;  // A
    /** First tier: A-Z (26). Second: a-z (27-52). Third: 0-9 (53-62). Beyond: raw number. */
    public static final int MAX_LETTER_AZ = 26;
    public static final int MAX_LETTER_az = 52;
    public static final int MAX_LETTER_09 = 62;
    /** Upper bound for storage; matches config max (256). Effective max = min(this, slot count). */
    public static final int MAX_LETTER = 256;

    /** Crafting mode: 0 = Shaped + Shapeless, 1 = Only Shaped, 2 = Only Shapeless */
    public static final int CRAFTING_MODE_BOTH = 0;
    public static final int CRAFTING_MODE_SHAPED_ONLY = 1;
    public static final int CRAFTING_MODE_SHAPELESS_ONLY = 2;

    private final int[] grid = new int[GRID_SIZE];
    /** Default: only shaped (1); user can change to both (0) or shapeless only (2) */
    private int craftingMode = CRAFTING_MODE_SHAPED_ONLY;

    public PatternData() {
        // All cells empty by default
    }

    public int getCraftingMode() {
        return craftingMode;
    }

    public void setCraftingMode(int mode) {
        this.craftingMode = Math.max(0, Math.min(2, mode));
    }

    /** Cycles mode: both -> shaped only -> shapeless only -> both */
    public void cycleCraftingMode() {
        craftingMode = (craftingMode + 1) % 3;
    }

    public int getCell(int index) {
        if (index < 0 || index >= GRID_SIZE) return EMPTY;
        return grid[index];
    }

    public void setCell(int index, int value) {
        if (index < 0 || index >= GRID_SIZE) return;
        if (value < EMPTY || value > MAX_LETTER) value = EMPTY;
        grid[index] = value;
    }

    /**
     * Cycles the cell value: empty -> 1 -> ... -> maxLetter -> empty.
     * @param maxLetter max key input from config (e.g. 18 or 36)
     */
    public int cycleCell(int index, int maxLetter) {
        if (maxLetter < 1) maxLetter = 1;
        if (maxLetter > MAX_LETTER) maxLetter = MAX_LETTER;
        int current = getCell(index);
        int next = current >= maxLetter ? EMPTY : current + 1;
        setCell(index, next);
        return next;
    }

    /**
     * Returns the letter character for a cell value (1-26 = A-Z), or '\0' for empty or out of range.
     */
    public static char valueToChar(int value) {
        if (value < MIN_LETTER || value > MAX_LETTER_AZ) return '\0';
        return (char) ('A' + value - 1);
    }

    /**
     * Display string for letter value: 1-26 = A-Z, 27-52 = a-z, 53-62 = 0-9, 63+ = number.
     */
    public static String letterValueToDisplayString(int value) {
        if (value < MIN_LETTER || value > MAX_LETTER) return "";
        if (value <= MAX_LETTER_AZ) return String.valueOf((char) ('A' + value - 1));
        if (value <= MAX_LETTER_az) return String.valueOf((char) ('a' + value - 27));
        if (value <= MAX_LETTER_09) return String.valueOf((char) ('0' + value - 53));
        return String.valueOf(value);
    }

    /**
     * Returns the cell value for a letter character (A-Z = 1-26).
     */
    public static int charToValue(char c) {
        if (c < 'A' || c > 'Z') return EMPTY;
        return c - 'A' + 1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("grid", grid.clone());
        tag.putInt("craftingMode", craftingMode);
        return tag;
    }

    public static PatternData load(CompoundTag tag) {
        PatternData data = new PatternData();
        if (tag.contains("grid")) {
            int[] saved = tag.getIntArray("grid");
            System.arraycopy(saved, 0, data.grid, 0, Math.min(saved.length, GRID_SIZE));
        }
        if (tag.contains("craftingMode")) {
            data.craftingMode = Math.max(0, Math.min(2, tag.getInt("craftingMode")));
        }
        return data;
    }
}
