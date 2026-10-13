package com.shrine.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class Consumption {
   static final Consumption.Replacer ERASE = (pos, old, d, dx, dy, dz) -> Blocks.AIR.defaultBlockState();
   private Consumption.Replacer replacer = ERASE;
   private Consumption.Sink sink;
   private static final int FLAGS = 50;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private final ServerLevel level;
   private final BlockPos centre;
   private final int maxRadius;
   private final long startTick;
   private final int growTicks;
   private int shell = 0;
   private int dx;
   private int dz;
   private boolean shellStarted;
   private boolean done;

   Consumption(ServerLevel level, Vec3 centre, int radius, long startTick, int growTicks) {
      this.level = level;
      this.centre = BlockPos.containing(centre);
      this.maxRadius = radius;
      this.startTick = startTick;
      this.growTicks = Math.max(1, growTicks);
      this.done = radius <= 0;
   }

   Consumption with(Consumption.Replacer r, Consumption.Sink s) {
      this.replacer = r;
      this.sink = s;
      return this;
   }

   boolean done() {
      return this.done;
   }

   double currentRadius(long now) {
      double t = Math.min(1.0, Math.max(0.0, (double)(now - this.startTick) / (double)this.growTicks));
      double eased = 1.0 - Math.pow(1.0 - t, 2.2);
      return Math.max(2.0, (double)this.maxRadius * eased);
   }

   void tick(long now, int budget) {
      if (!this.done) {
         int target = (int)Math.floor(this.currentRadius(now));
         int changed = 0;
         int scanned = 0;
         int scanBudget = budget * 12;
         int minY = this.level.getMinBuildHeight();
         int maxY = this.level.getMaxBuildHeight() - 1;

         for (MutableBlockPos pos = new MutableBlockPos(); this.shell <= Math.min(target, this.maxRadius); this.shellStarted = false) {
            int k = this.shell;
            if (!this.shellStarted) {
               this.dx = -k;
               this.dz = -k;
               this.shellStarted = true;
            }

            int k2 = k * k;

            for (int inner2 = (k - 1) * (k - 1); this.dx <= k; this.dx++) {
               while (this.dz <= k) {
                  int rxz = this.dx * this.dx + this.dz * this.dz;
                  if (rxz <= k2) {
                     int yMax = (int)Math.floor(Math.sqrt((double)(k2 - rxz)));
                     int yMin = k == 0 ? 0 : (rxz <= inner2 ? (int)Math.floor(Math.sqrt((double)(inner2 - rxz))) + 1 : 0);

                     for (int dy = yMin; dy <= yMax; dy++) {
                        for (int sgn = 0; sgn < (dy == 0 ? 1 : 2); sgn++) {
                           int y = this.centre.getY() + (sgn == 0 ? dy : -dy);
                           scanned++;
                           if (y >= minY && y <= maxY) {
                              pos.set(this.centre.getX() + this.dx, y, this.centre.getZ() + this.dz);
                              if (this.level.isLoaded(pos)) {
                                 BlockState old = this.level.getBlockState(pos);
                                 if (!old.isAir() || this.replacer != ERASE) {
                                    int ddy = y - this.centre.getY();
                                    BlockState next = this.replacer.replace(pos, old, Math.sqrt((double)(rxz + ddy * ddy)), this.dx, ddy, this.dz);
                                    if (next != null && next != old) {
                                       if (this.sink != null && !old.isAir() && next.isAir()) {
                                          this.sink.removed(pos.immutable(), old);
                                       }

                                       this.level.setBlock(pos, next, 50);
                                       changed++;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  this.dz++;
                  if (changed >= budget || scanned >= scanBudget) {
                     return;
                  }
               }

               this.dz = -k;
            }

            this.shell++;
         }

         if (this.shell > this.maxRadius) {
            this.done = true;
         }
      }
   }

   interface Replacer {
      BlockState replace(BlockPos var1, BlockState var2, double var3, int var5, int var6, int var7);
   }

   interface Sink {
      void removed(BlockPos var1, BlockState var2);
   }
}
