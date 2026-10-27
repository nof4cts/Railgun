package com.ironlung.mission;

/** Fixed positions on the hull where the external camera can be slewed. Pure Java (shared). */
public enum CameraMount {
    FORE("FORE", 3.2, 0.0, 0.4, 0.0, -0.10),
    PORT("PORT", 0.5, -2.0, 0.2, -Math.PI / 2, -0.15),
    STARBOARD("STBD", 0.5, 2.0, 0.2, Math.PI / 2, -0.15),
    AFT("AFT", -3.4, 0.0, 0.4, Math.PI, -0.10),
    KEEL("KEEL", 0.8, 0.0, -1.8, 0.0, -1.05);

    public final String label;
    public final double forward, side, up, yawOffset, pitchOffset;

    CameraMount(String label, double forward, double side, double up, double yawOffset, double pitchOffset) {
        this.label = label;
        this.forward = forward;
        this.side = side;
        this.up = up;
        this.yawOffset = yawOffset;
        this.pitchOffset = pitchOffset;
    }

    public static final double MAX_PAN = Math.toRadians(70);
    public static final double MAX_TILT = Math.toRadians(40);

    public static CameraMount byId(int id) {
        CameraMount[] v = values();
        return v[Math.floorMod(id, v.length)];
    }

    /**
     * World pose of the camera. heading: 0 = north (-z), clockwise. Returns {x, y, z, yaw, pitch}, y up.
     */
    public double[] pose(double subX, double subZ, double heading, double depth, double pan, double tilt) {
        double fx = Math.sin(heading), fz = -Math.cos(heading);
        double rx = Math.cos(heading), rz = Math.sin(heading);
        double x = subX + fx * forward + rx * side;
        double z = subZ + fz * forward + rz * side;
        double y = -depth + up;
        double yaw = heading + yawOffset + Math.max(-MAX_PAN, Math.min(MAX_PAN, pan));
        double pitch = pitchOffset + Math.max(-MAX_TILT, Math.min(MAX_TILT, tilt));
        pitch = Math.max(-1.45, Math.min(0.9, pitch));
        return new double[]{x, y, z, yaw, pitch};
    }
}
