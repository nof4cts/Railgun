package com.firestar.silver.client;

import com.firestar.silver.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The Zenith theme (the author's own track): starts on the summit of the awakening, plays while the player stays in
 * Silver Zenith (looping if the fight runs long), and fades out on revert or a change of style. Only the player who
 * awakened hears their own; it plays on the Music slider and holds back the vanilla music while it runs.
 */
public final class ZenithTheme extends AbstractTickableSoundInstance {
    private static ZenithTheme playing;
    private boolean ending;

    private ZenithTheme() {
        super(ModSounds.ZENITH_THEME.get(), SoundSource.MUSIC, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    /** The local player's awakening changed. */
    static void form(int form) {
        Minecraft mc = Minecraft.getInstance();
        if (form == 2 && (playing == null || playing.isStopped() || playing.ending)) {
            mc.getMusicManager().stopPlaying();
            playing = new ZenithTheme();
            mc.getSoundManager().play(playing);
        } else if (form != 2 && playing != null) {
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
        if (!inZenith) {
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
