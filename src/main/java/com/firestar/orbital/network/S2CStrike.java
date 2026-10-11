package com.firestar.orbital.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** A strike has begun: everything a client needs to play it on the shared timeline. */
public record S2CStrike(int id, int cannon, int caller, Vec3 target, long start, long seed, float radius) {
    public static void encode(S2CStrike m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.id);
        buf.writeVarInt(m.cannon);
        buf.writeVarInt(m.caller);
        buf.writeDouble(m.target.x);
        buf.writeDouble(m.target.y);
        buf.writeDouble(m.target.z);
        buf.writeLong(m.start);
        buf.writeLong(m.seed);
        buf.writeFloat(m.radius);
    }

    public static S2CStrike decode(FriendlyByteBuf buf) {
        return new S2CStrike(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readLong(), buf.readLong(), buf.readFloat());
    }

    public static void handle(S2CStrike m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.orbital.client.Strikes.begin(m)));
        ctx.get().setPacketHandled(true);
    }
}
