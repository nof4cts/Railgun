package com.hakari.idg.network;

import com.hakari.idg.client.ClientPacketHandler;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public class StatePacket {
   public int entityId;
   public int castTicks;
   public boolean domainActive;
   public int domainTicks;
   public double cx;
   public double cy;
   public double cz;
   public float radius;
   public int visuals;
   public int fails;
   public int riichiTicks;
   public int jackpotCutTicks;
   public int jackpotTicks;
   public int jackpotMax;
   public int jackpotNumber;
   public int rhythmTicks;
   public int counterTicks;
   public int pose;
   public boolean boosted;
   public transient long receivedAt;

   public static void encode(StatePacket p, FriendlyByteBuf b) {
      b.writeInt(p.entityId);
      b.writeVarInt(p.castTicks);
      b.writeBoolean(p.domainActive);
      b.writeVarInt(p.domainTicks);
      b.writeDouble(p.cx);
      b.writeDouble(p.cy);
      b.writeDouble(p.cz);
      b.writeFloat(p.radius);
      b.writeVarInt(p.visuals);
      b.writeVarInt(p.fails);
      b.writeVarInt(p.riichiTicks);
      b.writeVarInt(p.jackpotCutTicks);
      b.writeVarInt(p.jackpotTicks);
      b.writeVarInt(p.jackpotMax);
      b.writeVarInt(p.jackpotNumber);
      b.writeVarInt(p.rhythmTicks);
      b.writeVarInt(p.counterTicks);
      b.writeVarInt(p.pose);
      b.writeBoolean(p.boosted);
   }

   public static StatePacket decode(FriendlyByteBuf b) {
      StatePacket p = new StatePacket();
      p.entityId = b.readInt();
      p.castTicks = b.readVarInt();
      p.domainActive = b.readBoolean();
      p.domainTicks = b.readVarInt();
      p.cx = b.readDouble();
      p.cy = b.readDouble();
      p.cz = b.readDouble();
      p.radius = b.readFloat();
      p.visuals = b.readVarInt();
      p.fails = b.readVarInt();
      p.riichiTicks = b.readVarInt();
      p.jackpotCutTicks = b.readVarInt();
      p.jackpotTicks = b.readVarInt();
      p.jackpotMax = b.readVarInt();
      p.jackpotNumber = b.readVarInt();
      p.rhythmTicks = b.readVarInt();
      p.counterTicks = b.readVarInt();
      p.pose = b.readVarInt();
      p.boosted = b.readBoolean();
      return p;
   }

   public static void handle(StatePacket p, Supplier<Context> ctx) {
      DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.state(p));
   }
}
