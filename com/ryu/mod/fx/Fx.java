package com.ryu.mod.fx;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Fx {
   private static final String PROTOCOL = "1";
   private static final double SEND_RANGE = 128.0;
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("ryu", "fx"), () -> "1", "1"::equals, "1"::equals);

   private Fx() {
   }

   public static void register() {
      CHANNEL.registerMessage(0, FxPacket.class, FxPacket::encode, FxPacket::decode, FxPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
      CHANNEL.registerMessage(1, CinePacket.class, CinePacket::encode, CinePacket::decode, CinePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
   }

   public static void orb(ServerLevel level, Vec3 pos, float radius, int color, int core, int duration, byte mode, int followId) {
      send(level, pos, 0.0, new FxPacket((byte)0, mode, pos, Vec3.ZERO, radius, color, core, duration, followId));
   }

   public static void beam(ServerLevel level, Vec3 from, Vec3 to, float width, int color, int core, int duration) {
      Vec3 mid = from.add(to).scale(0.5);
      send(level, mid, from.distanceTo(to) / 2.0, new FxPacket((byte)1, (byte)0, from, to, width, color, core, duration, -1));
   }

   public static void shockwave(ServerLevel level, Vec3 center, Vec3 normal, float radius, int color, int duration) {
      send(level, center, (double)radius, new FxPacket((byte)2, (byte)0, center, normal, radius, color, 16777215, duration, -1));
   }

   public static void burst(ServerLevel level, Vec3 center, float speed, int color, int fadeTo, int count, byte mode) {
      send(level, center, 0.0, new FxPacket((byte)3, mode, center, Vec3.ZERO, speed, color, fadeTo, count, -1));
   }

   public static void trail(ServerLevel level, Vec3 from, Vec3 to, int color) {
      send(level, from, from.distanceTo(to), new FxPacket((byte)4, (byte)0, from, to, 0.0F, color, 16777215, 0, -1));
   }

   public static void domain(ServerLevel level, Vec3 center, float radius, int duration, byte mode, int color) {
      send(level, center, (double)radius, new FxPacket((byte)5, mode, center, Vec3.ZERO, radius, color, 16777215, duration, -1));
   }

   public static void shimmer(ServerLevel level, Vec3 pos, Vec3 normal, float size, int color, int duration, byte mode, int followId) {
      send(level, pos, 0.0, new FxPacket((byte)6, mode, pos, normal, size, color, 16777215, duration, followId));
   }

   public static void shake(ServerLevel level, Vec3 pos, float intensity, int flashArgb, int duration) {
      send(level, pos, 0.0, new FxPacket((byte)7, (byte)0, pos, Vec3.ZERO, intensity, flashArgb, 0, duration, -1));
   }

   public static void arcs(ServerLevel level, Vec3 center, float length, int color, int count, int duration) {
      send(level, center, (double)length, new FxPacket((byte)8, (byte)count, center, Vec3.ZERO, length, color, 16777215, duration, -1));
   }

   private static void send(ServerLevel level, Vec3 pos, double extraRange, FxPacket packet) {
      CHANNEL.send(PacketDistributor.NEAR.with(TargetPoint.p(pos.x, pos.y, pos.z, 128.0 + extraRange, level.dimension())), packet);
   }
}
