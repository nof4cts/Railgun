package com.firestar.mih.client.stand;

import static com.firestar.mih.client.stand.StandPose.*;

import com.firestar.mih.MihMod;
import com.firestar.mih.client.ClientCast;
import com.firestar.mih.client.ClientState;
import com.firestar.mih.client.Casts;
import com.firestar.mih.client.cinematic.Director;
import com.firestar.mih.client.render.Galaxy;
import com.firestar.mih.common.Move;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * Made in Heaven, drawn from scratch: the half-horse with a rider's body grown from its shoulders, a speedometer for a
 * face, clocks on its shoulders, arms, hands, the horse's brow and legs, a vine round the head, a ruff of feathers, the
 * cable arching from its back to the cut-off rear, blinkers and reins. Posed per frame from {@link StandPose}.
 */
public final class StandRenderer {
    public static final ResourceLocation WHITE = MihMod.id("textures/misc/white.png");

    static final int IVORY = 0xF1EEE6, IVORY_SHADE = 0xD8D2C4, JOINT = 0xB4AA97, BLACK = 0x141418, GOLD = 0xE8BE48,
            FACE = 0x0B0B10, MINT = 0x7CFFD4, RED = 0xFF3B3B, PINK = 0xFF9DCC, PINK_TIP = 0xFFE6F2, VINE = 0x4FBF63,
            YELLOW = 0xF3D34A, COLLAR = 0x2B2B35, HORSE = 0xF6F4EE, REIN = 0x3A2C25, FUR = 0xE6E0D2;

    private static final Map<Integer, float[]> SPIN = new HashMap<>();

    private StandRenderer() {}

    /** Everything needed to draw one Stand somewhere. */
    public static final class Placement {
        public Vec3 pos;
        public float yaw;
        public float[] pose;
        public float alpha;
        public float time;
        public int id;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack ps = event.getPoseStack();
        boolean any = false;
        for (Player p : mc.level.players()) {
            if (!(p instanceof AbstractClientPlayer ap)) {
                continue;
            }
            Placement pl = place(ap, partial);
            if (pl == null) {
                continue;
            }
            if (Casts.reset() != null && Galaxy.hidesWorld(partial)) {
                continue;
            }
            draw(ps, buffers, cam, pl);
            any = true;
        }
        if (any) {
            buffers.endBatch();
        }
    }

    /** Where and how a player's Stand stands this frame, or null if it isn't out. */
    public static Placement place(AbstractClientPlayer p, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientState.Info info = ClientState.get(p);
        ClientCast cast = Casts.of(p);
        boolean stand = cast != null && cast.move != Move.UNIVERSE_RESET && cast.time(partial) <= cast.move.length;
        float shown = info == null ? 0.0F : Mth.lerp(partial, info.shownO, info.shown);
        if (stand && cast.move != Move.PUNCH) {
            shown = Math.max(shown, Mth.clamp(cast.time(partial) / 3.0F, 0.0F, 1.0F));
        }
        ClientCast resetting = Casts.reset();
        if (shown < 0.01F && !stand && (resetting == null || resetting.casterId != p.getId())) {
            return null;
        }
        long now = mc.level.getGameTime();
        boolean accel = ClientState.accelerating(info, now);
        boolean da = info != null && info.daUntil > now;
        float[] pose = StandPose.idle(accel, da);
        float time = (now % 100000L) + partial;
        if (stand) {
            float t = cast.time(partial);
            pose = StandPose.during(cast.move, t, cast.seed);
            // hand the last ticks back to the standing pose
            float back = Mth.clamp((t - (cast.move.length - 4)) / 4.0F, 0.0F, 1.0F);
            if (back > 0) {
                pose = StandPose.lerp(pose, StandPose.idle(accel, da), back);
            }
        }
        // the end of the universe: Made in Heaven rises behind him, whether it was out or not
        if (resetting != null && resetting.casterId == p.getId() && !stand) {
            float t = resetting.time(partial);
            float in = Mth.clamp(t / 10.0F, 0.0F, 1.0F);
            pose = StandPose.lerp(pose, StandPose.with(StandPose.SPREAD, CLOCK, 80, SCALE, 1.35F), in);
            shown = Math.max(shown, in);
        }
        Placement pl = new Placement();
        float yaw = stand ? Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot) : Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        boolean firstPerson = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON && !Director.active();
        if (firstPerson && pose[OFF_F] > 0.3F) {
            pose = pose.clone();
            pose[OFF_F] += 0.45F;
            pose[OFF_R] += 0.45F;
            pose[ALPHA] *= 0.55F;
        }
        double rad = Math.toRadians(yaw);
        Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        float bob = (float) Math.sin(time * 0.09F + p.getId()) * 0.06F;
        pl.pos = p.getPosition(partial).add(right.scale(pose[OFF_R])).add(fwd.scale(pose[OFF_F])).add(0, pose[OFF_U] + bob, 0);
        pl.yaw = yaw + pose[YAW];
        pl.pose = pose;
        pl.alpha = shown * pose[ALPHA];
        pl.time = time;
        pl.id = p.getId();
        return pl;
    }

    /** The centre of the speedometer face and the way it faces, in world space, for the camera to find. */
    public static Vec3[] head(AbstractClientPlayer p, float partial) {
        Placement pl = place(p, partial);
        if (pl == null) {
            return null;
        }
        float[] P = pl.pose;
        PoseStack ps = new PoseStack();
        ps.translate(pl.pos.x, pl.pos.y, pl.pos.z);
        ps.mulPose(Axis.YP.rotationDegrees(-pl.yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(P[ROLL]));
        float s = 0.86F * P[SCALE];
        ps.scale(s, s, s);
        ps.translate(0, 1.1, -0.1);
        ps.mulPose(Axis.XP.rotationDegrees(P[BODY_PITCH]));
        ps.translate(0, 0.28, 0.16);
        ps.mulPose(Axis.YP.rotationDegrees(P[TORSO_YAW]));
        ps.mulPose(Axis.XP.rotationDegrees(P[TORSO_PITCH]));
        ps.mulPose(Axis.ZP.rotationDegrees(P[TORSO_ROLL]));
        ps.translate(0, 0.78F, 0);
        ps.mulPose(Axis.YP.rotationDegrees(P[HEAD_YAW]));
        ps.mulPose(Axis.XP.rotationDegrees(P[HEAD_PITCH]));
        Vector3f c = ps.last().pose().transformPosition(0, 0.205F, 0.17F, new Vector3f());
        Vector3f f = ps.last().pose().transformPosition(0, 0.205F, 1.17F, new Vector3f()).sub(c).normalize();
        return new Vec3[]{new Vec3(c.x, c.y, c.z), new Vec3(f.x, f.y, f.z)};
    }

    /** Both fists in world space (right, left), the same way {@link #draw} poses them: for light trails. */
    public static Vec3[] fists(Placement pl) {
        float[] P = pl.pose;
        Vec3[] out = new Vec3[2];
        boolean barrage = P[BARRAGE] > 0.01F;
        float bt = pl.time * 2.9F;
        for (int side = -1; side <= 1; side += 2) {
            boolean left = side > 0;
            PoseStack ps = new PoseStack();
            ps.translate(pl.pos.x, pl.pos.y, pl.pos.z);
            ps.mulPose(Axis.YP.rotationDegrees(-pl.yaw));
            ps.mulPose(Axis.ZP.rotationDegrees(P[ROLL]));
            float s = 0.86F * P[SCALE];
            ps.scale(s, s, s);
            ps.translate(0, 1.1, -0.1);
            ps.mulPose(Axis.XP.rotationDegrees(P[BODY_PITCH]));
            ps.translate(0, 0.28, 0.16);
            ps.mulPose(Axis.YP.rotationDegrees(P[TORSO_YAW]));
            ps.mulPose(Axis.XP.rotationDegrees(P[TORSO_PITCH]));
            ps.mulPose(Axis.ZP.rotationDegrees(P[TORSO_ROLL]));
            float pitch = left ? P[LA_PITCH] : P[RA_PITCH];
            float yaw = left ? P[LA_YAW] : P[RA_YAW];
            float roll = left ? P[LA_ROLL] : P[RA_ROLL];
            float elbow = left ? P[L_ELBOW] : P[R_ELBOW];
            if (barrage) {
                float ph = bt + (left ? (float) Math.PI : 0.0F);
                pitch += (float) Math.sin(ph) * 18.0F * P[BARRAGE];
                elbow = Mth.lerp(P[BARRAGE], elbow, 35.0F + 35.0F * (float) Math.sin(ph + 1.2F));
            }
            ps.translate(side * 0.31F, 0.62F, 0.0F);
            ps.mulPose(Axis.YP.rotationDegrees(left ? -yaw : yaw));
            ps.mulPose(Axis.ZP.rotationDegrees(left ? roll : -roll));
            ps.mulPose(Axis.XP.rotationDegrees(pitch));
            ps.translate(0, -0.34F, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-elbow));
            Vector3f f = ps.last().pose().transformPosition(0, -0.44F, 0, new Vector3f());
            out[left ? 1 : 0] = new Vec3(f.x, f.y, f.z);
        }
        return out;
    }

    public static void draw(PoseStack ps, MultiBufferSource.BufferSource buffers, Vec3 cam, Placement pl) {
        if (pl.alpha <= 0.01F) {
            return;
        }
        float[] P = pl.pose;
        Mesh mesh = new Mesh(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), 0xF000F0);
        mesh.alpha = Mth.clamp(pl.alpha, 0.0F, 1.0F);
        mesh.glow = P[GLOW];
        mesh.ink = 0.016F;
        // the clocks: an angle that runs at whatever speed the pose asks, smoothly across speed changes
        float[] spin = SPIN.computeIfAbsent(pl.id, k -> new float[]{System.nanoTime(), 0});
        long nowNs = System.nanoTime();
        float dt = Math.min(0.1F, (nowNs - (long) spin[0]) / 1.0E9F);
        spin[0] = nowNs;
        spin[1] = (spin[1] + dt * 36.0F * P[CLOCK]) % 360000.0F;
        float minute = spin[1];
        float hour = minute / 12.0F;

        ps.pushPose();
        ps.translate(pl.pos.x - cam.x, pl.pos.y - cam.y, pl.pos.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-pl.yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(P[ROLL]));
        float s = 0.86F * P[SCALE];
        ps.scale(s, s, s);

        Vector3f cableA = new Vector3f(), cableB = new Vector3f(), cableC = new Vector3f(), cableD = new Vector3f();
        Vector3f handR = new Vector3f(), handL = new Vector3f(), bitR = new Vector3f(), bitL = new Vector3f();

        // ---------------------------------------------------------------- the horse, sculpted smooth
        float R = 0.14F;
        ps.pushPose();
        ps.translate(0, 1.1, -0.1);
        ps.mulPose(Axis.XP.rotationDegrees(P[BODY_PITCH]));
        mesh.ellipsoid(ps, 0, 0, -0.08F, 0.30F, 0.29F, 0.52F, HORSE, R);
        mesh.ellipsoid(ps, 0, -0.02F, 0.40F, 0.27F, 0.30F, 0.27F, HORSE, R);
        // the cut where its hindquarters should be, sealed in gold
        mesh.ellipsoid(ps, 0, 0, -0.57F, 0.24F, 0.23F, 0.05F, IVORY_SHADE, 0.0F);
        mesh.ellipsoid(ps, 0, 0.04F, -0.61F, 0.10F, 0.10F, 0.03F, GOLD, 0.25F);
        // belly band and collar
        mesh.ellipsoid(ps, 0, 0, 0.0F, 0.305F, 0.295F, 0.045F, COLLAR, 0.0F);
        mesh.ellipsoid(ps, 0, 0.2F, 0.58F, 0.21F, 0.1F, 0.13F, COLLAR, 0.0F);
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(side * 0.29F, 0.0F, 0.42F);
            ps.mulPose(Axis.YP.rotationDegrees(side * 90.0F));
            mesh.clock(ps, 0.12F, hour, minute, 0.6F);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0, -0.06, -0.55);
        ps.last().pose().transformPosition(0, 0.25F, 0, cableD);
        ps.last().pose().transformPosition(0, 1.1F, -0.55F, cableC);
        ps.popPose();

        // neck and head
        ps.pushPose();
        ps.translate(0, 0.18, 0.52);
        ps.mulPose(Axis.XP.rotationDegrees(35 + P[NECK]));
        mesh.limb(ps, 0, -0.02F, 0, 0, 0.62F, 0, 0.14F, 0.11F, HORSE, R);
        for (int i = 0; i < 6; i++) {
            float y = 0.08F + i * 0.1F;
            mesh.limb(ps, 0, y, -0.1F, 0, y + 0.07F, -0.25F + i * 0.01F, 0.035F, 0.008F, BLACK, 0.0F);
        }
        ps.pushPose();
        ps.translate(0, 0.6, 0);
        ps.mulPose(Axis.XP.rotationDegrees(52 + P[HEAD_H]));
        mesh.ellipsoid(ps, 0, 0.08F, 0.17F, 0.13F, 0.14F, 0.27F, HORSE, R);
        mesh.ellipsoid(ps, 0, 0.0F, 0.48F, 0.10F, 0.10F, 0.13F, HORSE, 0.0F);
        mesh.ellipsoid(ps, 0, -0.04F, 0.58F, 0.085F, 0.05F, 0.04F, IVORY_SHADE, 0.0F);
        for (int side = -1; side <= 1; side += 2) {
            mesh.ellipsoid(ps, side * 0.122F, 0.1F, 0.2F, 0.014F, 0.035F, 0.045F, BLACK, 0.0F);
            mesh.ellipsoid(ps, side * 0.152F, 0.1F, 0.18F, 0.012F, 0.085F, 0.12F, BLACK, 0.0F);
            mesh.limb(ps, side * 0.07F, 0.18F, 0.0F, side * 0.09F, 0.36F, -0.03F, 0.04F, 0.012F, HORSE, 0.0F);
            mesh.ellipsoid(ps, side * 0.104F, 0.01F, 0.51F, 0.025F, 0.025F, 0.025F, GOLD, 0.0F);
        }
        ps.last().pose().transformPosition(-0.11F, 0.01F, 0.51F, bitR);
        ps.last().pose().transformPosition(0.11F, 0.01F, 0.51F, bitL);
        ps.pushPose();
        ps.translate(0, 0.215, 0.2);
        ps.mulPose(Axis.XP.rotationDegrees(-80));
        mesh.clock(ps, 0.1F, hour * 1.3F, minute * 1.3F, 0.7F);
        ps.popPose();
        ps.popPose();
        ps.popPose();

        // front legs
        for (int side = -1; side <= 1; side += 2) {
            boolean left = side > 0;
            float hip = left ? P[FL_HIP] : P[FR_HIP];
            float knee = left ? P[FL_KNEE] : P[FR_KNEE];
            ps.pushPose();
            ps.translate(side * 0.17F, -0.2F, 0.44F);
            ps.mulPose(Axis.XP.rotationDegrees(-hip));
            mesh.limb(ps, 0, 0.05F, 0, 0, -0.42F, 0, 0.095F, 0.07F, HORSE, R);
            ps.pushPose();
            ps.translate(0, -0.42, 0);
            ps.mulPose(Axis.XP.rotationDegrees(knee));
            mesh.limb(ps, 0, 0.0F, 0, 0, -0.4F, 0, 0.065F, 0.05F, HORSE, R);
            mesh.ellipsoid(ps, 0, -0.45F, 0.012F, 0.075F, 0.055F, 0.088F, GOLD, 0.2F);
            ps.popPose();
            ps.popPose();
        }

        // ---------------------------------------------------------------- the rider grown from its shoulders
        ps.pushPose();
        ps.translate(0, 0.28, 0.16);
        ps.mulPose(Axis.YP.rotationDegrees(P[TORSO_YAW]));
        ps.mulPose(Axis.XP.rotationDegrees(P[TORSO_PITCH]));
        ps.mulPose(Axis.ZP.rotationDegrees(P[TORSO_ROLL]));
        mesh.ellipsoid(ps, 0, 0.14F, 0, 0.165F, 0.2F, 0.12F, IVORY, R);
        mesh.ellipsoid(ps, 0, 0.5F, 0, 0.245F, 0.23F, 0.15F, IVORY, R);
        mesh.ellipsoid(ps, 0, 0.44F, 0.125F, 0.19F, 0.014F, 0.03F, GOLD, 0.0F);
        mesh.ellipsoid(ps, 0, 0.28F, 0.1F, 0.15F, 0.01F, 0.03F, IVORY_SHADE, 0.0F);
        for (int side = -1; side <= 1; side += 2) {
            mesh.ellipsoid(ps, side * 0.27F, 0.62F, 0, 0.1F, 0.095F, 0.1F, IVORY, R);
            ps.pushPose();
            ps.translate(side * 0.365F, 0.62F, 0.0F);
            ps.mulPose(Axis.YP.rotationDegrees(side * 90.0F));
            mesh.clock(ps, 0.11F, hour, minute, 0.7F);
            ps.popPose();
        }
        ps.last().pose().transformPosition(0, 0.55F, -0.15F, cableA);
        ps.last().pose().transformPosition(0, 1.45F, -0.55F, cableB);
        mesh.limb(ps, 0, 0.68F, 0, 0, 0.82F, 0, 0.06F, 0.055F, IVORY_SHADE, 0.0F);
        // the ruff of feathers, each a soft tapering plume
        for (int i = 0; i < 11; i++) {
            float a = -90.0F + i * 18.0F;
            ps.pushPose();
            ps.translate(0, 0.74F, -0.04F);
            ps.mulPose(Axis.YP.rotationDegrees(180 + a));
            ps.mulPose(Axis.XP.rotationDegrees(-55 - Math.abs(a) * 0.15F));
            mesh.ellipsoid(ps, 0, 0.15F, 0, 0.032F, 0.16F, 0.008F, PINK, 0.0F);
            mesh.ellipsoid(ps, 0, 0.3F, 0, 0.018F, 0.04F, 0.006F, PINK_TIP, 0.0F);
            ps.popPose();
        }

        // the head: a speedometer where the face should be
        ps.pushPose();
        ps.translate(0, 0.78F, 0);
        ps.mulPose(Axis.YP.rotationDegrees(P[HEAD_YAW]));
        ps.mulPose(Axis.XP.rotationDegrees(P[HEAD_PITCH]));
        mesh.ellipsoid(ps, 0, 0.23F, 0, 0.125F, 0.25F, 0.14F, IVORY, R);
        mesh.ellipsoid(ps, 0, 0.205F, 0.12F, 0.118F, 0.145F, 0.045F, FACE, 0.0F);
        ps.pushPose();
        ps.translate(0, 0.205F, 0.168F);
        for (int i = 0; i <= 12; i++) {
            float a = -120.0F + i * 20.0F;
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(-a));
            boolean major = i % 3 == 0;
            mesh.box(ps, -0.005F, 0.078F, 0.0F, 0.005F, major ? 0.105F : 0.095F, 0.003F, i >= 10 ? RED : 0xF4F4F4, 0.0F);
            mesh.glowBox(ps, -0.012F, 0.07F, 0.0F, 0.012F, major ? 0.11F : 0.1F, 0.008F, i >= 10 ? RED : MINT, 0.9F);
            ps.popPose();
        }
        float needle = -120.0F + 240.0F * Mth.clamp(P[DIAL], 0.0F, 1.0F) + (float) Math.sin(pl.time * 2.3F) * 4.0F * P[DIAL];
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(-needle));
        mesh.limb(ps, 0, -0.01F, 0.008F, 0, 0.095F, 0.008F, 0.008F, 0.003F, RED, 0.0F);
        mesh.glowBox(ps, -0.012F, -0.01F, 0.006F, 0.012F, 0.1F, 0.016F, RED, 0.8F);
        ps.popPose();
        mesh.disc(ps, 0, 0, 0.014F, 0.02F, 12, GOLD);
        mesh.glowDisc(ps, 0, 0, 0.02F, 0.14F, MINT, 0.35F);
        ps.popPose();
        for (int side = -1; side <= 1; side += 2) {
            mesh.ellipsoid(ps, side * 0.15F, 0.3F, 0.0F, 0.03F, 0.065F, 0.035F, YELLOW, 0.0F);
        }
        // the vine wound round the crown, bead by bead
        for (int i = 0; i < 22; i++) {
            double a = i * 0.62;
            float y = 0.3F + i * 0.011F;
            float rr = 0.15F - i * 0.0035F;
            mesh.ellipsoid(ps, (float) Math.cos(a) * rr, y, (float) Math.sin(a) * rr, 0.024F, 0.02F, 0.024F, VINE, 0.0F);
        }
        mesh.limb(ps, 0, 0.42F, 0, 0.02F, 0.54F, -0.02F, 0.03F, 0.01F, VINE, 0.0F);
        ps.popPose();

        // arms
        boolean barrage = P[BARRAGE] > 0.01F;
        float bt = pl.time * 2.9F;
        for (int side = -1; side <= 1; side += 2) {
            boolean left = side > 0;
            float pitch = left ? P[LA_PITCH] : P[RA_PITCH];
            float yaw = left ? P[LA_YAW] : P[RA_YAW];
            float roll = left ? P[LA_ROLL] : P[RA_ROLL];
            float elbow = left ? P[L_ELBOW] : P[R_ELBOW];
            if (barrage) {
                float ph = bt + (left ? (float) Math.PI : 0.0F);
                pitch += (float) Math.sin(ph) * 18.0F * P[BARRAGE];
                elbow = Mth.lerp(P[BARRAGE], elbow, 35.0F + 35.0F * (float) Math.sin(ph + 1.2F));
            }
            Vector3f hand = arm(ps, mesh, side, pitch, yaw, roll, elbow, hour, minute);
            (left ? handL : handR).set(hand);
        }
        // the barrage: a storm of fists, each one a frame of an arm moving faster than it can be drawn
        if (barrage) {
            float keep = mesh.alpha;
            int frame = (int) (pl.time * 1.5F);
            java.util.Random r = new java.util.Random(frame * 7919L + pl.id);
            float keepInk = mesh.ink;
            mesh.ink = 0.0F;
            for (int i = 0; i < 10; i++) {
                int side = r.nextBoolean() ? 1 : -1;
                mesh.alpha = keep * P[BARRAGE] * (0.18F + r.nextFloat() * 0.25F);
                ps.pushPose();
                ps.translate((r.nextFloat() - 0.5F) * 0.3F, (r.nextFloat() - 0.5F) * 0.3F, 0.0F);
                arm(ps, mesh, side, -78 - r.nextFloat() * 24, 12 + (r.nextFloat() - 0.5F) * 40, (r.nextFloat() - 0.5F) * 20,
                        r.nextFloat() * 30, hour, minute);
                ps.popPose();
            }
            mesh.alpha = keep;
            mesh.ink = keepInk;
        }
        ps.popPose();
        ps.popPose();
        ps.popPose();

        // the cable, the reins: drawn last, through the points the bones left behind
        PoseStack id = new PoseStack();
        Vector3f[] cable = new Vector3f[14];
        for (int i = 0; i < cable.length; i++) {
            float t = i / (float) (cable.length - 1);
            cable[i] = bezier(cableA, cableB, cableC, cableD, t);
        }
        mesh.tube(id, cable, 0.035F * s, IVORY_SHADE);
        if (P[REINS] > 0.5F) {
            mesh.tube(id, sag(bitR, handR), 0.012F * s, REIN);
            mesh.tube(id, sag(bitL, handL), 0.012F * s, REIN);
        }
        buffers.endBatch(RenderType.entityTranslucent(WHITE));
        if (mesh.hasInk()) {
            mesh.flushInk(buffers.getBuffer(RenderType.entityCutout(WHITE)));
            buffers.endBatch(RenderType.entityCutout(WHITE));
        }
        if (mesh.hasGlow()) {
            mesh.flushGlow(id, buffers.getBuffer(RenderType.lightning()));
            buffers.endBatch(RenderType.lightning());
        }
    }

    /** One arm, shoulder to fist, with its clocks. Returns the fist's centre in camera space. */
    private static Vector3f arm(PoseStack ps, Mesh mesh, int side, float pitch, float yaw, float roll, float elbow, float hour, float minute) {
        boolean left = side > 0;
        ps.pushPose();
        ps.translate(side * 0.31F, 0.62F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(left ? -yaw : yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(left ? roll : -roll));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        mesh.limb(ps, 0, 0.03F, 0, 0, -0.34F, 0, 0.078F, 0.066F, IVORY, 0.14F);
        mesh.ellipsoid(ps, 0, -0.34F, 0, 0.072F, 0.072F, 0.072F, JOINT, 0.0F);
        ps.translate(0, -0.34F, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-elbow));
        mesh.limb(ps, 0, 0.0F, 0, 0, -0.34F, 0, 0.068F, 0.058F, IVORY, 0.14F);
        // the fur on the forearms
        mesh.ellipsoid(ps, 0, -0.08F, 0, 0.082F, 0.055F, 0.082F, FUR, 0.0F);
        ps.pushPose();
        ps.translate(side * 0.068F, -0.2F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(side * 90.0F));
        mesh.clock(ps, 0.06F, hour * 2.0F, minute * 2.0F, 0.5F);
        ps.popPose();
        mesh.ellipsoid(ps, 0, -0.43F, 0.006F, 0.088F, 0.095F, 0.092F, IVORY, 0.2F);
        ps.pushPose();
        ps.translate(side * 0.088F, -0.43F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(side * 90.0F));
        mesh.clock(ps, 0.045F, hour * 3.0F, minute * 3.0F, 0.5F);
        ps.popPose();
        Vector3f fist = ps.last().pose().transformPosition(0, -0.44F, 0, new Vector3f());
        ps.popPose();
        return fist;
    }

    private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, Vector3f d, float t) {
        float u = 1 - t;
        return new Vector3f(a).mul(u * u * u).add(new Vector3f(b).mul(3 * u * u * t)).add(new Vector3f(c).mul(3 * u * t * t)).add(new Vector3f(d).mul(t * t * t));
    }

    private static Vector3f[] sag(Vector3f a, Vector3f b) {
        Vector3f[] out = new Vector3f[8];
        float len = a.distance(b);
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) (out.length - 1);
            out[i] = new Vector3f(a).lerp(b, t).sub(0, (float) Math.sin(Math.PI * t) * len * 0.12F, 0);
        }
        return out;
    }
}
