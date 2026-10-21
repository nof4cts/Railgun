package com.yuji.client;

import com.yuji.Y;
import com.yuji.common.YMove;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** His body: one Player Animator layer per player, with a speed control so his pose freezes with the hit-stop. */
public final class YAnims {
    public static final ResourceLocation LAYER = Y.id("moves");
    private static final Map<AbstractClientPlayer, SpeedModifier> SPEED = new WeakHashMap<>();
    private static final Map<Integer, Double> FROZEN = new HashMap<>();

    private YAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1100, player -> {
            SpeedModifier sp = new SpeedModifier(1.0f);
            SPEED.put(player, sp);
            return new ModifierLayer<IAnimation>(null, new AbstractModifier[]{sp});
        });
    }

    public static void play(AbstractClientPlayer player, YMove move) {
        ResourceLocation id = Y.id("yuji_" + move.name().toLowerCase(Locale.ROOT));
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(id);
        if (anim == null) {
            Y.LOG.warn("[yuji] missing player animation {}", id);
            return;
        }
        try {
            if (PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER) instanceof ModifierLayer<?> l0) {
                @SuppressWarnings("unchecked")
                ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) l0;
                KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(anim)
                        .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                        .setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true)
                                .setShowRightItem(true).setShowLeftItem(false));
                layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.OUTQUAD), p, true);
            }
        } catch (Throwable t) {
            Y.LOG.warn("[yuji] couldn't animate {}", move, t);
        }
    }

    /** Hold the pose still for the length of a hit-stop. */
    public static void freeze(int entityId, double seconds) {
        if (!Y.hitStop) return;
        FROZEN.put(entityId, YFx.time() + seconds);
    }

    /** Every frame: frozen poses stay frozen, the rest run at full speed. */
    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = YFx.time();
        for (Map.Entry<AbstractClientPlayer, SpeedModifier> e : SPEED.entrySet()) {
            Double until = FROZEN.get(e.getKey().getId());
            e.getValue().speed = until != null && until > now ? 0.0f : 1.0f;
        }
        FROZEN.values().removeIf(u -> u < now - 1);
    }

    static boolean isPlayer(Entity e) {
        return e instanceof AbstractClientPlayer;
    }
}
