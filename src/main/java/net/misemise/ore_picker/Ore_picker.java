package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.misemise.ore_picker.config.ConfigManager;
import net.misemise.ore_picker.network.HoldC2SPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Main initializer
 */
public class Ore_picker implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[OrePicker] onInitialize() start");

        // load config
        ConfigManager.load();

        // register payload codec
        try {
            PayloadTypeRegistry.playC2S().register(HoldC2SPayload.TYPE, HoldC2SPayload.CODEC);
            System.out.println("[OrePicker] Registered HoldC2SPayload type: " + HoldC2SPayload.ID);
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register HoldC2SPayload type: " + t.getMessage());
        }

        // register server receiver
        try {
            ServerPlayNetworking.registerGlobalReceiver(HoldC2SPayload.TYPE, (payload, context) -> {
                boolean pressed = payload.pressed();
                ServerPlayerEntity player = context.player();
                if (player != null) {
                    UUID id = player.getUuid();

                    // mode: hold or toggle
                    String mode = ConfigManager.INSTANCE.mode == null ? "hold" : ConfigManager.INSTANCE.mode;
                    if ("toggle".equalsIgnoreCase(mode)) {
                        // toggle only on pressed=true (client should send pressed=true when toggling)
                        if (pressed) {
                            KeybindHandler.toggleHolding(id);
                            player.sendMessage(Text.of("Hold toggled (server)"), false);
                        }
                    } else {
                        // default: hold mode
                        KeybindHandler.setHolding(id, pressed);
                    }

                    player.sendMessage(Text.of("Hold received (C2S): " + pressed), false);
                    System.out.println("[OrePicker] Received hold=" + pressed + " from " + player.getName().getString());
                }
            });
            System.out.println("[OrePicker] Registered server receiver for HoldC2SPayload");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register server receiver: " + t.getMessage());
        }

        // register MineAll handler for block-break
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
