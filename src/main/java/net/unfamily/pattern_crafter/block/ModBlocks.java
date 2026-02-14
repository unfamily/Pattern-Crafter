package net.unfamily.pattern_crafter.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.pattern_crafter.PatternCrafter;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PatternCrafter.MODID);

    private static final BlockBehaviour.Properties MACHINE_PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops();

    public static final DeferredBlock<ImprovedPatternCrafterBlock> IMPROVED_PATTERN_CRAFTER = BLOCKS.register("improved_pattern_crafter",
            () -> new ImprovedPatternCrafterBlock(MACHINE_PROPERTIES));

    public static final DeferredBlock<PatternCrafterBlock> PATTERN_CRAFTER = BLOCKS.register("pattern_crafter",
            () -> new PatternCrafterBlock(MACHINE_PROPERTIES));

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
