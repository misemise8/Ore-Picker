package net.misemise.ore_picker;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * OrePicker 用 MineAllHandler
 * - public static breakConnectedOres(ServerWorld, BlockPos, ServerPlayerEntity) を提供
 * - 既存の onBlockBreak(…) も残してある（BEFORE イベントで使えます）
 * - AutoCollectHandler.collectDrops(...) を呼び出してドロップ回収＋XP付与を行います
 */
public class MineAllHandler {

    private static final Set<Block> MINABLE_BLOCKS = Set.of(
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.COPPER_ORE,
            Blocks.LAPIS_ORE,
            Blocks.REDSTONE_ORE,
            Blocks.EMERALD_ORE
    );

    // 保護用：一括で壊す最大ブロック数
    private static final int MAX_BLOCKS = 256;

    /**
     * あなたが以前使っていた BEFORE イベント向けメソッド（元コード互換）
     * true を返すと通常の破壊処理を続行、false を返すとキャンセル
     */
    public static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity _blockEntity) {
        if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return true; // クライアント側または非サーバプレイヤーなら通常処理
        }

        Block brokenBlock = state.getBlock();
        if (MINABLE_BLOCKS.contains(brokenBlock)) {
            // プレイヤー通知（任意）
            try {
                serverPlayer.sendMessage(Text.of("鉱石を壊した！: " + brokenBlock.getName().getString()), false);
            } catch (Throwable ignored) {}

            // 実際の一括破壊処理（public メソッドを使う）
            breakConnectedOres(serverWorld, pos, serverPlayer);
            return false; // 元の破壊処理はキャンセル（こちらで処理済み）
        }

        return true;
    }

    /**
     * 既存コードが呼んでいたメソッド（コンパイルエラーの対象）
     * ServerWorld / ServerPlayerEntity を受け取る形に合わせています。
     */
    public static void breakConnectedOres(ServerWorld world, BlockPos startPos, ServerPlayerEntity player) {
        BlockState startState = world.getBlockState(startPos);
        Block startBlock = startState.getBlock();

        if (!MINABLE_BLOCKS.contains(startBlock)) return;

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(startPos);
        visited.add(startPos);

        int brokenCount = 0;

        while (!queue.isEmpty() && brokenCount < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            BlockState state = world.getBlockState(pos);

            if (state.getBlock() == startBlock) {
                // ドロップ回収 + XP 付与 + ブロック削除を AutoCollectHandler に委譲
                try {
                    AutoCollectHandler.collectDrops(world, player, pos, state);
                } catch (Throwable ignored) {}

                brokenCount++;

                // 近傍 3x3x3 を探査（既存コードの挙動に合わせる）
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos neighbor = pos.add(dx, dy, dz);
                            if (!visited.contains(neighbor)) {
                                visited.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
    }
}
