package com.hakari.idg.network;

import com.hakari.idg.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class CutscenePacket {
   public final int type;
   public final int anchor;
   public final int[] args;

   public CutscenePacket(int type, int anchor, int[] args) {
      this.type = type;
      this.anchor = anchor;
      this.args = args;
   }

   public int arg(int i) {
      return i < this.args.length ? this.args[i] : 0;
   }

   public static void encode(CutscenePacket p, FriendlyByteBuf buf) {
      buf.writeVarInt(p.type);
      buf.writeInt(p.anchor);
      buf.writeVarIntArray(p.args);
   }

   public static CutscenePacket decode(FriendlyByteBuf buf) {
      return new CutscenePacket(buf.readVarInt(), buf.readInt(), buf.readVarIntArray());
   }

   public static void handle(CutscenePacket p, Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.cutscene(p));
   }
}
