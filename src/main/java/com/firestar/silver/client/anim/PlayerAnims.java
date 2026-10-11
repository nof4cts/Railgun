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

    private PlayerAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, player -> new ModifierLayer<IAnimation>());
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
}
