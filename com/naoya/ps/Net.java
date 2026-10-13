package com.naoya.ps;

import com.jjk.mod.Balance;
import com.naoya.ps.client.Hud;
import com.naoya.ps.client.NFx;
import com.naoya.ps.server.Projection;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(Naoya.id("main"), () -> "1", "1"::equals, "1"::equals);

   private Net() {
   }

   static void register() {
      CHANNEL.registerMessage(
         0, Net.Move.class, (m, b) -> b.writeByte(m.slot), b -> new Net.Move(b.readByte()), Net.Move::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER)
      );
      CHANNEL.registerMessage(1, Net.Fx.class, Net.Fx::encode, Net.Fx::decode, Net.Fx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(2, Net.State.class, Net.State::encode, Net.State::decode, Net.State::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void fx(ServerLevel level, Net.Fx f) {
      CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(f.a().x, f.a().y, f.a().z, 160.0, level.dimension())), f);
   }

   public static void state(ServerPlayer p, Net.State s) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s);
   }

   public static record Fx(int kind, Vec3 a, Vec3 b, float f, int caster, int other, int dur) {
      public static final int DASH = 0;
      public static final int PALM = 1;
      public static final int FREEZE = 2;
      public static final int UNFREEZE = 3;
      public static final int BARRAGE = 4;
      public static final int HIT = 5;
      public static final int STEP = 6;
      public static final int BOOM = 7;
      public static final int TRAP = 8;
      public static final int SHATTER = 9;
      public static final int ULT = 10;
      public static final int ULT_BLINK = 11;
      public static final int ULT_HIT = 12;

      static void encode(Net.Fx x, FriendlyByteBuf b) {
         b.writeByte(x.kind);
         b.writeDouble(x.a.x);
         b.writeDouble(x.a.y);
         b.writeDouble(x.a.z);
         b.writeDouble(x.b.x);
         b.writeDouble(x.b.y);
         b.writeDouble(x.b.z);
         b.writeFloat(x.f);
         b.writeVarInt(x.caster);
         b.writeVarInt(x.other);
         b.writeVarInt(x.dur);
      }

      static Net.Fx decode(FriendlyByteBuf b) {
         return new Net.Fx(
            b.readByte(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
            b.readFloat(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt()
         );
      }

      static void handle(Net.Fx m, Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NFx.accept(m)));
         ctx.get().setPacketHandled(true);
      }
   }

   public static record Move(int slot) {
      static void handle(Net.Move m, Supplier<Context> ctx) {
         ServerPlayer p = ctx.get().getSender();
         ctx.get().enqueueWork(() -> {
            if (p != null && Balance.used(p, m.slot)) {
               Projection.useKey(p, m.slot);
            }
         });
         ctx.get().setPacketHandled(true);
      }
   }

   public static record State(long[] ready, int[] total, int accel) {
      static void encode(Net.State s, FriendlyByteBuf b) {
         b.writeVarInt(s.ready.length);

         for (int i = 0; i < s.ready.length; i++) {
            b.writeLong(s.ready[i]);
            b.writeVarInt(s.total[i]);
         }

         b.writeVarInt(s.accel);
      }

      static Net.State decode(FriendlyByteBuf b) {
         int n = b.readVarInt();
         long[] r = new long[n];
         int[] t = new int[n];

         for (int i = 0; i < n; i++) {
            r[i] = b.readLong();
            t[i] = b.readVarInt();
         }

         return new Net.State(r, t, b.readVarInt());
      }

      static void handle(Net.State m, Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Hud.accept(m)));
         ctx.get().setPacketHandled(true);
      }
   }
}
