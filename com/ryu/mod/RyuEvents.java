package com.ryu.mod;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.ryu.mod.fx.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class RyuEvents {
   private static final float REINFORCED_DAMAGE_MULTIPLIER = 1.5F;
   private static final float KINETIC_SHIELD_MULTIPLIER = 0.7F;
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

   private RyuEvents() {
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent event) {
      if (event.phase == Phase.END && event.player instanceof ServerPlayer player) {
         CursedEnergy.regen(player);
         int domainTicks = CursedEnergy.getDomainTicks(player);
         if (domainTicks > 0) {
            if (domainTicks % 10 == 0 && domainTicks <= 200) {
               Techniques.domainPulse(player.serverLevel(), player);
            }

            CursedEnergy.setDomainTicks(player, domainTicks - 1);
            if (domainTicks == 1) {
               player.displayClientMessage(Component.literal("Domain collapsed - output still at maximum").withStyle(ChatFormatting.AQUA), true);
            }
         }

         boolean reinforced = CursedEnergy.isReinforced(player);
         if (reinforced && player.tickCount % 20 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, false));
            Fx.shimmer(player.serverLevel(), player.position(), UP, 1.2F, 58879, 26, (byte)0, player.getId());
         }

         if (player.tickCount % 20 == 0 && domainTicks <= 0 && Sorcerers.is(player, Sorcerer.RYU) && reinforced) {
            player.displayClientMessage(Component.literal("Reinforced").withStyle(ChatFormatting.AQUA), true);
         }
      }
   }

   @SubscribeEvent
   public static void onLivingHurt(LivingHurtEvent event) {
      LivingEntity victim = event.getEntity();
      if (victim instanceof ServerPlayer defender && CursedEnergy.isReinforced(defender)) {
         event.setAmount(event.getAmount() * 0.7F);
         Entity from = event.getSource().getDirectEntity() != null ? event.getSource().getDirectEntity() : event.getSource().getEntity();
         if (from != null) {
            Vec3 center = defender.getBoundingBox().getCenter();
            Vec3 dir = from.getBoundingBox().getCenter().subtract(center);
            if (dir.lengthSqr() > 1.0E-4) {
               dir = dir.normalize();
               Fx.shimmer(defender.serverLevel(), center.add(dir.scale(0.8)), dir, 1.0F, 58879, 10, (byte)1, -1);
            }
         }
      }

      DamageSource source = event.getSource();
      if (source.getEntity() instanceof ServerPlayer attacker && source.is(DamageTypes.PLAYER_ATTACK) && CursedEnergy.isReinforced(attacker)) {
         event.setAmount(event.getAmount() * 1.5F);
         victim.knockback(1.5, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
         ServerLevel level = attacker.serverLevel();
         Vec3 hit = victim.getBoundingBox().getCenter();
         Fx.arcs(level, hit, 1.4F, 58879, 4, 6);
         Fx.burst(level, hit, 0.5F, 14745599, 58879, 20, (byte)0);
         Fx.shake(level, hit, 0.5F, 545325055, 6);
      }
   }
}
