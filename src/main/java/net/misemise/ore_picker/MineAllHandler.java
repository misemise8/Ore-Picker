package net.misemise.ore_picker;

import net.misemise.ore_picker.config.ConfigManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * MineAll handler:
 * - config.minableList を優先して判定（"namespace:path" 形式）
 * - その次に MINABLE_BLOCKS のハードコード集合をフォールバック
 * - キーバインド状態を確認して一括破壊（ConfigManager.INSTANCE.maxBlocks を上限）
 *
 * ここではタグ API を直接使わず、移植性を優先しています。
 */
public class MineAllHandler {

    // フォールバック用の代表的な鉱石セット（必要ならここに追加）
    private static final Set<Block> MINABLE_BLOCKS = Set.of(
            Blocks.COAL_ORE,
            Blocks.IRON_ORE,
            Blocks.GOLD_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.LAPIS_ORE,
            Blocks.NETHER_QUARTZ_ORE
    );

    public static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        try {
            if (world.isClient) return true;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;

            ConfigManager.Config cfg = ConfigManager.INSTANCE;
            int maxBlocks = Math.max(1, cfg.maxBlocks);

            Block blk = state.getBlock();

            // 1) try check using config.minableList (strings like "minecraft:coal_ore")
            boolean inList = false;
            List<String> list = cfg.minableList == null ? Collections.emptyList() : cfg.minableList;
            if (!list.isEmpty()) {
                try {
                    String blockId = inferBlockIdFromTranslationKey(blk.getTranslationKey());
                    if (blockId != null) {
                        for (String allowed : list) {
                            if (allowed != null && allowed.equalsIgnoreCase(blockId)) {
                                inList = true;
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    // ignore and fall through to block set
                    t.printStackTrace();
                }
            }

            // 2) fallback to built-in block set
            boolean isTarget = inList || MINABLE_BLOCKS.contains(blk);

            if (!isTarget) return true;

            // 3) optional checks
            if (cfg.requireSneak && !serverPlayer.isSneaking()) return true;
            if (cfg.requireTool && serverPlayer.getMainHandStack().isEmpty()) return true;

            // 4) keybind check
            boolean holding = KeybindHandler.isHolding(serverPlayer.getUuid());
            if (!holding) return true;

            // 5) perform vein-mine with limit
            serverPlayer.sendMessage(Text.of("鉱石一括破壊を開始します（最大 " + maxBlocks + "）"), false);
            int destroyed = mineAllWithLimit(world, serverPlayer, pos, maxBlocks, blk);
            serverPlayer.sendMessage(Text.of("破壊した鉱石数: " + destroyed), false);
            System.out.println("[OrePicker] mineAll finished for " + serverPlayer.getName().getString() + " destroyed=" + destroyed);

            return false;
        } catch (Throwable t) {
            t.printStackTrace();
            return true;
        }
    }

    private static int mineAllWithLimit(World world, PlayerEntity player, BlockPos origin, int maxBlocks, Block targetBlock) {
        Queue<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        q.add(origin);
        int destroyed = 0;

        while (!q.isEmpty() && destroyed < maxBlocks) {
            BlockPos p = q.poll();
            if (visited.contains(p)) continue;
            visited.add(p);

            BlockState s = world.getBlockState(p);
            if (s.getBlock() == targetBlock) {
                try {
                    AutoCollectHandler.collectDrops(world, player, p, s);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                destroyed++;
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos np = p.add(dx, dy, dz);
                            if (!visited.contains(np)) q.add(np);
                        }
            }
        }
        return destroyed;
    }

    /**
     * トランスレーションキーから "namespace:path" 形式の推測IDを返す。
     * 例: "block.minecraft.coal_ore" -> "minecraft:coal_ore"
     *   (失敗時は null を返す)
     */
    private static String inferBlockIdFromTranslationKey(String translationKey) {
        if (translationKey == null) return null;
        // expected pattern: "block.<namespace>.<path>"
        String prefix = "block.";
        if (!translationKey.startsWith(prefix)) return null;
        String rest = translationKey.substring(prefix.length()); // e.g. "minecraft.coal_ore"
        int dot = rest.indexOf('.');
        if (dot <= 0) return null;
        String namespace = rest.substring(0, dot);
        String path = rest.substring(dot + 1);
        // join with first dot -> namespace:path
        return namespace + ":" + path;
    }
}
