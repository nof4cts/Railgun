package com.firestar.mih.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * One Stand user's standing state: has the Stand, has it out, Double Acceleration until, the Time Acceleration run in
 * progress (start, length, centre, radius), and the Alternate Universe marks left by a reset (until when, which limb).
 */
public final class S2CState {
    public final int entityId;
    public final boolean has;
    public final boolean summoned;
    public final long daUntil;
    public final long taStart;
    public final int taLength;
    public final double cx, cy, cz;
    public final float radius;
    public final long auUntil;
    public final byte limb;

    public S2CState(int entityId, boolean has, boolean summoned, long daUntil, long taStart, int taLength, double cx, double cy, double cz,
                    float radius, long auUntil, byte limb) {
        this.entityId = entityId;
        this.has = has;
        this.summoned = summoned;
        this.daUntil = daUntil;
        this.taStart = taStart;
        this.taLength = taLength;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
        this.auUntil = auUntil;
        this.limb = limb;
    }

    public static void encode(S2CState m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entityId);
        buf.writeBoolean(m.has);
        buf.writeBoolean(m.summoned);
        buf.writeLong(m.daUntil);
        buf.writeLong(m.taStart);
        buf.writeVarInt(m.taLength);
        buf.writeDouble(m.cx);
        buf.writeDouble(m.cy);
        buf.writeDouble(m.cz);
        buf.writeFloat(m.radius);
        buf.writeLong(m.auUntil);
        buf.writeByte(m.limb);
    }

    public static S2CState decode(FriendlyByteBuf buf) {
        return new S2CState(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readLong(), buf.readLong(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readLong(), buf.readByte());
    }

    public static void handle(S2CState m, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.firestar.mih.client.ClientState.accept(m));
    }
}
