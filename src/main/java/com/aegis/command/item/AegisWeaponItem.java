package com.aegis.command.item;

import com.aegis.command.client.ClientWeaponHooks;
import com.aegis.command.client.render.WeaponRenderer;
import com.aegis.command.AegisConfig;
import com.aegis.command.entity.EnergyBolt;
import com.aegis.command.network.AegisNetwork;
import com.aegis.command.network.Packets;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.PacketDistributor;
import com.aegis.command.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Consumer;

/**
 * One class drives all three arsenal weapons; behaviour branches on {@link WeaponType}.
 * Ammo, reload state and cosmetics live in the stack's NBT. Logic runs on both sides so the
 * client can animate instantly; the server stays authoritative.
 */
public class AegisWeaponItem extends Item {
    public static final int TITAN_MIN_CHARGE = 14;
    public static final int TITAN_FULL_CHARGE = 34;

    public final WeaponType type;

    public AegisWeaponItem(WeaponType type, Properties props) {
        super(props);
        this.type = type;
    }

    // ------------------------------------------------------------------ stack helpers

    public static ItemStack create(WeaponType type, int finish, int energy) {
        ItemStack s = new ItemStack(ModItems.weapon(type));
        CompoundTag t = s.getOrCreateTag();
        t.putInt("Finish", finish);
        t.putInt("Energy", energy);
        t.putInt("Ammo", type.capacity);
        return s;
    }

    public static WeaponType typeOf(ItemStack s) {
        return s.getItem() instanceof AegisWeaponItem w ? w.type : null;
    }

    public static int ammo(ItemStack s) {
        WeaponType t = typeOf(s);
        CompoundTag tag = s.getTag();
        if (tag != null && tag.contains("Ammo")) return tag.getInt("Ammo");
        return t == null ? 0 : t.capacity;
    }

    public static int finish(ItemStack s) {
        WeaponType t = typeOf(s);
        CompoundTag tag = s.getTag();
        if (tag != null && tag.contains("Finish")) return Math.floorMod(tag.getInt("Finish"), WeaponType.FINISH_COLORS.length);
        return t == null ? 0 : t.defaultFinish;
    }

    public static int energy(ItemStack s) {
        WeaponType t = typeOf(s);
        CompoundTag tag = s.getTag();
        if (tag != null && tag.contains("Energy")) return Math.floorMod(tag.getInt("Energy"), WeaponType.ENERGY_COLORS.length);
        return t == null ? 0 : t.defaultEnergy;
    }

    public static int energyColor(ItemStack s) {
        return WeaponType.ENERGY_COLORS[energy(s)];
    }

    public static boolean isReloading(ItemStack s, Level level) {
        CompoundTag tag = s.getTag();
        return tag != null && tag.getLong("ReloadUntil") > level.getGameTime();
    }

    public static float reloadProgress(ItemStack s, Level level, float partial) {
        WeaponType t = typeOf(s);
        CompoundTag tag = s.getTag();
        if (t == null || tag == null) return 1f;
        long until = tag.getLong("ReloadUntil");
        double left = until - (level.getGameTime() + partial);
        if (left <= 0) return 1f;
        return (float) Math.max(0, 1 - left / t.reloadTicks);
    }

    private static Vector3f rgb(int c) {
        return new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (isReloading(stack, level)) return InteractionResultHolder.fail(stack);
        if (ammo(stack) <= 0) {
            startReload(stack, level, player);
            return InteractionResultHolder.fail(stack);
        }
        if (type != WeaponType.SPECTRE_7 && type != WeaponType.TITAN_X) {
            // semi-automatic: PHANTOM-9, NOVA-RAIL, HELIX-4, OBLIVION
            tryFire(level, player, stack, 1f);
            return InteractionResultHolder.consume(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (type == WeaponType.SPECTRE_7) {
            if (used % 2 == 0) {
                if (!tryFire(level, entity, stack, 1f) && ammo(stack) <= 0) entity.releaseUsingItem();
            }
        } else if (type == WeaponType.TITAN_X && level.isClientSide) {
            float charge = Math.min(1f, used / (float) TITAN_FULL_CHARGE);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWeaponHooks.onCharge(entity, stack, charge, used));
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (type != WeaponType.TITAN_X) return;
        int used = getUseDuration(stack) - timeLeft;
        if (used < TITAN_MIN_CHARGE) return;
        float charge = Math.min(1f, used / (float) TITAN_FULL_CHARGE);
        tryFire(level, entity, stack, 0.45f + 0.55f * charge);
    }

    private boolean tryFire(Level level, LivingEntity shooter, ItemStack stack, float charge) {
        CompoundTag tag = stack.getOrCreateTag();
        long now = level.getGameTime();
        if (isReloading(stack, level) || now < tag.getLong("NextShot")) return false;
        int ammo = ammo(stack);
        if (ammo <= 0) {
            startReload(stack, level, shooter);
            return false;
        }
        tag.putInt("Ammo", ammo - 1);
        tag.putLong("NextShot", now + type.fireDelay);
        if (level.isClientSide) {
            final float c = charge;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWeaponHooks.onFire(shooter, stack, type, c));
        } else {
            serverShot((ServerLevel) level, shooter, stack, charge);
        }
        if (ammo - 1 <= 0 && !type.heavy) {
            startReload(stack, level, shooter);
        }
        return true;
    }

    private void serverShot(ServerLevel level, LivingEntity shooter, ItemStack stack, float charge) {
        int color = energyColor(stack);
        Vec3 look = shooter.getViewVector(1f);
        Vec3 eye = shooter.getEyePosition();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = eye.add(look.scale(0.9)).add(right.scale(0.28)).add(0, -0.22, 0);
        switch (type) {
            case PHANTOM_9 -> {
                double range = 64;
                Vec3 end = eye.add(look.scale(range));
                BlockHitResult bhr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
                Vec3 hit = bhr.getType() != HitResult.Type.MISS ? bhr.getLocation() : end;
                AABB box = shooter.getBoundingBox().expandTowards(look.scale(range)).inflate(1);
                EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, shooter, eye, hit, box,
                        e -> !e.isSpectator() && e.isPickable() && e != shooter);
                if (ehr != null) {
                    hit = ehr.getLocation();
                    Entity target = ehr.getEntity();
                    target.invulnerableTime = 0;
                    target.hurt(shooter instanceof Player p ? level.damageSources().playerAttack(p)
                            : level.damageSources().mobAttack(shooter), type.damage);
                }
                tracer(level, muzzle, hit, color, (byte) 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 6, 0.1, 0.1, 0.1, 0.25);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.55f, 2.0f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.5f, 1.9f);
            }
            case SPECTRE_7 -> {
                EnergyBolt bolt = new EnergyBolt(level, shooter, EnergyBolt.SPECTRE, color, type.damage, 0.55f);
                bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
                bolt.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0f, 3.6f, 2.2f);
                level.addFreshEntity(bolt);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.25f, 2.0f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            case NOVA_RAIL -> {
                // piercing lance: hits every entity along the line, then detonates on the first block
                double range = 96;
                Vec3 end = eye.add(look.scale(range));
                BlockHitResult bhr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
                Vec3 hit = bhr.getType() != HitResult.Type.MISS ? bhr.getLocation() : end;
                AABB box = new AABB(eye, hit).inflate(1.2);
                for (Entity target : level.getEntities(shooter, box, e -> !e.isSpectator() && e.isPickable())) {
                    if (target.getBoundingBox().inflate(0.6).clip(eye, hit).isPresent()) {
                        target.invulnerableTime = 0;
                        target.hurt(shooter instanceof Player p ? level.damageSources().playerAttack(p)
                                : level.damageSources().mobAttack(shooter), type.damage);
                        target.push(look.x * 1.4, 0.35, look.z * 1.4);
                    }
                }
                if (bhr.getType() != HitResult.Type.MISS) {
                    Explosion.BlockInteraction bi = AegisConfig.COMMON.weaponBlockDamage.get()
                            ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP;
                    Explosion ex = new Explosion(level, shooter, level.damageSources().explosion(shooter, shooter), null,
                            hit.x, hit.y, hit.z, 2.4f, false, bi);
                    if (!ForgeEventFactory.onExplosionStart(level, ex)) {
                        ex.explode();
                        ex.finalizeExplosion(false);
                    }
                }
                tracer(level, muzzle, hit, color, (byte) 1);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.9f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.55f, 1.8f);
                level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.3f);
            }
            case HELIX_4 -> {
                for (int i = 0; i < 7; i++) {
                    EnergyBolt bolt = new EnergyBolt(level, shooter, EnergyBolt.SPECTRE, color, type.damage, 0.6f);
                    bolt.setPos(muzzle.x, muzzle.y, muzzle.z);
                    bolt.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0f, 3.0f, 9.0f);
                    level.addFreshEntity(bolt);
                }
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 0.7f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.2f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 0.5f, 1.6f);
            }
            case OBLIVION -> {
                EnergyBolt orb = new EnergyBolt(level, shooter, EnergyBolt.SINGULARITY, color, type.damage, 1.2f);
                orb.setPos(muzzle.x, muzzle.y, muzzle.z);
                orb.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0f, 1.5f, 0.1f);
                level.addFreshEntity(orb);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.9f, 1.6f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
            }
            case TITAN_X -> {
                EnergyBolt shell = new EnergyBolt(level, shooter, EnergyBolt.TITAN, color, type.damage * charge, 0.7f + charge * 0.6f);
                shell.setPos(muzzle.x, muzzle.y, muzzle.z);
                shell.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0f, 2.4f, 0.2f);
                level.addFreshEntity(shell);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7f, 1.5f);
                level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5f, 1.7f);
            }
        }
    }

    private static void tracer(ServerLevel level, Vec3 a, Vec3 b, int color, byte kind) {
        AegisNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(a.x, a.y, a.z, 160, level.dimension())),
                new Packets.Tracer(a.x, a.y, a.z, b.x, b.y, b.z, color, kind));
    }

    public void startReload(ItemStack stack, Level level, LivingEntity entity) {
        if (isReloading(stack, level) || ammo(stack) >= type.capacity) return;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong("ReloadUntil", level.getGameTime() + type.reloadTicks);
        tag.putBoolean("Reloading", true);
        if (entity.isUsingItem()) entity.releaseUsingItem();
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientWeaponHooks.onReload(entity, type));
        } else {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6f, 1.7f);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("Reloading")) return;
        if (level.getGameTime() >= tag.getLong("ReloadUntil")) {
            tag.putInt("Ammo", type.capacity);
            tag.remove("Reloading");
            if (!level.isClientSide) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4f, 2.0f);
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.6f, 1.5f);
            }
        }
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level,
                                  net.minecraft.core.BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(type.classification).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("aegiscommand.tooltip.ammo", ammo(stack), type.capacity).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(WeaponType.FINISH_NAMES[finish(stack)] + " / " + WeaponType.ENERGY_NAMES[energy(stack)])
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("aegiscommand.tooltip.equip").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return WeaponRenderer.get();
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }
}
