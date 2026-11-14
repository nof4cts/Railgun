package com.aegis.command.client.render;

import com.aegis.command.client.ui.Ease;
import com.aegis.command.item.WeaponType;
import net.minecraft.util.Mth;

/**
 * Procedural, layered weapon animation: idle breathing, deploy, recoil spring, reload choreography,
 * inspect turn and charge tremor. One instance drives the first-person hand, another the arsenal preview.
 */
public final class WeaponAnimator {
    public static final WeaponAnimator HAND = new WeaponAnimator();
    public static final WeaponAnimator PREVIEW = new WeaponAnimator();
    public static final WeaponAnimator STATIC = new WeaponAnimator();

    private long fireMs = -100000, reloadMs = -100000, deployMs = -100000, inspectMs = -100000, chargeMs = -100000;
    private int reloadDur = 1500;
    private float recoil = 0.5f;
    private float charge;
    private long seed;

    public static final class Pose {
        public float tx, ty, tz, rx, ry, rz;
        public float flash;
        public float glow = 1f;
        public float coreFlash;
        public float charge;
        /** 0..1: how far the magazine / cell group is pulled out (reload). */
        public float magOut;
        /** Degrees for the SPIN group and the counter-rotating AUX group. */
        public float spin, auxSpin;
        /** 0..1 heavy-shot hold: the weapon freezes at peak recoil for a beat (impact frame). */
        public float hitStop;
    }

    private static long now() { return System.currentTimeMillis(); }

    public void fire(WeaponType t, float strength) {
        fireMs = now();
        recoil = t.recoil * strength;
        seed = fireMs;
    }

    public void reload(WeaponType t) {
        reloadMs = now();
        reloadDur = t.reloadTicks * 50;
    }

    public void deploy() { deployMs = now(); }
    public void inspect() { inspectMs = now(); }
    public boolean busy() { return now() - reloadMs < reloadDur || now() - inspectMs < 2400; }

    public void setCharge(float c) {
        charge = c;
        chargeMs = now();
    }

    public float reloadProgress() {
        float p = (now() - reloadMs) / (float) reloadDur;
        return p < 0 || p >= 1 ? -1 : p;
    }

    private static float window(float p, float in, float out) {
        float a = Ease.inOutCubic(Mth.clamp(p / in, 0, 1));
        float b = Ease.inOutCubic(Mth.clamp((p - (1 - out)) / out, 0, 1));
        return a * (1 - b);
    }

    public Pose pose(WeaponType type, boolean idle) {
        Pose p = new Pose();
        long now = now();
        float t = (now % 3_600_000L) / 1000f;

        p.glow = 0.78f + 0.22f * (float) Math.sin(t * 3.0);
        if (type == WeaponType.SPECTRE_7) p.glow += 0.12f * (float) Math.sin(t * 11.0) * (float) Math.sin(t * 1.7);
        if (type == WeaponType.NOVA_RAIL) p.glow += 0.1f * (float) Math.sin(t * 23.0) * (float) Math.sin(t * 3.1);
        float sinceFire = (now - fireMs) / 1000f;
        float boost = sinceFire >= 0 && sinceFire < 1.2f ? (1.2f - sinceFire) : 0;
        p.spin = (t * (type == WeaponType.OBLIVION ? 70f : 40f) + boost * boost * 260f) % 360f;
        p.auxSpin = -(t * 25f + boost * 180f) % 360f;
        if (idle) {
            p.ty += (float) Math.sin(t * 1.6) * 0.006f;
            p.rz += (float) Math.sin(t * 1.1) * 0.6f;
            p.rx += (float) Math.sin(t * 0.9) * 0.4f;
        }

        // deploy: swing up from below with an overshoot, core powers on
        float de = (now - deployMs) / 380f;
        if (de >= 0 && de < 1) {
            float k = 1 - Ease.outBack(de);
            p.ty -= 0.55f * k;
            p.rx -= 55f * k;
            p.rz += 20f * k;
            p.glow *= Mth.clamp(de * 1.4f, 0, 1);
            if (de > 0.6f) p.coreFlash = Math.max(p.coreFlash, 1 - (de - 0.6f) / 0.4f);
        }

        // recoil spring
        float fe = now - fireMs;
        boolean heavy = type.heavy;
        if (fe >= 0 && fe < (heavy ? 900 : 450)) {
            // heavy weapons hold at peak recoil for ~70ms (hit-stop), then spring back slowly
            float hold = heavy ? 70f : 0f;
            float fe2 = heavy ? Math.max(0, fe - hold) : fe;
            float decay = heavy ? 150f : 85f;
            float k = recoil * (float) Math.exp(-fe2 / decay) * Mth.clamp(fe / 14f, 0, 1);
            if (heavy && fe < hold) p.hitStop = 1f;
            p.tz += 0.2f * k;
            p.rx += 10f * k;
            p.ry += (float) Math.sin(seed) * 1.6f * k;
            p.rz += (float) Math.cos(seed) * 2.2f * k;
            float fl = heavy ? 110f : 70f;
            p.flash = fe < fl ? 1 - fe / fl : 0;
            if (heavy) p.rx += 6f * k;
            p.glow += 0.7f * Math.max(0, 1 - fe / 160f);
        }

        // reload choreography: tilt, eject cell (core dies), seat new cell, recharge flash, return
        float rp = (now - reloadMs) / (float) reloadDur;
        if (rp >= 0 && rp < 1) {
            float a = window(rp, 0.2f, 0.2f);
            p.rz += 34f * a;
            p.ry -= 16f * a;
            p.ty -= 0.12f * a;
            p.rx += 6f * a;
            float dip = (float) Math.sin(Math.PI * Mth.clamp((rp - 0.35f) / 0.3f, 0, 1));
            p.ty -= 0.08f * dip;
            p.rx -= 12f * dip;
            float g = rp < 0.3f ? 1 - rp / 0.3f * 0.9f : (rp < 0.75f ? 0.1f : 0.1f + (rp - 0.75f) / 0.25f * 0.9f);
            p.glow *= g;
            p.coreFlash = Math.max(p.coreFlash, Math.max(0, 1 - Math.abs(rp - 0.78f) / 0.06f));
            // cell out at 25-45%, back in at 55-70%
            float out = Ease.inOutCubic(Mth.clamp((rp - 0.22f) / 0.2f, 0, 1));
            float in = Ease.outBack(Mth.clamp((rp - 0.55f) / 0.16f, 0, 1));
            p.magOut = Mth.clamp(out * (1 - in), 0, 1.1f);
        }

        // inspect: rotate to show the flank, roll, return
        float ie = (now - inspectMs) / 2400f;
        if (ie >= 0 && ie < 1) {
            float a = window(ie, 0.18f, 0.22f);
            float flip = window(Mth.clamp((ie - 0.35f) / 0.35f, 0, 1), 0.4f, 0.4f);
            p.ry += 70f * a + 110f * flip;
            p.rz -= 18f * a;
            p.rx += 8f * (float) Math.sin(ie * Math.PI * 2) * a;
            p.tx -= 0.22f * a;
            p.ty += 0.06f * a;
        }

        // charge tremor
        if (now - chargeMs < 150) {
            float c = charge;
            p.charge = c;
            p.tx += (float) (Math.sin(now * 0.09) * 0.006 * c);
            p.ty += (float) (Math.cos(now * 0.11) * 0.006 * c);
            p.tz += 0.06f * c;
            p.glow += c * 0.9f;
        }
        return p;
    }
}
