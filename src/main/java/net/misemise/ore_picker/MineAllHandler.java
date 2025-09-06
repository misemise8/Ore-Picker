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
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.reflect.Method;
import java.util.*;

/**
 * MineAll handler that sends destroyed count back to client using reflection to call ServerPlayNetworking.send variants.
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

    public static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        try {
            if (world.isClient) return true;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return true;

            ConfigManager.Config cfg = ConfigManager.INSTANCE;
            int maxBlocks = Math.max(1, cfg.maxBlocks);

            Block blk = state.getBlock();

            List<String> list = cfg.minableList == null ? Collections.emptyList() : cfg.minableList;
            boolean inList = false;
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
                    t.printStackTrace();
                }
            }

            boolean isTarget = inList || MINABLE_BLOCKS.contains(blk);
            if (!isTarget) return true;

            if (cfg.requireSneak && !serverPlayer.isSneaking()) return true;
            if (cfg.requireTool && serverPlayer.getMainHandStack().isEmpty()) return true;

            boolean holding = KeybindHandler.isHolding(serverPlayer.getUuid());
            if (!holding) return true;

            serverPlayer.sendMessage(Text.of("鉱石一括破壊を開始します（最大 " + maxBlocks + "）"), false);
            int destroyed = mineAllWithLimit(world, serverPlayer, pos, maxBlocks, blk);
            serverPlayer.sendMessage(Text.of("破壊した鉱石数: " + destroyed), false);
            System.out.println("[OrePicker] mineAll finished for " + serverPlayer.getName().getString() + " destroyed=" + destroyed);

            // Try to send destroyed count back to client using reflection, trying common ServerPlayNetworking.send signatures.
            try {
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                buf.writeInt(destroyed);
                trySendDestroyedCount(serverPlayer, buf);
            } catch (Throwable t) {
                t.printStackTrace();
            }

            return false;
        } catch (Throwable t) {
            t.printStackTrace();
            return true;
        }
    }

    private static void trySendDestroyedCount(ServerPlayerEntity player, PacketByteBuf buf) {
        try {
            Class<?> spn = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
            Method[] methods = spn.getMethods();
            boolean sent = false;

            // First: try (ServerPlayerEntity, Identifier, PacketByteBuf)
            for (Method m : methods) {
                if (!m.getName().equals("send")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 3) {
                    try {
                        // construct Identifier reflectively
                        Class<?> idClass = Class.forName("net.minecraft.util.Identifier");
                        Object id = idClass.getConstructor(String.class, String.class).newInstance("orepicker", "destroyed_count");
                        // check param compatibility
                        if (pts[0].isAssignableFrom(player.getClass()) &&
                                pts[1].isAssignableFrom(id.getClass()) &&
                                pts[2].isAssignableFrom(buf.getClass())) {
                            m.invoke(null, player, id, buf);
                            sent = true;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (!sent) {
                // Next: try (ServerPlayerEntity, PacketByteBuf)
                for (Method m : methods) {
                    if (!m.getName().equals("send")) continue;
                    Class<?>[] pts = m.getParameterTypes();
                    if (pts.length == 2) {
                        try {
                            if (pts[0].isAssignableFrom(player.getClass()) && pts[1].isAssignableFrom(buf.getClass())) {
                                m.invoke(null, player, buf);
                                sent = true;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (!sent) {
                System.err.println("[OrePicker] Unable to send destroyed-count via ServerPlayNetworking (no compatible send method found).");
            }
        } catch (Throwable t) {
            t.printStackTrace();
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
        return destroyed;
    }

    private static String inferBlockIdFromTranslationKey(String translationKey) {
        if (translationKey == null) return null;
        String prefix = "block.";
        if (!translationKey.startsWith(prefix)) return null;
        String rest = translationKey.substring(prefix.length());
        int dot = rest.indexOf('.');
        if (dot <= 0) return null;
        String namespace = rest.substring(0, dot);
        String path = rest.substring(dot + 1);
        return namespace + ":" + path;
    }
}
