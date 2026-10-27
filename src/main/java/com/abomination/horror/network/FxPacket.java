package com.abomination.horror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Camera shake / chromatic aberration / flash / light flicker for one player. */
public class FxPacket {
    public final float shake;
    public final float aberration;
    public final float flash;
    public final int flicker;

    public FxPacket(float shake, float aberration, float flash, int flicker) {
        this.shake = shake;
        this.aberration = aberration;
        this.flash = flash;
        this.flicker = flicker;
    }

    public FxPacket(FriendlyByteBuf buf) {
        this(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(shake);
        buf.writeFloat(aberration);
        buf.writeFloat(flash);
        buf.writeVarInt(flicker);
    }

    public static void handle(FxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.abomination.horror.client.ClientPacketHandler.onFx(msg));
    }
}
