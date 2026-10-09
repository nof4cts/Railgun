package com.railgun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.railgun.RailgunMod;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RailgunRenderer extends BlockEntityWithoutLevelRenderer {
    // ---- TUNABLES: first-person placement (camera-space offset from vanilla's hand anchor)
    private static final float FP_X = -0.24f, FP_Y = 0.28f, FP_Z = -0.20f, FP_SCALE = 0.80f, FP_YAW = 5f;

    public RailgunRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        float time = (Util.getMillis() % 1_000_000L) / 50f;
        float charge = 0f, heat = 0f;

        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5); // undo vanilla's -0.5 so origin is the model centre

        switch (ctx) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                float side = ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -1f : 1f;
                LocalPlayer pl = mc.player;
                float pt = mc.getPartialTick();
                float recoil = 0f;
                if (pl != null) {
                    charge = ScreenFx.charge(pt);
                    float cd = pl.getCooldowns().getCooldownPercent(RailgunMod.RAILGUN.get(), pt);
                    heat = cd;
                    recoil = (float) Math.pow(cd, 5.0);
                }
                float shake = charge * charge * 0.007f;
                ps.translate(FP_X * side, FP_Y, FP_Z + recoil * 0.22f);
                ps.translate(Mth.sin(time * 2.3f) * shake, Mth.cos(time * 3.1f) * shake, 0);
                ps.mulPose(Axis.YP.rotationDegrees(FP_YAW * side));
                ps.mulPose(Axis.XP.rotationDegrees(1.5f + recoil * 10f));
                ps.scale(FP_SCALE, FP_SCALE, FP_SCALE);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                ps.mulPose(Axis.XP.rotationDegrees(90f)); // barrel -Z -> item +Y (forward in the hand frame)
                ps.scale(0.62f, 0.62f, 0.62f);
                ps.translate(0, 0, 0.12f);
            }
            case GUI -> {
                ps.scale(0.74f, 0.74f, 0.74f);
                ps.mulPose(Axis.ZP.rotationDegrees(22f));
                ps.mulPose(Axis.XP.rotationDegrees(14f));
                ps.mulPose(Axis.YP.rotationDegrees(-75f));
                ps.translate(0, 0, 0.28f);
                charge = 0.25f;
            }
            case GROUND -> {
                ps.scale(0.5f, 0.5f, 0.5f);
                ps.translate(0, 0, 0.28f);
            }
            case FIXED -> {
                ps.mulPose(Axis.YP.rotationDegrees(-90f));
                ps.scale(0.62f, 0.62f, 0.62f);
                ps.translate(0, 0, 0.28f);
            }
            default -> {
                ps.scale(0.5f, 0.5f, 0.5f);
            }
        }
        GunModel.render(ps, buf, light, overlay, charge, heat, time);
        ps.popPose();
    }
}
