package com.avatar.mod.client;

import com.avatar.mod.Net;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.client.event.RenderPlayerEvent.Post;
import net.minecraftforge.client.event.RenderPlayerEvent.Pre;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;

final class Elems {
   static final float AIR = 0.81F;
   static final float WATER = 0.885F;
   static final float ICE = 0.94F;
   static final float FOAM = -2.0F;
   private static final List<Elems.Fx> FX = new ArrayList<>();
   private static final List<Elems.Rock> ROCKS = new ArrayList<>();
   static final Map<Integer, Long> GAUNT = new HashMap<>();
   static final Map<Integer, Long> SCOOT = new HashMap<>();
   static final Map<Integer, Long> GLIDE = new HashMap<>();
   static final Set<Integer> DRAWN = new HashSet<>();
   private static final Map<Integer, BlockState> SHOT_BLOCK = new HashMap<>();
   static int bloodVictim = -1;
   static Vec3 lastTornado;
   static Vec3 lastCoffin;
   private static long bloodUntil;

   private Elems() {
   }

   static void clear() {
      FX.clear();
      ROCKS.clear();
      GAUNT.clear();
      SCOOT.clear();
      GLIDE.clear();
      SHOT_BLOCK.clear();
      DRAWN.clear();
   }

   static boolean active() {
      return !FX.isEmpty() || !ROCKS.isEmpty() || !SCOOT.isEmpty() || !DRAWN.isEmpty();
   }

   static boolean on(Map<Integer, Long> m, Entity e) {
      Long t = m.get(e.getId());
      return t != null && t > FireFx.gameTime();
   }

   static BlockState groundBlock(Vec3 at) {
      Minecraft mc = Minecraft.getInstance();
      BlockPos p = BlockPos.containing(at).above();

      for (int i = 0; i < 8; p = p.below()) {
         BlockState st = mc.level.getBlockState(p);
         if (st.isFaceSturdy(mc.level, p, Direction.UP)) {
            return st.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : st;
         }

         i++;
      }

      return Blocks.STONE.defaultBlockState();
   }

   static Vec3 groundAt(Vec3 at) {
      Minecraft mc = Minecraft.getInstance();
      BlockPos p = BlockPos.containing(at).above();

      for (int i = 0; i < 8; p = p.below()) {
         if (mc.level.getBlockState(p).isFaceSturdy(mc.level, p, Direction.UP)) {
            return new Vec3(at.x, (double)(p.getY() + 1), at.z);
         }

         i++;
      }

      return at;
   }

   private static FireFx.Puff puff(Vec3 at, Vec3 v, float size, float grow, float cool, int life, int mode, float pal) {
      FireFx.Puff p = new FireFx.Puff(at, v, size, grow, 1.0F, cool, pal, life, mode);
      p.palette = pal;
      return p;
   }

   static void dust(Vec3 at, float size, int n) {
      for (int i = 0; i < n; i++) {
         Vec3 v = FireFx.rand(1.0).multiply(1.0, 0.4, 1.0).normalize().scale(0.05 + (double)FireFx.RNG.nextFloat() * 0.12 * (double)size).add(0.0, 0.03, 0.0);
         FireFx.PUFFS
            .add(
               new FireFx.Puff(
                  at.add(FireFx.rand(0.3 * (double)size)),
                  v,
                  0.4F * size + FireFx.RNG.nextFloat() * 0.4F,
                  1.05F,
                  0.08F,
                  0.01F,
                  0.55F,
                  25 + FireFx.RNG.nextInt(20),
                  0
               )
            );
      }
   }

   static void debris(Vec3 at, BlockState st, int n, double speed) {
      for (int i = 0; i < n; i++) {
         Vec3 v = FireFx.rand(1.0).normalize().scale(speed * (0.4 + (double)FireFx.RNG.nextFloat())).add(0.0, speed * 0.8, 0.0);
         ROCKS.add(new Elems.Rock(st, at, v, 0.18F + FireFx.RNG.nextFloat() * 0.22F, 18 + FireFx.RNG.nextInt(14), true));
      }
   }

   static void splash(Vec3 at, float size, float pal) {
      for (int i = 0; i < 8 + (int)(size * 10.0F); i++) {
         Vec3 v = FireFx.rand(1.0).normalize().scale(0.08 + (double)FireFx.RNG.nextFloat() * 0.18).add(0.0, 0.12, 0.0);
         FireFx.Puff p = puff(at, v, size * (0.25F + FireFx.RNG.nextFloat() * 0.3F), 1.02F, 0.06F, 12 + FireFx.RNG.nextInt(8), 14, pal);
         p.gravity = 0.03F;
         FireFx.PUFFS.add(p);
      }
   }

   static void receive(Net.Fx f, Entity caster) {
      Minecraft mc = Minecraft.getInstance();
      long now = FireFx.gameTime();
      boolean self = caster == mc.player;
      switch (f.kind) {
         case 19:
         case 20:
         case 21:
         case 22:
         case 23:
         case 24:
         case 25:
         case 26:
         case 30:
         case 31:
         case 33:
         case 35:
            FX.add(new Elems.Fx(new Net.Fx(f.kind, f.a, f.b, f.f, f.caster, f.other, f.dur > 0 ? f.dur : 10, f.flags), now));
         case 27:
         case 28:
         case 29:
         case 32:
         case 34:
         default:
            switch (f.kind) {
               case 23:
                  bloodVictim = f.other;
                  bloodUntil = now + (long)f.dur;
                  if (mc.player != null && mc.player.position().distanceTo(f.a) < 40.0) {
                     float[] c = Arts.screen(f.a.add(0.0, 1.0, 0.0));
                     Impact.hit(6, 0, 3, 0, c[0], c[1], true);
                     Impact.hit(5, 3, 3, 5, c[0], c[1], true);
                  }
               case 24:
               case 25:
               case 26:
               case 31:
               case 35:
               default:
                  break;
               case 27:
                  dust(f.a, f.f, 8 + (int)(f.f * 6.0F));
                  debris(f.a, groundBlock(f.a), 3 + (int)(f.f * 4.0F), 0.25);
                  FireFx.shake(f.a, 0.5F * f.f);
                  if ((f.flags & 2) != 0 && self) {
                     float[] c = Arts.screen(f.a);
                     Impact.hit(7, 0, 2, 1, c[0], c[1], true);
                     FireFx.fovKick = Math.max(FireFx.fovKick, 0.06F);
                  }
                  break;
               case 28:
                  GAUNT.put(f.caster, now + (long)f.dur);
                  break;
               case 29:
                  if (self) {
                     Impact.hit(9, 0, 16, 12, 0.5F, 0.1F, false);
                  }

                  FireFx.shake(f.a, 0.6F);
                  dust(f.a, 1.2F, 10);
                  break;
               case 30:
                  lastCoffin = f.a;
                  break;
               case 32:
                  SCOOT.put(f.caster, now + (long)f.dur);
                  if (f.dur == 0 && self) {
                     mc.player.setMaxUpStep(0.6F);
                  }
                  break;
               case 33:
                  lastTornado = f.a;
                  if (mc.player != null && mc.player.position().distanceTo(f.a) < 48.0) {
                     float[] c = Arts.screen(f.a.add(0.0, 4.0, 0.0));
                     Impact.hit(0, 8, 2, 0, c[0], c[1], true);
                     Impact.hit(5, 10, 3, 6, c[0], c[1], true);
                  }
                  break;
               case 34:
                  GLIDE.put(f.caster, now + (long)f.dur);
                  break;
               case 36:
                  if (self) {
                     FireFx.startCut((FireFx.Cine)(switch (f.other) {
                        case 1 -> new Elems.WaveCut(f);
                        case 2 -> new Elems.BloodCut(f);
                        case 3 -> new Elems.OctoCut(f);
                        case 4 -> new Elems.CoffinCut(f);
                        default -> new Elems.TornadoCut(f);
                     }));
                  }
                  break;
               case 37:
                  if (f.dur > 0) {
                     DRAWN.add(f.caster);
                  } else {
                     DRAWN.remove(f.caster);
                  }

                  if (caster != null) {
                     splash(caster.position().add(0.0, 1.0, 0.0), 0.6F, 0.885F);
                  }
            }
      }
   }

   static void impact(Vec3 at, Vec3 dir, float size, int elem) {
      switch (elem) {
         case 1:
            splash(at, size, 0.885F);
            break;
         case 2:
            BlockState st = Blocks.STONE.defaultBlockState();
            debris(at, st, 10, 0.35);
            dust(at, 1.6F, 14);
            FireFx.shake(at, 1.0F);
            break;
         case 3:
            for (int i = 0; i < 10; i++) {
               Vec3 v = FireFx.rand(1.0).normalize().scale(0.25);
               FireFx.PUFFS.add(puff(at, v, 0.35F + FireFx.RNG.nextFloat() * 0.3F, 1.06F, 0.12F, 8, 14, 0.81F));
            }

            FireFx.hazeAdd(at, 2.5F, 1.0F);
            break;
         case 4:
            splash(at, size * 0.7F, 0.94F);

            for (int i = 0; i < 6; i++) {
               FireFx.EMBERS.add(new FireFx.Ember(at, FireFx.rand(0.2), 6 + FireFx.RNG.nextInt(5), 0.9F, 0.02F).palette(0.94F));
            }
      }
   }

   static void trail(FireFx.Shot s) {
      Vec3 p = s.pos(0.0F);
      switch (s.elem) {
         case 1:
            FireFx.Puff d = puff(p.add(FireFx.rand(0.1)), s.vel.scale(0.05).add(FireFx.rand(0.03)), s.size * 0.4F, 1.0F, 0.1F, 8, 14, 0.885F);
            d.gravity = 0.03F;
            FireFx.PUFFS.add(d);
            break;
         case 2:
            FireFx.PUFFS.add(new FireFx.Puff(p.add(FireFx.rand(0.3)), FireFx.rand(0.02), 0.5F, 1.05F, 0.05F, 0.01F, 0.4F, 18, 0));
            break;
         case 3:
            FireFx.PUFFS.add(puff(p, FireFx.rand(0.02), s.size * 0.5F, 1.08F, 0.15F, 6, 14, 0.81F));
            break;
         case 4:
            FireFx.EMBERS.add(new FireFx.Ember(p, FireFx.rand(0.05), 5, 0.8F, 0.0F).palette(0.94F));
      }
   }

   static void tick(Minecraft mc, long now) {
      ROCKS.removeIf(rx -> !rx.tick());
      Iterator<Elems.Fx> it = FX.iterator();

      while (it.hasNext()) {
         Elems.Fx x = it.next();
         int t = (int)(now - x.start);
         if (t > x.f.dur + 10) {
            it.remove();
         } else {
            Net.Fx f = x.f;
            Entity c = mc.level.getEntity(f.caster);
            switch (f.kind) {
               case 19:
                  if (t == 2) {
                     splash(f.b, 0.6F, 0.885F);
                  }

                  if (t < f.dur) {
                     for (int i = 0; i < 2; i++) {
                        FireFx.Puff d = puff(
                           f.a.lerp(f.b, (double)FireFx.RNG.nextFloat()),
                           FireFx.rand(0.02),
                           0.08F + FireFx.RNG.nextFloat() * 0.06F,
                           1.0F,
                           0.08F,
                           14,
                           14,
                           0.885F
                        );
                        d.gravity = 0.035F;
                        FireFx.PUFFS.add(d);
                     }
                  }
                  break;
               case 20:
                  if (f.other != 1 || c == null || t >= f.dur) {
                     break;
                  }

                  Vec3 top = c.position().add(0.0, 3.3, 0.0);

                  for (int i = 0; i < 4; i++) {
                     double a = (double)FireFx.RNG.nextFloat() * Math.PI * 2.0;
                     Vec3 v = new Vec3(Math.cos(a) * 0.13, 0.05 + (double)FireFx.RNG.nextFloat() * 0.08, Math.sin(a) * 0.13);
                     FireFx.Puff d = puff(top.add(FireFx.rand(0.2)), v, 0.12F + FireFx.RNG.nextFloat() * 0.1F, 1.0F, 0.04F, 26, 14, 0.885F);
                     d.gravity = 0.03F;
                     d.drag(0.99F);
                     FireFx.PUFFS.add(d);
                  }

                  if (t % 3 == 0) {
                     double a = (double)FireFx.RNG.nextFloat() * Math.PI * 2.0;
                     splash(c.position().add(Math.cos(a) * (double)f.f, 0.1, Math.sin(a) * (double)f.f), 0.35F, 0.885F);
                  }
                  break;
               case 21:
                  if (c != null && t % 3 == 0 && t < f.dur) {
                     Vec3 h = c.position().add(0.0, 1.0, 0.0).add(Arts.fwd(c, 0.0F).scale(0.5));
                     FireFx.EMBERS.add(new FireFx.Ember(h.add(FireFx.rand(0.25)), new Vec3(0.0, 0.03, 0.0), 12, 0.7F, 0.0F).palette(0.885F));
                  }
                  break;
               case 22:
                  if (t < f.dur) {
                     Vec3 right = new Vec3(-f.b.z, 0.0, f.b.x);
                     double front = (double)t * 0.75;

                     for (int i = 0; i < 6; i++) {
                        Vec3 at = f.a
                           .add(f.b.scale(front))
                           .add(right.scale(((double)FireFx.RNG.nextFloat() - 0.5) * (double)f.f))
                           .add(0.0, 3.2 + (double)FireFx.RNG.nextFloat(), 0.0);
                        FireFx.Puff p = puff(at, f.b.scale(0.4).add(0.0, 0.12, 0.0), 0.5F + FireFx.RNG.nextFloat() * 0.4F, 1.03F, 0.06F, 14, 14, 0.94F);
                        p.gravity = 0.03F;
                        FireFx.PUFFS.add(p);
                     }
                  }
               case 23:
               case 24:
               case 27:
               case 28:
               case 29:
               case 32:
               case 34:
               default:
                  break;
               case 25:
                  if (t >= f.dur) {
                     break;
                  }

                  Vec3 front = groundAt(f.a.add(f.b.scale((double)t + 1.0)));
                  BlockState st = groundBlock(front);

                  for (int i = 0; i < 3; i++) {
                     Vec3 at = front.add(f.b.z * ((double)FireFx.RNG.nextFloat() - 0.5) * 1.6, 0.0, -f.b.x * ((double)FireFx.RNG.nextFloat() - 0.5) * 1.6);
                     ROCKS.add(
                        new Elems.Rock(
                           st,
                           at.add(0.0, -0.3, 0.0),
                           new Vec3(0.0, 0.3 + (double)FireFx.RNG.nextFloat() * 0.25, 0.0),
                           0.45F + FireFx.RNG.nextFloat() * 0.3F,
                           12,
                           true
                        )
                     );
                  }

                  dust(front, 0.8F, 2);
                  FireFx.shake(front, 0.25F);
                  break;
               case 26:
                  if (t == 0) {
                     x.block = groundBlock(f.a);
                     dust(f.a, 1.2F, 10);
                     debris(f.a, x.block, 5, 0.2);
                  }
                  break;
               case 30:
                  if (t == 12) {
                     dust(f.a, 1.6F, 16);
                     debris(f.a, groundBlock(f.a), 8, 0.3);
                     FireFx.shake(f.a, 1.2F);
                  }
                  break;
               case 31:
                  if (t < 4) {
                     for (int i = 0; i < 4; i++) {
                        Vec3 v = f.b.scale(0.7 + (double)FireFx.RNG.nextFloat() * 0.4).add(FireFx.rand(0.1));
                        FireFx.Puff p = puff(f.a.add(f.b.scale((double)t * 2.5)), v, 0.4F, 1.1F, 0.1F, 9, 14, 0.81F);
                        p.drag(0.9F);
                        FireFx.PUFFS.add(p);
                     }
                  }
                  break;
               case 33:
                  Vec3 base = f.a.add(f.b.scale((double)t));

                  for (int i = 0; i < 4; i++) {
                     double a = (double)FireFx.RNG.nextFloat() * Math.PI * 2.0;
                     double r = 1.0 + (double)FireFx.RNG.nextFloat() * 2.5;
                     Vec3 at = base.add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                     Vec3 tang = new Vec3(-Math.sin(a), 0.15, Math.cos(a)).scale(0.35);
                     FireFx.PUFFS.add(new FireFx.Puff(at, tang, 0.6F + FireFx.RNG.nextFloat() * 0.5F, 1.04F, 0.05F, 0.01F, 0.45F, 20, 0));
                  }

                  if (t % 6 == 0 && t < f.dur) {
                     ROCKS.add(
                        new Elems.Rock(
                              groundBlock(base), base, new Vec3(FireFx.RNG.nextGaussian() * 0.2, 0.5, FireFx.RNG.nextGaussian() * 0.2), 0.3F, 40, false
                           )
                           .orbit(base, f.b, t)
                     );
                  }
                  break;
               case 35:
                  if (t < 5 && c != null) {
                     FireFx.PUFFS.add(puff(c.position().add(0.0, 0.8, 0.0), FireFx.rand(0.03), 0.5F, 1.08F, 0.1F, 10, 14, 0.81F));
                  }
            }
         }
      }

      for (Entry<Integer, Long> s : SCOOT.entrySet()) {
         Entity e = mc.level.getEntity(s.getKey());
         if (e != null && s.getValue() > now) {
            FireFx.PUFFS.add(puff(e.position().add(0.0, 0.35, 0.0).add(FireFx.rand(0.25)), FireFx.rand(0.02), 0.25F, 1.06F, 0.12F, 8, 14, 0.81F));
            if (e == mc.player) {
               mc.player.setMaxUpStep(1.1F);
            }
         }
      }

      Long g = mc.player == null ? null : GLIDE.get(mc.player.getId());
      if (g != null && g > now && !mc.player.onGround()) {
         Vec3 look = mc.player.getLookAngle();
         Vec3 v = mc.player.getDeltaMovement();
         Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize().scale(0.7);
         double y = look.y < -0.35 ? Math.max(v.y, look.y * 0.7) : Math.max(v.y, -0.05);
         mc.player.setDeltaMovement(Mth.lerp(0.12, v.x, flat.x), y, Mth.lerp(0.12, v.z, flat.z));
         Vec3 r = new Vec3(-look.z, 0.0, look.x).normalize();

         for (int side = -1; side <= 1; side += 2) {
            FireFx.PUFFS.add(puff(mc.player.position().add(0.0, 2.2, 0.0).add(r.scale(1.3 * (double)side)), Vec3.ZERO, 0.15F, 1.05F, 0.15F, 8, 14, 0.81F));
         }
      }

      SCOOT.values().removeIf(t -> t < now - 5L);
      GLIDE.values().removeIf(t -> t < now - 5L);
      GAUNT.values().removeIf(t -> t < now - 5L);
   }

   static void render(Minecraft mc, Vec3 cam, float partial, float time, Matrix4f view, Matrix4f proj, PoseStack ps) {
      long now = FireFx.gameTime();
      Gfx.begin();
      int n = 0;

      for (Elems.Fx x : FX) {
         n += x.ribbons(mc, cam, now, partial, time);
      }

      for (FireFx.Shot s : FireFx.SHOTS) {
         if (s.elem != 0 && s.elem != 2) {
            n += shotRibbons(s, cam, partial, time);
         }
      }

      for (Entry<Integer, Long> sx : SCOOT.entrySet()) {
         Entity e = mc.level.getEntity(sx.getKey());
         if (e != null && sx.getValue() > now) {
            Vec3 c = e.getPosition(partial).add(0.0, 0.55, 0.0);

            for (int k = 0; k < 7; k++) {
               ring(
                  cam,
                  c,
                  0.62F - (float)(k % 3) * 0.06F,
                  time * (0.8F + (float)k * 0.07F) + (float)k * 0.9F,
                  (float)k * 0.45F,
                  0.16F,
                  k % 2 == 0 ? 0.94F : 0.81F
               );
            }

            n++;
         }
      }

      for (int id : DRAWN) {
         Entity e = mc.level.getEntity(id);
         if (e != null && !FireFx.firstPersonSelf(e)) {
            Vec3 c = e.getPosition(partial).add(0.0, 0.95, 0.0);
            List<Vec3> pts = new ArrayList<>();

            for (int i = 0; i < 24; i++) {
               float x = (float)(i % 23) / 23.0F;
               float a = time * 0.12F + x * (float) (Math.PI * 2);
               pts.add(c.add((double)Mth.cos(a) * 0.72, (double)Mth.sin(x * (float) (Math.PI * 2) * 2.0F + time * 0.2F) * 0.12, (double)Mth.sin(a) * 0.72));
            }

            ribbon(cam, pts, 0.13F, 0.885F, 1.0F, 0.2F);
            n++;
         }
      }

      if (n > 0) {
         FireFx.draw(AShaders.flame, 11, view, proj);
      } else {
         Gfx.flushEmpty();
      }

      Gfx.begin();

      for (FireFx.Shot sxx : FireFx.SHOTS) {
         if (sxx.elem == 1) {
            WaterMesh.sphere(sxx.pos(partial), sxx.size * 0.8F, WaterMesh.WATER, 1.0F);
         }
      }

      for (Elems.Fx x : FX) {
         if (x.f.kind == 21) {
            Entity c = mc.level.getEntity(x.f.caster);
            float t = (float)(now - x.start) + partial;
            if (c != null && t < (float)x.f.dur) {
               Vec3 h = c.getPosition(partial).add(0.0, 1.05, 0.0).add(Arts.fwd(c, partial).scale(0.55));

               for (int i = 0; i < 3; i++) {
                  float a = time * 0.25F + (float)i * 2.09F;
                  Vec3 p = h.add((double)Mth.cos(a) * 0.3, (double)Mth.sin(a * 1.3F) * 0.12, (double)Mth.sin(a) * 0.3);
                  WaterMesh.sphere(p, 0.12F, WaterMesh.WATER, 1.0F);
               }
            }
         }
      }

      FireFx.draw(AShaders.flame, 14, view, proj);
      Gfx.begin();

      for (FireFx.Shot sxxx : FireFx.SHOTS) {
         if (sxxx.elem == 4) {
            FireFx.billboard(cam, sxxx.pos(partial), 0.6F, 0.0F, 0.9F, 0.94F, 0.7F);
         }
      }

      for (Elems.Fx xx : FX) {
         Entity c = mc.level.getEntity(xx.f.kind == 23 ? xx.f.other : xx.f.caster);
         float t = (float)(now - xx.start) + partial;
         if (c != null && !(t > (float)xx.f.dur)) {
            if (xx.f.kind == 21) {
               FireFx.billboard(cam, c.getPosition(partial).add(0.0, 1.0, 0.0), 1.6F, 0.0F, 0.6F, 0.885F, 0.35F);
            } else if (xx.f.kind == 23) {
               FireFx.billboard(cam, c.getPosition(partial).add(0.0, (double)(c.getBbHeight() / 2.0F), 0.0), 2.2F, 0.0F, 0.3F, 0.0F, 0.5F);
            }
         }
      }

      FireFx.draw(AShaders.flame, 4, view, proj);
      BufferSource buf = mc.renderBuffers().bufferSource();
      boolean blocks = false;

      for (Elems.Rock r : ROCKS) {
         r.draw(mc, ps, buf, cam, partial, time);
         blocks = true;
      }

      for (Elems.Fx xxx : FX) {
         if (xxx.f.kind == 26 && xxx.block != null) {
            float t = Mth.clamp(((float)(now - xxx.start) + partial) / (float)xxx.f.dur, 0.0F, 1.0F);
            if (t < 1.0F) {
               drawBlock(mc, ps, buf, cam, xxx.block, xxx.f.a.add(0.0, -0.8 + 2.4 * (double)t, 0.0), xxx.f.f * 1.3F, t * 40.0F, 0);
               blocks = true;
            }
         }
      }

      for (FireFx.Shot sxxxx : FireFx.SHOTS) {
         if (sxxxx.elem == 2) {
            BlockState st = SHOT_BLOCK.computeIfAbsent(sxxxx.serial, kx -> groundBlock(s.start.add(0.0, -1.6, 0.0)));
            drawBlock(mc, ps, buf, cam, st, sxxxx.pos(partial), sxxxx.size * 1.3F, ((float)sxxxx.age + partial) * 14.0F, sxxxx.serial);
            blocks = true;
         }
      }

      if (blocks) {
         buf.endBatch();
      }

      for (FireFx.Puff p : FireFx.PUFFS) {
         if (p.mode == 14 && p.palette == 0.885F) {
            float k = Mth.clamp(((float)p.age + partial) / (float)p.life, 0.0F, 1.0F);
            WaterMesh.sphere(p.prev.lerp(p.pos, (double)partial), p.size * 0.5F * (1.0F - k * 0.5F), WaterMesh.WATER, 1.0F - k * k);
         }
      }

      WaterMesh.flush(mc, ps, buf, cam, time);
   }

   private static void drawBlock(Minecraft mc, PoseStack ps, MultiBufferSource buf, Vec3 cam, BlockState st, Vec3 p, float scale, float spin, int seed) {
      ps.pushPose();
      ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
      ps.mulPose(Axis.YP.rotationDegrees(spin + (float)(seed * 37)));
      ps.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));
      ps.scale(scale, scale, scale);
      ps.translate(-0.5, -0.5, -0.5);
      int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
      mc.getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
      ps.popPose();
   }

   static void ring(Vec3 cam, Vec3 c, float radius, float spin, float tilt, float width, float pal) {
      if (pal == 0.885F) {
         List<Vec3> pts = new ArrayList<>();

         for (int i = 0; i < 21; i++) {
            float a = spin + (float)(i % 20) / 20.0F * (float) (Math.PI * 2);
            double x = (double)(Mth.cos(a) * radius);
            double z = (double)(Mth.sin(a) * radius);
            pts.add(c.add(x, z * (double)Mth.sin(tilt), z * (double)Mth.cos(tilt)));
         }

         WaterMesh.tube(pts, width * 0.8F, WaterMesh.WATER, 1.0F);
      } else {
         int n = 18;
         float[] p = new float[n * 3];
         float[] w = new float[n];
         float[] u = new float[n];

         for (int i = 0; i < n; i++) {
            float a = spin + (float)i / ((float)n - 1.0F) * (float) (Math.PI * 2) * 0.8F;
            double x = (double)(Mth.cos(a) * radius);
            double z = (double)(Mth.sin(a) * radius);
            double y = z * (double)Mth.sin(tilt);
            z *= (double)Mth.cos(tilt);
            p[i * 3] = (float)(c.x + x - cam.x);
            p[i * 3 + 1] = (float)(c.y + y - cam.y);
            p[i * 3 + 2] = (float)(c.z + z - cam.z);
            w[i] = width;
            u[i] = 1.0F - (float)i / ((float)n - 1.0F);
         }

         Gfx.ribbon(p, w, u, tilt * 0.13F % 1.0F, 1.0F, pal, 1.0F);
      }
   }

   static void ribbon(Vec3 cam, List<Vec3> pts, float width, float pal, float alpha, float seed) {
      if (pal != 0.885F && pal != -2.0F) {
         int n = pts.size();
         float[] p = new float[n * 3];
         float[] w = new float[n];
         float[] u = new float[n];

         for (int i = 0; i < n; i++) {
            Vec3 v = pts.get(i);
            p[i * 3] = (float)(v.x - cam.x);
            p[i * 3 + 1] = (float)(v.y - cam.y);
            p[i * 3 + 2] = (float)(v.z - cam.z);
            w[i] = width;
            u[i] = (float)i / ((float)n - 1.0F);
         }

         Gfx.ribbon(p, w, u, seed, 1.0F, pal, alpha);
      } else {
         WaterMesh.tube(pts, width * 0.8F, pal == -2.0F ? WaterMesh.FOAM : WaterMesh.WATER, alpha);
      }
   }

   private static int shotRibbons(FireFx.Shot s, Vec3 cam, float partial, float time) {
      Vec3 head = s.pos(partial);
      Vec3 dir = s.vel.normalize();
      Vec3 side = dir.cross(new Vec3(0.0, 1.0, 0.0));
      side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
      Vec3 up = side.cross(dir).normalize();
      List<Vec3> pts = new ArrayList<>();
      switch (s.elem) {
         case 1:
            for (int k = 0; k < 2; k++) {
               pts.clear();

               for (int i = 0; i < 8; i++) {
                  float x = (float)i / 7.0F;
                  pts.add(
                     head.subtract(dir.scale((double)(x * s.size * 6.0F)))
                        .add(side.scale((double)Mth.sin(time * 0.5F + x * 5.0F + (float)(k * 3)) * 0.3 * (double)x))
                  );
               }

               ribbon(cam, pts, s.size * 0.9F, 0.885F, 1.0F, (float)k * 0.4F);
            }
            break;
         case 2:
         default:
            return 0;
         case 3:
            for (int i = 0; i < 9; i++) {
               float a = ((float)i / 8.0F - 0.5F) * 2.4F;
               pts.add(
                  head.add(side.scale((double)Mth.sin(a) * 0.9)).add(up.scale((double)Mth.sin(a) * 0.2)).subtract(dir.scale((double)(1.0F - Mth.cos(a)) * 0.7))
               );
            }

            List<Vec3> half = new ArrayList<>(pts.subList(4, 9));
            List<Vec3> other = new ArrayList<>(pts.subList(0, 5));
            Collections.reverse(other);
            ribbon(cam, half, 0.22F, 0.81F, 1.0F, 0.1F);
            ribbon(cam, other, 0.22F, 0.81F, 1.0F, 0.6F);
            break;
         case 4:
            pts.add(head);
            pts.add(head.subtract(dir.scale(1.4)));
            ribbon(cam, pts, 0.16F, 0.94F, 1.0F, 0.2F);
      }

      return 1;
   }

   static void ringAround(Vec3 cam, Vec3 c, Vec3 axis, float radius, float spin, float width, float pal, float alpha) {
      Vec3 a = axis.cross(new Vec3(0.0, 1.0, 0.0));
      a = a.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : a.normalize();
      Vec3 b = a.cross(axis).normalize();
      List<Vec3> pts = new ArrayList<>();

      for (int i = 0; i < 16; i++) {
         float t = spin + (float)i / 15.0F * (float) (Math.PI * 2) * 0.85F;
         pts.add(c.add(a.scale((double)(Mth.cos(t) * radius))).add(b.scale((double)(Mth.sin(t) * radius))));
      }

      ribbon(cam, pts, width, pal, alpha, 0.4F);
   }

   static void gui(GuiGraphics g, int w, int h, float partial) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && bloodVictim >= 0 && FireFx.gameTime() <= bloodUntil) {
         Entity v = mc.level.getEntity(bloodVictim);
         if (v != null && !(v.distanceTo(mc.player) > 40.0F)) {
            float k = Mth.clamp(((float)(bloodUntil - FireFx.gameTime()) - partial) / 10.0F, 0.0F, 1.0F);
            g.fill(0, 0, w, h, FireFx.argb(0.22F * k, 9043984));
            g.fillGradient(0, 0, w, h / 3, FireFx.argb(0.45F * k, 2097156), 0);
            g.fillGradient(0, h - h / 3, w, h, 0, FireFx.argb(0.45F * k, 2097156));
            if (v == mc.player) {
               g.drawCenteredString(mc.font, "Your body won't move...", w / 2, h / 2 + 30, FireFx.argb(k, 16728144));
            }
         }
      }
   }

   private static void card(GuiGraphics g, Minecraft mc, int w, int h, float t, float t0, float t1, String glyph, String name, int col) {
      if (!(t <= t0) && !(t >= t1)) {
         float a = Mth.clamp((t - t0) / 4.0F, 0.0F, 1.0F) * Mth.clamp((t1 - t) / 4.0F, 0.0F, 1.0F);
         g.pose().pushPose();
         g.pose().translate((float)w * 0.18F, (float)h * 0.36F, 0.0F);
         float s = 4.0F + (t - t0) * 0.02F;
         g.pose().scale(s, s, 1.0F);
         int gw = mc.font.width(glyph);
         g.drawString(mc.font, glyph, -gw / 2 + 1, 1, FireFx.argb(a, 1052696), false);
         g.drawString(mc.font, glyph, -gw / 2, 0, FireFx.argb(a, col), false);
         g.pose().popPose();
         g.pose().pushPose();
         g.pose().translate((float)w * 0.18F, (float)h * 0.36F + 40.0F, 0.0F);
         g.pose().scale(1.6F, 1.6F, 1.0F);
         g.drawCenteredString(mc.font, name, 0, 0, FireFx.argb(a, 16777215));
         g.pose().popPose();
      }
   }

   private static void line(GuiGraphics g, Minecraft mc, int w, int h, float t, float t0, float t1, String text, int col) {
      if (!(t <= t0) && !(t >= t1)) {
         float a = Mth.clamp((t - t0) / 3.0F, 0.0F, 1.0F) * Mth.clamp((t1 - t) / 3.0F, 0.0F, 1.0F);
         g.drawCenteredString(mc.font, text, w / 2, h - (int)((float)h * 0.12F) - 14, FireFx.argb(a, col));
      }
   }

   static final class BloodCut extends Elems.ECut {
      BloodCut(Net.Fx f) {
         super(f);
      }

      @Override
      float warm(float t) {
         return Mth.clamp(t / 5.0F, 0.0F, 1.0F) * Mth.clamp(((float)this.len - t) / 5.0F, 0.0F, 1.0F);
      }

      @Override
      int warmColor() {
         return 7995408;
      }

      @Override
      Vec3[] shot(float partial) {
         Minecraft mc = Minecraft.getInstance();
         Vec3 base = this.base(partial);
         Vec3 head = base.add(0.0, 1.6, 0.0);
         float t = (float)this.age + partial;
         if (t < 16.0F) {
            this.fov = 44.0F;
            this.roll = -6.0F;
            Vec3 eye = base.add(this.fwd.scale(2.6)).add(this.right.scale(-0.6)).add(0.0, 0.25, 0.0);
            return new Vec3[]{eye, head.add(0.0, 1.2, 0.0), head};
         } else {
            Entity v = mc.level.getEntity(Elems.bloodVictim);
            Vec3 vp = v != null ? v.getPosition(partial).add(0.0, (double)(v.getBbHeight() / 2.0F), 0.0) : head.add(this.dir.scale(8.0));
            this.fov = 34.0F + Mth.sin(t * 0.7F) * 3.0F;
            this.roll = 12.0F + Mth.sin(t * 1.9F) * 4.0F;
            Vec3 eye = vp.add(this.right.scale(2.4)).add(this.fwd.scale(-1.2)).add(0.0, -0.4, 0.0);
            return new Vec3[]{eye, vp, vp};
         }
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Elems.card(g, mc, w, h, t, 2.0F, 15.0F, "血", "B L O O D B E N D I N G", 13639724);
         Elems.line(g, mc, w, h, t, 17.0F, (float)(this.len - 2), "\"Your body belongs to me now.\"", 16744592);
      }
   }

   static final class CoffinCut extends Elems.ECut {
      CoffinCut(Net.Fx f) {
         super(f);
      }

      @Override
      void onAge() {
         if (this.age == 12) {
            FireFx.flash = 0.3F;
         }
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 v = Elems.lastCoffin != null ? Elems.lastCoffin : this.base(partial).add(this.dir.scale(6.0));
         float t = (float)this.age + partial;
         if (t < 14.0F) {
            this.fov = 50.0F;
            this.roll = Mth.sin(t * 2.3F) * 2.0F;
            Vec3 eye = v.add(this.right.scale(3.0)).add(this.fwd.scale(-1.5)).add(0.0, 0.25, 0.0);
            return new Vec3[]{eye, v.add(0.0, 0.9, 0.0), v.add(0.0, 0.9, 0.0)};
         } else {
            this.fov = 55.0F;
            this.roll = 0.0F;
            Vec3 eye = v.add(this.fwd.scale(-0.6)).add(0.0, 7.0 + (double)(t - 14.0F) * 0.08, 0.0);
            return new Vec3[]{eye, v, v.add(0.0, 1.0, 0.0)};
         }
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Elems.card(g, mc, w, h, t, 1.0F, 14.0F, "土", "E A R T H   C O F F I N", 10146922);
      }
   }

   abstract static class ECut extends FireFx.Cine {
      final int len;

      ECut(Net.Fx f) {
         super(f);
         this.len = f.dur;
      }

      @Override
      int length() {
         return this.len;
      }

      @Override
      void onAge() {
      }

      @Override
      float warm(float t) {
         return 0.0F;
      }
   }

   static final class Fx {
      final Net.Fx f;
      final long start;
      BlockState block;

      Fx(Net.Fx f, long start) {
         this.f = f;
         this.start = start;
      }

      int ribbons(Minecraft mc, Vec3 cam, long now, float partial, float time) {
         float t = (float)(now - this.start) + partial;
         Entity c = mc.level.getEntity(this.f.caster);
         float fade = Mth.clamp(((float)this.f.dur - t) / 6.0F, 0.0F, 1.0F) * Mth.clamp(t / 3.0F, 0.0F, 1.0F);
         if (fade <= 0.0F) {
            return 0;
         } else {
            switch (this.f.kind) {
               case 19:
                  List<Vec3> pts = new ArrayList<>();
                  Vec3 d = this.f.b.subtract(this.f.a);
                  Vec3 side = d.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                  float reach = Mth.clamp(t / 2.5F, 0.0F, 1.0F);

                  for (int i = 0; i < 12; i++) {
                     float x = (float)i / 11.0F;
                     double bend = (double)(Mth.sin(x * (float) Math.PI) * (1.0F - reach)) * 1.5 + (double)Mth.sin(x * 9.0F - t * 1.5F) * 0.12;
                     pts.add(
                        this.f
                           .a
                           .add(d.scale((double)(x * reach)))
                           .add(side.scale(bend))
                           .add(0.0, (double)Mth.sin(x * (float) Math.PI) * 0.3 * (double)(1.0F - reach), 0.0)
                     );
                  }

                  Collections.reverse(pts);
                  Elems.ribbon(cam, pts, this.f.f, 0.885F, fade, 0.3F);
                  return 1;
               case 20:
                  if (c == null) {
                     return 0;
                  } else {
                     if (this.f.other != 1) {
                        Vec3 o = c.getPosition(partial).add(0.0, 1.0, 0.0);
                        boolean air = this.f.other == 3;

                        for (int kx = 0; kx < (air ? 6 : 5); kx++) {
                           Elems.ring(
                              cam,
                              o,
                              this.f.f * (0.92F + 0.06F * (float)kx % 2.0F),
                              time * (air ? 0.9F : 0.25F) * (float)(kx % 2 == 0 ? 1 : -1) + (float)kx,
                              (float)kx * 0.62F,
                              air ? 0.22F : 0.4F,
                              air ? 0.81F : 0.885F
                           );
                        }

                        return 1;
                     }

                     Vec3 base = c.getPosition(partial);
                     Vec3 top = base.add(0.0, 3.3, 0.0);
                     List<Vec3> col = new ArrayList<>();

                     for (int i = 0; i < 8; i++) {
                        float x = (float)i / 7.0F;
                        col.add(base.add((double)Mth.sin(time * 0.3F + x * 6.0F) * 0.06, 1.2 + (double)x * 2.1, (double)Mth.cos(time * 0.3F + x * 6.0F) * 0.06));
                     }

                     Collections.reverse(col);
                     Elems.ribbon(cam, col, 0.3F, 0.885F, fade, 0.1F);

                     for (int kx = 0; kx < 10; kx++) {
                        float a = (float)kx * (float) (Math.PI * 2) / 10.0F + time * 0.02F;
                        List<Vec3> arc = new ArrayList<>();

                        for (int i = 0; i < 10; i++) {
                           float x = (float)i / 9.0F;
                           double r = (double)(this.f.f * x);
                           double y = 3.3 * (double)(1.0F - x * x)
                              + 0.5 * (double)x * (double)(1.0F - x)
                              + (double)Mth.sin(time * 0.4F + (float)kx + x * 5.0F) * 0.05;
                           arc.add(base.add((double)Mth.cos(a) * r, y, (double)Mth.sin(a) * r));
                        }

                        Elems.ribbon(cam, arc, 0.22F, 0.885F, fade * 0.9F, (float)kx * 0.09F);
                     }

                     return 1;
                  }
               case 21:
               case 23:
               case 25:
               case 26:
               case 27:
               case 28:
               case 29:
               case 30:
               case 32:
               case 34:
               default:
                  return 0;
               case 22:
                  Vec3 right = new Vec3(-this.f.b.z, 0.0, this.f.b.x);
                  double front = (double)t * 0.75;
                  float rise = Mth.clamp(t / 6.0F, 0.0F, 1.0F);
                  List<List<Vec3>> rows = new ArrayList<>();

                  for (int i = 0; i < 25; i++) {
                     float s = ((float)i / 24.0F - 0.5F) * this.f.f;
                     float edge = 1.0F - (float)Math.pow((double)(Math.abs(s) / (this.f.f * 0.5F)), 3.0);
                     float h = (4.6F + Mth.sin(time * 0.15F + (float)i * 0.7F) * 0.35F) * rise * Math.max(0.15F, edge);
                     Vec3 base = this.f.a.add(this.f.b.scale(front - 4.5)).add(right.scale((double)s));
                     List<Vec3> ptsx = new ArrayList<>();

                     for (int j = 0; j < 12; j++) {
                        float x = (float)j / 11.0F;
                        double lip = (double)x > 0.72 ? ((double)x - 0.72) / 0.28 : 0.0;
                        double y = (double)h * Math.pow(Math.sin(Math.min((double)x / 0.78, 1.0) * (float) (Math.PI / 2)), 1.4) - lip * lip * (double)h * 0.55;
                        ptsx.add(base.add(this.f.b.scale((double)x * 4.2 + lip * 1.6)).add(0.0, y, 0.0));
                     }

                     rows.add(ptsx);
                  }

                  WaterMesh.sheet(rows);
                  List<Vec3> crest = new ArrayList<>();

                  for (List<Vec3> row : rows) {
                     crest.add(row.get(8));
                  }

                  WaterMesh.tube(crest, 0.35F, WaterMesh.FOAM, fade);
                  return 1;
               case 24:
                  if (c == null) {
                     return 0;
                  }

                  Vec3 o = c.getPosition(partial).add(0.0, 0.9, 0.0);
                  float grow = Mth.clamp(t / 12.0F, 0.0F, 1.0F);

                  for (int kx = 0; kx < 2; kx++) {
                     Elems.ring(
                        cam,
                        o.add(0.0, 0.25 * (double)kx, 0.0),
                        1.25F + 0.15F * (float)kx,
                        time * (kx == 0 ? 0.25F : -0.2F),
                        0.12F * (float)(kx * 2 - 1),
                        0.22F,
                        0.885F
                     );
                  }

                  for (int kx = 0; kx < 8; kx++) {
                     float a = (float)kx * (float) (Math.PI * 2) / 8.0F + Mth.sin(time * 0.05F + (float)kx) * 0.2F;
                     List<Vec3> ptsx = new ArrayList<>();

                     for (int i = 0; i < 10; i++) {
                        float x = (float)i / 9.0F;
                        float r = 0.4F + x * 2.6F * grow;
                        float lash = Mth.sin(time * 0.3F + (float)kx * 1.7F - x * 3.0F) * 0.5F * x;
                        ptsx.add(
                           o.add(
                              (double)(Mth.cos(a + lash * 0.4F) * r),
                              (double)Mth.sin(x * 2.6F) * 1.6 * (double)grow + (double)lash,
                              (double)(Mth.sin(a + lash * 0.4F) * r)
                           )
                        );
                     }

                     Elems.ribbon(cam, ptsx, 0.32F, 0.885F, fade, (float)kx * 0.11F);
                  }

                  return 1;
               case 31:
                  Vec3 dir = this.f.b.normalize();

                  for (int k = 0; k < 4; k++) {
                     float d = t * 2.2F - (float)k * 1.5F;
                     if (!(d < 0.3F) && !(d > 11.0F)) {
                        Vec3 c0 = this.f.a.add(dir.scale((double)d));
                        Elems.ringAround(cam, c0, dir, 0.4F + d * 0.28F, time * 0.8F + (float)k, 0.14F, 0.81F, Mth.clamp(1.0F - d / 11.0F, 0.0F, 1.0F) * fade);
                     }
                  }

                  return 1;
               case 33:
                  Vec3 base = this.f.a.add(this.f.b.scale((double)t));
                  float grow = Mth.clamp(t / 10.0F, 0.0F, 1.0F);

                  for (int kx = 0; kx < 7; kx++) {
                     List<Vec3> ptsx = new ArrayList<>();

                     for (int i = 0; i < 16; i++) {
                        float x = (float)i / 15.0F;
                        float y = x * 14.0F * grow;
                        float r = 0.6F + x * x * 5.5F;
                        float a = -time * 0.55F + x * 9.0F + (float)kx * 0.9F;
                        ptsx.add(base.add((double)(Mth.cos(a) * r), (double)y, (double)(Mth.sin(a) * r)));
                     }

                     Elems.ribbon(cam, ptsx, 0.35F + (float)(kx % 3) * 0.15F, 0.81F, fade, (float)kx * 0.13F);
                  }

                  return 1;
               case 35:
                  List<Vec3> pts = new ArrayList<>();
                  pts.add(this.f.b.add(0.0, 1.0, 0.0));
                  pts.add(this.f.a.add(0.0, 1.0, 0.0));
                  Elems.ribbon(cam, pts, 0.35F, 0.81F, fade * 0.8F, 0.5F);
                  return 1;
            }
         }
      }
   }

   @EventBusSubscriber(
      modid = "jjk",
      value = {Dist.CLIENT}
   )
   public static final class Hooks {
      private static final Set<Integer> RAISED = new HashSet<>();

      @SubscribeEvent
      public static void pre(Pre e) {
         if (Elems.on(Elems.SCOOT, e.getEntity())) {
            e.getPoseStack().pushPose();
            e.getPoseStack().translate(0.0, 0.72, 0.0);
            RAISED.add(e.getEntity().getId());
         }
      }

      @SubscribeEvent
      public static void post(Post e) {
         if (RAISED.remove(e.getEntity().getId())) {
            e.getPoseStack().popPose();
         }
      }

      @SubscribeEvent
      public static void use(InteractionKeyMappingTriggered e) {
         Minecraft mc = Minecraft.getInstance();
         if (e.isUseItem() && mc.player != null) {
            for (Elems.Fx x : Elems.FX) {
               if (x.f.kind == 24 && x.f.caster == mc.player.getId() && FireFx.gameTime() - x.start < (long)x.f.dur) {
                  Net.CHANNEL.sendToServer(new Net.Key(15, true));
                  e.setSwingHand(true);
                  e.setCanceled(true);
                  return;
               }
            }
         }
      }
   }

   static final class OctoCut extends Elems.ECut {
      OctoCut(Net.Fx f) {
         super(f);
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 base = this.base(partial);
         Vec3 chest = base.add(0.0, 1.1, 0.0);
         float t = (float)this.age + partial;
         double a = Math.toRadians((double)(-50.0F + 100.0F * t / (float)this.len));
         this.fov = 58.0F;
         this.roll = 0.0F;
         Vec3 eye = chest.add(this.fwd.scale(Math.cos(a) * 5.0)).add(this.right.scale(Math.sin(a) * 5.0)).add(0.0, -0.4 + (double)t * 0.06, 0.0);
         return new Vec3[]{eye, chest.add(0.0, 0.6, 0.0), chest};
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Elems.card(g, mc, w, h, t, 2.0F, (float)(this.len - 2), "蛸", "O C T O P U S   F O R M", 8373503);
      }
   }

   static final class Rock {
      final BlockState st;
      Vec3 pos;
      Vec3 prev;
      Vec3 vel;
      final float scale;
      final int life;
      final boolean grav;
      int age;
      Vec3 orbitBase;
      Vec3 drift;
      int orbitStart = -1;

      Rock(BlockState st, Vec3 pos, Vec3 vel, float scale, int life, boolean grav) {
         this.st = st;
         this.pos = this.prev = pos;
         this.vel = vel;
         this.scale = scale;
         this.life = life;
         this.grav = grav;
      }

      Elems.Rock orbit(Vec3 base, Vec3 drift, int t) {
         this.orbitBase = base;
         this.drift = drift;
         return this;
      }

      boolean tick() {
         this.prev = this.pos;
         if (this.orbitBase != null) {
            this.orbitBase = this.orbitBase.add(this.drift);
            float a = (float)this.age * 0.35F;
            float r = 1.5F + (float)this.age * 0.08F;
            this.pos = this.orbitBase.add((double)(Mth.cos(a) * r), (double)this.age * 0.3, (double)(Mth.sin(a) * r));
         } else {
            this.pos = this.pos.add(this.vel);
            if (this.grav) {
               this.vel = this.vel.add(0.0, -0.06, 0.0).scale(0.98);
            }
         }

         return ++this.age < this.life;
      }

      void draw(Minecraft mc, PoseStack ps, MultiBufferSource buf, Vec3 cam, float partial, float time) {
         float k = ((float)this.age + partial) / (float)this.life;
         float s = this.scale * (k > 0.8F ? (1.0F - k) / 0.2F : 1.0F);
         Elems.drawBlock(mc, ps, buf, cam, this.st, this.prev.lerp(this.pos, (double)partial), s, ((float)this.age + partial) * 20.0F, this.hashCode() & 7);
      }
   }

   static final class TornadoCut extends Elems.ECut {
      TornadoCut(Net.Fx f) {
         super(f);
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 tp = Elems.lastTornado != null ? Elems.lastTornado : this.base(partial).add(this.dir.scale(10.0));
         float t = (float)this.age + partial;
         double a = Math.toRadians(-20.0 + (double)t * 1.2);
         this.fov = 82.0F;
         this.roll = 4.0F;
         Vec3 eye = tp.add(this.fwd.scale(-Math.cos(a) * 9.0)).add(this.right.scale(Math.sin(a) * 9.0)).add(0.0, 0.3, 0.0);
         return new Vec3[]{eye, tp.add(0.0, 8.0 + (double)t * 0.05, 0.0), tp.add(0.0, 2.0, 0.0)};
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Elems.card(g, mc, w, h, t, 2.0F, 22.0F, "風", "T O R N A D O", 16773312);
      }
   }

   static final class WaveCut extends Elems.ECut {
      WaveCut(Net.Fx f) {
         super(f);
      }

      @Override
      void onAge() {
         if (this.age == 20) {
            Impact.hit(7, 0, 6, 3, 0.5F, 0.5F, true);
         }
      }

      @Override
      Vec3[] shot(float partial) {
         Vec3 base = this.base(partial);
         float t = (float)this.age + partial;
         if (t < 13.0F) {
            this.fov = 50.0F;
            this.roll = 0.0F;
            Vec3 eye = base.add(this.fwd.scale(3.2)).add(this.right.scale(0.8)).add(0.0, 0.6, 0.0);
            return new Vec3[]{eye, base.add(0.0, 2.2, 0.0), base.add(0.0, 1.2, 0.0)};
         } else {
            double front = Math.max(0.0, (double)(t - 12.0F) * 0.75);
            Vec3 o = base.subtract(this.fwd.scale(2.0));
            this.fov = 68.0F;
            this.roll = -3.0F;
            Vec3 eye = o.add(this.fwd.scale(front - 3.0)).add(this.right.scale(11.0)).add(0.0, 3.5, 0.0);
            return new Vec3[]{eye, o.add(this.fwd.scale(front + 2.0)).add(0.0, 1.5, 0.0), o.add(this.fwd.scale(front)).add(0.0, 2.0, 0.0)};
         }
      }

      @Override
      void titles(GuiGraphics g, int w, int h, float t, Minecraft mc) {
         Elems.card(g, mc, w, h, t, 1.0F, 14.0F, "水", "W A T E R   W A V E", 8373503);
         Elems.line(g, mc, w, h, t, 3.0F, 13.0F, "\"Push... and pull. Like the tide.\"", 14544639);
      }
   }
}
