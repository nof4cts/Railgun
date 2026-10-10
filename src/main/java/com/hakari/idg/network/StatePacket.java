package com.hakari.idg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Snapshot of one Hakari user's technique state, sent to everyone tracking them. */
public class StatePacket {
    public int entityId;
    public int castTicks;
    public boolean domainActive;
    public int domainTicks;
    public double cx, cy, cz;
    public float radius;
    public int visuals, fails, riichiTicks;
    public int jackpotCutTicks, jackpotTicks, jackpotMax, jackpotNumber;
    public int rhythmTicks, counterTicks, pose;
    public boolean boosted;

    /** Client-only: when this snapshot arrived (ms), used to extrapolate timers smoothly. */
    public transient long receivedAt;

    public static void encode(StatePacket p, FriendlyByteBuf b) {
        b.writeInt(p.entityId); b.writeVarInt(p.castTicks); b.writeBoolean(p.domainActive); b.writeVarInt(p.domainTicks);
        b.writeDouble(p.cx); b.writeDouble(p.cy); b.writeDouble(p.cz); b.writeFloat(p.radius);
        b.writeVarInt(p.visuals); b.writeVarInt(p.fails); b.writeVarInt(p.riichiTicks);
        b.writeVarInt(p.jackpotCutTicks); b.writeVarInt(p.jackpotTicks); b.writeVarInt(p.jackpotMax); b.writeVarInt(p.jackpotNumber);
        b.writeVarInt(p.rhythmTicks); b.writeVarInt(p.counterTicks); b.writeVarInt(p.pose); b.writeBoolean(p.boosted);
    }

    public static StatePacket decode(FriendlyByteBuf b) {
        StatePacket p = new StatePacket();
        p.entityId = b.readInt(); p.castTicks = b.readVarInt(); p.domainActive = b.readBoolean(); p.domainTicks = b.readVarInt();
        p.cx = b.readDouble(); p.cy = b.readDouble(); p.cz = b.readDouble(); p.radius = b.readFloat();
        p.visuals = b.readVarInt(); p.fails = b.readVarInt(); p.riichiTicks = b.readVarInt();
        p.jackpotCutTicks = b.readVarInt(); p.jackpotTicks = b.readVarInt(); p.jackpotMax = b.readVarInt(); p.jackpotNumber = b.readVarInt();
        p.rhythmTicks = b.readVarInt(); p.counterTicks = b.readVarInt(); p.pose = b.readVarInt(); p.boosted = b.readBoolean();
        return p;
    }

    public static void handle(StatePacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.hakari.idg.client.ClientPacketHandler.state(p));
    }
}
