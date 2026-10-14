package com.gojo.rework.client;

import com.gojo.rework.Gojo2;
import com.gojo.rework.common.GMove;
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

/** Gojo's body for each technique: assets/gojo/player_animation/g_<technique>.json on a layer of its own. */
public final class GAnims {
    static final ResourceLocation LAYER = Gojo2.id("rework");

    private GAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1100, p -> new ModifierLayer<IAnimation>());
    }

    @SuppressWarnings("unchecked")
    static void play(AbstractClientPlayer p, GMove m) {
        Object o = PlayerAnimationAccess.getPlayerAssociatedData(p).get(LAYER);
        if (!(o instanceof ModifierLayer<?> l)) {
            return;
        }
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(Gojo2.id("g_" + m.key));
        if (anim == null) {
            return;
        }
        KeyframeAnimationPlayer a = new KeyframeAnimationPlayer(anim).setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true)
                        .setShowLeftItem(false));
        ((ModifierLayer<IAnimation>) l).replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.OUTQUAD), a, true);
    }
}
