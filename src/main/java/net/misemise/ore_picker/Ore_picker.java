package net.misemise.ore_picker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.misemise.ore_picker.network.HoldC2SPayload;

import java.util.UUID;

/**
 * Main initializer - payload 型登録、サーバ受信、block-break イベント登録 をここで行う
 */
public class Ore_picker implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[OrePicker] onInitialize() start");

        // 1) payload 型を登録（client->server）
        try {
            PayloadTypeRegistry.playC2S().register(HoldC2SPayload.TYPE, HoldC2SPayload.CODEC);
            System.out.println("[OrePicker] Registered HoldC2SPayload type: " + HoldC2SPayload.ID);
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register HoldC2SPayload type: " + t.getMessage());
        }

        // 2) サーバ受信ハンドラを登録（payload を受けて KeybindHandler を更新）
        try {
            ServerPlayNetworking.registerGlobalReceiver(HoldC2SPayload.TYPE, (payload, context) -> {
                boolean pressed = payload.pressed();
                ServerPlayerEntity player = context.player();
                if (player != null) {
                    UUID id = player.getUuid();

                    // KeybindHandler に状態をセット（setHolding は KeybindHandler に実装済みのこと）
                    KeybindHandler.setHolding(id, pressed);

                    // デバッグ表示
                    player.sendMessage(Text.of("Hold received (C2S): " + pressed), false);
                    System.out.println("[OrePicker] Received hold=" + pressed + " from " + player.getName().getString());
                }
            });
            System.out.println("[OrePicker] Registered server receiver for HoldC2SPayload");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register server receiver: " + t.getMessage());
        }

        // 3) **ここが重要**: ブロック破壊時の BEFORE イベントに MineAllHandler を登録する
        //    これでプレイヤーがブロックを壊すたびに MineAllHandler.onBlockBreak が呼ばれます。
        try {
            PlayerBlockBreakEvents.BEFORE.register(MineAllHandler::onBlockBreak);
            System.out.println("[OrePicker] Registered MineAllHandler for PlayerBlockBreakEvents.BEFORE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.err.println("[OrePicker] Failed to register MineAllHandler: " + t.getMessage());
        }

        System.out.println("[OrePicker] onInitialize() finished");
    }
}
