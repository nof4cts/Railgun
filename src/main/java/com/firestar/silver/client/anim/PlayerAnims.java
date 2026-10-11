package com.firestar.silver.client.anim;

import com.firestar.silver.SilverMod;
import com.firestar.silver.common.Move;
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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/** Body animation for every technique, from assets/silverinstinct/player_animation/<move>.json, on its own layer. */
public final class PlayerAnims {
    public static final ResourceLocation LAYER = SilverMod.id("technique");
    /** Under the techniques: the fighting stance an awakened fighter falls into when standing still. */
    public static final ResourceLocation STANCE = SilverMod.id("stance");
    private static final java.util.Set<Integer> IN_STANCE = new java.util.HashSet<>();

    private PlayerAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, player -> new ModifierLayer<IAnimation>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(STANCE, 900, player -> new ModifierLayer<IAnimation>());
    }

    public static void play(AbstractClientPlayer player, Move move) {
        play(player, move.key);
    }

    public static void play(AbstractClientPlayer player, String name) {
        ResourceLocation id = SilverMod.id(name);
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(id);
        if (anim == null) {
            SilverMod.LOGGER.debug("No player animation {}", id);
            return;
        }
        Object layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer instanceof ModifierLayer<?> modifier) {
            @SuppressWarnings("unchecked")
            ModifierLayer<IAnimation> l = (ModifierLayer<IAnimation>) modifier;
            KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(anim)
                    .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                    .setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true)
                            .setShowRightItem(true).setShowLeftItem(true));
            l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.OUTQUAD), p, true);
        }
    }

    private static final java.util.Map<Integer, String> MODE = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Integer> AIR = new java.util.HashMap<>();

    /** True if this awakened fighter is flying: the local player knows; for others, a long hang in the air. */
    public static boolean flying(AbstractClientPlayer p) {
        if (com.firestar.silver.client.ClientState.form(p) <= 0) {
            return false;
        }
        if (p == net.minecraft.client.Minecraft.getInstance().player) {
            return p.getAbilities().flying;
        }
        return AIR.getOrDefault(p.getId(), 0) > 12;
    }

    /**
     * Every client tick: awakened and standing still, he settles into the stance; flying, he takes the flight pose (or
     * hovers when he stops); moving or acting, he leaves them.
     */
    public static void tickStances() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            MODE.clear();
            AIR.clear();
            return;
        }
        for (net.minecraft.world.entity.player.Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer player)) {
                continue;
            }
            double dx = player.getX() - player.xo, dz = player.getZ() - player.zo, dy = player.getY() - player.yo;
            AIR.put(player.getId(), player.onGround() || Math.abs(dy) > 0.6 ? 0 : AIR.getOrDefault(player.getId(), 0) + 1);
            boolean awake = com.firestar.silver.client.ClientState.form(player) > 0 && com.firestar.silver.client.Casts.of(player) == null;
            String want = null;
            if (awake && flying(player)) {
                want = dx * dx + dz * dz > 0.04 ? "fly" : "hover";
            } else if (awake && dx * dx + dz * dz < 0.0009 && player.onGround() && !player.isCrouching() && !player.isSwimming()
                    && !player.isPassenger() && !player.isUsingItem()) {
                want = "stance";
            }
            Object layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(STANCE);
            if (!(layer instanceof ModifierLayer<?> modifier)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            ModifierLayer<IAnimation> l = (ModifierLayer<IAnimation>) modifier;
            boolean playing = l.getAnimation() != null && l.getAnimation().isActive();
            String cur = MODE.get(player.getId());
            if (want != null && (!want.equals(cur) || !playing)) {
                KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(SilverMod.id(want));
                if (anim != null) {
                    l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(want.equals("fly") ? 5 : 8, Ease.INOUTSINE),
                            new KeyframeAnimationPlayer(anim), true);
                    MODE.put(player.getId(), want);
                }
            } else if (want == null && MODE.remove(player.getId()) != null) {
                l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
            }
        }
    }
}
