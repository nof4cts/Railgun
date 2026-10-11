package com.firestar.mih.network;

import com.firestar.mih.common.Move;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * A move has started. Everything every client needs to play the same beats: who, where, which way, at what (point and
 * target entity), where the user ends up, and when (server game time, so all clients agree).
 */
public final class S2CCast {
    public final Move move;
    public final int casterId;
    public final Vec3 origin;
    public final Vec3 dir;
    public final Vec3 point;
    public final Vec3 exit;
    public final long startTick;
    public final int seed;
    public final int targetId;

    public S2CCast(Move move, int casterId, Vec3 origin, Vec3 dir, Vec3 point, Vec3 exit, long startTick, int seed, int targetId) {
        this.move = move;
        this.casterId = casterId;
        this.origin = origin;
        this.dir = dir;
        this.point = point;
        this.exit = exit;
        this.startTick = startTick;
        this.seed = seed;
        this.targetId = targetId;
    }

    private static void vec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    private static Vec3 vec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void encode(S2CCast m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.move.ordinal());
        buf.writeVarInt(m.casterId);
        vec(buf, m.origin);
        vec(buf, m.dir);
        vec(buf, m.point);
        vec(buf, m.exit);
        buf.writeLong(m.startTick);
        buf.writeInt(m.seed);
        buf.writeInt(m.targetId);
    }

    public static S2CCast decode(FriendlyByteBuf buf) {
        return new S2CCast(Move.byId(buf.readVarInt()), buf.readVarInt(), vec(buf), vec(buf), vec(buf), vec(buf), buf.readLong(), buf.readInt(), buf.readInt());
    }

    public static void handle(S2CCast m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.mih.client.Casts.start(m));
    }
}
