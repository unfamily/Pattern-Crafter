package net.unfamily.pattern_crafter.block.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.pattern_crafter.PatternCrafter;
import net.unfamily.pattern_crafter.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PatternCrafter.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ImprovedPatternCrafterBlockEntity>> IMPROVED_PATTERN_CRAFTER_BE =
            BLOCK_ENTITIES.register("improved_pattern_crafter",
                    () -> BlockEntityType.Builder.of(ImprovedPatternCrafterBlockEntity::new,
                            ModBlocks.IMPROVED_PATTERN_CRAFTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PatternCrafterBlockEntity>> PATTERN_CRAFTER_BE =
            BLOCK_ENTITIES.register("pattern_crafter",
                    () -> BlockEntityType.Builder.of(PatternCrafterBlockEntity::new,
                            ModBlocks.PATTERN_CRAFTER.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
