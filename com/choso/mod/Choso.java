package com.choso.mod;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent.Applicable;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Choso {
   public static final int MAX_BLOOD = 1000;
   public static final int SLOTS = 10;
   public static final int CONVERGE_TIME = 24;
   public static final int CONVERGED_TICKS = 240;
   public static final int SCALE_TICKS = 900;
   public static final int STACK_TICKS = 600;
   public static final int STACK_CUT = 56;
   public static final int DOMAIN_CUT = 64;
   public static final int DOMAIN_TICKS = 320;
   public static final int DOMAIN_RADIUS = 20;
   public static final int RAIN_CUT = 84;
   public static final int RAIN_TICKS = 190;
   public static final int RAIN_RADIUS = 28;
   private static final String TAG = "choso";
   private static final int[] COST = new int[]{10, 40, 40, 10, 40, 50, 40, 40, 50, 40};
   private static final int[] COOLDOWN = new int[]{7, 30, 300, 40, 200, 1600, 300, 60, 2400, 160};
   private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
   private static final Map<UUID, long[]> COMBO = new HashMap<>();
   private static final List<Choso.Timed> TIMED = new ArrayList<>();
   private static final List<Choso.Proj> PROJ = new ArrayList<>();
   private static int serial;
   private static long now;

   private Choso() {
   }

   private static CompoundTag data(Player p) {
      CompoundTag root = p.getPersistentData();
      if (!root.contains("choso")) {
         CompoundTag t = new CompoundTag();
         t.putInt("blood", 1000);
         root.put("choso", t);
      }

      return root.getCompound("choso");
   }

   public static int blood(Player p) {
      return data(p).getInt("blood");
   }

   private static void setBlood(Player p, int b) {
      data(p).putInt("blood", Math.max(0, Math.min(1000, b)));
   }

   private static int get(Player p, String k) {
      return data(p).getInt(k);
   }

   private static void set(Player p, String k, int v) {
      data(p).putInt(k, v);
   }

   public static boolean isChoso(Player p) {
      return Sorcerers.is(p, Sorcerer.CHOSO);
   }

   private static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   private static void say(ServerPlayer p, String msg, ChatFormatting c) {
      p.displayClientMessage(Component.literal(msg).withStyle(c), true);
   }

   private static DamageSource bloodDamage(ServerPlayer p) {
      return p.damageSources().indirectMagic(p, p);
   }

   private static void poison(LivingEntity e, int ticks, int amp) {
      e.addEffect(new MobEffectInstance(MobEffects.POISON, ticks, amp, false, false, true));
   }

   private static void hurt(ServerPlayer p, LivingEntity e, float dmg) {
      e.invulnerableTime = 0;
      e.hurt(bloodDamage(p), dmg * scaleMul(p));
   }

   private static float scaleMul(ServerPlayer p) {
      int lvl = get(p, "scaleLvl");
      return get(p, "scale") > 0 ? (lvl >= 2 ? 1.6F : 1.25F) : 1.0F;
   }

   private static Vec3 hands(ServerPlayer p) {
      return p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0.0, -0.35, 0.0);
   }

   private static LivingEntity lookTarget(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      if (bh.getType() != Type.MISS) {
         end = bh.getLocation();
      }

      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         p.level(), p, eye, end, new AABB(eye, end).inflate(1.5), e -> e instanceof LivingEntity && e.isAlive() && e != p, 0.0F
      );
      return eh != null ? (LivingEntity)eh.getEntity() : null;
   }

   private static Vec3 lookPoint(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      return bh.getType() == Type.MISS ? end : bh.getLocation();
   }

   private static List<LivingEntity> around(ServerLevel l, Vec3 c, double r, Entity except) {
      return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> e != except && e.isAlive() && e.distanceToSqr(c) < r * r);
   }

   private static Vec3 ground(ServerLevel l, double x, double z) {
      BlockPos top = l.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0.0, z));
      return new Vec3(x, (double)top.getY(), z);
   }

   private static void pose(ServerLevel l, ServerPlayer p, int pose, int dur) {
      Net.fx(l, new Net.Fx((byte)15, p.position(), Vec3.ZERO, (float)pose, p.getId(), -1, dur));
   }

   public static void useKey(ServerPlayer p, int slot) {
      if (slot >= 0 && slot < 10 && p.isAlive() && isChoso(p) && !busy(p)) {
         long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[10]);
         if (cd[slot] <= now) {
            boolean stackUp = slot == 6 && get(p, "scale") > 0 && get(p, "scaleLvl") < 2;
            int cost = stackUp ? 200 : COST[slot];
            if (!p.isCreative() && blood(p) < cost) {
               say(p, "Not enough blood (" + cost + ")", ChatFormatting.RED);
            } else {
               ServerLevel l = p.serverLevel();

               boolean ok = switch (slot) {
                  case 0 -> bloodEdge(l, p);
                  case 1 -> meteorite(l, p);
                  case 2 -> piercingBlood(l, p);
                  case 3 -> slicing(l, p);
                  case 4 -> binding(l, p);
                  case 5 -> domain(l, p);
                  case 6 -> redScale(l, p);
                  case 7 -> convergence(l, p);
                  case 8 -> bloodRain(l, p);
                  default -> supernova(l, p);
               };
               if (ok) {
                  if (!p.isCreative()) {
                     setBlood(p, blood(p) - cost);
                  }

                  cd[slot] = now + (long)(slot == 6 && !stackUp && get(p, "scaleLvl") < 2 ? 30 : COOLDOWN[slot]);
               }
            }
         }
      }
   }

   static boolean bloodEdge(ServerLevel l, ServerPlayer p) {
      long[] c = COMBO.computeIfAbsent(p.getUUID(), u -> new long[2]);
      int step = now - c[0] < 18L ? (int)((c[1] + 1L) % 3L) : 0;
      c[0] = now;
      c[1] = (long)step;
      Vec3 look = p.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
      p.setDeltaMovement(flat.x * (step == 2 ? 0.9 : 0.6), step == 2 ? 0.25 : 0.05, flat.z * (step == 2 ? 0.9 : 0.6));
      p.hurtMarked = true;
      Vec3 centre = p.getEyePosition().add(look.scale(1.6)).add(0.0, -0.3, 0.0);
      Net.fx(l, new Net.Fx((byte)1, centre, look, (float)step, p.getId(), -1, 8));
      pose(l, p, 3, 7);
      play(l, centre, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.8F + (float)step * 0.2F);
      play(l, centre, SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 1.6F);
      boolean any = false;

      for (LivingEntity e : around(l, centre, 2.6, p)) {
         Vec3 to = e.position().subtract(p.position());
         if (!(to.normalize().dot(flat) < 0.1)) {
            any = true;
            hurt(p, e, step == 2 ? 14.0F : 9.0F);
            poison(e, 60, 1);
            if (step == 2) {
               e.setDeltaMovement(flat.x * 0.6, 0.9, flat.z * 0.6);
               e.hurtMarked = true;
            }

            Vec3 at = e.position().add(0.0, (double)e.getBbHeight() * 0.6, 0.0);
            Net.fx(l, new Net.Fx((byte)0, at, flat, step == 2 ? 1.6F : 0.9F, p.getId(), e.getId(), 0));
         }
      }

      if (any) {
         play(l, centre, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.7F);
         play(l, centre, SoundEvents.SLIME_SQUISH, 1.2F, 0.6F);
      }

      return true;
   }

   static boolean meteorite(final ServerLevel l, ServerPlayer p) {
      pose(l, p, 2, 10);
      Vec3 look = p.getLookAngle();
      final Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();

      for (int i = 0; i < 3; i++) {
         final int k = i;
         TIMED.add(new Choso.Timed(l, p, 3 + i * 3) {
            @Override
            void tick(int age) {
               if (age == this.length) {
                  Vec3 d = this.caster.getLookAngle().add(right.scale((double)(k - 1) * 0.06)).normalize();
                  Choso.launch(l, this.caster, (byte)2, Choso.hands(this.caster).add(right.scale((double)(k - 1) * 0.3)), d.scale(1.7), 40, 9.0F, 1.6, 0.0F);
                  Choso.play(l, this.caster.position(), SoundEvents.TRIDENT_THROW, 1.0F, 0.7F + (float)k * 0.1F);
               }
            }
         });
      }

      return true;
   }

   static boolean piercingBlood(final ServerLevel l, ServerPlayer p) {
      final boolean conv = get(p, "converged") > 0;
      set(p, "converged", 0);
      final int wind = conv ? 10 : 6;
      pose(l, p, 1, wind + (conv ? 110 : 75) + 4);
      Net.fx(l, new Net.Fx((byte)7, hands(p), Vec3.ZERO, conv ? 2.0F : 1.0F, p.getId(), -1, wind));
      play(l, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.8F);
      TIMED.add(
         new Choso.Timed(l, p, wind, true, wind) {
            @Override
            void tick(int age) {
               if (age == wind) {
                  final double range = conv ? 96.0 : 64.0;
                  final double speed = conv ? 2.0 : 1.4;
                  final double turn = Math.toRadians(conv ? 1.6 : 2.4);
                  final int life = conv ? 110 : 75;
                  final int id = ++Choso.serial;
                  Net.fx(
                     l,
                     new Net.Fx(
                        (byte)3,
                        Choso.hands(this.caster),
                        Choso.hands(this.caster).add(this.caster.getLookAngle().scale(range)),
                        conv ? 0.38F : 0.2F,
                        this.caster.getId(),
                        id,
                        life
                     )
                  );
                  Choso.play(l, this.caster.getEyePosition(), SoundEvents.WARDEN_SONIC_BOOM, conv ? 2.0F : 1.0F, conv ? 0.8F : 1.5F);
                  Choso.play(l, this.caster.getEyePosition(), (SoundEvent)ChosoMod.SND_SHOCKWAVE.get(), conv ? 2.0F : 0.8F, conv ? 1.0F : 1.7F);
                  final Map<Integer, Integer> lastHit = new HashMap<>();
                  Choso.TIMED
                     .add(
                        new Choso.Timed(l, this.caster, life + 1) {
                           Vec3 dir = <unrepresentable>.super.caster.getLookAngle();
                           double len = 0.0;

                           @Override
                           void tick(int a2) {
                              if (a2 <= life) {
                                 Vec3 want = this.caster.getLookAngle();
                                 double ang = Math.acos(Mth.clamp(this.dir.dot(want), -1.0, 1.0));
                                 if (ang > 1.0E-4) {
                                    this.dir = this.dir
                                       .scale(Math.sin(ang - Math.min(ang, turn)) / Math.sin(ang))
                                       .add(want.scale(Math.sin(Math.min(ang, turn)) / Math.sin(ang)))
                                       .normalize();
                                 }

                                 this.len = Math.min(range, this.len + speed);
                                 Vec3 eye = Choso.hands(this.caster);
                                 Vec3 tip = eye.add(this.dir.scale(this.len));
                                 if (conv) {
                                    for (double k = Math.max(0.0, this.len - 4.0); k <= this.len; k += 0.5) {
                                       BlockPos bp = BlockPos.containing(eye.add(this.dir.scale(k)));
                                       BlockState st = l.getBlockState(bp);
                                       if (!st.isAir() && st.getDestroySpeed(l, bp) >= 0.0F && st.getDestroySpeed(l, bp) < 40.0F) {
                                          l.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
                                       }
                                    }
                                 } else {
                                    BlockHitResult bh = l.clip(new ClipContext(eye, tip, Block.COLLIDER, Fluid.NONE, this.caster));
                                    if (bh.getType() != Type.MISS) {
                                       tip = bh.getLocation();
                                       if (a2 % 4 == 0) {
                                          Net.fx(l, new Net.Fx((byte)6, tip, this.dir.scale(-1.0), 1.0F, this.caster.getId(), -1, 0));
                                       }
                                    }
                                 }

                                 Vec3 to = tip;
                                 Vec3 d0 = this.dir;
                                 double grow = conv ? 1.2 : 0.5;

                                 for (LivingEntity e : l.getEntitiesOfClass(
                                    LivingEntity.class,
                                    new AABB(eye, to).inflate(grow + 1.0),
                                    ex -> ex != this.caster && ex.isAlive() && ex.getBoundingBox().inflate(grow).clip(eye, to).isPresent()
                                 )) {
                                    Integer last = lastHit.get(e.getId());
                                    if (last == null || a2 - last >= 10) {
                                       lastHit.put(e.getId(), a2);
                                       Choso.hurt(this.caster, e, conv ? 30.0F : 12.0F);
                                       Choso.poison(e, 100, 2);
                                       e.setDeltaMovement(e.getDeltaMovement().add(d0.scale(conv ? 0.8 : 0.4)));
                                       e.hurtMarked = true;
                                       Net.fx(
                                          l,
                                          new Net.Fx(
                                             (byte)0,
                                             e.position().add(0.0, (double)e.getBbHeight() * 0.6, 0.0),
                                             d0,
                                             conv ? 2.4F : 1.0F,
                                             this.caster.getId(),
                                             e.getId(),
                                             0
                                          )
                                       );
                                    }
                                 }

                                 Net.fx(l, new Net.Fx((byte)18, tip, this.dir, a2 >= life ? 1.0F : 0.0F, this.caster.getId(), id, 0));
                                 if (a2 % 10 == 0) {
                                    Choso.play(l, tip, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8F, 1.3F);
                                 }

                                 if (a2 >= life) {
                                    Net.fx(l, new Net.Fx((byte)6, tip, this.dir.scale(-1.0), conv ? 3.5F : 1.6F, this.caster.getId(), -1, 0));
                                 }
                              }
                           }
                        }
                     );
               }
            }
         }
      );
      return true;
   }

   static boolean slicing(final ServerLevel l, ServerPlayer p) {
      pose(l, p, 3, 8);
      Vec3 look = p.getLookAngle();
      launch(l, p, (byte)4, hands(p).add(0.0, 0.2, 0.0), look.scale(1.25), 32, 14.0F, 1.6, 0.5F);
      TIMED.add(
         new Choso.Timed(l, p, 4) {
            @Override
            void tick(int age) {
               if (age == this.length) {
                  Choso.launch(
                     l, this.caster, (byte)4, Choso.hands(this.caster).add(0.0, -0.2, 0.0), this.caster.getLookAngle().scale(1.25), 32, 14.0F, 1.6, -0.5F
                  );
               }
            }
         }
      );
      play(l, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 1.2F, 1.5F);
      play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
      return true;
   }

   static boolean binding(ServerLevel l, ServerPlayer p) {
      final LivingEntity t = lookTarget(p, 24.0);
      if (t == null) {
         say(p, "No target", ChatFormatting.GRAY);
         return false;
      } else {
         int dur = 90;
         pose(l, p, 2, 10);
         Net.fx(l, new Net.Fx((byte)5, hands(p), t.position(), 0.0F, p.getId(), t.getId(), dur));
         Net.fx(l, new Net.Fx((byte)0, t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0), p.getLookAngle(), 1.25F, p.getId(), t.getId(), 0));
         play(l, t.position(), SoundEvents.CHAIN_PLACE, 1.6F, 0.6F);
         play(l, t.position(), SoundEvents.SLIME_SQUISH, 1.4F, 0.5F);
         hurt(p, t, 5.0F);
         poison(t, dur, 1);
         final Vec3 hold = t.position();
         TIMED.add(new Choso.Timed(l, p, dur) {
            @Override
            void tick(int age) {
               if (t.isAlive()) {
                  t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0);
                  if (t.position().distanceToSqr(hold) > 0.04) {
                     if (t instanceof ServerPlayer sp) {
                        sp.connection.teleport(hold.x, hold.y, hold.z, sp.getYRot(), sp.getXRot());
                     } else {
                        t.teleportTo(hold.x, hold.y, hold.z);
                     }
                  }

                  if (t instanceof Mob m) {
                     m.getNavigation().stop();
                  }

                  t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
                  t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 2, false, false, false));
               }
            }
         });
         return true;
      }
   }

   static boolean domain(final ServerLevel l, ServerPlayer p) {
      final Vec3 c = p.position();
      final int r = 20;
      Net.fx(l, new Net.Fx((byte)9, c, Vec3.ZERO, (float)r, p.getId(), -1, 384));
      pose(l, p, 4, 94);
      play(l, c, SoundEvents.ELDER_GUARDIAN_CURSE, 1.5F, 0.5F);
      play(l, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);
      TIMED.add(new Choso.Timed(l, p, 384, true, 64) {
         @Override
         void tick(int age) {
            if (age == 30) {
               Choso.play(l, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.5F);
            }

            if (age == 64) {
               Choso.play(l, c, (SoundEvent)ChosoMod.SND_RUMBLE.get(), 2.5F, 0.6F);
               Choso.play(l, c, (SoundEvent)ChosoMod.SND_SHOCKWAVE.get(), 2.0F, 0.5F);
            }

            if (age >= 64) {
               if (this.caster.position().distanceTo(c) < (double)r) {
                  Choso.setBlood(this.caster, Choso.blood(this.caster) + 4);
               }

               if ((age - 64) % 20 == 0) {
                  for (LivingEntity e : Choso.around(l, c, (double)r, this.caster)) {
                     Vec3 g = e.position();
                     Net.fx(l, new Net.Fx((byte)13, g, Vec3.ZERO, 2.6F, this.caster.getId(), e.getId(), 18));
                     Choso.hurt(this.caster, e, 7.0F);
                     Choso.poison(e, 80, 2);
                     e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, false, true));
                     e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.45, 0.0));
                     e.hurtMarked = true;
                     Choso.play(l, g, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.2F, 0.6F);
                  }

                  Choso.play(l, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.7F);
               }
            }
         }
      });
      return true;
   }

   static boolean redScale(final ServerLevel l, ServerPlayer p) {
      boolean active = get(p, "scale") > 0;
      if (active && get(p, "scaleLvl") >= 2) {
         return false;
      } else if (!active) {
         set(p, "scale", 900);
         set(p, "scaleLvl", 1);
         Net.fx(l, new Net.Fx((byte)8, p.position(), Vec3.ZERO, 1.0F, p.getId(), -1, 900));
         play(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6F, 1.3F);
         play(l, p.position(), SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.6F);
         say(p, "Flowing Red Scale", ChatFormatting.DARK_RED);
         return true;
      } else {
         Net.fx(l, new Net.Fx((byte)8, p.position(), Vec3.ZERO, 2.0F, p.getId(), -1, 600));
         pose(l, p, 5, 46);
         play(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.9F);
         TIMED.add(new Choso.Timed(l, p, 56, true, 56) {
            @Override
            void tick(int age) {
               if (age == 20 || age == 34) {
                  Choso.play(l, this.caster.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0F, 1.1F + (float)age * 0.01F);
               }

               if (age == 50) {
                  Choso.set(this.caster, "scale", 600);
                  Choso.set(this.caster, "scaleLvl", 2);
                  Choso.play(l, this.caster.position(), SoundEvents.FIRE_EXTINGUISH, 1.6F, 0.5F);
                  Choso.play(l, this.caster.position(), (SoundEvent)ChosoMod.SND_SHOCKWAVE.get(), 1.4F, 1.6F);

                  for (LivingEntity e : Choso.around(l, this.caster.position(), 5.0, this.caster)) {
                     Vec3 out = e.position().subtract(this.caster.position()).normalize();
                     e.setDeltaMovement(out.x * 1.2, 0.4, out.z * 1.2);
                     e.hurtMarked = true;
                  }
               }
            }
         });
         return true;
      }
   }

   static boolean convergence(final ServerLevel l, ServerPlayer p) {
      pose(l, p, 1, 28);
      Net.fx(l, new Net.Fx((byte)7, hands(p), Vec3.ZERO, 0.0F, p.getId(), -1, 24));
      play(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 1.6F);
      play(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 1.8F);
      TIMED.add(new Choso.Timed(l, p, 24, true, 24) {
         @Override
         void tick(int age) {
            if (age == 24) {
               Choso.set(this.caster, "converged", 240);
               Choso.play(l, this.caster.position(), SoundEvents.SLIME_BLOCK_BREAK, 1.2F, 0.5F);
               Choso.say(this.caster, "Convergence: Piercing Blood and Supernova are empowered", ChatFormatting.RED);
            }
         }
      });
      return true;
   }

   static boolean bloodRain(final ServerLevel l, ServerPlayer p) {
      final Vec3 c = p.position();
      final int r = 28;
      Net.fx(l, new Net.Fx((byte)10, c, Vec3.ZERO, (float)r, p.getId(), -1, 274));
      pose(l, p, 5, 84);
      play(l, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.5F);
      play(l, c, SoundEvents.ELDER_GUARDIAN_CURSE, 1.2F, 0.4F);
      final Set<Integer> speared = new HashSet<>();
      TIMED.add(
         new Choso.Timed(l, p, 274, true, 84) {
            @Override
            void tick(int age) {
               if (age == 26) {
                  Choso.play(l, c, SoundEvents.GENERIC_SPLASH, 3.0F, 0.4F);
                  Choso.play(l, c, (SoundEvent)ChosoMod.SND_RUMBLE.get(), 2.5F, 0.7F);
               }

               if (age == 74) {
                  Choso.play(l, c, (SoundEvent)ChosoMod.SND_CRACK.get(), 2.0F, 0.6F);
               }

               if (age >= 84) {
                  int t = age - 84;
                  if (t % 20 == 0) {
                     Choso.play(l, c, SoundEvents.WEATHER_RAIN_ABOVE, 3.0F, 0.5F);
                     Choso.play(l, c, SoundEvents.WEATHER_RAIN, 3.0F, 0.6F);
                  }

                  if (t % 8 == 0) {
                     for (LivingEntity e : Choso.around(l, c, (double)r, this.caster)) {
                        if (l.canSeeSky(e.blockPosition().above())) {
                           Choso.hurt(this.caster, e, 3.0F);
                           Choso.poison(e, 120, 2);
                           e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, true));
                        }
                     }
                  }

                  if (t >= 60 && t % 12 == 0) {
                     for (final LivingEntity ex : Choso.around(l, c, (double)r, this.caster)) {
                        if (speared.add(ex.getId() * 31 + t)) {
                           Vec3 at = ex.position();
                           Net.fx(
                              l,
                              new Net.Fx(
                                 (byte)17,
                                 at.add(l.random.nextGaussian() * 3.0, 34.0, l.random.nextGaussian() * 3.0),
                                 at,
                                 1.0F,
                                 this.caster.getId(),
                                 ex.getId(),
                                 8
                              )
                           );
                           final int hitT = t;
                           Choso.TIMED
                              .add(
                                 new Choso.Timed(l, this.caster, 6) {
                                    @Override
                                    void tick(int a2) {
                                       if (a2 == 6 && e.isAlive()) {
                                          Choso.hurt(this.caster, e, hitT >= 150 ? 60.0F : 18.0F);
                                          Net.fx(
                                             l,
                                             new Net.Fx(
                                                (byte)0,
                                                e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0),
                                                new Vec3(0.0, -1.0, 0.0),
                                                hitT >= 150 ? 2.4F : 1.3F,
                                                this.caster.getId(),
                                                e.getId(),
                                                0
                                             )
                                          );
                                          Net.fx(l, new Net.Fx((byte)6, e.position(), new Vec3(0.0, 1.0, 0.0), 2.5F, this.caster.getId(), -1, 0));
                                          Choso.play(l, e.position(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.6F, 0.5F);
                                          Choso.play(l, e.position(), (SoundEvent)ChosoMod.SND_CRACK.get(), 1.0F, 1.4F);
                                       }
                                    }
                                 }
                              );
                        }
                     }
                  }

                  if (t % 5 == 0) {
                     double a = l.random.nextDouble() * Math.PI * 2.0;
                     double d = Math.sqrt(l.random.nextDouble()) * (double)r;
                     Vec3 g = Choso.ground(l, c.x + Math.cos(a) * d, c.z + Math.sin(a) * d);
                     Net.fx(l, new Net.Fx((byte)6, g, new Vec3(0.0, 1.0, 0.0), 1.2F + l.random.nextFloat() * 1.5F, this.caster.getId(), -1, 0));
                  }
               }
            }
         }
      );
      return true;
   }

   static boolean supernova(final ServerLevel l, ServerPlayer p) {
      final boolean conv = get(p, "converged") > 0;
      set(p, "converged", 0);
      final int count = conv ? 16 : 8;
      final int orbit = 26;
      pose(l, p, 1, orbit);
      Net.fx(l, new Net.Fx((byte)11, p.position(), Vec3.ZERO, (float)count, p.getId(), -1, orbit));
      play(l, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.4F);
      play(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.6F, 1.5F);
      TIMED.add(new Choso.Timed(l, p, orbit) {
         @Override
         void tick(int age) {
            if (age == orbit) {
               Choso.pose(l, this.caster, 4, 10);
               Vec3 o = this.caster.position().add(0.0, 1.2, 0.0);
               List<LivingEntity> near = Choso.around(l, o, 24.0, this.caster);
               near.sort((x, y) -> Double.compare(x.distanceToSqr(o), y.distanceToSqr(o)));

               for (int i = 0; i < count; i++) {
                  Vec3 dir;
                  if (!near.isEmpty() && i % 2 == 0) {
                     LivingEntity t = near.get(i / 2 % near.size());
                     dir = t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0).subtract(o).normalize();
                  } else {
                     double a = (double)i * Math.PI * 2.0 / (double)count;
                     dir = new Vec3(Math.cos(a), 0.05, Math.sin(a));
                  }

                  dir = dir.add(l.random.nextGaussian() * 0.06, l.random.nextGaussian() * 0.04, l.random.nextGaussian() * 0.06).normalize();
                  Choso.launch(l, this.caster, (byte)16, o.add(dir.scale(1.6)), dir.scale(1.3), 30, conv ? 16.0F : 10.0F, 3.2, conv ? 1.5F : 1.0F);
               }

               Choso.play(l, o, (SoundEvent)ChosoMod.SND_CRACK.get(), 1.6F, 1.3F);
               Choso.play(l, o, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.6F);
            }
         }
      });
      return true;
   }

   private static void launch(ServerLevel l, ServerPlayer p, byte kind, Vec3 pos, Vec3 vel, int life, float dmg, double burst, float f) {
      Choso.Proj pr = new Choso.Proj(l, p, kind, pos, vel, life, dmg, burst, ++serial);
      PROJ.add(pr);
      Net.fx(l, new Net.Fx(kind, pos, vel, f, p.getId(), pr.serial, life));
   }

   private static void stepProjectiles() {
      Iterator<Choso.Proj> it = PROJ.iterator();

      while (it.hasNext()) {
         Choso.Proj pr = it.next();
         pr.age++;
         Vec3 next = pr.pos.add(pr.vel);
         boolean end = pr.age >= pr.life || !pr.owner.isAlive();
         Vec3 hitAt = null;
         if (!end) {
            BlockHitResult bh = pr.level.clip(new ClipContext(pr.pos, next, Block.COLLIDER, Fluid.NONE, pr.owner));
            if (pr.kind == 4) {
               for (double s = 0.0; s <= 1.0; s += 0.34) {
                  BlockPos bp = BlockPos.containing(pr.pos.add(pr.vel.scale(s)));
                  BlockState st = pr.level.getBlockState(bp);
                  if (st.is(BlockTags.LEAVES) || st.is(BlockTags.FLOWERS) || st.is(BlockTags.REPLACEABLE) || st.is(Blocks.BAMBOO)) {
                     pr.level.destroyBlock(bp, false);
                  }
               }
            }

            if (bh.getType() != Type.MISS) {
               hitAt = bh.getLocation();
               end = true;
            }

            AABB box = new AABB(pr.pos, next).inflate(pr.kind == 4 ? 1.1 : 0.6);

            for (LivingEntity e : pr.level.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != pr.owner && ex.isAlive())) {
               if (pr.kind != 4) {
                  hitAt = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
                  end = true;
                  break;
               }

               if (pr.hit.add(e.getId())) {
                  hurt(pr.owner, e, pr.dmg);
                  poison(e, 60, 1);
                  Net.fx(
                     pr.level,
                     new Net.Fx((byte)0, e.position().add(0.0, (double)e.getBbHeight() * 0.6, 0.0), pr.vel.normalize(), 1.1F, pr.owner.getId(), e.getId(), 0)
                  );
                  play(pr.level, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.4F);
               }
            }
         }

         if (end) {
            Vec3 at = hitAt != null ? hitAt : pr.pos;
            if (pr.kind != 4 || hitAt != null) {
               for (LivingEntity e : around(pr.level, at, pr.burst, pr.owner)) {
                  hurt(pr.owner, e, pr.dmg);
                  poison(e, 80, 1);
                  Vec3 out = e.position().subtract(at).normalize();
                  e.setDeltaMovement(e.getDeltaMovement().add(out.scale(pr.kind == 16 ? 0.9 : 0.5)).add(0.0, 0.25, 0.0));
                  e.hurtMarked = true;
                  Net.fx(
                     pr.level,
                     new Net.Fx(
                        (byte)0,
                        e.position().add(0.0, (double)e.getBbHeight() * 0.6, 0.0),
                        pr.vel.normalize(),
                        pr.kind == 16 ? 1.3F : 1.0F,
                        pr.owner.getId(),
                        e.getId(),
                        0
                     )
                  );
               }
            }

            Net.fx(
               pr.level,
               new Net.Fx(
                  (byte)(pr.kind == 16 ? 12 : 6), at, pr.vel.normalize().scale(-1.0), pr.kind == 16 ? (float)pr.burst : 1.6F, pr.owner.getId(), pr.serial, 0
               )
            );
            play(
               pr.level,
               at,
               pr.kind == 16 ? (SoundEvent)ChosoMod.SND_CRACK.get() : SoundEvents.SLIME_BLOCK_BREAK,
               pr.kind == 16 ? 1.2F : 1.0F,
               pr.kind == 16 ? 1.5F : 0.7F
            );
            play(pr.level, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8F, 1.2F);
            it.remove();
         } else {
            pr.pos = next;
         }
      }
   }

   private static boolean busy(ServerPlayer p) {
      for (Choso.Timed t : TIMED) {
         if (t.caster == p && t.holds && t.age < t.holdUntil) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.END) {
         now++;

         for (Choso.Timed t : new ArrayList<>(TIMED)) {
            t.age++;
            if (!t.caster.isAlive()) {
               TIMED.remove(t);
            } else {
               if (t.holds && t.age < t.holdUntil) {
                  t.caster.setDeltaMovement(0.0, Math.min(0.0, t.caster.getDeltaMovement().y), 0.0);
                  if (t.caster.position().distanceToSqr(t.at) > 0.01) {
                     t.caster.connection.teleport(t.at.x, Math.min(t.at.y, t.caster.getY()), t.at.z, t.caster.getYRot(), t.caster.getXRot());
                  }
               }

               t.tick(t.age);
               if (t.age >= t.length) {
                  TIMED.remove(t);
               }
            }
         }

         stepProjectiles();
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         ServerLevel var9 = p.serverLevel();
         boolean choso = isChoso(p);
         int scale = get(p, "scale");
         if (choso && p.tickCount % 8 == 0) {
            setBlood(p, blood(p) + 1);
         }

         if (choso && p.hasEffect(MobEffects.POISON)) {
            p.removeEffect(MobEffects.POISON);
         }

         if (scale > 0) {
            set(p, "scale", scale - 1);
            int lvl = get(p, "scaleLvl");
            if (p.tickCount % 20 == 0) {
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, lvl >= 2 ? 2 : 1, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, lvl >= 2 ? 3 : 1, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, lvl >= 2 ? 2 : 1, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, lvl >= 2 ? 1 : 0, false, false));
               if (lvl >= 2) {
                  p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
               }
            }

            if (scale == 1) {
               set(p, "scaleLvl", 0);
               Net.fx(var9, new Net.Fx((byte)14, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
               COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[10])[6] = now + (long)COOLDOWN[6];
               if (lvl >= 2 && !p.isCreative()) {
                  p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                  p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                  setBlood(p, blood(p) - 50);
                  say(p, "Flowing Red Scale: Stack has burned out", ChatFormatting.DARK_RED);
               }
            }
         }

         int conv = get(p, "converged");
         if (conv > 0) {
            set(p, "converged", conv - 1);
         }

         if (p.tickCount % 5 == 0 && (choso || scale > 0)) {
            long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[10]);
            int[] left = new int[10];

            for (int i = 0; i < 10; i++) {
               left[i] = (int)Math.max(0L, cd[i] - now);
            }

            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.State(blood(p), get(p, "scale"), get(p, "scaleLvl"), get(p, "converged"), left));
         }
      }
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent e) {
      if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p || !isChoso(p)) {
         return;
      }

      if (!e.getSource().getMsgId().equals("indirectMagic")) {
         LivingEntity t = e.getEntity();
         ServerLevel l = p.serverLevel();
         int lvl = get(p, "scale") > 0 ? get(p, "scaleLvl") : 0;
         Vec3 at = t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0);
         Net.fx(l, new Net.Fx((byte)0, at, p.getLookAngle(), lvl >= 2 ? 1.25F : 0.7F, p.getId(), t.getId(), 0));
         if (lvl > 0) {
            Vec3 d = p.getLookAngle();
            t.setDeltaMovement(t.getDeltaMovement().add(d.x * 0.5 * (double)lvl, 0.15 * (double)lvl, d.z * 0.5 * (double)lvl));
            t.hurtMarked = true;
         }
      }
   }

   @SubscribeEvent
   public static void onEffect(Applicable e) {
      if (e.getEffectInstance().getEffect() == MobEffects.POISON && e.getEntity() instanceof Player p && isChoso(p)) {
         e.setResult(Result.DENY);
      }
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      TIMED.clear();
      PROJ.clear();
      COOLDOWNS.clear();
      COMBO.clear();
   }

   private static final class Proj {
      final ServerLevel level;
      final ServerPlayer owner;
      final byte kind;
      final Vec3 vel;
      final int life;
      final int serial;
      final float dmg;
      final double burst;
      final Set<Integer> hit = new HashSet<>();
      Vec3 pos;
      int age;

      Proj(ServerLevel level, ServerPlayer owner, byte kind, Vec3 pos, Vec3 vel, int life, float dmg, double burst, int serial) {
         this.level = level;
         this.owner = owner;
         this.kind = kind;
         this.pos = pos;
         this.vel = vel;
         this.life = life;
         this.dmg = dmg;
         this.burst = burst;
         this.serial = serial;
      }
   }

   private abstract static class Timed {
      final ServerLevel level;
      final ServerPlayer caster;
      final int length;
      final boolean holds;
      final int holdUntil;
      final Vec3 at;
      int age;

      Timed(ServerLevel level, ServerPlayer caster, int length) {
         this(level, caster, length, false, 0);
      }

      Timed(ServerLevel level, ServerPlayer caster, int length, boolean holds, int holdUntil) {
         this.level = level;
         this.caster = caster;
         this.length = length;
         this.holds = holds;
         this.holdUntil = holdUntil;
         this.at = caster.position();
      }

      abstract void tick(int var1);
   }
}
