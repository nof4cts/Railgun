package com.firestar.sage.client;

import com.firestar.sage.SageMod;
import com.firestar.sage.common.SMove;
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
 * The sage's body: each rite from assets/celestial/player_animation/<name>.json on its own layer, and under it the
 * calm of a mantled sage (standing, hovering, drifting forward in flight).
 */
public final class SageAnims {
    static final ResourceLocation RITE = SageMod.id("rite");
    static final ResourceLocation CALM = SageMod.id("calm");
    private static final Map<Integer, String> MODE = new HashMap<>();
    private static final Map<Integer, Integer> AIR = new HashMap<>();

    private SageAnims() {}

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(RITE, 1150, p -> new ModifierLayer<IAnimation>());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(CALM, 960, p -> new ModifierLayer<IAnimation>());
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer p, ResourceLocation id) {
        Object o = PlayerAnimationAccess.getPlayerAssociatedData(p).get(id);
        return o instanceof ModifierLayer<?> m ? (ModifierLayer<IAnimation>) m : null;
    }

    private static void start(ModifierLayer<IAnimation> l, String name, int fade) {
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(SageMod.id(name));
        if (anim == null || l == null) {
            return;
        }
        KeyframeAnimationPlayer a = new KeyframeAnimationPlayer(anim).setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true)
                        .setShowLeftItem(false));
        l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.OUTQUAD), a, true);
    }

    static void play(AbstractClientPlayer p, SMove m, float size) {
        String name = switch (m) {
            case STAFF -> "staff_" + Math.max(0, Math.min(2, (int) size));
            case UNMANTLE -> "unmantle";
            default -> m.key;
        };
        start(layer(p, RITE), name, 2);
    }

    /** Each client tick: a mantled sage at rest stands in calm; in the air he hovers, or drifts forward. */
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
            boolean up = SageForms.ascended(p) && SageCasts.of(p) == null && p.getMainHandItem().isEmpty();
            boolean air = p == mc.player ? p.getAbilities().flying : AIR.getOrDefault(p.getId(), 0) > 12;
            String want = null;
            if (up && air) {
                want = dx * dx + dz * dz > 0.04 ? "drift" : "float";
            } else if (up && dx * dx + dz * dz < 0.0009 && p.onGround() && !p.isCrouching()) {
                want = "calm";
            }
            ModifierLayer<IAnimation> l = layer(p, CALM);
            if (l == null) {
                continue;
            }
            boolean playing = l.getAnimation() != null && l.getAnimation().isActive();
            String cur = MODE.get(p.getId());
            if (want != null && (!want.equals(cur) || !playing)) {
                start(l, want, 6);
                MODE.put(p.getId(), want);
            } else if (want == null && cur != null) {
                l.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(6, Ease.OUTQUAD), null);
                MODE.remove(p.getId());
            }
        }
    }
}
