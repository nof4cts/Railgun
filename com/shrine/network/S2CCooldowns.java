package com.shrine.network;

import com.shrine.client.TechniqueHud;
import com.shrine.common.Move;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public final class S2CCooldowns {
   public final long[] readyAt;
   public final int[] total;

   public S2CCooldowns(long[] readyAt, int[] total) {
      this.readyAt = readyAt;
      this.total = total;
   }

   public S2CCooldowns(FriendlyByteBuf b) {
      int n = Move.values().length;
      this.readyAt = new long[n];
      this.total = new int[n];

      for (int i = 0; i < n; i++) {
         this.readyAt[i] = b.readLong();
         this.total[i] = b.readVarInt();
      }
   }

   public void encode(FriendlyByteBuf b) {
      for (int i = 0; i < this.readyAt.length; i++) {
         b.writeLong(this.readyAt[i]);
         b.writeVarInt(this.total[i]);
      }
   }

   public void handle(Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TechniqueHud.cooldowns(this));
      ctx.get().setPacketHandled(true);
   }
}
