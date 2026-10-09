package com.railgun;

import com.railgun.net.Net;
import com.railgun.net.ShotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;

public class RailgunItem extends Item {
    public static final int MIN_CHARGE = 14;   // ticks held before it will fire
    public static final int FULL_CHARGE = 40;  // ticks for 100% power
    public static final int COOLDOWN = 70;
    public static final double RANGE = 256.0;

    public RailgunItem(Properties props) { super(props); }

    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.NONE; }
    @Override public int getUseDuration(ItemStack s) { return 72000; }
    @Override public boolean isValidRepairItem(ItemStack a, ItemStack b) { return b.is(Items.DIAMOND) || b.is(Items.NETHERITE_SCRAP); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 2.0f);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack stack, int remaining) {
        if (level.isClientSide) return;
        int used = getUseDuration(stack) - remaining;
        if (used % 4 == 0 && used <= FULL_CHARGE + 4) {
            float p = Math.min(1f, used / (float) FULL_CHARGE);
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6f, 0.5f + p * 1.5f);
        }
        if (used == FULL_CHARGE) {
            level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.6f, 1.9f);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity e, int remaining) {
        if (!(e instanceof Player player) || level.isClientSide) return;
        int used = getUseDuration(stack) - remaining;
        if (used < MIN_CHARGE) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.6f);
            return;
        }
        fire((ServerLevel) level, player, stack, Math.min(1f, used / (float) FULL_CHARGE));
    }

    private void fire(ServerLevel level, Player player, ItemStack stack, float power) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(RANGE));
        Vec3 cur = eye;
        Vec3 finalEnd = end;
        int face = -1;

        // Trace. Weak blocks (glass, leaves, wool...) are punched straight through.
        for (int i = 0; i < 10; i++) {
            BlockHitResult r = level.clip(new ClipContext(cur, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (r.getType() == HitResult.Type.MISS) { finalEnd = end; break; }
            BlockPos bp = r.getBlockPos();
            BlockState bs = level.getBlockState(bp);
            float hardness = bs.getDestroySpeed(level, bp);
            boolean weak = hardness >= 0f && hardness <= 1.0f && !bs.hasBlockEntity();
            if (weak && i < 6 && level.mayInteract(player, bp)) {
                level.destroyBlock(bp, true, player);
                cur = r.getLocation().add(look.scale(0.02));
                continue;
            }
            finalEnd = r.getLocation();
            face = r.getDirection().get3DDataValue();
            break;
        }

        // Pierce every living thing along the line.
        AABB box = new AABB(eye, finalEnd).inflate(1.5);
        List<Entity> list = level.getEntities(player, box,
                en -> en.isAlive() && en.isPickable() && !en.isSpectator() && en instanceof LivingEntity);
        float dmg = 16f + 24f * power;
        int hits = 0;
        for (Entity t : list) {
            AABB bb = t.getBoundingBox().inflate(0.35);
            if (bb.clip(eye, finalEnd).isPresent() || bb.contains(eye)) {
                t.invulnerableTime = 0;
                if (t.hurt(level.damageSources().playerAttack(player), dmg)) {
                    hits++;
                    Vec3 k = look.scale(0.9 + 1.4 * power);
                    t.setDeltaMovement(t.getDeltaMovement().add(k.x, 0.35, k.z));
                    t.hurtMarked = true;
                }
            }
        }

        // Muzzle position (where the beam visually starts)
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        InteractionHand hand = player.getUsedItemHand();
        boolean rightHanded = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        double side = rightHanded ? 1.0 : -1.0;
        Vec3 muzzle = eye.add(look.scale(1.3)).add(right.scale(0.28 * side)).add(0, -0.22, 0);

        // Layered sound design out of vanilla sounds
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4f, 1.5f);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3f, 1.9f);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 3f, 1.3f);
        level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.PLAYERS, 3f, 0.6f);
        level.playSound(null, finalEnd.x, finalEnd.y, finalEnd.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3f, 1.4f);
        level.playSound(null, finalEnd.x, finalEnd.y, finalEnd.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3f, 1.6f);

        final Vec3 fe = finalEnd;
        Net.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                        new PacketDistributor.TargetPoint(eye.x, eye.y, eye.z, 320, level.dimension())),
                new ShotPacket(muzzle.x, muzzle.y, muzzle.z, fe.x, fe.y, fe.z, player.getId(), power, face, hits));

        if (!player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer;
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.railgun.client.RailgunRenderer();
                return renderer;
            }
        });
    }
}
