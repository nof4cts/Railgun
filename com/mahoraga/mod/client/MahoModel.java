package com.mahoraga.mod.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahoraga.mod.MahoragaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

final class MahoModel {
   static final ResourceLocation FILE = new ResourceLocation("mahoraga", "models/entity/mahoraga.json");
   static final int ROOT = 0;
   static final int HIPS = 1;
   static final int CHEST = 2;
   static final int HEAD = 3;
   static final int WHEEL = 4;
   static final int L_SHOULDER = 5;
   static final int L_ELBOW = 6;
   static final int L_WRIST = 7;
   static final int R_SHOULDER = 8;
   static final int R_ELBOW = 9;
   static final int R_WRIST = 10;
   static final int R_HIP = 11;
   static final int R_KNEE = 12;
   static final int L_HIP = 13;
   static final int L_KNEE = 14;
   private static MahoModel instance;
   final int[] parent;
   final Vector3f[] pivot;
   final int[] meshBone;
   final float[][] verts;
   int wheelMesh = -1;
   int bladeMesh = -1;
   int bladeVert;
   Vector3f wheelCenter = new Vector3f();
   Vector3f wheelAxis = new Vector3f(0.0F, 1.0F, 0.0F);

   private MahoModel(JsonObject j) {
      JsonArray bones = j.getAsJsonArray("bones");
      int nb = bones.size();
      this.parent = new int[nb];
      this.pivot = new Vector3f[nb];
      Matrix4f[] bind = new Matrix4f[nb];

      for (int i = 0; i < nb; i++) {
         JsonObject b = bones.get(i).getAsJsonObject();
         this.parent[i] = b.get("parent").isJsonNull() ? -1 : b.get("parent").getAsInt();
         JsonArray m = b.getAsJsonArray("m");
         float[] f = new float[16];

         for (int k = 0; k < 16; k++) {
            f[k] = m.get(k).getAsFloat();
         }

         bind[i] = new Matrix4f().set(f);
         this.pivot[i] = bind[i].getTranslation(new Vector3f());
      }

      JsonArray meshes = j.getAsJsonArray("meshes");
      this.meshBone = new int[meshes.size()];
      this.verts = new float[meshes.size()][];

      for (int i = 0; i < meshes.size(); i++) {
         JsonObject m = meshes.get(i).getAsJsonObject();
         int b = m.get("bone").getAsInt();
         this.meshBone[i] = b;
         JsonArray v = m.getAsJsonArray("v");
         float[] out = new float[v.size()];
         Matrix4f W = bind[b];
         Matrix3f N = new Matrix3f(W).normal();

         for (int k = 0; k < v.size(); k += 8) {
            Vector4f p = W.transform(new Vector4f(v.get(k).getAsFloat(), v.get(k + 1).getAsFloat(), v.get(k + 2).getAsFloat(), 1.0F));
            Vector3f n = N.transform(new Vector3f(v.get(k + 5).getAsFloat(), v.get(k + 6).getAsFloat(), v.get(k + 7).getAsFloat())).normalize();
            out[k] = p.x;
            out[k + 1] = p.y;
            out[k + 2] = p.z;
            out[k + 3] = v.get(k + 3).getAsFloat();
            out[k + 4] = v.get(k + 4).getAsFloat();
            out[k + 5] = n.x;
            out[k + 6] = n.y;
            out[k + 7] = n.z;
         }

         this.verts[i] = out;
         String part = m.get("part").getAsString();
         if (part.startsWith("rueda")) {
            this.wheelMesh = i;
            Vector3f c = new Vector3f();

            for (int k = 0; k < out.length; k += 8) {
               c.add(out[k], out[k + 1], out[k + 2]);
            }

            this.wheelCenter = c.div((float)out.length / 8.0F);
            this.wheelAxis = W.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
         }

         if (part.startsWith("der brazo 1")) {
            this.bladeMesh = i;
            float low = Float.MAX_VALUE;

            for (int k = 0; k < out.length; k += 8) {
               if (out[k + 1] < low) {
                  low = out[k + 1];
                  this.bladeVert = k;
               }
            }
         }
      }
   }

   static MahoModel get() {
      if (instance == null) {
         try (Reader r = new InputStreamReader(Minecraft.getInstance().getResourceManager().open(FILE), StandardCharsets.UTF_8)) {
            instance = new MahoModel(JsonParser.parseReader(r).getAsJsonObject());
         } catch (Exception var5) {
            throw new IllegalStateException("Could not load the Mahoraga model", var5);
         }
      }

      return instance;
   }

   private static float smooth(float a, float b, float x) {
      float k = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return k * k * (3.0F - 2.0F * k);
   }

   private static float snap(float a, float b, float x) {
      float k = 1.0F - Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
      return 1.0F - k * k * k;
   }

   Vec3 offset(MahoragaEntity e, float partial) {
      float t = e.actionAge(partial);

      return switch (e.action()) {
         case 2 -> new Vec3(0.0, -0.18 * (double)smooth(11.0F, 13.0F, t) * (double)(1.0F - smooth(14.0F, 22.0F, t)), 0.0);
         case 3 -> new Vec3(
         0.0,
         -0.45 * (double)smooth(0.0F, 7.0F, t) * (double)(1.0F - smooth(7.0F, 10.0F, t))
            - 0.4 * (double)smooth(24.0F, 27.0F, t) * (double)(1.0F - smooth(34.0F, 44.0F, t)),
         0.0
      );
         default -> Vec3.ZERO;
         case 5 -> {
            float wind = smooth(0.0F, 4.0F, t) * (1.0F - snap(4.0F, 6.0F, t));
            float ext = snap(4.0F, 6.0F, t) * (1.0F - smooth(9.0F, 14.0F, t));
            yield new Vec3(0.0, -0.07 * (double)ext - 0.06 * (double)wind, 0.4 * (double)ext - 0.12 * (double)wind);
         }
         case 6 -> {
            float ext = snap(6.0F, 9.0F, t) * (1.0F - smooth(12.0F, 18.0F, t));
            yield new Vec3(0.0, -0.05 * (double)ext, -0.15 * (double)ext);
         }
         case 7 -> new Vec3(
         0.0,
         -0.08 * (double)smooth(0.0F, 6.0F, t) * (double)(1.0F - smooth(34.0F, 40.0F, t)),
         0.12 * (double)Mth.sin(t * 0.8F) * (double)smooth(0.0F, 6.0F, t) * (double)(1.0F - smooth(34.0F, 40.0F, t))
      );
         case 11, 12 -> {
            float wind = smooth(0.0F, 5.0F, t) * (1.0F - snap(5.0F, 8.0F, t));
            float strike = snap(5.0F, 8.0F, t) * (1.0F - smooth(11.0F, 16.0F, t));
            yield new Vec3(0.0, -0.1 * (double)(wind + strike), 0.3 * (double)strike - 0.1 * (double)wind);
         }
         case 14 -> new Vec3(0.0, -0.15 * (double)smooth(0.0F, 8.0F, t), 0.0);
         case 16 -> new Vec3(0.0, -0.35 * (double)snap(11.0F, 13.0F, t) * (double)(1.0F - smooth(18.0F, 34.0F, t)), 0.0);
      };
   }

   Quaternionf[] pose(MahoragaEntity e, float partial) {
      int n = this.parent.length;
      Quaternionf[] q = new Quaternionf[n];

      for (int i = 0; i < n; i++) {
         q[i] = new Quaternionf();
      }

      float pos = e.walkAnimation.position(partial);
      float amt = Math.min(1.0F, e.walkAnimation.speed(partial) * 1.6F);
      float sw = Mth.sin(pos * 0.55F) * 0.6F * amt;
      float time = (float)e.tickCount + partial;
      q[11].rotateX(-sw);
      q[13].rotateX(sw);
      q[12].rotateX(0.6F * amt * Math.max(0.0F, Mth.sin(pos * 0.55F + 1.3F)));
      q[14].rotateX(0.6F * amt * Math.max(0.0F, -Mth.sin(pos * 0.55F + 1.3F)));
      float swl = Mth.sin(pos * 0.55F - 0.5F) * 0.6F * amt;
      q[5].rotateZ(0.16F * amt).rotateX(-sw * 0.85F);
      q[8].rotateZ(-0.16F * amt).rotateX(sw * 0.85F);
      q[2].rotateY(sw * 0.16F).rotateX(0.04F * Mth.sin(time * 0.08F) + 0.06F * amt);
      q[6].rotateX(-0.15F - 0.55F * Math.max(0.0F, swl));
      q[9].rotateX(-0.15F - 0.55F * Math.max(0.0F, -swl));
      q[3].rotateY(-sw * 0.1F);
      int act = e.action();
      float t = e.actionAge(partial);
      float busyR = 0.0F;
      float busyL = 0.0F;
      switch (act) {
         case 1:
            float up = smooth(0.0F, 5.0F, t) * (1.0F - smooth(6.0F, 8.0F, t));
            float down = smooth(6.0F, 8.0F, t) * (1.0F - smooth(11.0F, 14.0F, t));
            q[8].rotateX(-2.5F * up - 0.6F * down).rotateY(-0.5F * down);
            q[9].rotateX(-0.6F * up);
            q[2].rotateY(0.4F * up - 0.5F * down);
            break;
         case 2: {
            float lift = smooth(0.0F, 9.0F, t) * (1.0F - smooth(10.0F, 12.0F, t));
            q[11].rotateX(-1.4F * lift);
            q[12].rotateX(1.2F * lift);
            q[2].rotateX(-0.2F * lift + 0.25F * smooth(10.0F, 12.0F, t) * (1.0F - smooth(15.0F, 20.0F, t)));
            q[5].rotateZ(0.5F * lift);
            q[8].rotateZ(-0.5F * lift);
            break;
         }
         case 3: {
            float crouch = smooth(0.0F, 7.0F, t) * (1.0F - smooth(7.0F, 10.0F, t));
            float air = smooth(8.0F, 12.0F, t) * (1.0F - smooth(22.0F, 26.0F, t));
            float hammer = smooth(10.0F, 16.0F, t) * (1.0F - smooth(24.0F, 26.0F, t));
            float smash = smooth(24.0F, 27.0F, t) * (1.0F - smooth(34.0F, 44.0F, t));

            for (int h : new int[]{11, 13}) {
               q[h].rotateX(-0.9F * crouch - 0.7F * air - 0.8F * smash);
            }

            for (int k : new int[]{12, 14}) {
               q[k].rotateX(1.5F * crouch + 1.3F * air + 1.2F * smash);
            }

            q[2].rotateX(0.35F * crouch - 0.15F * hammer + 0.6F * smash);
            q[8].rotateY(0.35F * hammer + 0.3F * smash).rotateX(-1.2F * air - 1.7F * hammer - 0.9F * smash);
            q[5].rotateY(-0.35F * hammer - 0.3F * smash).rotateX(-1.2F * air - 1.7F * hammer - 0.9F * smash);
            q[9].rotateX(-0.4F * hammer);
            q[6].rotateX(-0.4F * hammer);
            break;
         }
         case 4: {
            float raise = smooth(0.0F, 6.0F, t) * (1.0F - smooth(7.0F, 10.0F, t));
            float hold = smooth(7.0F, 10.0F, t) * (1.0F - smooth(23.0F, 26.0F, t));
            float back = smooth(23.0F, 27.0F, t) * (1.0F - smooth(32.0F, 40.0F, t));
            q[8].rotateY(-0.4F * raise + 0.9F * hold - 0.9F * back).rotateX(-2.6F * raise - 0.6F * hold - 1.9F * back);
            q[9].rotateX(-0.6F * raise - 0.4F * hold);
            q[2].rotateY(0.4F * raise - 0.6F * hold + 0.6F * back).rotateX(0.2F * hold - 0.1F * back);
            q[5].rotateX(-0.5F * hold);
            q[11].rotateX(0.3F * back);
            q[13].rotateX(-0.5F * back);
            break;
         }
         case 5: {
            int side = e.punchSide();
            float in = side == 0 ? 1.0F : -1.0F;
            float wind = smooth(0.0F, 4.0F, t) * (1.0F - snap(4.0F, 6.0F, t));
            float ext = snap(4.0F, 6.0F, t) * (1.0F - smooth(9.0F, 14.0F, t));
            int armx = side == 0 ? 8 : 5;
            int elbowx = side == 0 ? 9 : 6;
            q[armx].rotateY(in * 0.18F * ext).rotateX(0.45F * wind - 1.62F * ext);
            q[elbowx].rotateX(-1.5F * wind);
            float twist = in * (0.65F * ext - 0.4F * wind);
            q[2].rotateY(twist).rotateX(0.12F * ext - 0.05F * wind);
            q[3].rotateY(-0.45F * twist);
            q[side == 0 ? 13 : 11].rotateX(-0.35F * ext);
            q[side == 0 ? 11 : 13].rotateX(0.3F * ext);
            q[12].rotateX(0.25F * wind + 0.2F * ext);
            q[14].rotateX(0.25F * wind + 0.2F * ext);
            if (side == 0) {
               busyR = Math.max(wind, ext);
            } else {
               busyL = Math.max(wind, ext);
            }
            break;
         }
         case 6: {
            float chamber = smooth(0.0F, 6.0F, t) * (1.0F - snap(6.0F, 9.0F, t));
            float ext = snap(6.0F, 9.0F, t) * (1.0F - smooth(12.0F, 18.0F, t));
            q[11].rotateX(-1.1F * chamber - 1.75F * ext);
            q[12].rotateX(1.8F * chamber + 0.1F * ext);
            q[14].rotateX(0.25F * (chamber + ext));
            q[2].rotateX(0.1F * chamber - 0.3F * ext).rotateY(-0.15F * ext);
            q[8].rotateZ(-0.45F * (chamber + ext));
            q[5].rotateZ(0.45F * (chamber + ext));
            break;
         }
         case 7: {
            float w = smooth(0.0F, 6.0F, t) * (1.0F - smooth(34.0F, 40.0F, t));
            float ph = t / 4.0F;
            int ni = (int)Math.floor((double)ph);
            float u = ph - (float)ni;
            float k = u < 0.35F ? snap(0.0F, 0.35F, u) : 1.0F - smooth(0.35F, 1.0F, u);
            float kr = (ni % 2 == 0 ? k : 0.0F) * w;
            float kl = (ni % 2 == 1 ? k : 0.0F) * w;
            boolean hook = ni / 2 % 2 == 1;
            q[8].rotateY(hook ? 0.55F * kr : 0.2F * kr).rotateX(-0.5F * w - 1.1F * kr);
            q[5].rotateY(hook ? -0.55F * kl : -0.2F * kl).rotateX(-0.5F * w - 1.1F * kl);
            q[9].rotateX(-1.3F * (w - kr)).rotateZ(hook ? 1.2F * kr : 0.0F);
            q[6].rotateX(-1.3F * (w - kl)).rotateZ(hook ? -1.2F * kl : 0.0F);
            float twist = 0.5F * (kr - kl);
            q[2].rotateY(twist).rotateX(0.12F * w + 0.05F * Mth.sin(ph * (float) Math.PI));
            q[3].rotateY(-0.5F * twist);
            q[13].rotateX(-0.3F * w);
            q[11].rotateX(0.25F * w);
            q[12].rotateX(0.3F * w);
            q[14].rotateX(0.3F * w);
            busyR = w;
            busyL = w;
            break;
         }
         case 8: {
            float bend = smooth(0.0F, 7.0F, t) * (1.0F - smooth(9.0F, 14.0F, t));
            float lift = smooth(8.0F, 13.0F, t) * (1.0F - smooth(15.0F, 17.0F, t));
            float thr = smooth(15.0F, 17.0F, t) * (1.0F - smooth(19.0F, 24.0F, t));
            q[2].rotateX(0.7F * bend - 0.25F * lift + 0.3F * thr);
            q[5].rotateX(-1.0F * bend - 2.9F * lift - 1.3F * thr);
            q[8].rotateX(-0.4F * bend);

            for (int h : new int[]{11, 13}) {
               q[h].rotateX(-0.5F * bend);
            }

            for (int k : new int[]{12, 14}) {
               q[k].rotateX(0.8F * bend);
            }
            break;
         }
         case 9: {
            float reach = smooth(0.0F, 8.0F, t) * (1.0F - smooth(50.0F, 60.0F, t));
            float squeeze = smooth(14.0F, 43.0F, t);
            float clap = smooth(43.0F, 46.0F, t);
            float in = 0.3F + 0.2F * squeeze + 0.2F * clap;
            q[8].rotateY(in * reach).rotateX(-1.25F * reach);
            q[5].rotateY(-in * reach).rotateX(-1.25F * reach);
            q[9].rotateX(-0.45F * reach);
            q[6].rotateX(-0.45F * reach);
            q[2].rotateX(0.12F * reach + 0.08F * clap);
            break;
         }
         case 10: {
            float raise = smooth(0.0F, 8.0F, t) * (1.0F - smooth(18.0F, 24.0F, t));
            float recoil = smooth(12.0F, 13.0F, t) * (1.0F - smooth(13.0F, 18.0F, t));
            q[8].rotateX(-1.6F * raise + 0.3F * recoil);
            q[2].rotateX(-0.15F * recoil).rotateY(0.25F * raise);
            q[5].rotateX(0.3F * raise);
            break;
         }
         case 11:
         case 12: {
            boolean right = act == 12;
            float in = right ? 1.0F : -1.0F;
            float wind = smooth(0.0F, 5.0F, t) * (1.0F - snap(5.0F, 8.0F, t));
            float strike = snap(5.0F, 8.0F, t) * (1.0F - smooth(11.0F, 16.0F, t));
            float over = smooth(7.0F, 10.0F, t) * (1.0F - smooth(10.0F, 14.0F, t));
            int arm = right ? 8 : 5;
            int elbow = right ? 9 : 6;
            q[arm].rotateY(in * (-1.0F * wind + 0.75F * strike + 0.3F * over)).rotateX(-1.0F * wind - 1.5F * strike);
            q[elbow].rotateZ(in * 1.45F * (wind + strike));
            float twist = in * (-0.6F * wind + 0.9F * strike + 0.25F * over);
            q[2].rotateY(twist).rotateX(0.16F * strike + 0.06F * wind);
            q[3].rotateY(-0.5F * twist);
            q[right ? 13 : 11].rotateX(-0.3F * strike).rotateY(in * 0.2F * strike);
            q[right ? 11 : 13].rotateX(0.35F * strike);
            q[12].rotateX(0.35F * wind + 0.25F * strike);
            q[14].rotateX(0.35F * wind + 0.25F * strike);
            if (right) {
               busyR = Math.max(wind, strike);
            } else {
               busyL = Math.max(wind, strike);
            }
            break;
         }
         case 13:
            q[3].rotateX(0.18F);
            q[2].rotateX(0.08F + 0.03F * Mth.sin(time * 0.1F));
            busyR = 1.0F;
            busyL = 1.0F;
            break;
         case 14: {
            float wind = smooth(0.0F, 6.0F, t) * (1.0F - smooth(8.0F, 12.0F, t));
            float run = smooth(6.0F, 10.0F, t) * (1.0F - smooth(66.0F, 70.0F, t));
            float ph = time * 0.9F;
            q[2].rotateX(0.5F * wind + 0.4F * run);
            q[3].rotateX(-0.25F * run);
            q[8].rotateX(0.6F * wind + 1.1F * run * Mth.sin(ph));
            q[5].rotateX(0.6F * wind - 1.1F * run * Mth.sin(ph));
            q[9].rotateX(-1.3F * run);
            q[6].rotateX(-1.3F * run);
            q[11].rotateX(-0.9F * run * Mth.sin(ph));
            q[13].rotateX(0.9F * run * Mth.sin(ph));
            q[12].rotateX(1.2F * run * Math.max(0.0F, Mth.sin(ph + 1.3F)));
            q[14].rotateX(1.2F * run * Math.max(0.0F, -Mth.sin(ph + 1.3F)));
            busyR = Math.max(wind, run);
            busyL = busyR;
            break;
         }
         case 15: {
            float hold = smooth(0.0F, 3.0F, t) * (1.0F - smooth(5.0F, 9.0F, t));
            float lift = smooth(3.0F, 8.0F, t) * (1.0F - smooth(9.0F, 11.0F, t));
            float fling = snap(9.0F, 11.0F, t) * (1.0F - smooth(12.0F, 16.0F, t));
            q[8].rotateY(0.35F * hold).rotateX(-1.3F * hold - 2.7F * lift - 1.0F * fling);
            q[5].rotateY(-0.35F * hold).rotateX(-1.3F * hold - 2.7F * lift - 1.0F * fling);
            q[2].rotateX(-0.25F * lift + 0.45F * fling);
            q[12].rotateX(0.3F * lift);
            q[14].rotateX(0.3F * lift);
            busyR = 1.0F;
            busyL = 1.0F;
            break;
         }
         case 16: {
            float lift = smooth(0.0F, 10.0F, t) * (1.0F - snap(10.0F, 12.0F, t));
            float impact = snap(11.0F, 13.0F, t) * (1.0F - smooth(18.0F, 34.0F, t));
            q[11].rotateX(-1.6F * lift - 0.2F * impact);
            q[12].rotateX(1.7F * lift + 0.3F * impact);
            q[14].rotateX(0.3F * lift + 0.6F * impact);
            q[13].rotateX(-0.4F * impact);
            q[2].rotateX(-0.15F * lift + 0.4F * impact);
            q[8].rotateZ(-0.9F * lift - 0.5F * impact).rotateX(-0.4F * lift);
            q[5].rotateZ(0.9F * lift + 0.5F * impact).rotateX(-0.4F * lift);
            q[3].rotateX(0.25F * impact);
            busyR = 1.0F;
            busyL = 1.0F;
         }
      }

      boolean fighting = !e.intro() && (act == 0 || act == 5 || act == 11 || act == 12 || act == 6 || act == 2 || act == 7);
      if (fighting) {
         float g = 1.0F - 0.85F * amt;
         float gr = g * (1.0F - busyR);
         float gl = g * (1.0F - busyL);
         float breath = Mth.sin(time * 0.11F);
         q[8].rotateX(-0.55F * gr).rotateY(0.2F * gr);
         q[5].rotateX(-0.6F * gl).rotateY(-0.2F * gl);
         q[9].rotateX(-1.35F * gr);
         q[6].rotateX(-1.45F * gl);
         q[2].rotateX(0.06F + 0.025F * breath);
         q[3].rotateX(-0.02F * breath);
      }

      if (e.intro() && act == 0) {
         q[3].rotateX(0.18F);
      }

      return q;
   }

   Matrix4f[] joints(Quaternionf[] q) {
      int n = this.parent.length;
      Matrix4f[] m = new Matrix4f[n];

      for (int i = 0; i < n; i++) {
         Vector3f p = this.pivot[i];
         Matrix4f local = new Matrix4f().translate(p).rotate(q[i]).translate(-p.x, -p.y, -p.z);
         m[i] = this.parent[i] < 0 ? local : new Matrix4f(m[this.parent[i]]).mul(local);
      }

      return m;
   }

   void draw(PoseStack ps, VertexConsumer vc, Matrix4f[] jm, float wheelDeg, int light, int overlay, float r, float g, float b) {
      this.draw(ps, vc, jm, wheelDeg, light, overlay, r, g, b, new float[]{r, g, b}, light);
   }

   void draw(
      PoseStack ps, VertexConsumer vc, Matrix4f[] jm, float wheelDeg, int light, int overlay, float r, float g, float b, float[] wheelRgb, int wheelLight
   ) {
      Matrix4f pose = ps.last().pose();
      Matrix3f nrm = ps.last().normal();
      Vector4f v4 = new Vector4f();
      Vector3f n3 = new Vector3f();

      for (int i = 0; i < this.verts.length; i++) {
         Matrix4f m = new Matrix4f(pose).mul(jm[this.meshBone[i]]);
         if (i == this.wheelMesh) {
            m.translate(this.wheelCenter)
               .rotate(new Quaternionf().rotateAxis(wheelDeg * (float) (Math.PI / 180.0), this.wheelAxis))
               .translate(-this.wheelCenter.x, -this.wheelCenter.y, -this.wheelCenter.z);
         }

         Matrix3f mn = new Matrix3f(nrm).mul(new Matrix3f(jm[this.meshBone[i]]));
         float[] v = this.verts[i];
         boolean wheel = i == this.wheelMesh;
         float cr = wheel ? wheelRgb[0] : r;
         float cg = wheel ? wheelRgb[1] : g;
         float cb = wheel ? wheelRgb[2] : b;
         int li = wheel ? wheelLight : light;

         for (int k = 0; k < v.length; k += 24) {
            for (int c = 0; c < 4; c++) {
               int o = k + Math.min(c, 2) * 8;
               m.transform(v4.set(v[o], v[o + 1], v[o + 2], 1.0F));
               mn.transform(n3.set(v[o + 5], v[o + 6], v[o + 7])).normalize();
               vc.vertex(v4.x, v4.y, v4.z, cr, cg, cb, 1.0F, v[o + 3], v[o + 4], overlay, li, n3.x, n3.y, n3.z);
            }
         }
      }
   }

   Vec3 joint(Matrix4f[] jm, int j) {
      Vector4f p = jm[j].transform(new Vector4f(this.pivot[j].x, this.pivot[j].y, this.pivot[j].z, 1.0F));
      return new Vec3((double)p.x, (double)p.y, (double)p.z);
   }

   Vec3 wheel(Matrix4f[] jm) {
      int b = this.wheelMesh < 0 ? 3 : this.meshBone[this.wheelMesh];
      Vector4f p = jm[b].transform(new Vector4f(this.wheelCenter.x, this.wheelCenter.y, this.wheelCenter.z, 1.0F));
      return new Vec3((double)p.x, (double)p.y, (double)p.z);
   }

   Vec3 bladeTip(Matrix4f[] jm) {
      if (this.bladeMesh < 0) {
         return Vec3.ZERO;
      } else {
         float[] v = this.verts[this.bladeMesh];
         Vector4f p = jm[this.meshBone[this.bladeMesh]].transform(new Vector4f(v[this.bladeVert], v[this.bladeVert + 1], v[this.bladeVert + 2], 1.0F));
         return new Vec3((double)p.x, (double)p.y, (double)p.z);
      }
   }
}
