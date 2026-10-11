package com.firestar.santoryu.client;

import com.firestar.santoryu.common.Move;
import com.firestar.santoryu.network.S2CCast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One technique as this client plays it: the cast's geometry plus its clock (server game time, so all agree). */
public final class ClientCast {
    public final Move move;
    public final int casterId;
    public final long start;
    public final int seed;
    /** Eye at the cast, the feet he is held on, the flat facing, its right, the target point, where he ends up. */
    public final Vec3 eye;
    public final Vec3 feet;
    public final Vec3 dir;
    public final Vec3 fwd;
    public final Vec3 right;
    public final Vec3 point;
    public final Vec3 exit;
    public final ItemStack bladeRight;
    public final ItemStack bladeLeft;
    public final ItemStack bladeMouth;
    public final boolean mine;
    int lastTick = -1;

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
        this.bladeRight = m.right;
        this.bladeLeft = m.left;
        this.bladeMouth = m.mouth;
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

    /** Where the swordsman's feet are right now: held on the cast spot, then wherever the dash put him. */
    public Vec3 casterFeet(float partial) {
        Entity e = caster();
        return e != null ? e.getPosition(partial) : feet;
    }

    public boolean isCaster(Player p) {
        return p.getId() == casterId;
    }

    /** A point in the swordsman's own frame: right, up, forward from his held feet. */
    public Vec3 local(double r, double u, double f) {
        return feet.add(right.scale(r)).add(0, u, 0).add(fwd.scale(f));
    }

    public static final Vec3 UP = new Vec3(0, 1, 0);
}
