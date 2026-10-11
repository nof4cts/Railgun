package com.firestar.orbital.client.space;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.client.render.GlProgram;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Orbit. When a film cuts to space, its shot sets a {@link View} for the frame; this then paints the whole picture over
 * the world, at the end of the level render and before the post pipeline (so the sun and the lance still bloom and the
 * impact frames still bite).
 */
public final class Space {
    private Space() {}

    /** A camera in orbit and everything in the scene. Units: Earth radii for the planets, kilometres for the array. */
    public static final class View {
        public Vec3 eye = new Vec3(0, 0, 1.08);
        public Vec3 forward = new Vec3(0, 0, -1), up = new Vec3(0, 1, 0);
        public float fov = 60;
        public float fade = 1;
        public Vec3 sun = new Vec3(0.6, 0.35, -0.72).normalize();
        public Vec3 earth2 = Vec3.ZERO;
        public float earth2Radius;
        public float contact;
        public Vec3 breach = Vec3.ZERO, breachNormal = new Vec3(0, 0, 1);
        public float breachRadius, breachOpen;
        public Vec3 mark = new Vec3(0, 1, 0);
        public float markGlow;
        public boolean helios;
        public Vec3 localEye = new Vec3(0, 0, 8), axis = new Vec3(0, -1, 0);
        public float unfold, charge, lance;
        /** The lance in Earth radii, from the array down to the ground. */
        public Vec3 lanceFrom = Vec3.ZERO, lanceTo = Vec3.ZERO;
        public float lanceGlow;

        public View look(Vec3 eye, Vec3 at, Vec3 upHint) {
            this.eye = eye;
            this.forward = at.subtract(eye).normalize();
            this.up = upHint;
            return this;
        }
    }

    private static GlProgram program;
    private static int vao = -1;
    private static View view;
    private static long stamp = -1;
    private static long frame;
    private static final long T0 = System.nanoTime();

    public static void reload(ResourceManager rm) {
        RenderSystem.recordRenderCall(() -> {
            if (program != null) {
                program.close();
                program = null;
            }
            try {
                program = GlProgram.load(rm, "fullscreen.vsh", "space.fsh");
                OrbitalMod.LOGGER.info("Orbital Arsenal space shader compiled");
            } catch (Exception e) {
                OrbitalMod.LOGGER.error("Orbital Arsenal space shader failed to compile; orbit shots will show the world instead", e);
            }
        });
    }

    /** Called by a space shot each frame. */
    public static void show(View v) {
        view = v;
        stamp = frame;
    }

    public static boolean showing() {
        return view != null && stamp == frame;
    }

    /** Called once per rendered frame, before the camera is placed. */
    public static void newFrame() {
        frame++;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || !showing() || program == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        View v = view;
        try {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            if (vao < 0) {
                vao = GL30.glGenVertexArrays();
            }
            mc.getMainRenderTarget().bindWrite(true);
            GL30.glBindVertexArray(vao);
            program.use();
            Vec3 f = v.forward.normalize();
            Vec3 r = f.cross(v.up).normalize();
            Vec3 u = r.cross(f).normalize();
            set3("CamR", r);
            set3("CamU", u);
            set3("CamF", f);
            program.set("TanHalf", (float) Math.tan(Math.toRadians(v.fov) / 2));
            program.set("Aspect", (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight()));
            program.set("Time", (System.nanoTime() - T0) / 1.0E9F % 10000.0F);
            program.set("Fade", v.fade);
            set3("EyeP", v.eye);
            set3("SunDir", v.sun.normalize());
            program.set("Earth2", (float) v.earth2.x, (float) v.earth2.y, (float) v.earth2.z, v.earth2Radius);
            program.set("Contact", v.contact);
            program.set("Breach", (float) v.breach.x, (float) v.breach.y, (float) v.breach.z, v.breachRadius);
            set3("BreachN", v.breachNormal.normalize());
            program.set("BreachOpen", v.breachOpen);
            Vec3 m = v.mark.normalize();
            program.set("Mark", (float) m.x, (float) m.y, (float) m.z, v.markGlow);
            program.set("Mode", v.helios ? 1.0F : 0.0F);
            set3("LocalEye", v.localEye);
            set3("ArrayAxis", v.axis.normalize());
            program.set("Unfold", v.unfold);
            program.set("Charge", v.charge);
            program.set("Lance", v.lance);
            program.set("LanceA", (float) v.lanceFrom.x, (float) v.lanceFrom.y, (float) v.lanceFrom.z, v.lanceGlow);
            program.set("LanceB", (float) v.lanceTo.x, (float) v.lanceTo.y, (float) v.lanceTo.z, 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            // nothing of the world drawn after this should show through
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        } catch (Throwable t) {
            OrbitalMod.LOGGER.error("Orbital Arsenal space pass failed; disabling it until resources reload", t);
            program = null;
        } finally {
            GL30.glBindVertexArray(0);
            BufferUploader.reset();
            GlStateManager._glUseProgram(0);
            ShaderInstance any = GameRenderer.getPositionTexShader();
            if (any != null) {
                any.clear();
            }
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
    }

    private static void set3(String name, Vec3 v) {
        program.set(name, (float) v.x, (float) v.y, (float) v.z);
    }
}
