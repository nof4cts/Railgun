package com.abomination.horror.entity;

import com.abomination.horror.config.AbomConfig;
import com.abomination.horror.entity.ai.*;
import com.abomination.horror.finisher.FinisherManager;
import com.abomination.horror.network.ModNetwork;
import com.abomination.horror.registry.ModBlocks;
import com.abomination.horror.registry.ModItems;
import com.abomination.horror.registry.ModParticles;
import com.abomination.horror.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * THE ABOMINATION.
 * <p>
 * AI runs as a four-phase state machine (Observe -> Stalk -> Hunt -> Nightmare) layered on top of three
 * boss stages driven by health. Animation is fully procedural on the client; the server only syncs a small
 * set of flags (animation state + variant, phase, sprint/climb/frozen, warp, boss stage).
 */
public class AbominationEntity extends Monster {
    // ---- animation states (synced) ----
    public static final int ANIM_IDLE = 0;
    public static final int ANIM_DETECT = 1;
    public static final int ANIM_ATTACK = 2;
    public static final int ANIM_FINISHER = 3;
    public static final int ANIM_ROAR = 4;
    public static final int ANIM_EMERGE = 5;
    public static final int ANIM_LEAP = 6;
    public static final int ANIM_SLAM = 7;
    public static final int ANIM_JUMPSCARE = 8;
    public static final int JUMPSCARE_TICKS = 36;
    private static final int JUMPSCARE_COOLDOWN = 2400;

    // ---- AI phases (synced) ----
    public static final int PHASE_OBSERVE = 0;
    public static final int PHASE_STALK = 1;
    public static final int PHASE_HUNT = 2;
    public static final int PHASE_NIGHTMARE = 3;

    public static final int ATTACK_SWIPE = 0;
    public static final int ATTACK_SLAM = 1;

    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM_VARIANT = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOSS_STAGE = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AWAKENED = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DASHING = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FROZEN = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_WARP = SynchedEntityData.defineId(AbominationEntity.class, EntityDataSerializers.FLOAT);

    private final ServerBossEvent bossEvent = (ServerBossEvent) new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);

    // ---- server state ----
    private int animTimer;
    private int phaseTicks;
    public int attackCooldown;
    public int currentAttack = ATTACK_SWIPE;
    private int warpTimer;
    private int jumpscareCooldown = 400;
    private int nightmareFxCooldown = 100;
    private int corruptionCooldown = 200;
    private int vanishCooldown;
    private int observeDuration = 500;
    private boolean stageRoar;
    private float finisherYaw;
    private final List<BlockPos> arenaCorruption = new ArrayList<>();
    private final java.util.Map<java.util.UUID, Long> lastJumpscare = new java.util.HashMap<>();
    private int sightJumpscareRetry;

    // ---- client animation state ----
    public int animStartTick;
    public float sprintBlend, prevSprintBlend;
    public float climbBlend, prevClimbBlend;
    public float frozenBlend, prevFrozenBlend;
    public float warpClient, prevWarp;

    public AbominationEntity(EntityType<? extends AbominationEntity> type, Level level) {
        super(type, level);
        this.xpReward = 80;
        this.setMaxUpStep(1.5F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ATTACK_DAMAGE, 14.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 72.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9D)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2D);
    }

    public static boolean checkAbominationSpawnRules(EntityType<AbominationEntity> type, ServerLevelAccessor level,
                                                     MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            if (!AbomConfig.COMMON.naturalSpawning.get()) return false;
            if (!level.getEntitiesOfClass(AbominationEntity.class, new AABB(pos).inflate(128.0D)).isEmpty()) return false;
        }
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, ANIM_IDLE);
        this.entityData.define(DATA_ANIM_VARIANT, 0);
        this.entityData.define(DATA_PHASE, PHASE_OBSERVE);
        this.entityData.define(DATA_BOSS_STAGE, 1);
        this.entityData.define(DATA_AWAKENED, false);
        this.entityData.define(DATA_DASHING, false);
        this.entityData.define(DATA_FROZEN, false);
        this.entityData.define(DATA_CLIMBING, false);
        this.entityData.define(DATA_WARP, 0.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ANIM.equals(key) || DATA_ANIM_VARIANT.equals(key)) {
            this.animStartTick = this.tickCount;
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LockedGoal(this));
        this.goalSelector.addGoal(2, new AbomAttackGoal(this));
        this.goalSelector.addGoal(3, new HuntGoal(this));
        this.goalSelector.addGoal(4, new StalkGoal(this));
        this.goalSelector.addGoal(5, new ObserveGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false, null));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        double mult = AbomConfig.COMMON.healthMultiplier.get();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300.0D * mult);
        this.setHealth(this.getMaxHealth());
        this.observeDuration = 400 + this.random.nextInt(500);
        return result;
    }

    // =====================================================================================
    // Synced accessors
    // =====================================================================================
    public int getAnim() { return this.entityData.get(DATA_ANIM); }
    public int getAnimVariant() { return this.entityData.get(DATA_ANIM_VARIANT); }
    public int getPhase() { return this.entityData.get(DATA_PHASE); }
    public int getBossStage() { return this.entityData.get(DATA_BOSS_STAGE); }
    public boolean isAwakened() { return this.entityData.get(DATA_AWAKENED); }
    public boolean isDashing() { return this.entityData.get(DATA_DASHING); }
    public boolean isFrozenByGaze() { return this.entityData.get(DATA_FROZEN); }
    public boolean isClimbing() { return this.entityData.get(DATA_CLIMBING); }
    public float getWarp() { return this.entityData.get(DATA_WARP); }

    public void setAnim(int anim, int duration) {
        this.entityData.set(DATA_ANIM_VARIANT, this.getAnimVariant() + 1);
        this.entityData.set(DATA_ANIM, anim);
        this.animTimer = duration;
    }

    public void setPhase(int phase) {
        int old = this.getPhase();
        this.entityData.set(DATA_PHASE, phase);
        this.phaseTicks = 0;
        if (phase != old) onPhaseChanged(old, phase);
    }

    public void setDashing(boolean b) { this.entityData.set(DATA_DASHING, b); }
    public void setFrozenByGaze(boolean b) { this.entityData.set(DATA_FROZEN, b); }
    public void setClimbing(boolean b) { this.entityData.set(DATA_CLIMBING, b); }

    public void setWarp(float warp, int ticks) {
        this.entityData.set(DATA_WARP, warp);
        this.warpTimer = ticks;
    }

    public void setAwakened(boolean awakened) {
        this.entityData.set(DATA_AWAKENED, awakened);
        if (awakened) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(650.0D * AbomConfig.COMMON.healthMultiplier.get());
            this.getAttribute(Attributes.ARMOR).setBaseValue(12.0D);
            this.setHealth(this.getMaxHealth());
            this.setPersistenceRequired();
            this.bossEvent.setName(Component.translatable("entity.abomination.abomination.awakened"));
        }
    }

    /** Animations during which the body is rooted in place and the normal goals are suspended. */
    public boolean isLocked() {
        int a = getAnim();
        return a == ANIM_FINISHER || a == ANIM_ROAR || a == ANIM_EMERGE || a == ANIM_DETECT || a == ANIM_JUMPSCARE;
    }

    public boolean inFinisher() { return getAnim() == ANIM_FINISHER; }

    @Nullable
    public Player targetPlayer() {
        return this.getTarget() instanceof Player p && p.isAlive() && !p.isCreative() && !p.isSpectator() ? p : null;
    }

    // =====================================================================================
    // Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            this.setClimbing(this.horizontalCollision && !this.inFinisher());
            if (this.inFinisher()) {
                this.setYRot(this.finisherYaw);
                this.yBodyRot = this.finisherYaw;
                this.yHeadRot = this.finisherYaw;
                this.setDeltaMovement(0.0D, Math.min(0.0D, this.getDeltaMovement().y), 0.0D);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.animTimer > 0 && --this.animTimer == 0) {
            onAnimFinished(getAnim());
        }
        if (this.attackCooldown > 0) this.attackCooldown--;
        if (this.vanishCooldown > 0) this.vanishCooldown--;
        if (this.warpTimer > 0 && --this.warpTimer == 0) this.entityData.set(DATA_WARP, 0.0F);
        if (getAnim() == ANIM_EMERGE) tickEmergence();
        tickPhase();
        tickBoss();
        tickNightmare();
    }

    private void onAnimFinished(int anim) {
        if (anim == ANIM_ROAR) this.stageRoar = false;
        if (anim == ANIM_JUMPSCARE) {
            // the cutscene is over: now it runs you down, with a beat of head start before it can strike
            this.entityData.set(DATA_ANIM, ANIM_IDLE);
            if (getPhase() < PHASE_HUNT) {
                this.entityData.set(DATA_PHASE, PHASE_HUNT);
                this.phaseTicks = 0;
            }
            this.attackCooldown = 30;
            this.playSound(ModSounds.ROAR.get(), 3.0F, 0.8F);
            return;
        }
        if (anim == ANIM_EMERGE) {
            this.playSound(ModSounds.ROAR.get(), 4.0F, 0.7F);
            shockwave(10.0D, 1.4D, 1.0F);
        }
        this.entityData.set(DATA_ANIM, ANIM_IDLE);
    }

    private void tickPhase() {
        Player t = targetPlayer();
        this.phaseTicks++;
        int phase = getPhase();
        if (t == null) {
            this.setDashing(false);
            this.setFrozenByGaze(false);
            if (!isAwakened() && phase != PHASE_OBSERVE && this.phaseTicks > 300) setPhase(PHASE_OBSERVE);
            return;
        }
        double dist = this.distanceTo(t);
        // first sighting: no attack - it appears in your face, screams, then gives chase
        if (phase < PHASE_HUNT && !isAwakened() && !isLocked() && t instanceof ServerPlayer sp && dist < 56.0D) {
            if (this.sightJumpscareRetry > 0) {
                this.sightJumpscareRetry--;
            } else if (this.hasLineOfSight(sp)) {
                Long last = this.lastJumpscare.get(sp.getUUID());
                long now = this.level().getGameTime();
                if (last == null || now - last > JUMPSCARE_COOLDOWN) {
                    if (sightJumpscare(sp)) return;
                    this.sightJumpscareRetry = 20;
                }
            }
        }
        boolean hurtRecently = this.getLastHurtByMob() != null && this.tickCount - this.getLastHurtByMobTimestamp() < 100;
        switch (phase) {
            case PHASE_OBSERVE -> {
                if (hurtRecently || dist < 9.0D) setPhase(PHASE_HUNT);
                else if (this.phaseTicks > this.observeDuration) setPhase(PHASE_STALK);
            }
            case PHASE_STALK -> {
                if (hurtRecently || dist < 5.0D || this.phaseTicks > 900 || isFleeing(t)) setPhase(PHASE_HUNT);
            }
            case PHASE_HUNT -> {
                if (this.getHealth() < this.getMaxHealth() * 0.5F || this.phaseTicks > 1400) setPhase(PHASE_NIGHTMARE);
                else if (!isAwakened() && dist > 60.0D && this.phaseTicks > 200) setPhase(PHASE_STALK);
            }
            default -> { }
        }
    }

    private boolean isFleeing(Player t) {
        if (!t.isSprinting() || this.phaseTicks < 100) return false;
        Vec3 away = t.position().subtract(this.position());
        return t.getDeltaMovement().dot(away) > 0.0D;
    }

    private void onPhaseChanged(int old, int phase) {
        if (this.level().isClientSide) return;
        Player t = targetPlayer();
        if (phase == PHASE_HUNT && old < PHASE_HUNT) {
            // the world goes quiet... then it screams
            if (t instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
                sp.connection.send(new ClientboundStopSoundPacket(null, SoundSource.AMBIENT));
                ModNetwork.fx(sp, 0.6F, 0.5F, 0.0F, 0);
            }
            this.playSound(ModSounds.SCREAM.get(), 3.0F, 0.8F);
        }
        if (phase == PHASE_NIGHTMARE) {
            this.playSound(ModSounds.ROAR.get(), 4.0F, 0.6F);
            setWarp(1.0F, 50);
            if (t instanceof ServerPlayer sp) ModNetwork.fx(sp, 0.8F, 1.0F, 0.3F, 40);
        }
        if (phase != PHASE_STALK) setFrozenByGaze(false);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        boolean newTarget = target != null && this.getTarget() == null;
        super.setTarget(target);
        if (newTarget && !this.level().isClientSide && !isLocked() && getPhase() == PHASE_OBSERVE) {
            // detection: it freezes completely, then its head creaks round to face you
            this.setAnim(ANIM_DETECT, 34);
            this.getNavigation().stop();
        }
    }

    // =====================================================================================
    // Boss stages
    // =====================================================================================
    private void tickBoss() {
        boolean active = isAwakened() || getPhase() >= PHASE_HUNT;
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 10 == 0 && this.level() instanceof ServerLevel sl) {
            for (ServerPlayer p : sl.players()) {
                double d2 = p.distanceToSqr(this);
                boolean contains = this.bossEvent.getPlayers().contains(p);
                boolean wants = active && d2 < 48 * 48 && (p == this.getTarget() || p.hasLineOfSight(this));
                if (wants && !contains) this.bossEvent.addPlayer(p);
                else if (contains && (!active || d2 > 72 * 72)) this.bossEvent.removePlayer(p);
            }
        }
        float frac = this.getHealth() / this.getMaxHealth();
        int stage = frac > 0.66F ? 1 : frac > 0.33F ? 2 : 3;
        if (stage > getBossStage()) {
            this.entityData.set(DATA_BOSS_STAGE, stage);
            onStageChanged(stage);
        }
    }

    private void onStageChanged(int stage) {
        // temporary mutation between phases
        this.stageRoar = true;
        this.setAnim(ANIM_ROAR, 50);
        this.getNavigation().stop();
        this.setWarp(1.0F, 90);
        this.playSound(ModSounds.ROAR.get(), 5.0F, stage == 3 ? 0.55F : 0.7F);
        shockwave(9.0D, 1.6D, 1.0F);
        breakFragileAround(this.blockPosition(), 5);
        if (getPhase() < PHASE_NIGHTMARE && stage == 3) setPhase(PHASE_NIGHTMARE);
        if (stage >= 2) corruptArena(stage == 3 ? 14 : 8);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.ICHOR.get(), getX(), getY() + 2.0D, getZ(), 120, 1.2D, 1.5D, 1.2D, 0.08D);
            sl.sendParticles(ModParticles.BLOOD.get(), getX(), getY() + 2.5D, getZ(), 60, 0.8D, 0.8D, 0.8D, 0.3D);
        }
    }

    public void shockwave(double radius, double strength, float shake) {
        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(radius))) {
            if (p.isCreative() || p.isSpectator() || FinisherManager.isInFinisher(p)) continue;
            Vec3 d = p.position().subtract(this.position()).multiply(1, 0, 1).normalize();
            p.push(d.x * strength, 0.45D, d.z * strength);
            p.hurtMarked = true;
            if (p instanceof ServerPlayer sp) ModNetwork.fx(sp, shake, 0.8F, 0.2F, 0);
        }
    }

    // =====================================================================================
    // Nightmare phase: lighting distortion, false sounds, proportion warps, jumpscares
    // =====================================================================================
    private void tickNightmare() {
        if (getPhase() != PHASE_NIGHTMARE || !(targetPlayer() instanceof ServerPlayer sp) || isLocked()) return;
        if (--this.nightmareFxCooldown <= 0) {
            this.nightmareFxCooldown = 100 + this.random.nextInt(140);
            switch (this.random.nextInt(3)) {
                case 0 -> {
                    sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
                    ModNetwork.fx(sp, 0.0F, 0.3F, 0.0F, 50);
                }
                case 1 -> {
                    Vec3 fake = sp.position().add(randomOffset(10.0D, 18.0D));
                    playTo(sp, this.random.nextBoolean() ? ModSounds.SCREAM.get() : ModSounds.STEP.get(), fake, 2.0F, 0.6F + this.random.nextFloat() * 0.3F);
                }
                default -> setWarp(0.5F + this.random.nextFloat() * 0.5F, 30 + this.random.nextInt(40));
            }
        }
        if (AbomConfig.COMMON.jumpscares.get() && --this.jumpscareCooldown <= 0) {
            double d = this.distanceTo(sp);
            Vec3 look = sp.getViewVector(1.0F);
            Vec3 toMe = this.position().subtract(sp.position()).normalize();
            if (d > 8.0D && d < 30.0D && look.dot(toMe) < -0.2D) {
                if (tryJumpscare(sp)) {
                    this.jumpscareCooldown = 500 + this.random.nextInt(400);
                } else {
                    this.jumpscareCooldown = 40;
                }
            }
        }
    }

    /** Teleport into the player's face and play the animatronic jumpscare. */
    public boolean sightJumpscare(ServerPlayer sp) {
        Vec3 view = sp.getViewVector(1.0F).multiply(1, 0, 1);
        if (view.lengthSqr() < 1.0E-4D) view = forward(sp.getYRot());
        view = view.normalize();
        Vec3 spot = null;
        for (double d : new double[]{2.3D, 3.0D, 1.8D, 3.8D}) {
            spot = findGround(sp.getX() + view.x * d, sp.getY(), sp.getZ() + view.z * d, 3);
            if (spot != null) break;
        }
        if (spot == null) {
            for (int i = 0; i < 10 && spot == null; i++) {
                Vec3 o = randomOffset(2.0D, 3.5D);
                spot = findGround(sp.getX() + o.x, sp.getY(), sp.getZ() + o.z, 3);
            }
        }
        if (spot == null) return false;
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.8D, getZ(), 60, 0.5D, 1.2D, 0.5D, 0.06D);
            sl.sendParticles(ModParticles.ICHOR.get(), getX(), getY() + 1.8D, getZ(), 40, 0.5D, 1.0D, 0.5D, 0.04D);
        }
        this.teleportTo(spot.x, spot.y, spot.z);
        float yaw = (float) (Mth.atan2(sp.getZ() - spot.z, sp.getX() - spot.x) * (180.0F / (float) Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.getNavigation().stop();
        this.setTarget(sp);
        this.setAnim(ANIM_JUMPSCARE, JUMPSCARE_TICKS);
        this.lastJumpscare.put(sp.getUUID(), this.level().getGameTime());
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.BLOOD.get(), spot.x, spot.y + 2.5D, spot.z, 40, 0.6D, 0.8D, 0.6D, 0.25D);
        }
        // everyone else hears it from where it stands; the victim hears it in their head (client side)
        this.level().playSound(sp, spot.x, spot.y + 2.0D, spot.z, ModSounds.JUMPSCARE_SCREAM.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, JUMPSCARE_TICKS, 5, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                new com.abomination.horror.network.JumpscarePacket(this.getId()));
        return true;
    }

    private boolean tryJumpscare(ServerPlayer sp) {
        Vec3 view = sp.getViewVector(1.0F).multiply(1, 0, 1).normalize();
        Vec3 spot = findGround(sp.getX() + view.x * 3.0D, sp.getY(), sp.getZ() + view.z * 3.0D, 3);
        if (spot == null) return false;
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.ICHOR.get(), getX(), getY() + 1.5D, getZ(), 40, 0.5D, 1.0D, 0.5D, 0.05D);
        }
        this.teleportTo(spot.x, spot.y, spot.z);
        float yaw = (float) (Mth.atan2(sp.getZ() - spot.z, sp.getX() - spot.x) * (180.0F / (float) Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.setAnim(ANIM_ROAR, 22);
        this.playSound(ModSounds.SCREAM.get(), 3.0F, 0.9F + this.random.nextFloat() * 0.2F);
        sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
        ModNetwork.fx(sp, 1.4F, 1.0F, 0.9F, 10);
        this.attackCooldown = 0;
        return true;
    }

    // =====================================================================================
    // Awakened boss: emerges from the ground when the Throbbing Heart is used
    // =====================================================================================
    public void beginEmergence(Player summoner) {
        this.setTarget(summoner);
        this.entityData.set(DATA_PHASE, PHASE_HUNT);
        this.setAnim(ANIM_EMERGE, 80);
        for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D))) {
            if (p instanceof ServerPlayer sp) {
                sp.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
                playTo(sp, ModSounds.EMERGE_STING.get(), this.position(), 3.0F, 1.0F);
                sp.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                ModNetwork.fx(sp, 0.5F, 0.6F, 0.0F, 60);
            }
        }
    }

    private void tickEmergence() {
        int age = 80 - this.animTimer;
        if (!(this.level() instanceof ServerLevel sl)) return;
        BlockState ground = this.level().getBlockState(this.blockPosition().below());
        if (!ground.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), getX(), getY() + 0.2D, getZ(), 12, 0.8D, 0.1D, 0.8D, 0.15D);
        }
        sl.sendParticles(ModParticles.BLOOD.get(), getX(), getY() + 0.3D, getZ(), 4, 0.6D, 0.1D, 0.6D, 0.25D);
        if (age % 12 == 0) {
            this.playSound(ModSounds.GORE.get(), 2.0F, 0.6F);
            for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(24.0D))) {
                if (p instanceof ServerPlayer sp) ModNetwork.fx(sp, 0.35F, 0.2F, 0.0F, 0);
            }
        }
        if (age == 40) this.playSound(ModSounds.SCRAPE.get(), 3.0F, 0.5F);
    }

    // =====================================================================================
    // Combat
    // =====================================================================================
    public void chooseAttack() {
        boolean slamAvailable = getBossStage() >= 2 || getPhase() == PHASE_NIGHTMARE;
        this.currentAttack = slamAvailable && this.random.nextFloat() < 0.35F ? ATTACK_SLAM : ATTACK_SWIPE;
        if (this.currentAttack == ATTACK_SLAM) {
            this.setAnim(ANIM_SLAM, 30);
        } else {
            this.setAnim(ANIM_ATTACK, 20);
        }
    }

    public int strikeTick() {
        return this.currentAttack == ATTACK_SLAM ? 14 : 8;
    }

    public void performStrike(LivingEntity target) {
        float dmg = (float) (this.getAttributeValue(Attributes.ATTACK_DAMAGE) * AbomConfig.COMMON.damageMultiplier.get());
        if (getBossStage() == 3) dmg *= 1.25F;
        Vec3 fwd = forward(this.getYRot());
        Vec3 impact = this.position().add(fwd.scale(2.2D)).add(0, 0.5D, 0);
        if (this.currentAttack == ATTACK_SLAM) {
            this.playSound(ModSounds.IMPACT.get(), 3.0F, 0.6F);
            for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(4.0D, 2.5D, 4.0D), x -> x != this)) {
                if (e.hurt(this.damageSources().mobAttack(this), dmg * 1.3F)) {
                    e.push(0.0D, 0.7D, 0.0D);
                    e.hurtMarked = true;
                }
            }
            for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(16.0D))) {
                if (p instanceof ServerPlayer sp) ModNetwork.fx(sp, (float) Math.max(0.2D, 1.2D - p.distanceTo(this) / 14.0D), 0.4F, 0.0F, 0);
            }
            breakFragileAround(BlockPos.containing(impact), 3);
        } else {
            if (this.distanceToSqr(target) <= 4.6D * 4.6D) {
                if (target.hurt(this.damageSources().mobAttack(this), dmg)) {
                    target.knockback(1.1D, Mth.sin(this.getYRot() * ((float) Math.PI / 180.0F)), -Mth.cos(this.getYRot() * ((float) Math.PI / 180.0F)));
                    this.doEnchantDamageEffects(this, target);
                    this.playSound(ModSounds.GORE.get(), 1.5F, 0.9F + this.random.nextFloat() * 0.2F);
                }
                if (target instanceof ServerPlayer sp) ModNetwork.fx(sp, 0.9F, 0.5F, 0.25F, 0);
            } else {
                this.playSound(ModSounds.SCRAPE.get(), 1.4F, 1.3F);
            }
            breakFragileAround(BlockPos.containing(impact), 2);
        }
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.02F), 2.0F), impact.x, impact.y + 1.0D, impact.z, 30, 0.8D, 0.6D, 0.8D, 0.1D);
            sl.sendParticles(ModParticles.ICHOR.get(), impact.x, impact.y + 1.0D, impact.z, 18, 0.6D, 0.5D, 0.6D, 0.06D);
        }
        int base = getPhase() == PHASE_NIGHTMARE ? 14 : 22;
        this.attackCooldown = getBossStage() == 3 ? base - 6 : base;
    }

    public void leapAt(LivingEntity target) {
        Vec3 d = target.position().subtract(this.position());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        this.setDeltaMovement(d.x / h * 1.25D, 0.55D + Mth.clamp(d.y * 0.06D, -0.1D, 0.4D), d.z / h * 1.25D);
        this.hasImpulse = true;
        this.setAnim(ANIM_LEAP, 16);
        this.playSound(ModSounds.SCRAPE.get(), 2.0F, 0.8F);
    }

    private void breakFragileAround(BlockPos center, int r) {
        if (!canGrief()) return;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 3, r))) {
            BlockState s = this.level().getBlockState(p);
            if (s.isAir() || s.liquid()) continue;
            boolean fragile = s.is(BlockTags.LEAVES) || s.is(Tags.Blocks.GLASS) || s.is(Tags.Blocks.GLASS_PANES)
                    || s.is(BlockTags.FLOWERS) || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.canBeReplaced();
            if (fragile && this.random.nextFloat() < 0.6F && ForgeEventFactory.onEntityDestroyBlock(this, p, s)) {
                this.level().destroyBlock(p.immutable(), false, this);
            }
        }
    }

    /** Tear through walls between us and the prey. */
    public boolean breakTowards(LivingEntity target) {
        if (!AbomConfig.COMMON.blockBreaking.get() || !canGrief()) return false;
        Vec3 dir = target.position().subtract(this.position()).multiply(1, 0, 1).normalize();
        double maxHardness = AbomConfig.COMMON.maxBreakHardness.get();
        int top = target.getY() > this.getY() + 1.5D ? 5 : 4;
        boolean broke = false;
        for (int dy = 0; dy < top; dy++) {
            for (double side = -0.6D; side <= 0.6D; side += 0.6D) {
                BlockPos p = BlockPos.containing(getX() + dir.x * 1.2D - dir.z * side, getY() + dy, getZ() + dir.z * 1.2D + dir.x * side);
                BlockState s = this.level().getBlockState(p);
                if (s.isAir() || s.liquid()) continue;
                float hardness = s.getDestroySpeed(this.level(), p);
                if (hardness < 0 || hardness > maxHardness) continue;
                if (!s.canEntityDestroy(this.level(), p, this) || !ForgeEventFactory.onEntityDestroyBlock(this, p, s)) continue;
                this.level().destroyBlock(p, true, this);
                broke = true;
            }
        }
        if (broke) {
            this.playSound(ModSounds.IMPACT.get(), 1.6F, 1.1F);
            this.setAnim(ANIM_ATTACK, 14);
        }
        return broke;
    }

    private boolean canGrief() {
        return ForgeEventFactory.getMobGriefingEvent(this.level(), this);
    }

    // =====================================================================================
    // Stalking helpers: gaze checks, hiding spots, false sounds, corruption
    // =====================================================================================
    public boolean isObservedBy(Player p) {
        Vec3 view = p.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(this.getX() - p.getX(), this.getY(0.6D) - p.getEyeY(), this.getZ() - p.getZ());
        double d = to.length();
        if (d < 1.0E-4D) return true;
        double dot = view.dot(to.scale(1.0D / d));
        return dot > 0.9D && p.hasLineOfSight(this);
    }

    public void vanish(Player observer) {
        if (this.vanishCooldown > 0) return;
        this.vanishCooldown = 120;
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.8D, getZ(), 60, 0.5D, 1.2D, 0.5D, 0.05D);
            sl.sendParticles(ModParticles.ICHOR.get(), getX(), getY() + 1.8D, getZ(), 30, 0.4D, 1.0D, 0.4D, 0.02D);
        }
        this.playSound(ModSounds.VANISH.get(), 1.0F, 0.8F + this.random.nextFloat() * 0.3F);
        if (observer instanceof ServerPlayer sp) ModNetwork.fx(sp, 0.0F, 0.8F, 0.0F, 6);
        Vec3 spot = findHidingSpot(observer, 26.0D, 40.0D, true);
        if (spot != null) this.teleportTo(spot.x, spot.y, spot.z);
    }

    @Nullable
    public Vec3 findHidingSpot(Player t, double minR, double maxR, boolean requireHidden) {
        Vec3 best = null;
        int bestScore = -1;
        Vec3 eye = t.getEyePosition();
        for (int i = 0; i < 16; i++) {
            double ang = this.random.nextDouble() * Math.PI * 2.0D;
            double r = minR + this.random.nextDouble() * (maxR - minR);
            Vec3 g = findGround(t.getX() + Math.cos(ang) * r, t.getY(), t.getZ() + Math.sin(ang) * r, 10);
            if (g == null) continue;
            boolean bodyHidden = blocked(eye, g.add(0, 1.2D, 0), t);
            boolean headHidden = blocked(eye, g.add(0, 3.3D, 0), t);
            int score = requireHidden ? (bodyHidden && headHidden ? 3 : -1) : (bodyHidden ? 2 : 0) + (!headHidden ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = g;
            }
        }
        return bestScore >= 0 ? best : null;
    }

    @Nullable
    public Vec3 peripheralSpot(Player p) {
        float side = this.random.nextBoolean() ? 1.0F : -1.0F;
        Vec3 dir = forward(p.getYRot() + side * (65.0F + this.random.nextInt(20)));
        double r = 12.0D + this.random.nextInt(8);
        return findGround(p.getX() + dir.x * r, p.getY(), p.getZ() + dir.z * r, 8);
    }

    private boolean blocked(Vec3 a, Vec3 b, Entity viewer) {
        return this.level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)).getType() != HitResult.Type.MISS;
    }

    @Nullable
    public Vec3 findGround(double x, double y0, double z, int range) {
        for (int i = 0; i <= range * 2; i++) {
            int dy = (i % 2 == 0) ? i / 2 : -(i + 1) / 2;
            BlockPos bp = BlockPos.containing(x, y0 + dy, z);
            BlockPos below = bp.below();
            if (this.level().getBlockState(below).isFaceSturdy(this.level(), below, Direction.UP)
                    && this.level().noCollision(this, this.getType().getDimensions().makeBoundingBox(x, bp.getY(), z))) {
                return new Vec3(x, bp.getY(), z);
            }
        }
        return null;
    }

    public Vec3 randomOffset(double minR, double maxR) {
        double ang = this.random.nextDouble() * Math.PI * 2.0D;
        double r = minR + this.random.nextDouble() * (maxR - minR);
        return new Vec3(Math.cos(ang) * r, 0.0D, Math.sin(ang) * r);
    }

    /** Plays a sound that only one player can hear, at an arbitrary (possibly false) position. */
    public static void playTo(ServerPlayer p, SoundEvent sound, Vec3 pos, float volume, float pitch) {
        p.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.HOSTILE, pos.x, pos.y, pos.z, volume, pitch, p.getRandom().nextLong()));
    }

    /** Grows temporary flesh around its lair: the environmental clue that leads to the hidden encounter. */
    public void corruptAround(BlockPos center, int radius, int count) {
        if (!AbomConfig.COMMON.corruption.get() || !canGrief()) return;
        if (this.corruptionCooldown > 0) {
            this.corruptionCooldown--;
            return;
        }
        this.corruptionCooldown = 300 + this.random.nextInt(300);
        placeCorruption(center, radius, count);
    }

    private void corruptArena(int count) {
        if (!AbomConfig.COMMON.corruption.get() || !canGrief()) return;
        placeCorruption(this.blockPosition(), 9, count);
    }

    private void placeCorruption(BlockPos center, int radius, int count) {
        BlockState flesh = ModBlocks.FLESH_GROWTH.get().defaultBlockState();
        for (int i = 0; i < count * 4 && count > 0; i++) {
            BlockPos p = center.offset(this.random.nextInt(radius * 2 + 1) - radius, this.random.nextInt(5) - 2, this.random.nextInt(radius * 2 + 1) - radius);
            if (this.level().getBlockState(p).isAir() && flesh.canSurvive(this.level(), p)) {
                this.level().setBlock(p, flesh, 3);
                this.arenaCorruption.add(p.immutable());
                count--;
            }
        }
    }

    // =====================================================================================
    // Finisher hooks (see FinisherManager)
    // =====================================================================================
    public void startFinisher(LivingEntity victim, float yaw) {
        this.finisherYaw = yaw;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        this.getNavigation().stop();
        this.setDashing(false);
        this.setAnim(ANIM_FINISHER, FinisherManager.CREATURE_RELEASE_TICK);
    }

    public void endFinisher() {
        if (this.inFinisher()) {
            this.entityData.set(DATA_ANIM, ANIM_IDLE);
            this.animTimer = 0;
        }
    }

    // =====================================================================================
    // Damage, death, loot
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if ((getAnim() == ANIM_EMERGE || this.stageRoar) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean result = super.hurt(source, amount);
        if (result && !this.level().isClientSide) {
            if (this.random.nextFloat() < 0.35F) setWarp(0.6F + this.random.nextFloat() * 0.4F, 12);
            if (getPhase() < PHASE_HUNT && source.getEntity() instanceof Player p && !p.isCreative()) {
                this.setTarget(p);
                setPhase(PHASE_HUNT);
            }
        }
        return result;
    }

    @Override
    protected void tickDeath() {
        ++this.deathTime;
        if (this.level().isClientSide) {
            int n = this.deathTime < 30 ? 2 : 6;
            for (int i = 0; i < n; i++) {
                this.level().addParticle(ModParticles.ICHOR.get(), getRandomX(0.8D), getY() + this.random.nextDouble() * 2.5D, getRandomZ(0.8D),
                        0.0D, 0.02D + this.random.nextDouble() * 0.04D, 0.0D);
            }
            if (this.deathTime < 25 && this.random.nextInt(2) == 0) {
                this.level().addParticle(ModParticles.BLOOD.get(), getRandomX(0.6D), getY() + 1.5D, getRandomZ(0.6D),
                        (this.random.nextDouble() - 0.5D) * 0.3D, 0.2D, (this.random.nextDouble() - 0.5D) * 0.3D);
            }
        } else if (this.deathTime >= 70 && !this.isRemoved()) {
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ModParticles.ICHOR.get(), getX(), getY() + 1.5D, getZ(), 220, 0.7D, 1.3D, 0.7D, 0.12D);
            }
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        this.spawnAtLocation(new ItemStack(ModItems.HEART_FRAGMENT.get(), 1 + this.random.nextInt(2 + looting)));
        if (isAwakened()) this.spawnAtLocation(new ItemStack(ModItems.ABOMINATION_SPINE.get()));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.bossEvent.removeAllPlayers();
        if (!this.level().isClientSide) {
            for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D))) {
                if (p instanceof ServerPlayer sp) {
                    sp.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
                    ModNetwork.fx(sp, 0.6F, 0.6F, 0.2F, 0);
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.bossEvent.removeAllPlayers();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(this.getDisplayName());
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return this.isClimbing();
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motion) {
        if (!state.is(Blocks.COBWEB)) super.makeStuckInBlock(state, motion);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isAwakened() && super.removeWhenFarAway(distance);
    }

    // =====================================================================================
    // Sounds
    // =====================================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return getPhase() == PHASE_STALK ? ModSounds.WHISPER.get() : ModSounds.AMBIENT.get();
    }

    @Override
    public void playAmbientSound() {
        if (getPhase() == PHASE_OBSERVE || isFrozenByGaze()) return; // it is silent while it watches
        super.playAmbientSound();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 140;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (getPhase() == PHASE_OBSERVE) return;
        this.playSound(ModSounds.STEP.get(), isDashing() ? 0.9F : 0.5F, 0.75F + this.random.nextFloat() * 0.3F);
        if (isDashing() && this.random.nextInt(4) == 0) this.playSound(ModSounds.SCRAPE.get(), 0.6F, 1.2F);
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
    }

    // =====================================================================================
    // Client visuals
    // =====================================================================================
    private void clientTick() {
        this.prevSprintBlend = this.sprintBlend;
        this.prevClimbBlend = this.climbBlend;
        this.prevFrozenBlend = this.frozenBlend;
        this.prevWarp = this.warpClient;
        this.sprintBlend = approach(this.sprintBlend, isDashing() && !isLocked() ? 1.0F : 0.0F, 0.12F);
        this.climbBlend = approach(this.climbBlend, isClimbing() && !this.onGround() ? 1.0F : 0.0F, 0.15F);
        this.frozenBlend = approach(this.frozenBlend, isFrozenByGaze() || getAnim() == ANIM_DETECT ? 1.0F : 0.0F, 0.35F);
        float targetWarp = getWarp() + (getPhase() == PHASE_NIGHTMARE ? 0.15F + 0.1F * Mth.sin(this.tickCount * 0.05F) : 0.0F);
        this.warpClient += (targetWarp - this.warpClient) * 0.2F;

        if (this.deathTime > 0) return;
        if (this.random.nextInt(5) == 0) {
            this.level().addParticle(ModParticles.ICHOR.get(), getRandomX(0.6D), getY() + 1.0D + this.random.nextDouble() * 2.4D, getRandomZ(0.6D), 0.0D, -0.01D, 0.0D);
        }
        if (this.random.nextInt(4) == 0) {
            this.level().addParticle(ModParticles.BLOOD.get(), getRandomX(0.5D), getY() + 1.4D + this.random.nextDouble() * 1.2D, getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
        }
        // blood and drool pouring from the open maw
        float s = isAwakened() ? 1.18F : 1.0F;
        Vec3 mouth = this.position().add(forward(this.yBodyRot).scale(1.05D * s)).add(0.0D, (3.0D - this.sprintBlend * 1.2D) * s, 0.0D);
        if (this.random.nextInt(2) == 0) {
            this.level().addParticle(ModParticles.BLOOD.get(), mouth.x + (this.random.nextDouble() - 0.5D) * 0.5D, mouth.y, mouth.z + (this.random.nextDouble() - 0.5D) * 0.5D, 0.0D, -0.05D, 0.0D);
        }
        // claws drip, feet leave bloody prints
        if (this.random.nextInt(6) == 0) {
            Vec3 side = new Vec3(Mth.cos(this.yBodyRot * ((float) Math.PI / 180.0F)), 0, Mth.sin(this.yBodyRot * ((float) Math.PI / 180.0F)));
            Vec3 claw = this.position().add(side.scale(this.random.nextBoolean() ? 0.45D : -0.45D)).add(0, 0.3D, 0);
            this.level().addParticle(ModParticles.BLOOD.get(), claw.x, claw.y, claw.z, 0.0D, -0.02D, 0.0D);
        }
        if (this.onGround() && this.getDeltaMovement().horizontalDistanceSqr() > 0.002D && this.tickCount % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                this.level().addParticle(ModParticles.BLOOD.get(), getRandomX(0.6D), getY() + 0.05D, getRandomZ(0.6D), 0.0D, 0.0D, 0.0D);
            }
        }
        if (this.sprintBlend > 0.5F && this.getDeltaMovement().horizontalDistanceSqr() > 0.01D) {
            for (int i = 0; i < 3; i++) {
                this.level().addParticle(new DustParticleOptions(new Vector3f(0.35F, 0.0F, 0.02F), 1.6F), getRandomX(0.7D), getY() + this.random.nextDouble() * 1.5D, getRandomZ(0.7D), 0, 0, 0);
            }
            this.level().addParticle(ModParticles.ICHOR.get(), getRandomX(0.5D), getY() + 0.8D, getRandomZ(0.5D), 0, 0.02D, 0);
        }
        if (getPhase() == PHASE_NIGHTMARE && this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, getRandomX(1.2D), getY() + this.random.nextDouble() * 3.5D, getRandomZ(1.2D), 0, 0, 0);
        }
    }

    private static float approach(float cur, float target, float step) {
        return cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
    }

    public static Vec3 forward(float yawDeg) {
        float r = yawDeg * ((float) Math.PI / 180.0F);
        return new Vec3(-Mth.sin(r), 0.0D, Mth.cos(r));
    }

    // =====================================================================================
    // Persistence
    // =====================================================================================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Awakened", isAwakened());
        tag.putInt("Phase", getPhase());
        tag.putInt("BossStage", getBossStage());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_AWAKENED, tag.getBoolean("Awakened"));
        this.entityData.set(DATA_PHASE, tag.getInt("Phase"));
        this.entityData.set(DATA_BOSS_STAGE, Math.max(1, tag.getInt("BossStage")));
        if (isAwakened()) this.bossEvent.setName(Component.translatable("entity.abomination.abomination.awakened"));
        if (this.hasCustomName()) this.bossEvent.setName(this.getDisplayName());
    }
}
