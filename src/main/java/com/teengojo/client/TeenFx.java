package com.teengojo.client;

import com.jjk.mod.Sorcerer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.teengojo.TeenGojo;
import com.teengojo.TeenNet;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Teen Gojo's techniques as drawn: glowing orbs, the flicked red bolt, the small Purple, Infinity's shimmer, the void's flash. */
public final class TeenFx {
    static final ResourceLocation LAYER = new ResourceLocation("jjk", "teen_gojo");
    static final List<Eff> EFFECTS = new ArrayList<>();
    /** His own cooldowns: slot -> [game time it ends, its length]. */
    static final long[][] CD = new long[TeenGojo.MOVES.length][2];
    static long frozenUntil = -1, frozenStart = -1;

    private TeenFx() {}

    record Eff(TeenNet.Fx m, long start) {
        float age(float partial) {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? 0 : (mc.level.getGameTime() - start) + partial;
        }
    }

    // ------------------------------------------------------------------------------------------------ receiving

    public static void receive(TeenNet.Fx m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        switch (m.kind()) {
            case TeenNet.COOLDOWN -> {
                int slot = (int) m.a();
                if (slot >= 0 && slot < CD.length) {
                    CD[slot][0] = now + (long) m.b();
                    CD[slot][1] = (long) m.b();
                }
                return;
            }
            case TeenNet.FROZEN -> {
                frozenStart = now;
                frozenUntil = now + (long) m.a();
                return;
            }
            default -> EFFECTS.add(new Eff(m, now));
        }
        // the body
        Entity caster = m.entity() >= 0 ? mc.level.getEntity(m.entity()) : null;
        if (caster instanceof AbstractClientPlayer pl) {
            switch (m.kind()) {
                case TeenNet.RED_CHARGE -> anim(pl, m.a() < 0 ? "tg_blue" : "tg_red");
                case TeenNet.INFINITY -> anim(pl, "tg_guard");
                case TeenNet.PURPLE_CHARGE -> anim(pl, "tg_purple");
                case TeenNet.DOMAIN -> anim(pl, "tg_sign");
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the body

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void onSetup(FMLClientSetupEvent e) {
            e.enqueueWork(() -> {
                try {
                    PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1090, p -> new ModifierLayer<IAnimation>());
                } catch (Throwable t) {
                    TeenGojo.class.getName();
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    public static void anim(AbstractClientPlayer p, String name) {
        anim(p, "teengojo", name);
    }

    @SuppressWarnings("unchecked")
    public static void anim(AbstractClientPlayer p, String ns, String name) {
        try {
            Object o = PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER);
            if (!(o instanceof ModifierLayer<?> l)) {
                return;
            }
            KeyframeAnimation a = PlayerAnimationRegistry.getAnimation(new ResourceLocation(ns, name));
            if (a == null) {
                return;
            }
            KeyframeAnimationPlayer play = new KeyframeAnimationPlayer(a).setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                    .setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true)
                            .setShowLeftItem(false));
            ((ModifierLayer<IAnimation>) l).replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.OUTQUAD), play, true);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------------------------------------ the world

    @Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void onStage(RenderLevelStageEvent e) {
            if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || EFFECTS.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                EFFECTS.clear();
                return;
            }
            float partial = e.getPartialTick();
            Draw d = new Draw(e.getCamera(), e.getPoseStack().last().pose());
            Iterator<Eff> it = EFFECTS.iterator();
            List<Eff> live = new ArrayList<>();
            while (it.hasNext()) {
                Eff f = it.next();
                if (f.age(partial) > life(f.m)) {
                    it.remove();
                } else {
                    live.add(f);
                }
            }
            // dark cores first (ordinary blending), then the light (added)
            d.begin(false);
            for (Eff f : live) {
                dark(d, f, partial);
            }
            d.end();
            d.begin(true);
            for (Eff f : live) {
                light(d, f, partial);
            }
            d.end();
        }

        @SubscribeEvent
        public static void onGui(RenderGuiEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            GuiGraphics g = e.getGuiGraphics();
            int w = g.guiWidth(), h = g.guiHeight();
            float partial = e.getPartialTick();
            long now = mc.level.getGameTime();
            // the void: a fifth of a second of everything at once, for whoever it caught (and him)
            for (Eff f : EFFECTS) {
                if (f.m.kind() == TeenNet.DOMAIN && (f.m.entity() == mc.player.getId()
                        || mc.player.position().distanceTo(new Vec3(f.m.x(), f.m.y(), f.m.z())) <= f.m.a() + 1)) {
                    voidFlash(g, w, h, f.age(partial), f.start);
                }
            }
            if (frozenUntil > now) {
                float left = (frozenUntil - now - partial) / Math.max(1F, frozenUntil - frozenStart);
                int a = (int) (90 * Mth.clamp(left, 0, 1));
                g.fillGradient(0, 0, w, h, a << 24 | 0x1A0830, a << 24 | 0x05010C);
            }
            // Infinity, from inside: a cool shimmer at the edge of his sight while it lasts
            for (Eff f : EFFECTS) {
                if (f.m.kind() == TeenNet.INFINITY && f.m.entity() == mc.player.getId()) {
                    float age = f.age(partial), len = f.m.a();
                    float k = Mth.clamp(Math.min(age / 4F, (len - age) / 8F), 0, 1);
                    int a = (int) (60 * k * (0.75 + 0.25 * Math.sin(age * 0.4)));
                    int bw = (int) (w * 0.12);
                    g.fillGradient(0, 0, w, bw / 2, a << 24 | 0x9FE4FF, 0x009FE4FF);
                    g.fillGradient(0, h - bw / 2, w, h, 0x009FE4FF, a << 24 | 0x9FE4FF);
                    String s = String.format("INFINITY  %.1fs", Math.max(0, (len - age) / 20F));
                    g.drawString(mc.font, s, w / 2 - mc.font.width(s) / 2, h / 2 + 18, (int) (200 * k) << 24 | 0xBFEFFF, true);
                }
            }
            cooldowns(g, w, h, now, partial);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
            EFFECTS.clear();
            for (long[] c : CD) {
                c[0] = 0;
            }
            frozenUntil = -1;
        }
    }

    /** His moves and what's left of each cooldown, small, over the hotbar's right end. */
    static void cooldowns(GuiGraphics g, int w, int h, long now, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Sorcerer mine;
        try {
            mine = com.jjk.mod.client.ClientSorcerers.of(mc.player);
        } catch (Throwable t) {
            return;
        }
        if (mine == null || !mine.name().equals(TeenGojo.NAME) || mc.options.hideGui) {
            return;
        }
        int x = w / 2 + 96, y = h - 6 - TeenGojo.MOVES.length * 10;
        for (int i = 0; i < TeenGojo.MOVES.length; i++) {
            long end = CD[i][0], len = Math.max(1, CD[i][1]);
            float left = Math.max(0, end - now - partial);
            boolean ready = left <= 0;
            String label = (i + 1) + "  " + TeenGojo.MOVES[i];
            int col = ready ? 0xFFBFEFFF : 0xFF7A8A99;
            g.fill(x - 2, y - 1, x + 128, y + 9, 0x66000000);
            if (!ready) {
                int bar = (int) (130 * Mth.clamp(left / len, 0, 1));
                g.fill(x - 2, y + 8, x - 2 + bar, y + 9, 0xFF6FC8FF);
                label += String.format("  %.0fs", Math.ceil(left / 20F));
            }
            g.drawString(mc.font, label, x, y, col, false);
            y += 10;
        }
    }

    static void voidFlash(GuiGraphics g, int w, int h, float age, long seed) {
        if (age > 16) {
            return;
        }
        // 0-4: the void itself, everything at once; then it lets go
        float k = age < 4 ? 1 : Mth.clamp(1 - (age - 4) / 12F, 0, 1);
        int a = (int) (235 * k);
        g.fill(0, 0, w, h, a << 24 | 0x05030E);
        Random r = new Random(seed);
        int cx = w / 2, cy = h / 2;
        for (int i = 0; i < 90; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double l0 = (r.nextDouble() * 0.4 + age * 0.06) * w, l1 = l0 + 20 + r.nextDouble() * w * 0.3;
            int col = i % 5 == 0 ? 0xFFFFFF : i % 2 == 0 ? 0xB58CFF : 0x7FD2FF;
            int alpha = (int) (a * (0.3 + 0.7 * r.nextDouble()));
            int steps = 6;
            for (int s = 0; s < steps; s++) {
                double u = l0 + (l1 - l0) * s / steps;
                int px = cx + (int) (Math.cos(ang) * u), py = cy + (int) (Math.sin(ang) * u);
                g.fill(px, py, px + 2, py + 1, alpha << 24 | col);
            }
        }
        for (int i = 0; i < 140; i++) {
            int sx = r.nextInt(Math.max(1, w)), sy = r.nextInt(Math.max(1, h));
            g.fill(sx, sy, sx + 1, sy + 1, (int) (a * r.nextDouble()) << 24 | 0xFFFFFF);
        }
    }

    static float life(TeenNet.Fx m) {
        return switch (m.kind()) {
            case TeenNet.RED_CHARGE -> m.a() < 0 ? 12 : m.a() + 2;
            case TeenNet.RED_BOLT -> m.b() / Math.max(0.1F, m.a()) + 2;
            case TeenNet.RED_BLAST -> 14;
            case TeenNet.BLUE -> m.a() + 2;
            case TeenNet.BLUE_END -> 10;
            case TeenNet.BLINK -> 10;
            case TeenNet.INFINITY -> m.a();
            case TeenNet.RIPPLE -> 10;
            case TeenNet.PURPLE_CHARGE -> m.a() + 2;
            case TeenNet.PURPLE_SHOT -> m.b() / Math.max(0.1F, m.a()) + 2;
            case TeenNet.PURPLE_END -> 16;
            case TeenNet.DOMAIN -> 18;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------------------------------------ drawing

    static final int RED = 0xFF2A2A, RED_CORE = 0xFFD9C7, BLUE = 0x2A7BFF, BLUE_CORE = 0xD8F0FF, PURPLE = 0x9B3CFF, PURPLE_CORE = 0xF3E2FF,
            CYAN = 0x9FE4FF, WHITE = 0xFFFFFF;

    static Vec3 eye(Entity e, float partial) {
        return e.getEyePosition(partial);
    }

    public static Vec3 hand(Entity e, float partial, boolean right) {
        Vec3 look = e.getViewVector(partial);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x).scale(right ? 1 : -1);
        return eye(e, partial).add(look.scale(0.7)).add(side.scale(0.38)).add(0, -0.35, 0);
    }

    /** The parts drawn with ordinary blending: the Purple's dark heart. */
    static void dark(Draw d, Eff f, float partial) {
        TeenNet.Fx m = f.m;
        float age = f.age(partial);
        if (m.kind() == TeenNet.PURPLE_SHOT) {
            Vec3 p = along(m, age);
            float gone = Mth.clamp((age - m.b() / m.a()) / 2F, 0, 1);
            d.disc(p, 0.55 * (1 - gone), argb(0x1A0638, 0.85F * (1 - gone)), argb(0x2A0A55, 0.0F));
        }
    }

    static void light(Draw d, Eff f, float partial) {
        Minecraft mc = Minecraft.getInstance();
        TeenNet.Fx m = f.m;
        float age = f.age(partial);
        Vec3 at = new Vec3(m.x(), m.y(), m.z());
        Vec3 dir = new Vec3(m.dx(), m.dy(), m.dz());
        Entity caster = m.entity() >= 0 && mc.level != null ? mc.level.getEntity(m.entity()) : null;
        switch (m.kind()) {
            case TeenNet.RED_CHARGE -> {
                if (caster == null || m.a() < 0) {
                    return;
                }
                float k = Mth.clamp(age / Math.max(1, m.a()), 0, 1);
                Vec3 h = hand(caster, partial, true).add(caster.getViewVector(partial).scale(0.15));
                orb(d, h, 0.08 + 0.18 * k, RED, RED_CORE, 0.7F + 0.3F * k, age);
                d.ring(h, 0.9 * (1 - k) + 0.2, 0.03, argb(RED, 0.5F * k));
            }
            case TeenNet.RED_BOLT -> {
                Vec3 p = along(m, age);
                Vec3 tail = new Vec3(m.x(), m.y(), m.z()).add(dir.scale(Math.max(0, age * m.a() - 4)));
                d.beam(tail, p, 0.14, argb(RED, 0.0F), argb(RED, 0.75F));
                orb(d, p, 0.26, RED, RED_CORE, 1, age);
            }
            case TeenNet.RED_BLAST -> {
                float k = Mth.clamp(age / 12F, 0, 1);
                orb(d, at, 0.9 * (1 - k) + 0.2, RED, RED_CORE, 1 - k, age);
                d.ring(at, m.a() * (0.3 + 0.9 * k), 0.12 * (1 - k) + 0.02, argb(RED, 0.8F * (1 - k)));
                d.ring(at, m.a() * (0.15 + 0.6 * k), 0.06, argb(RED_CORE, 0.6F * (1 - k)));
                rays(d, at, 1.2 + 2.4 * k, RED, 0.7F * (1 - k), f.start);
            }
            case TeenNet.BLUE -> {
                float len = m.a();
                float in = Mth.clamp(age / 5F, 0, 1), out = Mth.clamp((len - age) / 4F, 0, 1);
                float k = Math.min(in, out);
                double r = 0.45 * k * (1 + 0.06 * Math.sin(age * 0.9));
                orb(d, at, r, BLUE, BLUE_CORE, k, age);
                // the world spiralling into it
                for (int i = 0; i < 3; i++) {
                    float u = ((age * 0.07F + i / 3F) % 1F);
                    d.ring(at, (2.6 - 2.2 * u) * k, 0.025, argb(BLUE, 0.45F * u * k));
                }
                Random rr = new Random(f.start);
                for (int i = 0; i < 14; i++) {
                    double ang = rr.nextDouble() * Math.PI * 2 + age * (0.18 + rr.nextDouble() * 0.12);
                    double rad = (1.0 + 2.5 * ((rr.nextDouble() + age * 0.03) % 1.0)) * k;
                    Vec3 sp = at.add(Math.cos(ang) * rad, (rr.nextDouble() - 0.5) * 1.6 * k, Math.sin(ang) * rad);
                    d.disc(sp, 0.05, argb(BLUE_CORE, 0.7F * k), argb(BLUE, 0));
                }
            }
            case TeenNet.BLUE_END -> {
                float k = Mth.clamp(age / 8F, 0, 1);
                orb(d, at, 0.7 * (1 - k), BLUE, BLUE_CORE, 1 - k, age);
                d.ring(at, 0.4 + 2.6 * k, 0.08 * (1 - k) + 0.02, argb(BLUE, 0.7F * (1 - k)));
            }
            case TeenNet.BLINK -> {
                float k = Mth.clamp(age / 10F, 0, 1);
                Vec3 to = at.add(dir);
                d.beam(at.add(0, 1, 0), to.add(0, 1, 0), 0.35 * (1 - k), argb(CYAN, 0.0F), argb(CYAN, 0.5F * (1 - k)));
                d.ring(at.add(0, 1, 0), 0.5 + 1.2 * k, 0.05, argb(BLUE_CORE, 0.6F * (1 - k)));
                d.ring(to.add(0, 1, 0), 1.6 - 1.2 * k, 0.05, argb(BLUE_CORE, 0.6F * (1 - k)));
            }
            case TeenNet.INFINITY -> {
                if (caster == null) {
                    return;
                }
                float len = m.a();
                float k = Mth.clamp(Math.min(age / 4F, (len - age) / 8F), 0, 1);
                Vec3 c = caster.getPosition(partial).add(0, caster.getBbHeight() * 0.55, 0);
                // not drawn for him in first person: the edge shimmer on his screen says it
                if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    double r = 1.15 + 0.1 * i + 0.04 * Math.sin(age * 0.5 + i * 2);
                    d.ring(c, r, 0.025, argb(i == 1 ? WHITE : CYAN, (0.22F - i * 0.04F) * k));
                }
                d.disc(c, 1.3, argb(CYAN, 0.0F), argb(CYAN, 0.10F * k));
            }
            case TeenNet.RIPPLE -> {
                float k = Mth.clamp(age / 10F, 0, 1);
                d.ring(at, 0.15 + 0.9 * k, 0.04, argb(CYAN, 0.75F * (1 - k)));
                d.ring(at, 0.05 + 0.5 * k, 0.03, argb(WHITE, 0.6F * (1 - k)));
            }
            case TeenNet.PURPLE_CHARGE -> {
                if (caster == null) {
                    return;
                }
                float len = m.a();
                float k = Mth.clamp(age / len, 0, 1);
                Vec3 front = eye(caster, partial).add(caster.getViewVector(partial).scale(1.3)).add(0, -0.2, 0);
                Vec3 rp = hand(caster, partial, true).lerp(front, smooth(Mth.clamp((k - 0.45F) / 0.5F, 0, 1)));
                Vec3 bp = hand(caster, partial, false).lerp(front, smooth(Mth.clamp((k - 0.45F) / 0.5F, 0, 1)));
                float grow = Mth.clamp(k / 0.35F, 0, 1);
                if (k < 0.95F) {
                    orb(d, rp, 0.18 * grow, RED, RED_CORE, grow, age);
                    orb(d, bp, 0.2 * grow, BLUE, BLUE_CORE, grow, age);
                }
                if (k > 0.6F) {
                    float pk = Mth.clamp((k - 0.6F) / 0.4F, 0, 1);
                    d.beam(rp, bp, 0.05 * pk, argb(PURPLE_CORE, 0.6F * pk), argb(PURPLE_CORE, 0.6F * pk));
                    orb(d, front, 0.4 * pk, PURPLE, PURPLE_CORE, pk, age);
                    d.ring(front, 1.6 - 1.1 * pk, 0.04, argb(PURPLE, 0.6F * pk));
                }
            }
            case TeenNet.PURPLE_SHOT -> {
                Vec3 p = along(m, age);
                float gone = Mth.clamp((age - m.b() / m.a()) / 2F, 0, 1);
                Vec3 from = new Vec3(m.x(), m.y(), m.z());
                d.beam(from.add(dir.scale(Math.max(0, age * m.a() - 9))), p, 0.55 * (1 - gone), argb(PURPLE, 0.0F), argb(PURPLE, 0.35F * (1 - gone)));
                orb(d, p, 1.0 * (1 - gone), PURPLE, PURPLE_CORE, 1 - gone, age);
                d.ring(p, 1.45, 0.06, argb(PURPLE_CORE, 0.6F * (1 - gone)));
            }
            case TeenNet.PURPLE_END -> {
                float k = Mth.clamp(age / 14F, 0, 1);
                orb(d, at, 1.2 * (1 - k), PURPLE, PURPLE_CORE, 1 - k, age);
                d.ring(at, 0.6 + 3.2 * k, 0.12 * (1 - k) + 0.02, argb(PURPLE, 0.7F * (1 - k)));
                rays(d, at, 1.0 + 3.0 * k, PURPLE, 0.6F * (1 - k), f.start);
            }
            case TeenNet.DOMAIN -> {
                float k = Mth.clamp(age / 16F, 0, 1);
                Vec3 c = at.add(0, 1, 0);
                d.ring(c, m.a() * (0.2 + 0.8 * smooth(k)), 0.2 * (1 - k) + 0.04, argb(0xB58CFF, 0.6F * (1 - k)));
                d.ring(c, m.a() * (0.1 + 0.5 * smooth(k)), 0.1, argb(CYAN, 0.5F * (1 - k)));
            }
            default -> {
            }
        }
    }

    static Vec3 along(TeenNet.Fx m, float age) {
        Vec3 from = new Vec3(m.x(), m.y(), m.z());
        Vec3 dir = new Vec3(m.dx(), m.dy(), m.dz());
        return from.add(dir.scale(Math.min(m.b(), age * m.a())));
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** A glowing sphere: a soft halo, a body of colour, a white-hot middle. */
    public static void orb(Draw d, Vec3 at, double r, int col, int core, float k, float age) {
        if (r <= 0.005 || k <= 0.01F) {
            return;
        }
        double pulse = 1 + 0.05 * Math.sin(age * 1.3);
        d.disc(at, r * 3.2 * pulse, argb(col, 0.28F * k), argb(col, 0));
        d.disc(at, r * 1.6, argb(col, 0.75F * k), argb(col, 0.05F * k));
        d.disc(at, r * 1.05, argb(core, 0.95F * k), argb(col, 0.5F * k));
        d.disc(at, r * 0.55, argb(WHITE, k), argb(core, 0.6F * k));
        d.ring(at, r * 1.25 * pulse, r * 0.08, argb(core, 0.5F * k));
    }

    public static void rays(Draw d, Vec3 at, double len, int col, float k, long seed) {
        Random r = new Random(seed);
        for (int i = 0; i < 10; i++) {
            Vec3 dir = new Vec3(r.nextDouble() - 0.5, r.nextDouble() - 0.3, r.nextDouble() - 0.5).normalize();
            d.beam(at, at.add(dir.scale(len * (0.6 + 0.6 * r.nextDouble()))), 0.06, argb(WHITE, k), argb(col, 0));
        }
    }

    public static int argb(int rgb, float a) {
        return (int) (Mth.clamp(a, 0, 1) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** Camera-facing shapes in one buffer: discs, rings, beams. */
    public static final class Draw {
        final Vec3 cam;
        final Vector3f left, up;
        final Matrix4f pose;
        BufferBuilder b;
        boolean add;

        public Draw(Camera c, Matrix4f pose) {
            this.cam = c.getPosition();
            this.left = c.getLeftVector();
            this.up = c.getUpVector();
            this.pose = pose;
        }

        public void begin(boolean additive) {
            add = additive;
            b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        }

        public void end() {
            RenderSystem.enableBlend();
            if (add) {
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            } else {
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(b.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }

        void v(Vec3 p, int argb) {
            b.vertex(pose, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                    .color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
        }

        Vec3 off(double a, double r) {
            return new Vec3((left.x() * Math.cos(a) + up.x() * Math.sin(a)) * r, (left.y() * Math.cos(a) + up.y() * Math.sin(a)) * r,
                    (left.z() * Math.cos(a) + up.z() * Math.sin(a)) * r);
        }

        public void disc(Vec3 c, double r, int centre, int edge) {
            int n = 28;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                v(c, centre);
                v(c.add(off(a0, r)), edge);
                v(c.add(off(a1, r)), edge);
            }
        }

        public void ring(Vec3 c, double r, double w, int col) {
            if (r <= 0 || (col >>> 24) == 0) {
                return;
            }
            int n = 48;
            int clear = col & 0xFFFFFF;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                Vec3 i0 = c.add(off(a0, r - w)), i1 = c.add(off(a1, r - w)), m0 = c.add(off(a0, r)), m1 = c.add(off(a1, r));
                Vec3 o0 = c.add(off(a0, r + w)), o1 = c.add(off(a1, r + w));
                // inner fade -> line -> outer fade
                v(i0, clear); v(m0, col); v(m1, col);
                v(i0, clear); v(m1, col); v(i1, clear);
                v(m0, col); v(o0, clear); v(o1, clear);
                v(m0, col); v(o1, clear); v(m1, col);
            }
        }

        public void beam(Vec3 a, Vec3 z, double w, int ca, int cz) {
            Vec3 along = z.subtract(a);
            if (along.lengthSqr() < 1e-6 || w <= 0) {
                return;
            }
            Vec3 view = a.lerp(z, 0.5).subtract(cam);
            Vec3 side = along.cross(view);
            if (side.lengthSqr() < 1e-9) {
                return;
            }
            side = side.normalize().scale(w);
            v(a.add(side), ca & 0xFFFFFF); v(a, ca); v(z, cz);
            v(a.add(side), ca & 0xFFFFFF); v(z, cz); v(z.add(side), cz & 0xFFFFFF);
            v(a, ca); v(a.subtract(side), ca & 0xFFFFFF); v(z.subtract(side), cz & 0xFFFFFF);
            v(a, ca); v(z.subtract(side), cz & 0xFFFFFF); v(z, cz);
        }
    }
}
