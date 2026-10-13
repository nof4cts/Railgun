package com.gojo.mod;

import com.gojo.mod.fx.Fx;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "jjk"
)
public final class GojoEvents {
   private static final double INFINITY_RADIUS = 4.0;
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
   private static final float BLACK_FLASH_CHANCE = 0.12F;
   private static final float BLACK_FLASH_MULTIPLIER = 2.5F;
   private static final DustParticleOptions BLACK_DUST = new DustParticleOptions(new Vector3f(0.05F, 0.0F, 0.0F), 2.5F);
   private static final DustParticleOptions FLASH_RED_DUST = new DustParticleOptions(new Vector3f(0.9F, 0.0F, 0.05F), 1.5F);

   private GojoEvents() {
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.END && event.player instanceof ServerPlayer player) {
         CursedEnergy.regen(player);
         int purpleCharge = CursedEnergy.getPurpleCharge(player);
         if (purpleCharge > 0) {
            CursedEnergy.setPurpleCharge(player, purpleCharge - 1);
            if (!player.isAlive()) {
               CursedEnergy.setPurpleCharge(player, 0);
            } else if (purpleCharge == 1) {
               Techniques.firePurple(player.serverLevel(), player);
            } else {
               Techniques.purpleChargeTick(player.serverLevel(), player, purpleCharge);
            }
         }

         boolean infinity = CursedEnergy.isInfinity(player);
         if (infinity) {
            infinityField(player);
            if (player.tickCount % 20 == 0) {
               Fx.shimmer(player.serverLevel(), player.position(), UP, 1.3F, 4033535, 26, (byte)0, player.getId());
            }
         }

         if (player.tickCount % 20 == 0 && Sorcerers.is(player, Sorcerer.GOJO) && infinity) {
            player.displayClientMessage(Component.literal("Infinity ON").withStyle(ChatFormatting.AQUA), true);
         }
      }
   }

   private static void infinityField(ServerPlayer player) {
      ServerLevel level = player.serverLevel();
      AABB field = player.getBoundingBox().inflate(4.0);

      for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, field, p -> p.getOwner() != player)) {
         Vec3 motion = projectile.getDeltaMovement();
         if (motion.lengthSqr() > 0.01 && CursedEnergy.rippleReady(player)) {
            Fx.shimmer(level, projectile.position(), motion.scale(-1.0), 0.9F, 4033535, 10, (byte)1, -1);
         }

         projectile.setDeltaMovement(motion.scale(0.2));
         projectile.hurtMarked = true;
      }

      if (player.tickCount % 5 == 0) {
         for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(2.5))) {
            Vec3 away = monster.position().subtract(player.position());
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            if (!(flat.lengthSqr() < 1.0E-4)) {
               Vec3 dir = flat.normalize();
               monster.setDeltaMovement(dir.x * 0.6, 0.1, dir.z * 0.6);
               monster.hurtMarked = true;
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLivingAttack(LivingAttackEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && CursedEnergy.isInfinity(player)) {
         DamageSource source = event.getSource();
         if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
         }

         if (source.getEntity() != null
            || source.getDirectEntity() != null
            || source.is(DamageTypeTags.IS_PROJECTILE)
            || source.is(DamageTypeTags.IS_EXPLOSION)
            || source.is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
            Entity attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
            if (attacker != null && CursedEnergy.rippleReady(player)) {
               Vec3 center = player.position().add(0.0, (double)player.getBbHeight() / 2.0, 0.0);
               Vec3 dir = attacker.position().add(0.0, (double)attacker.getBbHeight() / 2.0, 0.0).subtract(center);
               if (dir.lengthSqr() > 1.0E-4) {
                  dir = dir.normalize();
                  Fx.shimmer(player.serverLevel(), center.add(dir.scale(0.9)), dir, 1.2F, 4033535, 12, (byte)1, -1);
               }
            }
         }

         return;
      }
   }

   @SubscribeEvent
   public static void onLivingHurt(LivingHurtEvent event) {
      DamageSource source = event.getSource();
      if (!(source.getEntity() instanceof ServerPlayer player) || !source.is(DamageTypes.PLAYER_ATTACK) || !CursedEnergy.isInfinity(player)) {
         return;
      }

      if (!(player.getRandom().nextFloat() >= 0.12F)) {
         event.setAmount(event.getAmount() * 2.5F);
         LivingEntity target = event.getEntity();
         ServerLevel level = player.serverLevel();
         double x = target.getX();
         double y = target.getY() + (double)target.getBbHeight() / 2.0;
         double z = target.getZ();
         level.sendParticles(BLACK_DUST, x, y, z, 50, 0.5, 0.5, 0.5, 0.0);
         level.sendParticles(FLASH_RED_DUST, x, y, z, 25, 0.6, 0.6, 0.6, 0.0);
         level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, 0.5, 0.5, 0.5, 0.4);
         Vec3 hit = new Vec3(x, y, z);
         Fx.arcs(level, hit, 2.2F, 16718362, 6, 8);
         Fx.burst(level, hit, 0.7F, 16728128, 4194304, 40, (byte)0);
         Fx.shake(level, hit, 1.0F, 1879048192, 8);
         level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.8F);
         player.displayClientMessage(Component.literal("BLACK FLASH!").withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD}), true);
      }
   }
}
