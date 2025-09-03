package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class Ore_picker implements ModInitializer {

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient()) return;
            // サーバ側でのみ実行
            if (player instanceof ServerPlayerEntity && world instanceof ServerWorld) {
                MineAllHandler.breakConnectedOres((ServerWorld) world, pos, (ServerPlayerEntity) player);
            }
        });
    }
}
