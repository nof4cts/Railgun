package com.geto.csm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class FxPacket {
    public final int type, src, tgt;
    public final double x, y, z;
    public final float a, b;

    public FxPacket(int type, int src, int tgt, double x, double y, double z, float a, float b) {
        this.type = type; this.src = src; this.tgt = tgt;
        this.x = x; this.y = y; this.z = z; this.a = a; this.b = b;
    }

    public static void encode(FxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.type); buf.writeInt(p.src); buf.writeInt(p.tgt);
        buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
        buf.writeFloat(p.a); buf.writeFloat(p.b);
    }

    public static FxPacket decode(FriendlyByteBuf buf) {
        return new FxPacket(buf.readVarInt(), buf.readInt(), buf.readInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(FxPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geto.csm.client.ClientPacketHandler.fx(p));
    }
}
