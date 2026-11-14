package com.aegis.command.client.render;

import com.aegis.command.AegisCommand;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Original aircraft and ordnance geometry, built from boxes. Nose points to -Z, model space is
 * y-down like vanilla entity models. Each mesh has a "hull" and an emissive "glow" group.
 */
public final class AircraftModels {
    public static final ModelLayerLocation KESTREL = new ModelLayerLocation(AegisCommand.id("kestrel"), "main");
    public static final ModelLayerLocation GOLIATH = new ModelLayerLocation(AegisCommand.id("goliath"), "main");
    public static final ModelLayerLocation WRAITH = new ModelLayerLocation(AegisCommand.id("wraith"), "main");
    public static final ModelLayerLocation BOMB = new ModelLayerLocation(AegisCommand.id("aerial_bomb"), "main");

    private AircraftModels() {}

    private static CubeListBuilder box(float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(0, 0).addBox(x, y, z, w, h, d);
    }

    public static LayerDefinition kestrel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.ZERO);

        hull.addOrReplaceChild("fuselage", box(-3, -3, -24, 6, 6, 44), PartPose.ZERO);
        hull.addOrReplaceChild("nose", box(-2, -2, -32, 4, 4, 8), PartPose.ZERO);
        hull.addOrReplaceChild("tip", box(-1, -1, -37, 2, 2, 5), PartPose.ZERO);
        hull.addOrReplaceChild("canopy", box(-2, -5.5f, -21, 4, 3, 11), PartPose.ZERO);
        hull.addOrReplaceChild("spine", box(-1.5f, -4.5f, -10, 3, 2, 24), PartPose.ZERO);
        hull.addOrReplaceChild("intake_l", box(-5.5f, -1.5f, -13, 2.5f, 4, 12), PartPose.ZERO);
        hull.addOrReplaceChild("intake_r", box(3, -1.5f, -13, 2.5f, 4, 12), PartPose.ZERO);
        hull.addOrReplaceChild("wing_l", box(-25, 0, -5, 25, 1, 15), PartPose.offsetAndRotation(-3, 0.5f, 0, 0, 0.42f, 0));
        hull.addOrReplaceChild("wing_r", box(0, 0, -5, 25, 1, 15), PartPose.offsetAndRotation(3, 0.5f, 0, 0, -0.42f, 0));
        hull.addOrReplaceChild("canard_l", box(-7, 0, 0, 7, 0.6f, 4), PartPose.offsetAndRotation(-3, -1, -16, 0, 0.3f, 0));
        hull.addOrReplaceChild("canard_r", box(0, 0, 0, 7, 0.6f, 4), PartPose.offsetAndRotation(3, -1, -16, 0, -0.3f, 0));
        hull.addOrReplaceChild("fin_l", box(-0.5f, -10, 0, 1, 10, 8), PartPose.offsetAndRotation(-2.5f, -3, 12, 0, 0, -0.3f));
        hull.addOrReplaceChild("fin_r", box(-0.5f, -10, 0, 1, 10, 8), PartPose.offsetAndRotation(2.5f, -3, 12, 0, 0, 0.3f));
        hull.addOrReplaceChild("stab_l", box(-10, 0, 0, 10, 1, 7), PartPose.offsetAndRotation(-3, 0.5f, 13, 0, 0.35f, 0));
        hull.addOrReplaceChild("stab_r", box(0, 0, 0, 10, 1, 7), PartPose.offsetAndRotation(3, 0.5f, 13, 0, -0.35f, 0));
        hull.addOrReplaceChild("nozzle_l", box(-3, -2, 20, 2.8f, 2.8f, 2), PartPose.ZERO);
        hull.addOrReplaceChild("nozzle_r", box(0.2f, -2, 20, 2.8f, 2.8f, 2), PartPose.ZERO);
        hull.addOrReplaceChild("pylon_l", box(-0.6f, 0, -2, 1.2f, 2.5f, 6), PartPose.offset(-11, 1.5f, 2));
        hull.addOrReplaceChild("pylon_r", box(-0.6f, 0, -2, 1.2f, 2.5f, 6), PartPose.offset(11, 1.5f, 2));

        glow.addOrReplaceChild("burner_l", box(-2.6f, -1.6f, 22, 2, 2, 0.5f), PartPose.ZERO);
        glow.addOrReplaceChild("burner_r", box(0.6f, -1.6f, 22, 2, 2, 0.5f), PartPose.ZERO);
        glow.addOrReplaceChild("nav_l", box(-0.5f, -0.5f, -0.5f, 1, 1, 1), PartPose.offset(-22, 0.5f, 9));
        glow.addOrReplaceChild("nav_r", box(-0.5f, -0.5f, -0.5f, 1, 1, 1), PartPose.offset(22, 0.5f, 9));
        return LayerDefinition.create(mesh, 256, 256);
    }

    public static LayerDefinition goliath() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.ZERO);

        hull.addOrReplaceChild("fuselage", box(-5, -4, -30, 10, 8, 56), PartPose.ZERO);
        hull.addOrReplaceChild("nose", box(-4, -3, -36, 8, 6, 6), PartPose.ZERO);
        hull.addOrReplaceChild("chin", box(-3, -2, -40, 6, 4, 4), PartPose.ZERO);
        hull.addOrReplaceChild("cockpit", box(-3.5f, -7, -31, 7, 3, 9), PartPose.ZERO);
        hull.addOrReplaceChild("hump", box(-3, -6, -20, 6, 2, 30), PartPose.ZERO);
        hull.addOrReplaceChild("bay", box(-3.5f, 4, -12, 7, 1, 18), PartPose.ZERO);
        hull.addOrReplaceChild("wing_l", box(-46, 0, -6, 46, 2, 22), PartPose.offsetAndRotation(-4, -1, -2, 0, 0.18f, 0));
        hull.addOrReplaceChild("wing_r", box(0, 0, -6, 46, 2, 22), PartPose.offsetAndRotation(4, -1, -2, 0, -0.18f, 0));
        hull.addOrReplaceChild("tail", box(-0.7f, -16, 0, 1.4f, 16, 11), PartPose.offset(0, -3, 16));
        hull.addOrReplaceChild("stab", box(-15, 0, 0, 30, 1.2f, 8), PartPose.offset(0, -18, 19));
        float[][] pods = {{-14, -2}, {14, -2}, {-27, 2}, {27, 2}};
        int i = 0;
        for (float[] p : pods) {
            hull.addOrReplaceChild("pod" + i, box(-2, 0, -7, 4, 4, 12), PartPose.offset(p[0], 1.5f, p[1]));
            hull.addOrReplaceChild("pylon" + i, box(-0.6f, -1, -3, 1.2f, 2, 6), PartPose.offset(p[0], 1, p[1]));
            glow.addOrReplaceChild("exhaust" + i, box(-1.5f, 0.5f, 5, 3, 3, 0.5f), PartPose.offset(p[0], 1.5f, p[1]));
            i++;
        }
        glow.addOrReplaceChild("beacon", box(-0.6f, -0.6f, -0.6f, 1.2f, 1.2f, 1.2f), PartPose.offset(0, -19.5f, 24));
        return LayerDefinition.create(mesh, 256, 256);
    }

    public static LayerDefinition wraith() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.ZERO);

        hull.addOrReplaceChild("body", box(-6, -2.5f, -24, 12, 4, 40), PartPose.ZERO);
        hull.addOrReplaceChild("nose", box(-4, -1.5f, -32, 8, 3, 8), PartPose.ZERO);
        hull.addOrReplaceChild("tip", box(-2, -1, -36, 4, 2, 4), PartPose.ZERO);
        hull.addOrReplaceChild("canopy", box(-2.5f, -4.5f, -20, 5, 2, 10), PartPose.ZERO);
        hull.addOrReplaceChild("wing_l", box(-30, 0, -4, 28, 1.5f, 24), PartPose.offsetAndRotation(-4, -0.5f, -6, 0, 0.72f, 0));
        hull.addOrReplaceChild("wing_r", box(2, 0, -4, 28, 1.5f, 24), PartPose.offsetAndRotation(4, -0.5f, -6, 0, -0.72f, 0));
        hull.addOrReplaceChild("blend_l", box(-8, -1, -10, 8, 2.5f, 26), PartPose.offset(-6, -0.5f, 0));
        hull.addOrReplaceChild("blend_r", box(0, -1, -10, 8, 2.5f, 26), PartPose.offset(6, -0.5f, 0));
        hull.addOrReplaceChild("vtail_l", box(-0.5f, -8, 0, 1, 8, 8), PartPose.offsetAndRotation(-4, -2.5f, 8, 0, 0, -0.65f));
        hull.addOrReplaceChild("vtail_r", box(-0.5f, -8, 0, 1, 8, 8), PartPose.offsetAndRotation(4, -2.5f, 8, 0, 0, 0.65f));

        glow.addOrReplaceChild("slit", box(-5, -1, 16, 10, 1.4f, 0.5f), PartPose.ZERO);
        glow.addOrReplaceChild("strip_l", box(-0.3f, -0.2f, -6, 0.6f, 0.4f, 12), PartPose.offset(-9, -1.6f, 2));
        glow.addOrReplaceChild("strip_r", box(-0.3f, -0.2f, -6, 0.6f, 0.4f, 12), PartPose.offset(9, -1.6f, 2));
        return LayerDefinition.create(mesh, 256, 256);
    }

    public static LayerDefinition bomb() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition glow = root.addOrReplaceChild("glow", CubeListBuilder.create(), PartPose.ZERO);
        hull.addOrReplaceChild("body", box(-1.5f, -1.5f, -6, 3, 3, 12), PartPose.ZERO);
        hull.addOrReplaceChild("nose", box(-1, -1, -8.5f, 2, 2, 2.5f), PartPose.ZERO);
        hull.addOrReplaceChild("fin_h", box(-3.2f, -0.25f, 4, 6.4f, 0.5f, 3), PartPose.ZERO);
        hull.addOrReplaceChild("fin_v", box(-0.25f, -3.2f, 4, 0.5f, 6.4f, 3), PartPose.ZERO);
        glow.addOrReplaceChild("band", box(-1.6f, -1.6f, -3, 3.2f, 3.2f, 0.6f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }
}
