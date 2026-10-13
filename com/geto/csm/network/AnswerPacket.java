package com.geto.csm.network;

import com.geto.csm.server.KuchisakeGame;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class AnswerPacket {
   public final int entityId;
   public final int answer;

   public AnswerPacket(int entityId, int answer) {
      this.entityId = entityId;
      this.answer = answer;
   }

   public static void encode(AnswerPacket p, FriendlyByteBuf b) {
      b.writeInt(p.entityId);
      b.writeVarInt(p.answer);
   }

   public static AnswerPacket decode(FriendlyByteBuf b) {
      return new AnswerPacket(b.readInt(), b.readVarInt());
   }

   public static void handle(AnswerPacket p, Supplier<Context> ctx) {
      ServerPlayer sp = ctx.get().getSender();
      if (sp != null) {
         KuchisakeGame.answer(sp, p.entityId, p.answer);
      }
   }
}
