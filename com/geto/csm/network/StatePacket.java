package com.geto.csm.network;

import com.geto.csm.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class StatePacket {
   public final int selected;
   public final int absorbed;
   public final int slots;
   public final int[] cooldowns;

   public StatePacket(int selected, int absorbed, int slots, int[] cooldowns) {
      this.selected = selected;
      this.absorbed = absorbed;
      this.slots = slots;
      this.cooldowns = cooldowns;
   }

   public static void encode(StatePacket p, FriendlyByteBuf b) {
      b.writeVarInt(p.selected);
      b.writeVarInt(p.absorbed);
      b.writeVarInt(p.slots);
      b.writeVarIntArray(p.cooldowns);
   }

   public static StatePacket decode(FriendlyByteBuf b) {
      return new StatePacket(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarIntArray());
   }

   public static void handle(StatePacket p, Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.state(p));
   }
}
