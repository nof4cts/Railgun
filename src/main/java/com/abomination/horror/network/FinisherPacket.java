package com.abomination.horror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Tells clients to start (or abort) the spine-rip cinematic for a victim. */
public class FinisherPacket {
    public static final byte START = 0;
    public static final byte ABORT = 1;

    public final byte action;
    public final int creatureId;
    public final int victimId;
    public final long startTime;

    public FinisherPacket(byte action, int creatureId, int victimId, long startTime) {
        this.action = action;
        this.creatureId = creatureId;
        this.victimId = victimId;
        this.startTime = startTime;
    }

    public FinisherPacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeVarInt(creatureId);
        buf.writeVarInt(victimId);
        buf.writeLong(startTime);
    }

    public static void handle(FinisherPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.abomination.horror.client.ClientPacketHandler.onFinisher(msg));
    }
}
