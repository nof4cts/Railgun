package com.firestar.santoryu.network;

import com.firestar.santoryu.common.Move;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * A technique has started. Carries everything every client needs to play the same beats: where, which way, at what,
 * when (server game time), and the three blades (right hand, left hand, mouth) so they can be drawn.
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
    public final ItemStack right;
    public final ItemStack left;
    public final ItemStack mouth;

    public S2CCast(Move move, int casterId, Vec3 origin, Vec3 dir, Vec3 point, Vec3 exit, long startTick, int seed,
                   ItemStack right, ItemStack left, ItemStack mouth) {
        this.move = move;
        this.casterId = casterId;
        this.origin = origin;
        this.dir = dir;
        this.point = point;
        this.exit = exit;
        this.startTick = startTick;
        this.seed = seed;
        this.right = right;
        this.left = left;
        this.mouth = mouth;
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
        buf.writeItem(m.right);
        buf.writeItem(m.left);
        buf.writeItem(m.mouth);
    }

    public static S2CCast decode(FriendlyByteBuf buf) {
        return new S2CCast(Move.byId(buf.readVarInt()), buf.readVarInt(), vec(buf), vec(buf), vec(buf), vec(buf),
                buf.readLong(), buf.readInt(), buf.readItem(), buf.readItem(), buf.readItem());
    }

    public static void handle(S2CCast m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.santoryu.client.Casts.start(m));
    }
}
