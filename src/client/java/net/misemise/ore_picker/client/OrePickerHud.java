package net.misemise.ore_picker.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * HUD: "ホールド中..." をツールバーの上に表示し、
 * ホールド解除で 2 秒かけて徐々に透明にフェードアウトする。
 *
 * クライアント初期化時に OrePickerHud.register() を呼んでください。
 */
public class OrePickerHud {
    private static volatile boolean holding = false;
    private static volatile int fadeTicks = 0;
    private static volatile int destroyedCount = 0;
    private static final int FADE_DURATION = 40; // ticks (40 ≒ 2秒)

    public static void onToggle(boolean hold) {
        holding = hold;
        // 押下でも解除でもフェード値をリセットして表示状態を安定させる
        fadeTicks = FADE_DURATION;
    }

    public static void onDestroyedCount(int count) {
        destroyedCount = count;
    }

    /** client 初期化時に一度だけ呼ぶ */
    public static void register() {
        // 注意：第二引数は RenderTickCounter 型になっているためそちらを受け取るラムダを渡す
        HudRenderCallback.EVENT.register((context, renderTickCounter) -> render(context, renderTickCounter));
    }

    /** 実際の描画。RenderTickCounter を受け取る形に合わせる */
    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        // 非表示条件
        if (!holding && fadeTicks <= 0) return;

        String text = "ホールド中... (" + destroyedCount + ")";

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // ホットバー上に表示する位置（微調整可）
        int hotbarOffset = 22;
        int y = screenH - hotbarOffset - 10 - 20; // ホットバーの上に余白を持たせる
        int x = (screenW / 2) - (mc.textRenderer.getWidth(text) / 2);

        float alphaRatio = holding ? 1.0f : (fadeTicks / (float) FADE_DURATION);
        alphaRatio = Math.max(0f, Math.min(1f, alphaRatio));
        int alpha = (int) (255 * alphaRatio);
        int color = (alpha << 24) | 0xFFFFFF;

        RenderSystem.enableBlend();
        Text t = Text.literal(text);
        TextRenderer tr = mc.textRenderer;
        context.drawText(tr, t, x, y, color, false);
        RenderSystem.disableBlend();

        if (!holding && fadeTicks > 0) {
            fadeTicks--;
        }
    }
}
