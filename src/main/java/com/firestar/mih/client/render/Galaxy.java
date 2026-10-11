package com.firestar.mih.client.render;

import com.firestar.mih.MihMod;
import com.firestar.mih.client.ClientCast;
import com.firestar.mih.client.Casts;
import com.firestar.mih.client.stand.Mesh;
import com.firestar.mih.client.stand.StandPose;
import com.firestar.mih.client.stand.StandRenderer;
import com.firestar.mih.common.Beats;
import com.firestar.mih.config.ZConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The Universe Reset's middle act. Under cover of the white-out, the world around the user is captured (its exposed
 * blocks, every living thing), then the world is replaced by deep space and a spiral galaxy, and everything captured is
 * torn upward, flung into orbit and spun into the core until the singularity swallows it. Purely visual: the world
 * itself is never touched.
 */
public final class Galaxy {
    private static GlProgram program;
    private static int vao = -1;
    private static final long T0 = System.nanoTime();

    /** Galaxy centre above the user's feet, its radius, and when the world is hidden. */
    public static final double LIFT = 30.0;
    public static final float SCALE = 75.0F;
    public static final int HIDE_FROM = 50;
    public static final int HIDE_UNTIL = Beats.RESET_NEW;

    private Galaxy() {}

    // ------------------------------------------------------------------------------------------------ the capture

    static final class Piece {
        final BlockState state;
        final Vec3 origin;
        final float delay;
        final Vector3f axis;
        final float spin;
        final float r0;
        final float th0;
        final float yOff;
        final float k0;
        final float rise;

        Piece(BlockState state, Vec3 origin, float delay, Vector3f axis, float spin, float r0, float th0, float yOff, float k0, float rise) {
            this.state = state;
            this.origin = origin;
            this.delay = delay;
            this.axis = axis;
            this.spin = spin;
            this.r0 = r0;
            this.th0 = th0;
            this.yOff = yOff;
            this.k0 = k0;
            this.rise = rise;
        }
    }

    static final class Body {
        final int id;
        final Vec3 origin;
        final Piece path;

        Body(int id, Vec3 origin, Piece path) {
            this.id = id;
            this.origin = origin;
            this.path = path;
        }
    }

    public static final class Capture {
        final List<Piece> pieces = new ArrayList<>();
        final List<Body> bodies = new ArrayList<>();
        Vec3 centre;
        Vec3 core;
        float maxR = 1.0F;
    }

    public static void capture(ClientCast c) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        Capture cap = new Capture();
        cap.centre = c.point;
        cap.core = c.point.add(0, LIFT, 0);
        int R = ZConfig.CLIENT.galaxyRadius.get();
        int limit = ZConfig.CLIENT.galaxyBlocks.get();
        Random rnd = new Random(c.seed);
        BlockPos o = BlockPos.containing(c.point);
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -8; dy <= R; dy++) {
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dz * dz + Math.max(0, dy) * Math.max(0, dy) > R * R) {
                        continue;
                    }
                    m.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir() || s.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    if (exposed(level, m)) {
                        found.add(m.immutable());
                    }
                }
            }
        }
        Collections.shuffle(found, rnd);
        if (found.size() > limit) {
            found = found.subList(0, limit);
        }
        for (BlockPos p : found) {
            Vec3 at = Vec3.atCenterOf(p);
            cap.pieces.add(piece(level.getBlockState(p), at, cap, rnd, R));
        }
        for (Entity e : level.getEntities((Entity) null, new AABB(c.point, c.point).inflate(R + 10), e -> e instanceof LivingEntity && e.isAlive())) {
            if (e.getId() == c.casterId) {
                continue;
            }
            Piece path = piece(null, e.position().add(0, e.getBbHeight() * 0.5, 0), cap, rnd, R);
            cap.bodies.add(new Body(e.getId(), e.position(), path));
        }
        c.data = cap;
        MihMod.LOGGER.info("Universe reset: captured {} blocks and {} bodies", cap.pieces.size(), cap.bodies.size());
    }

    private static Piece piece(BlockState s, Vec3 at, Capture cap, Random rnd, int R) {
        Vec3 off = at.subtract(cap.centre);
        double flat = Math.sqrt(off.x * off.x + off.z * off.z);
        float delay = (float) (flat / R * 9.0 + rnd.nextFloat() * 4.0);
        Vector3f axis = new Vector3f(rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F, rnd.nextFloat() - 0.5F).normalize();
        float r0 = (float) (flat * 1.7 + 7.0 + rnd.nextFloat() * 6.0);
        cap.maxR = Math.max(cap.maxR, r0);
        float th = (float) Math.atan2(off.z, off.x) + (rnd.nextFloat() - 0.5F) * 0.5F;
        return new Piece(s, at, delay, axis, 90.0F + rnd.nextFloat() * 400.0F, r0, th, (rnd.nextFloat() - 0.5F) * 3.0F,
                0.0F, 2.0F + rnd.nextFloat() * 9.0F);
    }

    private static boolean exposed(ClientLevel level, BlockPos p) {
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            BlockState n = level.getBlockState(p.relative(d));
            if (n.isAir() || !n.canOcclude()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ motion

    private static float smooth(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Where a captured thing is at time t, and how big. */
    private static Vec3 where(Capture cap, Piece p, float t, float[] scaleOut) {
        float lift = smooth(Beats.RESET_LIFT + p.delay, Beats.RESET_LIFT + 24 + p.delay, t);
        Vec3 up = p.origin.add(0, lift * p.rise, 0).add(p.origin.subtract(cap.centre).multiply(1, 0, 1).scale(lift * 0.15));
        float k = p.r0 / cap.maxR;
        float fall = smooth(104 + k * 50.0F, Beats.RESET_SINGULARITY - 2, t);
        float r = p.r0 * (1.0F - 0.96F * fall);
        float th = p.th0 + (t - 60.0F) * 0.42F / (0.11F * r + 1.0F);
        Vec3 orbit = cap.core.add(Math.cos(th) * r, p.yOff * (r / p.r0) + Math.sin(th * 3.0 + p.r0) * 0.4 * (1 - fall), Math.sin(th) * r);
        float w = smooth(66 + p.delay * 0.6F, 104 + p.delay, t);
        scaleOut[0] = 1.0F - 0.88F * smooth(150, Beats.RESET_SINGULARITY, t) * (1.0F - k * 0.4F);
        return up.lerp(orbit, w);
    }

    /** The old world is gone from the screen (galaxy showing). */
    public static boolean hidesWorld(float partial) {
        ClientCast c = Casts.reset();
        if (c == null) {
            return false;
        }
        float t = c.time(partial);
        return t >= HIDE_FROM && t < HIDE_UNTIL;
    }

    // ------------------------------------------------------------------------------------------------ drawing

    public static void reload(ResourceManager rm) {
        RenderSystem.recordRenderCall(() -> {
            if (program != null) {
                program.close();
                program = null;
            }
            try {
                program = GlProgram.load(rm, "fullscreen.vsh", "galaxy.fsh");
            } catch (Exception e) {
                MihMod.LOGGER.error("Made in Heaven galaxy shader failed to compile", e);
            }
        });
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        ClientCast c = Casts.reset();
        if (c == null || !(c.data instanceof Capture cap)) {
            return;
        }
        float partial = event.getPartialTick();
        float t = c.time(partial);
        if (t < Beats.RESET_LIFT || t >= HIDE_UNTIL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        boolean hide = t >= HIDE_FROM;
        if (hide && program != null) {
            try {
                backdrop(event, cam, cap, t);
            } finally {
                restore(mc);
            }
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
        if (!hide) {
            return;
        }
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float[] sc = new float[1];
        Mesh trails = new Mesh(buffers.getBuffer(RenderType.lightning()), 0xF000F0);
        buffers.endBatch(RenderType.lightning());
        int i = 0;
        for (Piece p : cap.pieces) {
            Vec3 pos = where(cap, p, t, sc);
            if (sc[0] < 0.03F) {
                continue;
            }
            ps.pushPose();
            ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            float ang = (float) Math.toRadians(p.spin * (t - Beats.RESET_LIFT - p.delay) / 20.0F);
            if (ang > 0) {
                ps.mulPose(new Quaternionf().rotateAxis(ang, p.axis));
            }
            ps.scale(sc[0], sc[0], sc[0]);
            ps.translate(-0.5, -0.5, -0.5);
            mc.getBlockRenderer().renderSingleBlock(p.state, ps, buffers, 0xF000F0, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
            if (i++ % 4 == 0) {
                trail(trails, cap, p, t, cam, 0x9FD8FF, sc[0]);
            }
        }
        buffers.endBatch();

        EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        boolean shadows = mc.options.entityShadows().get();
        erd.setRenderShadow(false);
        try {
            for (Body b : cap.bodies) {
                Entity e = mc.level.getEntity(b.id);
                if (e == null) {
                    continue;
                }
                Vec3 pos = where(cap, b.path, t, sc);
                if (sc[0] < 0.05F) {
                    continue;
                }
                ps.pushPose();
                ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                float ang = (float) Math.toRadians(b.path.spin * 0.25F * (t - Beats.RESET_LIFT) / 20.0F);
                ps.mulPose(new Quaternionf().rotateAxis(ang, b.path.axis));
                ps.scale(sc[0], sc[0], sc[0]);
                erd.render(e, 0, -e.getBbHeight() * 0.5, 0, e.getYRot(), partial, ps, buffers, 0xF000F0);
                ps.popPose();
                trail(trails, cap, b.path, t, cam, 0xFFFFFF, sc[0] * 2.0F);
            }
            // the user hangs over the core, arms open, his Stand behind him: he alone is not falling
            Entity user = c.caster();
            if (user instanceof AbstractClientPlayer up) {
                Vec3 at = cap.core.add(0, 4.0 + Math.sin(t * 0.05) * 0.3, 0);
                float yaw = up.getYRot();
                ps.pushPose();
                ps.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                erd.render(up, 0, 0, 0, yaw, partial, ps, buffers, 0xF000F0);
                ps.popPose();
                buffers.endBatch();
                StandRenderer.Placement pl = new StandRenderer.Placement();
                double rad = Math.toRadians(yaw);
                Vec3 fwd = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
                pl.pos = at.add(fwd.scale(-1.4)).add(0, 0.9, 0);
                pl.yaw = yaw;
                pl.pose = StandPose.with(StandPose.SPREAD, StandPose.CLOCK, 80, StandPose.SCALE, 1.35F);
                pl.alpha = 1.0F;
                pl.time = t;
                pl.id = up.getId();
                StandRenderer.draw(ps, buffers, cam, pl);
            }
        } finally {
            erd.setRenderShadow(shadows);
        }
        buffers.endBatch();
        PoseStack id = new PoseStack();
        trails.flushGlow(id, buffers.getBuffer(RenderType.lightning()));
        buffers.endBatch(RenderType.lightning());
    }

    /** A streak of light behind a falling thing: where it was a few ticks ago to where it is. */
    private static void trail(Mesh mesh, Capture cap, Piece p, float t, Vec3 cam, int rgb, float width) {
        float[] tmp = new float[1];
        Vec3 a = where(cap, p, t - 3.5F, tmp).subtract(cam);
        Vec3 b = where(cap, p, t, tmp).subtract(cam);
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 0.04) {
            return;
        }
        Vec3 side = dir.cross(b).normalize().scale(0.18 * Math.max(0.2, width));
        mesh.glowQuadRaw(v(a.add(side)), v(a.subtract(side)), v(b.subtract(side)), v(b.add(side)), rgb, 0.0F, 0.55F);
    }

    private static Vector3f v(Vec3 p) {
        return new Vector3f((float) p.x, (float) p.y, (float) p.z);
    }

    private static void backdrop(RenderLevelStageEvent event, Vec3 cam, Capture cap, float t) {
        Minecraft mc = Minecraft.getInstance();
        if (vao < 0) {
            vao = GL30.glGenVertexArrays();
        }
        Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        Matrix4f proj = new Matrix4f(event.getProjectionMatrix());
        Matrix4f inv = proj.mul(view).invert();
        Vec3 rel = cam.subtract(cap.core);
        float collapse = smooth(Beats.RESET_PLUNGE, Beats.RESET_SINGULARITY, t);
        float spin = (float) (0.012 * (t - HIDE_FROM) + 0.0006 * Math.pow(Math.max(0, t - 120), 2.0));
        float core = 1.0F + 4.0F * collapse * collapse;

        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GL30.glBindVertexArray(vao);
        program.use();
        program.set("InvViewProj", inv);
        program.set("CamPos", (float) rel.x, (float) rel.y, (float) rel.z);
        program.set("Time", (System.nanoTime() - T0) / 1.0E9F % 3600.0F);
        program.set("Spin", spin);
        program.set("Collapse", collapse);
        program.set("Scale", SCALE);
        program.set("Fade", 1.0F);
        program.set("Core", core);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static void restore(Minecraft mc) {
        GL30.glBindVertexArray(0);
        BufferUploader.reset();
        GlStateManager._glUseProgram(0);
        ShaderInstance any = GameRenderer.getPositionTexShader();
        if (any != null) {
            any.clear();
        }
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @SuppressWarnings("unused")
    private static float unusedAxis() {
        return Axis.YP.rotationDegrees(0).w;
    }
}
