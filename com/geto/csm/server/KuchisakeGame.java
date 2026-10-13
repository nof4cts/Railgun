package com.geto.csm.server;

import com.geto.csm.entity.CurseEntity;
import com.geto.csm.entity.KuchisakeEntity;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.network.QuestionPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class KuchisakeGame {
   public static final int ANSWER_TICKS = 200;
   private static final RandomSource RNG = RandomSource.create();
   private static final Map<Integer, KuchisakeGame.Game> BY_TARGET = new HashMap<>();

   public static boolean busy(LivingEntity t) {
      return BY_TARGET.containsKey(t.getId());
   }

   public static boolean bound(Entity e) {
      return e != null && BY_TARGET.containsKey(e.getId());
   }

   public static void start(KuchisakeEntity k, LivingEntity t) {
      KuchisakeGame.Game g = new KuchisakeGame.Game();
      g.k = k;
      g.t = t;
      g.stage = 1;
      g.ticks = 200;
      g.anchor = t.position();
      BY_TARGET.put(t.getId(), g);
      k.vowTicks = 440;
      k.setUnmasked(false);
      Vec3 mid = k.position().add(t.position()).scale(0.5);
      CsmNetwork.fx(k, FxType.KUCHI_DOMAIN, k, t, mid, 5.0F, 200.0F);
      k.level().playSound((Player)null, k.getX(), k.getY(), k.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.6F);
      ask(g);
   }

   private static void ask(KuchisakeGame.Game g) {
      g.ticks = 200;
      if (g.t instanceof ServerPlayer sp) {
         CsmNetwork.toPlayer(sp, new QuestionPacket(g.k.getId(), g.stage, 200));
      }
   }

   public static void answer(ServerPlayer sp, int entityId, int ans) {
      KuchisakeGame.Game g = BY_TARGET.get(sp.getId());
      if (g != null && g.k.getId() == entityId) {
         resolve(g, Math.floorMod(ans, 3));
      }
   }

   private static void resolve(KuchisakeGame.Game g, int ans) {
      if (g.stage == 1 && ans == 0) {
         g.stage = 2;
         g.k.setUnmasked(true);
         CsmNetwork.fx(g.k, FxType.ACTION, g.k, g.t, g.k.position(), 3.0F, 0.0F);
         g.k.level().playSound((Player)null, g.k.getX(), g.k.getY(), g.k.getZ(), SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 1.5F, 0.5F);
         ask(g);
      } else {
         finish(g);
         if (ans == 2) {
            escape(g);
         } else if (g.stage == 2 && ans == 0) {
            mouthSlash(g);
         } else {
            snip(g);
         }
      }
   }

   private static void finish(KuchisakeGame.Game g) {
      BY_TARGET.remove(g.t.getId());
      g.k.vowTicks = 0;
      g.k.askCooldown = 1200;
      if (g.t instanceof ServerPlayer sp) {
         CsmNetwork.toPlayer(sp, new QuestionPacket(g.k.getId(), 0, 0));
      }

      KuchisakeEntity k = g.k;
      Scheduler.after(80, () -> k.setUnmasked(false));
   }

   private static void snip(KuchisakeGame.Game g) {
      LivingEntity t = g.t;
      KuchisakeEntity k = g.k;
      CsmNetwork.fx(k, FxType.SHEARS, k, t, t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0), k.getYRot(), 1.0F);
      Scheduler.after(9, () -> {
         if (k.isAlive() && t.isAlive()) {
            k.strike(t, 18.0F);
            k.level().playSound((Player)null, t.getX(), t.getY(), t.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.HOSTILE, 3.0F, 0.4F);
         }
      });
   }

   private static void mouthSlash(KuchisakeGame.Game g) {
      LivingEntity t = g.t;
      KuchisakeEntity k = g.k;
      CsmNetwork.fx(k, FxType.MOUTH_SLASH, k, t, t.getEyePosition(), k.getYRot(), 0.0F);
      Scheduler.after(6, () -> {
         if (k.isAlive() && t.isAlive()) {
            k.strike(t, 12.0F);
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
         }
      });
   }

   private static void escape(KuchisakeGame.Game g) {
      g.k.askCooldown = 1200;
      g.k.vowTicks = 60;
      CsmNetwork.fx(g.k, FxType.ACTION, g.k, g.t, g.k.position(), 4.0F, 0.0F);
   }

   public static void tick() {
      Iterator<Entry<Integer, KuchisakeGame.Game>> it = BY_TARGET.entrySet().iterator();
      List<KuchisakeGame.Game> timedOut = new ArrayList<>();
      List<KuchisakeGame.Game[]> auto = new ArrayList<>();

      while (it.hasNext()) {
         KuchisakeGame.Game g = it.next().getValue();
         if (g.k.isAlive() && g.t.isAlive() && g.t.level() == g.k.level()) {
            if (g.t.position().distanceTo(g.anchor) > 0.3) {
               g.t.teleportTo(g.anchor.x, g.anchor.y, g.anchor.z);
            }

            g.t.setDeltaMovement(Vec3.ZERO);
            g.t.hurtMarked = true;
            g.ticks--;
            if (!(g.t instanceof ServerPlayer) && g.ticks == 150) {
               auto.add(new KuchisakeGame.Game[]{g});
            } else if (g.ticks <= 0) {
               timedOut.add(g);
            }
         } else {
            it.remove();
            if (g.t instanceof ServerPlayer sp) {
               CsmNetwork.toPlayer(sp, new QuestionPacket(g.k.getId(), 0, 0));
            }

            g.k.vowTicks = 0;
         }
      }

      for (KuchisakeGame.Game[] a : auto) {
         resolve(a[0], RNG.nextInt(3));
      }

      for (KuchisakeGame.Game g : timedOut) {
         if (BY_TARGET.get(g.t.getId()) == g) {
            resolve(g, 1);
         }
      }
   }

   public static void clear() {
      BY_TARGET.clear();
   }

   private static boolean isCurse(Entity e) {
      return e instanceof CurseEntity;
   }

   private KuchisakeGame() {
   }

   private static final class Game {
      KuchisakeEntity k;
      LivingEntity t;
      int stage;
      int ticks;
      Vec3 anchor;
   }
}
