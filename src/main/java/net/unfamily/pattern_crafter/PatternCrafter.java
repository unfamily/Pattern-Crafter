package net.unfamily.pattern_crafter;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.unfamily.pattern_crafter.block.ModBlocks;
import net.unfamily.pattern_crafter.block.entity.ModBlockEntities;
import net.unfamily.pattern_crafter.client.gui.ModMenuTypes;
import net.unfamily.pattern_crafter.client.gui.ImprovedPatternCrafterScreen;
import net.unfamily.pattern_crafter.item.ModItems;
import net.unfamily.pattern_crafter.network.ModMessages;

@Mod(PatternCrafter.MODID)
public class PatternCrafter {

    public static final String MODID = "pattern_crafter";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PatternCrafter(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);
        modEventBus.addListener(this::registerCapabilities);

        // Register all deferred registers
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        // Register network packet handlers
        modEventBus.register(ModMessages.class);

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Pattern Crafter initialized");
    }

    // Add our items to the Redstone creative tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.PATTERN_CRAFTER.get());
            event.accept(ModItems.IMPROVED_PATTERN_CRAFTER.get());
        }
    }

    // Register capabilities for automation and energy
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Improved Pattern Crafter
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.IMPROVED_PATTERN_CRAFTER_BE.get(),
                (blockEntity, direction) -> blockEntity.getAutomationHandler()
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.IMPROVED_PATTERN_CRAFTER_BE.get(),
                (blockEntity, direction) -> blockEntity.getEnergyStorage()
        );
        // Normal Pattern Crafter (same capabilities; energy may be 0 capacity)
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.PATTERN_CRAFTER_BE.get(),
                (blockEntity, direction) -> blockEntity.getAutomationHandler()
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.PATTERN_CRAFTER_BE.get(),
                (blockEntity, direction) -> blockEntity.getEnergyStorage()
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Pattern Crafter server starting");
    }

    // Client-side event handlers
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.IMPROVED_PATTERN_CRAFTER_MENU.get(), ImprovedPatternCrafterScreen::new);
        }
    }
}
