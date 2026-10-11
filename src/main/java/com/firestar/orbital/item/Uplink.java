package com.firestar.orbital.item;

import com.firestar.orbital.common.Cannon;
import com.firestar.orbital.config.OConfig;
import com.firestar.orbital.registry.ModSounds;
import com.firestar.orbital.server.Strikes;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Orbital Uplink: a rugged handset with a flip-up antenna. Right-click marks the ground in your crosshair for the
 * selected cannon; sneak + right-click changes cannon.
 */
public final class Uplink extends Item {
    private static final String KEY = "cannon";

    public Uplink() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant());
    }

    public static Cannon selected(ItemStack stack) {
        return Cannon.of(stack.getOrCreateTag().getInt(KEY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            Cannon next = selected(stack).next();
            stack.getOrCreateTag().putInt(KEY, next.ordinal());
            if (!level.isClientSide) {
                level.playSound(null, player.blockPosition(), ModSounds.SELECT.get(), SoundSource.PLAYERS, 0.8F, 1.0F + next.ordinal() * 0.15F);
                player.displayClientMessage(Component.literal(next.code + "  " + next.title).withStyle(s -> s.withColor(next.color)), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (OConfig.SERVER.opsOnly.get() && !sp.hasPermissions(2)) {
                sp.displayClientMessage(Component.literal("Only operators can use the uplink.").withStyle(ChatFormatting.RED), true);
            } else if (Strikes.fire(sp, selected(stack))) {
                level.playSound(null, player.blockPosition(), ModSounds.ARM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        Cannon c = selected(stack);
        return Component.translatable(getDescriptionId(stack)).append(Component.literal(" · " + c.code + " " + c.title).withStyle(s -> s.withColor(c.color)));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        Cannon c = selected(stack);
        tip.add(Component.literal("Channel: " + c.code + " " + c.title).withStyle(s -> s.withColor(c.color)));
        if (c == Cannon.HELIOS) {
            tip.add(Component.literal("A ring of mirrors in orbit pours the sun down one lance.").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Once it lands, it follows your crosshair: steer the cut.").withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(Component.literal("A breach opens beside the Earth and another Earth comes through.").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("Where they touch, its land replaces ours, upside down.").withStyle(ChatFormatting.GRAY));
        }
        tip.add(Component.literal("Right-click: fire at your crosshair").withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.literal("Sneak + right-click: change channel").withStyle(ChatFormatting.DARK_GRAY));
    }
}
