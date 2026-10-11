package com.firestar.orbital.client.cinematic;

import java.util.List;
import net.minecraft.util.Mth;

/** A cinematic: its shots on a clock, a title card, and the lines spoken over it. */
public final class Film {
    @FunctionalInterface
    public interface Clock {
        float time(float partial);
    }

    /** The name card: small spaced style line, big kanji, title under a stroke. Shown from start for length ticks. */
    public record Card(String style, String kanji, String title, int color, int start, int length) {}

    public final Clock clock;
    public final List<Shot> shots;
    public final int end;
    public final Card card;
    public final String[][] lines;
    public final int blendIn, blendOut;

    public Film(Clock clock, List<Shot> shots, int end, Card card, String[][] lines, int blendIn, int blendOut) {
        this.clock = clock;
        this.shots = shots;
        this.end = end;
        this.card = card;
        this.lines = lines;
        this.blendIn = blendIn;
        this.blendOut = blendOut;
    }

    CameraPose pose(float t) {
        int index = 0;
        for (int i = 0; i < shots.size(); i++) {
            if (t >= shots.get(i).start()) {
                index = i;
            }
        }
        Shot shot = shots.get(index);
        CameraPose pose = evaluate(shot, t);
        if (index > 0 && shot.blend() > 0 && t - shot.start() < shot.blend()) {
            Shot prev = shots.get(index - 1);
            CameraPose from = evaluate(prev, Math.min(t, prev.end()));
            pose = from.lerp(pose, smooth((t - shot.start()) / shot.blend()));
        }
        return pose;
    }

    private static CameraPose evaluate(Shot s, float t) {
        float k = Mth.clamp((t - s.start()) / Math.max(1.0F, s.end() - s.start()), 0.0F, 1.0F);
        return s.path().pose(easeInOut(k), t);
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Slow in, slow out, like a camera operator's hands. */
    static float easeInOut(float x) {
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0) / 2.0F;
    }
}
