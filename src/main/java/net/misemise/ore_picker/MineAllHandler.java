package net.misemise.ore_picker;

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
 * Verbose debug 用 MineAllHandler
 * - PlayerBlockBreakEvents.BEFORE から呼ばれるように想定
 * - ログを多めに出して「なぜ一括破壊が起きないか？」を特定する
 */
public class MineAllHandler {

    private static final Set<Block> MINABLE_BLOCKS = Set.of(
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.LAPIS_ORE,
            Blocks.NETHER_QUARTZ_ORE
    );

    // BEFOREイベント用。Fabric の PlayerBlockBreakEvents.BEFORE に渡すシグネチャ
    public static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        try {
            System.out.println("[OrePicker] onBlockBreak invoked: world.isClient=" + world.isClient + ", player=" + (player == null ? "null" : player.getName().getString())
                    + ", pos=" + pos + ", block=" + state.getBlock().getName().getString());

            // 1) 必ずサーバ側で処理（安全のため）
            if (world.isClient) {
                System.out.println("[OrePicker] onBlockBreak: called on client side — ignoring");
                return true;
            }

            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                System.out.println("[OrePicker] onBlockBreak: player is not ServerPlayerEntity — ignoring");
                return true;
            }

            Block broken = state.getBlock();

            // 2) 対象ブロックか
            boolean isTarget = MINABLE_BLOCKS.contains(broken);
            System.out.println("[OrePicker] Block check: " + broken.getName().getString() + " isTarget=" + isTarget);
            if (!isTarget) {
                return true;
            }

            // 3) KeybindHandler に問い合わせる
            boolean holding = KeybindHandler.isHolding(serverPlayer.getUuid());
            System.out.println("[OrePicker] KeybindHandler.isHolding for " + serverPlayer.getUuid() + " => " + holding);

            // If you previously used sneak+hold, and want to require sneak, uncomment next lines:
            // boolean sneak = serverPlayer.isSneaking();
            // System.out.println("[OrePicker] player.isSneaking=" + sneak);
            // if (!(holding && sneak)) return true;

            if (!holding) {
                System.out.println("[OrePicker] Not holding -> normal break");
                return true;
            }

            // 4) Start vein mine (BFS)
            try {
                serverPlayer.sendMessage(Text.of("鉱石一括破壊を開始します: " + broken.getName().getString()), false);
            } catch (Throwable ignore) {}

            System.out.println("[OrePicker] Starting mineAll from " + pos + " target=" + broken.getName().getString());
            mineAll(world, serverPlayer, pos, broken);
            System.out.println("[OrePicker] mineAll finished. returning false to cancel default break");

            // We handled block destruction ourselves (AutoCollectHandler removes blocks), so cancel the normal break
            return false;
        } catch (Throwable t) {
            t.printStackTrace();
            // 何か例外が起きたら安全のため通常処理へ
            return true;
        }
    }

    private static void mineAll(World world, PlayerEntity player, BlockPos origin, Block targetBlock) {
        Queue<BlockPos> toCheck = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        toCheck.add(origin);

        while (!toCheck.isEmpty()) {
            BlockPos pos = toCheck.poll();
            if (visited.contains(pos)) continue;
            visited.add(pos);

            BlockState state = world.getBlockState(pos);
            if (state.getBlock() == targetBlock) {
                System.out.println("[OrePicker] Mining block at " + pos);
                // AutoCollectHandler は既に提供済みのものを使う（ドロップ回収＋XP＋ブロック除去）
                try {
                    AutoCollectHandler.collectDrops(world, player, pos, state);
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                // 近傍を追加
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++)
                            toCheck.add(pos.add(dx, dy, dz));
            }
        }
    }
}
