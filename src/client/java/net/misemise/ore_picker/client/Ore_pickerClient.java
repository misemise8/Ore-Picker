package net.misemise.ore_picker.client;

import com.mojang.blaze3d.platform.InputUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class OrePickerClient implements ClientModInitializer {
    // must match server id
    private static final Identifier HOLD_STATE_ID = new Identifier("orepicker", "hold_state");

    private static KeyBinding holdKey;
    private static boolean lastState = false;

    @Override
    public void onInitializeClient() {
        holdKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orepicker.toggle_hold", // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.orepicker"
        ));

        // tick to monitor key (for hold mode)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null) return;
            boolean pressed = holdKey.isPressed();
            if (pressed != lastState) {
                lastState = pressed;
                sendHoldStateToServer(pressed);
                // optional quick HUD feedback client-side
                if (client.player != null) {
                    client.player.sendMessage(Text.of("[OrePicker] local: " + (pressed ? "ON" : "OFF")), true);
                }
            }
        });

        System.out.println("[OrePicker] Client initialized (V key registered).");
    }

    private void sendHoldStateToServer(boolean pressed) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
        ClientPlayNetworking.send(HOLD_STATE_ID, buf -> buf.writeBoolean(pressed));
    }
}
