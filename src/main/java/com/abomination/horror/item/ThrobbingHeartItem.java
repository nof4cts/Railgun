package com.abomination.horror.item;

import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/** Crafted from Heart Fragments. Pressed to the ground at night (or underground), it calls the Awakened Abomination. */
public class ThrobbingHeartItem extends LoreItem {
    public ThrobbingHeartItem(Properties properties) {
        super(properties, true);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        boolean dark = !level.isDay() || level.getBrightness(LightLayer.SKY, pos) < 4;
        if (!dark) {
            if (player != null) player.displayClientMessage(Component.translatable("item.abomination.throbbing_heart.daylight").withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResult.FAIL;
        }
        if (!level.getEntitiesOfClass(AbominationEntity.class, new AABB(pos).inflate(96.0D), AbominationEntity::isAwakened).isEmpty()) {
            if (player != null) player.displayClientMessage(Component.translatable("item.abomination.throbbing_heart.busy").withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResult.FAIL;
        }
        AbominationEntity boss = ModEntities.ABOMINATION.get().create(level);
        if (boss == null) return InteractionResult.FAIL;
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        boss.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null, null);
        boss.setAwakened(true);
        level.addFreshEntity(boss);
        if (player != null) {
            boss.beginEmergence(player);
            player.displayClientMessage(Component.translatable("item.abomination.throbbing_heart.answer").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            if (!player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
