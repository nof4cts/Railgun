package com.geto.csm.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: open (stage 1/2) or close (stage 0) the binding-vow question. */
public class QuestionPacket {
    public final int entityId, stage, timeoutTicks;

    public QuestionPacket(int entityId, int stage, int timeoutTicks) {
        this.entityId = entityId; this.stage = stage; this.timeoutTicks = timeoutTicks;
    }

    public static void encode(QuestionPacket p, FriendlyByteBuf b) {
        b.writeInt(p.entityId); b.writeVarInt(p.stage); b.writeVarInt(p.timeoutTicks);
    }

    public static QuestionPacket decode(FriendlyByteBuf b) {
        return new QuestionPacket(b.readInt(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(QuestionPacket p, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.geto.csm.client.ClientPacketHandler.question(p));
    }
}
