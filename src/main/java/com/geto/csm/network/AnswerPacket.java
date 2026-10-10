package com.geto.csm.network;

import com.geto.csm.server.KuchisakeGame;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the answer to "Am I pretty?" (0 yes, 1 no, 2 "you're average"). */
public class AnswerPacket {
    public final int entityId, answer;

    public AnswerPacket(int entityId, int answer) {
        this.entityId = entityId; this.answer = answer;
    }

    public static void encode(AnswerPacket p, FriendlyByteBuf b) {
        b.writeInt(p.entityId); b.writeVarInt(p.answer);
    }

    public static AnswerPacket decode(FriendlyByteBuf b) {
        return new AnswerPacket(b.readInt(), b.readVarInt());
    }

    public static void handle(AnswerPacket p, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp != null) KuchisakeGame.answer(sp, p.entityId, p.answer);
    }
}
