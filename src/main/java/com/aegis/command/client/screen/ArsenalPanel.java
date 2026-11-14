package com.aegis.command.client.screen;

import com.aegis.command.client.ClientData;
import com.aegis.command.client.Sfx;
import com.aegis.command.client.render.WeaponAnimator;
import com.aegis.command.client.render.WeaponRenderer;
import com.aegis.command.client.ui.Ease;
import com.aegis.command.client.ui.Theme;
import com.aegis.command.client.ui.Ui;
import com.aegis.command.item.WeaponType;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import com.aegis.command.registry.ModItems;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ArsenalPanel extends Panel {
    private enum Mode { GRID, INSPECT }

    private static final float VX0 = 8, VY0 = 42, VX1 = 330, VY1 = 292;
    private static final float RX0 = 338, RX1 = 512;
    private static final String[] VIEWS = {"ORBIT", "MUZZLE", "CORE", "GRIP"};
    private static final RandomSource RNG = RandomSource.create();

    private Mode mode = Mode.GRID;
    private WeaponType sel = WeaponType.PHANTOM_9;
    private long gridMs, inspectMs;
    private int revealed;
    /** Armory page: 0 = SERIES I (first three), 1 = SERIES II (next three). */
    private int page;
    private static final String[] PAGE_NAMES = {"SERIES I // STANDARD ISSUE", "SERIES II // HEAVY ORDNANCE"};

    // inspection camera
    private float yaw = -60, pitch = 14, zoom = 1, panX, panY, panZ;
    private float tYaw = -60, tPitch = 14, tZoom = 1, tPanX, tPanY, tPanZ;
    private int view;
    private long lastInput;
    private boolean dragging;

    // weapon switching
    private long switchMs = -1;
    private int switchDir;
    private WeaponType switchTo;
    private boolean swapped;

    // preview choreography
    private record Cue(long due, Runnable run) {}
    private final List<Cue> cues = new ArrayList<>();
    private final List<float[]> motes = new ArrayList<>();
    private final List<float[]> shots = new ArrayList<>();
    private float muzzleSx, muzzleSy, muzzleDx = 1, muzzleDy;
    private float coreSx, coreSy;

    public ArsenalPanel(LaptopScreen s) {
        super(s);
    }

    @Override
    public void show() {
        super.show();
        if (mode == Mode.GRID) {
            gridMs = System.currentTimeMillis();
            revealed = 0;
            Sfx.scan();
        }
    }

    private static float centerZ(WeaponType t) {
        return switch (t) {
            case PHANTOM_9 -> 4f;
            case TITAN_X -> 3f;
            case SPECTRE_7 -> 2.5f;
            case NOVA_RAIL -> 3.0f;
            case HELIX_4 -> 3.6f;
            case OBLIVION -> 3.7f;
        };
    }

    private static float[] grip(WeaponType t) {
        return switch (t) {
            case PHANTOM_9 -> new float[]{8, 5.5f, 11};
            case TITAN_X -> new float[]{8, 4.5f, 9};
            case SPECTRE_7 -> new float[]{8, 5.0f, 7};
            case NOVA_RAIL -> new float[]{8, 5.5f, 10};
            case HELIX_4 -> new float[]{8, 5.0f, 9.5f};
            case OBLIVION -> new float[]{8, 4.5f, 11};
        };
    }

    private boolean owned(WeaponType t) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getInventory().contains(new net.minecraft.world.item.ItemStack(ModItems.weapon(t)));
    }

    private int energyRgb(WeaponType t) {
        return WeaponType.ENERGY_COLORS[Math.floorMod(ClientData.energy(t), WeaponType.ENERGY_COLORS.length)];
    }

    // ------------------------------------------------------------------ 3D

    /** Render a weapon into the GUI and remember where its muzzle/core land on screen. */
    private void renderWeapon(WeaponType t, float cx, float cy, float ppb, float yawDeg, float pitchDeg,
                              float px, float py, float pz, WeaponAnimator.Pose pose, boolean track) {
        var g = Ui.g;
        g.flush();
        PoseStack ps = g.pose();
        Matrix4f canvasInv = new Matrix4f(ps.last().pose()).invert();
        ps.pushPose();
        ps.translate(cx, cy, 250);
        ps.scale(ppb, -ppb, ppb);
        ps.mulPose(Axis.XP.rotationDegrees(pitchDeg));
        ps.mulPose(Axis.YP.rotationDegrees(yawDeg));
        ps.translate(-0.5 - px, -0.5 - py, -0.5 - pz);
        if (track) {
            // project muzzle and core into canvas space for callouts and preview shots
            Matrix4f m = ps.last().pose();
            Vector4f a = canvasInv.transform(m.transform(new Vector4f(t.muzzle[0] / 16f, t.muzzle[1] / 16f, t.muzzle[2] / 16f, 1)));
            Vector4f b = canvasInv.transform(m.transform(new Vector4f(t.muzzle[0] / 16f, t.muzzle[1] / 16f, t.muzzle[2] / 16f + 0.5f, 1)));
            Vector4f c = canvasInv.transform(m.transform(new Vector4f(t.core[0] / 16f, t.core[1] / 16f, t.core[2] / 16f, 1)));
            muzzleSx = a.x;
            muzzleSy = a.y;
            float dx = a.x - b.x, dy = a.y - b.y, l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l > 0.01f) { muzzleDx = dx / l; muzzleDy = dy / l; }
            coreSx = c.x;
            coreSy = c.y;
        }
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource bs = g.bufferSource();
        WeaponRenderer.get().render(t, ClientData.finish(t), ClientData.energy(t), pose, ps, bs, WeaponRenderer.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        bs.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(float mx, float my, float dt) {
        long now = System.currentTimeMillis();
        Iterator<Cue> it = cues.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) { Cue c = it.next(); if (c.due <= now) { due.add(c.run); it.remove(); } }
        due.forEach(Runnable::run);
        if (mode == Mode.GRID) renderGrid(mx, my);
        else renderInspect(mx, my, dt);
    }

    private void renderGrid(float mx, float my) {
        long now = System.currentTimeMillis();
        float t = (now - gridMs) / 1000f;
        float scan = Math.min(1, t / 0.7f);
        Ui.text("ARMORY", 10, 39, Theme.a(Theme.TECH, 1f), 0.6f);
        Ui.text("//  " + (scan < 1 ? "SCANNING SIGNATURES..." : WeaponType.values().length + " SIGNATURES RESOLVED  //  " + PAGE_NAMES[page]),
                10 + Ui.width("ARMORY") * 0.6f + 4, 39, Theme.a(Theme.MUTED, 1f), 0.55f);
        // page selector
        for (int pg = 0; pg < 2; pg++) {
            final int target = pg;
            float bx0 = 440 + pg * 38, bx1 = bx0 + 34;
            s.button("page" + pg, bx0, 35, bx1, 45, pg == 0 ? "I" : "II", page == pg ? Theme.TECH : Theme.MUTED, true, page == pg, () -> {
                if (page == target) return;
                page = target;
                gridMs = System.currentTimeMillis();
                revealed = 0;
                Sfx.scan();
            });
        }

        for (int i = 0; i < 3; i++) {
            WeaponType w = WeaponType.byIndex(page * 3 + i);
            float x0 = 10 + i * 170, x1 = x0 + 162, y0 = 48, y1 = 292;
            float r = Ease.outBack((t - 0.7f - i * 0.22f) / 0.5f);
            if (r > 0 && revealed <= i) {
                revealed = i + 1;
                Sfx.ui(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f + i * 0.25f, 0.25f);
                Sfx.ui(SoundEvents.BEACON_POWER_SELECT, 1.8f, 0.2f);
            }
            int ec = energyRgb(w);
            if (r <= 0) {
                Ui.brackets(x0, y0, x1, y1, 6, 0.6f, Theme.a(Theme.TECH, 0.15f));
                continue;
            }
            float ra = Math.min(1, r);
            boolean hov = s.hovered(x0, y0, x1, y1);
            float lift = hov ? -2 : 0;
            float cxs = (x0 + x1) / 2f;
            float hw = (x1 - x0) / 2f * Math.min(1, r);
            Ui.alpha *= ra;
            Ui.rect(cxs - hw, y0 + lift, cxs + hw, y1 + lift, Theme.a(0x08101A, 0.92f));
            Ui.gradV(cxs - hw, y0 + lift, cxs + hw, y0 + 90 + lift, Theme.a(ec, 0.12f), Theme.a(ec, 0f));
            Ui.frame(cxs - hw, y0 + lift, cxs + hw, y1 + lift, 0.5f, Theme.a(Theme.TECH, hov ? 0.7f : 0.3f));
            Ui.brackets(cxs - hw - 1, y0 - 1 + lift, cxs + hw + 1, y1 + 1 + lift, 7, 1, Theme.a(hov ? ec : Theme.TECH, 0.95f));

            Ui.text(String.format("SLOT %02d", page * 3 + i + 1), x0 + 6, y0 + 6 + lift, Theme.a(Theme.MUTED, 1f), 0.5f);
            float chipW = Ui.width(w.rarity) * 0.5f + 8;
            Ui.rect(x1 - 6 - chipW, y0 + 4 + lift, x1 - 6, y0 + 12 + lift, Theme.a(w.rarityColor, 0.2f));
            Ui.frame(x1 - 6 - chipW, y0 + 4 + lift, x1 - 6, y0 + 12 + lift, 0.5f, Theme.a(w.rarityColor, 0.9f));
            Ui.text(w.rarity, x1 - 2 - chipW, y0 + 6 + lift, Theme.a(w.rarityColor, 1f), 0.5f);

            // pedestal glow + 3D model
            float py = y0 + 88 + lift;
            Ui.ellipseGlow(cxs, py + 26, 60, 9, Theme.a(ec, 0.35f), 32);
            Ui.ellipse(cxs, py + 26, 52, 7, 0.5f, Theme.a(ec, 0.6f), 40);
            float spin = (now % 100000) / 1000f * 35 + i * 120;
            Ui.alpha /= Math.max(ra, 0.001f);
            renderWeapon(w, cxs, py, 58 * Math.min(1, r) * (hov ? 1.06f : 1), spin, 16, 0, 0, (centerZ(w) - 8) / 16f,
                    WeaponAnimator.STATIC.pose(w, false), false);
            Ui.alpha *= ra;

            boolean unlocked = ClientData.unlocked(w);
            boolean eq = owned(w);
            Ui.textGlow(w.displayName, x0 + 8, y0 + 132 + lift, Theme.WHITE, 1.3f, hov ? 0.6f : 0);
            Ui.text(w.classification, x0 + 8, y0 + 146 + lift, Theme.a(Theme.TECH, 1f), 0.55f);
            String status = !unlocked ? "LOCKED  //  " + w.unlockMissions + " MISSIONS REQUIRED" : eq ? "EQUIPPED" : "READY FOR ISSUE";
            int stc = !unlocked ? Theme.RED : eq ? 0x3CFF8E : Theme.AMBER;
            Ui.disc(x0 + 10, y0 + 159 + lift, 2, Theme.a(stc, 1f), 10);
            Ui.text(status, x0 + 15, y0 + 157 + lift, Theme.a(stc, 1f), 0.5f);
            for (int st = 0; st < 5; st++) {
                float sy = y0 + 170 + st * 10 + lift;
                Ui.text(WeaponType.STAT_NAMES[st], x0 + 8, sy, Theme.a(Theme.MUTED, 1f), 0.45f);
                float bx0 = x0 + 60, bx1 = x1 - 10;
                for (int seg = 0; seg < 10; seg++) {
                    float sx0 = bx0 + seg * (bx1 - bx0) / 10f, sx1 = sx0 + (bx1 - bx0) / 10f - 1;
                    boolean on = seg < w.stats[st] * Ease.outCubic(t - 1.0f - i * 0.2f);
                    Ui.rect(sx0, sy + 1, sx1, sy + 5, Theme.a(on ? ec : Theme.TECH_DEEP, on ? 0.95f : 0.6f));
                }
            }
            if (!unlocked) {
                float lx = cxs, ly = py - 40;
                Ui.rect(lx - 5, ly, lx + 5, ly + 7, Theme.a(Theme.RED, 0.9f));
                Ui.arc(lx, ly, 3.5f, 1.2f, (float) Math.PI, (float) Math.PI * 2, Theme.a(Theme.RED, 0.9f), 10);
            }
            Ui.textC(hov ? "> INSPECT <" : "INSPECT", cxs, y1 - 14 + lift, Theme.a(hov ? ec : Theme.TECH, 1f), 0.6f);
            Ui.alpha /= Math.max(ra, 0.001f);
            final WeaponType target = w;
            if (r > 0.6f) s.hit("slot" + (page * 3 + i), x0, y0, x1, y1, true, () -> enterInspect(target));
        }

        if (scan < 1) {
            float sy = 38 + scan * 258;
            Ui.rect(4, sy - 0.75f, 516, sy + 0.75f, Theme.a(Theme.TECH, 0.95f));
            Ui.gradV(4, sy - 30, 516, sy, Theme.a(Theme.TECH, 0f), Theme.a(Theme.TECH, 0.2f));
        }
    }

    private void enterInspect(WeaponType t) {
        mode = Mode.INSPECT;
        sel = t;
        inspectMs = System.currentTimeMillis();
        view = 0;
        setView(0);
        yaw = tYaw + 200;
        zoom = 2.6f;
        lastInput = 0;
        WeaponAnimator.PREVIEW.deploy();
        Sfx.raw(SoundEvents.BEACON_ACTIVATE, 1.5f, 0.35f);
        Sfx.later(3, () -> Sfx.raw(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.3f, 0.6f));
        Sfx.later(8, () -> Sfx.ui(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.3f));
    }

    private void setView(int v) {
        view = v;
        float baseZ = (centerZ(sel) - 8) / 16f;
        switch (v) {
            case 1 -> { tYaw = 145; tPitch = 8; tZoom = 2.4f; pan(sel.muzzle); }
            case 2 -> { tYaw = -90; tPitch = 4; tZoom = 2.2f; pan(sel.core); }
            case 3 -> { tYaw = -125; tPitch = -18; tZoom = 1.9f; pan(grip(sel)); }
            default -> { tYaw = -60; tPitch = 14; tZoom = 1f; tPanX = 0; tPanY = 0; tPanZ = baseZ; }
        }
        lastInput = System.currentTimeMillis();
    }

    private void pan(float[] p) {
        tPanX = (p[0] - 8) / 16f;
        tPanY = (p[1] - 8) / 16f;
        tPanZ = (p[2] - 8) / 16f;
    }

    private void switchWeapon(int dir) {
        if (switchMs >= 0) return;
        switchMs = System.currentTimeMillis();
        switchDir = dir;
        switchTo = WeaponType.byIndex(sel.ordinal() + dir);
        swapped = false;
        Sfx.raw(SoundEvents.ARMOR_EQUIP_IRON, 1.6f, 0.5f);
    }

    private void renderInspect(float mx, float my, float dt) {
        long now = System.currentTimeMillis();
        float t = (now - inspectMs) / 1000f;
        int ec = energyRgb(sel);

        // camera easing
        if (view == 0 && now - lastInput > 2500 && !dragging) tYaw += dt * 16;
        yaw = Ease.approach(yaw, tYaw, 6f, dt);
        pitch = Ease.approach(pitch, tPitch, 6f, dt);
        zoom = Ease.approach(zoom, tZoom, 5f, dt);
        panX = Ease.approach(panX, tPanX, 5f, dt);
        panY = Ease.approach(panY, tPanY, 5f, dt);
        panZ = Ease.approach(panZ, tPanZ, 5f, dt);

        // weapon switching slide
        float slide = 0, fade = 1;
        if (switchMs >= 0) {
            float p = (now - switchMs) / 460f;
            if (p >= 0.5f && !swapped) {
                swapped = true;
                sel = switchTo;
                setView(view);
                WeaponAnimator.PREVIEW.deploy();
                Sfx.raw(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.4f, 0.5f);
            }
            if (p < 0.5f) { slide = -switchDir * Ease.inCubic(p * 2) * 200; fade = 1 - p * 2; }
            else if (p < 1) { slide = switchDir * (1 - Ease.outCubic((p - 0.5f) * 2)) * 200; fade = (p - 0.5f) * 2; }
            else switchMs = -1;
        }

        // ---- viewport stage
        float cx = (VX0 + VX1) / 2f, cy = 150;
        Ui.rect(VX0, VY0, VX1, VY1, Theme.a(0x05080C, 1f));
        Ui.glow(cx, cy, 190, Theme.a(ec, 0.14f), 48);
        Ui.glow(cx, VY0, 140, Theme.a(Theme.TECH, 0.08f), 32);
        // perspective floor grid
        float horizon = 205;
        Ui.scissor(VX0, VY0, VX1, VY1);
        for (int i = -12; i <= 12; i++) {
            Ui.line(cx + i * 10, horizon, cx + i * 70, VY1 + 40, 0.5f, Theme.a(Theme.TECH, 0.09f));
        }
        for (int k = 0; k < 9; k++) {
            float yy = horizon + k * k * 1.3f + k * 2;
            Ui.rect(VX0, yy, VX1, yy + 0.5f, Theme.a(Theme.TECH, 0.08f * (1 - k / 10f)));
        }
        // spotlight cone + turntable
        Ui.tri(cx - 14, VY0, cx - 120, 214, cx + 120, 214, Theme.a(Theme.WHITE, 0.035f), Theme.a(Theme.WHITE, 0.0f), Theme.a(Theme.WHITE, 0.0f));
        Ui.tri(cx - 14, VY0, cx + 14, VY0, cx + 120, 214, Theme.a(Theme.WHITE, 0.035f), Theme.a(Theme.WHITE, 0.035f), Theme.a(Theme.WHITE, 0.0f));
        Ui.ellipseGlow(cx, 214, 125, 20, Theme.a(ec, 0.3f), 48);
        Ui.ellipse(cx, 214, 112, 16, 0.8f, Theme.a(ec, 0.7f), 64);
        float rs = (now % 100000) / 900f;
        for (int q = 0; q < 6; q++) {
            double a = rs + q * Math.PI / 3;
            Ui.disc(cx + (float) Math.cos(a) * 112, 214 + (float) Math.sin(a) * 16, 1.4f, Theme.a(ec, 1f), 8);
        }

        // motes
        if (RNG.nextFloat() < 0.6f) motes.add(new float[]{VX0 + RNG.nextFloat() * (VX1 - VX0), 214 + RNG.nextFloat() * 20, (RNG.nextFloat() - 0.5f) * 6, -6 - RNG.nextFloat() * 14, 0});
        Iterator<float[]> mi = motes.iterator();
        while (mi.hasNext()) {
            float[] m = mi.next();
            m[0] += m[2] * dt; m[1] += m[3] * dt; m[4] += dt;
            if (m[4] > 4) { mi.remove(); continue; }
            float a = Math.min(1, m[4]) * (1 - m[4] / 4f);
            Ui.rect(m[0], m[1], m[0] + 1, m[1] + 1, Theme.a(ec, 0.7f * a));
        }

        // showcase intro: spin-in, zoom-out, flash
        float intro = Ease.outCubic(t / 1.3f);
        float showYaw = yaw;
        float showZoom = zoom;
        Ui.alpha *= fade;
        renderWeapon(sel, cx + slide, cy, 105 * showZoom, showYaw, pitch, panX, panY, panZ,
                WeaponAnimator.PREVIEW.pose(sel, true), true);
        Ui.alpha /= Math.max(fade, 0.001f);

        // energy shots fired by the preview
        Iterator<float[]> si = shots.iterator();
        while (si.hasNext()) {
            float[] sh = si.next();
            float age = (now - (long) sh[4]) / 1000f;
            float life = sh[5];
            if (age > life) { si.remove(); continue; }
            float k = age / life;
            float len = sh[6];
            float hx = sh[0] + sh[2] * age * 900, hy = sh[1] + sh[3] * age * 900;
            Ui.line(hx - sh[2] * len, hy - sh[3] * len, hx, hy, sh[7] * (1 - k * 0.5f), Theme.a(ec, 1 - k));
            Ui.line(hx - sh[2] * len * 0.5f, hy - sh[3] * len * 0.5f, hx, hy, sh[7] * 0.4f, Theme.a(Theme.WHITE, 1 - k));
        }

        // light sweep across the metal
        float sweepPhase = (t % 6f) / 1.1f;
        if (t < 1.3f || sweepPhase < 1) {
            float p = t < 1.3f ? t / 1.3f : sweepPhase;
            float sx = VX0 - 60 + p * (VX1 - VX0 + 120);
            Ui.tri(sx, VY0, sx + 26, VY0, sx - 40, VY1, Theme.a(Theme.WHITE, 0.07f), Theme.a(Theme.WHITE, 0.0f), Theme.a(Theme.WHITE, 0.0f));
            Ui.tri(sx + 26, VY0, sx - 14, VY1, sx - 40, VY1, Theme.a(Theme.WHITE, 0.0f), Theme.a(Theme.WHITE, 0.05f), Theme.a(Theme.WHITE, 0.0f));
        }
        if (t < 0.3f) Ui.rect(VX0, VY0, VX1, VY1, Theme.a(Theme.WHITE, 0.5f * (1 - t / 0.3f)));

        // callouts
        if (switchMs < 0 && t > 1.2f && fade > 0.99f) {
            callout(coreSx, coreSy, coreSx + (coreSx < cx ? -40 : 40), coreSy - 34, "ENERGY CORE", ec);
            callout(muzzleSx, muzzleSy, muzzleSx + (muzzleSx < cx ? -30 : 30), muzzleSy + 30, "EMITTER", ec);
        }
        Ui.endScissor();

        // viewport HUD
        Ui.brackets(VX0, VY0, VX1, VY1, 10, 1, Theme.a(Theme.TECH, 0.9f));
        Ui.frame(VX0, VY0, VX1, VY1, 0.5f, Theme.a(Theme.TECH, 0.3f));
        Ui.text("INSPECTION BAY  //  " + VIEWS[view], VX0 + 6, VY0 + 5, Theme.a(Theme.TECH, 1f), 0.55f);
        Ui.textR(String.format("YAW %03d  PITCH %+03d  ZOOM %.1fx", (int) Math.floorMod((int) yaw, 360), (int) pitch, zoom), VX1 - 6, VY0 + 5, Theme.a(Theme.MUTED, 1f), 0.5f);
        Ui.textC("DRAG TO ROTATE  //  SCROLL TO ZOOM", cx, VY1 - 10, Theme.a(Theme.MUTED, 0.8f), 0.45f);
        s.button("prevW", VX0 + 6, VY1 - 22, VX0 + 26, VY1 - 6, "<", Theme.TECH, switchMs < 0, false, () -> switchWeapon(-1));
        s.button("nextW", VX1 - 26, VY1 - 22, VX1 - 6, VY1 - 6, ">", Theme.TECH, switchMs < 0, false, () -> switchWeapon(1));
        for (int i = 0; i < 3; i++) {
            float dx = cx - 12 + i * 12;
            Ui.disc(dx, VY1 - 16, i == sel.ordinal() ? 2.4f : 1.5f, Theme.a(i == sel.ordinal() ? ec : Theme.TECH, 1f), 10);
        }

        renderInfo(t, ec);
    }

    private void callout(float ax, float ay, float bx, float by, String label, int col) {
        if (Float.isNaN(ax) || Float.isNaN(ay)) return;
        Ui.disc(ax, ay, 1.6f, Theme.a(col, 1f), 10);
        Ui.ring(ax, ay, 3.5f, 0.5f, Theme.a(col, 0.8f), 14);
        Ui.line(ax, ay, bx, by, 0.5f, Theme.a(col, 0.8f));
        float ex = bx + (bx > ax ? 14 : -14);
        Ui.line(bx, by, ex, by, 0.5f, Theme.a(col, 0.8f));
        if (bx > ax) Ui.text(label, ex + 2, by - 3, Theme.a(Theme.WHITE, 1f), 0.45f);
        else Ui.textR(label, ex - 2, by - 3, Theme.a(Theme.WHITE, 1f), 0.45f);
    }

    private void renderInfo(float t, int ec) {
        WeaponType w = sel;
        float x = RX0, y = 42;
        Ui.panel(RX0 - 3, VY0, RX1, VY1, Theme.TECH, 0.9f);
        x += 4;
        y += 5;
        Ui.textGlow(Ui.typed(w.displayName, t * 3), x, y, Theme.WHITE, 1.7f, 0.5f);
        y += 17;
        Ui.text(w.classification, x, y, Theme.a(Theme.TECH, 1f), 0.6f);
        float chipW = Ui.width(w.rarity) * 0.5f + 8;
        Ui.rect(RX1 - 6 - chipW, y - 1, RX1 - 6, y + 7, Theme.a(w.rarityColor, 0.2f));
        Ui.frame(RX1 - 6 - chipW, y - 1, RX1 - 6, y + 7, 0.5f, Theme.a(w.rarityColor, 0.9f));
        Ui.text(w.rarity, RX1 - 2 - chipW, y + 1, Theme.a(w.rarityColor, 1f), 0.5f);
        y += 10;
        boolean unlocked = ClientData.unlocked(w);
        boolean eq = owned(w);
        String status = !unlocked ? "LOCKED - COMPLETE " + w.unlockMissions + " MISSIONS (" + ClientData.missions() + "/" + w.unlockMissions + ")"
                : eq ? "EQUIPPED  //  IN INVENTORY" : "UNLOCKED  //  READY FOR ISSUE";
        Ui.text(status, x, y, Theme.a(!unlocked ? Theme.RED : eq ? 0x3CFF8E : Theme.AMBER, 1f), 0.47f);
        y += 9;

        // description, wrapped
        float scale = 0.5f;
        List<FormattedCharSequence> lines = Ui.font.split(Component.literal(w.description), (int) ((RX1 - RX0 - 10) / scale));
        for (FormattedCharSequence line : lines) {
            Ui.g.flush();
            Ui.g.pose().pushPose();
            Ui.g.pose().translate(x, y, 0);
            Ui.g.pose().scale(scale, scale, 1);
            Ui.g.drawString(Ui.font, line, 0, 0, Theme.a(Theme.WHITE, 0.75f * Ui.alpha) | 0x05000000, false);
            Ui.g.pose().popPose();
            y += 5.5f;
        }
        y += 3;

        // stats
        for (int i = 0; i < 5; i++) {
            Ui.text(WeaponType.STAT_NAMES[i], x, y, Theme.a(Theme.MUTED, 1f), 0.45f);
            float bx0 = x + 46, bx1 = RX1 - 22;
            float f = Ease.outCubic(t * 1.8f - 0.3f - i * 0.1f) * w.stats[i] / 10f;
            Ui.rect(bx0, y + 1, bx1, y + 4.5f, Theme.a(Theme.TECH_DEEP, 0.8f));
            Ui.gradH(bx0, y + 1, bx0 + (bx1 - bx0) * f, y + 4.5f, Theme.a(Theme.scale(ec, 0.5f), 1f), Theme.a(ec, 1f));
            Ui.textR(String.valueOf(Math.round(w.stats[i] * Math.min(1, f * 10f / Math.max(1, w.stats[i])))), RX1 - 6, y, Theme.a(Theme.WHITE, 0.9f), 0.45f);
            y += 7.5f;
        }
        y += 1;
        Ui.text(String.format("CELLS %d  //  RECHARGE %.1fs  //  OUTPUT %.0f", w.capacity, w.reloadTicks / 20f, w.damage), x, y, Theme.a(Theme.TECH, 0.9f), 0.45f);
        y += 9;

        // finish swatches
        Ui.text("FINISH  " + WeaponType.FINISH_NAMES[Math.floorMod(ClientData.finish(w), 4)], x, y, Theme.a(Theme.MUTED, 1f), 0.45f);
        y += 6;
        for (int i = 0; i < WeaponType.FINISH_COLORS.length; i++) {
            float sx = x + i * 20;
            boolean on = ClientData.finish(w) == i;
            final int idx = i;
            Ui.rect(sx, y, sx + 16, y + 8, Theme.a(WeaponType.FINISH_COLORS[i], 1f));
            Ui.gradV(sx, y, sx + 16, y + 4, 0x30FFFFFF, 0x00FFFFFF);
            Ui.frame(sx - 1, y - 1, sx + 17, y + 9, on ? 1f : 0.5f, Theme.a(on ? Theme.WHITE : Theme.TECH, on ? 1f : 0.4f));
            s.hit("fin" + i, sx - 1, y - 1, sx + 17, y + 9, true, () -> {
                ClientData.setPref(w, "F", idx);
                AegisNetwork.toServer(new Packets.ArsenalAction(Packets.SET_FINISH, w.ordinal(), idx));
                Sfx.raw(SoundEvents.ARMOR_EQUIP_CHAIN, 1.5f, 0.4f);
            });
        }
        y += 12;
        Ui.text("ENERGY  " + WeaponType.ENERGY_NAMES[Math.floorMod(ClientData.energy(w), 5)], x, y, Theme.a(Theme.MUTED, 1f), 0.45f);
        y += 6;
        for (int i = 0; i < WeaponType.ENERGY_COLORS.length; i++) {
            float sx = x + i * 20;
            boolean on = ClientData.energy(w) == i;
            final int idx = i;
            Ui.glow(sx + 8, y + 4, 9, Theme.a(WeaponType.ENERGY_COLORS[i], on ? 0.45f : 0.15f), 16);
            Ui.rect(sx + 2, y + 1, sx + 14, y + 7, Theme.a(WeaponType.ENERGY_COLORS[i], 1f));
            Ui.frame(sx - 1, y - 1, sx + 17, y + 9, on ? 1f : 0.5f, Theme.a(on ? Theme.WHITE : Theme.TECH, on ? 1f : 0.4f));
            s.hit("en" + i, sx - 1, y - 1, sx + 17, y + 9, true, () -> {
                ClientData.setPref(w, "E", idx);
                AegisNetwork.toServer(new Packets.ArsenalAction(Packets.SET_ENERGY, w.ordinal(), idx));
                Sfx.raw(SoundEvents.BEACON_POWER_SELECT, 1.7f, 0.35f);
                WeaponAnimator.PREVIEW.reload(w);
            });
        }
        y += 13;

        // animation previews
        float bw = (RX1 - 6 - x - 6) / 4f;
        String[] pv = {"DEPLOY", "FIRE", "RELOAD", "INSPECT"};
        Ui.text("PREVIEW", x, y - 1, Theme.a(Theme.MUTED, 1f), 0.42f);
        y += 5;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            s.button("pv" + i, x + i * (bw + 2), y, x + i * (bw + 2) + bw, y + 10, pv[i], Theme.TECH, switchMs < 0, false, () -> preview(idx));
        }
        y += 15;
        Ui.text("VIEW", x, y - 1, Theme.a(Theme.MUTED, 1f), 0.42f);
        y += 5;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            s.button("vw" + i, x + i * (bw + 2), y, x + i * (bw + 2) + bw, y + 10, VIEWS[i], Theme.TECH, true, view == i, () -> setView(idx));
        }
        y += 15;

        // equipment
        float half = (RX1 - 6 - x - 3) / 2f;
        if (!eq) {
            s.button("equip", x, y, x + half, y + 13, unlocked ? "EQUIP" : "LOCKED", 0x3CFF8E, unlocked, false, () -> {
                AegisNetwork.toServer(new Packets.ArsenalAction(Packets.EQUIP, w.ordinal(), 0));
                ClientData.feed(w.displayName + " ISSUED TO OPERATOR", ClientData.Level.OK);
                WeaponAnimator.PREVIEW.deploy();
                Sfx.raw(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.2f, 0.7f);
            });
        } else {
            s.button("unequip", x, y, x + half, y + 13, "UNEQUIP", Theme.AMBER, true, false, () -> {
                AegisNetwork.toServer(new Packets.ArsenalAction(Packets.UNEQUIP, w.ordinal(), 0));
                ClientData.feed(w.displayName + " RETURNED TO ARMORY", ClientData.Level.INFO);
                Sfx.raw(SoundEvents.ARMOR_EQUIP_IRON, 0.8f, 0.6f);
            });
        }
        s.button("ready", x + half + 3, y, RX1 - 6, y + 13, "SWITCH TO", Theme.TECH, eq, false, () -> {
            AegisNetwork.toServer(new Packets.ArsenalAction(Packets.SELECT, w.ordinal(), 0));
            ClientData.feed(w.displayName + " READIED IN PRIMARY HAND", ClientData.Level.OK);
            Sfx.raw(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.5f, 0.5f);
        });
        y += 17;
        s.button("back", x, y, RX1 - 6, y + 11, "< RETURN TO ARMORY", Theme.MUTED, true, false, () -> {
            mode = Mode.GRID;
            page = sel.ordinal() / 3;
            gridMs = System.currentTimeMillis() - 3000;
            revealed = 3;
        });
    }

    // ------------------------------------------------------------------ preview choreography

    private void preview(int which) {
        WeaponType w = sel;
        long now = System.currentTimeMillis();
        switch (which) {
            case 0 -> {
                WeaponAnimator.PREVIEW.deploy();
                Sfx.raw(SoundEvents.ARMOR_EQUIP_NETHERITE, 1.4f, 0.6f);
                Sfx.later(4, () -> Sfx.raw(SoundEvents.BEACON_POWER_SELECT, 1.9f, 0.3f));
            }
            case 1 -> {
                switch (w) {
                    case PHANTOM_9 -> { for (int i = 0; i < 3; i++) cues.add(new Cue(now + i * 220L, () -> shot(w, 1f))); }
                    case SPECTRE_7 -> { for (int i = 0; i < 10; i++) cues.add(new Cue(now + i * 85L, () -> shot(w, 1f))); }
                    case TITAN_X -> {
                        for (int i = 0; i <= 10; i++) {
                            final float c = i / 10f;
                            cues.add(new Cue(now + i * 90L, () -> {
                                WeaponAnimator.PREVIEW.setCharge(c);
                                Sfx.raw(SoundEvents.NOTE_BLOCK_BIT.value(), 0.5f + c * 1.4f, 0.3f);
                            }));
                        }
                        cues.add(new Cue(now + 1000, () -> shot(w, 1f)));
                    }
                    case NOVA_RAIL -> {
                        Sfx.raw(SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.4f);
                        for (int i = 0; i <= 8; i++) {
                            final float c = i / 8f;
                            cues.add(new Cue(now + i * 70L, () -> WeaponAnimator.PREVIEW.setCharge(c * 0.6f)));
                        }
                        cues.add(new Cue(now + 650, () -> shot(w, 1f)));
                    }
                    case HELIX_4 -> {
                        cues.add(new Cue(now, () -> shot(w, 1f)));
                        cues.add(new Cue(now + 700, () -> shot(w, 1f)));
                    }
                    case OBLIVION -> {
                        Sfx.raw(SoundEvents.WARDEN_SONIC_CHARGE, 1.6f, 0.5f);
                        for (int i = 0; i <= 10; i++) {
                            final float c = i / 10f;
                            cues.add(new Cue(now + i * 80L, () -> WeaponAnimator.PREVIEW.setCharge(c)));
                        }
                        cues.add(new Cue(now + 900, () -> shot(w, 1f)));
                    }
                }
            }
            case 2 -> {
                WeaponAnimator.PREVIEW.reload(w);
                int d = w.reloadTicks;
                Sfx.raw(SoundEvents.BEACON_DEACTIVATE, 1.8f, 0.4f);
                Sfx.later((int) (d * 0.35), () -> Sfx.raw(SoundEvents.ARMOR_EQUIP_IRON, 1.5f, 0.6f));
                Sfx.later((int) (d * 0.55), () -> Sfx.raw(SoundEvents.IRON_TRAPDOOR_CLOSE, 1.9f, 0.5f));
                Sfx.later((int) (d * 0.78), () -> Sfx.raw(SoundEvents.BEACON_ACTIVATE, 2.0f, 0.4f));
            }
            case 3 -> {
                WeaponAnimator.PREVIEW.inspect();
                Sfx.raw(SoundEvents.ARMOR_EQUIP_CHAIN, 1.2f, 0.5f);
                Sfx.later(22, () -> Sfx.raw(SoundEvents.ARMOR_EQUIP_CHAIN, 1.4f, 0.5f));
            }
            default -> {}
        }
    }

    private void shot(WeaponType w, float strength) {
        WeaponAnimator.PREVIEW.fire(w, strength);
        long now = System.currentTimeMillis();
        float width = switch (w) {
            case TITAN_X -> 4f; case NOVA_RAIL -> 5f; case OBLIVION -> 7f; case HELIX_4 -> 2f; case PHANTOM_9 -> 1.6f; default -> 1.2f;
        };
        float len = switch (w) {
            case PHANTOM_9, NOVA_RAIL -> 400; case TITAN_X -> 40; case OBLIVION -> 26; case HELIX_4 -> 16; default -> 22;
        };
        float life = switch (w) {
            case PHANTOM_9 -> 0.18f; case NOVA_RAIL -> 0.45f; case OBLIVION -> 1.1f; default -> 0.6f;
        };
        if (w == WeaponType.HELIX_4) {
            for (int k = -3; k <= 3; k++) {
                double a = k * 0.07 + (RNG.nextFloat() - 0.5f) * 0.05;
                float dx = (float) (muzzleDx * Math.cos(a) - muzzleDy * Math.sin(a));
                float dy = (float) (muzzleDx * Math.sin(a) + muzzleDy * Math.cos(a));
                shots.add(new float[]{muzzleSx, muzzleSy, dx, dy, now, life, len, width});
            }
        } else {
            shots.add(new float[]{muzzleSx, muzzleSy, muzzleDx, muzzleDy, now, life, len, width});
        }
        if (w.heavy) com.aegis.command.client.fx.ImpactFrames.trigger(0.5f, energyRgb(w));
        switch (w) {
            case PHANTOM_9 -> {
                Sfx.raw(SoundEvents.BEACON_POWER_SELECT, 2.0f, 0.4f);
                Sfx.raw(SoundEvents.FIREWORK_ROCKET_BLAST, 1.9f, 0.4f);
            }
            case SPECTRE_7 -> {
                Sfx.raw(SoundEvents.BLAZE_SHOOT, 2.0f, 0.15f);
                Sfx.raw(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.4f);
            }
            case TITAN_X -> {
                Sfx.raw(SoundEvents.WARDEN_SONIC_BOOM, 1.5f, 0.5f);
                Sfx.raw(SoundEvents.GENERIC_EXPLODE, 1.7f, 0.35f);
                WeaponAnimator.PREVIEW.setCharge(0);
            }
            case NOVA_RAIL -> {
                Sfx.raw(SoundEvents.WARDEN_SONIC_BOOM, 1.9f, 0.6f);
                Sfx.raw(SoundEvents.TRIDENT_THUNDER, 1.8f, 0.3f);
                WeaponAnimator.PREVIEW.setCharge(0);
            }
            case HELIX_4 -> {
                Sfx.raw(SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, 0.6f);
                Sfx.raw(SoundEvents.PISTON_EXTEND, 1.6f, 0.4f);
                Sfx.later(6, () -> Sfx.raw(SoundEvents.PISTON_CONTRACT, 1.4f, 0.4f));
            }
            case OBLIVION -> {
                Sfx.raw(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.6f, 0.6f);
                Sfx.raw(SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.5f);
                WeaponAnimator.PREVIEW.setCharge(0);
            }
        }
        for (int i = 0; i < (w.heavy ? 30 : w == WeaponType.HELIX_4 ? 18 : 8); i++) {
            motes.add(new float[]{muzzleSx, muzzleSy, (RNG.nextFloat() - 0.5f) * 80 + muzzleDx * 40, (RNG.nextFloat() - 0.5f) * 80 + muzzleDy * 40, 2.5f});
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseDown(float mx, float my, int button) {
        if (mode == Mode.INSPECT && mx >= VX0 && mx < VX1 && my >= VY0 && my < VY1) {
            dragging = true;
            lastInput = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseUp(float mx, float my, int button) {
        dragging = false;
        return false;
    }

    @Override
    public boolean drag(float mx, float my, float dx, float dy, int button) {
        if (mode != Mode.INSPECT || !dragging) return false;
        tYaw += dx * 0.9f;
        tPitch = Math.max(-70, Math.min(70, tPitch + dy * 0.6f));
        lastInput = System.currentTimeMillis();
        return true;
    }

    @Override
    public boolean scroll(float mx, float my, double delta) {
        if (mode != Mode.INSPECT) return false;
        tZoom = Math.max(0.6f, Math.min(3.2f, tZoom + (float) delta * 0.18f));
        lastInput = System.currentTimeMillis();
        Sfx.ui(SoundEvents.NOTE_BLOCK_HAT.value(), 1.5f + tZoom * 0.2f, 0.06f);
        return true;
    }
}
