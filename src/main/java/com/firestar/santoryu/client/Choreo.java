package com.firestar.santoryu.client;

import static com.firestar.santoryu.client.fx.ScreenFx.hit;
import static com.firestar.santoryu.client.fx.ScreenFx.smooth;
import static com.firestar.santoryu.client.fx.ScreenFx.window;

import com.firestar.santoryu.common.Beats;
import com.firestar.santoryu.client.fx.Impact;
import com.firestar.santoryu.client.fx.Ribbon;
import com.firestar.santoryu.client.fx.ScreenFx;
import com.firestar.santoryu.client.fx.Shapes;
import com.firestar.santoryu.registry.ModSounds;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import org.joml.Vector3f;

/**
 * The choreography: for every technique, what sounds and particles land on which tick, which strokes are drawn at a
 * given moment, and how the frame itself is graded and hit. Everything is a function of the cast clock, so a scrub
 * through time always draws the same picture, and every client sees the same beats.
 */
final class Choreo {
    static final int WHITE = 0xFFFFFF;
    static final int STEEL = 0xDDF2FF;
    static final int CRIMSON = 0xFF2416;
    static final int VIOLET = 0x8A3BFF;
    static final int FIRE = 0xFF6A1A;
    static final int GOLD = 0xFFD98A;
    static final Vec3 UP = ClientCast.UP;

    private Choreo() {}

    // ============================================================================================ sound & particles

    static void tick(ClientCast c, int t) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 chest = c.local(0, 1.2, 0);
        switch (c.move) {
            case ONI_GIRI -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 1.1F);
                if (t == Beats.ONI_DASH) {
                    play(c, ModSounds.DASH, chest, 1.2F, 1.0F);
                    burst(level, ParticleTypes.CLOUD, c.feet, 12, 0.6, 0.05);
                }
                if (t == Beats.ONI_HIT) {
                    play(c, ModSounds.SLASH_HEAVY, c.point, 1.4F, 1.0F);
                    play(c, ModSounds.IMPACT, c.point, 1.2F, 1.1F);
                    burst(level, ParticleTypes.CRIT, c.point, 30, 0.8, 0.6);
                    burst(level, ParticleTypes.SWEEP_ATTACK, c.point, 3, 0.6, 0.0);
                }
                if (t == 15) play(c, ModSounds.SHEATHE, c.exit.add(0, 1, 0), 1.0F, 1.0F);
            }
            case TATSU_MAKI -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 1.0F);
                if (t == 4) play(c, ModSounds.TORNADO, c.feet, 1.4F, 1.1F);
                for (int i = 0; i < Beats.TATSU_PULSES; i++) {
                    if (t == Beats.TATSU_FIRST + i * Beats.TATSU_EVERY) {
                        play(c, ModSounds.SLASH, chest, 1.0F, 0.9F + i * 0.08F);
                    }
                }
                if (t >= 4 && t < 36) {
                    for (int i = 0; i < 2; i++) {
                        double a = (t * 0.7 + i * Math.PI);
                        double r = 1.5 + (t - 4) * 0.09;
                        Vec3 p = c.feet.add(Math.cos(a) * r, 0.2 + i * 0.6, Math.sin(a) * r);
                        level.addParticle(ParticleTypes.CLOUD, true, p.x, p.y, p.z, -Math.sin(a) * 0.3, 0.12, Math.cos(a) * 0.3);
                    }
                }
                if (t == Beats.TATSU_FIRST + (Beats.TATSU_PULSES - 1) * Beats.TATSU_EVERY) {
                    play(c, ModSounds.IMPACT, chest, 1.3F, 0.9F);
                }
            }
            case POUND_HO -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 1.2F);
                if (t == Beats.POUND_FIRE) {
                    play(c, ModSounds.CANNON, c.eye, 1.4F, 1.15F);
                    burst(level, ParticleTypes.CLOUD, c.eye.add(c.dir), 14, 0.4, 0.15);
                }
                if (t > Beats.POUND_FIRE && t <= Beats.POUND_FIRE + Beats.POUND_FLIGHT) {
                    Vec3 p = projectile(c, t, Beats.POUND_FIRE, Beats.POUND_SPEED);
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, true, p.x, p.y, p.z, 0, 0, 0);
                    level.addParticle(ParticleTypes.CLOUD, true, p.x, p.y, p.z, 0, 0.02, 0);
                }
            }
            case SHISHI_SONSON -> {
                if (t == 2 || t == 16) play(c, ModSounds.HEARTBEAT, chest, 1.0F, 1.0F);
                if (t == Beats.SHISHI_VANISH - 2) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 1.3F);
                if (t == Beats.SHISHI_VANISH) {
                    play(c, ModSounds.DASH, chest, 0.8F, 1.4F);
                    burst(level, ParticleTypes.CLOUD, c.feet, 8, 0.4, 0.04);
                }
                if (t == Beats.SHISHI_CLICK) play(c, ModSounds.SHEATHE, c.exit.add(0, 1, 0), 1.6F, 1.0F);
                if (t == Beats.SHISHI_CLICK + 1) {
                    play(c, ModSounds.SLASH_HEAVY, c.point, 1.6F, 0.85F);
                    play(c, ModSounds.IMPACT_BIG, c.point, 1.2F, 1.2F);
                    burst(level, ParticleTypes.CRIT, c.point, 40, 1.2, 0.8);
                    burst(level, ParticleTypes.SWEEP_ATTACK, c.point, 6, 1.0, 0.0);
                }
                if (t == Beats.SHISHI_CLICK + 3) play(c, ModSounds.TING, c.exit.add(0, 1, 0), 1.0F, 1.0F);
            }
            case HYAKUHACHI -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 0.95F);
                if (t == 12) play(c, ModSounds.TORNADO, chest, 0.8F, 1.4F);
                if (t == 14) play(c, ModSounds.STING, chest, 0.9F, 1.0F);
                if (t >= 12 && t < Beats.HYAKU_FIRE && t % 3 == 0) {
                    double a = t * 0.9;
                    Vec3 p = chest.add(Math.cos(a) * 1.3, -0.2, Math.sin(a) * 1.3);
                    level.addParticle(ParticleTypes.CLOUD, true, p.x, p.y, p.z, 0, 0.05, 0);
                }
                if (t == Beats.HYAKU_FIRE) {
                    play(c, ModSounds.CANNON, c.eye, 1.8F, 0.85F);
                    play(c, ModSounds.SLASH_HEAVY, c.eye, 1.2F, 0.9F);
                    burst(level, ParticleTypes.EXPLOSION, c.eye.add(c.dir.scale(1.5)), 2, 0.3, 0.0);
                }
                if (t > Beats.HYAKU_FIRE && t <= Beats.HYAKU_FIRE + Beats.HYAKU_FLIGHT) {
                    Vec3 p = projectile(c, t, Beats.HYAKU_FIRE, Beats.HYAKU_SPEED);
                    burst(level, ParticleTypes.CLOUD, p, 3, 1.2, 0.05);
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, true, p.x, p.y, p.z, 0, 0, 0);
                }
                if (t == Beats.HYAKU_FIRE + Beats.HYAKU_FLIGHT) {
                    Vec3 p = projectile(c, t, Beats.HYAKU_FIRE, Beats.HYAKU_SPEED);
                    play(c, ModSounds.IMPACT_BIG, p, 1.4F, 1.0F);
                }
            }
            case KOKUJO -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 0.9F);
                if (t == 6) play(c, ModSounds.HAKI, chest, 1.1F, 1.0F);
                if (t == 12) play(c, ModSounds.STING, chest, 1.0F, 0.9F);
                if (t == Beats.KOKUJO_FORM) {
                    play(c, ModSounds.TORNADO, c.point, 2.0F, 0.75F);
                    play(c, ModSounds.DRAGON, c.point, 1.6F, 1.0F);
                }
                if (t > Beats.KOKUJO_FORM && t < Beats.KOKUJO_BURST) {
                    if (t % 8 == 0) play(c, ModSounds.SLASH, c.point.add(0, 4, 0), 1.2F, 0.7F + (t % 3) * 0.1F);
                    for (int i = 0; i < 5; i++) {
                        double a = t * 0.55 + i * 1.256;
                        double h = (i * 2.3 + t * 0.3) % 12.0;
                        double r = 2.0 + h * 0.45;
                        Vec3 p = c.point.add(Math.cos(a) * r, h, Math.sin(a) * r);
                        level.addParticle(ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, -Math.sin(a) * 0.4, 0.2, Math.cos(a) * 0.4);
                        level.addParticle(dust(VIOLET, 1.0F), true, p.x, p.y, p.z, 0, 0.1, 0);
                    }
                }
                if (t == Beats.KOKUJO_BURST) {
                    play(c, ModSounds.IMPACT_BIG, c.point, 2.0F, 0.8F);
                    burst(level, ParticleTypes.EXPLOSION, c.point.add(0, 3, 0), 6, 4.0, 0.0);
                }
            }
            case SANZEN_SEKAI -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 1.0F);
                if (t == 8) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 1.12F);
                if (t == 18) play(c, ModSounds.UNSHEATHE, c.local(0, 1.55, 0.2), 1.1F, 1.3F);
                if (t == Beats.cardStart(c.move)) play(c, ModSounds.STING, chest, 1.0F, 1.0F);
                if (t >= 48 && t < Beats.SANZEN_DASH && t % 5 == 0) play(c, ModSounds.SLASH, chest, 0.5F, 1.4F + (t % 10) * 0.02F);
                if (t == Beats.SANZEN_DASH) play(c, ModSounds.DASH, chest, 1.4F, 0.9F);
                if (t == Beats.SANZEN_HIT) {
                    play(c, ModSounds.SLASH_HEAVY, c.point, 1.8F, 0.9F);
                    play(c, ModSounds.IMPACT_BIG, c.point, 1.6F, 1.0F);
                    burst(level, ParticleTypes.CRIT, c.point, 60, 2.0, 1.0);
                    burst(level, ParticleTypes.SWEEP_ATTACK, c.point, 10, 1.6, 0.0);
                }
                if (t == Beats.SANZEN_HIT + 2) play(c, ModSounds.TING, c.exit.add(0, 1, 0), 1.0F, 0.9F);
                if (t == Beats.SANZEN_HIT + 14) play(c, ModSounds.SHEATHE, c.exit.add(0, 1, 0), 1.0F, 1.0F);
            }
            case ASURA -> {
                if (t == 0 || t == 40 || t == 110 || t == 130) play(c, ModSounds.HEARTBEAT, chest, 1.2F, t > 100 ? 1.15F : 1.0F);
                if (t == Beats.ASURA_AURA) play(c, ModSounds.ASURA, chest, 1.4F, 1.0F);
                if (t == Beats.ASURA_MANIFEST) {
                    play(c, ModSounds.ASURA, chest, 1.8F, 0.85F);
                    play(c, ModSounds.IMPACT_BIG, chest, 1.0F, 0.7F);
                }
                if (t == Beats.cardStart(c.move)) play(c, ModSounds.STING, chest, 1.2F, 0.9F);
                if (t >= Beats.ASURA_AURA && t < Beats.ASURA_HIT) {
                    float k = smooth((t - Beats.ASURA_AURA) / 50.0F);
                    for (int i = 0; i < 1 + (int) (k * 4); i++) {
                        Vec3 p = c.local((level.random.nextDouble() - 0.5) * 1.6, level.random.nextDouble() * 2.2, (level.random.nextDouble() - 0.5) * 1.2);
                        level.addParticle(dust(CRIMSON, 0.8F), true, p.x, p.y, p.z, 0, 0.12, 0);
                        if (i == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, true, p.x, p.y, p.z, 0, 0.08, 0);
                    }
                }
                if (t == Beats.ASURA_DASH - 2) play(c, ModSounds.UNSHEATHE, chest, 1.2F, 0.8F);
                if (t == Beats.ASURA_DASH) play(c, ModSounds.DASH, chest, 1.6F, 0.8F);
                if (t == Beats.ASURA_HIT) {
                    play(c, ModSounds.SLASH_HEAVY, c.point, 2.0F, 0.75F);
                    play(c, ModSounds.IMPACT_BIG, c.point, 2.0F, 0.85F);
                    burst(level, ParticleTypes.CRIT, c.point, 80, 2.4, 1.2);
                    burst(level, ParticleTypes.SWEEP_ATTACK, c.point, 14, 2.0, 0.0);
                    burst(level, dust(CRIMSON, 2.5F), c.point, 40, 2.0, 0.2);
                }
                if (t == Beats.ASURA_HIT + 3) play(c, ModSounds.TING, c.exit.add(0, 1, 0), 1.0F, 0.8F);
                if (t == Beats.ASURA_HIT + 26) play(c, ModSounds.SHEATHE, c.exit.add(0, 1, 0), 1.2F, 0.95F);
            }
            case RENGOKU -> {
                if (t == 0) play(c, ModSounds.UNSHEATHE, chest, 1.0F, 0.8F);
                if (t == Beats.RENGOKU_DRAIN) play(c, ModSounds.HAKI, chest, 1.3F, 0.9F);
                if (t == 30 || t == 100) play(c, ModSounds.HEARTBEAT, chest, 1.3F, 0.9F);
                if (t == Beats.RENGOKU_CONQUER) {
                    play(c, ModSounds.IMPACT_BIG, chest, 1.8F, 0.6F);
                    play(c, ModSounds.HAKI, chest, 1.8F, 0.7F);
                }
                if (t == Beats.cardStart(c.move)) play(c, ModSounds.STING, chest, 1.3F, 0.85F);
                if (t >= Beats.RENGOKU_DRAIN && t < Beats.RENGOKU_HIT) {
                    if (t % 2 == 0) {
                        Vec3 p = c.local(0.45 + level.random.nextDouble() * 0.5, 0.9 + level.random.nextDouble() * 0.9, 0.3);
                        level.addParticle(dust(VIOLET, 0.7F), true, p.x, p.y, p.z, 0, 0.08, 0);
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, p.x, p.y, p.z, 0, 0, 0);
                    }
                }
                if (t == Beats.RENGOKU_DASH - 2) play(c, ModSounds.UNSHEATHE, chest, 1.2F, 0.75F);
                if (t == Beats.RENGOKU_DASH) play(c, ModSounds.DASH, chest, 1.6F, 0.75F);
                if (t == Beats.RENGOKU_HIT) {
                    play(c, ModSounds.SLASH_HEAVY, c.point, 2.0F, 0.7F);
                    play(c, ModSounds.IMPACT_BIG, c.point, 2.0F, 0.8F);
                    play(c, ModSounds.DRAGON, c.point, 1.2F, 1.3F);
                    burst(level, ParticleTypes.FLAME, c.point, 60, 2.0, 0.3);
                    burst(level, ParticleTypes.LAVA, c.point, 16, 1.5, 0.0);
                    burst(level, dust(VIOLET, 2.5F), c.point, 40, 2.0, 0.2);
                }
                if (t == Beats.RENGOKU_HIT + 3) play(c, ModSounds.TING, c.exit.add(0, 1, 0), 1.0F, 0.75F);
                if (t == Beats.RENGOKU_HIT + 24) play(c, ModSounds.SHEATHE, c.exit.add(0, 1, 0), 1.2F, 0.9F);
            }
            case DRAGON -> {
                if (t == 0 || t == 30 || t == 158) play(c, ModSounds.HEARTBEAT, chest, 1.4F, 0.85F);
                if (t == 10 || t == 120) play(c, ModSounds.HAKI, chest, 1.3F, 0.8F);
                if (t == Beats.DRAGON_CONQUER) {
                    play(c, ModSounds.IMPACT_BIG, chest, 2.0F, 0.5F);
                    play(c, ModSounds.HAKI, chest, 2.0F, 0.6F);
                }
                if (t == 70 || t == 78 || t == 86) play(c, ModSounds.UNSHEATHE, chest, 1.2F, 0.8F + (t - 70) * 0.02F);
                if (t == Beats.cardStart(c.move)) play(c, ModSounds.STING, chest, 1.4F, 0.8F);
                if (t >= 70 && t < Beats.DRAGON_HIT && t % 2 == 0) {
                    Vec3 p = c.local((level.random.nextDouble() - 0.5) * 2.0, level.random.nextDouble() * 2.4, (level.random.nextDouble() - 0.5) * 2.0);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, true, p.x, p.y, p.z, 0, 0, 0);
                    level.addParticle(dust(VIOLET, 0.8F), true, p.x, p.y, p.z, 0, 0.1, 0);
                }
                if (t == Beats.DRAGON_LEAP) play(c, ModSounds.DASH, chest, 2.0F, 0.6F);
                if (t == Beats.DRAGON_LEAP + 4) play(c, ModSounds.DRAGON, c.point, 2.0F, 0.85F);
                if (t == Beats.DRAGON_HIT) {
                    play(c, ModSounds.SLASH_HEAVY, c.point, 2.0F, 0.6F);
                    play(c, ModSounds.IMPACT_BIG, c.point, 2.0F, 0.7F);
                    play(c, ModSounds.CANNON, c.point, 1.6F, 0.6F);
                    burst(level, ParticleTypes.EXPLOSION, c.point, 6, 3.0, 0.0);
                    burst(level, ParticleTypes.FLAME, c.point, 80, 3.0, 0.4);
                    burst(level, dust(VIOLET, 3.0F), c.point, 60, 3.0, 0.3);
                }
                if (t == Beats.DRAGON_HIT + 4) play(c, ModSounds.TING, c.exit.add(0, 1, 0), 1.0F, 0.7F);
                if (t == 240) play(c, ModSounds.SHEATHE, c.exit.add(0, 1, 0), 1.4F, 0.85F);
            }
        }
    }

    // ============================================================================================ strokes

    static void ribbons(ClientCast c, float t, List<Ribbon> out) {
        Vec3 chest = c.local(0, 1.2, 0);
        Vec3 r = c.right, f = c.fwd;
        switch (c.move) {
            case ONI_GIRI -> {
                // arms crossed, blades crossed in front of the chest, gathering
                float gather = window(t, 0, 5, Beats.ONI_DASH, Beats.ONI_DASH + 1);
                Shapes.blade(out, chest.add(f.scale(0.4)), r.add(UP).normalize(), r.scale(-1).add(UP).normalize(), 0.55, -40, 40, 0.06F, 1, gather, STEEL);
                Shapes.blade(out, chest.add(f.scale(0.4)), r.scale(-1).add(UP).normalize(), r.add(UP).normalize(), 0.55, -40, 40, 0.06F, 1, gather, STEEL);
                dashTrail(out, c, t, Beats.ONI_DASH, 6, STEEL, 1);
                float rev = (t - Beats.ONI_HIT) / 2.0F;
                float fade = 1.0F - smooth((t - Beats.ONI_HIT - 4) / 9.0F);
                cut(out, c.point, r.add(UP).normalize(), 3.2, 0.32F, rev, fade, STEEL);
                cut(out, c.point, r.subtract(UP).normalize(), 3.2, 0.32F, rev - 0.3F, fade, STEEL);
                cut(out, c.point.add(0, 0.2, 0), r, 3.6, 0.26F, rev - 0.6F, fade, WHITE);
            }
            case TATSU_MAKI -> {
                float on = window(t, 3, 8, 36, 44);
                for (int i = 0; i < 5; i++) {
                    double phase = t * 0.55 + i * 1.2566;
                    Shapes.helix(out, c.feet, 1.2 + i * 0.25, 3.8 + i * 0.4, 4.5 + i * 0.6, 1.6, phase, 0.35F, on * 0.55F, STEEL, Ribbon.Style.GLOW);
                }
                // the blade ring at the waist, one full turn per pulse
                float spin = t * 1.05F;
                Shapes.blade(out, c.local(0, 1.1, 0), new Vec3(Math.cos(spin), 0, Math.sin(spin)), new Vec3(-Math.sin(spin), 0, Math.cos(spin)),
                        1.4, -60, 60, 0.12F, 1, window(t, 4, 6, 32, 38), WHITE);
            }
            case POUND_HO -> {
                float draw = window(t, 0, 6, Beats.POUND_FIRE, Beats.POUND_FIRE + 1);
                Shapes.blade(out, chest.add(f.scale(-0.2)), f.scale(-1), r, 0.9, -50, 50, 0.08F, 1, draw, STEEL);
                flyingSlash(out, c, t, Beats.POUND_FIRE, Beats.POUND_FLIGHT, Beats.POUND_SPEED, 2.4, 0.6F, STEEL, 1);
            }
            case SHISHI_SONSON -> {
                dashTrail(out, c, t, Beats.SHISHI_VANISH, 5, WHITE, 1);
                float rev = (t - Beats.SHISHI_CLICK) / 1.0F;
                float fade = 1.0F - smooth((t - Beats.SHISHI_CLICK - 6) / 18.0F);
                cut(out, c.point, r, 7.5, 0.22F + 0.25F * smooth((t - Beats.SHISHI_CLICK) / 8.0F), rev, fade, WHITE);
                float ring = t - Beats.SHISHI_CLICK;
                if (ring > 0 && ring < 14) {
                    Shapes.ring(out, c.point, UP, 0.5 + ring * 0.7, 0.25F, 1.0F - ring / 14.0F, STEEL, Ribbon.Style.GLOW);
                }
            }
            case HYAKUHACHI -> {
                float wind = window(t, 10, 18, Beats.HYAKU_FIRE - 2, Beats.HYAKU_FIRE + 1);
                float speed = 0.2F + 0.8F * smooth((t - 10) / 38.0F);
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.9 * speed + i * 2.094;
                    Vec3 u = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 v = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    Shapes.blade(out, c.local(0, 1.1 + (i - 1) * 0.25, 0), u, v, 1.35, -50, 50, 0.1F, 1, wind, STEEL);
                }
                for (int i = 0; i < 3; i++) {
                    flyingSlash(out, c, t, Beats.HYAKU_FIRE, Beats.HYAKU_FLIGHT, Beats.HYAKU_SPEED, 3.2 - i * 0.3, 0.8F, i == 1 ? WHITE : STEEL, 1.0 - i * 0.12);
                }
                float end = t - Beats.HYAKU_FIRE - Beats.HYAKU_FLIGHT;
                if (end > 0 && end < 16) {
                    Vec3 p = projectile(c, Beats.HYAKU_FIRE + Beats.HYAKU_FLIGHT, Beats.HYAKU_FIRE, Beats.HYAKU_SPEED);
                    Shapes.ring(out, p, c.dir, 1 + end * 0.8, 0.5F, 1.0F - end / 16.0F, STEEL, Ribbon.Style.GLOW);
                }
            }
            case KOKUJO -> {
                // haki creeping up the blades
                float black = window(t, 4, 20, Beats.KOKUJO_FORM, Beats.KOKUJO_FORM + 6);
                for (int i = 0; i < 3; i++) {
                    Shapes.helix(out, c.local(0, 0.2, 0), 0.7, 0.9, 2.2, 1.5, t * 0.4 + i * 2.1, 0.05F, black * 0.5F, 0x08000C, Ribbon.Style.INK);
                    Shapes.helix(out, c.local(0, 0.2, 0), 0.75, 0.95, 2.2, 1.5, t * 0.4 + i * 2.1, 0.35F, black * 0.4F, VIOLET, Ribbon.Style.GLOW);
                }
                if (black > 0.2F) {
                    long tick = (long) t;
                    Random rr = new Random(c.seed + tick * 31);
                    for (int i = 0; i < 2; i++) {
                        Vec3 a = c.local((rr.nextDouble() - 0.5) * 1.6, 0.4 + rr.nextDouble() * 1.6, 0.4);
                        Shapes.bolt(out, a, a.add((rr.nextDouble() - 0.5) * 1.8, rr.nextDouble() * 1.2, (rr.nextDouble() - 0.5) * 1.8), tick * 7 + i, 0.5, 0.05F, black, 0x050008, Ribbon.Style.INK);
                    }
                }
                // the black dragon tornado
                float rise = smooth((t - Beats.KOKUJO_FORM) / 14.0F);
                float on = window(t, Beats.KOKUJO_FORM, Beats.KOKUJO_FORM + 4, Beats.KOKUJO_BURST, Beats.KOKUJO_BURST + 6);
                for (int i = 0; i < 12; i++) {
                    double phase = t * (0.38 + i * 0.012) + i * 0.5236;
                    double h = (11.0 + (i % 3)) * rise;
                    Shapes.helix(out, c.point, 0.8 + i * 0.25, 4.5 + i * 0.45, h, 2.0 + (i % 4) * 0.25, phase, 0.38F, on * 0.75F, 0x060009, Ribbon.Style.INK);
                    Shapes.helix(out, c.point, 0.9 + i * 0.25, 4.6 + i * 0.45, h, 2.0 + (i % 4) * 0.25, phase + 0.12, 1.0F, on * 0.3F, VIOLET, Ribbon.Style.GLOW);
                }
                // its head, circling the crown of the storm
                double ha = t * 0.3;
                Vec3 head = c.point.add(Math.cos(ha) * 6.0, 13.0 * rise, Math.sin(ha) * 6.0);
                Vec3 hu = new Vec3(Math.cos(ha), 0, Math.sin(ha));
                Shapes.hakiBlade(out, head, hu, UP, 2.2, -70, 70, 0.6F, 1, on, VIOLET);
                float burst = t - Beats.KOKUJO_BURST;
                if (burst >= 0 && burst < 18) {
                    Shapes.ring(out, c.point.add(0, 0.5, 0), UP, 2 + burst * 1.4, 1.2F, 1 - burst / 18.0F, VIOLET, Ribbon.Style.GLOW);
                    Shapes.ring(out, c.point.add(0, 0.5, 0), UP, 1.8 + burst * 1.4, 0.5F, 1 - burst / 18.0F, 0x050008, Ribbon.Style.INK);
                }
            }
            case SANZEN_SEKAI -> {
                // three blades spun like windmill sails: right hand, left hand, mouth
                float mill = window(t, 36, 44, Beats.SANZEN_DASH - 2, Beats.SANZEN_DASH + 1);
                double spin = t * 1.4;
                Vec3[] hubs = {c.local(0.55, 1.15, 0.45), c.local(-0.55, 1.15, 0.45), c.local(0.0, 1.55, 0.45)};
                for (int i = 0; i < 3; i++) {
                    double s = spin * (i == 2 ? -1.3 : 1.0) + i;
                    Vec3 u = UP.scale(Math.cos(s)).add(r.scale(Math.sin(s)));
                    Vec3 v = UP.scale(-Math.sin(s)).add(r.scale(Math.cos(s)));
                    Shapes.blade(out, hubs[i], u, v, 0.75, 0, 300, 0.06F, 1, mill * 0.8F, GOLD);
                }
                dashTrail(out, c, t, Beats.SANZEN_DASH, 8, GOLD, 3);
                float rev = (t - Beats.SANZEN_HIT) / 2.0F;
                float fade = 1.0F - smooth((t - Beats.SANZEN_HIT - 6) / 14.0F);
                cut(out, c.point, r.add(UP.scale(0.6)).normalize(), 5.5, 0.5F, rev, fade, WHITE);
                cut(out, c.point, r.subtract(UP.scale(0.6)).normalize(), 5.5, 0.5F, rev - 0.25F, fade, GOLD);
                cut(out, c.point, r, 6.0, 0.4F, rev - 0.5F, fade, STEEL);
                // the three thousand worlds: small cuts bursting outward all round the target
                if (t >= Beats.SANZEN_HIT && fade > 0) {
                    Random rr = new Random(c.seed);
                    float spread = smooth((t - Beats.SANZEN_HIT) / 8.0F);
                    for (int i = 0; i < 24; i++) {
                        Vec3 axis = new Vec3(rr.nextGaussian(), rr.nextGaussian(), rr.nextGaussian()).normalize();
                        Vec3 along = axis.cross(UP).lengthSqr() < 1e-3 ? r : axis.cross(UP).normalize();
                        Vec3 at = c.point.add(axis.scale(0.6 + 2.6 * spread));
                        cut(out, at, along, 0.8 + rr.nextDouble(), 0.1F, rev - 0.2F - i * 0.02F, fade, i % 3 == 0 ? GOLD : WHITE);
                    }
                }
            }
            case ASURA -> {
                // the demon's aura: crimson flame coiling up round him, black at its heart
                float aura = window(t, Beats.ASURA_AURA, Beats.ASURA_AURA + 30, Beats.ASURA_HIT + 4, Beats.ASURA_HIT + 30);
                for (int i = 0; i < 6; i++) {
                    double phase = t * 0.21 + i * 1.047;
                    float big = Beats.ASURA_MANIFEST <= t ? 1.0F : 0.6F;
                    Shapes.helix(out, c.feet.add(0, -0.2, 0), 0.6 * big, 1.6 * big, 3.2 * big, 0.9, phase, 0.45F, aura * 0.5F, CRIMSON, Ribbon.Style.GLOW);
                    Shapes.helix(out, c.feet.add(0, -0.2, 0), 0.65 * big, 1.5 * big, 2.6 * big, 0.9, phase + 0.3, 0.07F, aura * 0.55F, 0x0A0000, Ribbon.Style.INK);
                }
                float man = t - Beats.ASURA_MANIFEST;
                if (man >= 0 && man < 16) {
                    Shapes.ring(out, c.local(0, 0.1, 0), UP, 1 + man * 1.6, 0.6F, 1 - man / 16.0F, CRIMSON, Ribbon.Style.GLOW);
                }
                // nine blades, three per body, through the target
                for (int body = -1; body <= 1; body++) {
                    Vec3 off = r.scale(body * 1.1);
                    for (int b = 0; b < 3; b++) {
                        Vec3 o = off.add(0, 0.8 + b * 0.35, 0);
                        float a = 1.0F - smooth((t - Beats.ASURA_DASH - 3) / 8.0F);
                        if (t >= Beats.ASURA_DASH) {
                            Shapes.streak(out, c.feet.add(o), c.exit.add(o), 0.35F, a, b == 1 ? WHITE : CRIMSON, Ribbon.Style.GLOW);
                        }
                    }
                }
                float rev = (t - Beats.ASURA_HIT) / 2.0F;
                float fade = 1.0F - smooth((t - Beats.ASURA_HIT - 8) / 18.0F);
                for (int i = 0; i < 9; i++) {
                    double ang = Math.toRadians(-60 + i * 15);
                    Vec3 along = r.scale(Math.cos(ang)).add(UP.scale(Math.sin(ang)));
                    cut(out, c.point.add(f.scale((i % 3 - 1) * 0.4)), along, 6.5, 0.38F, rev - i * 0.12F, fade, i % 3 == 1 ? WHITE : CRIMSON);
                }
                float ring = t - Beats.ASURA_HIT;
                if (ring >= 0 && ring < 20) {
                    Shapes.ring(out, c.point, f, 1 + ring * 1.2, 0.8F, 1 - ring / 20.0F, CRIMSON, Ribbon.Style.GLOW);
                }
            }
            case RENGOKU -> {
                // Enma draws: violet climbing the swordsman, black lightning on the blades
                float drain = window(t, Beats.RENGOKU_DRAIN, Beats.RENGOKU_DRAIN + 20, Beats.RENGOKU_HIT, Beats.RENGOKU_HIT + 20);
                for (int i = 0; i < 4; i++) {
                    Shapes.helix(out, c.feet, 0.5, 1.0, 2.4, 1.2, t * 0.33 + i * 1.57, 0.4F, drain * 0.5F, VIOLET, Ribbon.Style.GLOW);
                }
                blackLightning(out, c, t, drain, 3, 1.3);
                float wave = t - Beats.RENGOKU_CONQUER;
                if (wave >= 0 && wave < 24) {
                    Shapes.ring(out, c.local(0, 0.3, 0), UP, 1 + wave * 1.3, 1.2F, 1 - wave / 24.0F, VIOLET, Ribbon.Style.GLOW);
                    Shapes.ring(out, c.local(0, 0.3, 0), UP, 0.8 + wave * 1.3, 0.35F, 1 - wave / 24.0F, 0x050008, Ribbon.Style.INK);
                }
                dashTrail(out, c, t, Beats.RENGOKU_DASH, 7, FIRE, 2);
                float rev = (t - Beats.RENGOKU_HIT) / 2.0F;
                float fade = 1.0F - smooth((t - Beats.RENGOKU_HIT - 8) / 20.0F);
                Vec3 d1 = r.add(UP).normalize(), d2 = r.subtract(UP).normalize();
                cutHaki(out, c.point, d1, 7.0, 0.7F, rev, fade, FIRE);
                cutHaki(out, c.point, d2, 7.0, 0.7F, rev - 0.4F, fade, FIRE);
                cut(out, c.point.add(f.scale(0.3)), d1, 6.5, 0.25F, rev, fade, VIOLET);
                cut(out, c.point.add(f.scale(0.3)), d2, 6.5, 0.25F, rev - 0.4F, fade, VIOLET);
            }
            case DRAGON -> {
                float aura = window(t, 10, 40, Beats.DRAGON_HIT, Beats.DRAGON_HIT + 30);
                for (int i = 0; i < 5; i++) {
                    Shapes.helix(out, c.feet, 0.6, 1.5, 3.0, 1.1, t * 0.27 + i * 1.256, 0.3F, aura * 0.45F, VIOLET, Ribbon.Style.GLOW);
                    Shapes.helix(out, c.feet, 0.55, 1.3, 2.6, 1.1, t * 0.27 + i * 1.256 + 0.3, 0.06F, aura * 0.5F, 0x050008, Ribbon.Style.INK);
                }
                blackLightning(out, c, t, window(t, 60, 80, Beats.DRAGON_LEAP, Beats.DRAGON_LEAP + 2), 5, 1.8);
                float wave = t - Beats.DRAGON_CONQUER;
                if (wave >= 0 && wave < 30) {
                    Shapes.ring(out, c.local(0, 0.3, 0), UP, 1 + wave * 1.6, 1.6F, 1 - wave / 30.0F, VIOLET, Ribbon.Style.GLOW);
                    Shapes.ring(out, c.local(0, 0.3, 0), UP, 0.8 + wave * 1.6, 0.5F, 1 - wave / 30.0F, 0x050008, Ribbon.Style.INK);
                }
                // the leap: an arc from his feet up to the target
                float leap = (t - Beats.DRAGON_LEAP) / (float) (Beats.DRAGON_HIT - Beats.DRAGON_LEAP);
                if (leap >= 0 && leap <= 1.6F) {
                    float a = 1.0F - smooth((leap - 1.0F) / 0.6F);
                    Vec3 prev = null;
                    for (int i = 0; i <= 10; i++) {
                        float k = Math.min(leap, 1.0F) * i / 10.0F;
                        Vec3 p = c.feet.add(0, 1, 0).lerp(c.point, k).add(0, Math.sin(Math.PI * k) * 6.0, 0);
                        if (prev != null) {
                            Shapes.streak(out, prev, p, 0.9F, a * 0.6F, VIOLET, Ribbon.Style.GLOW);
                            Shapes.streak(out, prev, p, 0.3F, a, 0x050008, Ribbon.Style.INK);
                        }
                        prev = p;
                    }
                }
                // the serpent coils round the target, then closes
                float coil = window(t, Beats.DRAGON_LEAP + 4, Beats.DRAGON_HIT - 4, Beats.DRAGON_HIT + 6, Beats.DRAGON_HIT + 26);
                float close = smooth((t - Beats.DRAGON_LEAP - 4) / 18.0F);
                for (int i = 0; i < 5; i++) {
                    double phase = t * 0.5 + i * 1.2566;
                    double r0 = 5.5 - 3.5 * close + (i % 2) * 0.4;
                    Shapes.helix(out, c.point.add(0, -3, 0), r0, 1.0, 9.0, 3.2, phase, 0.9F, coil * 0.45F, VIOLET, Ribbon.Style.GLOW);
                    Shapes.helix(out, c.point.add(0, -3, 0), r0 - 0.05, 0.9, 9.0, 3.2, phase + 0.04, 0.22F, coil * 0.85F, 0x050008, Ribbon.Style.INK);
                    Shapes.helix(out, c.point.add(0, -3, 0), r0, 1.0, 9.0, 3.2, phase, 0.06F, coil, WHITE, Ribbon.Style.GLOW);
                }
                float rev = (t - Beats.DRAGON_HIT) / 2.0F;
                float fade = 1.0F - smooth((t - Beats.DRAGON_HIT - 10) / 26.0F);
                cutHaki(out, c.point, r, 12.0, 1.1F, rev, fade, VIOLET);
                cutHaki(out, c.point, UP, 11.0, 1.0F, rev - 0.3F, fade, FIRE);
                cutHaki(out, c.point, r.add(UP).normalize(), 10.0, 0.8F, rev - 0.6F, fade, VIOLET);
                float ring = t - Beats.DRAGON_HIT;
                if (ring >= 0 && ring < 26) {
                    Shapes.ring(out, c.point, f, 1 + ring * 1.8, 1.4F, 1 - ring / 26.0F, VIOLET, Ribbon.Style.GLOW);
                    Shapes.ring(out, c.point, UP, 1 + ring * 2.2, 0.9F, 1 - ring / 26.0F, FIRE, Ribbon.Style.GLOW);
                }
            }
        }
    }

    // ============================================================================================ the frame

    static void screen(ClientCast c, float t, ScreenFx fx) {
        switch (c.move) {
            case ONI_GIRI -> {
                float s = t - Beats.ONI_HIT;
                fx.impact(Impact.HEAVY, s, c.point, CRIMSON);
                fx.shake(hit(t, Beats.ONI_HIT, 5) * 0.7F);
                fx.punch(hit(t, Beats.ONI_DASH, 3) * 0.8F);
                fx.aberration(hit(t, Beats.ONI_HIT, 6) * 0.9F);
                fx.flash(WHITE, hit(t, Beats.ONI_HIT, 2) * 0.35F);
            }
            case TATSU_MAKI -> {
                fx.shake(window(t, 4, 10, 32, 40) * 0.25F);
                int last = Beats.TATSU_FIRST + (Beats.TATSU_PULSES - 1) * Beats.TATSU_EVERY;
                fx.impact(Impact.LIGHT, t - last, c.feet.add(0, 1, 0), STEEL);
                fx.punch(hit(t, last, 3) * 0.5F);
                fx.bloom(window(t, 4, 10, 32, 40) * 0.3F);
            }
            case POUND_HO -> {
                fx.punch(hit(t, Beats.POUND_FIRE, 3) * 0.5F);
                fx.shake(hit(t, Beats.POUND_FIRE, 4) * 0.3F);
                fx.aberration(hit(t, Beats.POUND_FIRE, 5) * 0.5F);
            }
            case SHISHI_SONSON -> {
                // the silence before the draw: colour drains, the world holds its breath
                float still = window(t, 0, 10, Beats.SHISHI_CLICK + 2, Beats.SHISHI_CLICK + 20);
                fx.desaturate(still * 0.75F);
                fx.vignette(0x000000, still * 0.55F);
                fx.grain(still * 0.6F);
                fx.contrast(still * 0.35F);
                fx.punch(hit(t, Beats.SHISHI_VANISH, 2) * 0.9F);
                float s = t - Beats.SHISHI_CLICK;
                fx.impact(Impact.IAI, s, c.point, CRIMSON);
                fx.flash(WHITE, s < 1 && s >= 0 ? 1.4F : hit(t, Beats.SHISHI_CLICK + 1, 5) * 0.6F);
                fx.shake(hit(t, Beats.SHISHI_CLICK, 6) * 0.9F);
                fx.aberration(hit(t, Beats.SHISHI_CLICK, 8) * 1.2F);
            }
            case HYAKUHACHI -> {
                fx.shake(window(t, 10, Beats.HYAKU_FIRE - 4, Beats.HYAKU_FIRE, Beats.HYAKU_FIRE + 1) * 0.3F);
                fx.impact(Impact.HEAVY, t - Beats.HYAKU_FIRE, c.eye.add(c.dir.scale(4)), STEEL);
                fx.punch(hit(t, Beats.HYAKU_FIRE, 4) * 1.0F);
                fx.flash(WHITE, hit(t, Beats.HYAKU_FIRE, 3) * 0.5F);
                fx.aberration(hit(t, Beats.HYAKU_FIRE, 8) * 1.0F);
                fx.shake(hit(t, Beats.HYAKU_FIRE + Beats.HYAKU_FLIGHT, 6) * 0.6F);
                fx.bloom(window(t, 10, 30, Beats.HYAKU_FIRE + 10, Beats.HYAKU_FIRE + 24) * 0.5F);
            }
            case KOKUJO -> {
                fx.haki(window(t, 6, 24, Beats.KOKUJO_BURST + 4, Beats.KOKUJO_BURST + 20) * 0.6F);
                fx.shake(window(t, Beats.KOKUJO_FORM, Beats.KOKUJO_FORM + 8, Beats.KOKUJO_BURST, Beats.KOKUJO_BURST + 4) * 0.35F);
                fx.impact(Impact.LIGHT, t - Beats.KOKUJO_FORM, c.point.add(0, 3, 0), VIOLET);
                fx.impact(Impact.KING, t - Beats.KOKUJO_BURST, c.point.add(0, 4, 0), VIOLET);
                fx.flash(0xC9A6FF, hit(t, Beats.KOKUJO_BURST, 4) * 0.9F);
                fx.shake(hit(t, Beats.KOKUJO_BURST, 8) * 1.0F);
                fx.vignette(0x12001E, window(t, 6, 24, Beats.KOKUJO_BURST, Beats.KOKUJO_BURST + 18) * 0.5F);
                fx.bloom(0.4F * window(t, Beats.KOKUJO_FORM, Beats.KOKUJO_FORM + 8, Beats.KOKUJO_BURST, Beats.KOKUJO_BURST + 14));
            }
            case SANZEN_SEKAI -> {
                fx.shake(window(t, 44, 60, Beats.SANZEN_DASH - 4, Beats.SANZEN_DASH) * 0.15F);
                fx.bloom(window(t, 36, 50, Beats.SANZEN_HIT, Beats.SANZEN_HIT + 20) * 0.5F);
                fx.punch(hit(t, Beats.SANZEN_DASH, 3) * 1.0F);
                float s = t - Beats.SANZEN_HIT;
                fx.impact(Impact.ULTIMATE, s, c.point, GOLD);
                fx.flash(WHITE, s >= 0 && s < 1 ? 1.3F : hit(t, Beats.SANZEN_HIT + 1, 6) * 0.5F);
                fx.shake(hit(t, Beats.SANZEN_HIT, 9) * 1.0F);
                fx.aberration(hit(t, Beats.SANZEN_HIT, 10) * 1.3F);
                fx.desaturate(window(t, Beats.SANZEN_HIT + 12, Beats.SANZEN_HIT + 16, 124, 134) * 0.4F);
            }
            case ASURA -> {
                float aura = window(t, Beats.ASURA_AURA, Beats.ASURA_AURA + 40, Beats.ASURA_HIT + 10, Beats.ASURA_HIT + 40);
                fx.vignette(0x3A0000, aura * 0.65F);
                fx.desaturate(aura * 0.35F);
                fx.grain(aura * 0.5F);
                fx.contrast(aura * 0.25F);
                fx.bloom(aura * 0.5F);
                fx.shake(aura * 0.08F);
                fx.impact(Impact.LIGHT, t - Beats.ASURA_MANIFEST, c.local(0, 1.4, 0), CRIMSON);
                fx.flash(CRIMSON, hit(t, Beats.ASURA_MANIFEST, 4) * 0.7F);
                fx.shake(hit(t, Beats.ASURA_MANIFEST, 10) * 0.7F);
                fx.punch(hit(t, Beats.ASURA_DASH, 3) * 1.1F);
                float s = t - Beats.ASURA_HIT;
                fx.impact(Impact.ULTIMATE, s, c.point, CRIMSON);
                fx.flash(WHITE, s >= 0 && s < 1 ? 1.6F : hit(t, Beats.ASURA_HIT + 1, 8) * 0.6F);
                fx.shake(hit(t, Beats.ASURA_HIT, 12) * 1.2F);
                fx.aberration(hit(t, Beats.ASURA_HIT, 14) * 1.5F);
            }
            case RENGOKU -> {
                float drain = window(t, Beats.RENGOKU_DRAIN, Beats.RENGOKU_DRAIN + 30, Beats.RENGOKU_HIT + 10, Beats.RENGOKU_HIT + 40);
                fx.haki(drain * 0.85F);
                fx.vignette(0x14001F, drain * 0.6F);
                fx.grain(drain * 0.5F);
                fx.bloom(drain * 0.4F);
                fx.shake(drain * 0.06F);
                fx.impact(new int[][]{{Impact.HAKI, 2}, {Impact.INVERT, 1}, {Impact.HAKI, 1}}, t - Beats.RENGOKU_CONQUER, c.local(0, 1.4, 0), VIOLET);
                fx.flash(0xB98CFF, hit(t, Beats.RENGOKU_CONQUER, 4) * 0.8F);
                fx.shake(hit(t, Beats.RENGOKU_CONQUER, 10) * 0.9F);
                fx.punch(hit(t, Beats.RENGOKU_DASH, 3) * 1.1F);
                float s = t - Beats.RENGOKU_HIT;
                fx.impact(Impact.KING, s, c.point, FIRE);
                fx.flash(WHITE, s >= 0 && s < 1 ? 1.7F : hit(t, Beats.RENGOKU_HIT + 1, 8) * 0.6F);
                fx.shake(hit(t, Beats.RENGOKU_HIT, 12) * 1.3F);
                fx.aberration(hit(t, Beats.RENGOKU_HIT, 14) * 1.5F);
            }
            case DRAGON -> {
                float aura = window(t, 10, 50, Beats.DRAGON_HIT + 20, Beats.DRAGON_HIT + 60);
                fx.haki(aura * 0.95F);
                fx.vignette(0x0E0016, aura * 0.7F);
                fx.grain(aura * 0.6F);
                fx.contrast(aura * 0.2F);
                fx.bloom(aura * 0.5F);
                fx.shake(aura * 0.07F);
                fx.impact(new int[][]{{Impact.WHITEOUT, 1}, {Impact.HAKI, 2}, {Impact.INVERT, 1}, {Impact.HAKI, 2}}, t - Beats.DRAGON_CONQUER, c.local(0, 1.4, 0), VIOLET);
                fx.flash(0xB98CFF, hit(t, Beats.DRAGON_CONQUER, 5) * 1.0F);
                fx.shake(hit(t, Beats.DRAGON_CONQUER, 14) * 1.1F);
                fx.punch(hit(t, Beats.DRAGON_LEAP, 6) * 1.0F);
                float s = t - Beats.DRAGON_HIT;
                fx.impact(Impact.KING, s, c.point, VIOLET);
                fx.flash(WHITE, s >= 0 && s < 2 ? 1.9F : hit(t, Beats.DRAGON_HIT + 2, 10) * 0.7F);
                fx.shake(hit(t, Beats.DRAGON_HIT, 16) * 1.5F);
                fx.aberration(hit(t, Beats.DRAGON_HIT, 18) * 1.8F);
                fx.desaturate(window(t, Beats.DRAGON_HIT + 16, Beats.DRAGON_HIT + 24, 250, 274) * 0.5F);
            }
        }
    }

    // ============================================================================================ helpers

    /** A near-straight cut through {@code at} along {@code along}: a long crescent with a slight belly. */
    static void cut(List<Ribbon> out, Vec3 at, Vec3 along, double length, float width, float reveal, float fade, int rgb) {
        if (reveal <= 0 || fade <= 0) {
            return;
        }
        Vec3 across = along.cross(perpTo(along)).normalize();
        double radius = length * 1.6;
        double half = Math.toDegrees(length * 0.5 / radius);
        Shapes.blade(out, at.subtract(across.scale(radius)), across, along, radius, -half, half, width, reveal, fade, rgb);
    }

    static void cutHaki(List<Ribbon> out, Vec3 at, Vec3 along, double length, float width, float reveal, float fade, int halo) {
        if (reveal <= 0 || fade <= 0) {
            return;
        }
        Vec3 across = along.cross(perpTo(along)).normalize();
        double radius = length * 1.6;
        double half = Math.toDegrees(length * 0.5 / radius);
        Shapes.hakiBlade(out, at.subtract(across.scale(radius)), across, along, radius, -half, half, width, reveal, fade, halo);
    }

    private static Vec3 perpTo(Vec3 v) {
        Vec3 n = v.cross(UP);
        return n.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : n.normalize();
    }

    /** Streaks along the dash path, fading over {@code life} ticks. */
    static void dashTrail(List<Ribbon> out, ClientCast c, float t, int dash, int life, int rgb, int count) {
        float k = t - dash;
        if (k < 0 || k > life) {
            return;
        }
        float a = 1.0F - k / life;
        for (int i = 0; i < count + 2; i++) {
            Vec3 off = c.right.scale((i - (count + 1) / 2.0) * 0.35).add(0, 0.5 + i * 0.3, 0);
            Shapes.streak(out, c.feet.add(off), c.exit.add(off), 0.4F, a, rgb, Ribbon.Style.GLOW);
        }
        Shapes.streak(out, c.feet.add(0, 1.0, 0), c.exit.add(0, 1.0, 0), 0.12F, a, WHITE, Ribbon.Style.GLOW);
    }

    static Vec3 projectile(ClientCast c, float t, int fire, double speed) {
        Vec3 from = c.eye.add(c.dir.scale(0.8)).add(0, -0.25, 0);
        return from.add(c.dir.scale(speed * Math.max(0, t - fire)));
    }

    /** A flying slash: a horizontal crescent riding the air cannon, with two fading afterimages behind it. */
    static void flyingSlash(List<Ribbon> out, ClientCast c, float t, int fire, int flight, double speed, double radius, float width, int rgb, double scale) {
        if (t < fire || t > fire + flight + 2) {
            return;
        }
        float fade = 1.0F - smooth((t - fire - flight + 2) / 4.0F);
        Vec3 side = c.dir.cross(UP);
        side = side.lengthSqr() < 1.0E-4 ? c.right : side.normalize();
        for (int ghost = 0; ghost < 3; ghost++) {
            float tt = t - ghost * 0.6F;
            if (tt < fire) {
                continue;
            }
            Vec3 p = projectile(c, tt, fire, speed);
            Vec3 centre = p.subtract(c.dir.scale(radius * 0.7 * scale));
            float a = fade * (ghost == 0 ? 1.0F : 0.35F / ghost);
            Shapes.blade(out, centre, c.dir, side, radius * scale, -75, 75, width, 1, a, rgb);
            Vec3 lift = side.cross(c.dir).normalize();
            Vec3 up = lift.y < 0 ? lift.scale(-1) : lift;
            Shapes.blade(out, p.add(up.scale(radius * 0.55 * scale)), up.scale(-1), side, radius * scale, -70, 70, width * 0.8F, 1, a * 0.9F, rgb);
        }
    }

    static void blackLightning(List<Ribbon> out, ClientCast c, float t, float on, int count, double reach) {
        if (on <= 0.05F) {
            return;
        }
        long tick = (long) t;
        Random rr = new Random(c.seed * 31L + tick * 977L);
        for (int i = 0; i < count; i++) {
            Vec3 a = c.local((rr.nextDouble() - 0.5) * 1.2, 0.6 + rr.nextDouble() * 1.4, 0.3 + rr.nextDouble() * 0.4);
            Vec3 b = a.add((rr.nextDouble() - 0.5) * reach * 2, (rr.nextDouble() - 0.3) * reach, (rr.nextDouble() - 0.5) * reach * 2);
            Shapes.bolt(out, a, b, tick * 13 + i, reach * 0.5, 0.25F, on * 0.5F, VIOLET, Ribbon.Style.GLOW);
            Shapes.bolt(out, a, b, tick * 13 + i, reach * 0.5, 0.04F, on * 0.85F, 0x020004, Ribbon.Style.INK);
        }
    }

    static void play(ClientCast c, RegistryObject<SoundEvent> sound, Vec3 at, float volume, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        // the swordsman hears his own technique at full presence, wherever the cinematic camera is
        if (c.mine) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 toward = at.subtract(cam);
            at = toward.lengthSqr() > 16 ? cam.add(toward.normalize().scale(4)) : at;
        }
        level.playLocalSound(at.x, at.y, at.z, sound.get(), SoundSource.PLAYERS, volume, pitch, false);
    }

    static void burst(ClientLevel level, ParticleOptions p, Vec3 at, int n, double spread, double speed) {
        for (int i = 0; i < n; i++) {
            level.addParticle(p, true, at.x + level.random.nextGaussian() * spread * 0.5, at.y + level.random.nextGaussian() * spread * 0.5,
                    at.z + level.random.nextGaussian() * spread * 0.5, level.random.nextGaussian() * speed, level.random.nextGaussian() * speed + speed * 0.3,
                    level.random.nextGaussian() * speed);
        }
    }

    static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), scale);
    }
}
