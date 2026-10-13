package com.mahoraga.mod;

import com.jjk.mod.Balance;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Ritual {
   public static final int LENGTH = 236;
   public static final int SPAWN = 196;
   public static final int EXECUTE = 214;
   public static final int DRAIN = 72000;
   private static final int ARM_WINDOW = 200;
   private static final Map<UUID, Long> ARMED = new HashMap<>();
   private static final Map<UUID, Ritual.Run> RUNS = new HashMap<>();

   private Ritual() {
   }

   public static boolean inRitual(ServerPlayer p) {
      return RUNS.containsKey(p.getUUID());
   }

   static void key(ServerPlayer p, int slot) {
      if (slot == 0 && Sorcerers.get(p) == Sorcerer.TEN_SHADOWS && p.isAlive() && !p.isSpectator() && !RUNS.containsKey(p.getUUID())) {
         if (Balance.used(p, slot)) {
            long now = p.serverLevel().getGameTime();
            Long armed = ARMED.get(p.getUUID());
            if (armed != null && now - armed <= 200L) {
               LivingEntity victim = null;
               double best = 400.0;

               for (LivingEntity e : p.serverLevel()
                  .getEntitiesOfClass(
                     LivingEntity.class, p.getBoundingBox().inflate(20.0), ex -> ex instanceof Mob && ex.isAlive() && !(ex instanceof MahoragaEntity)
                  )) {
                  double d = e.distanceToSqr(p);
                  if (d < best) {
                     best = d;
                     victim = e;
                  }
               }

               if (victim == null) {
                  p.displayClientMessage(
                     Component.literal("The ritual needs another creature to drag in. There is none nearby.").withStyle(ChatFormatting.RED), true
                  );
               } else {
                  ARMED.remove(p.getUUID());
                  start(p, victim);
               }
            } else {
               ARMED.put(p.getUUID(), now);
               Net.to(p, new Net.Fx(13, p.getId(), p.position(), 200.0F, ""));
               p.sendSystemMessage(
                  Component.literal("Warning: summoning Mahoraga kills you and seals the Ten Shadows for an hour. Press again to perform the ritual.")
                     .withStyle(ChatFormatting.DARK_RED)
               );
               p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.7F);
            }
         }
      }
   }

   private static void start(ServerPlayer p, LivingEntity victim) {
      ServerLevel l = p.serverLevel();
      Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
      look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
      Vec3 s = p.position().add(look.scale(3.4));
      int y = l.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, (int)Math.floor(s.x), (int)Math.floor(s.z));
      Vec3 spot = new Vec3(s.x, Math.abs((double)y - p.getY()) < 4.0 ? (double)y : p.getY(), s.z);
      Ritual.Run r = new Ritual.Run(p, victim, spot);
      RUNS.put(p.getUUID(), r);
      Balance.drain(p, 72000);
      Net.fx(l, new Net.Fx(12, p.getId(), spot, 236.0F, "", r.at, victim.getId()));
      Net.fx(l, new Net.Fx(14, p.getId(), p.position(), 196.0F, "", Vec3.ZERO, 1));
   }

   @SubscribeEvent
   public static void onTick(ServerTickEvent e) {
      if (e.phase == Phase.END && !RUNS.isEmpty()) {
         Iterator<Ritual.Run> it = RUNS.values().iterator();

         while (it.hasNext()) {
            Ritual.Run r = it.next();
            ServerLevel l = r.p.serverLevel();
            int t = (int)(l.getGameTime() - r.start);
            if ((r.p.isRemoved() || !r.p.isAlive() && t < 214) && r.maho == null) {
               it.remove();
            } else {
               if (t < 214 && r.p.isAlive()) {
                  r.p.teleportTo(r.at.x, r.at.y, r.at.z);
                  r.p.setDeltaMovement(Vec3.ZERO);
                  if (r.victim.isAlive() && r.maho == null) {
                     r.victim.teleportTo(r.victimAt.x, r.victimAt.y, r.victimAt.z);
                     r.victim.setDeltaMovement(Vec3.ZERO);
                  }
               }

               if (t < 186 && t % 22 == 0) {
                  play(l, r.at, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
               }

               switch (t) {
                  case 0:
                     play(l, r.at, (SoundEvent)SoundEvents.AMBIENT_CAVE.value(), 2.0F, 0.5F);
                     play(l, r.at, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 0.4F);
                     break;
                  case 34:
                  case 104:
                     play(l, r.at, SoundEvents.WOLF_HOWL, 2.0F, 0.65F);
                     break;
                  case 50:
                  case 120:
                     play(l, r.at, SoundEvents.WOLF_HOWL, 1.6F, 0.8F);
                     break;
                  case 76:
                  case 92:
                     play(l, r.at, SoundEvents.FROG_AMBIENT, 2.0F, 0.55F);
                     break;
                  case 140:
                     play(l, r.at, SoundEvents.ELDER_GUARDIAN_CURSE, 1.6F, 0.5F);
                     break;
                  case 158:
                     play(l, r.at, SoundEvents.WARDEN_ROAR, 2.0F, 0.6F);
                     break;
                  case 186:
                     play(l, r.spot, SoundEvents.WARDEN_EMERGE, 2.5F, 0.7F);
                     play(l, r.spot, SoundEvents.GENERIC_EXPLODE, 1.5F, 0.6F);
                     play(l, r.spot, SoundEvents.BELL_BLOCK, 2.5F, 0.4F);
                     break;
                  case 196:
                     MahoragaEntity m = (MahoragaEntity)((EntityType)MahoragaMod.MAHORAGA.get()).create(l);
                     if (m != null) {
                        float yaw = (float)Math.toDegrees(Math.atan2(r.at.z - r.spot.z, r.at.x - r.spot.x)) - 90.0F;
                        m.moveTo(r.spot.x, r.spot.y, r.spot.z, yaw, 0.0F);
                        m.setYBodyRot(yaw);
                        m.setYHeadRot(yaw);
                        m.summoned();
                        l.addFreshEntity(m);
                        r.maho = m;
                     }
                     break;
                  case 214:
                     if (r.maho != null && r.maho.isAlive() && r.p.isAlive() && r.p.level() == l) {
                        r.maho.executeSummoner(r.p);
                     }

                     if (r.maho != null && r.victim.isAlive()) {
                        r.maho.setTarget(r.victim);
                     }
               }

               if (t >= 236) {
                  it.remove();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onAttack(LivingAttackEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && !(e.getSource().getEntity() instanceof MahoragaEntity)) {
         Ritual.Run r = RUNS.get(p.getUUID());
         if (r != null && p.serverLevel().getGameTime() - r.start < 214L) {
            e.setCanceled(true);
         }
      }
   }

   private static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, BlockPos.containing(at), s, SoundSource.HOSTILE, vol, pitch);
   }

   private static final class Run {
      final ServerPlayer p;
      final LivingEntity victim;
      final Vec3 at;
      final Vec3 spot;
      final Vec3 victimAt;
      final long start;
      MahoragaEntity maho;

      Run(ServerPlayer p, LivingEntity victim, Vec3 spot) {
         this.p = p;
         this.victim = victim;
         this.at = p.position();
         this.spot = spot;
         this.victimAt = victim.position();
         this.start = p.serverLevel().getGameTime();
      }
   }
}
