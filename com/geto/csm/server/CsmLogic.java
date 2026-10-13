package com.geto.csm.server;

import com.geto.csm.CurseKind;
import com.geto.csm.ModRegistry;
import com.geto.csm.entity.CurseEntity;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.CutType;
import com.geto.csm.network.FxType;
import com.geto.csm.network.StatePacket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CsmLogic {
   public static final int MAX_SLOTS = 3;
   public static final int UZUMAKI_LOCK = 120;
   public static final int UZUMAKI_FIRE = 86;

   public static void select(ServerPlayer sp, int kind) {
      CsmState st = CsmState.get(sp);
      st.selected = Math.floorMod(kind, CurseKind.values().length);
      sync(sp, st);
   }

   public static void summon(ServerPlayer sp) {
      CsmState st = CsmState.get(sp);
      if (st.lockTicks <= 0) {
         CurseKind kind = CurseKind.byId(st.selected);
         long now = sp.level().getGameTime();
         if (now < st.readyAt[kind.ordinal()]) {
            long left = (st.readyAt[kind.ordinal()] - now + 19L) / 20L;
            sp.displayClientMessage(Component.literal("§7" + kind.title + " is recovering — " + left + "s"), true);
         } else {
            cleanSlots(sp, st);

            while (st.slots.size() >= 3) {
               dismissSlot(sp, st, 0);
            }

            ServerLevel lvl = sp.serverLevel();
            Vec3 look = Combat.flatLook(sp);
            float yaw = sp.getYRot();
            List<Integer> ids = new ArrayList<>();
            int count = 1;

            for (int i = 0; i < count; i++) {
               Vec3 pos = spawnPos(sp, kind, look, i);
               if (ModRegistry.typeOf(kind).create(lvl) instanceof CurseEntity e) {
                  e.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
                  e.yHeadRot = yaw;
                  e.yBodyRot = yaw;
                  e.bind(sp);
                  lvl.addFreshEntity(e);
                  ids.add(e.getId());
                  CsmNetwork.fx(sp, FxType.RIFT, sp, e, pos, (float)kind.ordinal(), (float)i);
               }
            }

            st.slots.add(ids);
            st.readyAt[kind.ordinal()] = now + (long)kind.cooldown;
            Combat.sound(sp, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7F, kind.special ? 0.5F : 0.9F);
            Combat.sound(sp, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.4F);
            if (kind.special) {
               CsmNetwork.cutscene(sp, CutType.SUMMON, sp.getId(), kind.ordinal());
            }

            sync(sp, st);
         }
      }
   }

   private static Vec3 spawnPos(ServerPlayer sp, CurseKind kind, Vec3 look, int i) {
      return switch (kind) {
         case WYRM -> sp.position().add(look.scale(4.0)).add(0.0, 2.5, 0.0);
         case ATROXIIA -> sp.position().add(look.multiply(1.0, 0.0, 1.0).normalize().scale(6.0));
         case CINDERVANE, RAEVYX -> sp.position().add(look.scale(5.0)).add(0.0, 3.0, 0.0);
         case TAMAMO -> sp.position().add(look.scale(4.0)).add(0.0, 3.0, 0.0);
         default -> sp.position().add(look.scale(3.0));
      };
   }

   private static void cleanSlots(ServerPlayer sp, CsmState st) {
      ServerLevel lvl = sp.serverLevel();
      st.slots.removeIf(slot -> {
         slot.removeIf(id -> {
            Entity e = lvl.getEntity(id);
            return e == null || !e.isAlive();
         });
         return slot.isEmpty();
      });
   }

   private static void dismissSlot(ServerPlayer sp, CsmState st, int index) {
      for (int id : st.slots.remove(index)) {
         if (sp.serverLevel().getEntity(id) instanceof CurseEntity c) {
            c.vanish();
         }
      }
   }

   public static void dismissAll(ServerPlayer sp) {
      CsmState st = CsmState.peek(sp);
      if (st != null) {
         while (!st.slots.isEmpty()) {
            dismissSlot(sp, st, 0);
         }
      }
   }

   public static List<CurseEntity> active(ServerPlayer sp, CsmState st) {
      cleanSlots(sp, st);
      List<CurseEntity> out = new ArrayList<>();

      for (List<Integer> slot : st.slots) {
         for (int id : slot) {
            if (sp.serverLevel().getEntity(id) instanceof CurseEntity c) {
               out.add(c);
            }
         }
      }

      return out;
   }

   public static void dropOrb(LivingEntity victim) {
      if (victim.level() instanceof ServerLevel lvl) {
         Vec3 var4 = victim.position().add(0.0, (double)victim.getBbHeight() * 0.5, 0.0);
         ItemEntity orb = new ItemEntity(lvl, var4.x, var4.y, var4.z, new ItemStack((ItemLike)ModRegistry.CURSE_ORB.get()));
         orb.setDeltaMovement(0.0, 0.25, 0.0);
         orb.setPickUpDelay(10);
         lvl.addFreshEntity(orb);
         CsmNetwork.fx(lvl, FxType.ORB_FORM, victim.getId(), -1, var4, 0.0F, 0.0F);
      }
   }

   public static void absorb(ServerPlayer sp) {
      CsmState st = CsmState.get(sp);
      st.absorbed++;
      CsmNetwork.fx(sp, FxType.ABSORB, sp, (Entity)null, sp.getEyePosition(), (float)st.absorbed, 0.0F);
      Combat.sound(sp, SoundEvents.PLAYER_BURP, 0.8F, 0.6F);
      sp.displayClientMessage(Component.literal("§5Curse absorbed. §7It tastes like something scraped off a floor.  §fStored: " + st.absorbed), true);
      sync(sp, st);
   }

   public static void uzumaki(ServerPlayer sp, Item item) {
      CsmState st = CsmState.get(sp);
      if (st.lockTicks <= 0 && !sp.getCooldowns().isOnCooldown(item)) {
         List<CurseEntity> curses = active(sp, st);
         int n = curses.size() + st.absorbed;
         if (n == 0) {
            sp.displayClientMessage(Component.literal("§7You have no curses to combine. Summon some, or swallow curse orbs."), true);
         } else {
            Set<String> extracted = new LinkedHashSet<>();

            for (CurseEntity c : curses) {
               if (c.kind().special) {
                  extracted.add(c.kind().title);
               }
            }

            st.lockTicks = 120;
            Combat.stun(sp, 110);
            Vec3 core = sp.getEyePosition().add(sp.getLookAngle().scale(3.5));
            CsmNetwork.cutscene(sp, CutType.UZUMAKI, sp.getId(), n, extracted.isEmpty() ? 0 : 1, curses.size());
            CsmNetwork.fx(sp, FxType.UZUMAKI_CORE, sp, (Entity)null, core, (float)n, 0.0F);

            for (CurseEntity cx : curses) {
               cx.vowTicks = 40;
               CsmNetwork.fx(sp, FxType.UZUMAKI_GATHER, cx, sp, core, (float)cx.kind().ordinal(), 0.0F);
               Scheduler.after(24, cx::discard);
            }

            st.slots.clear();
            st.absorbed = 0;
            Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.4F);
            Combat.sound(sp, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
            sp.getCooldowns().addCooldown(item, 600);
            float damage = Math.min(100.0F, 20.0F + 5.0F * (float)n);
            Scheduler.after(86, () -> {
               if (sp.isAlive()) {
                  Vec3 dir = sp.getLookAngle();
                  Vec3 origin = sp.getEyePosition().add(dir.scale(3.5));
                  float yaw = sp.getYRot();
                  float pitch = sp.getXRot();
                  CsmNetwork.fx(sp, FxType.UZUMAKI_BEAM, sp, (Entity)null, origin, yaw, pitch);
                  Combat.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
                  Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 3.0F, 0.5F);
                  double length = 52.0;
                  AABB box = new AABB(origin, origin.add(dir.scale(length))).inflate(5.0);

                  for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box, x -> x != sp && x.isAlive() && !x.isSpectator())) {
                     if (e instanceof CurseEntity cx && cx.ownerId() == sp.getId()) {
                        continue;
                     }

                     Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
                     double along = to.dot(dir);
                     if (!(along < -1.0) && !(along > length)) {
                        double off = to.subtract(dir.scale(along)).length();
                        if (!(off > 3.2 + along * 0.06)) {
                           Combat.damage(sp, e, damage, true);
                           CurseEntity.launch(e, dir.scale(2.6).add(0.0, 0.6, 0.0));
                        }
                     }
                  }

                  if (!extracted.isEmpty()) {
                     sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1800, 1));
                     sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1800, 0));
                     sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1800, 0));
                     sp.displayClientMessage(Component.literal("§dCursed technique extracted from: §f" + String.join(", ", extracted)), false);
                  }
               }
            });
            sync(sp, st);
         }
      }
   }

   public static void tick(ServerPlayer sp) {
      CsmState st = CsmState.peek(sp);
      if (st != null) {
         if (st.lockTicks > 0) {
            st.lockTicks--;
         }

         if (sp.tickCount % 20 == 0) {
            cleanSlots(sp, st);
            sync(sp, st);
         }
      }
   }

   public static void sync(ServerPlayer sp, CsmState st) {
      long now = sp.level().getGameTime();
      int[] cds = new int[CurseKind.values().length];

      for (int i = 0; i < cds.length; i++) {
         cds[i] = (int)Mth.clamp((float)(st.readyAt[i] - now), 0.0F, 2.1474836E9F);
      }

      CsmNetwork.toPlayer(sp, new StatePacket(st.selected, st.absorbed, st.slots.size(), cds));
   }

   private CsmLogic() {
   }
}
