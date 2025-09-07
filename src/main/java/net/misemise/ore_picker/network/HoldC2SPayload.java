package net.misemise.ore_picker.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client -> Server payload carrying a single boolean "hold".
 */
public record HoldC2SPayload(boolean hold) implements CustomPayload {
    public static final Identifier ID = Identifier.of("orepicker", "hold_vein");
    public static final CustomPayload.Id<HoldC2SPayload> TYPE = new CustomPayload.Id<>(ID);

    // Simple codec: writeBoolean / readBoolean
    public static final PacketCodec<PacketByteBuf, HoldC2SPayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> buf.writeBoolean(payload.hold()),
                    buf -> new HoldC2SPayload(buf.readBoolean())
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }

    // helper to build PacketByteBuf quickly
    public static PacketByteBuf toBuf(boolean v) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBoolean(v);
        return buf;
    }

    public static HoldC2SPayload readFromBuf(PacketByteBuf buf) {
        return new HoldC2SPayload(buf.readBoolean());
    }
}
