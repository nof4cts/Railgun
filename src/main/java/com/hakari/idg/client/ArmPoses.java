package com.hakari.idg.client;

import net.minecraft.client.model.HumanoidModel.ArmPose;

public final class ArmPoses {
   public static final ArmPose SIGN = ArmPose.create("HAKARI_SIGN", true, (model, entity, arm) -> {
      model.rightArm.xRot = -1.3F;
      model.rightArm.yRot = -0.62F;
      model.rightArm.zRot = 0.1F;
      model.leftArm.xRot = -1.3F;
      model.leftArm.yRot = 0.62F;
      model.leftArm.zRot = -0.1F;
   });
   public static final ArmPose PUNCH = ArmPose.create("HAKARI_PUNCH", true, (model, entity, arm) -> {
      model.rightArm.xRot = -0.25F;
      model.rightArm.yRot = 0.85F;
      model.rightArm.zRot = 0.55F;
      model.leftArm.xRot = -1.45F;
      model.leftArm.yRot = 0.25F;
      model.leftArm.zRot = 0.0F;
   });
   public static final ArmPose GUARD = ArmPose.create("HAKARI_GUARD", true, (model, entity, arm) -> {
      model.rightArm.xRot = -1.55F;
      model.rightArm.yRot = -0.75F;
      model.rightArm.zRot = 0.0F;
      model.leftArm.xRot = -1.45F;
      model.leftArm.yRot = 0.75F;
      model.leftArm.zRot = 0.0F;
   });

   private ArmPoses() {
   }
}
