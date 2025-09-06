package net.misemise.ore_picker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.misemise.ore_picker.config.ConfigManager;
import net.misemise.ore_picker.network.HoldC2SPayload; // NOTE: this class is now obsolete; keep import removed if you deleted it
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import org.lwjgl.glfw.GLFW;

/**
 * Client initializer: keybind, HUD, and receiver for destroyed count using PacketByteBuf.
 */
public class Ore_pickerClient implements ClientModInitializer {
    public static final Identifier HOLD_ID = Identifier.of("orepicker", "hold_vein");
    public static final Identifier DESTROYED_ID = Identifier.of("orepicker", "destroyed_count");

    private KeyBinding holdKey;
    private boolean lastPressed = false;
    private boolean clientToggleState = false;

    // HUD state
    private static volatile long lastHudShownMs = 0L;
    private static volatile boolean hudVisible = false;
    private static volatile int lastDestroyedCount = 0;

    private static final long SHOW_DURATION_MS = 2000L; // 2 seconds shown
    private static final long FADE_DURATION_MS = 500L;  // 0.5 second fade

    @Override
    public void onInitializeClient() {
        try { ConfigManager.load(); } catch (Throwable ignored) {}

        holdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orepicker.hold",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.orepicker"
        ));

        // S2C receiver: destroyed count
        try {
            ClientPlayNetworking.registerGlobalReceiver(DESTROYED_ID, (client, handler, buf, responseSender) -> {
                try {
                    int cnt = buf.readInt();
                    lastDestroyedCount = cnt;
                    hudShowNow();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // tick: watch key changes and send PacketByteBuf to server
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (holdKey == null || MinecraftClient.getInstance().player == null) return;
                boolean pressed = holdKey.isPressed();
                String mode = ConfigManager.INSTANCE.mode == null ? "hold" : ConfigManager.INSTANCE.mode;

                if ("toggle".equalsIgnoreCase(mode)) {
                    if (pressed && !lastPressed) {
                        clientToggleState = !clientToggleState;
                        sendTogglePacket();
                        hudShowNow();
                    }
                } else {
                    if (pressed != lastPressed) {
                        sendHoldPacket(pressed);
                        hudShowNow();
                    }
                }
                lastPressed = pressed;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        // HUD rendering
        HudRenderCallback.EVENT.register((DrawContext drawContext, RenderTickCounter tickCounter) -> {
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;

                long now = System.currentTimeMillis();
                if (!hudVisible) return;

                long elapsedSinceShow = now - lastHudShownMs;
                float alpha = 1.0f;
                if (elapsedSinceShow <= SHOW_DURATION_MS) {
                    alpha = 1.0f;
                } else if (elapsedSinceShow <= SHOW_DURATION_MS + FADE_DURATION_MS) {
                    float t = (float)(elapsedSinceShow - SHOW_DURATION_MS) / (float)FADE_DURATION_MS;
                    alpha = 1.0f - t;
                } else {
                    hudVisible = false;
                    return;
                }

                int scaledW = mc.getWindow().getScaledWidth();
                int scaledH = mc.getWindow().getScaledHeight();

                String mode = ConfigManager.INSTANCE.mode == null ? "hold" : ConfigManager.INSTANCE.mode;
                boolean active = "toggle".equalsIgnoreCase(mode) ? clientToggleState : (holdKey != null && holdKey.isPressed());

                String text = "OrePicker: " + (active ? "ON" : "OFF") + " (" + lastDestroyedCount + ")";
                int baseColor = active ? 0x66FF66 : 0xCCCCCC; // base rgb
                int alphaByte = Math.max(0, Math.min(255, (int)(alpha * 255f)));
                int color = (alphaByte << 24) | (baseColor & 0x00FFFFFF);

                Text t = Text.of(text);
                int width = mc.textRenderer.getWidth(t);

                int x = scaledW / 2 - width / 2;
                int y = scaledH - 70; // moved upward to avoid hotbar overlay

                drawContext.drawTextWithShadow(mc.textRenderer, t, x, y, color);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    private static void hudShowNow() {
        hudVisible = true;
        lastHudShownMs = System.currentTimeMillis();
    }

    private void sendHoldPacket(boolean pressed) {
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeBoolean(pressed);
            ClientPlayNetworking.send(HOLD_ID, buf);
            MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send hold=" + pressed + " -> SENT"), false);
        } catch (Throwable t) {
            t.printStackTrace();
            try { MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send hold=" + pressed + " -> NOT_SENT"), false); } catch (Throwable ignored) {}
        }
    }

    private void sendTogglePacket() {
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeBoolean(true); // only pressed=true for toggle
            ClientPlayNetworking.send(HOLD_ID, buf);
            MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send toggle -> SENT"), false);
        } catch (Throwable t) {
            t.printStackTrace();
            try { MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send toggle -> NOT_SENT"), false); } catch (Throwable ignored) {}
        }
    }
}
