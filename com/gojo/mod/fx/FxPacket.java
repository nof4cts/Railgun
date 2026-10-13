package com.gojo.mod.fx;

import com.gojo.mod.fx.client.ClientFx;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent.Context;

public final class FxPacket {
   public final byte kind;
   public final byte mode;
   public final double x;
   public final double y;
   public final double z;
   public final double bx;
   public final double by;
   public final double bz;
   public final float size;
   public final int colorA;
   public final int colorB;
   public final int duration;
   public final int entityId;

   public FxPacket(byte kind, byte mode, Vec3 a, Vec3 b, float size, int colorA, int colorB, int duration, int entityId) {
      this(kind, mode, a.x, a.y, a.z, b.x, b.y, b.z, size, colorA, colorB, duration, entityId);
   }

   private FxPacket(
      byte kind, byte mode, double x, double y, double z, double bx, double by, double bz, float size, int colorA, int colorB, int duration, int entityId
   ) {
      this.kind = kind;
      this.mode = mode;
      this.x = x;
      this.y = y;
      this.z = z;
      this.bx = bx;
      this.by = by;
      this.bz = bz;
      this.size = size;
      this.colorA = colorA;
      this.colorB = colorB;
      this.duration = duration;
      this.entityId = entityId;
   }

   public static void encode(FxPacket msg, FriendlyByteBuf buf) {
      buf.writeByte(msg.kind);
      buf.writeByte(msg.mode);
      buf.writeDouble(msg.x);
      buf.writeDouble(msg.y);
      buf.writeDouble(msg.z);
      buf.writeDouble(msg.bx);
      buf.writeDouble(msg.by);
      buf.writeDouble(msg.bz);
      buf.writeFloat(msg.size);
      buf.writeInt(msg.colorA);
      buf.writeInt(msg.colorB);
      buf.writeVarInt(msg.duration);
      buf.writeInt(msg.entityId);
   }

   public static FxPacket decode(FriendlyByteBuf buf) {
      return new FxPacket(
         buf.readByte(),
         buf.readByte(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readDouble(),
         buf.readFloat(),
         buf.readInt(),
         buf.readInt(),
         buf.readVarInt(),
         buf.readInt()
      );
   }

   public static void handle(FxPacket msg, Supplier<Context> ctx) {
      Context context = ctx.get();
      context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientFx.handle(msg)));
      context.setPacketHandled(true);
   }
}
