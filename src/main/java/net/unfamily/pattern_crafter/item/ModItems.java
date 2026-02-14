package net.unfamily.pattern_crafter.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.pattern_crafter.PatternCrafter;
import net.unfamily.pattern_crafter.block.ModBlocks;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PatternCrafter.MODID);

    private static final Item.Properties ITEM_PROPERTIES = new Item.Properties();

    public static final DeferredItem<Item> IMPROVED_PATTERN_CRAFTER = ITEMS.register("improved_pattern_crafter",
            () -> new BlockItem(ModBlocks.IMPROVED_PATTERN_CRAFTER.get(), ITEM_PROPERTIES));

    public static final DeferredItem<Item> PATTERN_CRAFTER = ITEMS.register("pattern_crafter",
            () -> new BlockItem(ModBlocks.PATTERN_CRAFTER.get(), ITEM_PROPERTIES));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
