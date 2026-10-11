package com.naoya.ps.client;

import com.naoya.ps.Naoya;
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

/** Naoya's body for each move, from assets/naoya/player_animation/<name>.json. */
public final class Anims {
    public static final ResourceLocation LAYER = Naoya.id("projection");

    private Anims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1100, player -> new ModifierLayer<IAnimation>());
    }

    public static void play(AbstractClientPlayer player, String name) {
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(Naoya.id(name));
        if (anim == null) {
            return;
        }
        Object layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer instanceof ModifierLayer<?> modifier) {
            @SuppressWarnings("unchecked")
            ModifierLayer<IAnimation> l = (ModifierLayer<IAnimation>) modifier;
            l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD),
                    new KeyframeAnimationPlayer(anim).setFirstPersonMode(FirstPersonMode.NONE), true);
        }
    }
}
