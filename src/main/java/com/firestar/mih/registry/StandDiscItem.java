package com.firestar.mih.registry;

import com.firestar.mih.server.StandUsers;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** The Stand DISC: push it into your head to awaken Made in Heaven. Sneak-use with the Stand to pull it back out. */
public final class StandDiscItem extends Item {
    public StandDiscItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        if (StandUsers.has(sp)) {
            sp.displayClientMessage(Component.literal("You already wield Made in Heaven.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        StandUsers.grant(sp, true);
        com.firestar.styles.Styles.takeUpHeaven(sp);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), ModSounds.SUMMON.get(), SoundSource.PLAYERS, 1.0F, 0.8F);
        sp.displayClientMessage(Component.literal("「メイド・イン・ヘブン」 Made in Heaven has awakened.").withStyle(ChatFormatting.AQUA), false);
        sp.displayClientMessage(Component.literal("Q summon · LMB punch · E barrage · R finisher · T knives · Y speed slice · G heaven's judgement · Z double accel · X infinite speed · H time acceleration")
                .withStyle(ChatFormatting.DARK_AQUA), false);
        if (!sp.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Stand: Made in Heaven").withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("Use to awaken it.").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Sneak + Q with the Stand out to give it back up.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
