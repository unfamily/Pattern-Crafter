package net.unfamily.pattern_crafter.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.pattern_crafter.Config;

/**
 * Normal Pattern Crafter block entity. Extends Improved and overrides all config
 * and behavior: no energy by default (capacity/perCraft 0), 60 tick interval,
 * no upgrades (max logic/speed 0 → hide bar and dim slots in GUI).
 */
public class PatternCrafterBlockEntity extends ImprovedPatternCrafterBlockEntity {

    public PatternCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PATTERN_CRAFTER_BE.get(), pos, state);
    }

    @Override
    protected int getEnergyCapacity() {
        int cap = getNormalEnergyCapacityRaw();
        int perCraft = getNormalEnergyPerCraftRaw();
        if (cap <= 0 || perCraft <= 0) return 0;
        return cap;
    }

    @Override
    protected int getEnergyPerCraft() {
        int cap = getNormalEnergyCapacityRaw();
        int perCraft = getNormalEnergyPerCraftRaw();
        if (cap <= 0 || perCraft <= 0) return 0;
        return perCraft;
    }

    private int getNormalEnergyCapacityRaw() {
        try {
            return Config.NORMAL_ENERGY_CAPACITY.get();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int getNormalEnergyPerCraftRaw() {
        try {
            return Config.NORMAL_ENERGY_PER_CRAFT.get();
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Override
    protected int getCraftingInterval() {
        try {
            return Config.NORMAL_CRAFTING_INTERVAL.get();
        } catch (Exception ignored) {
            return 30;
        }
    }

    @Override
    protected int getBasePatterns() {
        try {
            return Config.NORMAL_BASE_PATTERNS.get();
        } catch (Exception ignored) {
            return 4;
        }
    }

    @Override
    protected int getMaxPatterns() {
        try {
            return Config.NORMAL_MAX_PATTERNS.get();
        } catch (Exception ignored) {
            return 2;
        }
    }

    @Override
    protected int getMaxLogicModules() {
        try {
            return Config.NORMAL_MAX_LOGIC_MODULES.get();
        } catch (Exception ignored) {
            return 0;
        }
    }

    @Override
    protected int getMaxSpeedModules() {
        try {
            return Config.NORMAL_MAX_SPEED_MODULES.get();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
