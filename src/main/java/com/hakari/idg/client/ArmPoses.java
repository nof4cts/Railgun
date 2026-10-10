package com.hakari.idg.client;

import net.minecraft.client.model.HumanoidModel;

/** Custom body poses shown on the player model while a scroll is held. */
public final class ArmPoses {

    /** Both hands pressed together at the chest — the domain hand sign. */
    public static final HumanoidModel.ArmPose SIGN = HumanoidModel.ArmPose.create("HAKARI_SIGN", true, (model, entity, arm) -> {
        model.rightArm.xRot = -1.3f;
        model.rightArm.yRot = -0.62f;
        model.rightArm.zRot = 0.1f;
        model.leftArm.xRot = -1.3f;
        model.leftArm.yRot = 0.62f;
        model.leftArm.zRot = -0.1f;
    });

    /** Right fist cocked back, left arm guiding — Rough Energy windup / barrage stance. */
    public static final HumanoidModel.ArmPose PUNCH = HumanoidModel.ArmPose.create("HAKARI_PUNCH", true, (model, entity, arm) -> {
        model.rightArm.xRot = -0.25f;
        model.rightArm.yRot = 0.85f;
        model.rightArm.zRot = 0.55f;
        model.leftArm.xRot = -1.45f;
        model.leftArm.yRot = 0.25f;
        model.leftArm.zRot = 0f;
    });

    /** Forearms crossed — the door counter guard. */
    public static final HumanoidModel.ArmPose GUARD = HumanoidModel.ArmPose.create("HAKARI_GUARD", true, (model, entity, arm) -> {
        model.rightArm.xRot = -1.55f;
        model.rightArm.yRot = -0.75f;
        model.rightArm.zRot = 0f;
        model.leftArm.xRot = -1.45f;
        model.leftArm.yRot = 0.75f;
        model.leftArm.zRot = 0f;
    });

    private ArmPoses() {}
}
