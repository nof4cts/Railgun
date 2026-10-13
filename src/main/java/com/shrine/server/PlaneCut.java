package com.shrine.server;

import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class PlaneCut {
   private static final int FLAGS = 50;
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private final ServerLevel level;
   private final Vec3 start;
   private final Vec3 dir;
   private final Vec3 across;
   private final int length;
   private final int thickness;
   private int step;
   private int col;
   private int y = Integer.MIN_VALUE;
   private boolean done;

   PlaneCut(ServerLevel level, Vec3 start, Vec3 dir, int length, int thickness) {
      this.level = level;
      this.start = start;
      this.dir = new Vec3(dir.x, 0.0, dir.z).normalize();
      this.across = new Vec3(-this.dir.z, 0.0, this.dir.x);
      this.length = length;
      this.thickness = Math.max(1, thickness);
   }

   boolean done() {
      return this.done;
   }

   int reach() {
      return this.step;
   }

   void tick(int budget) {
      if (!this.done) {
         int changed = 0;
         int scanned = 0;
         int minY = this.level.getMinBuildHeight();
         int top = this.level.getMaxBuildHeight() - 1;
         MutableBlockPos pos = new MutableBlockPos();

         while (changed < budget && scanned < budget * 6) {
            if (this.step > this.length) {
               this.done = true;
               return;
            }

            if (this.col >= this.thickness * 2) {
               this.step++;
               this.col = 0;
            } else {
               double a = (double)this.step + (double)(this.col % 2) * 0.5;
               double c = (double)(this.col / 2) - (double)(this.thickness - 1) * 0.5;
               Vec3 p = this.start.add(this.dir.scale(a)).add(this.across.scale(c));
               int x = Mth.floor(p.x);
               int z = Mth.floor(p.z);
               pos.set(x, 0, z);
               if (!this.level.isLoaded(pos)) {
                  this.col++;
                  this.y = Integer.MIN_VALUE;
               } else {
                  if (this.y == Integer.MIN_VALUE) {
                     this.y = top;
                  }

                  while (true) {
                     if (this.y >= minY && changed < budget) {
                        pos.set(x, this.y, z);
                        scanned++;
                        if (!this.level.getBlockState(pos).isAir()) {
                           this.level.setBlock(pos, AIR, 50);
                           changed++;
                        }

                        this.y--;
                        if (scanned < budget * 6) {
                           continue;
                        }
                     }

                     if (this.y < minY) {
                        this.col++;
                        this.y = Integer.MIN_VALUE;
                     }
                     break;
                  }
               }
            }
         }
      }
   }
}
