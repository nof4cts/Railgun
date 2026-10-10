package com.hakari.idg.server;

import com.hakari.idg.ModRegistry;
import com.hakari.idg.Technique;
import com.hakari.idg.TechniqueScrollItem;
import com.hakari.idg.network.CutType;
import com.hakari.idg.network.FxType;
import com.hakari.idg.network.HakariNetwork;
import com.hakari.idg.network.StatePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Idle Death Gamble state machine:
 * cast cutscene → domain → (2 visuals = riichi spin) → jackpot cutscene → 4:11 jackpot.
 */
public final class HakariLogic {
    public static final float DOMAIN_RADIUS = 20f;
    public static final int CAST_TICKS = 180;          // 9.0 s domain cutscene
    public static final int DOMAIN_DURATION = 20 * 60; // 60 s to hit before the domain burns out
    public static final int RIICHI_TICKS = 72;         // 3.6 s riichi overlay
    public static final int JACKPOT_CUT_TICKS = 110;   // 5.5 s jackpot cutscene
    public static final int JACKPOT_FULL = 251 * 20;   // 4 minutes 11 seconds

    private static final float[] TIER_CHANCE = {0.10f, 0.30f, 0.65f, 1.0f};
    private static final RandomSource RNG = RandomSource.create();

    // ───────────────────────────── input ─────────────────────────────

    public static void use(ServerPlayer sp, TechniqueScrollItem item) {
        HakariState st = HakariState.get(sp);
        if (st.locked()) return;
        if (sp.getCooldowns().isOnCooldown(item)) return;
        boolean jp = st.jackpotTicks > 0;
        int cd;
        switch (item.technique) {
            case RESERVE_BALL -> cd = jp ? Moves.luckyVolley(sp, st) : Moves.reserveBall(sp, st);
            case SHUTTER_DOORS -> cd = jp ? Moves.luckyRushdown(sp, st) : Moves.shutterDoors(sp, st);
            case ROUGH_ENERGY -> cd = jp ? Moves.overwhelmingLuck(sp, st) : Moves.roughEnergy(sp, st);
            case FEVER_BREAKER -> cd = jp ? Moves.energySurge(sp, st) : Moves.feverBreaker(sp, st);
            case COUNTER -> cd = jp ? Moves.rhythm(sp, st) : Moves.counter(sp, st);
            case DOMAIN -> {
                if (jp || st.domainActive) {
                    sp.displayClientMessage(Component.literal("§6You're already on a roll."), true);
                    return;
                }
                cd = startDomain(sp, st);
            }
            default -> cd = 0;
        }
        if (cd > 0) {
            if (st.rhythmTicks > 0 && item.technique != Technique.DOMAIN) cd = Math.max(4, cd * 2 / 5);
            sp.getCooldowns().addCooldown(item, cd);
        }
        sync(sp, st);
    }

    // ───────────────────────────── domain ─────────────────────────────

    private static int startDomain(ServerPlayer sp, HakariState st) {
        ServerLevel lvl = sp.serverLevel();
        st.castTicks = CAST_TICKS;
        st.domainCenter = sp.position();
        st.radius = DOMAIN_RADIUS;
        st.trapped.clear();
        st.visuals = 0;
        st.fails = 0;
        st.chanceBonus = 0;
        st.riichiTicks = 0;
        st.pose = 1;

        AABB box = sp.getBoundingBox().inflate(DOMAIN_RADIUS);
        for (LivingEntity le : lvl.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != sp && e.isAlive() && !e.isSpectator() && e.distanceTo(sp) <= DOMAIN_RADIUS)) {
            st.trapped.add(le.getId());
            Combat.stun(le, CAST_TICKS + 40); // +2 s "brain freeze" after the rules hit
        }
        Combat.stun(sp, CAST_TICKS);

        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        HakariNetwork.cutscene(sp, CutType.DOMAIN, sp.getId(), 0, st.boosted ? 1 : 0);
        for (int id : st.trapped) {
            Entity e = lvl.getEntity(id);
            if (e instanceof ServerPlayer victim) HakariNetwork.cutscene(victim, CutType.DOMAIN, sp.getId(), 1, st.boosted ? 1 : 0);
        }
        return 20 * 600; // the domain scroll stays locked until the domain resolves
    }

    private static void openDomain(ServerPlayer sp, HakariState st) {
        st.domainActive = true;
        st.domainTicks = DOMAIN_DURATION;
        st.pose = 0;
        Combat.sound(sp, SoundEvents.BELL_BLOCK, 2f, 0.6f);
        if (st.boosted) sp.displayClientMessage(Component.literal("§d確変 — Probability Up: odds doubled this domain"), true);
    }

    private static void enforceBarrier(ServerPlayer sp, HakariState st, ServerLevel lvl) {
        List<Entity> inside = new ArrayList<>();
        inside.add(sp);
        Iterator<Integer> it = st.trapped.iterator();
        while (it.hasNext()) {
            Entity e = lvl.getEntity(it.next());
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            inside.add(e);
        }
        Vec3 c = st.domainCenter;
        for (Entity e : inside) {
            Vec3 off = e.position().subtract(c);
            double d = off.length();
            if (d > st.radius - 1.0) {
                Vec3 dir = d < 1.0e-3 ? new Vec3(1, 0, 0) : off.scale(1.0 / d);
                Vec3 back = c.add(dir.scale(st.radius - 1.6));
                e.teleportTo(back.x, Math.max(back.y, c.y - 2), back.z);
                e.setDeltaMovement(dir.scale(-0.6));
                e.hurtMarked = true;
            }
        }
    }

    private static void collapse(ServerPlayer sp, HakariState st, boolean burnout) {
        boolean was = st.domainActive || st.castTicks > 0;
        st.domainActive = false;
        st.castTicks = 0;
        st.riichiTicks = 0;
        st.trapped.clear();
        if (!was) return;
        HakariNetwork.fx(sp, burnout ? FxType.DOMAIN_COLLAPSE : FxType.DOMAIN_SHATTER, sp, null, st.domainCenter, st.radius, 0);
        Combat.sound(sp.serverLevel(), st.domainCenter, SoundEvents.GLASS_BREAK, 3f, 0.5f);
        if (burnout) {
            st.boosted = false;
            sp.getCooldowns().addCooldown(ModRegistry.DOMAIN.get(), 20 * 45);
            for (RegistryObject<Item> ro : ModRegistry.MOVE_SCROLLS) sp.getCooldowns().addCooldown(ro.get(), 100);
            sp.displayClientMessage(Component.literal("§7The domain burns out. Your brain is fried — cursed technique on cooldown."), true);
        }
    }

    // ───────────────────────────── spins ─────────────────────────────

    /** Called whenever a "visual" lands (reserve ball, doors, a landed fever breaker, a counter). */
    public static void onVisual(ServerPlayer sp, HakariState st) {
        if (!st.domainActive || st.riichiTicks > 0) return;
        st.visuals++;
        HakariNetwork.fx(sp, FxType.VISUAL_TICK, sp, null, sp.position(), st.visuals, 0);
        if (st.visuals >= 2) startRiichi(sp, st);
        sync(sp, st);
    }

    private static void startRiichi(ServerPlayer sp, HakariState st) {
        st.visuals = 0;
        boolean pity = st.fails >= 3;
        float r = RNG.nextFloat();
        int tier = pity ? 3 : r < 0.5f ? 0 : r < 0.8f ? 1 : r < 0.95f ? 2 : 3;
        float chance = TIER_CHANCE[tier] + st.chanceBonus;
        if (st.boosted) chance *= 2f;
        boolean win = pity || RNG.nextFloat() < chance;
        int num = 1 + RNG.nextInt(7);
        int fin = win ? num : 1 + ((num - 1 + 1 + RNG.nextInt(6)) % 7);

        st.riichiTier = tier;
        st.riichiScenario = RNG.nextInt(3);
        st.riichiNumber = num;
        st.riichiFinal = fin;
        st.riichiWin = win;
        st.riichiPity = pity;
        st.riichiTicks = RIICHI_TICKS;

        int[] args = {tier, st.riichiScenario, num, fin, win ? 1 : 0, pity ? 1 : 0};
        HakariNetwork.cutscene(sp, CutType.RIICHI, sp.getId(), args);
        for (int id : st.trapped) {
            Entity e = sp.serverLevel().getEntity(id);
            if (e instanceof ServerPlayer p) HakariNetwork.cutscene(p, CutType.RIICHI, sp.getId(), args);
        }
        Combat.sound(sp, SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 1.2f);
    }

    private static void resolveRiichi(ServerPlayer sp, HakariState st) {
        if (st.riichiWin) {
            startJackpot(sp, st, st.riichiNumber, st.riichiPity);
        } else {
            st.fails++;
            st.chanceBonus += 0.05f;
            String pity = st.fails >= 3 ? "  §d§lPITY READY" : "";
            sp.displayClientMessage(Component.literal("§7ハズレ — miss.  §fMisses: " + st.fails + "/3" + pity), true);
        }
    }

    // ───────────────────────────── jackpot ─────────────────────────────

    private static void startJackpot(ServerPlayer sp, HakariState st, int number, boolean pity) {
        collapse(sp, st, false);
        st.boosted = number % 2 == 1; // odd jackpot → probability up next domain
        st.jackpotNumber = number;
        st.jackpotMax = pity ? JACKPOT_FULL / 2 : JACKPOT_FULL;
        st.jackpotCutTicks = JACKPOT_CUT_TICKS;
        st.fails = 0;
        st.chanceBonus = 0;
        st.pose = 0;
        sp.setHealth(sp.getMaxHealth());
        clearHarmful(sp);

        Combat.sound(sp, SoundEvents.TOTEM_USE, 1.5f, 1f);
        Combat.sound(sp, SoundEvents.PLAYER_LEVELUP, 1.5f, 0.7f);
        for (ServerPlayer p : sp.serverLevel().players()) {
            if (p.distanceToSqr(sp) < 48 * 48) {
                HakariNetwork.cutscene(p, CutType.JACKPOT, sp.getId(), number, pity ? 1 : 0, p == sp ? 1 : 0);
            }
        }
        for (RegistryObject<Item> ro : ModRegistry.MOVE_SCROLLS) sp.getCooldowns().removeCooldown(ro.get());
        sp.getCooldowns().removeCooldown(ModRegistry.DOMAIN.get());
        sp.getCooldowns().addCooldown(ModRegistry.DOMAIN.get(), st.jackpotMax + JACKPOT_CUT_TICKS);
    }

    private static void jackpotTick(ServerPlayer sp, HakariState st) {
        // Infinite cursed energy pours into automatic reverse cursed technique.
        sp.heal(1.0f);
        sp.clearFire();
        sp.fallDistance = 0;
        sp.getFoodData().setFoodLevel(20);
        if (sp.tickCount % 20 == 0) clearHarmful(sp);
    }

    private static void endJackpot(ServerPlayer sp, HakariState st) {
        HakariNetwork.fx(sp, FxType.JACKPOT_END, sp, null, sp.position(), 0, 0);
        Combat.sound(sp, SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.8f);
        // The leftover energy is enough to expand the domain again right away.
        sp.getCooldowns().removeCooldown(ModRegistry.DOMAIN.get());
        sp.displayClientMessage(Component.literal("§6Jackpot over — the domain is ready. Spin again."), true);
    }

    private static void clearHarmful(ServerPlayer sp) {
        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance inst : sp.getActiveEffects()) {
            if (!inst.getEffect().isBeneficial()) bad.add(inst);
        }
        for (MobEffectInstance inst : bad) sp.removeEffect(inst.getEffect());
    }

    // ───────────────────────────── tick ─────────────────────────────

    public static void tick(ServerPlayer sp) {
        HakariState st = HakariState.peek(sp);
        if (st == null) return;
        boolean dirty = false;
        ServerLevel lvl = sp.serverLevel();

        if (st.castTicks > 0) {
            st.castTicks--;
            if (st.castTicks == 0) {
                openDomain(sp, st);
                dirty = true;
            }
        }
        if (st.domainActive) {
            st.domainTicks--;
            enforceBarrier(sp, st, lvl);
            if (st.riichiTicks > 0) {
                st.riichiTicks--;
                if (st.riichiTicks == 0) {
                    resolveRiichi(sp, st);
                    dirty = true;
                }
            } else if (st.domainTicks <= 0) {
                collapse(sp, st, true);
                dirty = true;
            }
        }
        if (st.jackpotCutTicks > 0) {
            st.jackpotCutTicks--;
            sp.setHealth(sp.getMaxHealth());
            if (st.jackpotCutTicks == 0) {
                st.jackpotTicks = st.jackpotMax;
                dirty = true;
            }
        }
        if (st.jackpotTicks > 0) {
            st.jackpotTicks--;
            jackpotTick(sp, st);
            if (st.jackpotTicks == 0) {
                endJackpot(sp, st);
                dirty = true;
            }
        }
        if (st.counterTicks > 0) {
            st.counterTicks--;
            if (st.counterTicks == 0 && st.pose == 3) {
                st.pose = 0;
                dirty = true;
            }
        }
        if (st.rhythmTicks > 0) st.rhythmTicks--;
        if (st.windupTicks > 0) {
            st.windupTicks--;
            if (st.windupTicks == 0 && st.pose == 2) {
                st.pose = 0;
                dirty = true;
            }
        }
        boolean live = st.domainActive || st.jackpotTicks > 0 || st.castTicks > 0 || st.jackpotCutTicks > 0;
        if (dirty || (live && sp.tickCount % 10 == 0)) sync(sp, st);
    }

    public static void onLogout(ServerPlayer sp) {
        HakariState st = HakariState.peek(sp);
        if (st != null) collapse(sp, st, false);
        HakariState.remove(sp);
    }

    // ───────────────────────────── sync ─────────────────────────────

    public static void sync(ServerPlayer sp, HakariState st) {
        StatePacket p = new StatePacket();
        p.entityId = sp.getId();
        p.castTicks = st.castTicks;
        p.domainActive = st.domainActive;
        p.domainTicks = st.domainTicks;
        p.cx = st.domainCenter.x;
        p.cy = st.domainCenter.y;
        p.cz = st.domainCenter.z;
        p.radius = st.radius;
        p.visuals = st.visuals;
        p.fails = st.fails;
        p.riichiTicks = st.riichiTicks;
        p.jackpotCutTicks = st.jackpotCutTicks;
        p.jackpotTicks = st.jackpotTicks;
        p.jackpotMax = st.jackpotMax;
        p.jackpotNumber = st.jackpotNumber;
        p.rhythmTicks = st.rhythmTicks;
        p.counterTicks = st.counterTicks;
        p.pose = st.pose;
        p.boosted = st.boosted;
        HakariNetwork.state(sp, p);
    }

    private HakariLogic() {}
}
