package com.firestar.worldbreaker.item;

import com.firestar.worldbreaker.server.Marches;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Blow it, looking the way they should come from: the march rises on the horizon and walks over everything in its path. */
public final class HeraldsHorn extends Item {
    public HeraldsHorn() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            Marches.call(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click: call the march").withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("They rise ahead of you and walk toward you,").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("flattening everything in their path.").withStyle(ChatFormatting.GRAY));
    }
}
