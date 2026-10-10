package com.hakari.idg.client;

import com.hakari.idg.ReserveBallEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** A chrome pachinko ball with a hot-pink comet tail. Pure geometry. */
public class ReserveBallRenderer extends EntityRenderer<ReserveBallEntity> {

    public ReserveBallRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ReserveBallEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        double cy = 0.175;
        Vec3 v = e.getDeltaMovement();
        for (int i = 9; i >= 1; i--) {
            float k = 1 - i / 10f;
            Vec3 o = v.scale(-i * 0.3);
            Draw.sphere(vc, m, o.x, o.y + cy, o.z, 0.16 * k, 6, 8, Draw.alpha(0xFFFF4FB8, 0.45f * k));
        }
        Draw.sphere(vc, m, 0, cy, 0, 0.14, 8, 12, (nx, ny, nz) -> Draw.lerp(0xFF9EA2B0, 0xFFFFFFFF, (float) (ny * 0.5 + 0.5)));
        Draw.sphere(vc, m, 0, cy, 0, 0.26, 8, 12, Draw.alpha(0xFFFF4FB8, 0.35f));
        Draw.sphere(vc, m, 0, cy, 0, 0.5, 8, 12, Draw.alpha(0xFFFF2D7A, 0.1f));
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ReserveBallEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
