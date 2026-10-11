package com.naoya.ps.client.cine;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Where the camera is, what it looks at, its field of view and its roll. Blends by position and aim point. */
public record CameraPose(Vec3 pos, Vec3 look, float fov, float roll) {
    public float yaw() {
        Vec3 d = look.subtract(pos);
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public float pitch() {
        Vec3 d = look.subtract(pos);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        return (float) -Math.toDegrees(Math.atan2(d.y, flat));
    }

    public CameraPose lerp(CameraPose o, float k) {
        return new CameraPose(pos.lerp(o.pos, k), look.lerp(o.look, k), Mth.lerp(k, fov, o.fov), Mth.lerp(k, roll, o.roll));
    }
}
