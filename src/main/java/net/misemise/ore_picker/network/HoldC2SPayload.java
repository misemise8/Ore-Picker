package net.misemise.ore_picker.network;

import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;

// PacketCodec / PacketCodecs
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> Server payload for "hold" (boolean).
 * Note: Some Yarn versions name the boolean codec PacketCodecs.BOOL, others PacketCodecs.BOOLEAN.
 * This file uses PacketCodecs.BOOLEAN. If compilation fails saying BOOLEAN がない場合は
 * PacketCodecs.BOOL に置き換えてください。
 */
public record HoldC2SPayload(boolean pressed) implements CustomPayload {
    public static final Identifier ID = Identifier.of("orepicker", "hold_vein");

    // CustomPayload.Id wrapper (mapping may differ by mappings, but this is standard in newer mappings)
    public static final CustomPayload.Id<HoldC2SPayload> TYPE = new CustomPayload.Id<>(ID);

    // Use the boolean codec from PacketCodecs (BOOLEAN variant)
    public static final PacketCodec<PacketByteBuf, HoldC2SPayload> CODEC =
            PacketCodec.tuple(
                    // ← ここを PacketCodecs.BOOL に変更する必要があればそうしてください
                    PacketCodecs.BOOLEAN,
                    HoldC2SPayload::pressed,
                    HoldC2SPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
