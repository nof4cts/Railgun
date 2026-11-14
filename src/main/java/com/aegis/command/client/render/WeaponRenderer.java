package com.aegis.command.client.render;

import com.aegis.command.AegisCommand;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders the arsenal weapons from two baked JSON models each: a metal body tinted by the chosen
 * finish, and an energy layer drawn full-bright plus an additive bloom pass, tinted by the energy
 * colour. Animation poses are applied around the model centre.
 */
public class WeaponRenderer extends BlockEntityWithoutLevelRenderer {
    private static WeaponRenderer instance;
    private static final RandomSource RAND = RandomSource.create();
    public static final ResourceLocation FLARE = AegisCommand.id("textures/entity/energy_bolt.png");
    public static final int FULL_BRIGHT = 0xF000F0;

    public static WeaponRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new WeaponRenderer(mc);
        }
        return instance;
    }

    private WeaponRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    public static ResourceLocation bodyModel(WeaponType t) { return AegisCommand.id("weapon/" + t.id + "_body"); }
    public static ResourceLocation glowModel(WeaponType t) { return AegisCommand.id("weapon/" + t.id + "_glow"); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        WeaponType type = AegisWeaponItem.typeOf(stack);
        if (type == null) return;
        boolean firstPerson = ctx.firstPerson();
        WeaponAnimator.Pose pose = (firstPerson ? WeaponAnimator.HAND : WeaponAnimator.STATIC).pose(type, firstPerson);
        render(type, AegisWeaponItem.finish(stack), AegisWeaponItem.energy(stack), pose, ps, buffers, light, overlay);
    }

    public void render(WeaponType type, int finish, int energy, WeaponAnimator.Pose pose, PoseStack ps,
                       MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.translate(pose.tx, pose.ty, pose.tz);
        ps.mulPose(Axis.YP.rotationDegrees(pose.ry));
        ps.mulPose(Axis.XP.rotationDegrees(pose.rx));
        ps.mulPose(Axis.ZP.rotationDegrees(pose.rz));
        ps.translate(-0.5, -0.5, -0.5);

        int fc = WeaponType.FINISH_COLORS[Math.floorMod(finish, WeaponType.FINISH_COLORS.length)];
        float fr = Math.min(1f, ((fc >> 16) & 255) / 255f * 1.45f);
        float fg = Math.min(1f, ((fc >> 8) & 255) / 255f * 1.45f);
        float fb = Math.min(1f, (fc & 255) / 255f * 1.45f);
        int ec = WeaponType.ENERGY_COLORS[Math.floorMod(energy, WeaponType.ENERGY_COLORS.length)];
        float er = ((ec >> 16) & 255) / 255f, eg = ((ec >> 8) & 255) / 255f, eb = (ec & 255) / 255f;

        BakedModel body = mc.getModelManager().getModel(bodyModel(type));
        BakedModel glow = mc.getModelManager().getModel(glowModel(type));

        drawModel(body, ps, buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), fr, fg, fb, 1f, light, overlay);

        float g = Mth.clamp(pose.glow + pose.coreFlash * 0.8f, 0.05f, 2f);
        float base = Math.min(1f, 0.35f + 0.65f * Math.min(1f, g));
        drawModel(glow, ps, buffers.getBuffer(RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)),
                Math.min(1, er * base + 0.15f * g), Math.min(1, eg * base + 0.15f * g), Math.min(1, eb * base + 0.15f * g), 1f, FULL_BRIGHT, overlay);
        // additive bloom layer
        float bloom = Mth.clamp((g - 0.4f) * 0.7f, 0, 1);
        if (bloom > 0.01f) {
            drawModel(glow, ps, buffers.getBuffer(RenderType.eyes(InventoryMenu.BLOCK_ATLAS)), er * bloom, eg * bloom, eb * bloom, 1f, FULL_BRIGHT, overlay);
        }

        if (pose.flash > 0.01f) {
            float[] m = type.muzzle;
            float size = (type == WeaponType.TITAN_X ? 0.55f : type == WeaponType.SPECTRE_7 ? 0.3f : 0.38f) * (0.6f + 0.6f * pose.flash);
            flare(ps, buffers, m[0] / 16f, m[1] / 16f, m[2] / 16f, size, er, eg, eb, pose.flash, true);
        }
        if (pose.coreFlash > 0.01f || pose.charge > 0.05f) {
            float[] c = type.core;
            float k = Math.max(pose.coreFlash, pose.charge);
            flare(ps, buffers, c[0] / 16f, c[1] / 16f, c[2] / 16f, 0.25f + 0.3f * k, er, eg, eb, k, false);
        }
        ps.popPose();
    }

    private static void drawModel(BakedModel model, PoseStack ps, VertexConsumer vc, float r, float g, float b, float a, int light, int overlay) {
        PoseStack.Pose last = ps.last();
        for (Direction d : Direction.values()) {
            RAND.setSeed(42L);
            for (BakedQuad q : model.getQuads(null, d, RAND)) vc.putBulkData(last, q, r, g, b, a, light, overlay, false);
        }
        RAND.setSeed(42L);
        for (BakedQuad q : model.getQuads(null, null, RAND)) vc.putBulkData(last, q, r, g, b, a, light, overlay, false);
    }

    /** Crossed additive quads (and a forward streak for muzzle flashes). */
    private static void flare(PoseStack ps, MultiBufferSource buffers, float x, float y, float z, float s,
                              float r, float g, float b, float a, boolean streak) {
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(FLARE));
        ps.pushPose();
        ps.translate(x, y, z);
        float spin = (System.currentTimeMillis() % 100000) / 40f;
        for (int i = 0; i < 3; i++) {
            ps.pushPose();
            if (i == 1) ps.mulPose(Axis.YP.rotationDegrees(90));
            if (i == 2) ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.mulPose(Axis.ZP.rotationDegrees(spin + i * 30));
            quad(ps, vc, s, s, r * a, g * a, b * a);
            ps.popPose();
        }
        if (streak) {
            ps.pushPose();
            ps.translate(0, 0, -s * 1.2f);
            ps.mulPose(Axis.YP.rotationDegrees(90));
            quad(ps, vc, s * 2.2f, s * 0.45f, r * a, g * a, b * a);
            ps.mulPose(Axis.XP.rotationDegrees(90));
            quad(ps, vc, s * 2.2f, s * 0.45f, r * a, g * a, b * a);
            ps.popPose();
        }
        // white-hot centre
        quad(ps, vc, s * 0.45f, s * 0.45f, a, a, a);
        ps.popPose();
    }

    private static void quad(PoseStack ps, VertexConsumer vc, float w, float h, float r, float g, float b) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        vertex(vc, m, n, -w, -h, 0, 0, r, g, b);
        vertex(vc, m, n, w, -h, 1, 0, r, g, b);
        vertex(vc, m, n, w, h, 1, 1, r, g, b);
        vertex(vc, m, n, -w, h, 0, 1, r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, float r, float g, float b) {
        vc.vertex(m, x, y, 0).color(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1), 1f)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
    }
}
