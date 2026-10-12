package com.firestar.orbital.client.mob;

import com.firestar.orbital.entity.Bloater;
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

/** The Bloater: a swollen sac of a body grown lumps and a ring of mouths, on four stubby legs. It swells before it bursts. */
public class BloaterModel extends EntityModel<Bloater> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(com.firestar.orbital.OrbitalMod.id("bloater"), "main");
    private final ModelPart root, body;
    private final ModelPart[] legs = new ModelPart[4];
    private final ModelPart[] lumps = new ModelPart[3];

    public BloaterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        for (int i = 0; i < 4; i++) {
            legs[i] = root.getChild("leg" + i);
        }
        for (int i = 0; i < 3; i++) {
            lumps[i] = body.getChild("lump" + i);
        }
    }

    public static LayerDefinition layer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -14, -7, 14, 14, 14), PartPose.offset(0, 21, 0));
        body.addOrReplaceChild("lump0", CubeListBuilder.create().texOffs(0, 28).addBox(-4, -4, -3.5F, 8, 4, 7), PartPose.offset(0, -14, 0));
        body.addOrReplaceChild("lump1", CubeListBuilder.create().texOffs(30, 28).addBox(-2.5F, -3, -3, 5, 6, 3), PartPose.offset(5, -9, -6.5F));
        body.addOrReplaceChild("lump2", CubeListBuilder.create().texOffs(30, 28).mirror().addBox(-3, -3, -2.5F, 3, 6, 5), PartPose.offset(-6.8F, -6, 3));
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0 ? -1 : 1) * 4.5F, z = (i < 2 ? -1 : 1) * 4.5F;
            r.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(0, 40).addBox(-1.5F, 0, -1.5F, 3, 4, 3), PartPose.offset(x, 20, z));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Bloater e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        float partial = age - e.tickCount;
        float sw = e.swell(partial);
        float breathe = 1.0F + 0.04F * Mth.sin(age * 0.12F);
        float shake = sw > 0 ? Mth.sin(age * (2.0F + sw * 4.0F)) * sw * 0.06F : 0;
        float s = breathe * (1.0F + sw * 0.45F);
        body.xScale = s + shake;
        body.yScale = s * (1.0F - sw * 0.1F) - shake;
        body.zScale = s + shake;
        body.yRot = Mth.sin(age * 0.03F) * 0.1F;
        body.zRot = Mth.sin(limbSwing * 0.6F) * 0.08F * limbAmount;
        for (int i = 0; i < 3; i++) {
            float p = 1.0F + 0.12F * Mth.sin(age * 0.2F + i * 2.1F) + sw * 0.4F;
            lumps[i].xScale = lumps[i].yScale = lumps[i].zScale = p;
        }
        for (int i = 0; i < 4; i++) {
            legs[i].xRot = Mth.cos(limbSwing * 0.9F + (i % 2 == 0 ? 0 : Mth.PI) + (i < 2 ? 0 : Mth.PI)) * 0.8F * limbAmount;
        }
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }
}
