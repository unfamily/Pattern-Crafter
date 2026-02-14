package net.unfamily.pattern_crafter.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.unfamily.pattern_crafter.PatternCrafter;
import net.unfamily.pattern_crafter.network.packet.CraftingModeSwitchC2SPacket;
import net.unfamily.pattern_crafter.network.packet.FilterLetterUpdateC2SPacket;
import net.unfamily.pattern_crafter.network.packet.MarkInputC2SPacket;
import net.unfamily.pattern_crafter.network.packet.PatternCellUpdateC2SPacket;
import net.unfamily.pattern_crafter.network.packet.PatternSwitchC2SPacket;
import net.unfamily.pattern_crafter.network.packet.RedstoneModeC2SPacket;

/**
 * Registers all network packets for the Pattern Crafter mod.
 */
public class ModMessages {

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PatternCrafter.MODID).versioned("1");

        // Pattern cell update (client -> server)
        registrar.playToServer(
                PatternCellUpdateC2SPacket.TYPE,
                PatternCellUpdateC2SPacket.STREAM_CODEC,
                PatternCellUpdateC2SPacket::handle
        );

        // Pattern switch (client -> server)
        registrar.playToServer(
                PatternSwitchC2SPacket.TYPE,
                PatternSwitchC2SPacket.STREAM_CODEC,
                PatternSwitchC2SPacket::handle
        );

        // Filter letter update (client -> server)
        registrar.playToServer(
                FilterLetterUpdateC2SPacket.TYPE,
                FilterLetterUpdateC2SPacket.STREAM_CODEC,
                FilterLetterUpdateC2SPacket::handle
        );

        // Crafting mode switch (client -> server)
        registrar.playToServer(
                CraftingModeSwitchC2SPacket.TYPE,
                CraftingModeSwitchC2SPacket.STREAM_CODEC,
                CraftingModeSwitchC2SPacket::handle
        );

        // Redstone mode cycle (client -> server)
        registrar.playToServer(
                RedstoneModeC2SPacket.TYPE,
                RedstoneModeC2SPacket.STREAM_CODEC,
                RedstoneModeC2SPacket::handle
        );

        // Mark Input (set/clear input filters, like Structure Placer Filter button)
        registrar.playToServer(
                MarkInputC2SPacket.TYPE,
                MarkInputC2SPacket.STREAM_CODEC,
                MarkInputC2SPacket::handle
        );
    }
}
