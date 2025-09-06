package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.misemise.ore_picker.network.HoldC2SPayload;

import java.util.UUID;

/**
 * Main mod initializer — register payload type and server receiver here.
 */
public class Ore_picker implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[OrePicker] onInitialize() registering payload + receiver");

        // 1) register payload type for client->server (must be done before client sends)
        try {
            PayloadTypeRegistry.playC2S().register(HoldC2SPayload.TYPE, HoldC2SPayload.CODEC);
            System.out.println("[OrePicker] Registered HoldC2SPayload type: " + HoldC2SPayload.ID);
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register HoldC2SPayload type: " + t.getMessage());
        }

        // 2) register server receiver using the same TYPE
        try {
            ServerPlayNetworking.registerGlobalReceiver(HoldC2SPayload.TYPE, (payload, context) -> {
                // This runs on the server thread (fabric ensures context execution)
                boolean pressed = payload.pressed();
                ServerPlayerEntity player = context.player();

                if (player != null) {
                    UUID id = player.getUuid();

                    // Use KeybindHandler helper to set holding state
                    if (pressed) {
                        KeybindHandler.setHolding(id, true);
                    } else {
                        KeybindHandler.setHolding(id, false);
                    }

                    // Debug message to player
                    player.sendMessage(Text.of("Hold received (C2S): " + pressed), false);

                    System.out.println("[OrePicker] Received hold=" + pressed + " from " + player.getName().getString());
                }
            });
            System.out.println("[OrePicker] Registered server receiver for HoldC2SPayload");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register server receiver: " + t.getMessage());
        }
    }
}
