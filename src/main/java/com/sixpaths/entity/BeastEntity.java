package com.sixpaths.entity;

import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A creature brought out by the Animal Path. Bound to its summoner; hunts what the summoner fights. */
public abstract class BeastEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(BeastEntity.class, EntityDataSerializers.INT);

    protected UUID ownerUuid;
    protected int life = 20 * 90;
    protected int attackCd, specialCd = 60;

    protected BeastEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes(double hp, double speed, double damage) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, hp).add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, damage).add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.FLYING_SPEED, 0.6);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(OWNER_ID, -1);
    }

    public void bind(ServerPlayer owner, int lifetime) {
        ownerUuid = owner.getUUID();
        entityData.set(OWNER_ID, owner.getId());
        life = lifetime;
    }

    public int ownerId() {
        return entityData.get(OWNER_ID);
    }

    public ServerPlayer owner() {
        if (ownerUuid == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getPlayerByUUID(ownerUuid) instanceof ServerPlayer sp ? sp : null;
    }

    public boolean isFriend(Entity e) {
        if (e == null) return false;
        if (e == this || e.getId() == ownerId()) return true;
        return e instanceof BeastEntity b && b.ownerId() == ownerId();
    }

    @Override protected void registerGoals() {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean causeFallDamage(float a, float b, DamageSource s) { return false; }
    @Override public boolean isAlliedTo(Entity e) { return isFriend(e) || super.isAlliedTo(e); }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (isFriend(src.getEntity())) return false;
        return super.hurt(src, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerPlayer o = owner();
        if (isVehicle()) {
            life = Math.max(life, 200);
            for (Entity p : getPassengers()) p.fallDistance = 0;
        }
        if (o == null || !o.isAlive() || o.level() != level() || --life <= 0) {
            vanish();
            return;
        }
        if (attackCd > 0) attackCd--;
        if (specialCd > 0) specialCd--;
        if (isVehicle() && getControllingPassenger() != null) return;
        ai(o);
    }

    protected abstract void ai(ServerPlayer owner);

    public void vanish() {
        PathsNetwork.fx(this, FxType.BEAST_VANISH, this, null, position().add(0, getBbHeight() / 2, 0), getBbWidth(), 0);
        discard();
    }

    protected LivingEntity findTarget(ServerPlayer o, double range) {
        LivingEntity t = o.getLastHurtMob();
        if (valid(t, o, range)) return t;
        t = o.getLastHurtByMob();
        if (valid(t, o, range)) return t;
        t = getTarget();
        if (valid(t, o, range)) return t;
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, o.getBoundingBox().inflate(18),
                e -> e instanceof Enemy && !isFriend(e) && e.isAlive());
        t = list.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
        setTarget(t);
        return t;
    }

    private boolean valid(LivingEntity t, ServerPlayer o, double range) {
        return t != null && t.isAlive() && !isFriend(t) && t.level() == level() && t.distanceTo(o) < range && !t.isSpectator();
    }

    protected List<LivingEntity> enemiesAround(Vec3 c, double r) {
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e.isAlive() && !isFriend(e) && !e.isSpectator() && e.getBoundingBox().getCenter().distanceTo(c) <= r + e.getBbWidth() / 2);
    }

    public void strike(LivingEntity t, float amount) {
        if (t == null || !t.isAlive() || isFriend(t)) return;
        t.invulnerableTime = 0;
        t.hurt(damageSources().mobAttack(this), amount);
    }

    public static void launch(LivingEntity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    protected void sound(SoundEvent s, float vol, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), s, SoundSource.HOSTILE, vol, pitch);
    }

    protected void action(int id) {
        PathsNetwork.fx(this, FxType.BEAST_ACTION, this, null, position(), id, 0);
    }

    protected void faceToward(Vec3 p) {
        Vec3 d = p.subtract(position());
        if (d.horizontalDistanceSqr() < 1.0e-4) return;
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float next = getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -14f, 14f);
        setYRot(next);
        yBodyRot = next;
        yHeadRot = next;
    }

    protected void flyToward(Vec3 target, double speed) {
        Vec3 d = target.subtract(position());
        double len = d.length();
        Vec3 want = len < 1.0e-3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
        setDeltaMovement(getDeltaMovement().lerp(want, 0.12));
        if (want.lengthSqr() > 1.0e-4) faceToward(position().add(want.scale(10)));
    }

    protected void followOwner(ServerPlayer o, double dist, double speed) {
        double d = distanceTo(o);
        if (d > dist) getNavigation().moveTo(o, speed);
        else getNavigation().stop();
        if (d > 40) teleportTo(o.getX(), o.getY(), o.getZ());
    }

    // ── riding (mounts override) ──
    public boolean rideable() { return false; }
    protected int maxRiders() { return 1; }
    protected double rideSpeed() { return 0.8; }

    @Override
    public LivingEntity getControllingPassenger() {
        if (!rideable()) return null;
        return getFirstPassenger() instanceof Player pl ? pl : null;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return rideable() && getPassengers().size() < maxRiders();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!rideable() || player.isShiftKeyDown()) return super.mobInteract(player, hand);
        if (!level().isClientSide && (player.getId() == ownerId() || maxRiders() > 1)) player.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void travel(Vec3 input) {
        if (rideable() && isVehicle() && getControllingPassenger() instanceof Player p) {
            setYRot(p.getYRot());
            yRotO = getYRot();
            setXRot(p.getXRot() * 0.5f);
            yBodyRot = getYRot();
            yHeadRot = getYRot();
            Vec3 look = p.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0, look.x);
            if (right.lengthSqr() > 1.0e-4) right = right.normalize();
            double sp = rideSpeed();
            Vec3 want = look.scale(p.zza >= 0 ? p.zza * sp : p.zza * sp * 0.4).add(right.scale(-p.xxa * sp * 0.6));
            Vec3 v = getDeltaMovement().lerp(want, 0.12);
            setDeltaMovement(v);
            move(MoverType.SELF, v);
            return;
        }
        super.travel(input);
    }
}
