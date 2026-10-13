package com.geto.csm.network;

import com.geto.csm.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class FxPacket {
   public final int type;
   public final int src;
   public final int tgt;
   public final double x;
   public final double y;
   public final double z;
   public final float a;
   public final float b;

   public FxPacket(int type, int src, int tgt, double x, double y, double z, float a, float b) {
      this.type = type;
      this.src = src;
      this.tgt = tgt;
      this.x = x;
      this.y = y;
      this.z = z;
      this.a = a;
      this.b = b;
   }

   public static void encode(FxPacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.type);
      buf.writeInt(p.src);
      buf.writeInt(p.tgt);
      buf.writeDouble(p.x);
      buf.writeDouble(p.y);
      buf.writeDouble(p.z);
      buf.writeFloat(p.a);
      buf.writeFloat(p.b);
   }

   public static FxPacket decode(FriendlyByteBuf buf) {
      return new FxPacket(
         buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat()
      );
   }

   public static void handle(FxPacket p, Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.fx(p));
   }
}
