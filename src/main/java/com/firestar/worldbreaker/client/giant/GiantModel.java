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
 * The giant: a blocky, naked-looking human the height of a hill. Human proportions (the head an eighth of its height),
 * long dangling arms, a mop of hair, ears, a jaw that hangs in a fixed, too-wide grin. Built at four model units per
 * design unit so its 1024px skin holds up at 50 blocks tall; the texture layout is drawn by tools/make_skin.py from
 * the same numbers.
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
        this(false);
    }

    /** @param king the leader: twice as broad, bone grown over its skin, a crown of antlers, long skeletal fingers */
    public GiantModel(boolean king) {
        root = (king ? buildKing() : build()).bakeRoot();
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
        PartDefinition hips = root.addOrReplaceChild("hips", box(CubeListBuilder.create(), 0, 0, -14, -6, -7, 28, 12, 14), at(0, 62, 0));

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "thigh_r" : "thigh_l";
            PartDefinition thigh = hips.addOrReplaceChild(n, box(CubeListBuilder.create(), 0, 110, -5.5F, 0, -5.5F, 11, 32, 11), at(s * 7.5F, 4, 0));
            PartDefinition shin = thigh.addOrReplaceChild("shin", box(CubeListBuilder.create(), 180, 110, -4.5F, 0, -4.5F, 9, 30, 9), at(0, 32, 0));
            shin.addOrReplaceChild("foot", box(CubeListBuilder.create(), 330, 110, -6, 0, -12, 12, 4, 16), at(0, 30, 2));
        }

        PartDefinition abdomen = hips.addOrReplaceChild("abdomen", box(CubeListBuilder.create(), 340, 0, -12, -18, -6, 24, 18, 12),
                at(0, -6, 0, 0.03F, 0, 0));
        PartDefinition chest = abdomen.addOrReplaceChild("chest", box(CubeListBuilder.create(), 630, 0, -16, -22, -8, 32, 22, 16),
                at(0, -18, 0, 0.06F, 0, 0));
        PartDefinition neck = chest.addOrReplaceChild("neck", box(CubeListBuilder.create(), 560, 180, -4.5F, -7, -4.5F, 9, 7, 9),
                at(0, -22, -1, -0.06F, 0, 0));
        // the head: the face is painted on its front, the grin carries on down the jaw; hair over the top; ears
        CubeListBuilder headB = box(CubeListBuilder.create(), 0, 300, -7.5F, -17, -8, 15, 17, 16);
        headB = box(headB, 260, 300, -8, -18, -8.5F, 16, 6, 17);
        headB = box(headB, 740, 300, -9.5F, -11, -2, 2, 5, 4);
        headB = box(headB, 740, 300, 7.5F, -11, -2, 2, 5, 4);
        PartDefinition head = neck.addOrReplaceChild("head", headB, at(0, -6, 0));
        head.addOrReplaceChild("jaw", box(CubeListBuilder.create(), 540, 300, -6.5F, 0, -9, 13, 5, 11), at(0, -3, 0.5F));

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "arm_r" : "arm_l";
            CubeListBuilder upper = box(CubeListBuilder.create(), 0, 480, -5, -2, -5, 10, 32, 10);
            upper = box(upper, 170, 480, -6, -4, -6, 12, 10, 12);
            PartDefinition arm = chest.addOrReplaceChild(n, upper, at(s * 20, -18, 0));
            PartDefinition fore = arm.addOrReplaceChild("fore", box(CubeListBuilder.create(), 370, 480, -4.5F, 0, -4.5F, 9, 30, 9), at(0, 31, 0));
            CubeListBuilder hand = box(CubeListBuilder.create(), 520, 480, -5, 0, -4, 10, 11, 8);
            for (int f = 0; f < 4; f++) {
                hand = box(hand, 680, 480, -4.6F + f * 2.4F, 10.5F, -3.5F, 2.2F, f == 1 || f == 2 ? 9 : 7.5F, 3);
            }
            hand = box(hand, 680, 480, s < 0 ? 4.2F : -6.4F, 3, -5.5F, 2.2F, 6, 3);
            fore.addOrReplaceChild("hand", hand, at(0, 30, 0));
        }
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /** Skin regions for the king's broad torso, and the bone texture everything bony samples. */
    private static final int BONE_U = 520, BONE_V = 652;

    private static CubeListBuilder bone(CubeListBuilder b, int du, int dv, float x, float y, float z, float w, float h, float d) {
        return box(b, BONE_U + du, BONE_V + dv, x, y, z, w, h, d);
    }

    private static LayerDefinition buildKing() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition hips = root.addOrReplaceChild("hips", box(CubeListBuilder.create(), 0, 652, -19, -6, -7.5F, 38, 12, 15), at(0, 62, 0));
        // a girdle of bone round the hips
        hips.addOrReplaceChild("girdle", bone(CubeListBuilder.create(), 0, 0, -20, -2, -8.5F, 40, 4, 17), at(0, 0, 0));

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "thigh_r" : "thigh_l";
            PartDefinition thigh = hips.addOrReplaceChild(n, box(CubeListBuilder.create(), 0, 110, -5.5F, 0, -5.5F, 11, 32, 11), at(s * 11, 4, 0));
            PartDefinition shin = thigh.addOrReplaceChild("shin",
                    bone(box(CubeListBuilder.create(), 180, 110, -4.5F, 0, -4.5F, 9, 30, 9), 0, 110, -4, -3, -6.5F, 8, 9, 3), at(0, 32, 0));
            shin.addOrReplaceChild("foot", box(CubeListBuilder.create(), 330, 110, -6, 0, -12, 12, 4, 16), at(0, 30, 2));
        }

        PartDefinition abdomen = hips.addOrReplaceChild("abdomen", box(CubeListBuilder.create(), 0, 760, -16, -16, -6, 32, 16, 12),
                at(0, -6, 0, 0.03F, 0, 0));
        CubeListBuilder chestB = box(CubeListBuilder.create(), 0, 872, -22, -22, -8, 44, 22, 16);
        // bone grown over the chest: a breastplate down the middle, a collar across the top
        chestB = bone(chestB, 0, 140, -5, -20, -9.5F, 10, 18, 2);
        chestB = bone(chestB, 60, 140, -20, -22.5F, -9, 40, 3, 4);
        // and a ridge of plates down the back
        for (int i = 0; i < 4; i++) {
            chestB = bone(chestB, 200 + i * 40, 140, -3, -21 + i * 5.5F, 7.5F, 6, 4, 3);
        }
        PartDefinition chest = abdomen.addOrReplaceChild("chest", chestB, at(0, -16, 0, 0.06F, 0, 0));
        PartDefinition neck = chest.addOrReplaceChild("neck", box(CubeListBuilder.create(), 560, 180, -4.5F, -7, -4.5F, 9, 7, 9),
                at(0, -22, -1, -0.06F, 0, 0));
        CubeListBuilder headB = box(CubeListBuilder.create(), 0, 300, -7.5F, -17, -8, 15, 17, 16);
        headB = box(headB, 260, 300, -8, -18, -8.5F, 16, 6, 17);
        // a brow of bone, and cheek plates
        headB = bone(headB, 0, 210, -8, -12, -9, 16, 3, 3);
        headB = bone(headB, 120, 210, -8.5F, -8, -8.5F, 3, 5, 8);
        headB = bone(headB, 120, 210, 5.5F, -8, -8.5F, 3, 5, 8);
        PartDefinition head = neck.addOrReplaceChild("head", headB, at(0, -6, 0));
        // the jaw: bone, heavy, jutting past the face
        head.addOrReplaceChild("jaw", bone(CubeListBuilder.create(), 200, 210, -7.5F, 0, -11, 15, 6, 13), at(0, -3, 0.5F));
        // the crown: two antlered beams sweeping up and out, tines along them
        for (int s = -1; s <= 1; s += 2) {
            PartDefinition beam = head.addOrReplaceChild(s < 0 ? "antler_r" : "antler_l",
                    bone(CubeListBuilder.create(), 0, 260, -1.5F, -22, -1.5F, 3, 22, 3), at(s * 5.5F, -17, -1, -0.25F, 0, s * 0.55F));
            for (int k = 0; k < 3; k++) {
                beam.addOrReplaceChild("tine" + k, bone(CubeListBuilder.create(), 40, 260, -1, -9 + k, -1, 2, 9 - k, 2),
                        at(0, -6 - k * 6.5F, 0, -0.5F + k * 0.15F, 0, -s * (0.7F + k * 0.12F)));
            }
            beam.addOrReplaceChild("tip", bone(CubeListBuilder.create(), 40, 260, -1, -8, -1, 2, 8, 2), at(0, -21, 0, 0.2F, 0, s * 0.35F));
        }

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "arm_r" : "arm_l";
            CubeListBuilder upper = box(CubeListBuilder.create(), 0, 480, -5, -2, -5, 10, 32, 10);
            upper = box(upper, 170, 480, -6, -4, -6, 12, 10, 12);
            // a pauldron of bone on each shoulder
            upper = bone(upper, 0, 20, -7.5F, -7, -7.5F, 15, 7, 15);
            PartDefinition arm = chest.addOrReplaceChild(n, upper, at(s * 27, -18, 0));
            PartDefinition fore = arm.addOrReplaceChild("fore",
                    bone(box(CubeListBuilder.create(), 370, 480, -4.5F, 0, -4.5F, 9, 30, 9), 200, 40, -5.5F, 14, -5.5F, 11, 12, 11), at(0, 31, 0));
            CubeListBuilder hand = box(CubeListBuilder.create(), 520, 480, -5, 0, -4, 10, 11, 8);
            // long fingers of bare bone
            for (int f = 0; f < 4; f++) {
                hand = bone(hand, 100, 260, -4.6F + f * 2.4F, 10.5F, -3.5F, 2, f == 1 || f == 2 ? 16 : 13, 2.5F);
            }
            hand = bone(hand, 100, 260, s < 0 ? 4.2F : -6.2F, 3, -5.5F, 2, 11, 2.5F);
            fore.addOrReplaceChild("hand", hand, at(0, 30, 0));
        }
        return LayerDefinition.create(mesh, 1024, 1024);
    }

    /**
     * Poses the giant for a moment of its walk: a slow, heavy, slightly wrong walk, the arms hanging, the head lolling.
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
        thighL.zRot = -0.03F;
        thighR.zRot = 0.03F;

        hips.y = 62 * U - 3.0F * U * (1 - Math.abs(c));
        hips.zRot = 0.05F * c;
        hips.yRot = 0.07F * c;
        chest.yRot = -0.1F * c;
        chest.zRot = -0.03F * c;

        // arms: dead weight, swinging opposite the legs and lagging behind them
        float lag = (float) Math.cos(a - 0.6);
        armL.xRot = 0.36F * lag - 0.05F;
        armR.xRot = -0.36F * lag - 0.05F;
        armL.zRot = -0.08F;
        armR.zRot = 0.08F;
        foreL.xRot = -0.18F - 0.15F * Math.max(0, lag);
        foreR.xRot = -0.18F - 0.15F * Math.max(0, -lag);
        handL.xRot = -0.1F;
        handR.xRot = -0.1F;

        // the head: lolling, tilted, turning to stare; each giant its own habit
        float habit = (seed * 0.618F) % 1;
        head.yRot = 0.32F * Mth.sin(time * 0.3F + seed * 1.7F);
        head.zRot = (habit - 0.5F) * 0.5F + 0.06F * Mth.sin(time * 0.4F + seed);
        head.xRot = -0.04F + 0.06F * Mth.sin(time * 0.5F + seed);
        jaw.xRot = 0.04F + 0.05F * Math.max(0, Mth.sin(time * 0.7F + seed * 2.3F));

        if (rising < 1) {
            // clawing up out of the earth: arms raised and reaching, head thrown back
            float k = 1 - rising;
            armL.xRot = Mth.lerp(k, armL.xRot, -2.6F);
            armR.xRot = Mth.lerp(k, armR.xRot, -2.4F);
            foreL.xRot = Mth.lerp(k, foreL.xRot, -0.6F);
            foreR.xRot = Mth.lerp(k, foreR.xRot, -0.7F);
            head.xRot = Mth.lerp(k, head.xRot, -0.6F);
            jaw.xRot = Mth.lerp(k, jaw.xRot, 0.3F);
        }
    }
}
