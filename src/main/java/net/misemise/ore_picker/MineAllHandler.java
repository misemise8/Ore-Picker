package net.misemise.ore_picker; // あなたのパッケージに合わせてください

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * 一括破壊ハンドラ（PlayerBlockBreakEvents.BEFORE 用）
 *
 * 注意：
 * - この before ハンドラはサーバ側でのみ呼ばれます（Fabric が保証）
 * - 返り値: true を返すと次のリスナへ進み、最終的に破壊が続行されます。
 *   false を返すと破壊がキャンセルされます（ハンドラ内でブロックを直接消す場合は false を返す）。
 */
public class MineAllHandler {

    // 対象ブロック群（必要なら追加）
    private static final Set<Block> MINABLE_BLOCKS = Set.of(
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.LAPIS_ORE,
            Blocks.NETHER_QUARTZ_ORE
    );

    /**
     * PlayerBlockBreakEvents.Before の関数シグネチャに合わせた公開メソッド。
     * Fabric API のインターフェイスは
     *   boolean beforeBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity)
     * なので、そのまま渡せます（method reference）。
     */
    public static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        // サーバ側だけ処理（Fabric はサーバのみ呼ぶはずだが二重チェック）
        if (world.isClient) return true;

        Block broken = state.getBlock();

        // 1) 対象ブロックかチェック
        if (!MINABLE_BLOCKS.contains(broken)) {
            return true; // 対象外は通常の処理に渡す
        }

        // 2) プレイヤーがサーバプレイヤーかどうか（安全のため）
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return true;
        }

        // 3) キーバインドでホールド中か確認（KeybindHandlerはサーバ側で保持される）
        boolean holding = KeybindHandler.isHolding(serverPlayer.getUuid());

        // 任意：スニーク必須にするなら下のコメントを外す（今はホールド必須のみ）
        // boolean sneak = serverPlayer.isSneaking();
        // if (!(holding && sneak)) return true;

        if (!holding) {
            // ホールドしていなければ通常処理へ
            return true;
        }

        // 4) デバッグメッセージ（クライアントにも見せる）
        try {
            serverPlayer.sendMessage(Text.of("鉱石一括破壊を開始します: " + broken.getName().getString()), false);
        } catch (Throwable ignored) {}

        // 5) 一括破壊（BFSで同種の鉱石を辿る）
        mineAll((World) world, serverPlayer, pos, broken);

        // 6) 元のブロック破壊はキャンセル（自分で AIR にするため）
        return false;
    }

    private static void mineAll(World world, PlayerEntity player, BlockPos origin, Block targetBlock) {
        Queue<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        q.add(origin);

        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            if (visited.contains(p)) continue;
            visited.add(p);

            BlockState s = world.getBlockState(p);
            if (s.getBlock() == targetBlock) {
                // AutoCollectHandler に任せてドロップ回収・XP付与・ブロック消去を行う
                try {
                    AutoCollectHandler.collectDrops(world, player, p, s);
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                // 近傍9x9x9（3x3x3の隣接）を探索
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos np = p.add(dx, dy, dz);
                            if (!visited.contains(np)) q.add(np);
                        }
                    }
                }
            }
        }
    }
}
