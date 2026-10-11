package com.firestar.worldbreaker.client.giant;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The colossus: a hunched giant of basalt plates with molten seams. Built at four model units per design unit so its
 * 1024px texture is dense enough to hold up at 50 blocks tall. Legs and arms are two segments each, so the walk bends
 * at the knee and the arms hang and swing like dead weight.
 *
 * <p>Design units: feet at y = 128, the crown of the head near y = -6, the front facing -z (Minecraft's convention).
 */
public final class GiantModel {
    /** Model units per design unit. */
    static final float U = 4.0F;
    /** Design units from the soles to the crown. */
    public static final float TALL = 134.0F;

    public final ModelPart root, hips, abdomen, chest, neck, head, jaw;
    public final ModelPart thighL, thighR, shinL, shinR, footL, footR;
    public final ModelPart armL, armR, foreL, foreR, handL, handR;

    public GiantModel() {
        root = build().bakeRoot();
        hips = root.getChild("hips");
        abdomen = hips.getChild("abdomen");
        chest = abdomen.getChild("chest");
        neck = chest.getChild("neck");
        head = neck.getChild("head");
        jaw = head.getChild("jaw");
        thighL = hips.getChild("thigh_l");
        thighR = hips.getChild("thigh_r");
        shinL = thighL.getChild("shin");
        shinR = thighR.getChild("shin");
        footL = shinL.getChild("foot");
        footR = shinR.getChild("foot");
        armL = chest.getChild("arm_l");
        armR = chest.getChild("arm_r");
        foreL = armL.getChild("fore");
        foreR = armR.getChild("fore");
        handL = foreL.getChild("hand");
        handR = foreR.getChild("hand");
    }

    private static CubeListBuilder box(CubeListBuilder b, int u, int v, float x, float y, float z, float w, float h, float d) {
        return b.texOffs(u, v).addBox(x * U, y * U, z * U, w * U, h * U, d * U, CubeDeformation.NONE);
    }

    private static PartPose at(float x, float y, float z) {
        return PartPose.offset(x * U, y * U, z * U);
    }

    private static PartPose at(float x, float y, float z, float rx, float ry, float rz) {
        return PartPose.offsetAndRotation(x * U, y * U, z * U, rx, ry, rz);
    }

    private static LayerDefinition build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips",
                box(box(CubeListBuilder.create(), 0, 0, -14, -6, -7, 28, 12, 14), 0, 640, -15, 2, -8, 30, 6, 16), at(0, 62, 0));

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "thigh_r" : "thigh_l";
            PartDefinition thigh = hips.addOrReplaceChild(n,
                    box(box(CubeListBuilder.create(), 0, 200, -5.5F, 0, -5.5F, 11, 28, 11), 480, 640, -4.5F, 21, -7.5F, 9, 9, 4),
                    at(s * 8, 4, 0));
            PartDefinition shin = thigh.addOrReplaceChild("shin",
                    box(box(CubeListBuilder.create(), 200, 200, -4.5F, 0, -4.5F, 9, 30, 9), 600, 640, -5, 4, -5.5F, 10, 14, 4),
                    at(0, 28, 0));
            shin.addOrReplaceChild("foot",
                    box(box(CubeListBuilder.create(), 400, 200, -6, 0, -11, 12, 4, 16), 700, 640, -6.5F, 1, -13, 13, 3, 4),
                    at(0, 30, 0));
        }

        PartDefinition abdomen = hips.addOrReplaceChild("abdomen",
                box(CubeListBuilder.create(), 0, 400, -12, -18, -6, 24, 18, 12), at(0, -6, 0, 0.08F, 0, 0));
        // the chest: broad and hunched forward, a crest of plates down the back, slabs on the shoulders
        CubeListBuilder chestB = box(CubeListBuilder.create(), 0, 0, -17, -22, -9, 34, 22, 18);
        chestB = box(chestB, 300, 400, -13, -20, -10.5F, 26, 14, 3);
        for (int i = 0; i < 4; i++) {
            chestB = box(chestB, 420 + i * 40, 400, -2.5F + (i % 2) * 0.5F, -21 + i * 5, 8, 5, 6, 6 + (3 - i));
        }
        PartDefinition chest = abdomen.addOrReplaceChild("chest", chestB, at(0, -18, 0, 0.3F, 0, 0));
        chest.addOrReplaceChild("plate_l", box(CubeListBuilder.create(), 0, 760, -6, -5, -9, 12, 9, 18), at(16, -21, 0, 0, 0, -0.35F));
        chest.addOrReplaceChild("plate_r", box(CubeListBuilder.create(), 0, 760, -6, -5, -9, 12, 9, 18), at(-16, -21, 0, 0, 0, 0.35F));
        chest.addOrReplaceChild("spike_l", box(CubeListBuilder.create(), 200, 760, -2, -10, -3, 4, 10, 6), at(19, -24, 2, -0.3F, 0, -0.5F));
        chest.addOrReplaceChild("spike_r", box(CubeListBuilder.create(), 200, 760, -2, -10, -3, 4, 10, 6), at(-19, -24, 2, -0.3F, 0, 0.5F));

        PartDefinition neck = chest.addOrReplaceChild("neck", box(CubeListBuilder.create(), 300, 760, -4.5F, -7, -4.5F, 9, 7, 9),
                at(0, -21, -4, -0.35F, 0, 0));
        // the head has its own texture region: the face is painted on its front
        CubeListBuilder headB = box(CubeListBuilder.create(), 640, 0, -8, -16, -9, 16, 16, 16);
        headB = box(headB, 640, 160, -8.5F, -11.5F, -10.5F, 17, 3, 3);
        headB = box(headB, 760, 160, -3, -19, -6, 6, 4, 12);
        PartDefinition head = neck.addOrReplaceChild("head", headB, at(0, -6, 0));
        head.addOrReplaceChild("jaw", box(CubeListBuilder.create(), 640, 220, -6.5F, 0, -9.5F, 13, 5, 12), at(0, -3, 0));

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "arm_r" : "arm_l";
            PartDefinition arm = chest.addOrReplaceChild(n,
                    box(box(CubeListBuilder.create(), 0, 520, -5, -2, -5, 10, 34, 10), 480, 760, -5.5F, -3, -6, 11, 8, 12),
                    at(s * 19, -17, 0));
            PartDefinition fore = arm.addOrReplaceChild("fore",
                    box(box(CubeListBuilder.create(), 200, 520, -4.5F, 0, -4.5F, 9, 32, 9), 600, 760, -5, 2, -5.5F, 10, 10, 4),
                    at(0, 32, 0));
            CubeListBuilder hand = box(CubeListBuilder.create(), 400, 520, -5, 0, -5, 10, 11, 10);
            for (int f = 0; f < 3; f++) {
                hand = box(hand, 700 + f * 40, 760, -4.5F + f * 3.3F, 10, -4.5F, 2.6F, 9 - f, 3);
            }
            fore.addOrReplaceChild("hand", hand, at(0, 32, 0));
        }
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /**
     * Poses the giant for a moment of its walk.
     *
     * @param gait   0..1 across a full stride (left foot lands at 0, right at 0.5)
     * @param time   seconds, for the slow idle motion of the head and jaw
     * @param seed   per-giant variation
     * @param rising 0..1, how far out of the ground it has come (it claws its way up)
     */
    public void pose(double gait, float time, int seed, float rising) {
        float a = (float) (gait * Math.PI * 2);
        float c = Mth.cos(a), s = Mth.sin(a);
        thighL.xRot = -0.5F * c;
        thighR.xRot = 0.5F * c;
        shinL.xRot = Math.max(0, -s) * 0.95F;
        shinR.xRot = Math.max(0, s) * 0.95F;
        footL.xRot = -0.5F * Math.max(0, -s) + 0.25F * c;
        footR.xRot = -0.5F * Math.max(0, s) - 0.25F * c;
        thighL.zRot = -0.04F;
        thighR.zRot = 0.04F;

        // weight: the body drops onto each landing and rides up mid-stride; hips roll over the planted foot
        hips.y = 62 * U - 3.5F * U * (1 - Math.abs(c));
        hips.zRot = 0.05F * c;
        hips.yRot = 0.06F * c;
        chest.yRot = -0.09F * c;
        chest.zRot = -0.03F * c;

        // arms: dead weight, swinging opposite the legs and lagging behind them
        float lag = (float) Math.cos(a - 0.6);
        armL.xRot = 0.32F * lag - 0.08F;
        armR.xRot = -0.32F * lag - 0.08F;
        armL.zRot = -0.12F;
        armR.zRot = 0.12F;
        foreL.xRot = -0.25F - 0.18F * Math.max(0, lag);
        foreR.xRot = -0.25F - 0.18F * Math.max(0, -lag);
        handL.xRot = -0.15F;
        handR.xRot = -0.15F;

        // the head: looking slowly about, the jaw hanging and working
        head.yRot = 0.28F * Mth.sin(time * 0.35F + seed * 1.7F);
        head.xRot = 0.1F + 0.06F * Mth.sin(time * 0.5F + seed);
        jaw.xRot = 0.12F + 0.1F * Mth.sin(time * 0.9F + seed * 2.3F);

        if (rising < 1) {
            // clawing up out of the earth: arms raised and reaching, head thrown back
            float k = 1 - rising;
            armL.xRot = Mth.lerp(k, armL.xRot, -2.6F);
            armR.xRot = Mth.lerp(k, armR.xRot, -2.4F);
            foreL.xRot = Mth.lerp(k, foreL.xRot, -0.6F);
            foreR.xRot = Mth.lerp(k, foreR.xRot, -0.7F);
            head.xRot = Mth.lerp(k, head.xRot, -0.6F);
            jaw.xRot = Mth.lerp(k, jaw.xRot, 0.45F);
        }
    }
}
