package com.railgun.client;

import com.railgun.net.ShotPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ClientShots {
    public static final List<ShotFx> SHOTS = new ArrayList<>();

    public static void onShot(ShotPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        if (level == null || me == null) return;

        ShotFx fx = new ShotFx(p, level.getGameTime());
        SHOTS.add(fx);
        fx.burst(level);

        if (me.getId() == p.shooter) {
            ScreenFx.onFire(p.power);
            if (p.hits > 0) ScreenFx.onHit();
            // recoil kick
            me.setXRot(me.getXRot() - 3.5f * (0.6f + 0.4f * p.power));
            me.setYRot(me.getYRot() + (level.random.nextFloat() - 0.5f) * 1.6f);
        } else {
            Vec3 a = new Vec3(p.mx, p.my, p.mz), b = new Vec3(p.ex, p.ey, p.ez), ab = b.subtract(a);
            Vec3 pos = me.position().add(0, 0.9, 0);
            double t = ab.lengthSqr() < 1e-6 ? 0 : Mth.clamp(pos.subtract(a).dot(ab) / ab.lengthSqr(), 0, 1);
            double dBeam = pos.distanceTo(a.add(ab.scale(t)));
            ScreenFx.onNearby(dBeam, pos.distanceTo(b));
        }
    }

    private ClientShots() {}
}
