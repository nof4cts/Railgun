package com.firestar.orbital.client.mob;

import com.firestar.orbital.entity.Inverted;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The Inverted: a tall, hunched body; its head hangs upside down off one shoulder; arms long enough to drag; the chest
 * split down the middle into two flaps that open on the light inside; a ridge of bone down its back.
 */
public class InvertedModel extends EntityModel<Inverted> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(com.firestar.orbital.OrbitalMod.id("inverted"), "main");
    private final ModelPart root, body, head, flapL, flapR, armL, armR, legL, legR;

    public InvertedModel(ModelPart root) {
        this.root = root;
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.flapL = body.getChild("flap_l");
        this.flapR = body.getChild("flap_r");
        this.armL = body.getChild("arm_l");
        this.armR = body.getChild("arm_r");
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 32).addBox(-2, 0, -2, 4, 14, 4), PartPose.offset(-2.3F, 10, 0));
        r.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(0, 32).mirror().addBox(-2, 0, -2, 4, 14, 4), PartPose.offset(2.3F, 10, 0));
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4.5F, -13, -2.5F, 9, 13, 5)
                .texOffs(0, 54).addBox(-1, -13, 2.5F, 2, 2, 3).texOffs(0, 54).addBox(-1, -9, 2.5F, 2, 2, 3).texOffs(0, 54).addBox(-1, -5, 2.5F, 2, 2, 2),
                PartPose.offsetAndRotation(0, 10.5F, 0, 0.32F, 0, 0));
        body.addOrReplaceChild("flap_l", CubeListBuilder.create().texOffs(44, 16).addBox(0, -4.5F, -1, 4, 9, 1), PartPose.offset(0.1F, -8, -2.6F));
        body.addOrReplaceChild("flap_r", CubeListBuilder.create().texOffs(44, 26).addBox(-4, -4.5F, -1, 4, 9, 1), PartPose.offset(-0.1F, -8, -2.6F));
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8), PartPose.offsetAndRotation(1.5F, -13, -0.5F,
                0, 0, Mth.PI));
        body.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(40, 36).addBox(-1.5F, -1, -1.5F, 3, 21, 3), PartPose.offset(-6, -12, 0));
        body.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(40, 36).mirror().addBox(-1.5F, -1, -1.5F, 3, 21, 3), PartPose.offset(6, -12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Inverted e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float walk = Mth.cos(limbSwing * 0.55F) * 1.1F * limbAmount;
        legR.xRot = walk;
        legL.xRot = -walk;
        // dragging arms, swinging late and loose; reaching forward to strike
        float lag = Mth.cos(limbSwing * 0.55F - 0.9F) * 0.6F * limbAmount;
        float reach = attackTime > 0 ? Mth.sin(attackTime * Mth.PI) : 0;
        armR.xRot = -0.32F + lag - reach * 1.9F + Mth.sin(age * 0.07F) * 0.05F;
        armL.xRot = -0.32F - lag - reach * 1.7F + Mth.sin(age * 0.06F + 1) * 0.05F;
        armR.zRot = 0.08F + Mth.sin(age * 0.09F) * 0.04F;
        armL.zRot = -0.08F - Mth.sin(age * 0.08F) * 0.04F;
        // the hanging head: lolling, then jerking without warning
        float twitch = (float) Math.pow(Math.max(0, Mth.sin(age * 0.31F + e.getId())), 30) * 0.9F;
        head.zRot = Mth.PI - 0.55F + Mth.sin(age * 0.05F) * 0.12F + twitch;
        head.yRot = headYaw * Mth.DEG_TO_RAD * 0.5F + twitch * 0.6F;
        head.xRot = 0.2F + Mth.sin(age * 0.04F) * 0.08F;
        // the chest breathing open, wide when it bites
        float open = 0.12F + 0.14F * (0.5F + 0.5F * Mth.sin(age * 0.11F)) + reach * 1.1F;
        flapL.yRot = open;
        flapR.yRot = -open;
        body.xRot = 0.32F + Mth.sin(age * 0.05F) * 0.03F + reach * 0.25F;
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }
}
