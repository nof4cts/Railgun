package com.firestar.orbital.server;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.entity.BlightMob;
import com.firestar.orbital.entity.Inverted;
import com.firestar.orbital.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Parasitic: whatever the blight's creatures kill gets back up as one of them. */
@Mod.EventBusSubscriber(modid = OrbitalMod.MODID)
public final class BlightEvents {
    private BlightEvents() {}

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof BlightMob)) {
            return;
        }
        if (!(dead instanceof Animal || dead instanceof AbstractVillager || dead instanceof Player || dead instanceof net.minecraft.world.entity.monster.AbstractIllager
                || dead instanceof net.minecraft.world.entity.monster.Zombie)) {
            return;
        }
        Inverted risen = ModEntities.INVERTED.get().create(level);
        if (risen == null) {
            return;
        }
        risen.moveTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0);
        risen.finalizeSpawn(level, level.getCurrentDifficultyAt(risen.blockPosition()), MobSpawnType.CONVERSION, null, null);
        if (dead.hasCustomName()) {
            risen.setCustomName(dead.getCustomName());
        }
        risen.getPersistentData().putBoolean("orbital_blight", true);
        level.addFreshEntity(risen);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.DRAGON_BREATH, dead.getX(), dead.getY() + 1, dead.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
    }
}
