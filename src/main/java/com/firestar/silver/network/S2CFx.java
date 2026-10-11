package com.firestar.silver.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** A one-off visual the server decides on, at some points in the world: a dodge (from, to), a counter (where it lands). */
public final class S2CFx {
    public static final int DODGE = 2;
    public static final int COUNTER = 3;
    public final int kind;
    public final int entityId;
    public final List<Vec3> points;

    public S2CFx(int kind, int entityId, List<Vec3> points) {
        this.kind = kind;
        this.entityId = entityId;
        this.points = points;
    }

    public static void encode(S2CFx m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.kind);
        buf.writeVarInt(m.entityId);
        buf.writeVarInt(m.points.size());
        for (Vec3 v : m.points) {
            buf.writeDouble(v.x);
            buf.writeDouble(v.y);
            buf.writeDouble(v.z);
        }
    }

    public static S2CFx decode(FriendlyByteBuf buf) {
        int kind = buf.readVarInt();
        int id = buf.readVarInt();
        int n = buf.readVarInt();
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }
        return new S2CFx(kind, id, pts);
    }

    public static void handle(S2CFx m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.silver.client.Choreo.serverFx(m));
    }
}
