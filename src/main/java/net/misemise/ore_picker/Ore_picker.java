package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.misemise.ore_picker.config.ConfigManager;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Simple, robust mod initializer using the standard ServerPlayNetworking.registerGlobalReceiver signature.
 */
public class Ore_picker implements ModInitializer {
    public static final Identifier HOLD_ID = Identifier.of("orepicker", "hold_vein");
    public static final Identifier DESTROYED_ID = Identifier.of("orepicker", "destroyed_count");

    @Override
    public void onInitialize() {
        System.out.println("[OrePicker] onInitialize() start");

        // load config (best-effort)
        try {
            ConfigManager.load();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Register server receiver for simple boolean payload (client -> server).
        // This uses the common signature: (MinecraftServer server, ServerPlayerEntity player,
        // net.minecraft.network.PacketByteBuf buf, ... ) — different Fabric versions may have slight param order,
        // but this is the standard used earlier in your project and should work.
        try {
            ServerPlayNetworking.registerGlobalReceiver(HOLD_ID, (MinecraftServer server, ServerPlayerEntity player, net.minecraft.network.PacketByteBuf buf, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayChannelHandler.PlayChannelContext ctx) -> {
                boolean pressed = false;
                try {
                    pressed = buf.readBoolean();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }

                // ensure handling on server thread
                if (server != null) {
                    server.execute(() -> {
                        try {
                            if (player == null) return;
                            UUID id = player.getUuid();
                            String mode = ConfigManager.INSTANCE.mode == null ? "hold" : ConfigManager.INSTANCE.mode;
                            if ("toggle".equalsIgnoreCase(mode)) {
                                if (pressed) {
                                    KeybindHandler.toggleHolding(id);
                                    try { player.sendMessage(Text.of("Hold toggled (server)"), false); } catch (Throwable ignored) {}
                                }
                            } else {
                                KeybindHandler.setHolding(id, pressed);
                            }
                            try { player.sendMessage(Text.of("Hold received (C2S): " + pressed), false); } catch (Throwable ignored) {}
                            System.out.println("[OrePicker] Received hold=" + pressed + " from " + player.getName().getString());
                        } catch (Throwable inner) {
                            inner.printStackTrace();
                        }
                    });
                }
            });
            System.out.println("[OrePicker] Registered server receiver for HOLD_ID");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register server receiver: " + t.getMessage());
        }

        // Register MineAll handler (BEFORE so we can cancel original break).
        try {
            PlayerBlockBreakEvents.BEFORE.register(MineAllHandler::onBlockBreak);
            System.out.println("[OrePicker] Registered MineAllHandler for PlayerBlockBreakEvents.BEFORE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register MineAllHandler: " + t.getMessage());
        }

        System.out.println("[OrePicker] onInitialize() finished");
    }
}
