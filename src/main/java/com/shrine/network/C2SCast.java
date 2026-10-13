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
   /** Open only: play the film cutscene (false = the move as it happens in the film, straight away, no cutscene). */
   public final boolean film;

   public C2SCast(Move move) {
      this(move, false);
   }

   public C2SCast(Move move, boolean film) {
      this.move = move;
      this.film = film;
   }

   public C2SCast(FriendlyByteBuf buf) {
      this.move = Move.byId(buf.readVarInt());
      this.film = buf.readBoolean();
   }

   public void encode(FriendlyByteBuf buf) {
      buf.writeVarInt(this.move.ordinal());
      buf.writeBoolean(this.film);
   }

   public void handle(Supplier<Context> ctx) {
      ServerPlayer p = ctx.get().getSender();
      if (p != null && Balance.used(p, this.move.ordinal())) {
         Techniques.cast(p, this.move, this.film);
      }

      ctx.get().setPacketHandled(true);
   }
}
