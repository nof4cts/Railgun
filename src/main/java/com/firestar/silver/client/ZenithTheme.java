package com.firestar.silver.client;

import com.firestar.silver.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The Zenith theme (the author's own track): starts the moment the Silver Zenith awakening begins, scores the whole
 * cutscene in place of the aura sounds, plays while the player stays in Silver Zenith (looping if the fight runs long),
 * and fades out on revert or a change of style. Only the player who awakened hears their own. It plays on the Master
 * slider (so a muted Music slider cannot silence it) and holds back the vanilla music while it runs.
 */
public final class ZenithTheme extends AbstractTickableSoundInstance {
    private static ZenithTheme playing;
    private boolean ending;
    /** Ticks the theme keeps playing before the Zenith form has to be confirmed (the awakening cutscene). */
    private int grace;

    private ZenithTheme() {
        super(ModSounds.ZENITH_THEME.get(), SoundSource.MASTER, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    private static boolean on() {
        return playing != null && !playing.isStopped() && !playing.ending
                && Minecraft.getInstance().getSoundManager().isActive(playing);
    }

    private static void start(int grace) {
        Minecraft mc = Minecraft.getInstance();
        if (playing != null && !playing.isStopped()) {
            mc.getSoundManager().stop(playing);
        }
        mc.getMusicManager().stopPlaying();
        playing = new ZenithTheme();
        playing.grace = grace;
        mc.getSoundManager().play(playing);
    }

    /** The local player began the Silver Zenith awakening: the theme starts now, over the cutscene. */
    static void begin(int grace) {
        if (on()) {
            playing.grace = Math.max(playing.grace, grace);
        } else {
            start(grace);
        }
    }

    /** The local player's awakening changed. */
    static void form(int form) {
        if (form == 2) {
            if (!on()) {
                start(0);
            }
        } else if (playing != null && playing.grace <= 0) {
            playing.ending = true;
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            stop();
            return;
        }
        boolean inZenith = ClientState.form(mc.player) == 2 && com.firestar.styles.client.ClientStyles.mine(com.firestar.styles.Style.INSTINCT);
        if (grace > 0) {
            grace--;
        } else if (!inZenith) {
            ending = true;
        }
        if (ending) {
            volume -= 0.025F;
            if (volume <= 0.02F) {
                stop();
            }
        } else {
            // keep the game's own music from starting over the theme
            mc.getMusicManager().stopPlaying();
        }
    }
}
