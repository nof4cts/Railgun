package com.abomination.horror.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Starts the first-sighting jumpscare cutscene on the victim's client. */
public class JumpscarePacket {
    public final int creatureId;

    public JumpscarePacket(int creatureId) {
        this.creatureId = creatureId;
    }

    public JumpscarePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(creatureId);
    }

    public static void handle(JumpscarePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.abomination.horror.client.ClientPacketHandler.onJumpscare(msg));
    }
}
