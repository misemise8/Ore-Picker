package net.misemise.ore_picker.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.misemise.ore_picker.network.HoldC2SPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Client initializer: registers keybind and sends HoldC2SPayload when press state changes.
 * Uses direct ClientPlayNetworking API (preferred, simpler and robust when fabric-api is present).
 */
public class Ore_pickerClient implements ClientModInitializer {
    private KeyBinding holdKey;
    private boolean lastPressed = false;

    // 定期再送はここでは無し（キー変化で送る）。必要なら短周期で再送するよう拡張可能。
    @Override
    public void onInitializeClient() {
        holdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orepicker.hold",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.orepicker"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (holdKey == null || MinecraftClient.getInstance().player == null) return;
                boolean pressed = holdKey.isPressed();
                if (pressed != lastPressed) {
                    lastPressed = pressed;

                    // make payload and send
                    HoldC2SPayload payload = new HoldC2SPayload(pressed);
                    boolean sent = false;
                    try {
                        ClientPlayNetworking.send(payload);
                        sent = true;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        sent = false;
                    }

                    try {
                        MinecraftClient.getInstance().player.sendMessage(
                                net.minecraft.text.Text.of("Client: send hold=" + pressed + " -> " + (sent ? "SENT" : "NOT_SENT")), false);
                    } catch (Throwable ignore) {}
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }
}
