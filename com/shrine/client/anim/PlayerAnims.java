package com.shrine.client.anim;

import com.shrine.ShrineMod;
import com.shrine.common.Move;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.PlayerAssociatedAnimationData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

public final class PlayerAnims {
   public static final ResourceLocation LAYER_ID = ShrineMod.id("technique");

   private PlayerAnims() {
   }

   public static void init() {
      PlayerAnimationFactory.ANIMATION_DATA_FACTORY
         .registerFactory(LAYER_ID, 1000, player -> new ModifierLayer((IAnimation)null, new AbstractModifier[]{new SpeedModifier(1.0F)}));
   }

   public static void play(AbstractClientPlayer player, Move move) {
      ResourceLocation id = ShrineMod.id("move_" + move.key);
      KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(id);
      if (anim == null) {
         ShrineMod.LOGGER.warn("Missing player animation {}", id);
      } else {
         PlayerAssociatedAnimationData data = PlayerAnimationAccess.getPlayerAssociatedData(player);
         if (data.get(LAYER_ID) instanceof ModifierLayer<IAnimation> layerAny) {
            KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(anim)
               .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
               .setFirstPersonConfiguration(
                  new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true).setShowLeftItem(false)
               );
            layerAny.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), p, true);
         }
      }
   }
}
