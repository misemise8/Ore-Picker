package net.misemise.ore_picker;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class AutoCollectHandler {

    /**
     * ブロック破壊時にドロップを即座にプレイヤーへ回収し、
     * XPは直接プレイヤーへ加算する（オーブは湧かせない）。
     */
    public static void collectDrops(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        ServerPlayerEntity serverPlayer = (player instanceof ServerPlayerEntity) ? (ServerPlayerEntity) player : null;

        // 1) dropStacks を呼んでドロップをワールドへ出す（互換性重視）
        try {
            state.getBlock().dropStacks(state, serverWorld, pos, null, player, player.getMainHandStack());
        } catch (Throwable t) {
            // 例外が出ても先へ進む（近傍回収を試みる）
        }

        // 2) 近傍に湧いた ItemEntity を回収してインベントリへ入れる
        boolean anyInserted = false;
        if (serverPlayer != null) {
            Box box = new Box(pos.getX() - 1.5, pos.getY() - 1.5, pos.getZ() - 1.5,
                    pos.getX() + 1.5, pos.getY() + 1.5, pos.getZ() + 1.5);
            List<ItemEntity> items = serverWorld.getEntitiesByClass(ItemEntity.class, box, e -> true);
            for (ItemEntity ie : items) {
                ItemStack stack = ie.getStack().copy();
                if (stack.isEmpty()) continue;
                boolean inserted = serverPlayer.getInventory().insertStack(stack);
                if (inserted) {
                    anyInserted = true;
                    try {
                        ie.remove(Entity.RemovalReason.DISCARDED);
                    } catch (Throwable ex) {
                        try { ie.discard(); } catch (Throwable ignored) {}
                    }
                }
            }
        }

        // 3) アイテム回収音（少なくとも1スタック入ったら鳴らす）
        if (anyInserted) {
            try {
                serverWorld.playSound(null,
                        player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.2f, 1.0f);
            } catch (Throwable ignored) {}
        }

        // --- 近傍の ExperienceOrbEntity を削除して「オーブが出る」現象を抑制 ---
        try {
            Box orbBox = new Box(pos.getX() - 2.5, pos.getY() - 2.5, pos.getZ() - 2.5,
                    pos.getX() + 2.5, pos.getY() + 2.5, pos.getZ() + 2.5);
            List<ExperienceOrbEntity> orbs = serverWorld.getEntitiesByClass(ExperienceOrbEntity.class, orbBox, e -> true);
            for (ExperienceOrbEntity orb : orbs) {
                try {
                    orb.remove(Entity.RemovalReason.DISCARDED);
                } catch (Throwable ex) {
                    try { orb.discard(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // 4) XP を直接プレイヤーへ与える（オーブを湧かせない）
        int xp = estimateXpForBlock(state.getBlock(), serverWorld);
        if (xp > 0 && serverPlayer != null) {
            try {
                serverPlayer.addExperience(xp);
                try {
                    serverWorld.playSound(null,
                            player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.3f, 1.0f);
                } catch (Throwable ignored) {}
            } catch (Throwable t) {
                // addExperience が存在しない等なら無視
            }
        }

        // 5) ブロックを消す（破壊済みに）
        try {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        } catch (Throwable ignored) {}
    }

    // 簡易XP推定（必要なら調整）
    private static int estimateXpForBlock(net.minecraft.block.Block block, ServerWorld world) {
        Random rnd = new Random();
        if (block == Blocks.COAL_ORE) return rnd.nextInt(3);
        if (block == Blocks.DIAMOND_ORE) return 3 + rnd.nextInt(5);
        if (block == Blocks.LAPIS_ORE) return 2 + rnd.nextInt(4);
        if (block == Blocks.NETHER_QUARTZ_ORE) return 2 + rnd.nextInt(4);
        if (block == Blocks.IRON_ORE || block == Blocks.GOLD_ORE) return 1 + rnd.nextInt(2);
        return 0;
    }
}
