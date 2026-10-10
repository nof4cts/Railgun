package com.sixpaths.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sixpaths.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Draws every summon, rod, missile and Mesh-based effect: ink outline pass, then cel-shaded fill. */
public final class ModelRenderer {
    private static final double MAX_DIST = 160;

    static List<Entity> visible(Vfx.Ctx c) {
        Minecraft mc = Minecraft.getInstance();
        List<Entity> out = new ArrayList<>();
        if (mc.level == null) return out;
        Set<Integer> alive = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            boolean ours = e instanceof BeastEntity || e instanceof RodEntity || e instanceof MissileEntity;
            if (!ours || !e.isAlive()) continue;
            alive.add(e.getId());
            if (e.position().distanceTo(c.camPos) < MAX_DIST && !e.isInvisible()) out.add(e);
        }
        if (mc.level.getGameTime() % 100 == 0) SpineTrail.prune(alive);
        return out;
    }

    private static float brightness(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        int l = mc.level.getMaxLocalRawBrightness(BlockPos.containing(e.getEyePosition()));
        return 0.45f + 0.55f * l / 15f;
    }

    public static void solid(Vfx.Ctx c) {
        List<Entity> list = visible(c);
        List<PainFx.Modeled> models = new ArrayList<>();
        for (Vfx.Effect fx : Vfx.EFFECTS) {
            if (fx instanceof PainFx.Modeled md && !fx.dead(c.t) && md.visible(c.t)) models.add(md);
        }
        if (list.isEmpty() && models.isEmpty()) return;
        RenderSystem.enableCull();
        GL11.glCullFace(GL11.GL_FRONT);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        BufferBuilder b = Draw.begin(VertexFormat.Mode.QUADS);
        for (Entity e : list) pass(c, e, b, Mesh.OUTLINE);
        for (PainFx.Modeled md : models) passFx(c, md, b, Mesh.OUTLINE);
        Draw.end(b);
        GL11.glCullFace(GL11.GL_BACK);
        RenderSystem.disableCull();
        b = Draw.begin(VertexFormat.Mode.QUADS);
        for (Entity e : list) pass(c, e, b, Mesh.FILL);
        for (PainFx.Modeled md : models) passFx(c, md, b, Mesh.FILL);
        Draw.end(b);
        RenderSystem.enableCull();
        Mesh.ink = 0.035;
    }

    private static void pass(Vfx.Ctx c, Entity e, BufferBuilder b, int mode) {
        Vec3 origin = e.getPosition(c.partial);
        c.at(origin);
        float hurt = e instanceof BeastEntity be && be.hurtTime > 0 ? be.hurtTime / 10f : 0f;
        Mesh.ink = e instanceof RodEntity || e instanceof MissileEntity ? 0.015 : 0.045;
        Mesh.begin(mode, b, c.m(), c.camRel(origin), brightness(e), hurt);
        if (e instanceof RhinoEntity r) Models.rhino(r, c.partial);
        else if (e instanceof CentipedeEntity ce) Models.centipede(ce, origin, c.partial);
        else if (e instanceof HoundEntity h) Models.hound(h, c.partial);
        else if (e instanceof BirdEntity bd) Models.bird(bd, c.partial);
        else if (e instanceof RodEntity) Models.rod(heading(e));
        else if (e instanceof MissileEntity) Models.missile(heading(e));
        c.pop();
    }

    private static void passFx(Vfx.Ctx c, PainFx.Modeled md, BufferBuilder b, int mode) {
        Vec3 origin = md.origin(c.t);
        c.at(origin);
        Mesh.ink = 0.06;
        Mesh.begin(mode, b, c.m(), c.camRel(origin), 1f, 0f);
        md.model(c.t);
        c.pop();
    }

    static Vec3 heading(Entity e) {
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() > 1.0e-4) return v;
        Vec3 last = e.position().subtract(e.xo, e.yo, e.zo);
        return last.lengthSqr() > 1.0e-6 ? last : Draw.forward(e.getYRot(), e.getXRot());
    }

    /** Exhaust flames and rod glints. */
    public static void glow(Vfx.Ctx c) {
        for (Entity e : visible(c)) {
            if (e instanceof MissileEntity) {
                Vec3 p = e.getPosition(c.partial);
                Vec3 d = heading(e).normalize();
                Vec3 tail = p.add(d.scale(-0.5));
                Plasma.sun(tail, 0.35, 0xFFFF9A40, 0.9f);
                c.at(tail);
                Draw.cone(c.vc, c.m(), Vec3.ZERO, d.scale(-1), 1.6, 0.18, Draw.alpha(0xFFFFF0C0, 1f), Draw.alpha(0xFFFF6A20, 0f), 12);
                Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 0.5, 0xFFFF8A2A, 0.6f);
                c.pop();
            } else if (e instanceof RodEntity && e.getDeltaMovement().lengthSqr() > 0.05) {
                Vec3 p = e.getPosition(c.partial);
                Vec3 d = heading(e).normalize();
                c.at(p);
                Draw.ribbon(c.vc, c.m(), Vec3.ZERO, d.scale(-3), 0.07, 0.0, Draw.alpha(0xFFB48CFF, 0.6f), 0, c.camRel(p));
                c.pop();
            }
        }
    }

    /** Called every client tick: missiles leave smoke. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof MissileEntity && e.isAlive() && e.distanceTo(mc.player) < 96) {
                Vfx.add(new PainFx.Puff(e.position(), 0.35, 0xFF8A8890, 900));
            }
        }
    }

    private ModelRenderer() {}
}
