package com.geto.csm.network;

import com.geto.csm.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class QuestionPacket {
   public final int entityId;
   public final int stage;
   public final int timeoutTicks;

   public QuestionPacket(int entityId, int stage, int timeoutTicks) {
      this.entityId = entityId;
      this.stage = stage;
      this.timeoutTicks = timeoutTicks;
   }

   public static void encode(QuestionPacket p, FriendlyByteBuf b) {
      b.writeInt(p.entityId);
      b.writeVarInt(p.stage);
      b.writeVarInt(p.timeoutTicks);
   }

   public static QuestionPacket decode(FriendlyByteBuf b) {
      return new QuestionPacket(b.readInt(), b.readVarInt(), b.readVarInt());
   }

   public static void handle(QuestionPacket p, Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.question(p));
   }
}
