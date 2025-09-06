package net.misemise.ore_picker.network;

import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Client -> Server payload for hold state.
 * Uses PacketCodecs.BOOLEAN (some mappings may need PacketCodecs.BOOL — もしコンパイルエラーが出たら置き換えてください)
 */
public record HoldC2SPayload(boolean pressed) implements CustomPayload {
    public static final Identifier ID = Identifier.of("orepicker", "hold_vein");
    public static final CustomPayload.Id<HoldC2SPayload> TYPE = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, HoldC2SPayload> CODEC =
            PacketCodec.tuple(
                    // If BOOLEAN is not present in your mappings, change to PacketCodecs.BOOL
                    PacketCodecs.BOOLEAN,
                    HoldC2SPayload::pressed,
                    HoldC2SPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
