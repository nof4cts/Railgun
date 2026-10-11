package com.firestar.mih.client.cinematic;

import com.firestar.mih.client.ClientCast;
import java.util.List;
import net.minecraft.util.Mth;

/** A technique's cinematic: its shots, played on the cast's own clock. */
public final class Film {
    public final ClientCast cast;
    public final List<Shot> shots;
    public final int end;

    public Film(ClientCast cast, List<Shot> shots) {
        this.cast = cast;
        this.shots = shots;
        this.end = cast.move.length;
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

    static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Slow in, slow out, like a camera operator's hands. */
    static float easeInOut(float x) {
        return x < 0.5F ? 4.0F * x * x * x : 1.0F - (float) Math.pow(-2.0F * x + 2.0F, 3.0) / 2.0F;
    }
}
