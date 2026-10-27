package com.ironlung.item;

import com.ironlung.IronLungMod;
import com.ironlung.mission.MissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Sealed survey orders. Using them puts you in the submarine. */
public class DiveOrdersItem extends Item {
    public DiveOrdersItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (level.dimension().equals(IronLungMod.DIMENSION) || MissionManager.hasMission(sp)) {
                sp.displayClientMessage(Component.translatable("ironlung.msg.already_diving").withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResultHolder.fail(stack);
            }
            long seed = level.getRandom().nextLong();
            if (MissionManager.startMission(sp, seed)) {
                if (!sp.getAbilities().instabuild) stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ironlung.dive_orders.desc").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
