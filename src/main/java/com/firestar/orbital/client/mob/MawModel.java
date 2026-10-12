package com.firestar.orbital.client.mob;

import com.firestar.orbital.entity.Maw;
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
 * The Maw: no head. Its chest is a mouth from collarbone to gut, two plates of flesh that swing open like doors on rows of
 * teeth and the light inside. Massive arms ending in claws of bone; spines down its back.
 */
public class MawModel extends EntityModel<Maw> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(com.firestar.orbital.OrbitalMod.id("maw"), "main");
    private final ModelPart root, hips, torso, jawL, jawR, armL, armR, foreL, foreR, legL, legR;

    public MawModel(ModelPart root) {
        this.root = root;
        this.hips = root.getChild("hips");
        this.legL = root.getChild("leg_l");
        this.legR = root.getChild("leg_r");
        this.torso = hips.getChild("torso");
        this.jawL = torso.getChild("jaw_l");
        this.jawR = torso.getChild("jaw_r");
        this.armL = torso.getChild("arm_l");
        this.armR = torso.getChild("arm_r");
        this.foreL = armL.getChild("fore");
        this.foreR = armR.getChild("fore");
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, 0, -3.5F, 7, 16, 7), PartPose.offset(-5.5F, 8, 0));
        r.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-3.5F, 0, -3.5F, 7, 16, 7), PartPose.offset(5.5F, 8, 0));
        PartDefinition hips = r.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 0).addBox(-8, -4, -5, 16, 6, 10), PartPose.offset(0, 8, 0));
        CubeListBuilder tb = CubeListBuilder.create().texOffs(0, 16).addBox(-10, -22, -6, 20, 22, 12)
                // the inside of the mouth, set back behind the jaws
                .texOffs(64, 24).addBox(-7, -20, -5.6F, 14, 18, 1);
        for (int i = 0; i < 5; i++) {
            tb.texOffs(100, 24).addBox(-1, -24 + i * 4.6F, 5.5F, 2, 6, 2);
        }
        PartDefinition torso = hips.addOrReplaceChild("torso", tb, PartPose.offsetAndRotation(0, -2, 0, 0.22F, 0, 0));
        CubeListBuilder jl = CubeListBuilder.create().texOffs(64, 0).addBox(-9, -9, -2, 9, 18, 2);
        CubeListBuilder jr = CubeListBuilder.create().texOffs(64, 0).mirror().addBox(0, -9, -2, 9, 18, 2);
        for (int i = 0; i < 6; i++) {
            jl.texOffs(100, 0).addBox(-1.2F, -8 + i * 3.1F, -1.5F, 1, 2, 2);
            jr.texOffs(100, 0).addBox(0.2F, -8 + i * 3.1F, -1.5F, 1, 2, 2);
        }
        torso.addOrReplaceChild("jaw_l", jl, PartPose.offset(9, -11, -6));
        torso.addOrReplaceChild("jaw_r", jr, PartPose.offset(-9, -11, -6));
        for (int s = -1; s <= 1; s += 2) {
            CubeListBuilder up = CubeListBuilder.create().texOffs(32, 64);
            if (s > 0) {
                up.addBox(-3.5F, -2, -3.5F, 7, 18, 7);
            } else {
                up.mirror().addBox(-3.5F, -2, -3.5F, 7, 18, 7);
            }
            PartDefinition arm = torso.addOrReplaceChild(s > 0 ? "arm_l" : "arm_r", up, PartPose.offset(s * 13.5F, -19, 0));
            CubeListBuilder fo = CubeListBuilder.create().texOffs(64, 64).addBox(-3, 0, -3, 6, 16, 6);
            for (int c = 0; c < 3; c++) {
                fo.texOffs(100, 10).addBox(-2.2F + c * 2.1F, 15, -2.6F, 1, 8, 1);
            }
            arm.addOrReplaceChild("fore", fo, PartPose.offset(0, 15, 0));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(Maw e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float walk = Mth.cos(limbSwing * 0.4F) * 0.8F * limbAmount;
        legR.xRot = walk;
        legL.xRot = -walk;
        hips.y = 8 + Math.abs(Mth.sin(limbSwing * 0.4F)) * 0.8F * limbAmount;
        hips.zRot = Mth.sin(limbSwing * 0.4F) * 0.06F * limbAmount;
        float smash = attackTime > 0 ? Mth.sin(attackTime * Mth.PI) : 0;
        float lag = Mth.cos(limbSwing * 0.4F - 0.8F) * 0.45F * limbAmount;
        armR.xRot = -0.15F + lag - smash * 2.2F;
        armL.xRot = -0.15F - lag - smash * 2.0F;
        armR.zRot = 0.25F;
        armL.zRot = -0.25F;
        foreR.xRot = -0.35F - smash * 0.6F;
        foreL.xRot = -0.35F - smash * 0.6F;
        torso.yRot = headYaw * Mth.DEG_TO_RAD * 0.25F;
        torso.xRot = 0.22F + Mth.sin(age * 0.06F) * 0.03F - smash * 0.2F;
        // the mouth: breathing a little open, flung wide as it strikes
        float open = 0.1F + 0.12F * (0.5F + 0.5F * Mth.sin(age * 0.09F)) + smash * 1.2F;
        jawL.yRot = -open;
        jawR.yRot = open;
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }
}
