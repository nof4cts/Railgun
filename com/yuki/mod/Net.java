package com.yuki.mod;

import com.jjk.mod.Balance;
import com.yuki.mod.client.Hud;
import com.yuki.mod.client.YFx;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
   private static final String V = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("yuki", "main"), () -> "1", "1"::equals, "1"::equals);

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
      CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(f.a.x, f.a.y, f.a.z, 128.0, level.dimension())), f);
   }

   public static final class Fx {
      public final byte kind;
      public final Vec3 a;
      public final Vec3 b;
      public final float f;
      public final int caster;
      public final int other;
      public final int dur;

      public Fx(byte kind, Vec3 a, Vec3 b, float f, int caster, int other, int dur) {
         this.kind = kind;
         this.a = a;
         this.b = b;
         this.f = f;
         this.caster = caster;
         this.other = other;
         this.dur = dur;
      }

      static void encode(Net.Fx x, FriendlyByteBuf buf) {
         buf.writeByte(x.kind);
         buf.writeDouble(x.a.x);
         buf.writeDouble(x.a.y);
         buf.writeDouble(x.a.z);
         buf.writeDouble(x.b.x);
         buf.writeDouble(x.b.y);
         buf.writeDouble(x.b.z);
         buf.writeFloat(x.f);
         buf.writeVarInt(x.caster);
         buf.writeVarInt(x.other);
         buf.writeVarInt(x.dur);
      }

      static Net.Fx decode(FriendlyByteBuf buf) {
         return new Net.Fx(
            buf.readByte(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readFloat(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt()
         );
      }

      static void handle(Net.Fx x, Supplier<Context> ctx) {
         Context c = ctx.get();
         c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> YFx.receive(x)));
         c.setPacketHandled(true);
      }
   }

   public static final class Move {
      public final byte slot;

      public Move(int slot) {
         this.slot = (byte)slot;
      }

      static void handle(Net.Move m, Supplier<Context> ctx) {
         Context c = ctx.get();
         ServerPlayer p = c.getSender();
         c.enqueueWork(() -> {
            if (p != null && Balance.used(p, m.slot)) {
               Yuki.useKey(p, m.slot);
            }
         });
         c.setPacketHandled(true);
      }
   }

   public static final class State {
      public final int energy;
      public final int mass;
      public final int garuda;
      public final int simple;
      public final int armed;
      public final int[] cooldowns;

      public State(int energy, int mass, int garuda, int simple, int armed, int[] cooldowns) {
         this.energy = energy;
         this.mass = mass;
         this.garuda = garuda;
         this.simple = simple;
         this.armed = armed;
         this.cooldowns = cooldowns;
      }

      static void encode(Net.State s, FriendlyByteBuf b) {
         b.writeVarInt(s.energy);
         b.writeVarInt(s.mass);
         b.writeVarInt(s.garuda);
         b.writeVarInt(s.simple);
         b.writeVarInt(s.armed);
         b.writeVarIntArray(s.cooldowns);
      }

      static Net.State decode(FriendlyByteBuf b) {
         return new Net.State(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarIntArray(16));
      }

      static void handle(Net.State s, Supplier<Context> ctx) {
         Context c = ctx.get();
         c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Hud.set(s)));
         c.setPacketHandled(true);
      }
   }
}
