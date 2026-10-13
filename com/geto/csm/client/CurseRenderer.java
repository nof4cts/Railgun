package com.geto.csm.client;

import com.geto.csm.entity.CurseEntity;
import com.geto.csm.entity.PyreEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import software.bernie.geckolib.animatable.GeoEntity;

public final class CurseRenderer {
   private static final double MAX_DIST = 160.0;

   private static List<CurseEntity> visible(Vfx.Ctx c) {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel level = mc.level;
      List<CurseEntity> out = new ArrayList<>();
      if (level == null) {
         return out;
      } else {
         Set<Integer> alive = new HashSet<>();

         for (Entity e : level.entitiesForRendering()) {
            if (e instanceof CurseEntity) {
               CurseEntity ce = (CurseEntity)e;
               if (e instanceof GeoEntity) {
                  alive.add(e.getId());
               } else if (ce.isAlive()) {
                  alive.add(e.getId());
                  if (e.position().distanceTo(c.camPos) < 160.0 && !e.isInvisible()) {
                     out.add(ce);
                  }
               }
            }
         }

         if (mc.level.getGameTime() % 100L == 0L) {
            SpineTrail.prune(alive);
         }

         return out;
      }
   }

   private static float brightness(CurseEntity e) {
      Minecraft mc = Minecraft.getInstance();
      int l = mc.level.getMaxLocalRawBrightness(BlockPos.containing(e.getEyePosition()));
      return 0.42F + 0.58F * (float)l / 15.0F;
   }

   public static void solid(Vfx.Ctx c) {
      List<CurseEntity> list = visible(c);
      if (!list.isEmpty()) {
         RenderSystem.enableCull();
         GL11.glCullFace(1028);
         RenderSystem.disableBlend();
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(true);
         BufferBuilder b = Draw.begin(Mode.QUADS);

         for (CurseEntity e : list) {
            pass(c, e, b, 0);
         }

         Draw.end(b);
         GL11.glCullFace(1029);
         RenderSystem.disableCull();
         b = Draw.begin(Mode.QUADS);

         for (CurseEntity e : list) {
            pass(c, e, b, 1);
         }

         Draw.end(b);
         RenderSystem.enableCull();
      }
   }

   private static void pass(Vfx.Ctx c, CurseEntity e, BufferBuilder b, int mode) {
      Vec3 origin = e.getPosition(c.partial);
      c.at(origin);
      Mesh.ink = CurseModels.inkWidth(e);
      float hurt = e.hurtTime > 0 ? (float)e.hurtTime / 10.0F : 0.0F;
      Mesh.begin(mode, b, c.m(), c.camRel(origin), brightness(e), hurt);
      CurseModels.draw(e, origin, c.partial);
      c.pop();
   }

   public static void glow(Vfx.Ctx c) {
      for (CurseEntity e : visible(c)) {
         Vec3 origin = e.getPosition(c.partial);
         c.at(origin);
         CurseModels.glow(e, origin, c.partial, c.vc, c.m(), c.camRel(origin), c);
         c.pop();
      }
   }

   public static void text(Vfx.Ctx c, BufferSource buffers) {
      Font font = Minecraft.getInstance().font;

      for (CurseEntity e : visible(c)) {
         if (e instanceof PyreEntity p) {
            float heat = PyreEntity.heat(p.tickCount);
            Vec3 at = p.getPosition(c.partial).add(0.0, 3.3, 0.0);
            int col = Draw.lerp(-38368, -2864, (heat - 120.0F) / 2880.0F);
            billboard(c, font, buffers, String.format("%,d°C", (int)heat), at, 0.028F, col);
         }
      }

      buffers.endBatch();
   }

   private static void billboard(Vfx.Ctx c, Font font, MultiBufferSource buffers, String s, Vec3 world, float scale, int color) {
      PoseStack ps = c.ps;
      ps.pushPose();
      ps.translate(world.x - c.camPos.x, world.y - c.camPos.y, world.z - c.camPos.z);
      ps.mulPose(c.cam.rotation());
      ps.scale(-scale, -scale, scale);
      font.drawInBatch(s, (float)(-font.width(s)) / 2.0F, -4.0F, color, false, ps.last().pose(), buffers, DisplayMode.NORMAL, 1610612736, 15728880);
      ps.popPose();
   }

   private CurseRenderer() {
   }
}
