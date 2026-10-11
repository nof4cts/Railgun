package com.firestar.silver.client;

import com.firestar.silver.common.Move;
import com.firestar.silver.network.S2CCast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** One move as this client plays it: the cast's geometry plus its clock (server game time, so all agree). */
public final class ClientCast {
    public final Move move;
    public final int casterId;
    public final long start;
    public final int seed;
    /** Eye at the cast, the feet he is held on, the look, its flat forward, its right, the target point, where he ends up. */
    public final Vec3 eye;
    public final Vec3 feet;
    public final Vec3 dir;
    public final Vec3 fwd;
    public final Vec3 right;
    public final Vec3 point;
    public final Vec3 exit;
    public final int targetId;
    public final boolean mine;
    int lastTick = -1;
    /** Free per-cast storage for the choreography. */
    public Object data;

    ClientCast(S2CCast m) {
        this.move = m.move;
        this.casterId = m.casterId;
        this.start = m.startTick;
        this.seed = m.seed;
        this.eye = m.origin;
        this.feet = m.origin.subtract(0, 1.62, 0);
        this.dir = m.dir;
        Vec3 f = new Vec3(m.dir.x, 0, m.dir.z);
        this.fwd = f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
        this.right = new Vec3(-fwd.z, 0, fwd.x);
        this.point = m.point;
        this.exit = m.exit;
        this.targetId = m.targetId;
        Minecraft mc = Minecraft.getInstance();
        this.mine = mc.player != null && mc.player.getId() == m.casterId;
    }

    public float time(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0.0F : (float) (level.getGameTime() - start) + partial;
    }

    public boolean over() {
        return time(0.0F) > move.length + 2;
    }

    public Entity caster() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(casterId);
    }

    public LivingEntity target() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null || targetId < 0 || !(level.getEntity(targetId) instanceof LivingEntity l) ? null : l;
    }

    public boolean hasTarget() {
        return targetId >= 0;
    }

    /** Where the target's chest is now (or the aimed point if there is none). */
    public Vec3 targetCentre(float partial) {
        LivingEntity t = target();
        return t == null ? point : t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
    }

    public Vec3 casterFeet(float partial) {
        Entity e = caster();
        return e != null ? e.getPosition(partial) : feet;
    }

    public boolean isCaster(Player p) {
        return p.getId() == casterId;
    }

    /** A point in the user's own frame: right, up, forward from his feet at the cast. */
    public Vec3 local(double r, double u, double f) {
        return feet.add(right.scale(r)).add(0, u, 0).add(fwd.scale(f));
    }
}
