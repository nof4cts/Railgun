package com.railgun.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ShotPacket {
    public final double mx, my, mz, ex, ey, ez;
    public final int shooter, face, hits;
    public final float power;

    public ShotPacket(double mx, double my, double mz, double ex, double ey, double ez,
                      int shooter, float power, int face, int hits) {
        this.mx = mx; this.my = my; this.mz = mz;
        this.ex = ex; this.ey = ey; this.ez = ez;
        this.shooter = shooter; this.power = power; this.face = face; this.hits = hits;
    }

    public static void encode(ShotPacket p, FriendlyByteBuf b) {
        b.writeDouble(p.mx); b.writeDouble(p.my); b.writeDouble(p.mz);
        b.writeDouble(p.ex); b.writeDouble(p.ey); b.writeDouble(p.ez);
        b.writeVarInt(p.shooter); b.writeFloat(p.power); b.writeVarInt(p.face + 1); b.writeVarInt(p.hits);
    }

    public static ShotPacket decode(FriendlyByteBuf b) {
        return new ShotPacket(b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readDouble(), b.readDouble(),
                b.readVarInt(), b.readFloat(), b.readVarInt() - 1, b.readVarInt());
    }

    public static void handle(ShotPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.railgun.client.ClientShots.onShot(p)));
        ctx.get().setPacketHandled(true);
    }
}
