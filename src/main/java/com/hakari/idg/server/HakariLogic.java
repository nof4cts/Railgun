package com.hakari.idg.server;

import com.hakari.idg.ModRegistry;
import com.hakari.idg.Technique;
import com.hakari.idg.TechniqueScrollItem;
import com.hakari.idg.network.CutType;
import com.hakari.idg.network.FxType;
import com.hakari.idg.network.HakariNetwork;
import com.hakari.idg.network.StatePacket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

public final class HakariLogic {
   public static final float DOMAIN_RADIUS = 20.0F;
   public static final int CAST_TICKS = 180;
   public static final int DOMAIN_DURATION = 1200;
   public static final int RIICHI_TICKS = 72;
   public static final int JACKPOT_CUT_TICKS = 110;
   public static final int JACKPOT_FULL = 5020;
   private static final float[] TIER_CHANCE = new float[]{0.1F, 0.3F, 0.65F, 1.0F};
   private static final RandomSource RNG = RandomSource.create();

   public static void use(ServerPlayer sp, TechniqueScrollItem item) {
      HakariState st = HakariState.get(sp);
      if (!st.locked() && !sp.getCooldowns().isOnCooldown(item)) {
         boolean jp = st.jackpotTicks > 0;
         int cd;
         switch (item.technique) {
            case RESERVE_BALL:
               cd = jp ? Moves.luckyVolley(sp, st) : Moves.reserveBall(sp, st);
               break;
            case SHUTTER_DOORS:
               cd = jp ? Moves.luckyRushdown(sp, st) : Moves.shutterDoors(sp, st);
               break;
            case ROUGH_ENERGY:
               cd = jp ? Moves.overwhelmingLuck(sp, st) : Moves.roughEnergy(sp, st);
               break;
            case FEVER_BREAKER:
               cd = jp ? Moves.energySurge(sp, st) : Moves.feverBreaker(sp, st);
               break;
            case COUNTER:
               cd = jp ? Moves.rhythm(sp, st) : Moves.counter(sp, st);
               break;
            case DOMAIN:
               if (jp || st.domainActive) {
                  sp.displayClientMessage(Component.literal("§6You're already on a roll."), true);
                  return;
               }

               cd = startDomain(sp, st);
               break;
            default:
               cd = 0;
         }

         if (cd > 0) {
            if (st.rhythmTicks > 0 && item.technique != Technique.DOMAIN) {
               cd = Math.max(4, cd * 2 / 5);
            }

            sp.getCooldowns().addCooldown(item, cd);
         }

         sync(sp, st);
      }
   }


   /** Cast ticks left when the barrier has closed round everyone (2.45 s into the cinematic): the sweep starts then. */
   private static final int CLEAR_START = 180 - 49;
   private static final int CLEAR_PER_TICK = 900;

   /**
    * Everything inside the domain from the caster's feet up is taken by it, so no stray terrain pokes through the
    * station, the train or the machine. Ordered centre-outward so it reads as the domain eating the world.
    */
   private static void queueClear(ServerPlayer sp, HakariState st) {
      ServerLevel lvl = sp.serverLevel();
      net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(st.domainCenter);
      int r = (int)Math.ceil(st.radius);
      int floorY = (int)Math.floor(st.domainCenter.y + 0.01);
      List<net.minecraft.core.BlockPos> list = new ArrayList<>();
      double r2 = (st.radius + 0.5) * (st.radius + 0.5);
      for (int x = -r; x <= r; x++) {
         for (int z = -r; z <= r; z++) {
            for (int y = floorY; y <= c.getY() + r; y++) {
               double dy = y + 0.5 - st.domainCenter.y;
               double dx = x + 0.5 - (st.domainCenter.x - c.getX());
               double dz = z + 0.5 - (st.domainCenter.z - c.getZ());
               if (dx * dx + dy * dy + dz * dz <= r2 && y < lvl.getMaxBuildHeight() && y >= lvl.getMinBuildHeight()) {
                  list.add(new net.minecraft.core.BlockPos(c.getX() + x, y, c.getZ() + z));
               }
            }
         }
      }
      Vec3 ctr = st.domainCenter;
      list.sort(java.util.Comparator.comparingDouble(p -> p.distToCenterSqr(ctr.x, ctr.y, ctr.z)));
      st.clearQueue = list;
      st.clearIndex = 0;
   }

   private static void sweep(ServerLevel lvl, HakariState st) {
      int done = 0;
      while (st.clearIndex < st.clearQueue.size() && done < CLEAR_PER_TICK) {
         net.minecraft.core.BlockPos pos = st.clearQueue.get(st.clearIndex++);
         net.minecraft.world.level.block.state.BlockState state = lvl.getBlockState(pos);
         if (state.isAir() && state.getFluidState().isEmpty()) {
            continue;
         }
         if (state.getDestroySpeed(lvl, pos) < 0.0F) {
            continue;
         }
         lvl.removeBlockEntity(pos);
         lvl.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
         done++;
      }
      if (st.clearIndex >= st.clearQueue.size()) {
         st.clearQueue = new ArrayList<>();
         st.clearIndex = 0;
      }
   }

   private static int startDomain(ServerPlayer sp, HakariState st) {
      ServerLevel lvl = sp.serverLevel();
      st.castTicks = 180;
      st.domainCenter = sp.position();
      st.radius = 20.0F;
      st.trapped.clear();
      st.visuals = 0;
      st.fails = 0;
      st.chanceBonus = 0.0F;
      st.riichiTicks = 0;
      st.pose = 1;
      queueClear(sp, st);
      AABB box = sp.getBoundingBox().inflate(20.0);

      for (LivingEntity le : lvl.getEntitiesOfClass(LivingEntity.class, box, ex -> ex != sp && ex.isAlive() && !ex.isSpectator() && ex.distanceTo(sp) <= 20.0F)) {
         st.trapped.add(le.getId());
         Combat.stun(le, 220);
      }

      Combat.stun(sp, 180);
      Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
      HakariNetwork.cutscene(sp, CutType.DOMAIN, sp.getId(), 0, st.boosted ? 1 : 0);

      for (int id : st.trapped) {
         if (lvl.getEntity(id) instanceof ServerPlayer victim) {
            HakariNetwork.cutscene(victim, CutType.DOMAIN, sp.getId(), 1, st.boosted ? 1 : 0);
         }
      }

      return 12000;
   }

   private static void openDomain(ServerPlayer sp, HakariState st) {
      st.domainActive = true;
      st.domainTicks = 1200;
      st.pose = 0;
      Combat.sound(sp, SoundEvents.BELL_BLOCK, 2.0F, 0.6F);
      if (st.boosted) {
         sp.displayClientMessage(Component.literal("§d確変 — Probability Up: odds doubled this domain"), true);
      }
   }

   private static void enforceBarrier(ServerPlayer sp, HakariState st, ServerLevel lvl) {
      List<Entity> inside = new ArrayList<>();
      inside.add(sp);
      Iterator<Integer> it = st.trapped.iterator();

      while (it.hasNext()) {
         Entity e = lvl.getEntity(it.next());
         if (e != null && e.isAlive()) {
            inside.add(e);
         } else {
            it.remove();
         }
      }

      Vec3 c = st.domainCenter;

      for (Entity e : inside) {
         Vec3 off = e.position().subtract(c);
         double d = off.length();
         if (d > (double)st.radius - 1.0) {
            Vec3 dir = d < 0.001 ? new Vec3(1.0, 0.0, 0.0) : off.scale(1.0 / d);
            Vec3 back = c.add(dir.scale((double)st.radius - 1.6));
            e.teleportTo(back.x, Math.max(back.y, c.y - 2.0), back.z);
            e.setDeltaMovement(dir.scale(-0.6));
            e.hurtMarked = true;
         }
      }
   }

   private static void collapse(ServerPlayer sp, HakariState st, boolean burnout) {
      boolean was = st.domainActive || st.castTicks > 0;
      st.domainActive = false;
      st.castTicks = 0;
      st.riichiTicks = 0;
      st.trapped.clear();
      if (was) {
         HakariNetwork.fx(sp, burnout ? FxType.DOMAIN_COLLAPSE : FxType.DOMAIN_SHATTER, sp, (Entity)null, st.domainCenter, st.radius, 0.0F);
         Combat.sound(sp.serverLevel(), st.domainCenter, SoundEvents.GLASS_BREAK, 3.0F, 0.5F);
         if (burnout) {
            st.boosted = false;
            sp.getCooldowns().addCooldown((Item)ModRegistry.DOMAIN.get(), 900);

            for (RegistryObject<Item> ro : ModRegistry.MOVE_SCROLLS) {
               sp.getCooldowns().addCooldown((Item)ro.get(), 100);
            }

            sp.displayClientMessage(Component.literal("§7The domain burns out. Your brain is fried — cursed technique on cooldown."), true);
         }
      }
   }

   public static void onVisual(ServerPlayer sp, HakariState st) {
      if (st.domainActive && st.riichiTicks <= 0) {
         st.visuals++;
         HakariNetwork.fx(sp, FxType.VISUAL_TICK, sp, (Entity)null, sp.position(), (float)st.visuals, 0.0F);
         if (st.visuals >= 2) {
            startRiichi(sp, st);
         }

         sync(sp, st);
      }
   }

   private static void startRiichi(ServerPlayer sp, HakariState st) {
      st.visuals = 0;
      boolean pity = st.fails >= 3;
      float r = RNG.nextFloat();
      int tier = pity ? 3 : (r < 0.5F ? 0 : (r < 0.8F ? 1 : (r < 0.95F ? 2 : 3)));
      float chance = TIER_CHANCE[tier] + st.chanceBonus;
      if (st.boosted) {
         chance *= 2.0F;
      }

      boolean win = pity || RNG.nextFloat() < chance;
      int num = 1 + RNG.nextInt(7);
      int fin = win ? num : 1 + (num - 1 + 1 + RNG.nextInt(6)) % 7;
      st.riichiTier = tier;
      st.riichiScenario = RNG.nextInt(3);
      st.riichiNumber = num;
      st.riichiFinal = fin;
      st.riichiWin = win;
      st.riichiPity = pity;
      st.riichiTicks = 72;
      int[] args = new int[]{tier, st.riichiScenario, num, fin, win ? 1 : 0, pity ? 1 : 0};
      HakariNetwork.cutscene(sp, CutType.RIICHI, sp.getId(), args);

      for (int id : st.trapped) {
         if (sp.serverLevel().getEntity(id) instanceof ServerPlayer p) {
            HakariNetwork.cutscene(p, CutType.RIICHI, sp.getId(), args);
         }
      }

      Combat.sound(sp, (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F, 1.2F);
   }

   private static void resolveRiichi(ServerPlayer sp, HakariState st) {
      if (st.riichiWin) {
         startJackpot(sp, st, st.riichiNumber, st.riichiPity);
      } else {
         st.fails++;
         st.chanceBonus += 0.05F;
         String pity = st.fails >= 3 ? "  §d§lPITY READY" : "";
         sp.displayClientMessage(Component.literal("§7ハズレ — miss.  §fMisses: " + st.fails + "/3" + pity), true);
      }
   }

   private static void startJackpot(ServerPlayer sp, HakariState st, int number, boolean pity) {
      collapse(sp, st, false);
      st.boosted = number % 2 == 1;
      st.jackpotNumber = number;
      st.jackpotMax = pity ? 2510 : 5020;
      st.jackpotCutTicks = 110;
      st.fails = 0;
      st.chanceBonus = 0.0F;
      st.pose = 0;
      sp.setHealth(sp.getMaxHealth());
      clearHarmful(sp);
      Combat.sound(sp, SoundEvents.TOTEM_USE, 1.5F, 1.0F);
      Combat.sound(sp, SoundEvents.PLAYER_LEVELUP, 1.5F, 0.7F);

      for (ServerPlayer p : sp.serverLevel().players()) {
         if (p.distanceToSqr(sp) < 2304.0) {
            HakariNetwork.cutscene(p, CutType.JACKPOT, sp.getId(), number, pity ? 1 : 0, p == sp ? 1 : 0);
         }
      }

      for (RegistryObject<Item> ro : ModRegistry.MOVE_SCROLLS) {
         sp.getCooldowns().removeCooldown((Item)ro.get());
      }

      sp.getCooldowns().removeCooldown((Item)ModRegistry.DOMAIN.get());
      sp.getCooldowns().addCooldown((Item)ModRegistry.DOMAIN.get(), st.jackpotMax + 110);
   }

   private static void jackpotTick(ServerPlayer sp, HakariState st) {
      sp.heal(1.0F);
      sp.clearFire();
      sp.fallDistance = 0.0F;
      sp.getFoodData().setFoodLevel(20);
      if (sp.tickCount % 20 == 0) {
         clearHarmful(sp);
      }
   }

   private static void endJackpot(ServerPlayer sp, HakariState st) {
      HakariNetwork.fx(sp, FxType.JACKPOT_END, sp, (Entity)null, sp.position(), 0.0F, 0.0F);
      Combat.sound(sp, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.8F);
      sp.getCooldowns().removeCooldown((Item)ModRegistry.DOMAIN.get());
      sp.displayClientMessage(Component.literal("§6Jackpot over — the domain is ready. Spin again."), true);
   }

   private static void clearHarmful(ServerPlayer sp) {
      List<MobEffectInstance> bad = new ArrayList<>();

      for (MobEffectInstance inst : sp.getActiveEffects()) {
         if (!inst.getEffect().isBeneficial()) {
            bad.add(inst);
         }
      }

      for (MobEffectInstance instx : bad) {
         sp.removeEffect(instx.getEffect());
      }
   }

   public static void tick(ServerPlayer sp) {
      HakariState st = HakariState.peek(sp);
      if (st != null) {
         boolean dirty = false;
         ServerLevel lvl = sp.serverLevel();
         if (st.castTicks > 0) {
            st.castTicks--;
            if (st.castTicks == 0) {
               openDomain(sp, st);
               dirty = true;
            }
         }

         if ((st.castTicks > 0 && st.castTicks <= CLEAR_START) || st.domainActive) {
            sweep(lvl, st);
         }

         if (st.domainActive) {
            st.domainTicks--;
            enforceBarrier(sp, st, lvl);
            if (st.riichiTicks > 0) {
               st.riichiTicks--;
               if (st.riichiTicks == 0) {
                  resolveRiichi(sp, st);
                  dirty = true;
               }
            } else if (st.domainTicks <= 0) {
               collapse(sp, st, true);
               dirty = true;
            }
         }

         if (st.jackpotCutTicks > 0) {
            st.jackpotCutTicks--;
            sp.setHealth(sp.getMaxHealth());
            if (st.jackpotCutTicks == 0) {
               st.jackpotTicks = st.jackpotMax;
               dirty = true;
            }
         }

         if (st.jackpotTicks > 0) {
            st.jackpotTicks--;
            jackpotTick(sp, st);
            if (st.jackpotTicks == 0) {
               endJackpot(sp, st);
               dirty = true;
            }
         }

         if (st.counterTicks > 0) {
            st.counterTicks--;
            if (st.counterTicks == 0 && st.pose == 3) {
               st.pose = 0;
               dirty = true;
            }
         }

         if (st.rhythmTicks > 0) {
            st.rhythmTicks--;
         }

         if (st.windupTicks > 0) {
            st.windupTicks--;
            if (st.windupTicks == 0 && st.pose == 2) {
               st.pose = 0;
               dirty = true;
            }
         }

         boolean live = st.domainActive || st.jackpotTicks > 0 || st.castTicks > 0 || st.jackpotCutTicks > 0;
         if (dirty || live && sp.tickCount % 10 == 0) {
            sync(sp, st);
         }
      }
   }

   public static void onLogout(ServerPlayer sp) {
      HakariState st = HakariState.peek(sp);
      if (st != null) {
         collapse(sp, st, false);
      }

      HakariState.remove(sp);
   }

   public static void sync(ServerPlayer sp, HakariState st) {
      StatePacket p = new StatePacket();
      p.entityId = sp.getId();
      p.castTicks = st.castTicks;
      p.domainActive = st.domainActive;
      p.domainTicks = st.domainTicks;
      p.cx = st.domainCenter.x;
      p.cy = st.domainCenter.y;
      p.cz = st.domainCenter.z;
      p.radius = st.radius;
      p.visuals = st.visuals;
      p.fails = st.fails;
      p.riichiTicks = st.riichiTicks;
      p.jackpotCutTicks = st.jackpotCutTicks;
      p.jackpotTicks = st.jackpotTicks;
      p.jackpotMax = st.jackpotMax;
      p.jackpotNumber = st.jackpotNumber;
      p.rhythmTicks = st.rhythmTicks;
      p.counterTicks = st.counterTicks;
      p.pose = st.pose;
      p.boosted = st.boosted;
      HakariNetwork.state(sp, p);
   }

   private HakariLogic() {
   }
}
