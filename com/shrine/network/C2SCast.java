package com.shrine.network;

import com.jjk.mod.Balance;
import com.shrine.common.Move;
import com.shrine.server.Techniques;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

public final class C2SCast {
   public final Move move;

   public C2SCast(Move move) {
      this.move = move;
   }

   public C2SCast(FriendlyByteBuf buf) {
      this.move = Move.byId(buf.readVarInt());
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.move.ordinal());
   }

   public void handle(Supplier<Context> ctx) {
      ServerPlayer p = ctx.get().getSender();
      if (p != null && Balance.used(p, this.move.ordinal())) {
         Techniques.cast(p, this.move);
      }

      ctx.get().setPacketHandled(true);
   }
}
