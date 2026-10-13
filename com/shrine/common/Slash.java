package com.shrine.common;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public record Slash(Vec3 origin, Vec3 dir, Vec3 side, Vec3 normal, double length, double width) {
   public static Slash of(Vec3 origin, Vec3 dir, double rollDeg, double length, double width) {
      dir = dir.normalize();
      Vec3 right = dir.cross(new Vec3(0.0, 1.0, 0.0));
      if (right.lengthSqr() < 1.0E-6) {
         right = new Vec3(1.0, 0.0, 0.0);
      }

      right = right.normalize();
      Vec3 up = right.cross(dir).normalize();
      double r = Math.toRadians(rollDeg);
      Vec3 side = right.scale(Math.cos(r)).add(up.scale(Math.sin(r)));
      Vec3 normal = dir.cross(side).normalize();
      return new Slash(origin, dir, side, normal, length, width);
   }

   public static double roll(long seed, int i) {
      RandomSource r = RandomSource.create(seed * 31L + (long)i * 7919L);
      double base = r.nextBoolean() ? 25.0 : 155.0;
      return base + (r.nextDouble() - 0.5) * 70.0;
   }

   public static Vec3 jitter(long seed, int i, Vec3 dir, double amount) {
      RandomSource r = RandomSource.create(seed * 131L + (long)i * 104729L);
      Vec3 j = new Vec3(r.nextDouble() - 0.5, (r.nextDouble() - 0.5) * 0.6, r.nextDouble() - 0.5).scale(amount);
      return dir.add(j).normalize();
   }

   public boolean contains(Vec3 p, double t, double reach) {
      Vec3 d = p.subtract(this.origin);
      double a = d.dot(this.dir);
      if (!(a < 0.0) && !(a > Math.min(this.length, reach))) {
         return Math.abs(d.dot(this.side)) > this.width * 0.5 ? false : Math.abs(d.dot(this.normal)) <= t;
      } else {
         return false;
      }
   }
}
