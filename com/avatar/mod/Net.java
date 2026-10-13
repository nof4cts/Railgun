package com.avatar.mod;

import com.avatar.mod.client.FireFx;
import com.avatar.mod.client.Hud;
import com.jjk.mod.Balance;
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
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("avatar", "main"), () -> "1", "1"::equals, "1"::equals);

   private Net() {
   }

   static void register() {
      CHANNEL.registerMessage(0, Net.Key.class, (m, b) -> {
         b.writeByte(m.slot);
         b.writeBoolean(m.down);
      }, b -> new Net.Key(b.readByte(), b.readBoolean()), Net.Key::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
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
      public final int flags;

      public Fx(byte kind, Vec3 a, Vec3 b, float f, int caster, int other, int dur) {
         this(kind, a, b, f, caster, other, dur, 0);
      }

      public Fx(byte kind, Vec3 a, Vec3 b, float f, int caster, int other, int dur, int flags) {
         this.flags = flags;
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
         buf.writeVarInt(x.flags);
      }

      static Net.Fx decode(FriendlyByteBuf buf) {
         return new Net.Fx(
            buf.readByte(),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readFloat(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt()
         );
      }

      static void handle(Net.Fx x, Supplier<Context> ctx) {
         Context c = ctx.get();
         c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FireFx.receive(x)));
         c.setPacketHandled(true);
      }
   }

   public static record Key(int slot, boolean down) {
      static void handle(Net.Key m, Supplier<Context> ctx) {
         Context c = ctx.get();
         ServerPlayer p = c.getSender();
         c.enqueueWork(() -> {
            if (p != null) {
               if (m.down && !Balance.used(p, m.slot)) {
                  return;
               }

               Bending.useKey(p, m.slot, m.down);
            }
         });
         c.setPacketHandled(true);
      }
   }

   public static record State(int charge, int lcharge, int blue, int stance, int[] cooldowns) {
      static void encode(Net.State s, FriendlyByteBuf b) {
         b.writeVarInt(s.charge + 1);
         b.writeVarInt(s.lcharge + 1);
         b.writeVarInt(s.blue);
         b.writeVarInt(s.stance);
         b.writeVarIntArray(s.cooldowns);
      }

      static Net.State decode(FriendlyByteBuf b) {
         return new Net.State(b.readVarInt() - 1, b.readVarInt() - 1, b.readVarInt(), b.readVarInt(), b.readVarIntArray(16));
      }

      static void handle(Net.State s, Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Hud.set(s)));
         ctx.get().setPacketHandled(true);
      }
   }
}
