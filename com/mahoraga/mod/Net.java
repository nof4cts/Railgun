package com.mahoraga.mod;

import com.mahoraga.mod.client.MahoFx;
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
   private static final String V = "2";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("mahoraga", "main"), () -> "2", "2"::equals, "2"::equals);
   public static final int SUMMON = 0;
   public static final int CINE_JUMP = 1;
   public static final int CINE_SWORD = 2;
   public static final int IMPACT = 3;
   public static final int SPIN = 4;
   public static final int ADAPTED = 5;
   public static final int DEFLECT = 6;
   public static final int SLAM = 7;
   public static final int CHUNK = 8;
   public static final int CHUNK_END = 9;
   public static final int GRAB = 10;
   public static final int MIMIC = 11;
   public static final int RITUAL = 12;
   public static final int WARN = 13;
   public static final int POSE = 14;
   public static final int CRUSH = 15;
   public static final int HIT = 16;
   public static final int GROW = 17;
   public static final int BLINK = 18;
   public static final int CINE_WALK = 19;
   public static final int LEARNED = 20;
   public static final int DOMAIN_TEXT = 21;
   public static final int RIPPLE = 22;
   public static final int HALF = 23;
   public static final int POSE_SUMMON = 1;
   public static final int POSE_CRUSHED = 2;

   private Net() {
   }

   static void register() {
      CHANNEL.registerMessage(0, Net.Fx.class, Net.Fx::encode, Net.Fx::decode, Net.Fx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(1, Net.Move.class, Net.Move::encode, Net.Move::decode, Net.Move::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
   }

   static void fx(ServerLevel l, Net.Fx f) {
      CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(f.at().x, f.at().y, f.at().z, 100.0, l.dimension())), f);
   }

   static void to(ServerPlayer p, Net.Fx f) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), f);
   }

   public static record Fx(int kind, int entity, Vec3 at, float f, String text, Vec3 v, int data) {
      public Fx(int kind, int entity, Vec3 at, float f, String text) {
         this(kind, entity, at, f, text, Vec3.ZERO, 0);
      }

      static void encode(Net.Fx x, FriendlyByteBuf b) {
         b.writeVarInt(x.kind);
         b.writeVarInt(x.entity);
         b.writeDouble(x.at.x);
         b.writeDouble(x.at.y);
         b.writeDouble(x.at.z);
         b.writeFloat(x.f);
         b.writeUtf(x.text);
         b.writeDouble(x.v.x);
         b.writeDouble(x.v.y);
         b.writeDouble(x.v.z);
         b.writeVarInt(x.data);
      }

      static Net.Fx decode(FriendlyByteBuf b) {
         return new Net.Fx(
            b.readVarInt(),
            b.readVarInt(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
            b.readFloat(),
            b.readUtf(),
            new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
            b.readVarInt()
         );
      }

      static void handle(Net.Fx x, Supplier<Context> ctx) {
         ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MahoFx.receive(x)));
         ctx.get().setPacketHandled(true);
      }
   }

   public static record Move(int slot) {
      static void encode(Net.Move m, FriendlyByteBuf b) {
         b.writeVarInt(m.slot);
      }

      static Net.Move decode(FriendlyByteBuf b) {
         return new Net.Move(b.readVarInt());
      }

      static void handle(Net.Move m, Supplier<Context> ctx) {
         ServerPlayer p = ctx.get().getSender();
         ctx.get().enqueueWork(() -> {
            if (p != null) {
               Ritual.key(p, m.slot);
            }
         });
         ctx.get().setPacketHandled(true);
      }
   }
}
