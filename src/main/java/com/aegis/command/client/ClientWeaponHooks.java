package com.aegis.command.client;

import com.aegis.command.client.fx.FxWorld;
import com.aegis.command.client.fx.ImpactFrames;
import com.aegis.command.client.fx.ScreenFx;
import com.aegis.command.client.fx.WeaponFx;
import com.aegis.command.client.render.WeaponAnimator;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Client reactions to weapon events: animation triggers, camera recoil, local particles and sounds. */
public final class ClientWeaponHooks {
    private ClientWeaponHooks() {}

    private static boolean isLocal(LivingEntity e) {
        return e == Minecraft.getInstance().player;
    }

    private static Vec3 muzzle(LivingEntity e) {
        Vec3 look = e.getViewVector(1f);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return e.getEyePosition().add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.22, 0);
    }

    public static void onFire(LivingEntity shooter, ItemStack stack, WeaponType type, float strength) {
        if (!isLocal(shooter)) return;
        WeaponAnimator.HAND.fire(type, strength);
        Player p = (Player) shooter;
        float kick = type.recoil * strength;
        p.setXRot(p.getXRot() - kick * 1.6f);
        p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * kick * 0.8f);
        ScreenFx.addTrauma(type.heavy ? 0.32f * strength : type == WeaponType.HELIX_4 ? 0.14f : type == WeaponType.PHANTOM_9 ? 0.07f : 0.025f);
        int c = AegisWeaponItem.energyColor(stack);
        if (type.heavy) {
            Vec3 back = p.getLookAngle().scale(-(type == WeaponType.TITAN_X ? 0.45 : 0.3) * strength);
            p.push(back.x, Math.max(0, back.y) + 0.05, back.z);
            // cannon blast: the shot itself gets an impact frame
            ImpactFrames.trigger(0.55f * strength, c);
            Vec3 mz = muzzle(shooter);
            FxWorld.ring(mz.add(p.getLookAngle().scale(0.8)), 0.2f, 2.4f, 0.35f, c, false);
            FxWorld.sparkBurst(mz, 26, 9f, 0.45f, c, 6f);
        } else {
            WeaponFx.casing(muzzle(shooter), p.getLookAngle(), c);
            FxWorld.sparkBurst(muzzle(shooter).add(p.getLookAngle().scale(0.3)), type == WeaponType.HELIX_4 ? 16 : 4, 5f, 0.18f, c, 4f);
        }
        Vector3f col = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
        Vec3 m = muzzle(shooter);
        var level = shooter.level();
        for (int i = 0; i < (type.heavy ? 14 : 4); i++) {
            level.addParticle(new DustParticleOptions(col, 0.6f), m.x, m.y, m.z,
                    (level.random.nextDouble() - 0.5) * 0.1, (level.random.nextDouble() - 0.5) * 0.1, (level.random.nextDouble() - 0.5) * 0.1);
        }
        if (type.heavy || type == WeaponType.HELIX_4) {
            for (int i = 0; i < (type.heavy ? 8 : 4); i++) level.addParticle(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 0, 0.02, 0);
        }
    }

    public static void onReload(LivingEntity e, WeaponType type) {
        if (!isLocal(e)) return;
        WeaponAnimator.HAND.reload(type);
        int d = type.reloadTicks;
        double x = e.getX(), y = e.getY() + 1, z = e.getZ();
        Sfx.at(SoundEvents.BEACON_DEACTIVATE, x, y, z, 1.8f, 0.4f);
        Sfx.later((int) (d * 0.35), () -> Sfx.at(SoundEvents.ARMOR_EQUIP_IRON, x, y, z, 1.5f, 0.7f));
        Sfx.later((int) (d * 0.55), () -> Sfx.at(SoundEvents.IRON_TRAPDOOR_CLOSE, x, y, z, 1.9f, 0.5f));
        Sfx.later((int) (d * 0.75), () -> Sfx.at(SoundEvents.BEACON_POWER_SELECT, x, y, z, 1.9f, 0.4f));
    }

    public static void onCharge(LivingEntity e, ItemStack stack, float charge, int used) {
        if (!isLocal(e)) return;
        WeaponAnimator.HAND.setCharge(charge);
        if (used % 4 == 0 && charge < 1f) {
            Sfx.at(SoundEvents.NOTE_BLOCK_BIT.value(), e.getX(), e.getY() + 1, e.getZ(), 0.5f + charge * 1.4f, 0.35f);
        }
        if (used == AegisWeaponItem.TITAN_FULL_CHARGE) {
            Sfx.at(SoundEvents.BEACON_POWER_SELECT, e.getX(), e.getY() + 1, e.getZ(), 1.6f, 0.6f);
        }
        ScreenFx.addTrauma(0.01f * charge);
        Vec3 m = muzzle(e);
        var level = e.level();
        if (level.random.nextFloat() < 0.3f + charge * 0.6f) {
            Vec3 off = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).scale(0.35);
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, m.x + off.x, m.y + off.y, m.z + off.z, -off.x * 0.4, -off.y * 0.4, -off.z * 0.4);
        }
    }
}
