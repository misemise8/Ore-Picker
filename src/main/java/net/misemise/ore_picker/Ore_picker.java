package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Ore_picker implements ModInitializer {
    // packet id for client -> server: hold state
    public static final Identifier HOLD_STATE_ID = new Identifier("orepicker", "hold_state");

    // per-player state (true = holding / enabled)
    public static final Map<UUID, Boolean> playerHoldState = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        // load config
        net.misemise.ore_picker.config.ConfigManager.load();

        // register server receiver for hold-state packets
        ServerPlayNetworking.registerGlobalReceiver(HOLD_STATE_ID, (server, player, handler, buf, responseSender) -> {
            boolean hold = buf.readBoolean();
            UUID uuid = player.getUuid();
            if (hold) playerHoldState.put(uuid, true);
            else playerHoldState.put(uuid, false);

            // debug log + send chat to that player on server thread
            server.execute(() -> {
                if (player instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(Text.of("[OrePicker] auto-collect: " + (hold ? "ON" : "OFF")), false);
                    System.out.println("[OrePicker] Received hold state from " + sp.getEntityName() + ": " + hold);
                }
            });
        });

        // register block-break AFTER event (called on server after break completes)
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // only server-side; ensure we use server-side player
            if (!(player instanceof ServerPlayerEntity)) return;
            UUID uuid = player.getUuid();
            boolean enabled = playerHoldState.getOrDefault(uuid, false);

            // check config: mode/toggles/requirements (basic example)
            var cfg = net.misemise.ore_picker.config.ConfigManager.INSTANCE;
            if (cfg != null && "toggle".equalsIgnoreCase(cfg.mode)) {
                // for toggle mode we also check stored state (same map will work)
            }

            if (enabled) {
                // call the existing handler
                try {
                    AutoCollectHandler.collectDrops(world, player, pos, state);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });

        System.out.println("[OrePicker] Initialized");
    }
}
