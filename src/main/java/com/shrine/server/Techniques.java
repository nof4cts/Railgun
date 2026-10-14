package com.shrine.server;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.shrine.common.Move;
import com.shrine.common.Slash;
import com.shrine.common.Timing;
import com.shrine.config.ShrineConfig;
import com.shrine.network.Net;
import com.shrine.network.S2CCast;
import com.shrine.network.S2CCooldowns;
import com.shrine.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PacketDistributor.TargetPoint;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Techniques {
   private static final int FLAGS = 50;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private static final Map<UUID, long[]> READY = new HashMap<>();
   private static final Map<UUID, Long> BUSY = new HashMap<>();
   private static final List<Techniques.Cast> ACTIVE = new ArrayList<>();

   private Techniques() {
   }

   public static boolean domainActive(ServerPlayer p) {
      long now = p.serverLevel().getGameTime();

      for (Techniques.Cast c : ACTIVE) {
         if (c.caster == p && c.move == Move.DOMAIN && now < c.endsAt) {
            return true;
         }
      }

      return false;
   }

   public static long domainOpenFor(ServerPlayer p) {
      long now = p.serverLevel().getGameTime();

      for (Techniques.Cast c : ACTIVE) {
         if (c.caster == p && c.move == Move.DOMAIN && now < c.endsAt) {
            return now - c.start;
         }
      }

      return -1L;
   }

   /** The build server's self-test: ready every move again. */
   public static void testReset(ServerPlayer p) {
      READY.remove(p.getUUID());
      BUSY.remove(p.getUUID());
   }

   private static int cooldown(Move m) {
      // Dismantle is ready again after 5 seconds; everything else keeps its 10 second floor
      return Math.max(m == Move.DISMANTLE ? 100 : 200, (int)Math.round((double)m.cooldown * (Double)ShrineConfig.SERVER.cooldownScale.get()));
   }

   public static void cast(ServerPlayer p, Move move) {
      cast(p, move, false);
   }

   public static void cast(ServerPlayer p, Move move, boolean film) {
      film = film && move == Move.FUGA;
      if (Sorcerers.is(p, Sorcerer.SUKUNA)) {
         ServerLevel level = p.serverLevel();
         long now = level.getGameTime();
         if (!(Boolean)ShrineConfig.SERVER.opOnly.get() || p.hasPermissions(2)) {
            long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
            Long busy = BUSY.get(p.getUUID());

            int ceCost = switch (move) {
               case DISMANTLE, BARRAGE, CLEAVE -> 10;
               case SPIDERWEB, BLACK_FLASH, FUGA -> 40;
               default -> 50;
            };
            if (now >= ready[move.ordinal()] && (busy == null || now >= busy) && !p.isSpectator() && Energy.has(p, ceCost)) {
               Vec3 eye = p.getEyePosition();
               Vec3 look = p.getLookAngle();
               long seed = level.random.nextLong();
               Vec3 point = eye;
               float size = 0.0F;
               Entity target = null;
               switch (move) {
                  case DISMANTLE:
                  case BARRAGE:
                     size = (float)((Integer)ShrineConfig.SERVER.dismantleLength.get()).intValue();
                     break;
                  case CLEAVE:
                     target = pick(p, 7.0);
                     if (target == null) {
                        p.playNotifySound((SoundEvent)ModSounds.DENY.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
                        return;
                     }

                     point = target.position().add(0.0, (double)target.getBbHeight() * 0.5, 0.0);
                     size = Math.max(target.getBbWidth(), target.getBbHeight());
                     break;
                  case SPIDERWEB:
                     BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(6.0)), Block.COLLIDER, Fluid.NONE, p));
                     point = hit.getType() == Type.MISS ? p.position() : hit.getLocation();
                     size = 24.0F;
                     break;
                  case BLACK_FLASH:
                     target = pick(p, 5.0);
                     point = target != null ? target.position().add(0.0, (double)target.getBbHeight() * 0.55, 0.0) : eye.add(look.scale(2.2));
                     break;
                  case FUGA:
                     double range = (double)((Integer)ShrineConfig.SERVER.fugaRange.get()).intValue();
                     BlockHitResult fhit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), Block.COLLIDER, Fluid.NONE, p));
                     point = fhit.getType() == Type.MISS ? eye.add(look.scale(range)) : fhit.getLocation();
                     Entity e = pick(p, eye.distanceTo(point));
                     if (e != null) {
                        point = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
                     }

                     size = (float)((Integer)ShrineConfig.SERVER.fugaRadius.get()).intValue();
                     break;
                  case DOMAIN:
                     point = p.position();
                     size = (float)((Integer)ShrineConfig.SERVER.domainRadius.get()).intValue();
                     break;
                  case WORLD_SLASH:
                     Vec3 flat = new Vec3(look.x, 0.0, look.z);
                     look = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
                     point = p.position();
                     size = (float)((Integer)ShrineConfig.SERVER.worldSlashLength.get()).intValue();
               }

               ready[move.ordinal()] = now + (long)cooldown(move);
               Energy.spend(p, ceCost);
               if (move.cutscene) {
                  BUSY.put(p.getUUID(), now + (long)(move == Move.DOMAIN ? 150 : Timing.end(move, film)));
               }

               Techniques.Cast c = new Techniques.Cast(move, p, now, eye, look, point, seed, size, target);
               c.film = film;
               c.endsAt = now + (long)Timing.end(move, film);
               if (move == Move.DOMAIN) {
                  c.endsAt = now + 52L + (long)((Integer)ShrineConfig.SERVER.domainSeconds.get()).intValue() * 20L;
               }

               ACTIVE.add(c);
               S2CCast msg = new S2CCast(
                  move,
                  p.getId(),
                  now,
                  eye,
                  look,
                  point,
                  seed,
                  move == Move.FUGA ? size * 0.1F : size, // Open: the fireball is drawn at a tenth of the blast it leaves
                  move == Move.DOMAIN ? (Integer)ShrineConfig.SERVER.domainSeconds.get() : (target == null ? -1 : target.getId()),
                  (float)((Integer)ShrineConfig.SERVER.cutsceneRadius.get()).intValue(),
                  film
               );
               double reach = move == Move.WORLD_SLASH ? (double)Math.max(512.0F, size) : 512.0;
               Net.CHANNEL.send(PacketDistributor.NEAR.with(() -> new TargetPoint(eye.x, eye.y, eye.z, reach, level.dimension())), msg);
               sendCooldowns(p);
            } else {
               p.playNotifySound((SoundEvent)ModSounds.DENY.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            }
         }
      }
   }

   private static void sendCooldowns(ServerPlayer p) {
      long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[Move.values().length]);
      int[] total = new int[Move.values().length];

      for (Move m : Move.values()) {
         total[m.ordinal()] = cooldown(m);
      }

      Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CCooldowns((long[])ready.clone(), total));
   }

   private static Entity pick(ServerPlayer p, double reach) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(reach));
      AABB box = p.getBoundingBox().expandTowards(p.getLookAngle().scale(reach)).inflate(1.5);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(p.level(), p, eye, end, box, e -> e.isAlive() && e.isPickable() && e != p, 0.8F);
      return hit == null ? null : hit.getEntity();
   }

   @SubscribeEvent
   public static void onLevelTick(LevelTickEvent event) {
      if (event.phase == Phase.END && event.level instanceof ServerLevel level) {
         if (!ACTIVE.isEmpty()) {
            long now = level.getGameTime();
            Iterator<Techniques.Cast> it = ACTIVE.iterator();

            while (it.hasNext()) {
               Techniques.Cast c = it.next();
               if (c.level == level) {
                  int t = (int)(now - c.start);
                  if (c.caster.isRemoved() && c.move.cutscene && t < 2) {
                     it.remove();
                  } else {
                     switch (c.move) {
                        case DISMANTLE:
                           if (t == 2) {
                              slash(c, Slash.of(c.origin.subtract(c.dir.scale(0.5)), c.dir, Slash.roll(c.seed, 0), (double)c.size, 16.0), 1.0);
                              sound(c, c.origin, (SoundEvent)ModSounds.DISMANTLE.get(), 2.5F, 1.0F);
                           }
                           break;
                        case BARRAGE:
                           int k = t - 3;
                           if (k >= 0 && k % 2 == 0 && k / 2 < 16) {
                              int i = k / 2;
                              Vec3 eye = c.caster.getEyePosition();
                              Vec3 d = Slash.jitter(c.seed, i, c.caster.getLookAngle(), 0.35);
                              slash(c, Slash.of(eye.subtract(d.scale(0.5)), d, Slash.roll(c.seed, i), (double)c.size * 0.75, 11.0), 0.3);
                              if (i == 0) {
                                 sound(c, eye, (SoundEvent)ModSounds.BARRAGE.get(), 2.5F, 1.0F);
                              }
                           }
                           break;
                        case CLEAVE:
                           if (t == 3 && c.target != null && c.target.isAlive()) {
                              hit(c, c.target, 1.25);
                              sound(c, c.point, (SoundEvent)ModSounds.CLEAVE.get(), 2.0F, 1.0F);
                           }
                           break;
                        case SPIDERWEB:
                           if (t == 7) {
                              spiderweb(c);
                           }
                           break;
                        case BLACK_FLASH:
                           if (t == 4) {
                              blackFlash(c);
                           }
                           break;
                        case FUGA:
                           fuga(c, t);
                           break;
                        case DOMAIN:
                           domain(c, t, now);
                           break;
                        case WORLD_SLASH:
                           worldSlash(c, t);
                     }

                     if (now >= c.endsAt) {
                        it.remove();
                        if (c.caster.isAlive()) {
                           sendCooldowns(c.caster);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void sound(Techniques.Cast c, Vec3 at, SoundEvent e, float volume, float pitch) {
      c.level.playSound((Player)null, at.x, at.y, at.z, e, SoundSource.PLAYERS, volume, pitch);
   }

   private static DamageSource source(Techniques.Cast c) {
      return c.level.damageSources().indirectMagic(c.caster, c.caster);
   }

   private static void hit(Techniques.Cast c, Entity e, double scale) {
      if (e != c.caster && e.isAlive()) {
         if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
            return;
         }

         double dmg = (Double)ShrineConfig.SERVER.damage.get() * scale;
         if (e instanceof LivingEntity l) {
            l.invulnerableTime = 0;
            l.hurt(source(c), (float)Math.min(dmg, Float.MAX_VALUE));
         } else if (!(e instanceof Player)) {
            e.discard();
         }
      }
   }

   private static void slash(Techniques.Cast c, Slash s, double scale) {
      AABB box = new AABB(s.origin(), s.origin().add(s.dir().scale(s.length()))).inflate(s.width() * 0.5 + 2.0);

      for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
         double t = Math.max((double)e.getBbWidth(), 0.6) * 0.5 + 0.9;
         Vec3 base = e.position();
         if (s.contains(base.add(0.0, (double)e.getBbHeight() * 0.5, 0.0), t + (double)e.getBbHeight() * 0.3, s.length())
            || s.contains(base.add(0.0, (double)e.getBbHeight() * 0.9, 0.0), t, s.length())
            || s.contains(base.add(0.0, 0.1, 0.0), t, s.length())) {
            hit(c, e, scale);
         }
      }

      if ((Boolean)ShrineConfig.SERVER.cutBlocks.get()) {
         MutableBlockPos pos = new MutableBlockPos();
         double hw = s.width() * 0.5;

         for (double a = 1.5; a <= s.length(); a += 0.45) {
            for (double w = -hw; w <= hw; w += 0.45) {
               Vec3 p = s.origin().add(s.dir().scale(a)).add(s.side().scale(w));
               pos.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z));
               BlockState st = c.level.getBlockState(pos);
               if (!st.isAir() && !st.is(Blocks.BEDROCK) && st.getFluidState().isEmpty() && !(st.getDestroySpeed(c.level, pos) < 0.0F)) {
                  c.level.setBlock(pos, AIR, 50);
               }
            }
         }
      }
   }

   private static void blackFlash(Techniques.Cast c) {
      sound(c, c.point, (SoundEvent)ModSounds.BLACK_FLASH.get(), 3.0F, 1.0F);
      AABB box = new AABB(c.point, c.point).inflate(2.5);

      for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
         Vec3 push = e.position().subtract(c.caster.position()).multiply(1.0, 0.0, 1.0);
         push = push.lengthSqr() < 1.0E-4 ? c.dir : push.normalize();
         hit(c, e, 1.25);
         e.setDeltaMovement(push.scale(4.5).add(0.0, 0.8, 0.0));
         e.hurtMarked = true;
      }

      if ((Boolean)ShrineConfig.SERVER.cutBlocks.get()) {
         crater(c.level, BlockPos.containing(c.point), 3);
      }
   }

   private static void crater(ServerLevel level, BlockPos centre, int r) {
      MutableBlockPos pos = new MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               if (dx * dx + dy * dy + dz * dz <= r * r) {
                  pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                  BlockState st = level.getBlockState(pos);
                  if (!st.isAir() && st.getDestroySpeed(level, pos) >= 0.0F) {
                     level.setBlock(pos, AIR, 50);
                  }
               }
            }
         }
      }
   }

   private static void spiderweb(Techniques.Cast c) {
      sound(c, c.point, (SoundEvent)ModSounds.SPIDERWEB.get(), 4.0F, 1.0F);
      double r = (double)c.size;
      AABB box = new AABB(c.point, c.point).inflate(r, 10.0, r);

      for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
         if (!(e.position().distanceTo(c.point) > r)) {
            hit(c, e, 1.0);
            e.setDeltaMovement(e.getDeltaMovement().add(0.0, -1.0, 0.0));
            e.hurtMarked = true;
         }
      }

      if ((Boolean)ShrineConfig.SERVER.cutBlocks.get()) {
         RandomSource rand = RandomSource.create(c.seed);
         BlockPos centre = BlockPos.containing(c.point);
         MutableBlockPos pos = new MutableBlockPos();
         int R = (int)r;

         for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
               double d = Math.sqrt((double)(dx * dx + dz * dz));
               if (!(d > (double)R)) {
                  double ang = Math.atan2((double)dz, (double)dx);
                  double spoke = Math.abs(Mth.wrapDegrees(Math.toDegrees(ang) * 1.0 + 15.0) % 30.0);
                  boolean onSpoke = Math.min(spoke, 30.0 - spoke) * Math.PI / 180.0 * d < 0.9;
                  boolean onRing = Math.abs(d - 7.0) < 0.7 || Math.abs(d - 14.0) < 0.7 || Math.abs(d - 21.0) < 0.7;
                  int depth;
                  if (!onSpoke && !onRing) {
                     depth = (double)rand.nextFloat() < 0.55 * (1.0 - d / (double)R) + 0.2 ? 2 + rand.nextInt(3) : 0;
                  } else {
                     depth = 9;
                  }

                  if (depth != 0) {
                     for (int dy = 2; dy >= -depth; dy--) {
                        pos.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                        BlockState st = c.level.getBlockState(pos);
                        if (!st.isAir() && !(st.getDestroySpeed(c.level, pos) < 0.0F)) {
                           c.level.setBlock(pos, AIR, 50);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   /** Mahoraga adapts to everything else; Open burns him out of existence in one shot. */
   private static boolean mahoraga(Entity e) {
      return e instanceof com.mahoraga.mod.MahoragaEntity;
   }

   private static void annihilate(Techniques.Cast c, Entity e) {
      if (e instanceof LivingEntity l && e.isAlive()) {
         l.invulnerableTime = 0;
         // generic_kill bypasses invulnerability, so his adaptation never sees it
         l.hurt(new DamageSource(c.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
            .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL), c.caster), Float.MAX_VALUE);
         if (l.isAlive()) {
            l.setHealth(0.0F);
            l.die(source(c));
         }
      }
   }

   private static void openHit(Techniques.Cast c, Entity e, double scale) {
      if (mahoraga(e)) {
         annihilate(c, e);
      } else {
         hit(c, e, scale);
      }
   }

   private static void fuga(Techniques.Cast c, int t0) {
      // the film version runs the same beats, shifted so the blast lands the tick the film ends
      int t = c.film ? t0 - Timing.FUGA_FILM_SHIFT : t0;
      // whoever watches the film hears its own soundtrack; the world's sounds are for everyone else
      Player deaf = c.film ? c.caster : null;
      if (t == 0) {
         c.level.playSound(deaf, c.origin.x, c.origin.y, c.origin.z, (SoundEvent)ModSounds.FUGA_IGNITE.get(), SoundSource.PLAYERS, 3.0F, 1.0F);
      }

      if (t == 80) {
         c.level.playSound(deaf, c.origin.x, c.origin.y, c.origin.z, (SoundEvent)ModSounds.FUGA_RELEASE.get(), SoundSource.PLAYERS, 4.0F, 1.0F);
      }

      if (t > 80 && t <= 89) {
         float u0 = (float)(t - 1 - 80) / 9.0F;
         float u1 = (float)(t - 80) / 9.0F;
         Vec3 from = c.origin.lerp(c.point, (double)u0);
         Vec3 to = c.origin.lerp(c.point, (double)u1);

         for (Entity e : c.level.getEntities(c.caster, new AABB(from, to).inflate(3.5), Entity::isAlive)) {
            openHit(c, e, 5.0);
            e.setSecondsOnFire(12);
         }
      }

      if (t == 89) {
         sound(c, c.point, (SoundEvent)ModSounds.FUGA_IMPACT.get(), 60.0F, 1.0F);
         double r = (double)c.size;
         AABB box = new AABB(c.point, c.point).inflate(r * 1.8);

         for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
            double d = e.position().distanceTo(c.point);
            if (!(d > r * 1.8)) {
               double k = 1.0 - d / (r * 1.8);
               if (mahoraga(e)) {
                  annihilate(c, e);
               } else if (d < r * 0.6) {
                  hit(c, e, 8.0);
               } else if (e instanceof LivingEntity) {
                  hit(c, e, 2.0 + 4.0 * k);
               }

               e.setSecondsOnFire(15);
               Vec3 away = e.position().subtract(c.point);
               away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : away.normalize();
               e.setDeltaMovement(e.getDeltaMovement().add(away.scale(1.5 + 4.0 * k)).add(0.0, 1.2 + 2.5 * k, 0.0));
               e.hurtMarked = true;
            }
         }

         if ((Boolean)ShrineConfig.SERVER.cutBlocks.get()) {
            c.blast = new Consumption(c.level, c.point, (int)Math.ceil(r * 1.6) + 1, c.level.getGameTime(), 40)
               .with(scorcher(c, Math.max(1, (int)r)), (Consumption.Sink)null);
         }
      }

      if (c.blast != null) {
         c.blast.tick(c.level.getGameTime(), (Integer)ShrineConfig.SERVER.blocksPerTick.get());
      }

      if (t > 89 && t % 10 == 0) {
         for (Entity ex : c.level
            .getEntities(c.caster, new AABB(c.point, c.point).inflate((double)c.size * 1.3, (double)(c.size * 4.0F), (double)c.size * 1.3), Entity::isAlive)) {
            ex.setSecondsOnFire(6);
         }
      }

      if (t >= 174 && c.blast != null && !c.blast.done() && c.level.getGameTime() + 2 >= c.endsAt) {
         c.endsAt += 200L;
      }
   }

   private static Consumption.Replacer scorcher(Techniques.Cast c, int r) {
      BlockState lava = Blocks.LAVA.defaultBlockState();
      BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
      BlockState black = Blocks.BLACKSTONE.defaultBlockState();
      BlockState basalt = Blocks.BASALT.defaultBlockState();
      BlockState fire = Blocks.FIRE.defaultBlockState();
      return (pos, old, d, dx, dy, dz) -> {
         if (d <= (double)r) {
            double floor = -Math.sqrt(Math.max(0.0, (double)r * (double)r - (double)(dx * dx) - (double)(dz * dz)));
            if ((double)dy < floor + 3.0 && (double)dy < (double)(-r) * 0.3) {
               return lava;
            } else if (old.is(Blocks.BEDROCK)) {
               return null;
            } else {
               return old.isAir() ? null : AIR;
            }
         } else if (old.isAir()) {
            if (d < (double)(r + 8) && c.level.random.nextFloat() < 0.15F) {
               BlockState below = c.level.getBlockState(pos.below());
               if (!below.isAir() && below.isFaceSturdy(c.level, pos.below(), Direction.UP)) {
                  return fire;
               }
            }

            return null;
         } else if (!old.getFluidState().isEmpty()) {
            return old.getFluidState().is(FluidTags.WATER) ? AIR : null;
         } else if (old.is(Blocks.BEDROCK)) {
            return null;
         } else if (!old.is(BlockTags.LEAVES) && !old.is(BlockTags.LOGS) && !old.is(BlockTags.REPLACEABLE) && !old.is(BlockTags.FLOWERS)) {
            float f = c.level.random.nextFloat();
            double k = (d - (double)r) / 12.0;
            if ((double)f < 0.55 * (1.0 - k)) {
               return magma;
            } else {
               return (double)f < 0.8 ? black : basalt;
            }
         } else {
            return AIR;
         }
      };
   }

   private static void domain(Techniques.Cast c, int t, long now) {
      if (t == 0) {
         sound(c, c.origin, (SoundEvent)ModSounds.DOMAIN_SIGN.get(), 6.0F, 1.0F);
      }

      if (t == 32) {
         sound(c, c.origin, (SoundEvent)ModSounds.DOMAIN_OPEN.get(), 30.0F, 1.0F);
      }

      if (t >= 52) {
         double grow = Mth.clamp((double)(t - 52) / 40.0, 0.0, 1.0);
         double radius = (double)c.size * (1.0 - Math.pow(1.0 - grow, 2.5));
         if (t % 10 == 0) {
            Vec3 centre = c.point;
            AABB box = new AABB(centre, centre).inflate(radius, radius, radius);

            for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
               if (e.position().distanceTo(centre) <= radius) {
                  hit(c, e, 0.35);
               }
            }
         }

         if ((t - 52) % 60 == 0) {
            sound(c, c.point, (SoundEvent)ModSounds.DOMAIN_SHRED.get(), 20.0F, 1.0F);
         }

         if ((Boolean)ShrineConfig.SERVER.cutBlocks.get()) {
            if (c.leveler == null) {
               c.leveler = new Leveler(c.level, BlockPos.containing(c.point), Math.max(4, (int)c.size), Mth.floor(c.point.y), c.seed);
            }

            c.leveler.allow(radius);
            c.leveler.tick((Integer)ShrineConfig.SERVER.blocksPerTick.get());
         }
      }
   }

   private static int shredRadius(float size) {
      return Math.max(4, Math.min(16, (int)(size / 3.0F)));
   }

   private static void worldSlash(Techniques.Cast c, int t) {
      if (t == 0 || t == 18 || t == 36) {
         sound(c, c.origin, (SoundEvent)ModSounds.WORLD_SIGN.get(), 4.0F, t == 36 ? 0.8F : 1.0F);
      }

      if (t == 78) {
         sound(c, c.origin, (SoundEvent)ModSounds.WORLD_SLASH.get(), 60.0F, 1.0F);
         Vec3 n = new Vec3(-c.dir.z, 0.0, c.dir.x);
         double len = (double)c.size;
         Vec3 a = c.point;
         Vec3 b = c.point.add(c.dir.scale(len));
         AABB box = new AABB(a, b).inflate(4.0, 400.0, 4.0);

         for (Entity e : c.level.getEntities(c.caster, box, Entity::isAlive)) {
            Vec3 rel = e.position().subtract(c.point);
            double along = rel.dot(c.dir);
            if (!(along < 0.0) && !(along > len) && !(Math.abs(rel.dot(n)) > (double)e.getBbWidth() * 0.5 + 2.5)) {
               hit(c, e, 6.0);
            }
         }

         if ((Boolean)ShrineConfig.SERVER.cutBlocks.get()) {
            c.cut = new PlaneCut(c.level, c.point.add(c.dir.scale(2.5)), c.dir, (int)len, 2);
         }
      }

      if (t == 92) {
         sound(c, c.origin, (SoundEvent)ModSounds.WORLD_SPLIT.get(), 60.0F, 1.0F);
      }

      if (c.cut != null) {
         c.cut.tick((Integer)ShrineConfig.SERVER.blocksPerTick.get());
         if (t == 199 && !c.cut.done()) {
            c.endsAt += 600L;
         }
      }
   }

   @SubscribeEvent
   public static void onAttack(LivingAttackEvent event) {
      if (event.getEntity() instanceof ServerPlayer p) {
         Long busy = BUSY.get(p.getUUID());
         if (busy != null && p.level().getGameTime() < busy) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void onLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer sp) {
         sendCooldowns(sp);
      }
   }

   @SubscribeEvent
   public static void onServerStopped(ServerStoppedEvent event) {
      ACTIVE.clear();
      READY.clear();
      BUSY.clear();
   }

   private static final class Cast {
      final Move move;
      final ServerPlayer caster;
      final ServerLevel level;
      final long start;
      final Vec3 origin;
      final Vec3 dir;
      final Vec3 point;
      final long seed;
      final float size;
      final Entity target;
      Consumption blast;
      Leveler leveler;
      PlaneCut cut;
      long endsAt;
      boolean film;

      Cast(Move move, ServerPlayer caster, long start, Vec3 origin, Vec3 dir, Vec3 point, long seed, float size, Entity target) {
         this.move = move;
         this.caster = caster;
         this.level = caster.serverLevel();
         this.start = start;
         this.origin = origin;
         this.dir = dir;
         this.point = point;
         this.seed = seed;
         this.size = size;
         this.target = target;
         this.endsAt = start + (long)Timing.end(move);
      }
   }
}
