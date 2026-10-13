package com.hakari.idg.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class Cutscene {
   public final int anchorId;
   public final double duration;
   public final boolean follow;
   private final List<Cutscene.Key> keys = new ArrayList<>();
   private final List<Cutscene.Ev> events = new ArrayList<>();
   long start;
   Vec3 anchorStart = Vec3.ZERO;
   float yawStart;
   Vec3 outPos = Vec3.ZERO;
   float outYaw;
   float outPitch;
   float outRoll;
   double outFov = 70.0;

   public Cutscene(int anchorId, double duration, boolean follow) {
      this.anchorId = anchorId;
      this.duration = duration;
      this.follow = follow;
   }

   public Cutscene key(double t, double f, double r, double u, double lf, double lr, double lu, double fov, double roll, Ease ease) {
      this.keys.add(new Cutscene.Key(t, new Vec3(f, r, u), new Vec3(lf, lr, lu), fov, roll, ease, false));
      return this;
   }

   public Cutscene cut(double t, double f, double r, double u, double lf, double lr, double lu, double fov, double roll) {
      this.keys.add(new Cutscene.Key(t, new Vec3(f, r, u), new Vec3(lf, lr, lu), fov, roll, Ease.LINEAR, true));
      return this;
   }

   public Cutscene at(double t, Runnable r) {
      this.events.add(new Cutscene.Ev(t, r));
      return this;
   }

   void begin() {
      this.start = Vfx.now();
      Entity a = this.anchor();
      if (a != null) {
         this.anchorStart = a.getEyePosition(1.0F);
         this.yawStart = a.getYRot();
      }
   }

   Entity anchor() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? null : mc.level.getEntity(this.anchorId);
   }

   double time() {
      return (double)(Vfx.now() - this.start) / 1000.0;
   }

   boolean done() {
      return this.time() >= this.duration;
   }

   private Vec3 local(Vec3 origin, Vec3 frl) {
      float yaw = this.yawStart * (float) (Math.PI / 180.0);
      Vec3 f = new Vec3((double)(-Mth.sin(yaw)), 0.0, (double)Mth.cos(yaw));
      Vec3 r = new Vec3((double)(-Mth.cos(yaw)), 0.0, (double)(-Mth.sin(yaw)));
      return origin.add(f.scale(frl.x)).add(r.scale(frl.y)).add(0.0, frl.z, 0.0);
   }

   void update(float partial) {
      double t = this.time();

      for (Cutscene.Ev e : this.events) {
         if (!e.fired && t >= e.t) {
            e.fired = true;
            e.run.run();
         }
      }

      Vec3 origin = this.anchorStart;
      if (this.follow) {
         Entity a = this.anchor();
         if (a != null) {
            origin = a.getEyePosition(partial);
         }
      }

      if (!this.keys.isEmpty()) {
         Cutscene.Key a = this.keys.get(0);
         Cutscene.Key b = a;

         for (int i = 0; i < this.keys.size(); i++) {
            if (this.keys.get(i).t <= t) {
               a = this.keys.get(i);
            }

            if (this.keys.get(i).t > t) {
               b = this.keys.get(i);
               break;
            }

            b = this.keys.get(i);
         }

         Vec3 pos;
         Vec3 look;
         double fov;
         double roll;
         if (a != b && !b.cut) {
            float k = b.ease.apply((float)((t - a.t) / Math.max(1.0E-6, b.t - a.t)));
            pos = a.pos.lerp(b.pos, (double)k);
            look = a.look.lerp(b.look, (double)k);
            fov = Mth.lerp((double)k, a.fov, b.fov);
            roll = Mth.lerp((double)k, a.roll, b.roll);
         } else {
            pos = a.pos;
            look = a.look;
            fov = a.fov;
            roll = a.roll;
         }

         this.outPos = this.local(origin, pos);
         Vec3 target = this.local(origin, look);
         Vec3 d = target.subtract(this.outPos);
         double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
         this.outYaw = (float)(Mth.atan2(d.z, d.x) * 180.0 / (float) Math.PI) - 90.0F;
         this.outPitch = (float)(-(Mth.atan2(d.y, horiz) * 180.0 / (float) Math.PI));
         this.outFov = fov;
         this.outRoll = (float)roll;
      }
   }

   private static final class Ev {
      final double t;
      final Runnable run;
      boolean fired;

      Ev(double t, Runnable run) {
         this.t = t;
         this.run = run;
      }
   }

   public static record Key(double t, Vec3 pos, Vec3 look, double fov, double roll, Ease ease, boolean cut) {
   }
}
