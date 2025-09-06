package net.misemise.ore_picker.network;

import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> Client payload: sends an int "destroyedCount"
 */
public record DestroyedCountS2CPayload(int count) implements CustomPayload {
    public static final Identifier ID = Identifier.of("orepicker", "destroyed_count");
    public static final CustomPayload.Id<DestroyedCountS2CPayload> TYPE = new CustomPayload.Id<>(ID);

    public static final PacketCodec<PacketByteBuf, DestroyedCountS2CPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.INT,
                    DestroyedCountS2CPayload::count,
                    DestroyedCountS2CPayload::new
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return TYPE;
    }
}
