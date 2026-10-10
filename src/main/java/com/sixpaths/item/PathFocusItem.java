package com.sixpaths.item;

import com.sixpaths.server.PathsLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Six Paths focus: a black chakra receiver. Right-click casts, sneak + right-click picks a technique. */
public class PathFocusItem extends Item {
    public PathFocusItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.sixpaths.client.ClientHooks.openSelector());
        } else if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PathsLogic.cast(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click: use the selected technique").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + right-click: technique wheel").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + scroll: cycle techniques").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("17 techniques across the Six Paths").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
