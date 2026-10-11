package com.firestar.mih.client.render;

import com.firestar.mih.client.stand.Mesh;
import com.firestar.mih.client.stand.StandRenderer;
import com.firestar.mih.server.KnifeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Pucci's knife in flight: a steel blade and dark grip along its line of travel, and a long streak of light behind it. */
public final class KnifeRenderer extends EntityRenderer<KnifeEntity> {
    public KnifeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(KnifeEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.xRotO, e.getXRot())));
        Mesh mesh = new Mesh(buffers.getBuffer(RenderType.entityTranslucent(StandRenderer.WHITE)), 0xF000F0);
        // blade along +z, the grip behind
        mesh.box(ps, -0.012F, -0.05F, 0.0F, 0.012F, 0.05F, 0.34F, 0xD8DDE6, 0.0F);
        mesh.box(ps, -0.008F, -0.03F, 0.34F, 0.008F, 0.03F, 0.42F, 0xEEF2F8, 0.0F);
        mesh.box(ps, -0.03F, -0.07F, -0.03F, 0.03F, 0.07F, 0.0F, 0x8A7A55, 0.0F);
        mesh.box(ps, -0.022F, -0.03F, -0.22F, 0.022F, 0.03F, -0.03F, 0x2A1E18, 0.0F);
        Matrix4f m = ps.last().pose();
        Vector3f tip = m.transformPosition(0, 0, 0.4F, new Vector3f());
        ps.popPose();
        // the streak: camera space, from the knife back along its flight
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() > 0.01) {
            Vector3f back = new Vector3f((float) -v.x, (float) -v.y, (float) -v.z).normalize().mul(3.2F);
            Vector3f tail = new Vector3f(tip).add(back);
            Vector3f dir = new Vector3f(tip).sub(tail);
            Vector3f side = new Vector3f(dir).cross(tip).normalize().mul(0.07F);
            mesh.glowQuadRaw(new Vector3f(tail).add(side), new Vector3f(tail).sub(side), new Vector3f(tip).sub(side), new Vector3f(tip).add(side),
                    0xCFE8FF, 0.0F, 0.9F);
            mesh.flushGlow(new PoseStack(), buffers.getBuffer(RenderType.lightning()));
        }
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(KnifeEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
