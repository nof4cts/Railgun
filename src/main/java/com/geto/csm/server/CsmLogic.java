package com.geto.csm.server;

import com.geto.csm.CurseKind;
import com.geto.csm.ModRegistry;
import com.geto.csm.entity.CurseEntity;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.CutType;
import com.geto.csm.network.FxType;
import com.geto.csm.network.StatePacket;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Summoning, selecting, swallowing and Maximum: Uzumaki. */
public final class CsmLogic {
    public static final int MAX_SLOTS = 3;
    public static final int UZUMAKI_LOCK = 120;
    public static final int UZUMAKI_FIRE = 86;

    // ─────────── selection ───────────

    public static void select(ServerPlayer sp, int kind) {
        CsmState st = CsmState.get(sp);
        st.selected = Math.floorMod(kind, CurseKind.values().length);
        sync(sp, st);
    }

    // ─────────── summoning ───────────

    public static void summon(ServerPlayer sp) {
        CsmState st = CsmState.get(sp);
        if (st.lockTicks > 0) return;
        CurseKind kind = CurseKind.byId(st.selected);
        long now = sp.level().getGameTime();
        if (now < st.readyAt[kind.ordinal()]) {
            long left = (st.readyAt[kind.ordinal()] - now + 19) / 20;
            sp.displayClientMessage(Component.literal("§7" + kind.title + " is recovering — " + left + "s"), true);
            return;
        }
        cleanSlots(sp, st);
        while (st.slots.size() >= MAX_SLOTS) dismissSlot(sp, st, 0);

        ServerLevel lvl = sp.serverLevel();
        Vec3 look = Combat.flatLook(sp);
        float yaw = sp.getYRot();
        List<Integer> ids = new ArrayList<>();
        int count = kind == CurseKind.CENTIPEDE ? 5 : 1;
        for (int i = 0; i < count; i++) {
            Vec3 pos = spawnPos(sp, kind, look, i);
            Entity raw = ModRegistry.typeOf(kind).create(lvl);
            if (!(raw instanceof CurseEntity e)) continue;
            e.moveTo(pos.x, pos.y, pos.z, yaw, 0);
            e.yHeadRot = yaw;
            e.yBodyRot = yaw;
            e.bind(sp);
            lvl.addFreshEntity(e);
            ids.add(e.getId());
            CsmNetwork.fx(sp, FxType.RIFT, sp, e, pos, kind.ordinal(), i);
        }
        st.slots.add(ids);
        st.readyAt[kind.ordinal()] = now + kind.cooldown;
        Combat.sound(sp, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7f, kind.special ? 0.5f : 0.9f);
        Combat.sound(sp, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.4f);
        if (kind.special || kind == CurseKind.WORM) {
            CsmNetwork.cutscene(sp, CutType.SUMMON, sp.getId(), kind.ordinal());
        }
        sync(sp, st);
    }

    private static Vec3 spawnPos(ServerPlayer sp, CurseKind kind, Vec3 look, int i) {
        return switch (kind) {
            case WORM -> {
                LivingEntity t = Combat.nearest(sp, 24, 0.7);
                yield t != null ? t.position() : sp.position().add(look.scale(6));
            }
            case WYRM -> sp.position().add(look.scale(4)).add(0, 2.5, 0);
            case RAY -> sp.position().add(look.scale(2.5)).add(0, 1, 0);
            case TAMAMO -> sp.position().add(look.scale(4)).add(0, 3, 0);
            case CENTIPEDE -> {
                double a = i * Math.PI * 2 / 5;
                yield sp.position().add(look.scale(2.5)).add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
            }
            default -> sp.position().add(look.scale(3));
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
        List<Integer> slot = st.slots.remove(index);
        for (int id : slot) {
            Entity e = sp.serverLevel().getEntity(id);
            if (e instanceof CurseEntity c) c.vanish();
        }
    }

    public static void dismissAll(ServerPlayer sp) {
        CsmState st = CsmState.peek(sp);
        if (st == null) return;
        while (!st.slots.isEmpty()) dismissSlot(sp, st, 0);
    }

    public static List<CurseEntity> active(ServerPlayer sp, CsmState st) {
        cleanSlots(sp, st);
        List<CurseEntity> out = new ArrayList<>();
        for (List<Integer> slot : st.slots) {
            for (int id : slot) {
                if (sp.serverLevel().getEntity(id) instanceof CurseEntity c) out.add(c);
            }
        }
        return out;
    }

    // ─────────── absorbing ───────────

    public static void dropOrb(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel lvl)) return;
        Vec3 p = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        ItemEntity orb = new ItemEntity(lvl, p.x, p.y, p.z, new ItemStack(ModRegistry.CURSE_ORB.get()));
        orb.setDeltaMovement(0, 0.25, 0);
        orb.setPickUpDelay(10);
        lvl.addFreshEntity(orb);
        CsmNetwork.fx(lvl, FxType.ORB_FORM, victim.getId(), -1, p, 0, 0);
    }

    public static void absorb(ServerPlayer sp) {
        CsmState st = CsmState.get(sp);
        st.absorbed++;
        CsmNetwork.fx(sp, FxType.ABSORB, sp, null, sp.getEyePosition(), st.absorbed, 0);
        Combat.sound(sp, SoundEvents.PLAYER_BURP, 0.8f, 0.6f);
        sp.displayClientMessage(Component.literal("§5Curse absorbed. §7It tastes like something scraped off a floor.  §fStored: " + st.absorbed), true);
        sync(sp, st);
    }

    // ─────────── Maximum: Uzumaki ───────────

    public static void uzumaki(ServerPlayer sp, Item item) {
        CsmState st = CsmState.get(sp);
        if (st.lockTicks > 0 || sp.getCooldowns().isOnCooldown(item)) return;
        List<CurseEntity> curses = active(sp, st);
        int n = curses.size() + st.absorbed;
        if (n == 0) {
            sp.displayClientMessage(Component.literal("§7You have no curses to combine. Summon some, or swallow curse orbs."), true);
            return;
        }
        Set<String> extracted = new LinkedHashSet<>();
        for (CurseEntity c : curses) if (c.kind().special) extracted.add(c.kind().title);

        st.lockTicks = UZUMAKI_LOCK;
        Combat.stun(sp, UZUMAKI_LOCK - 10);
        Vec3 core = sp.getEyePosition().add(sp.getLookAngle().scale(3.5));
        CsmNetwork.cutscene(sp, CutType.UZUMAKI, sp.getId(), n, extracted.isEmpty() ? 0 : 1, curses.size());
        CsmNetwork.fx(sp, FxType.UZUMAKI_CORE, sp, null, core, n, 0);
        for (CurseEntity c : curses) {
            c.vowTicks = 40;
            CsmNetwork.fx(sp, FxType.UZUMAKI_GATHER, c, sp, core, c.kind().ordinal(), 0);
            Scheduler.after(24, c::discard);
        }
        st.slots.clear();
        st.absorbed = 0;
        Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 2f, 0.4f);
        Combat.sound(sp, SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.6f);
        sp.getCooldowns().addCooldown(item, 20 * 30);

        float damage = Math.min(100f, 20f + 5f * n);
        Scheduler.after(UZUMAKI_FIRE, () -> {
            if (!sp.isAlive()) return;
            Vec3 dir = sp.getLookAngle();
            Vec3 origin = sp.getEyePosition().add(dir.scale(3.5));
            float yaw = sp.getYRot(), pitch = sp.getXRot();
            CsmNetwork.fx(sp, FxType.UZUMAKI_BEAM, sp, null, origin, yaw, pitch);
            Combat.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 3f, 0.5f);
            Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 3f, 0.5f);
            double length = 52;
            AABB box = new AABB(origin, origin.add(dir.scale(length))).inflate(5);
            for (LivingEntity e : sp.serverLevel().getEntitiesOfClass(LivingEntity.class, box, x -> x != sp && x.isAlive() && !x.isSpectator())) {
                if (e instanceof CurseEntity c && c.ownerId() == sp.getId()) continue;
                Vec3 to = e.getBoundingBox().getCenter().subtract(origin);
                double along = to.dot(dir);
                if (along < -1 || along > length) continue;
                double off = to.subtract(dir.scale(along)).length();
                if (off > 3.2 + along * 0.06) continue;
                Combat.damage(sp, e, damage, true);
                CurseEntity.launch(e, dir.scale(2.6).add(0, 0.6, 0));
            }
            if (!extracted.isEmpty()) {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 90, 1));
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 90, 0));
                sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 90, 0));
                sp.displayClientMessage(Component.literal("§dCursed technique extracted from: §f" + String.join(", ", extracted)), false);
            }
        });
        sync(sp, st);
    }

    // ─────────── tick + sync ───────────

    public static void tick(ServerPlayer sp) {
        CsmState st = CsmState.peek(sp);
        if (st == null) return;
        if (st.lockTicks > 0) st.lockTicks--;
        if (sp.tickCount % 20 == 0) {
            cleanSlots(sp, st);
            sync(sp, st);
        }
    }

    public static void sync(ServerPlayer sp, CsmState st) {
        long now = sp.level().getGameTime();
        int[] cds = new int[CurseKind.values().length];
        for (int i = 0; i < cds.length; i++) cds[i] = (int) Mth.clamp(st.readyAt[i] - now, 0, Integer.MAX_VALUE);
        CsmNetwork.toPlayer(sp, new StatePacket(st.selected, st.absorbed, st.slots.size(), cds));
    }

    private CsmLogic() {}
}
