package net.misemise.ore_picker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.misemise.ore_picker.config.ConfigManager;
import net.misemise.ore_picker.network.HoldC2SPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.CallbackI;

import org.spongepowered.include.org.checkerframework.checker.nullness.qual.Nullable; // ignore if not present

/**
 * Client initializer: registers keybinding, sends C2S payloads, and renders simple HUD above hotbar.
 *
 * Mode behavior:
 *  - "hold": sends pressed state on change (true/false)
 *  - "toggle": sends only pressed=true events; server will toggle on received pressed=true
 */
public class Ore_pickerClient implements ClientModInitializer {
    public static final Identifier HOLD_ID = Identifier.of("orepicker", "hold_vein");

    private KeyBinding holdKey;
    private boolean lastPressed = false;
    private boolean clientToggleState = false; // used when mode == "toggle" for local HUD

    @Override
    public void onInitializeClient() {
        // load client-side config copy
        try { ConfigManager.load(); } catch (Throwable ignored) {}

        holdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orepicker.hold",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.orepicker"
        ));

        // tick: watch key changes and send payload
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (holdKey == null || MinecraftClient.getInstance().player == null) return;
                boolean pressed = holdKey.isPressed();
                String mode = ConfigManager.INSTANCE.mode == null ? "hold" : ConfigManager.INSTANCE.mode;

                if ("toggle".equalsIgnoreCase(mode)) {
                    // on pressed=true edge, flip client local state and send pressed=true to server to indicate toggle
                    if (pressed && !lastPressed) {
                        clientToggleState = !clientToggleState;
                        sendTogglePacket();
                    }
                    // don't send on release
                } else {
                    // hold mode: send both changes
                    if (pressed != lastPressed) {
                        sendHoldPacket(pressed);
                    }
                }
                lastPressed = pressed;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });

        // HUD rendering: draw small indicator above hotbar
        try {
            HudRenderCallback.EVENT.register((DrawContext matrices, float tickDelta) -> {
                try {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc == null || mc.player == null) return;

                    int scaledW = mc.getWindow().getScaledWidth();
                    int scaledH = mc.getWindow().getScaledHeight();
                    // center horizontally, slightly above hotbar
                    String mode = ConfigManager.INSTANCE.mode == null ? "hold" : ConfigManager.INSTANCE.mode;
                    boolean active;
                    if ("toggle".equalsIgnoreCase(mode)) active = clientToggleState;
                    else active = (holdKey != null && holdKey.isPressed());

                    String text = "OrePicker: " + (active ? "ON" : "OFF");
                    int color = active ? 0xFF66FF66 : 0xFFCCCCCC;

                    // compute text width using TextRenderer (take Text.of for safety)
                    Text t = Text.of(text);
                    int textWidth = mc.textRenderer.getWidth(t);

                    int x = scaledW / 2 - textWidth / 2;
                    int y = scaledH - 40; // adjust if needed

                    // Use DrawContext.drawTextWithShadow to render text using the client's TextRenderer
                    matrices.drawTextWithShadow(mc.textRenderer, t, x, y, color);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            });
        } catch (Throwable t) {
            // If HudRenderCallback class/signature differs, fallback to no HUD (still OK)
            t.printStackTrace();
        }
    }

    private void sendHoldPacket(boolean pressed) {
        try {
            HoldC2SPayload payload = new HoldC2SPayload(pressed);
            ClientPlayNetworking.send(payload);
            MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send hold=" + pressed + " -> SENT"), false);
        } catch (Throwable t) {
            t.printStackTrace();
            try { MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send hold=" + pressed + " -> NOT_SENT"), false); } catch (Throwable ignored) {}
        }
    }

    private void sendTogglePacket() {
        try {
            HoldC2SPayload payload = new HoldC2SPayload(true); // server toggles only on pressed=true
            ClientPlayNetworking.send(payload);
            MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send toggle -> SENT"), false);
        } catch (Throwable t) {
            t.printStackTrace();
            try { MinecraftClient.getInstance().player.sendMessage(Text.of("Client: send toggle -> NOT_SENT"), false); } catch (Throwable ignored) {}
        }
    }
}
