package com.firestar.worldbreaker.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** A march begins (or, for someone joining late, is already under way): everything the client needs to replay it. */
public record S2CMarch(int id, int caller, Vec3 origin, Vec3 dir, long start, long seed, int count, int life) {
    public static void encode(S2CMarch m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeVarInt(m.caller);
        buf.writeDouble(m.origin.x);
        buf.writeDouble(m.origin.y);
        buf.writeDouble(m.origin.z);
        buf.writeDouble(m.dir.x);
        buf.writeDouble(m.dir.z);
        buf.writeLong(m.start);
        buf.writeLong(m.seed);
        buf.writeVarInt(m.count);
        buf.writeVarInt(m.life);
    }

    public static S2CMarch decode(FriendlyByteBuf buf) {
        return new S2CMarch(buf.readVarInt(), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), 0, buf.readDouble()), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CMarch m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.worldbreaker.client.Marching.accept(m)));
        ctx.get().setPacketHandled(true);
    }
}
