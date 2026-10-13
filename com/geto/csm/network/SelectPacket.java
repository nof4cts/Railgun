package com.geto.csm.network;

import com.geto.csm.server.CsmLogic;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public class SelectPacket {
   public final int kind;

   public SelectPacket(int kind) {
      this.kind = kind;
   }

   public static void encode(SelectPacket p, FriendlyByteBuf b) {
      b.writeVarInt(p.kind);
   }

   public static SelectPacket decode(FriendlyByteBuf b) {
      return new SelectPacket(b.readVarInt());
   }

   public static void handle(SelectPacket p, Supplier<Context> ctx) {
      ServerPlayer sp = ctx.get().getSender();
      if (sp != null) {
         CsmLogic.select(sp, p.kind);
      }
   }
}
