package com.naoya.ps.client.cine;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record CameraPose(Vec3 pos, Vec3 look, float fov, float roll) {
   public float yaw() {
      Vec3 d = this.look.subtract(this.pos);
      return (float)Math.toDegrees(Math.atan2(-d.x, d.z));
   }

   public float pitch() {
      Vec3 d = this.look.subtract(this.pos);
      double flat = Math.sqrt(d.x * d.x + d.z * d.z);
      return (float)(-Math.toDegrees(Math.atan2(d.y, flat)));
   }

   public CameraPose lerp(CameraPose o, float k) {
      Vec3 p = this.pos.lerp(o.pos, (double)k);
      float yaw = Mth.rotLerp(k, this.yaw(), o.yaw());
      float pitch = Mth.lerp(k, this.pitch(), o.pitch());
      double dist = Mth.lerp((double)k, this.look.distanceTo(this.pos), o.look.distanceTo(o.pos));
      float cp = Mth.cos(pitch * (float) (Math.PI / 180.0));
      Vec3 dir = new Vec3(
         (double)(-Mth.sin(yaw * (float) (Math.PI / 180.0)) * cp),
         (double)(-Mth.sin(pitch * (float) (Math.PI / 180.0))),
         (double)(Mth.cos(yaw * (float) (Math.PI / 180.0)) * cp)
      );
      return new CameraPose(p, p.add(dir.scale(Math.max(1.0, dist))), Mth.lerp(k, this.fov, o.fov), Mth.lerp(k, this.roll, o.roll));
   }
}
