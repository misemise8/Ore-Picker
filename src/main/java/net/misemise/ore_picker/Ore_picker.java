package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.misemise.ore_picker.network.HoldC2SPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main mod initializer (server-side).
 * - Manages per-player hold state
 * - Receives HoldC2SPayload via ServerPlayNetworking.registerGlobalReceiver(payloadType, handler)
 *
 * Note: handler signature is (payload, context) where context provides context.player().
 * The handler is invoked on the server thread according to Fabric docs, so world/block ops are safe here.
 */
public class Ore_picker implements ModInitializer {
    // Player UUID -> holding flag
    private static final Map<UUID, Boolean> HOLDING = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        try {
            ServerPlayNetworking.registerGlobalReceiver(
                    HoldC2SPayload.TYPE,
                    (HoldC2SPayload payload, ServerPlayNetworking.Context context) -> {
                        // According to Fabric API the handler runs on server thread already.
                        ServerPlayerEntity player = context.player();
                        if (player == null) return;

                        boolean holding = payload.hold();
                        if (holding) {
                            HOLDING.put(player.getUuid(), true);
                        } else {
                            HOLDING.remove(player.getUuid());
                        }

                        // optional debug chat for the player
                        try {
                            player.sendMessage(Text.of("Hold received (C2S): " + holding), false);
                        } catch (Throwable ignored) {}
                    }
            );
        } catch (Throwable t) {
            // If registration fails for any reason, log the stacktrace
            t.printStackTrace();
        }
    }

    /** Utility for other server-side classes to check if a player is holding. */
    public static boolean isHolding(ServerPlayerEntity player) {
        if (player == null) return false;
        return HOLDING.getOrDefault(player.getUuid(), false);
    }
}
