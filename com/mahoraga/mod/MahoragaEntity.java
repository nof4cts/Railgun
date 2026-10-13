package com.mahoraga.mod;

import com.geto.csm.entity.CurseEntity;
import com.jjk.mod.Balance;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.shrine.server.Techniques;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow.Pickup;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class MahoragaEntity extends Monster {
   static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> ACTION_AT = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Integer> SPINS = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Boolean> INTRO = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.BOOLEAN);
   static final EntityDataAccessor<Boolean> GILLS = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.BOOLEAN);
   static final EntityDataAccessor<Integer> HELD = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.INT);
   static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MahoragaEntity.class, EntityDataSerializers.FLOAT);
   public static final int NONE = 0;
   public static final int SLASH = 1;
   public static final int STOMP = 2;
   public static final int JUMP = 3;
   public static final int SWORD = 4;
   public static final int PUNCH = 5;
   public static final int KICK = 6;
   public static final int FLURRY = 7;
   public static final int THROW = 8;
   public static final int GRAB = 9;
   public static final int MIMIC = 10;
   public static final int HOOK_L = 11;
   public static final int HOOK_R = 12;
   public static final int DOMAIN_WALK = 13;
   public static final int CHARGE = 14;
   public static final int CHARGE_THROW = 15;
   public static final int QUAKE = 16;
   public static final int SLASH_T = 14;
   public static final int STOMP_T = 22;
   public static final int JUMP_T = 44;
   public static final int SWORD_T = 40;
   public static final int PUNCH_T = 14;
   public static final int KICK_T = 18;
   public static final int FLURRY_T = 40;
   public static final int THROW_T = 24;
   public static final int GRAB_T = 60;
   public static final int MIMIC_T = 24;
   public static final int HOOK_T = 16;
   public static final int DWALK_T = 200;
   public static final int CHARGE_T = 70;
   public static final int CHARGE_THROW_T = 16;
   public static final int QUAKE_T = 40;
   public static final int ADAPT_ENV = 600;
   public static final int DESPAWN_ALONE = 6000;
   public static final float QUAKE_SPEED = 0.55F;
   public static final float QUAKE_RADIUS = 13.0F;
   public static final float MAX_SCALE = 2.5F;
   public static final float GROW_STEP = 0.15F;
   public static final int CHARGE_AFTER = 6000;
   public static final int ADAPT_TIME = 3375;
   public static final int ADAPT_FAST = 1350;
   public static final int FAST_HITS = 11;
   public static final int LEARN_TIME = 1800;
   public static final int GILLS_TIME = 1200;
   private final Map<String, MahoragaEntity.Adapt> adapting = new LinkedHashMap<>();
   private final ServerBossEvent bossBar = new ServerBossEvent(
      Component.literal("Eight-Handled Sword Divergent Sila Divine General Mahoraga"), BossBarColor.YELLOW, BossBarOverlay.NOTCHED_10
   );
   private final List<MahoragaEntity.Chunk> chunks = new ArrayList<>();
   private int walkLen = 200;
   private int slashCd;
   private int stompCd = 40;
   private int jumpCd = 100;
   private int swordCd = 160;
   private int flurryCd = 60;
   private int throwCd = 120;
   private int mimicCd = 200;
   private int basicCd;
   private int waterTicks;
   private int chunkSerial;
   private int growCount;
   private int growCd;
   private int stompCount;
   private int blinkCd = 100;
   private int chargeCd = 100;
   private boolean domainWalked;
   private boolean dodge;
   private boolean domainSaid;
   private boolean lavaSwim;
   private int quakeCd = 120;
   private int aloneTicks;
   private int retargetIn;
   private Vec3 quakeAt;
   private final Set<Integer> quakeHit = new HashSet<>();
   private int walkTarget = -1;
   private boolean announced;
   private Vec3 jumpTo;
   private String mimicKind;
   private int hitsLanded;
   public Vec3 clientBlade;
   public Vec3 clientHandL;
   public Vec3 clientHands;
   private BlockState pendingChunk;

   public MahoragaEntity(EntityType<? extends Monster> type, Level level) {
      super(type, level);
      this.xpReward = 200;
      this.setMaxUpStep(1.5F);
   }

   public static Builder createAttributes() {
      return Monster.createMonsterAttributes()
         .add(Attributes.MAX_HEALTH, 500.0)
         .add(Attributes.ARMOR, 10.0)
         .add(Attributes.ARMOR_TOUGHNESS, 4.0)
         .add(Attributes.ATTACK_DAMAGE, 14.0)
         .add(Attributes.MOVEMENT_SPEED, 0.3)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.FOLLOW_RANGE, 48.0);
   }

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(ACTION, 0);
      this.entityData.define(ACTION_AT, 0);
      this.entityData.define(SPINS, 0);
      this.entityData.define(INTRO, true);
      this.entityData.define(GILLS, false);
      this.entityData.define(HELD, -1);
      this.entityData.define(SCALE, 1.0F);
   }

   public float scale() {
      return (Float)this.entityData.get(SCALE);
   }

   public EntityDimensions getDimensions(Pose pose) {
      return super.getDimensions(pose).scale(this.scale());
   }

   public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
      super.onSyncedDataUpdated(key);
      if (SCALE.equals(key)) {
         this.refreshDimensions();
      }
   }

   private void grow(ServerLevel l, String why) {
      if (!(this.scale() >= 2.499F)) {
         this.entityData.set(SCALE, Math.min(2.5F, this.scale() + 0.15F));
         this.refreshDimensions();
         this.growCount++;
         this.play(SoundEvents.WARDEN_HEARTBEAT, 2.5F, 0.5F);
         this.play(SoundEvents.IRON_GOLEM_REPAIR, 1.5F, 0.4F);
         Net.fx(l, new Net.Fx(17, this.getId(), this.position().add(0.0, (double)(this.getBbHeight() / 2.0F), 0.0), this.scale(), why));
      }
   }

   protected void registerGoals() {
      this.goalSelector.addGoal(0, new FloatGoal(this));
      this.targetSelector.addGoal(1, new HurtByTargetGoal(this, new Class[0]));
      this.targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
   }

   public int action() {
      return (Integer)this.entityData.get(ACTION);
   }

   public float actionAge(float partial) {
      return (float)((int)this.level().getGameTime() - (Integer)this.entityData.get(ACTION_AT)) + partial;
   }

   public int punchSide() {
      return (Integer)this.entityData.get(ACTION_AT) & 1;
   }

   public int spins() {
      return (Integer)this.entityData.get(SPINS);
   }

   public boolean intro() {
      return (Boolean)this.entityData.get(INTRO);
   }

   public boolean gills() {
      return (Boolean)this.entityData.get(GILLS);
   }

   public int held() {
      return (Integer)this.entityData.get(HELD);
   }

   public void summoned() {
      this.announced = true;
      this.entityData.set(INTRO, false);
   }

   private void act(int a) {
      this.entityData.set(ACTION, a);
      this.entityData.set(ACTION_AT, (int)this.level().getGameTime());
   }

   private static String[] kindOf(DamageSource src) {
      Entity att = src.getEntity();
      Entity direct = src.getDirectEntity();
      String id = src.type().msgId();
      boolean melee = direct == att && att != null && (id.equals("player") || id.equals("mob"));
      if (att instanceof ServerPlayer sp && !melee) {
         int[] m = Balance.lastMove(sp, 60);
         Sorcerer s = Sorcerers.get(sp);
         if (m != null && s != Sorcerer.NONE && m[0] == s.ordinal() && m[1] < s.moves.length) {
            return new String[]{"tech:" + s.name() + ":" + m[1], s.moves[m[1]], "1"};
         }
      }

      if (direct instanceof Projectile pr && direct != att) {
         String n = pr.getType().getDescription().getString();
         return new String[]{"proj:" + EntityType.getKey(pr.getType()), n, "0"};
      }

      if (melee && att instanceof Player p) {
         ItemStack held = p.getMainHandItem();
         if (held.isEmpty()) {
            return new String[]{"melee:fists", "fists", "0"};
         } else if (held.getItem() instanceof SwordItem) {
            return new String[]{"melee:swords", "swords", "0"};
         } else if (held.getItem() instanceof AxeItem) {
            return new String[]{"melee:axes", "axes", "0"};
         } else if (held.getItem() instanceof TridentItem) {
            return new String[]{"melee:tridents", "tridents", "0"};
         } else {
            return held.getItem() instanceof DiggerItem
               ? new String[]{"melee:tools", "tools", "0"}
               : new String[]{"melee:" + held.getItem(), held.getHoverName().getString() + " blows", "0"};
         }
      } else if (melee && att != null) {
         return new String[]{"melee:mob:" + EntityType.getKey(att.getType()), att.getType().getDescription().getString() + " attacks", "0"};
      } else {
         String n = id.replaceAll("([A-Z])", " $1").replace('.', ' ').toLowerCase();
         return new String[]{"dmg:" + id, n, "0"};
      }
   }

   public boolean hurt(DamageSource src, float amount) {
      if (!this.level().isClientSide && !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         String[] k = kindOf(src);
         MahoragaEntity.Adapt a = this.adapting.get(k[0]);
         if (a != null && a.done) {
            this.play(SoundEvents.ANVIL_LAND, 0.6F, 1.6F);
            Net.fx((ServerLevel)this.level(), new Net.Fx(6, this.getId(), this.position().add(0.0, 2.0, 0.0), 0.0F, ""));
            if (src.getDirectEntity() instanceof Projectile pr && pr != src.getEntity()) {
               pr.discard();
            }

            return false;
         } else {
            if ("1".equals(k[2])) {
               String[] parts = k[0].split(":");
               Sorcerer s = Sorcerer.valueOf(parts[1]);
               int slot = Integer.parseInt(parts[2]);

               for (int t : Balance.top(s)) {
                  if (t == slot) {
                     amount *= 4.0F;
                     if (src.getEntity() instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal("Maximum output!").withStyle(ChatFormatting.RED), true);
                     }
                  }
               }
            }

            boolean hit = super.hurt(src, amount);
            if (hit && this.isAlive()) {
               if (a == null) {
                  boolean env = src.getEntity() == null && !(src.getDirectEntity() instanceof Projectile);
                  a = new MahoragaEntity.Adapt(k[1], "1".equals(k[2]), env ? 600.0F : 3375.0F, this.level().getGameTime());
                  this.adapting.put(k[0], a);
               }

               a.hits++;
               if (a.hits > 11 && a.need > 1350.0F) {
                  a.need = 1350.0F;
               }

               if (this.intro()) {
                  this.entityData.set(INTRO, false);
               }

               ServerLevel l = (ServerLevel)this.level();
               if (src.getEntity() instanceof ServerPlayer sp && Sorcerers.get(sp) == Sorcerer.SUKUNA && src.is(DamageTypes.INDIRECT_MAGIC) && this.growCd == 0
                  )
                {
                  int[] m = Balance.lastMove(sp, 60);
                  if (m == null || m[1] != 4 && m[1] != 5) {
                     this.grow(l, "Sukuna's slashes");
                     this.growCd = 40;
                  }
               }

               if (src.getDirectEntity() instanceof Projectile && src.getDirectEntity() != src.getEntity() && this.blinkCd == 0) {
                  this.dodge = true;
               }
            }

            return hit;
         }
      } else {
         return super.hurt(src, amount);
      }
   }

   private void tickAdaptation(ServerLevel l) {
      for (Entry<String, MahoragaEntity.Adapt> en : this.adapting.entrySet()) {
         MahoragaEntity.Adapt a = en.getValue();
         if (!a.learned && l.getGameTime() - a.first >= 1800L && mimicStyle(en.getKey()) >= 0) {
            a.learned = true;
            Net.fx(l, new Net.Fx(20, this.getId(), this.position(), 0.0F, a.name));
         }

         if (!a.done) {
            a.progress++;
            int q = Math.min(4, (int)(a.progress * 4.0F / a.need));

            while (a.quarters < q) {
               a.quarters++;
               this.turnWheel(l, a.quarters, a.name);
               if (a.quarters == 2) {
                  Net.fx(l, new Net.Fx(23, this.getId(), this.position(), 0.0F, a.name));
               }
            }

            if (a.quarters >= 4) {
               a.done = true;
               this.adapted(l, a.name);
               if (en.getKey().equals("dmg:lava")) {
                  this.lavaSwim = true;
               }

               if (en.getKey().equals("tech:SUKUNA:0") || en.getKey().equals("tech:SUKUNA:2")) {
                  this.growCount = Math.max(this.growCount, 3);
               }
            }
         }
      }

      if (!this.gills() && this.isInWater()) {
         this.waterTicks++;
         if (this.waterTicks % 150 == 0) {
            this.turnWheel(l, this.waterTicks / 150, "water");
         }

         if (this.waterTicks >= 600) {
            this.entityData.set(GILLS, true);
            this.adapted(l, "water (he grew gills)");
         }
      }
   }

   private void turnWheel(ServerLevel l, int quarter, String name) {
      this.entityData.set(SPINS, this.spins() + 1);
      this.grow(l, "the wheel");
      this.play(SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
      this.play(SoundEvents.ANVIL_PLACE, 0.8F, 0.6F);
      Net.fx(l, new Net.Fx(4, this.getId(), this.position().add(0.0, 4.3, 0.0), (float)quarter, name));
   }

   private void adapted(ServerLevel l, String name) {
      if (!this.isDeadOrDying()) {
         this.setHealth(this.getMaxHealth());
      }

      this.play(SoundEvents.BELL_RESONATE, 2.5F, 0.6F);
      Net.fx(l, new Net.Fx(5, this.getId(), this.position().add(0.0, 4.3 * (double)this.scale(), 0.0), 0.0F, name));
   }

   public boolean canBreatheUnderwater() {
      return this.gills();
   }

   public void aiStep() {
      super.aiStep();
      if (!this.level().isClientSide && !this.isDeadOrDying()) {
         ServerLevel l = (ServerLevel)this.level();
         if (!this.announced) {
            this.announced = true;
            Net.fx(l, new Net.Fx(0, this.getId(), this.position(), 0.0F, ""));
            this.play(SoundEvents.WARDEN_EMERGE, 2.0F, 0.6F);
            this.play(SoundEvents.BELL_BLOCK, 2.5F, 0.4F);
         }

         this.bossBar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
         this.tickAdaptation(l);
         this.tickChunks(l);
         if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
            this.heal(3.0F);
         }

         this.slashCd = Math.max(0, this.slashCd - 1);
         this.stompCd = Math.max(0, this.stompCd - 1);
         this.jumpCd = Math.max(0, this.jumpCd - 1);
         this.swordCd = Math.max(0, this.swordCd - 1);
         this.flurryCd = Math.max(0, this.flurryCd - 1);
         this.throwCd = Math.max(0, this.throwCd - 1);
         this.mimicCd = Math.max(0, this.mimicCd - 1);
         this.basicCd = Math.max(0, this.basicCd - 1);
         this.growCd = Math.max(0, this.growCd - 1);
         this.blinkCd = Math.max(0, this.blinkCd - 1);
         this.chargeCd = Math.max(0, this.chargeCd - 1);
         boolean inLava = this.isInLava();
         if ((this.gills() && this.isInWater() || this.lavaSwim && inLava) && this.getTarget() != null) {
            Vec3 to = this.getTarget().position().subtract(this.position());
            if (to.lengthSqr() > 4.0) {
               this.setDeltaMovement(this.getDeltaMovement().scale(0.55).add(to.normalize().scale(0.22)));
            }
         }

         if (this.lavaSwim && inLava) {
            this.clearFire();
         }

         if (l.getNearestPlayer(this, 64.0) == null) {
            if (++this.aloneTicks >= 6000) {
               Net.fx(l, new Net.Fx(18, this.getId(), this.position(), this.scale(), "", this.position(), 0));
               this.discard();
               return;
            }
         } else {
            this.aloneTicks = 0;
         }

         this.quakeCd = Math.max(0, this.quakeCd - 1);
         this.retarget(l);
         this.sayDomain(l);
         LivingEntity t = this.getTarget();
         int act = this.action();
         if (!this.domainWalked && act != 9) {
            Iterator d = l.getPlayers(
                  sp -> sp.isAlive() && sp.distanceTo(this) < 48.0F && Sorcerers.get(sp) == Sorcerer.SUKUNA && Techniques.domainOpenFor(sp) >= 0L
               )
               .iterator();
            if (d.hasNext()) {
               ServerPlayer sp = (ServerPlayer)d.next();
               this.domainWalked = true;
               this.walkTarget = sp.getId();
               Vec3 away = this.position().subtract(sp.position()).multiply(1.0, 0.0, 1.0);
               if (away.length() < 12.0) {
                  Vec3 dir = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
                  Vec3 to = sp.position().add(dir.scale(14.0));
                  int y = l.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(to.x), Mth.floor(to.z));
                  to = new Vec3(to.x, (double)y, to.z);
                  if (Math.abs((double)y - this.getY()) < 8.0 && l.noCollision(this, this.getBoundingBox().move(to.subtract(this.position())))) {
                     Net.fx(l, new Net.Fx(18, this.getId(), this.position(), this.scale(), "", to, 0));
                     this.teleportTo(to.x, to.y, to.z);
                  }
               }

               this.entityData.set(HELD, -1);
               this.start(13);
               long open = Techniques.domainOpenFor(sp);
               this.walkLen = 200;
               Net.fx(l, new Net.Fx(19, this.getId(), sp.position(), (float)this.walkLen, "", Vec3.ZERO, sp.getId()));
               this.play(SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.4F);
               return;
            }
         }

         if (this.dodge && act == 0 && t != null) {
            this.dodge = false;
            Vec3 to = t.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            Vec3 side = to.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(-to.z, 0.0, to.x).normalize();
            this.blink(l, this.random.nextBoolean() ? side : side.reverse());
         }

         if (act != 0) {
            this.tickAction(l, act, (int)this.actionAge(0.0F), t);
         } else if (t != null && t.isAlive()) {
            this.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = (double)this.distanceTo(t);
            if (this.intro()) {
               this.getNavigation().moveTo(t, 0.45);
               if (d < 5.0) {
                  this.entityData.set(INTRO, false);
                  this.play(SoundEvents.RAVAGER_ROAR, 2.0F, 0.5F);
               }
            } else {
               double reach = 4.5 + (double)(t.getBbWidth() / 2.0F);
               if (t.getHealth() <= 2.0F && d < reach && canGrab(t)) {
                  this.start(9);
                  this.entityData.set(HELD, t.getId());
                  Net.fx(l, new Net.Fx(10, this.getId(), t.position(), 0.0F, "", Vec3.ZERO, t.getId()));
                  if (t instanceof ServerPlayer sp) {
                     Net.fx(l, new Net.Fx(14, sp.getId(), sp.position(), 60.0F, "", Vec3.ZERO, 2));
                  }

                  this.play(SoundEvents.RAVAGER_ROAR, 2.0F, 0.4F);
               } else if (this.tickCount > 6000 && this.chargeCd == 0 && d > 6.0 && d < 30.0 && this.hasLineOfSight(t)) {
                  this.start(14);
                  this.play(SoundEvents.RAVAGER_ROAR, 2.5F, 0.6F);
               } else if (d > 11.0 && this.blinkCd == 0 && this.random.nextFloat() < 0.02F) {
                  this.blink(l, t.position().subtract(this.position()));
               } else if (d > 7.0 && d < 22.0 && this.jumpCd == 0) {
                  this.start(3);
                  this.jumpTo = t.position();
               } else {
                  if (this.mimicCd == 0 && d > 3.0 && d < 24.0 && this.hasLineOfSight(t)) {
                     String k = this.pickLearned();
                     if (k != null) {
                        this.mimicKind = k;
                        this.start(10);
                        return;
                     }
                  }

                  if (d > 6.0 && d < 26.0 && this.throwCd == 0 && this.random.nextFloat() < 0.04F && this.hasLineOfSight(t)) {
                     this.start(8);
                     this.play(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 1.4F, 0.5F);
                  } else if (d < 9.0 && this.quakeCd == 0 && this.random.nextFloat() < 0.25F) {
                     this.start(16);
                  } else if (d < 7.0 && this.swordCd == 0 && this.getHealth() < this.getMaxHealth() * 0.85F) {
                     this.start(4);
                     Net.fx(l, new Net.Fx(2, this.getId(), t.position(), 0.0F, ""));
                  } else if (d < reach && this.basicCd == 0) {
                     float r = this.random.nextFloat();
                     if (this.flurryCd == 0 && r < 0.2F) {
                        this.start(7);
                        this.play(SoundEvents.RAVAGER_ROAR, 1.6F, 0.8F);
                     } else if (this.stompCd == 0 && r < 0.36F) {
                        this.start(2);
                     } else if (r < 0.52F) {
                        this.start(5);
                     } else if (r < 0.64F) {
                        this.start(11);
                     } else if (r < 0.76F) {
                        this.start(12);
                     } else if (r < 0.88F) {
                        this.start(6);
                     } else if (this.slashCd == 0) {
                        this.start(1);
                     } else {
                        this.start(5);
                     }
                  } else {
                     this.getNavigation().moveTo(t, 1.15);
                  }
               }
            }
         }
      }
   }

   private static boolean canGrab(LivingEntity t) {
      return (!(t instanceof Player p) || !p.isCreative() && !p.isSpectator()) && t.getBbHeight() < 3.5F && !t.isPassenger();
   }

   private void start(int a) {
      this.getNavigation().stop();
      this.act(a);
   }

   private void tickAction(ServerLevel l, int a, int age, LivingEntity t) {
      if (t != null && a != 9) {
         this.getLookControl().setLookAt(t, 30.0F, 30.0F);
      }

      Vec3 fwd = Vec3.directionFromRotation(0.0F, this.getYRot());
      switch (a) {
         case 1:
            if (age == 3) {
               this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
            }

            if (age == 7) {
               this.hitArc(fwd, 5.0, 14.0F, 0.9, 0.35);
            }

            if (age >= 14) {
               this.slashCd = 40;
               this.end(10);
            }
            break;
         case 2:
            if (age == 12) {
               this.stompCount++;
               boolean big = this.stompCount <= 3;
               float sc = this.scale();
               Vec3 at = this.position().add(fwd.scale(1.5 * (double)sc));
               double r = (big ? 8.5 : 5.5) * (double)sc;
               this.play(SoundEvents.GENERIC_EXPLODE, big ? 2.5F : 1.2F, big ? 0.45F : 0.6F);
               this.play(SoundEvents.ANVIL_LAND, 1.6F, 0.45F);
               if (big) {
                  this.play(SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 0.5F);
               }

               Net.fx(l, new Net.Fx(7, this.getId(), at, (float)r, big ? "heavy" : "", Vec3.ZERO, big ? 70 : 26));
               BlockState ground = this.groundBlock(Vec3.ZERO);
               l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, big ? 160 : 60, r * 0.4, 0.3, r * 0.4, 0.3);

               for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, 3.0, r), ex -> ex != this && ex.isAlive())) {
                  double d = e.position().distanceTo(at);
                  if (d < r) {
                     this.strike(e, (big ? 16.0F : 10.0F) * (float)(1.0 - 0.5 * d / r) * (1.0F + 0.4F * (sc - 1.0F)), 0.0, 0.0);
                     Vec3 out = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
                     out = out.lengthSqr() < 1.0E-4 ? fwd : out.normalize();
                     e.setDeltaMovement(e.getDeltaMovement().add(out.scale(big ? 0.7 : 0.25)).add(0.0, big ? 1.15 : 0.8, 0.0));
                     e.hurtMarked = true;
                  }
               }
            }

            if (age >= 22) {
               this.stompCd = 90;
               this.end(10);
            }
            break;
         case 3:
            if (age == 8 && this.jumpTo != null) {
               Vec3 d = this.jumpTo.subtract(this.position());
               double T = 18.0;
               double g = 0.08;
               this.setDeltaMovement(d.x / T, 0.5 * g * T + d.y / T, d.z / T);
               this.hurtMarked = true;
               this.play(SoundEvents.RAVAGER_ROAR, 1.8F, 0.7F);
            }

            if (age > 12 && (this.onGround() || age >= 34) && age < 38 && this.jumpTo != null) {
               Vec3 at = this.position().add(fwd.scale(1.8));
               this.jumpTo = null;
               this.play(SoundEvents.GENERIC_EXPLODE, 2.5F, 0.5F);
               this.play(SoundEvents.ANVIL_LAND, 2.0F, 0.4F);
               Net.fx(l, new Net.Fx(7, this.getId(), at, 8.0F, "heavy"));

               for (LivingEntity ex : l.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(7.0, 3.0, 7.0), exx -> exx != this && exx.isAlive())) {
                  double d = ex.position().distanceTo(at);
                  if (d < 7.0) {
                     this.strike(ex, 22.0F * (float)(1.0 - d / 9.0), 0.0, 0.0);
                     Vec3 out = ex.position().subtract(at).multiply(1.0, 0.0, 1.0);
                     out = out.lengthSqr() < 1.0E-4 ? fwd : out.normalize();
                     ex.setDeltaMovement(out.x * 1.3, 0.9, out.z * 1.3);
                     ex.hurtMarked = true;
                  }
               }

               Net.fx(l, new Net.Fx(3, this.getId(), at.add(0.0, 1.0, 0.0), 1.0F, ""));
            }

            if (age >= 44) {
               this.jumpCd = 260;
               this.end(10);
            }
            break;
         case 4:
            if (age == 6) {
               this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
            }

            if (age == 9) {
               this.hitArc(fwd, 5.2, 12.0F, 0.8, 0.3);
            }

            if (age == 11) {
               this.play(SoundEvents.BEACON_ACTIVATE, 2.0F, 1.4F);
               this.play(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.6F);
            }

            if (age == 23 && t != null) {
               Vec3 d = t.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
               double len = d.length();
               if (len > 3.5) {
                  Vec3 to = this.position().add(d.normalize().scale(len - 2.4));
                  this.teleportTo(to.x, t.getY(), to.z);
               }

               this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
            }

            if (age == 26) {
               boolean any = this.hitArc(fwd, 6.0, 30.0F, 1.6, 0.5);
               this.play(SoundEvents.ELDER_GUARDIAN_CURSE, 1.4F, 1.3F);
               if (any) {
                  Net.fx(l, new Net.Fx(3, this.getId(), this.position().add(fwd.scale(2.5)).add(0.0, 2.0, 0.0), 2.0F, ""));
               }
            }

            if (age >= 40) {
               this.swordCd = 340;
               this.end(10);
            }
            break;
         case 5:
         case 11:
         case 12:
            boolean hook = a != 5;
            int hitAt = hook ? 8 : 6;
            if (age == hitAt - 3) {
               this.play(SoundEvents.PLAYER_ATTACK_STRONG, 1.5F, 0.45F);
               this.setDeltaMovement(this.getDeltaMovement().add(fwd.scale(0.28)));
            }

            if (age == hitAt) {
               Vec3 hitPt = this.position().add(fwd.scale(2.6 * (double)this.scale())).add(0.0, 2.4 * (double)this.scale(), 0.0);
               if (this.hitArc(fwd, 4.6, hook ? 11.0F : 9.0F, hook ? 1.5 : 1.2, hook ? 0.35 : 0.3)) {
                  this.play(SoundEvents.ANVIL_LAND, 0.9F, 0.55F);
                  this.play(SoundEvents.IRON_GOLEM_ATTACK, 1.6F, 0.6F);
                  Net.fx(l, new Net.Fx(16, this.getId(), hitPt, hook ? 1.4F : 1.1F, ""));
               } else {
                  this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.4F);
               }
            }

            if (age >= (hook ? 16 : 14)) {
               if (hook && this.random.nextFloat() < 0.5F && t != null && this.distanceTo(t) < 5.0F * this.scale()) {
                  this.act(a == 11 ? 12 : 11);
               } else {
                  this.end(8);
               }
            }
            break;
         case 6:
            if (age == 6) {
               this.play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4F, 0.5F);
            }

            if (age == 9 && this.hitArc(fwd, 4.8, 10.0F, 1.9, 0.5)) {
               this.play(SoundEvents.ANVIL_LAND, 0.9F, 0.55F);
               this.play(SoundEvents.IRON_GOLEM_ATTACK, 1.6F, 0.6F);
               Net.fx(
                  l,
                  new Net.Fx(16, this.getId(), this.position().add(fwd.scale(2.6 * (double)this.scale())).add(0.0, 1.6 * (double)this.scale(), 0.0), 1.3F, "")
               );
            }

            if (age >= 18) {
               this.end(10);
            }
            break;
         case 7:
            if (age >= 6 && age <= 34 && age % 3 == 0) {
               this.play(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.6F + this.random.nextFloat() * 0.3F);
               if (this.hitArc(fwd, 4.8, 4.0F, age >= 33 ? 1.8 : 0.15, age >= 33 ? 0.5 : 0.05)) {
                  Vec3 at = this.position().add(fwd.scale(2.6)).add(0.0, 2.2, 0.0);
                  l.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.4, 0.4, 0.4, 0.0);
               }

               if (t != null && (double)this.distanceTo(t) > 3.4) {
                  this.setDeltaMovement(fwd.scale(0.25));
               }
            }

            if (age >= 40) {
               this.flurryCd = 200;
               this.end(16);
            }
            break;
         case 8:
            if (age == 8) {
               BlockState st = this.groundBlock(fwd);
               Vec3 at = this.position().add(fwd.scale(1.6));
               l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y + 0.2, at.z, 30, 0.6, 0.2, 0.6, 0.2);
               this.play(SoundEvents.GRAVEL_BREAK, 2.0F, 0.5F);
               this.pendingChunk = st;
            }

            if (age == 16 && t != null) {
               BlockState st = this.pendingChunk == null ? Blocks.DIRT.defaultBlockState() : this.pendingChunk;
               Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
               Vec3 from = this.position().add(0.0, 4.6, 0.0).add(right.scale(-0.9));
               Vec3 to = t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0).add(t.getDeltaMovement().scale(8.0));
               Vec3 vel = aim(from, to, 1.25, 0.05);
               MahoragaEntity.Chunk c = new MahoragaEntity.Chunk(++this.chunkSerial, from, vel, st);
               this.chunks.add(c);
               Net.fx(l, new Net.Fx(8, this.getId(), from, (float)c.serial, "", vel, Block.getId(st)));
               this.play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.0F, 0.4F);
            }

            if (age >= 24) {
               this.throwCd = 120;
               this.end(10);
            }
            break;
         case 9:
            if (!(l.getEntity(this.held()) instanceof LivingEntity vx) || !vx.isAlive()) {
               this.entityData.set(HELD, -1);
               this.end(20);
               return;
            }

            Vec3 hold = this.position().add(fwd.scale(1.9)).add(0.0, 2.2 - (double)vx.getBbHeight() * 0.45, 0.0);
            if (vx instanceof ServerPlayer sp) {
               sp.teleportTo(hold.x, hold.y, hold.z);
            } else {
               vx.teleportTo(hold.x, hold.y, hold.z);
            }

            vx.setDeltaMovement(Vec3.ZERO);
            vx.fallDistance = 0.0F;
            if (age == 14 || age == 26 || age == 36) {
               this.play(SoundEvents.SKELETON_HURT, 1.4F, 0.4F);
               this.play(SoundEvents.WOOD_BREAK, 1.2F, 0.5F);
            }

            if (age == 46) {
               this.play(SoundEvents.ANVIL_LAND, 2.0F, 0.5F);
               this.play(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 2.0F, 0.4F);
               Vec3 c = vx.position().add(0.0, (double)(vx.getBbHeight() / 2.0F), 0.0);
               l.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.6F), c.x, c.y, c.z, 60, 0.5, 0.5, 0.5, 0.1);
               Net.fx(l, new Net.Fx(15, this.getId(), c, 0.0F, ""));
               vx.invulnerableTime = 0;
               vx.hurt(this.damageSources().mobAttack(this), Math.max(40.0F, vx.getMaxHealth() * 2.0F));
               if (vx.isAlive() && (!(vx instanceof Player p) || !p.isCreative())) {
                  vx.kill();
               }

               this.entityData.set(HELD, -1);
            }

            if (age >= 60) {
               this.entityData.set(HELD, -1);
               this.end(20);
            }
            break;
         case 10:
            if (age == 12 && t != null && this.mimicKind != null) {
               this.mimic(l, this.mimicKind, t, fwd);
            }

            if (age >= 24) {
               this.mimicCd = 220;
               this.end(10);
            }
            break;
         case 13:
            Entity w = l.getEntity(this.walkTarget);
            if (w != null && w.isAlive()) {
               this.getLookControl().setLookAt(w, 10.0F, 10.0F);
               this.getNavigation().stop();
               Vec3 to = w.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
               if (to.length() > 3.5 * (double)this.scale()) {
                  Vec3 vx = to.normalize().scale(0.075);
                  this.setDeltaMovement(vx.x, this.getDeltaMovement().y, vx.z);
                  this.setYRot((float)(Mth.atan2(to.z, to.x) * 180.0F / (float)Math.PI) - 90.0F);
                  this.setYBodyRot(this.getYRot());
                  this.setYHeadRot(this.getYRot());
               }
            }

            if (age >= this.walkLen || w == null || !w.isAlive()) {
               this.getNavigation().stop();
               this.end(40);
            }
            break;
         case 14:
            if (t == null || !t.isAlive()) {
               this.chargeCd = 200;
               this.end(10);
               return;
            }

            if (age >= 8) {
               Vec3 to = t.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
               if (to.lengthSqr() > 1.0E-4) {
                  Vec3 vx = to.normalize().scale(0.9);
                  this.setDeltaMovement(vx.x, this.getDeltaMovement().y, vx.z);
                  this.setYRot((float)(Mth.atan2(to.z, to.x) * 180.0F / (float)Math.PI) - 90.0F);
                  this.setYBodyRot(this.getYRot());
               }

               if (age % 5 == 0) {
                  Net.fx(l, new Net.Fx(7, this.getId(), this.position(), 3.0F, "", Vec3.ZERO, 4));
                  this.play(SoundEvents.IRON_GOLEM_STEP, 2.0F, 0.5F);
               }

               if ((double)this.distanceTo(t) < 2.6 * (double)this.scale() + (double)(t.getBbWidth() / 2.0F)) {
                  this.entityData.set(HELD, t.getId());
                  this.act(15);
                  this.play(SoundEvents.RAVAGER_ROAR, 2.0F, 0.8F);
                  return;
               }
            }

            if (age >= 70) {
               this.chargeCd = 300;
               this.end(10);
            }
            break;
         case 15:
            if (l.getEntity(this.held()) instanceof LivingEntity v && v.isAlive()) {
               if (age < 9) {
                  float k = (float)age / 9.0F;
                  Vec3 hold = this.position()
                     .add(fwd.scale(1.6 * (double)this.scale() * (double)(1.0F - k)))
                     .add(0.0, (1.8 + 3.0 * (double)k) * (double)this.scale(), 0.0);
                  v.teleportTo(hold.x, hold.y, hold.z);
                  v.setDeltaMovement(Vec3.ZERO);
                  v.fallDistance = 0.0F;
               } else if (age == 9) {
                  this.strike(v, 10.0F, 0.0, 0.0);
                  v.setDeltaMovement(fwd.x * 2.4, 1.1, fwd.z * 2.4);
                  v.hurtMarked = true;
                  this.entityData.set(HELD, -1);
                  this.play(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 2.0F, 0.4F);
               }
            }

            if (age >= 16) {
               this.entityData.set(HELD, -1);
               this.chargeCd = 300;
               this.end(16);
            }
            break;
         case 16:
            if (age == 4) {
               this.play(SoundEvents.RAVAGER_ROAR, 1.8F, 0.6F);
            }

            if (age == 12) {
               this.quakeAt = this.position().add(fwd.scale(0.6 * (double)this.scale()));
               this.quakeHit.clear();
               this.play(SoundEvents.GENERIC_EXPLODE, 2.2F, 0.5F);
               this.play(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 0.4F);
               this.play(SoundEvents.ANVIL_LAND, 1.6F, 0.4F);
               Net.fx(l, new Net.Fx(22, this.getId(), this.quakeAt, 13.0F * this.scale(), "", Vec3.ZERO, 0));
            }

            if (age > 12 && this.quakeAt != null) {
               float r = (float)(age - 12) * 0.55F * this.scale();
               if (r < 13.0F * this.scale() + 1.0F) {
                  for (LivingEntity exx : l.getEntitiesOfClass(
                     LivingEntity.class,
                     new AABB(this.quakeAt, this.quakeAt).inflate((double)r + 1.5, 3.0, (double)r + 1.5),
                     exxx -> exxx != this && exxx.isAlive() && !this.quakeHit.contains(exxx.getId())
                  )) {
                     double d = Math.hypot(exx.getX() - this.quakeAt.x, exx.getZ() - this.quakeAt.z);
                     if (d > (double)r - 1.2 && d < (double)r + 0.6 && Math.abs(exx.getY() - this.quakeAt.y) < 2.5) {
                        this.quakeHit.add(exx.getId());
                        this.strike(exx, 9.0F * (1.0F + 0.4F * (this.scale() - 1.0F)), 0.0, 0.0);
                        Vec3 out = new Vec3(exx.getX() - this.quakeAt.x, 0.0, exx.getZ() - this.quakeAt.z);
                        out = out.lengthSqr() < 1.0E-4 ? fwd : out.normalize();
                        exx.setDeltaMovement(out.x * 0.45, 0.95, out.z * 0.45);
                        exx.hurtMarked = true;
                     }
                  }
               }
            }

            if (age >= 40) {
               this.quakeCd = 220;
               this.end(12);
            }
            break;
         default:
            this.end(0);
      }
   }

   boolean adaptedTo(String kind) {
      MahoragaEntity.Adapt a = this.adapting.get(kind);
      return a != null && a.done;
   }

   private void retarget(ServerLevel l) {
      if (--this.retargetIn <= 0 && this.action() != 13 && this.action() != 9 && this.action() != 15) {
         LivingEntity cur;
         boolean var10000;
         label63: {
            this.retargetIn = 10;
            cur = this.getTarget();
            if (cur instanceof Player cp && cp.isAlive() && !cp.isCreative() && !cp.isSpectator() && cp.level() == this.level() && cp.distanceTo(this) < 64.0F) {
               var10000 = true;
               break label63;
            }

            var10000 = false;
         }

         boolean curOk = var10000;
         ServerPlayer best = null;
         double bd = Double.MAX_VALUE;

         for (ServerPlayer p : l.getPlayers(px -> px.isAlive() && !px.isCreative() && !px.isSpectator() && px.distanceTo(this) < 48.0F)) {
            double d = (double)p.distanceTo(this);
            if ((!(d > 16.0) || this.hasLineOfSight(p)) && d < bd) {
               bd = d;
               best = p;
            }
         }

         if (best == null) {
            if (cur instanceof Player && !curOk) {
               this.setTarget(null);
            }
         } else {
            if (!curOk || best != cur && bd < (double)cur.distanceTo(this) * 0.5) {
               this.setTarget(best);
            }
         }
      }
   }

   private void sayDomain(ServerLevel l) {
      boolean any = false;

      for (ServerPlayer sp : l.getPlayers(spx -> spx.distanceTo(this) < 48.0F && Sorcerers.get(spx) == Sorcerer.SUKUNA)) {
         long open = Techniques.domainOpenFor(sp);
         if (open >= 0L) {
            any = true;
            if (open >= 200L && !this.domainSaid) {
               this.domainSaid = true;
               Net.fx(l, new Net.Fx(21, this.getId(), this.position(), 0.0F, ""));
            }
         }
      }

      if (!any) {
         this.domainSaid = false;
         if (this.action() != 13) {
            this.domainWalked = false;
         }
      }
   }

   private boolean blink(ServerLevel l, Vec3 dir) {
      Vec3 flat = dir.multiply(1.0, 0.0, 1.0);
      if (flat.lengthSqr() < 1.0E-4) {
         return false;
      } else {
         flat = flat.normalize();
         Vec3 from = this.position();

         for (int i = 0; i < 7; i++) {
            double ang = i == 0 ? 0.0 : (double)((i + 1) / 2) * 0.45 * (double)(i % 2 == 0 ? 1 : -1);
            Vec3 d = new Vec3(flat.x * Math.cos(ang) - flat.z * Math.sin(ang), 0.0, flat.x * Math.sin(ang) + flat.z * Math.cos(ang));
            Vec3 to = from.add(d.scale(7.0));
            int y = l.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(to.x), Mth.floor(to.z));
            if (!(Math.abs((double)y - from.y) > 5.0)) {
               to = new Vec3(to.x, (double)y, to.z);
               if (l.noCollision(this, this.getBoundingBox().move(to.subtract(from)))) {
                  this.teleportTo(to.x, to.y, to.z);
                  this.getNavigation().stop();
                  l.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 2.0F, 0.4F);
                  this.play(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.5F);
                  Net.fx(l, new Net.Fx(18, this.getId(), from, this.scale(), "", to, 0));
                  this.blinkCd = 140;
                  return true;
               }
            }
         }

         return false;
      }
   }

   private void end(int cd) {
      this.act(0);
      this.basicCd = Math.max(this.basicCd, cd);
   }

   private BlockState groundBlock(Vec3 fwd) {
      BlockPos p = BlockPos.containing(this.position().add(fwd.scale(1.6)).add(0.0, -0.5, 0.0));
      BlockState st = this.level().getBlockState(p);
      if (!st.isAir() && !st.hasBlockEntity() && st.isCollisionShapeFullBlock(this.level(), p) && !(st.getDestroySpeed(this.level(), p) < 0.0F)) {
         return st.is(Blocks.GRASS_BLOCK) ? Blocks.DIRT.defaultBlockState() : st;
      } else {
         return Blocks.DIRT.defaultBlockState();
      }
   }

   static Vec3 aim(Vec3 from, Vec3 to, double speed, double g) {
      Vec3 d = to.subtract(from);
      double flat = Math.sqrt(d.x * d.x + d.z * d.z);
      double T = Math.max(6.0, flat / speed);
      return new Vec3(d.x / T, d.y / T + 0.5 * g * T, d.z / T);
   }

   private void tickChunks(ServerLevel l) {
      Iterator<MahoragaEntity.Chunk> it = this.chunks.iterator();

      while (it.hasNext()) {
         MahoragaEntity.Chunk c = it.next();
         c.age++;
         LivingEntity tgt = this.getTarget();
         if (tgt != null && tgt.isAlive() && !c.struck) {
            Vec3 aimPt = tgt.position().add(0.0, (double)tgt.getBbHeight() * 0.5, 0.0);
            if (aimPt.distanceTo(c.pos) < 6.0) {
               c.vel = aimPt.subtract(c.pos).normalize().scale(Math.max(1.1, c.vel.length()));
            }
         }

         if (tgt != null && tgt.isAlive() && c.age < 40 && !c.struck) {
            Vec3 to = tgt.position().add(0.0, (double)tgt.getBbHeight() * 0.5, 0.0).subtract(c.pos);
            Vec3 flat = to.multiply(1.0, 0.0, 1.0);
            if (flat.lengthSqr() > 1.0) {
               c.vel = c.vel.add(flat.normalize().scale(0.06));
               Vec3 hv = c.vel.multiply(1.0, 0.0, 1.0);
               if (hv.length() > 1.4) {
                  c.vel = new Vec3(hv.normalize().x * 1.4, c.vel.y, hv.normalize().z * 1.4);
               }
            }
         }

         Vec3 next = c.pos.add(c.vel);
         boolean hit = false;
         if (!c.struck) {
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c.pos, next).inflate(1.6), ex -> ex != this && ex.isAlive())) {
               this.strike(e, 13.0F, 1.4, 0.5);
               c.struck = true;
            }

            if (c.struck) {
               c.vel = new Vec3(c.vel.x * 0.15, 0.15, c.vel.z * 0.15);
               next = c.pos.add(c.vel);
            }
         }

         BlockPos bp = BlockPos.containing(next);
         if (c.age > 3 && !l.getBlockState(bp).getCollisionShape(l, bp).isEmpty()) {
            hit = true;
         }

         c.pos = next;
         c.vel = c.vel.add(0.0, -0.05, 0.0);
         if (!hit && c.age < 100) {
            Net.fx(l, new Net.Fx(8, this.getId(), c.pos, (float)c.serial, "", c.vel, Block.getId(c.state)));
         }

         if (hit || c.age > 100) {
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, c.state), c.pos.x, c.pos.y, c.pos.z, 60, 0.8, 0.5, 0.8, 0.3);
            l.sendParticles(ParticleTypes.EXPLOSION, c.pos.x, c.pos.y + 0.5, c.pos.z, 3, 0.6, 0.3, 0.6, 0.0);
            l.playSound(null, c.pos.x, c.pos.y, c.pos.z, SoundEvents.GRAVEL_BREAK, SoundSource.HOSTILE, 2.0F, 0.6F);
            l.playSound(null, c.pos.x, c.pos.y, c.pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.6F, 0.8F);

            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(c.pos, c.pos).inflate(3.2), ex -> ex != this && ex.isAlive())) {
               if (e.position().distanceTo(c.pos) < 3.5) {
                  this.strike(e, 6.0F, 0.8, 0.5);
               }
            }

            Net.fx(l, new Net.Fx(9, this.getId(), c.pos, (float)c.serial, "", Vec3.ZERO, Block.getId(c.state)));
            it.remove();
         }
      }
   }

   static int mimicStyle(String kind) {
      if (kind.startsWith("proj:")) {
         return 0;
      } else if (kind.startsWith("melee:")) {
         return 1;
      } else if (kind.startsWith("tech:")) {
         return 2;
      } else {
         String id = kind.substring(kind.indexOf(58) + 1);
         if (id.equals("inFire") || id.equals("onFire") || id.equals("lava") || id.equals("hotFloor") || id.equals("fireball")) {
            return 3;
         } else if (id.startsWith("explosion") || id.equals("badRespawnPoint") || id.equals("fireworks")) {
            return 4;
         } else if (id.equals("lightningBolt")) {
            return 5;
         } else if (id.equals("magic")
            || id.equals("indirectMagic")
            || id.equals("wither")
            || id.equals("witherSkull")
            || id.equals("sonic_boom")
            || id.equals("dragonBreath")
            || id.equals("thorns")) {
            return 6;
         } else {
            return id.equals("freeze") ? 7 : -1;
         }
      }
   }

   private String pickLearned() {
      List<String> l = new ArrayList<>();

      for (Entry<String, MahoragaEntity.Adapt> en : this.adapting.entrySet()) {
         if (en.getValue().learned) {
            l.add(en.getKey());
         }
      }

      return l.isEmpty() ? null : l.get(this.random.nextInt(l.size()));
   }

   private void mimic(ServerLevel l, String kind, LivingEntity t, Vec3 fwd) {
      MahoragaEntity.Adapt a = this.adapting.get(kind);
      String name = a == null ? "your attack" : a.name;
      Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
      Vec3 hand = this.position().add(0.0, 3.0, 0.0).add(fwd.scale(1.2)).add(right.scale(-0.8));
      Vec3 aimAt = t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0);
      int style = mimicStyle(kind);
      if (t instanceof ServerPlayer sp) {
         sp.displayClientMessage(Component.literal("Mahoraga turns " + name + " against you!").withStyle(ChatFormatting.DARK_RED), true);
      }

      Net.fx(l, new Net.Fx(11, this.getId(), hand, 0.0F, name, aimAt, style));
      switch (style) {
         case 0:
            EntityType<?> type = (EntityType<?>)EntityType.byString(kind.substring(5)).orElse(null);

            for (int i = 0; i < 3 && type != null; i++) {
               if (!(type.create(l) instanceof Projectile pr)) {
                  break;
               }

               pr.setOwner(this);
               pr.moveTo(hand.x, hand.y, hand.z, this.getYRot(), 0.0F);
               Vec3 dir = aimAt.subtract(hand).normalize().add(right.scale((double)(i - 1) * 0.06));
               if (pr instanceof AbstractHurtingProjectile hp) {
                  hp.setDeltaMovement(dir.scale(1.2));
                  hp.xPower = dir.x * 0.1;
                  hp.yPower = dir.y * 0.1;
                  hp.zPower = dir.z * 0.1;
               } else {
                  pr.shoot(dir.x, dir.y + 0.05, dir.z, 2.2F, 2.0F);
               }

               if (pr instanceof AbstractArrow ar) {
                  ar.pickup = Pickup.DISALLOWED;
                  ar.setBaseDamage(4.0);
               }

               l.addFreshEntity(pr);
            }

            this.play(SoundEvents.CROSSBOW_SHOOT, 1.6F, 0.6F);
            break;
         case 1:
            Vec3 dash = t.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            if (dash.length() > 3.0) {
               Vec3 to = this.position().add(dash.normalize().scale(dash.length() - 2.2));
               this.teleportTo(to.x, t.getY(), to.z);
            }

            this.strike(t, 16.0F, 1.8, 0.6);
            this.play(SoundEvents.PLAYER_ATTACK_CRIT, 2.0F, 0.5F);
            break;
         case 2:
         case 6:
            this.strike(t, style == 2 ? 16.0F : 10.0F, 1.0, 0.3);
            if (style == 6) {
               t.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), this);
            }

            this.play(SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 1.2F);
            break;
         case 3:
            this.strike(t, 8.0F, 0.6, 0.2);
            t.setSecondsOnFire(8);
            l.sendParticles(ParticleTypes.FLAME, aimAt.x, aimAt.y, aimAt.z, 40, 0.6, 0.6, 0.6, 0.05);
            this.play(SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F);
            break;
         case 4:
            l.explode(this, aimAt.x, aimAt.y - 0.5, aimAt.z, 3.0F, ExplosionInteraction.NONE);
            break;
         case 5:
            LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(l);
            if (bolt != null) {
               bolt.moveTo(t.getX(), t.getY(), t.getZ());
               l.addFreshEntity(bolt);
            }
            break;
         case 7:
            this.strike(t, 8.0F, 0.4, 0.1);
            t.setTicksFrozen(t.getTicksRequiredToFreeze() + 200);
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3), this);
      }
   }

   private boolean hitArc(Vec3 fwd, double reach, float dmg, double knock, double up) {
      float sc = this.scale();
      reach *= (double)sc;
      dmg *= 1.0F + 0.4F * (sc - 1.0F);
      boolean any = false;
      Vec3 c = this.position().add(0.0, (double)(2.0F * sc), 0.0);

      for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(reach), ex -> ex != this && ex.isAlive())) {
         Vec3 to = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(c);
         Vec3 flat = to.multiply(1.0, 0.0, 1.0);
         if (!(flat.length() > reach + (double)(e.getBbWidth() / 2.0F)) && (!(flat.lengthSqr() > 0.01) || !(flat.normalize().dot(fwd) < 0.35))) {
            this.strike(e, dmg, knock, up);
            any = true;
         }
      }

      if (any) {
         this.hitsLanded++;
      }

      return any;
   }

   private void strike(LivingEntity e, float dmg, double knock, double up) {
      if (e instanceof CurseEntity) {
         e.hurt(this.damageSources().mobAttack(this), Float.MAX_VALUE);
         if (e.isAlive()) {
            e.kill();
         }
      } else {
         if (e instanceof Player p && Sorcerers.get(p) != Sorcerer.NONE) {
            dmg *= 1.25F;
         }

         e.invulnerableTime = 0;
         e.hurt(this.damageSources().mobAttack(this), dmg);
         if (knock > 0.0) {
            Vec3 out = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
            if (out.lengthSqr() > 1.0E-4) {
               out = out.normalize();
               e.setDeltaMovement(e.getDeltaMovement().add(out.x * knock, up, out.z * knock));
               e.hurtMarked = true;
            }
         }
      }
   }

   public void executeSummoner(ServerPlayer p) {
      this.getLookControl().setLookAt(p, 90.0F, 90.0F);
      this.act(1);
      this.play(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
      this.play(SoundEvents.ELDER_GUARDIAN_CURSE, 1.4F, 0.8F);
      if (!p.isCreative() && !p.isSpectator()) {
         p.invulnerableTime = 0;
         p.hurt(this.damageSources().mobAttack(this), Float.MAX_VALUE);
         if (p.isAlive()) {
            p.kill();
         }
      }
   }

   public boolean causeFallDamage(float dist, float mult, DamageSource src) {
      return false;
   }

   public boolean removeWhenFarAway(double d) {
      return false;
   }

   private void play(SoundEvent s, float vol, float pitch) {
      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   protected SoundEvent getHurtSound(DamageSource src) {
      return SoundEvents.IRON_GOLEM_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.WARDEN_DEATH;
   }

   protected float getSoundVolume() {
      return 1.6F;
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Spins", this.spins());
      tag.putBoolean("Intro", this.intro());
      tag.putBoolean("Gills", this.gills());
      tag.putFloat("Scale", this.scale());
      tag.putInt("Grown", this.growCount);
      tag.putInt("Stomps", this.stompCount);
      tag.putBoolean("DomainWalked", this.domainWalked);
      tag.putBoolean("LavaSwim", this.lavaSwim);
      CompoundTag ad = new CompoundTag();

      for (Entry<String, MahoragaEntity.Adapt> en : this.adapting.entrySet()) {
         MahoragaEntity.Adapt a = en.getValue();
         CompoundTag t = new CompoundTag();
         t.putString("Name", a.name);
         t.putBoolean("Tech", a.technique);
         t.putLong("First", a.first);
         t.putFloat("Need", a.need);
         t.putFloat("Progress", a.progress);
         t.putInt("Quarters", a.quarters);
         t.putInt("Hits", a.hits);
         t.putBoolean("Done", a.done);
         t.putBoolean("Learned", a.learned);
         ad.put(en.getKey(), t);
      }

      tag.put("Adapting", ad);
   }

   public void tick() {
      super.tick();
      if (this.deathTime > 0 && !this.isDeadOrDying()) {
         this.deathTime = 0;
      }
   }

   public void startSeenByPlayer(ServerPlayer player) {
      super.startSeenByPlayer(player);
      this.bossBar.addPlayer(player);
   }

   public void stopSeenByPlayer(ServerPlayer player) {
      super.stopSeenByPlayer(player);
      this.bossBar.removePlayer(player);
   }

   public void remove(RemovalReason reason) {
      super.remove(reason);
      this.bossBar.removeAllPlayers();
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.entityData.set(SPINS, tag.getInt("Spins"));
      this.entityData.set(INTRO, !tag.contains("Intro") || tag.getBoolean("Intro"));
      this.entityData.set(GILLS, tag.getBoolean("Gills"));
      this.entityData.set(SCALE, tag.contains("Scale") ? Mth.clamp(tag.getFloat("Scale"), 1.0F, 2.5F) : 1.0F);
      this.growCount = tag.getInt("Grown");
      this.stompCount = tag.getInt("Stomps");
      this.domainWalked = tag.getBoolean("DomainWalked");
      this.lavaSwim = tag.getBoolean("LavaSwim");
      this.announced = tag.contains("Spins");
      CompoundTag ad = tag.getCompound("Adapting");

      for (String k : ad.getAllKeys()) {
         CompoundTag t = ad.getCompound(k);
         MahoragaEntity.Adapt a = new MahoragaEntity.Adapt(t.getString("Name"), t.getBoolean("Tech"), t.getFloat("Need"), t.getLong("First"));
         a.progress = t.getFloat("Progress");
         a.quarters = t.getInt("Quarters");
         a.hits = t.getInt("Hits");
         a.done = t.getBoolean("Done");
         a.learned = t.getBoolean("Learned");
         this.adapting.put(k, a);
      }
   }

   static float clamp01(float x) {
      return Mth.clamp(x, 0.0F, 1.0F);
   }

   private static final class Adapt {
      final String name;
      final boolean technique;
      final long first;
      float need;
      float progress;
      int quarters;
      int hits;
      boolean done;
      boolean learned;

      Adapt(String name, boolean technique, float need, long first) {
         this.name = name;
         this.technique = technique;
         this.need = need;
         this.first = first;
      }
   }

   private static final class Chunk {
      final int serial;
      Vec3 pos;
      Vec3 vel;
      final BlockState state;
      int age;
      boolean struck;

      Chunk(int serial, Vec3 pos, Vec3 vel, BlockState state) {
         this.serial = serial;
         this.pos = pos;
         this.vel = vel;
         this.state = state;
      }
   }
}
