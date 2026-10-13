package com.avatar.mod;

import java.util.ArrayList;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Earth {
   public static final int GAUNT_TICKS = 200;
   public static final int COFFIN_TICKS = 100;
   private static final Map<UUID, Long> GAUNT = new HashMap<>();
   private static final List<Earth.Coffin> COFFINS = new ArrayList<>();

   private Earth() {
   }

   static void key(ServerPlayer p, int slot) {
      switch (slot) {
         case 0:
            wave(p);
            break;
         case 1:
            boulder(p);
            break;
         case 2:
            pillars(p);
            break;
         case 3:
            gauntlets(p);
            break;
         case 4:
            wall(p);
            break;
         case 5:
            sense(p);
            break;
         case 6:
            coffin(p);
      }
   }

   private static Vec3 flat(ServerPlayer p) {
      Vec3 l = p.getLookAngle();
      Vec3 f = new Vec3(l.x, 0.0, l.z);
      return f.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
   }

   private static void wave(ServerPlayer p) {
      if (Elem.pay(p, 10)) {
         Bending.setCooldown(p, 0, 20);
         Bending.pose(p, 14, 10);
         ServerLevel l = p.serverLevel();
         Vec3 dir = flat(p);
         Vec3 start = p.position();
         Net.fx(l, new Net.Fx((byte)25, start, dir, 0.0F, p.getId(), 0, 16));
         Bending.play(l, start, (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 1.4F, 0.7F);
         Bending.play(l, start, SoundEvents.STONE_BREAK, 1.2F, 0.5F);
         Set<Integer> hit = new HashSet<>();

         for (int i = 1; i <= 16; i++) {
            int k = i;
            Bending.later(
               i,
               () -> {
                  Vec3 front = start.add(dir.scale((double)k + 0.5));

                  for (LivingEntity e : l.getEntitiesOfClass(
                     LivingEntity.class,
                     new AABB(front, front).inflate(1.6, 2.5, 1.6),
                     ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !hit.contains(ex.getId())
                  )) {
                     hit.add(e.getId());
                     Bending.hit(p, e, 8.0F);
                     e.setDeltaMovement(dir.x * 0.3, 0.85, dir.z * 0.3);
                     e.hurtMarked = true;
                  }

                  if (k % 4 == 0) {
                     Bending.play(l, front, SoundEvents.STONE_BREAK, 0.8F, 0.6F);
                  }
               }
            );
         }
      }
   }

   private static void boulder(ServerPlayer p) {
      if (Elem.pay(p, 15)) {
         Bending.setCooldown(p, 1, 30);
         Bending.pose(p, 20, 8);
         ServerLevel l = p.serverLevel();
         Vec3 pos = p.position().add(flat(p).scale(1.8));
         Net.fx(l, new Net.Fx((byte)26, pos, Vec3.ZERO, 1.2F, p.getId(), 0, 7));
         Bending.play(l, pos, SoundEvents.STONE_BREAK, 1.4F, 0.6F);
         Bending.play(l, pos, (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 1.0F, 1.2F);
         Bending.later(7, () -> {
            if (p.isAlive()) {
               Bending.pose(p, 5, 9);
               Vec3 from = pos.add(0.0, 1.6, 0.0);
               Vec3 target = Bending.aim(p, 40.0);
               Vec3 d = target.subtract(from);
               double dist = d.length();
               Vec3 dir = d.normalize().add(0.0, Math.min(0.4, dist * 0.011), 0.0).normalize();
               Bending.launchElem(p, from, dir, 1.2F, 1.5F, 14.0F, 60, 2.5F, 2, 0.035);
               Bending.play(l, from, (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.0F, 0.6F);
            }
         });
      }
   }

   private static void pillars(ServerPlayer p) {
      ServerLevel l = p.serverLevel();
      BlockPos g = Elem.ground(l, Bending.aim(p, 32.0));
      if (g != null) {
         if (Elem.pay(p, 15)) {
            Bending.setCooldown(p, 2, 40);
            Bending.pose(p, 14, 8);
            BlockState mat = Elem.earthOf(l, g);
            int[][] offs = new int[][]{{0, 0}, {1 + l.random.nextInt(2), l.random.nextInt(3) - 1}, {-1 - l.random.nextInt(2), l.random.nextInt(3) - 1}};

            for (int[] o : offs) {
               BlockPos base = g.offset(o[0], 0, o[1]);
               int h = 3 + l.random.nextInt(2);

               for (int k = 1; k <= h; k++) {
                  BlockPos at = base.above(k);
                  Bending.later(k, () -> TempBlocks.place(l, at, mat, Bending.now + 100L));
               }

               Vec3 top = Vec3.atBottomCenterOf(base.above(1));
               Net.fx(l, new Net.Fx((byte)27, top, Vec3.ZERO, 1.2F, p.getId(), 0, 0));
            }

            Vec3 c = Vec3.atBottomCenterOf(g.above());

            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.5, 2.0, 2.5), ex -> ex != p && ex.isAlive())) {
               Bending.hit(p, e, 10.0F);
               e.setDeltaMovement(e.getDeltaMovement().x, 1.15, e.getDeltaMovement().z);
               e.hurtMarked = true;
            }

            Bending.play(l, c, SoundEvents.GENERIC_EXPLODE, 0.8F, 0.5F);
            Bending.play(l, c, SoundEvents.DEEPSLATE_BREAK, 1.6F, 0.6F);
         }
      }
   }

   private static void gauntlets(ServerPlayer p) {
      if (Elem.pay(p, 40)) {
         Bending.setCooldown(p, 3, 400);
         GAUNT.put(p.getUUID(), Bending.now + 200L);
         Net.fx(p.serverLevel(), new Net.Fx((byte)28, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 200));
         Bending.pose(p, 20, 8);
         Bending.play(p.serverLevel(), p.position(), SoundEvents.STONE_PLACE, 1.5F, 0.6F);
         Bending.play(p.serverLevel(), p.position(), SoundEvents.DEEPSLATE_PLACE, 1.5F, 0.7F);
      }
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent e) {
      if (e.getSource().getDirectEntity() instanceof ServerPlayer p && e.getSource().getEntity() == p && hasGauntlets(p)) {
         LivingEntity v = e.getEntity();
         e.setAmount(e.getAmount() + 8.0F);
         Vec3 d = v.position().subtract(p.position()).multiply(1.0, 0.0, 1.0).normalize();
         Bending.knock(v, d, 1.4, 0.4);
         Net.fx(p.serverLevel(), new Net.Fx((byte)27, v.position().add(0.0, (double)v.getBbHeight() * 0.6, 0.0), d, 0.7F, p.getId(), 0, 0, 2));
         Bending.play(p.serverLevel(), v.position(), SoundEvents.ANVIL_LAND, 0.5F, 1.4F);
         Bending.play(p.serverLevel(), v.position(), SoundEvents.STONE_BREAK, 1.2F, 0.7F);
      }
   }

   static boolean hasGauntlets(ServerPlayer p) {
      Long t = GAUNT.get(p.getUUID());
      return t != null && t > Bending.now;
   }

   private static void wall(ServerPlayer p) {
      ServerLevel l = p.serverLevel();
      Vec3 f = flat(p);
      Vec3 r = new Vec3(-f.z, 0.0, f.x);
      Vec3 c = p.position().add(f.scale(3.0));
      BlockPos g = Elem.ground(l, c);
      if (g != null && Elem.pay(p, 15)) {
         Bending.setCooldown(p, 4, 120);
         Bending.pose(p, 20, 10);
         BlockState mat = Elem.earthOf(l, g);

         for (int y = 1; y <= 3; y++) {
            int yy = y;
            Bending.later(y * 2, () -> {
               for (int i = -2; i <= 2; i++) {
                  Vec3 at = c.add(r.scale((double)i));
                  BlockPos col = Elem.ground(l, at);
                  if (col != null) {
                     TempBlocks.place(l, col.above(yy), mat, Bending.now + 120L);
                  }
               }

               Bending.play(l, c, SoundEvents.STONE_PLACE, 1.4F, 0.6F + (float)yy * 0.1F);
            });
         }

         Net.fx(l, new Net.Fx((byte)27, Vec3.atBottomCenterOf(g.above()), Vec3.ZERO, 2.0F, p.getId(), 0, 0));
         Bending.play(l, c, (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 1.2F, 0.8F);
      }
   }

   private static void sense(ServerPlayer p) {
      if (Elem.pay(p, 40)) {
         Bending.setCooldown(p, 5, 400);
         Bending.pose(p, 14, 10);
         ServerLevel l = p.serverLevel();
         Net.fx(l, new Net.Fx((byte)29, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 40));

         for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40.0), ex -> ex != p && ex.isAlive())) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
         }

         Bending.play(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.8F);
         Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 1.0F, 0.5F);
      }
   }

   private static void coffin(ServerPlayer p) {
      LivingEntity v = Elem.target(p, 24.0);
      if (v == null || !v.onGround()) {
         p.displayClientMessage(Component.literal("Aim at someone standing on the ground.").withStyle(ChatFormatting.GRAY), true);
      } else if (Elem.pay(p, 50)) {
         Bending.setCooldown(p, 6, 600);
         ServerLevel l = p.serverLevel();
         Bending.hold(p, 26);
         Bending.pose(p, 14, 10);
         Net.fx(l, new Net.Fx((byte)36, p.getEyePosition(), p.getLookAngle(), 0.0F, p.getId(), 4, 42));
         Net.fx(l, new Net.Fx((byte)30, v.position(), Vec3.ZERO, 0.0F, p.getId(), v.getId(), 112));
         Bending.play(l, v.position(), (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 2.0F, 0.6F);
         Bending.later(12, () -> {
            if (v.isAlive()) {
               Bending.pose(p, 20, 10);
               BlockPos base = v.blockPosition();
               long until = Bending.now + 100L;
               BlockState mat = Elem.earthOf(l, base.below());
               TempBlocks.carve(l, base.below(1), Blocks.AIR.defaultBlockState(), until);
               TempBlocks.carve(l, base.below(2), Blocks.AIR.defaultBlockState(), until);
               v.teleportTo((double)base.getX() + 0.5, (double)(base.getY() - 2), (double)base.getZ() + 0.5);
               TempBlocks.place(l, base, mat, until);
               COFFINS.add(new Earth.Coffin(p, v, Bending.now, base));
               Bending.play(l, v.position(), SoundEvents.GENERIC_EXPLODE, 1.2F, 0.5F);
               Bending.play(l, v.position(), SoundEvents.DEEPSLATE_BREAK, 2.0F, 0.5F);
            }
         });
      }
   }

   static void tickPlayer(ServerPlayer p) {
      Long t = GAUNT.get(p.getUUID());
      if (t != null && t < Bending.now) {
         GAUNT.remove(p.getUUID());
         Net.fx(p.serverLevel(), new Net.Fx((byte)28, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
      }
   }

   static void tick(MinecraftServer server) {
      Iterator<Earth.Coffin> it = COFFINS.iterator();

      while (it.hasNext()) {
         Earth.Coffin c = it.next();
         long t = Bending.now - c.start;
         if (t < 100L && c.v.isAlive()) {
            c.v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 10, false, false));
            c.v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0, false, false));
            if (t % 20L == 0L) {
               Bending.hit(c.p, c.v, 3.5F);
            }
         } else {
            if (c.v.isAlive()) {
               c.v.teleportTo((double)c.base.getX() + 0.5, (double)(c.base.getY() + 1), (double)c.base.getZ() + 0.5);
            }

            it.remove();
         }
      }
   }

   static void leave(ServerPlayer p) {
      GAUNT.remove(p.getUUID());
   }

   static void clear() {
      GAUNT.clear();
      COFFINS.clear();
   }

   private static record Coffin(ServerPlayer p, LivingEntity v, long start, BlockPos base) {
   }
}
