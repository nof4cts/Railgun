package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.network.FxPacket;
import com.geto.csm.network.FxType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server FX events → choreographed world effects, impact frames and shake. */
public final class FxDispatcher {
    private static final int VIOLET = 0xFF9B5CFF, WHITE = 0xFFFFFFFF, EMBER = 0xFFFF7A2A, RED = 0xFFE0283C, CYAN = 0xFF4FE6E0, GOLD = 0xFFF2C14E;

    static boolean feels(Vec3 pos, double range) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceTo(pos) < range;
    }

    private static boolean involves(FxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (p.src == mc.player.getId() || p.tgt == mc.player.getId());
    }

    private static boolean feels(FxPacket p, Vec3 pos, double range) {
        return involves(p) || feels(pos, range);
    }

    public static void handle(FxPacket p) {
        Vec3 pos = new Vec3(p.x, p.y, p.z);
        FxType type = FxType.byId(p.type);
        switch (type) {
            case RIFT -> {
                CurseKind k = CurseKind.byId((int) p.a);
                double R = switch (k) {
                    case WYRM, TAMAMO, NAMAZU, WORM -> 3.4;
                    case RAY -> 2.6;
                    case CENTIPEDE -> 0.9;
                    default -> 1.8;
                };
                Vec3 ground = k == CurseKind.WYRM || k == CurseKind.TAMAMO || k == CurseKind.RAY ? pos.add(0, -0.4, 0) : pos;
                Vfx.add(new CsmFx.Rift(ground, k.color, R, k.special ? 2400 : 1500));
                Vfx.add(new Vfx.Spikes(pos.add(0, 0.5, 0), 18, R * 1.4, VIOLET, 380, new Vec3(0, 1, 0), 0.6));
                if (p.b < 0.5f) {
                    Vfx.add(Vfx.Shockwave.ground(ground, R * 2.2, VIOLET, 600));
                    if (feels(p, pos, 20)) {
                        ScreenFx.impact(k.special ? ScreenFx.INK : ScreenFx.INVERT, k.special ? 160 : 80, pos);
                        CameraDirector.shake(k.special ? 1.4f : 0.5f, 400);
                    }
                }
            }
            case VANISH -> {
                Vfx.add(new Vfx.Flash(pos, 1.6, VIOLET, 350));
                Vfx.add(new Vfx.Spikes(pos, 16, 2.4, VIOLET, 320));
                SpineTrail.forget(p.src);
            }
            case ORB_FORM -> {
                Vfx.add(new CsmFx.OrbForm(pos, 900));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, -0.4, 0), 2, VIOLET, 500));
            }
            case ABSORB -> {
                Vfx.add(new Vfx.Flash(pos.add(0, -0.3, 0), 0.6, VIOLET, 300));
                if (involves(p)) ScreenFx.card(new ScreenFx.Card("吸収", "CURSE ABSORBED · " + (int) p.a + " STORED", "", ScreenFx.Card.POP, VIOLET, 900, false));
            }
            case ACTION -> CurseAnim.trigger(p.src, (int) p.a);
            case BITE -> {
                boolean big = p.b > 0.5f;
                Vfx.add(new Vfx.Flash(pos, big ? 2.0 : 1.2, WHITE, 260));
                Vfx.add(new Vfx.Spikes(pos, big ? 30 : 16, big ? 4 : 2.4, EMBER, 300));
                Vfx.add(new Vfx.Debris(pos.add(0, -1, 0), big ? 14 : 6, 0.8, 1300));
                if (feels(p, pos, 18)) CameraDirector.shake(big ? 0.8f : 0.35f, 300);
            }
            case TAIL_SWEEP -> {
                double r = Math.max(3, p.b);
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, 0.3, 0), r, WHITE, 450));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, 0.8, 0), r * 1.3, EMBER, 600));
                Vfx.add(new Vfx.Debris(pos, 20, 1.2, 1500));
                if (feels(p, pos, r + 10)) CameraDirector.shake(0.9f, 400);
            }
            case ROAR_BEAM -> {
                Vec3 dir = Draw.forward(p.a, p.b);
                Vfx.add(new CsmFx.BreathCone(pos, dir, 24, VIOLET, EMBER, 1300));
                Vfx.add(Vfx.Shockwave.facing(pos.add(dir.scale(2)), dir, 4, WHITE, 400));
                if (feels(p, pos, 30)) {
                    ScreenFx.impact(ScreenFx.INK, 200, pos.add(dir.scale(6)));
                    CameraDirector.shake(1.8f, 1100);
                    ScreenFx.speed(900, 0.7f);
                }
            }
            case WORM_EMERGE -> {
                Vfx.add(Vfx.Shockwave.ground(pos, 6, 0xFFB06BC0, 700));
                Vfx.add(new Vfx.Debris(pos, 34, 1.6, 2000));
                Vfx.add(new Vfx.Spikes(pos.add(0, 0.5, 0), 26, 5, WHITE, 400, new Vec3(0, 1, 0), 1.2));
                if (feels(p, pos, 24)) {
                    ScreenFx.impact(ScreenFx.INK, 200, pos.add(0, 3, 0));
                    CameraDirector.shake(2.2f, 800);
                }
            }
            case WORM_SWALLOW -> {
                Vfx.add(new Vfx.Flash(pos, 2.2, 0xFFFF2A50, 300));
                if (feels(p, pos, 20)) {
                    ScreenFx.impact(ScreenFx.RED, 180, pos);
                    CameraDirector.shake(1.4f, 500);
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && p.tgt == mc.player.getId()) {
                    ScreenFx.vignette(1f);
                    ScreenFx.card(new ScreenFx.Card("SWALLOWED", "", "", ScreenFx.Card.SLAM, RED, 1400, false));
                    Sfx.later(3400, () -> ScreenFx.vignette(0f));
                }
            }
            case WORM_SPIT -> {
                Vfx.add(new Vfx.Flash(pos, 1.6, 0xFFB06BC0, 260));
                Vfx.add(new Vfx.Spikes(pos, 20, 3, 0xFFB06BC0, 300));
            }
            case GUST -> {
                Vfx.add(Vfx.Shockwave.ground(pos, 6, CYAN, 500));
                Vfx.add(Vfx.Shockwave.ground(pos.add(0, 0.6, 0), 4, WHITE, 400));
                if (feels(p, pos, 14)) {
                    ScreenFx.impact(ScreenFx.INVERT, 140, pos);
                    CameraDirector.shake(1.2f, 400);
                }
            }
            case CENTI_BITE -> {
                boolean venom = p.b > 0.5f;
                Vfx.add(new Vfx.Flash(pos, venom ? 2.0 : 0.5, 0xFF9CFF3A, venom ? 320 : 160));
                Vfx.add(new Vfx.Spikes(pos, venom ? 24 : 6, venom ? 3.0 : 0.9, 0xFF9CFF3A, 220));
                if (venom) {
                    Vfx.add(Vfx.Shockwave.ground(pos, 3.5, 0xFF9CFF3A, 450));
                    if (feels(p, pos, 14)) {
                        ScreenFx.impact(ScreenFx.INK, 150, pos);
                        CameraDirector.shake(1.0f, 350);
                    }
                }
            }
            case PYRE_PULSE -> {
                float k = (p.a - 120f) / 2880f;
                int mc = Draw.lerp(0xFFFF4A10, 0xFFFFF0C0, k);
                Vfx.add(Vfx.Shockwave.ground(pos, p.b, mc, 700));
            }
            case PYRE_FLARE -> {
                float k = (p.b - 120f) / 2880f;
                int mc = Draw.lerp(0xFFFF4A10, 0xFFFFF0C0, k);
                Entity t = Vfx.entity(p.tgt);
                Vec3 dir = t != null ? t.position().add(0, 1, 0).subtract(pos) : Draw.forward(p.a, 0);
                Vfx.add(new CsmFx.BreathCone(pos, dir, 10, mc, 0xFFFF2A00, 900));
                Sfx.later(400, () -> {
                    Vec3 hit = pos.add(dir.normalize().scale(5));
                    Vfx.add(new Vfx.Flash(hit, 2.5, mc, 350));
                    Vfx.add(Vfx.Shockwave.ground(hit.add(0, -1, 0), 6, mc, 600));
                    Vfx.add(new Vfx.Pillar(hit.add(0, -1.2, 0), 6 + k * 10, 1.2, mc, 700));
                    if (feels(hit, 20)) {
                        ScreenFx.impact(ScreenFx.INVERT, 150, hit);
                        CameraDirector.shake(1.5f, 450);
                    }
                });
            }
            case KUCHI_DOMAIN -> {
                Vfx.add(new CsmFx.VowDome(pos, p.a, (long) (p.b * 50)));
                if (feels(p, pos, 16)) {
                    ScreenFx.impact(ScreenFx.RED, 160, pos);
                    CameraDirector.shake(0.8f, 400);
                    Sfx.play(SoundEvents.BELL_RESONATE, 0.5f, 0.8f);
                }
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && p.tgt == mc.player.getId()) ScreenFx.rules(2200);
            }
            case SHEARS -> {
                boolean big = p.b > 0.5f;
                Vfx.add(new CsmFx.ShearsFx(p.tgt, pos, big, big ? 900 : 380));
            }
            case MOUTH_SLASH -> {
                Vfx.add(new Vfx.Flash(pos, 1.0, RED, 300));
                Vfx.add(new Vfx.Spikes(pos, 22, 2.4, RED, 300));
                if (feels(p, pos, 16)) {
                    ScreenFx.impact(ScreenFx.RED, 240, pos);
                    CameraDirector.shake(1.4f, 400);
                }
            }
            case FOXFIRE -> Vfx.add(new CsmFx.Foxfire(pos, p.tgt, (int) p.a, 900));
            case KILLING_STONE -> {
                Vfx.add(new CsmFx.KillingStone(pos, 7000));
                if (feels(p, pos, 30)) ScreenFx.card(new ScreenFx.Card("殺生石", "KILLING STONE", "", ScreenFx.Card.CORNER, VIOLET, 1500, false));
            }
            case MIASMA -> Vfx.add(new CsmFx.Miasma(pos, Math.max(3, p.a), 6000));
            case QUAKE_WAVE -> {
                Vfx.add(Vfx.Shockwave.ground(pos, p.a, 0xFFC9B26A, 600));
                Vfx.add(new Vfx.Debris(pos, (int) (p.a * 3), 1.0, 1400));
                if (feels(pos, p.a + 8)) {
                    CameraDirector.shake(2.0f, 500);
                    ScreenFx.impact(ScreenFx.INVERT, 70, pos);
                }
            }
            case UZUMAKI_CORE -> Vfx.add(new CsmFx.UzuCore(pos, (int) p.a, 4400));
            case UZUMAKI_GATHER -> {
                Entity src = Vfx.entity(p.src);
                Vec3 from = src != null ? src.position().add(0, src.getBbHeight() * 0.5, 0) : pos.add(5, 2, 0);
                CurseKind k = CurseKind.byId((int) p.a);
                Vfx.add(new CsmFx.UzuStream(from, pos, k.color, 1700));
                Vfx.add(new Vfx.Flash(from, 2.5, k.color, 400));
            }
            case UZUMAKI_BEAM -> {
                Vec3 dir = Draw.forward(p.a, p.b);
                Vfx.add(new CsmFx.UzuBeam(pos, dir, 1700));
                for (int i = 0; i < 5; i++) {
                    final int k = i;
                    Sfx.later(i * 60L, () -> Vfx.add(Vfx.Shockwave.facing(pos.add(dir.scale(3 + k * 7)), dir, 5 + k * 1.5, k % 2 == 0 ? WHITE : VIOLET, 600)));
                }
                Vfx.add(new Vfx.Spikes(pos, 60, 10, VIOLET, 500, dir, 1.6));
                if (feels(pos, 60)) {
                    ScreenFx.impact(ScreenFx.INVERT, 260, pos.add(dir.scale(6)));
                    ScreenFx.flash(0xFFE8D8FF, 500);
                    CameraDirector.shake(3.4f, 1200);
                }
            }
            case SMALL_HIT -> {
                Vfx.add(new Vfx.Flash(pos, 0.8, WHITE, 200));
                Vfx.add(new Vfx.Spikes(pos, 10, 1.6, VIOLET, 220));
            }
        }
    }

    private FxDispatcher() {}
}
