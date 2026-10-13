package com.ryu.mod.fx;

import com.ryu.mod.fx.client.RyuCine;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.PacketDistributor.TargetPoint;

public final class CinePacket {
   public static final int GRANITE = 0;
   public static final int DOMAIN = 1;
   public final int kind;
   public final int caster;

   public CinePacket(int kind, int caster) {
      this.kind = kind;
      this.caster = caster;
   }

   public static void send(ServerLevel level, Vec3 at, int kind, int caster) {
      Fx.CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(at.x, at.y, at.z, 64.0, level.dimension())), new CinePacket(kind, caster));
   }

   public static void encode(CinePacket p, FriendlyByteBuf buf) {
      buf.writeByte(p.kind);
      buf.writeVarInt(p.caster);
   }

   public static CinePacket decode(FriendlyByteBuf buf) {
      return new CinePacket(buf.readByte(), buf.readVarInt());
   }

   public static void handle(CinePacket p, Supplier<Context> ctx) {
      ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RyuCine.start(p)));
      ctx.get().setPacketHandled(true);
   }
}
