package net.misemise.ore_picker.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> Client payload sending an int count of destroyed blocks.
 */
public record DestroyedCountS2CPayload(int count) implements CustomPayload {
    public static final Identifier ID = Identifier.of("orepicker", "destroyed_count");
    public static final CustomPayload.Id<DestroyedCountS2CPayload> TYPE = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, DestroyedCountS2CPayload> CODEC =
            PacketCodec.of(
                    (payload, buf) -> buf.writeVarInt(payload.count()),
                    buf -> new DestroyedCountS2CPayload(buf.readVarInt())
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }

    public static PacketByteBuf toBuf(int count) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(count);
        return buf;
    }

    public static DestroyedCountS2CPayload readFromBuf(PacketByteBuf buf) {
        return new DestroyedCountS2CPayload(buf.readVarInt());
    }
}
