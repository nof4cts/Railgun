package com.yuji.server;

import com.yuji.Y;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Two things in the kit that Gojo's Infinity has no business stopping:
 * <ul>
 *   <li>the Inverted Spear of Heaven (Toji), which nullifies cursed techniques on contact;</li>
 *   <li>Sukuna's World-Cutting Slash, which cuts the space Infinity is made of.</li>
 * </ul>
 * Infinity works by cancelling the attack (the kit's Gojo and Teen Gojo both do). This runs last, sees the
 * cancelled attack, and if it came from one of those two and the victim was behind Infinity, lets it through.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class InfinityPierce {
    private static boolean shrineBroken;
    private static Field active, castMove, castCaster;

    private InfinityPierce() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttack(LivingAttackEvent e) {
        if (!e.isCanceled()) return;
        LivingEntity victim = e.getEntity();
        if (!(victim instanceof Player pv) || !behindInfinity(pv)) return;
        Entity by = e.getSource().getEntity();
        Entity direct = e.getSource().getDirectEntity();
        try {
            if (holdsSpear(by) || holdsSpear(direct) || worldSlash(by)) {
                e.setCanceled(false);
            }
        } catch (Throwable t) {
            Y.LOG.debug("[yuji] infinity check failed", t);
        }
    }

    private static boolean behindInfinity(Player p) {
        try {
            if (com.gojo.mod.CursedEnergy.isInfinity(p)) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (com.teengojo.TeenGojo.infinite(p)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** Toji's Inverted Spear of Heaven in either hand. */
    private static boolean holdsSpear(Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        Item spear = com.toji.mod.TojiMod.SPEAR.get();
        return le.getMainHandItem().is(spear) || le.getOffhandItem().is(spear);
    }

    /** Is this player in the middle of Sukuna's World-Cutting Slash? (The shrine's casts are private: read them.) */
    private static boolean worldSlash(Entity e) throws ReflectiveOperationException {
        if (!(e instanceof ServerPlayer p) || shrineBroken) return false;
        try {
            if (active == null) {
                Class<?> tech = Class.forName("com.shrine.server.Techniques");
                active = tech.getDeclaredField("ACTIVE");
                active.setAccessible(true);
                Class<?> cast = Class.forName("com.shrine.server.Techniques$Cast");
                castMove = cast.getDeclaredField("move");
                castMove.setAccessible(true);
                castCaster = cast.getDeclaredField("caster");
                castCaster.setAccessible(true);
            }
            for (Object c : (List<?>) active.get(null)) {
                if (castCaster.get(c) == p && "WORLD_SLASH".equals(String.valueOf(castMove.get(c)))) return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            shrineBroken = true;
            Y.LOG.warn("[yuji] couldn't read Sukuna's casts; the World-Cutting Slash won't pierce Infinity", ex);
            return false;
        }
    }
}
