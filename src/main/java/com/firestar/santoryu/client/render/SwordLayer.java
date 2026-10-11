package com.firestar.santoryu.client.render;

import com.firestar.santoryu.client.Casts;
import com.firestar.santoryu.client.ClientCast;
import com.firestar.santoryu.common.Need;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Santoryu: during a technique, the second blade in the left hand and the third held in the mouth. */
public final class SwordLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public SwordLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partial, float age, float headYaw, float headPitch) {
        ClientCast c = Casts.of(player);
        if (c == null || player.isInvisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (c.move.need.blades >= 2 && !c.bladeLeft.isEmpty() && player.getOffhandItem().isEmpty()) {
            pose.pushPose();
            getParentModel().translateToHand(HumanoidArm.LEFT, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(-1.0F / 16.0F, 0.125F, -0.625F);
            mc.getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(player, c.bladeLeft, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, pose, buffers, light);
            pose.popPose();
        }
        ItemStack mouth = c.bladeMouth;
        if (c.move.need.blades >= 3 || c.move.need == Need.ENMA) {
            if (!mouth.isEmpty()) {
                pose.pushPose();
                getParentModel().head.translateAndRotate(pose);
                // between the teeth, blade out to his right, edge forward
                pose.translate(0.0F, -0.13F, -0.3F);
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
                pose.scale(0.85F, -0.85F, -0.85F);
                // the sprite runs corner to corner (hilt bottom-left, tip top-right); this turn lays it flat across
                // the mouth with the blade out to his right and the hilt end out to his left, as in the anime
                pose.mulPose(Axis.ZP.rotationDegrees(-135.0F));
                // grip in the teeth, guard just beside the mouth: slide along the blade toward the tip (no vertical drift)
                pose.translate(-0.29F, 0.29F, 0.0F);
                mc.getItemRenderer().renderStatic(mouth, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), 0);
                pose.popPose();
            }
        }
    }
}
