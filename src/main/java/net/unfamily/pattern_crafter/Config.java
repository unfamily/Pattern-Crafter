package net.unfamily.pattern_crafter;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for Pattern Crafter mod.
 * Categories: normal_pattern_crafter, improved_pattern_crafter.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== Normal Pattern Crafter =====
    static {
        BUILDER.comment("Normal Pattern Crafter (base machine, no energy/upgrades by default)").push("normal_pattern_crafter");
    }

    public static final ModConfigSpec.IntValue NORMAL_MAX_PATTERNS = BUILDER
            .comment("Maximum number of patterns (default: 2)")
            .defineInRange("maxPatterns", 2, 1, 16);
    public static final ModConfigSpec.IntValue NORMAL_CRAFTING_INTERVAL = BUILDER
            .comment("Ticks between each crafting attempt (default: 30, 20 ticks = 1 second)")
            .defineInRange("craftingInterval", 30, 1, 6000);
    public static final ModConfigSpec.IntValue NORMAL_ENERGY_CAPACITY = BUILDER
            .comment("Energy capacity in RF; 0 = no energy, bar hidden (default: 0)")
            .defineInRange("energyCapacity", 0, 0, 1000000);
    public static final ModConfigSpec.IntValue NORMAL_ENERGY_PER_CRAFT = BUILDER
            .comment("RF per craft; if 0 or capacity 0, both treated as 0 (default: 0)")
            .defineInRange("energyPerCraft", 0, 0, 100000);
    public static final ModConfigSpec.IntValue NORMAL_BASE_PATTERNS = BUILDER
            .comment("Base number of patterns with no logic modules (default: 4)")
            .defineInRange("basePatterns", 4, 1, 16);
    public static final ModConfigSpec.IntValue NORMAL_MAX_LOGIC_MODULES = BUILDER
            .comment("Max logic modules in upgrade slot; 0 = no upgrades, slots dimmed (default: 0)")
            .defineInRange("maxLogicModules", 0, 0, 64);
    public static final ModConfigSpec.IntValue NORMAL_MAX_SPEED_MODULES = BUILDER
            .comment("Max speed modules in upgrade slot; 0 = no upgrades (default: 0)")
            .defineInRange("maxSpeedModules", 0, 0, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("Improved Pattern Crafter (energy, upgrades, more patterns)").push("improved_pattern_crafter");
    }

    // ===== Improved Pattern Crafter =====
    public static final ModConfigSpec.IntValue MAX_PATTERNS = BUILDER
            .comment("Maximum number of patterns (default: 7)")
            .defineInRange("maxPatterns", 7, 1, 16);
    public static final ModConfigSpec.IntValue CRAFTING_INTERVAL = BUILDER
            .comment("Ticks between each crafting attempt (default: 20)")
            .defineInRange("craftingInterval", 20, 1, 6000);
    public static final ModConfigSpec.IntValue ENERGY_CAPACITY = BUILDER
            .comment("Energy buffer capacity in RF (default: 1000)")
            .defineInRange("energyCapacity", 1000, 100, 1000000);
    public static final ModConfigSpec.IntValue ENERGY_PER_CRAFT = BUILDER
            .comment("RF consumed per crafting operation (default: 5)")
            .defineInRange("energyPerCraft", 5, 0, 100000);
    public static final ModConfigSpec.IntValue BASE_PATTERNS = BUILDER
            .comment("Base number of patterns with no logic modules (default: 4). Each logic module adds one pattern.")
            .defineInRange("basePatterns", 4, 1, 16);
    public static final ModConfigSpec.IntValue MAX_LOGIC_MODULES = BUILDER
            .comment("Maximum logic modules in upgrade slot (default: 3)")
            .defineInRange("maxLogicModules", 3, 1, 64);
    public static final ModConfigSpec.IntValue MAX_SPEED_MODULES = BUILDER
            .comment("Maximum speed modules in upgrade slot; only the first is used (default: 1)")
            .defineInRange("maxSpeedModules", 1, 1, 64);
    public static final ModConfigSpec.DoubleValue SPEED_MULTIPLIER_SLOW = BUILDER
            .comment("Crafting interval multiplier for slow_module (default: 0.75)")
            .defineInRange("speedMultiplierSlow", 0.75, 0.01, 1.0);
    public static final ModConfigSpec.DoubleValue SPEED_MULTIPLIER_MODERATE = BUILDER
            .comment("Crafting interval multiplier for moderate_module (default: 0.5)")
            .defineInRange("speedMultiplierModerate", 0.5, 0.01, 1.0);
    public static final ModConfigSpec.DoubleValue SPEED_MULTIPLIER_FAST = BUILDER
            .comment("Crafting interval multiplier for fast_module (default: 0.3)")
            .defineInRange("speedMultiplierFast", 0.3, 0.01, 1.0);
    public static final ModConfigSpec.DoubleValue SPEED_MULTIPLIER_EXTREME = BUILDER
            .comment("Crafting interval multiplier for extreme_module (default: 0.15)")
            .defineInRange("speedMultiplierExtreme", 0.15, 0.01, 1.0);
    public static final ModConfigSpec.DoubleValue SPEED_MULTIPLIER_ULTRA = BUILDER
            .comment("Crafting interval multiplier for ultra_module (default: 0.05)")
            .defineInRange("speedMultiplierUltra", 0.05, 0.01, 1.0);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
