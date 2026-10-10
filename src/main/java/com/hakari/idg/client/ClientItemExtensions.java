package com.hakari.idg.client;

import com.hakari.idg.network.StatePacket;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

public final class ClientItemExtensions {

    public static IClientItemExtensions create() {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                StatePacket s = ClientState.get(entity.getId());
                if (s == null) return null;
                return switch (s.pose) {
                    case 1 -> ArmPoses.SIGN;
                    case 2 -> ArmPoses.PUNCH;
                    case 3 -> ArmPoses.GUARD;
                    default -> null;
                };
            }
        };
    }

    private ClientItemExtensions() {}
}
