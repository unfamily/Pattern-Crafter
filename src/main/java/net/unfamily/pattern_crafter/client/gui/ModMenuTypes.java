package net.unfamily.pattern_crafter.client.gui;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.pattern_crafter.PatternCrafter;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, PatternCrafter.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ImprovedPatternCrafterMenu>> IMPROVED_PATTERN_CRAFTER_MENU =
            MENUS.register("improved_pattern_crafter_menu",
                    () -> IMenuTypeExtension.create(ImprovedPatternCrafterMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
