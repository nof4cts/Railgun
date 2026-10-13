package com.firestar.rose.client;

import com.firestar.rose.RoseMod;
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
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The reaper's body: each technique from assets/requiem/player_animation/<name>.json on its own layer, and under it the
 * idle poses of an ascended reaper (the scythe-ready stance, the hover, the glide).
 */
public final class RoseAnims {
    static final ResourceLocation LAYER = RoseMod.id("technique");
    static final ResourceLocation POSE = RoseMod.id("pose");
    private static final Map<Integer, String> MODE = new HashMap<>();
    private static final Map<Integer, Integer> AIR = new HashMap<>();

    private RoseAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1100, p -> new ModifierLayer<IAnimation>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(POSE, 950, p -> new ModifierLayer<IAnimation>());
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer p, ResourceLocation id) {
        Object o = PlayerAnimationAccess.getPlayerAssociatedData(p).get(id);
        return o instanceof ModifierLayer<?> m ? (ModifierLayer<IAnimation>) m : null;
    }

    public static void play(AbstractClientPlayer p, String name) {
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(RoseMod.id(name));
        ModifierLayer<IAnimation> l = layer(p, LAYER);
        if (anim == null || l == null) {
            return;
        }
        KeyframeAnimationPlayer a = new KeyframeAnimationPlayer(anim).setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true)
                        .setShowLeftItem(false));
        l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), a, true);
    }

    public static boolean flying(AbstractClientPlayer p) {
        if (!RoseForms.ascended(p)) {
            return false;
        }
        if (p == Minecraft.getInstance().player) {
            return p.getAbilities().flying;
        }
        return AIR.getOrDefault(p.getId(), 0) > 12;
    }

    /** Each client tick: an ascended reaper standing still settles into the stance; flying, he hovers or glides. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MODE.clear();
            AIR.clear();
            return;
        }
        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer p)) {
                continue;
            }
            double dx = p.getX() - p.xo, dz = p.getZ() - p.zo, dy = p.getY() - p.yo;
            AIR.put(p.getId(), p.onGround() || Math.abs(dy) > 0.6 ? 0 : AIR.getOrDefault(p.getId(), 0) + 1);
            boolean up = RoseForms.ascended(p) && RoseCasts.of(p) == null && p.getMainHandItem().isEmpty();
            String want = null;
            if (up && flying(p)) {
                want = dx * dx + dz * dz > 0.04 ? "glide" : "hover";
            } else if (up && dx * dx + dz * dz < 0.0009 && p.onGround() && !p.isCrouching() && !p.isSwimming() && !p.isPassenger()) {
                want = "stance";
            } else if (up && p.onGround() && !p.isCrouching()) {
                want = "carry";
            }
            ModifierLayer<IAnimation> l = layer(p, POSE);
            if (l == null) {
                continue;
            }
            boolean playing = l.getAnimation() != null && l.getAnimation().isActive();
            String cur = MODE.get(p.getId());
            if (want != null && (!want.equals(cur) || !playing)) {
                KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(RoseMod.id(want));
                if (anim != null) {
                    l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(want.equals("glide") ? 6 : 8, Ease.INOUTSINE),
                            new KeyframeAnimationPlayer(anim), true);
                    MODE.put(p.getId(), want);
                }
            } else if (want == null && MODE.remove(p.getId()) != null) {
                l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(6, Ease.INOUTSINE), null);
            }
        }
    }
}
