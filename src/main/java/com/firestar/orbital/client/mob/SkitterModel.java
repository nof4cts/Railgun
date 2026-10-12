package com.firestar.orbital.client.mob;

import com.firestar.orbital.entity.Skitter;
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

/** The Skitter: a low body, a glowing sac on its back, mandibles, six long jointed legs in a tripod gait. */
public class SkitterModel extends EntityModel<Skitter> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(com.firestar.orbital.OrbitalMod.id("skitter"), "main");
    private final ModelPart root, body, sac, head, mandL, mandR;
    private final ModelPart[] upper = new ModelPart[6], lower = new ModelPart[6];

    public SkitterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.sac = body.getChild("sac");
        this.head = body.getChild("head");
        this.mandL = head.getChild("mand_l");
        this.mandR = head.getChild("mand_r");
        for (int i = 0; i < 6; i++) {
            upper[i] = root.getChild("leg" + i);
            lower[i] = upper[i].getChild("lower");
        }
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-5, -4, -7, 10, 6, 14), PartPose.offset(0, 16, 1));
        body.addOrReplaceChild("sac", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5F, -4, -4, 7, 4, 8), PartPose.offset(0, -4, 1.5F));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(30, 20).addBox(-3, -3, -5, 6, 5, 5), PartPose.offset(0, -1, -7));
        head.addOrReplaceChild("mand_l", CubeListBuilder.create().texOffs(30, 32).addBox(-0.5F, -0.5F, -4, 1, 1, 4), PartPose.offset(1.6F, 1.2F, -4.5F));
        head.addOrReplaceChild("mand_r", CubeListBuilder.create().texOffs(30, 32).addBox(-0.5F, -0.5F, -4, 1, 1, 4), PartPose.offset(-1.6F, 1.2F, -4.5F));
        for (int i = 0; i < 6; i++) {
            int side = i < 3 ? 1 : -1;
            float z = -4 + (i % 3) * 4.5F;
            CubeListBuilder up = CubeListBuilder.create().texOffs(0, 36);
            CubeListBuilder lo = CubeListBuilder.create().texOffs(0, 42);
            if (side > 0) {
                up.addBox(0, -1, -1, 9, 2, 2);
                lo.addBox(0, -1, -1, 11, 2, 2);
            } else {
                up.mirror().addBox(-9, -1, -1, 9, 2, 2);
                lo.mirror().addBox(-11, -1, -1, 11, 2, 2);
            }
            PartDefinition leg = r.addOrReplaceChild("leg" + i, up, PartPose.offset(side * 4.5F, 15, z + 1));
            leg.addOrReplaceChild("lower", lo, PartPose.offset(side * 9, 0, 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Skitter e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float speed = limbSwing * 1.3F;
        for (int i = 0; i < 6; i++) {
            int side = i < 3 ? 1 : -1;
            // tripod: legs 0, 2 and 4 together, 1, 3 and 5 together
            float phase = ((i % 2 == 0) == (i < 3) ? 0 : Mth.PI) + (i % 3) * 0.3F;
            float swing = Mth.cos(speed + phase) * 0.5F * limbAmount;
            float lift = Math.max(0, Mth.sin(speed + phase)) * 0.5F * limbAmount;
            float splay = ((i % 3) - 1) * 0.55F;
            upper[i].yRot = side * (splay + swing);
            upper[i].zRot = -side * (0.75F + lift + Mth.sin(age * 0.2F + i) * 0.03F);
            lower[i].zRot = side * (1.75F - lift * 0.5F);
        }
        body.y = 16 - Math.abs(Mth.sin(speed)) * 0.6F * limbAmount;
        body.zRot = Mth.sin(speed) * 0.05F * limbAmount;
        float pulse = 1.0F + 0.08F * Mth.sin(age * 0.35F);
        sac.xScale = pulse;
        sac.yScale = pulse * 1.05F;
        sac.zScale = pulse;
        head.yRot = headYaw * Mth.DEG_TO_RAD * 0.6F;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5F;
        float chew = 0.25F + 0.2F * Mth.sin(age * 0.9F) + (attackTime > 0 ? Mth.sin(attackTime * Mth.PI) * 0.6F : 0);
        mandL.yRot = -chew;
        mandR.yRot = chew;
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }
}
