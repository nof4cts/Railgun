package com.avatar.mod;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Water {
   public static final int WAVE_TICKS = 32;
   public static final int BLOOD_TICKS = 140;
   public static final int OCTO_TICKS = 160;
   private static final Map<UUID, Long> HEAL = new HashMap<>();
   private static final Map<UUID, Long> OCTO = new HashMap<>();
   private static final List<Water.Wave> WAVES = new ArrayList<>();
   private static final List<Water.Bound> BOUND = new ArrayList<>();
   private static final Set<UUID> DRAWN = new HashSet<>();
   private static final Map<UUID, Long> STRIKE = new HashMap<>();

   private Water() {
   }

   static float boost(ServerPlayer p) {
      return p.isInWaterRainOrBubble() ? 1.3F : 1.0F;
   }

   static void key(ServerPlayer p, int slot) {
      if (slot == 0) {
         draw(p);
      } else if (!DRAWN.contains(p.getUUID())) {
         p.displayClientMessage(
            Component.literal("You have no water. Press G with a water bucket in your inventory to draw it.").withStyle(ChatFormatting.AQUA), true
         );
      } else {
         switch (slot) {
            case 1:
               whip(p);
               break;
            case 2:
               shards(p);
               break;
            case 3:
               shield(p);
               break;
            case 4:
               heal(p);
               break;
            case 5:
               wave(p);
               break;
            case 6:
               blood(p);
               break;
            case 7:
               octopus(p);
         }
      }
   }

   private static void draw(ServerPlayer p) {
      ServerLevel l = p.serverLevel();
      if (DRAWN.contains(p.getUUID())) {
         putBack(p);
      } else {
         int slot = find(p, Items.WATER_BUCKET);
         if (slot < 0) {
            p.displayClientMessage(Component.literal("You need a water bucket to waterbend.").withStyle(ChatFormatting.AQUA), true);
         } else {
            p.getInventory().setItem(slot, new ItemStack(Items.BUCKET));
            DRAWN.add(p.getUUID());
            Bending.setCooldown(p, 0, 10);
            Bending.pose(p, 20, 10);
            Net.fx(l, new Net.Fx((byte)37, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 1));
            Bending.play(l, p.position(), SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
            Bending.play(l, p.position(), SoundEvents.GENERIC_SPLASH, 0.6F, 1.4F);
         }
      }
   }

   static void putBack(ServerPlayer p) {
      if (DRAWN.remove(p.getUUID())) {
         ServerLevel l = p.serverLevel();
         Net.fx(l, new Net.Fx((byte)37, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
         int slot = find(p, Items.BUCKET);
         if (slot >= 0) {
            ItemStack st = p.getInventory().getItem(slot);
            if (st.getCount() == 1) {
               p.getInventory().setItem(slot, new ItemStack(Items.WATER_BUCKET));
            } else {
               st.shrink(1);
               if (!p.getInventory().add(new ItemStack(Items.WATER_BUCKET))) {
                  p.drop(new ItemStack(Items.WATER_BUCKET), false);
               }
            }

            Bending.play(l, p.position(), SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
         } else {
            p.displayClientMessage(Component.literal("No empty bucket: the water splashes away.").withStyle(ChatFormatting.GRAY), true);
            Bending.play(l, p.position(), SoundEvents.GENERIC_SPLASH, 0.8F, 1.0F);
         }
      }
   }

   private static int find(ServerPlayer p, Item item) {
      for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
         if (p.getInventory().getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }

   public static boolean drawn(Player p) {
      return DRAWN.contains(p.getUUID());
   }

   static void octoStrike(ServerPlayer p) {
      Long o = OCTO.get(p.getUUID());
      if (o != null && o >= Bending.now) {
         Long last = STRIKE.get(p.getUUID());
         if (last == null || Bending.now - last >= 8L) {
            STRIKE.put(p.getUUID(), Bending.now);
            ServerLevel l = p.serverLevel();
            Vec3 to = Bending.aim(p, 8.0);
            Vec3 look = p.getLookAngle();
            Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize();

            for (int i = -1; i <= 1; i++) {
               Vec3 from = p.position().add(0.0, 1.0, 0.0).add(side.scale(0.6 * (double)i));
               Vec3 end = to.add(side.scale(0.5 * (double)i)).add(0.0, (double)Math.abs(i) * 0.3, 0.0);
               Net.fx(l, new Net.Fx((byte)19, from, end, 0.35F, p.getId(), 0, 8));
               lash(p, from, end, 5.0F * boost(p));
            }

            Bending.pose(p, 13, 6);
            Bending.play(l, to, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 1.2F);
            Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 0.8F, 1.5F);
         }
      }
   }

   private static void whip(ServerPlayer p) {
      if (Elem.pay(p, 10)) {
         Bending.setCooldown(p, 1, 12);
         Bending.pose(p, 13, 10);
         ServerLevel l = p.serverLevel();
         Vec3 from = Bending.fist(p, 1);
         Vec3 to = Bending.aim(p, 11.0);
         Net.fx(l, new Net.Fx((byte)19, from, to, 0.35F, p.getId(), 0, 9));
         Bending.play(l, from, (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 0.7F, 1.5F);
         Bending.play(l, to, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8F, 1.3F);
         float dmg = 8.0F * boost(p);
         Bending.later(2, () -> lash(p, from, to, dmg));
      }
   }

   static void lash(ServerPlayer p, Vec3 from, Vec3 to, float dmg) {
      Vec3 d = to.subtract(from);
      double len = d.length();
      Vec3 n = d.normalize();

      for (LivingEntity e : p.serverLevel()
         .getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.2), ex -> ex != p && ex.isAlive() && !ex.isSpectator())) {
         Vec3 c = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(from);
         double along = c.dot(n);
         if (!(along < 0.0) && !(along > len + 0.8) && !(c.subtract(n.scale(along)).length() > 0.8 + (double)(e.getBbWidth() / 2.0F))) {
            Bending.hit(p, e, dmg);
            e.clearFire();
            Bending.knock(e, n, 0.9, 0.25);
         }
      }
   }

   private static void shards(ServerPlayer p) {
      if (Elem.pay(p, 15)) {
         Bending.setCooldown(p, 2, 30);
         float dmg = 5.0F * boost(p);

         for (int i = 0; i < 5; i++) {
            int k = i;
            Bending.later(i * 2, () -> {
               if (p.isAlive()) {
                  int side = k % 2 == 0 ? 1 : -1;
                  Bending.pose(p, side > 0 ? 1 : 2, 4);
                  Vec3 from = Bending.fist(p, side);
                  Bending.launchElem(p, from, Bending.dir(p, from, Bending.aim(p, 48.0)), 0.25F, 2.3F, dmg, 20, 0.0F, 4, 0.0);
                  Bending.play(p.serverLevel(), from, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F + (float)k * 0.1F);
               }
            });
         }
      }
   }

   private static void shield(ServerPlayer p) {
      if (Elem.pay(p, 20)) {
         Bending.setCooldown(p, 3, 200);
         Bending.SHIELDS.put(p.getUUID(), new double[]{3.2, (double)(Bending.now + 100L), 1.0});
         Net.fx(p.serverLevel(), new Net.Fx((byte)20, p.position(), Vec3.ZERO, 3.2F, p.getId(), 1, 100));
         Bending.pose(p, 20, 12);
         Bending.play(p.serverLevel(), p.position(), SoundEvents.BUCKET_EMPTY, 1.2F, 0.8F);
         Bending.play(p.serverLevel(), p.position(), SoundEvents.GENERIC_SPLASH, 1.0F, 0.7F);
      }
   }

   private static void heal(ServerPlayer p) {
      if (Elem.pay(p, 40)) {
         Bending.setCooldown(p, 4, 300);
         HEAL.put(p.getUUID(), Bending.now + 100L);
         Net.fx(p.serverLevel(), new Net.Fx((byte)21, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 100));
         Bending.pose(p, 15, 100);
         Bending.play(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.2F);
      }
   }

   private static void wave(ServerPlayer p) {
      if (Elem.pay(p, 50)) {
         Bending.setCooldown(p, 5, 400);
         ServerLevel l = p.serverLevel();
         Vec3 look = p.getLookAngle();
         Vec3 fwd = new Vec3(look.x, 0.0, look.z).normalize();
         Bending.hold(p, 16);
         Net.fx(l, new Net.Fx((byte)36, p.getEyePosition(), fwd, 0.0F, p.getId(), 1, 52));
         Bending.pose(p, 12, 14);
         Bending.play(l, p.position(), SoundEvents.AMBIENT_UNDERWATER_ENTER, 2.0F, 0.6F);
         float dmg = 10.0F * boost(p);
         Bending.later(12, () -> {
            Bending.pose(p, 5, 14);
            Vec3 o = p.position().subtract(fwd.scale(2.0));
            WAVES.add(new Water.Wave(p, l, o, fwd, 12.0, Bending.now, dmg, new HashSet<>()));
            Net.fx(l, new Net.Fx((byte)22, o, fwd, 12.0F, p.getId(), 0, 32));
            Bending.play(l, p.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.5F, 0.45F);
            Bending.play(l, p.position(), SoundEvents.GENERIC_SPLASH, 2.5F, 0.5F);
         });
      }
   }

   private static void blood(ServerPlayer p) {
      ServerLevel l = p.serverLevel();
      LivingEntity v = Elem.target(p, 24.0);
      if (v == null) {
         p.displayClientMessage(Component.literal("No one there to bend.").withStyle(ChatFormatting.GRAY), true);
      } else if (Elem.pay(p, 50)) {
         Bending.setCooldown(p, 6, 900);
         double dist = Math.max(2.5, v.position().add(0.0, (double)(v.getBbHeight() / 2.0F), 0.0).distanceTo(p.getEyePosition()));
         BOUND.add(new Water.Bound(p, v, dist, Bending.now));
         Net.fx(l, new Net.Fx((byte)23, v.position(), Vec3.ZERO, 0.0F, p.getId(), v.getId(), 140));
         Net.fx(l, new Net.Fx((byte)36, p.getEyePosition(), p.getLookAngle(), 0.0F, p.getId(), 2, 26));
         Bending.pose(p, 16, 140);
         p.displayClientMessage(
            Component.literal("Bloodbending: " + v.getDisplayName().getString() + " moves where you look.").withStyle(ChatFormatting.DARK_RED), true
         );
         Bending.play(l, v.position(), SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.6F);
         Bending.play(l, p.position(), (SoundEvent)SoundEvents.AMBIENT_CAVE.value(), 2.0F, 0.7F);
      }
   }

   private static void octopus(ServerPlayer p) {
      if (Elem.pay(p, 50)) {
         Bending.setCooldown(p, 7, 600);
         ServerLevel l = p.serverLevel();
         OCTO.put(p.getUUID(), Bending.now + 160L);
         Bending.SHIELDS.put(p.getUUID(), new double[]{2.6, (double)(Bending.now + 160L), 1.0});
         Bending.hold(p, 18);
         Net.fx(l, new Net.Fx((byte)24, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 160));
         Net.fx(l, new Net.Fx((byte)36, p.getEyePosition(), p.getLookAngle(), 0.0F, p.getId(), 3, 26));
         Bending.pose(p, 12, 18);
         Bending.play(l, p.position(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.6F);
      }
   }

   static void tickPlayer(ServerPlayer p) {
      UUID id = p.getUUID();
      long now = Bending.now;
      Long h = HEAL.get(id);
      if (h != null) {
         if (h < now) {
            HEAL.remove(id);
         } else if (now % 10L == 0L) {
            for (Player o : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(4.0))) {
               o.heal(1.5F);
               o.clearFire();
            }
         }
      }

      Long o = OCTO.get(id);
      if (o != null) {
         if (o >= now && p.isAlive()) {
            if (now % 8L == 0L) {
               int n = 0;

               for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(6.5), ex -> Elem.enemy(ex, p))) {
                  if (n++ >= 3) {
                     break;
                  }

                  Vec3 to = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0);
                  Vec3 from = p.position().add(0.0, 1.0, 0.0).add(to.subtract(p.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.7));
                  Net.fx(p.serverLevel(), new Net.Fx((byte)19, from, to, 0.3F, p.getId(), 0, 7));
                  Bending.hit(p, e, 4.5F * boost(p));
                  Bending.knock(e, to.subtract(from).normalize(), 0.7, 0.3);
                  Bending.play(p.serverLevel(), to, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.6F, 1.4F);
               }
            }
         } else {
            OCTO.remove(id);
         }
      }

      double[] s = Bending.SHIELDS.get(id);
      if (s != null && s[2] == 1.0 && s[1] >= (double)now) {
         Elem.shieldTick(p, s[0], false);
      }
   }

   static void tick(MinecraftServer server) {
      long now = Bending.now;
      Iterator<Water.Wave> it = WAVES.iterator();

      while (it.hasNext()) {
         Water.Wave w = it.next();
         long t = now - w.start;
         if (t > 32L) {
            it.remove();
         } else {
            double front = (double)t * 0.75;
            Vec3 right = new Vec3(-w.dir.z, 0.0, w.dir.x);

            for (LivingEntity e : w.l
               .getEntitiesOfClass(LivingEntity.class, new AABB(w.o, w.o).inflate(30.0), ex -> ex != w.p && ex.isAlive() && !ex.isSpectator())) {
               Vec3 d = e.position().subtract(w.o);
               double along = d.dot(w.dir);
               double side = d.dot(right);
               if (Math.abs(along - front) < 2.2 && Math.abs(side) < w.width / 2.0 && Math.abs(d.y) < 6.0) {
                  if (w.hit.add(e.getId())) {
                     Bending.hit(w.p, e, w.dmg);
                  }

                  e.clearFire();
                  e.setDeltaMovement(w.dir.x * 1.1, 0.35, w.dir.z * 1.1);
                  e.hurtMarked = true;
               }
            }
         }
      }

      it = BOUND.iterator();

      while (it.hasNext()) {
         Water.Bound b = (Water.Bound)it.next();
         long t = now - b.start;
         LivingEntity v = b.v;
         if (v.isAlive() && b.p.isAlive() && t <= 140L && Bending.isBender(b.p)) {
            Vec3 want = b.p.getEyePosition().add(b.p.getLookAngle().scale(b.dist)).subtract(0.0, (double)(v.getBbHeight() / 2.0F), 0.0);
            BlockPos g = Elem.ground((ServerLevel)v.level(), want);
            if (g != null && want.y < (double)(g.getY() + 1)) {
               want = new Vec3(want.x, (double)(g.getY() + 1), want.z);
            }

            Vec3 next = v.position().lerp(want, 0.3);
            if (t % 5L == 0L) {
               next = next.add((v.getRandom().nextDouble() - 0.5) * 0.12, 0.0, (v.getRandom().nextDouble() - 0.5) * 0.12);
            }

            if (v instanceof ServerPlayer vp) {
               vp.connection.teleport(next.x, next.y, next.z, 0.0F, 0.0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
            } else {
               v.teleportTo(next.x, next.y, next.z);
            }

            v.setDeltaMovement(Vec3.ZERO);
            v.fallDistance = 0.0F;
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 10, false, false));
            v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 10, false, false));
            v.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 5, 10, false, false));
         } else {
            Component msg = Component.literal("Bloodbending control ceased.").withStyle(ChatFormatting.DARK_RED);
            b.p.displayClientMessage(msg, true);
            if (v instanceof ServerPlayer vp) {
               vp.displayClientMessage(msg, true);
            }

            it.remove();
         }
      }
   }

   static void leave(ServerPlayer p) {
      putBack(p);
      HEAL.remove(p.getUUID());
      OCTO.remove(p.getUUID());
   }

   static void clear() {
      DRAWN.clear();
      STRIKE.clear();
      HEAL.clear();
      OCTO.clear();
      WAVES.clear();
      BOUND.clear();
   }

   private static record Bound(ServerPlayer p, LivingEntity v, double dist, long start) {
   }

   private static record Wave(ServerPlayer p, ServerLevel l, Vec3 o, Vec3 dir, double width, long start, float dmg, Set<Integer> hit) {
   }
}
