package com.mahoraga.mod.client;

import com.avatar.mod.client.AShaders;
import com.avatar.mod.client.Gfx;
import com.jjk.mod.client.JjkPrefs;
import com.mahoraga.mod.MahoragaEntity;
import com.mahoraga.mod.Net;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.shrine.client.cinematic.Cutscene;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderGuiEvent.Post;
import net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.minecraftforge.client.event.ViewportEvent.ComputeFov;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

@EventBusSubscriber(
   modid = "jjk",
   value = {Dist.CLIENT}
)
public final class MahoFx {
   static final float WHITE = 0.81F;
   static final float FIRE = 0.0F;
   static final float ICE = 0.94F;
   private static final Map<Integer, Integer> STEP = new HashMap<>();
   private static final List<double[]> FLASHES = new ArrayList<>();
   private static final List<double[]> BEAMS = new ArrayList<>();
   private static final List<MahoFx.Chunk> CHUNKS = new ArrayList<>();
   private static final Map<Integer, long[]> GLOW = new HashMap<>();
   static final Map<Integer, Vec3> WHEELS = new HashMap<>();
   static float shake;
   private static long summonAt = -1000L;
   private static String card;
   private static long cardAt = -1000L;
   private static long warnAt = -1000L;
   private static long warnLife;
   private static long domainTextAt = -1000L;
   private static final List<double[]> RIPPLES = new ArrayList<>();
   private static MahoFx.Cut cut;
   static MahoFx.CamEntity cam;
   static CameraType prevType;
   private static Matrix4f lastView = new Matrix4f();
   private static Matrix4f lastProj = new Matrix4f();
   static final float DIM = 0.1F;
   private static final List<double[]> DEBRIS = new ArrayList<>();
   private static final List<BlockState> DEBRIS_STATE = new ArrayList<>();

   private MahoFx() {
   }

   public static boolean cutsceneActive() {
      return cut != null || RitualFx.active();
   }

   static long now() {
      Minecraft mc = Minecraft.getInstance();
      return mc.level == null ? 0L : mc.level.getGameTime();
   }

   static void shakeFrom(Vec3 at, float amount, double range) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         double d = mc.player.position().distanceTo(at);
         shake = Math.max(shake, (float)((double)amount * Mth.clamp(1.0 - d / range, 0.0, 1.0)));
      }
   }

   static float[] screen(Vec3 p) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
      Vector4f v = new Vector4f((float)(p.x - c.x), (float)(p.y - c.y), (float)(p.z - c.z), 1.0F);
      lastView.transform(v);
      lastProj.transform(v);
      return v.w <= 0.01F
         ? new float[]{0.5F, 0.5F}
         : new float[]{Mth.clamp(v.x / v.w * 0.5F + 0.5F, 0.05F, 0.95F), Mth.clamp(v.y / v.w * 0.5F + 0.5F, 0.05F, 0.95F)};
   }

   static float wheelGlow(int id, float partial) {
      long[] g = GLOW.get(id);
      if (g == null) {
         return 0.0F;
      } else {
         float t = (float)(now() - g[0]) + partial;
         return Mth.clamp(t / 3.0F, 0.0F, 1.0F) * Mth.clamp(1.0F - (t - (float)g[1] * 0.4F) / ((float)g[1] * 0.6F), 0.0F, 1.0F);
      }
   }

   private static void flash(Vec3 at, int life, double size, float pal) {
      FLASHES.add(new double[]{at.x, at.y, at.z, (double)now(), (double)life, size, 0.0, 0.1F});
   }

   private static void flashFull(Vec3 at, int life, double size, float pal) {
      FLASHES.add(new double[]{at.x, at.y, at.z, (double)now(), (double)life, size, (double)pal, 1.0});
   }

   public static void receive(Net.Fx f) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         long now = now();
         double dist = mc.player.position().distanceTo(f.at());
         switch (f.kind()) {
            case 0:
               if (dist < 64.0) {
                  summonAt = now;
                  shakeFrom(f.at(), 1.2F, 40.0);
                  flash(f.at().add(0.0, 2.0, 0.0), 30, 6.0, 0.81F);
               }
               break;
            case 1:
            case 2:
               startCut(f.kind(), f.entity(), f.at(), dist < 32.0);
               break;
            case 3:
               if (dist < 40.0) {
                  shake = Math.max(shake, 2.2F);
               }
               break;
            case 4:
               GLOW.put(f.entity(), new long[]{now, 50L});
               flash(f.at(), 14, 1.4, 0.0F);
               shakeFrom(f.at(), 0.6F, 24.0);
               break;
            case 5:
               GLOW.put(f.entity(), new long[]{now, 100L});
               flash(f.at(), 30, 3.0, 0.0F);
               shakeFrom(f.at(), 1.5F, 30.0);
               if (dist < 100.0) {
                  card = "He adapted to " + f.text() + ".";
                  cardAt = now;
               }
               break;
            case 6:
               flash(f.at(), 6, 1.4, 0.81F);
               break;
            case 7:
               boolean heavy = "heavy".equals(f.text());
               shakeFrom(f.at(), heavy ? 3.5F : (f.data() > 0 && f.data() < 8 ? 0.5F : 1.8F), (double)(f.f() * 4.0F));
               flash(f.at().add(0.0, 0.3, 0.0), 10, (double)f.f() * 0.8, 0.81F);
               if (f.data() > 0) {
                  debris(mc, f.at(), f.f(), f.data(), heavy);
               }
               break;
            case 8:
               CHUNKS.removeIf(c -> c.serial == (int)f.f() && c.owner == f.entity());
               CHUNKS.add(new MahoFx.Chunk((int)f.f(), f.entity(), f.at(), f.v(), Block.stateById(f.data()), now));
               break;
            case 9:
               CHUNKS.removeIf(c -> c.serial == (int)f.f() && c.owner == f.entity());
               shakeFrom(f.at(), 1.8F, 20.0);
               flash(f.at(), 8, 1.6, 0.81F);
               debris(mc, f.at().add(0.0, -0.5, 0.0), 3.5F, 34, true);
               break;
            case 10:
               if (f.data() == mc.player.getId()) {
                  startCut(10, f.entity(), f.at(), true);
               }
               break;
            case 11:
               float pal = switch (f.data()) {
                  case 3 -> 0.0F;
                  case 7 -> 0.94F;
                  default -> 0.81F;
               };
               flashFull(f.at(), 12, 2.0, pal);
               if (f.data() == 2 || f.data() == 3 || f.data() == 6 || f.data() == 7) {
                  BEAMS.add(new double[]{f.at().x, f.at().y, f.at().z, f.v().x, f.v().y, f.v().z, (double)now, 10.0, (double)pal});
                  flashFull(f.v(), 10, 1.8, pal);
               }
               break;
            case 12:
               warnAt = -1000L;
               RitualFx.start(f);
               break;
            case 13:
               if (f.entity() == mc.player.getId()) {
                  warnAt = now;
                  warnLife = (long)f.f();
               }
               break;
            case 14:
               MahoAnim.pose(f.entity(), f.data(), (int)f.f());
               break;
            case 15:
               shakeFrom(f.at(), 2.6F, 20.0);
               flash(f.at(), 6, 1.2, 0.81F);
               break;
            case 16:
               shakeFrom(f.at(), 1.3F * f.f(), 14.0);

               for (int i = 0; i < 8; i++) {
                  mc.level
                     .addParticle(
                        ParticleTypes.CLOUD,
                        f.at().x,
                        f.at().y,
                        f.at().z,
                        (Math.random() - 0.5) * 0.35,
                        (Math.random() - 0.3) * 0.2,
                        (Math.random() - 0.5) * 0.35
                     );
               }

               mc.level.addParticle(ParticleTypes.EXPLOSION, f.at().x, f.at().y, f.at().z, 0.0, 0.0, 0.0);
               break;
            case 17:
               shakeFrom(f.at(), 1.2F, 32.0);
               flash(f.at(), 20, 3.0, 0.0F);
               break;
            case 18:
               for (Vec3 at : new Vec3[]{f.at(), f.v()}) {
                  for (int i = 0; i < 40; i++) {
                     mc.level
                        .addParticle(
                           ParticleTypes.LARGE_SMOKE,
                           at.x + (Math.random() - 0.5) * 1.6 * (double)f.f(),
                           at.y + Math.random() * 4.2 * (double)f.f(),
                           at.z + (Math.random() - 0.5) * 1.6 * (double)f.f(),
                           0.0,
                           0.02,
                           0.0
                        );
                  }
               }

               flash(f.v().add(0.0, 2.0, 0.0), 10, 3.0, 0.0F);
               break;
            case 19:
               if (Cutscene.active()) {
                  Cutscene.stop();
               }

               startCut(19, f.entity(), f.at(), dist < 48.0);
               if (cut != null) {
                  cut.other = f.data();
               }
               break;
            case 20:
               if (dist < 100.0) {
                  card = "He has adapted and will use your move against you.";
                  cardAt = now;
               }
               break;
            case 21:
               if (dist < 100.0) {
                  domainTextAt = now;
               }
               break;
            case 22:
               RIPPLES.add(new double[]{f.at().x, f.at().y, f.at().z, (double)now, (double)f.f()});
               shakeFrom(f.at(), 2.6F, 24.0);
               break;
            case 23:
               if (dist < 100.0) {
                  card = "Mahoraga is halfway adapted to " + f.text() + ".";
                  cardAt = now;
               }
         }
      }
   }

   private static void startCut(int kind, int entity, Vec3 at, boolean near) {
      Minecraft mc = Minecraft.getInstance();
      if (near && cut == null && !RitualFx.active() && !JjkPrefs.skipCutscenes()) {
         cut = new MahoFx.Cut(kind, entity, at);
         prevType = mc.options.getCameraType();
         mc.options.setCameraType(CameraType.FIRST_PERSON);
         cam = new MahoFx.CamEntity(mc.level);
      }
   }

   private static void debris(Minecraft mc, Vec3 at, float radius, int count, boolean heavy) {
      long now = now();

      for (int i = 0; i < count; i++) {
         double ang = Math.random() * Math.PI * 2.0;
         double r = Math.sqrt(Math.random()) * (double)radius * 0.7;
         double x = at.x + Math.cos(ang) * r;
         double z = at.z + Math.sin(ang) * r;
         int top = mc.level.getHeight(Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z)) - 1;
         BlockState st = mc.level.getBlockState(new BlockPos(Mth.floor(x), top, Mth.floor(z)));
         if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
            st = Blocks.DIRT.defaultBlockState();
         }

         double out = (heavy ? 0.2 : 0.1) + Math.random() * (heavy ? 0.3 : 0.15);
         double up = (heavy ? 0.5 : 0.35) + Math.random() * (heavy ? 0.5 : 0.3);
         DEBRIS.add(
            new double[]{
               x,
               (double)top + 1.0,
               z,
               Math.cos(ang) * out,
               up,
               Math.sin(ang) * out,
               (double)now,
               40.0 + Math.random() * 30.0,
               0.25 + Math.random() * (heavy ? 0.55 : 0.35),
               Math.random(),
               Math.random(),
               Math.random()
            }
         );
         DEBRIS_STATE.add(st);
      }
   }

   private static void stepDebris(Minecraft mc) {
      long now = now();

      for (int i = DEBRIS.size() - 1; i >= 0; i--) {
         double[] d = DEBRIS.get(i);
         if ((double)now - d[6] > d[7]) {
            DEBRIS.remove(i);
            DEBRIS_STATE.remove(i);
         } else {
            double nx = d[0] + d[3];
            double ny = d[1] + d[4];
            double nz = d[2] + d[5];
            BlockPos bp = BlockPos.containing(nx, ny - d[8] * 0.5, nz);
            if (d[4] < 0.0 && !mc.level.getBlockState(bp).getCollisionShape(mc.level, bp).isEmpty()) {
               d[4] = -d[4] * 0.3;
               d[3] *= 0.55;
               d[5] *= 0.55;
               ny = d[1];
            }

            d[0] = nx;
            d[1] = ny;
            d[2] = nz;
            d[4] -= 0.07;
         }
      }
   }

   static void drawBall(PoseStack ps, MultiBufferSource buf, BlockState st, int light, float spin, float radius) {
      Minecraft mc = Minecraft.getInstance();
      ps.pushPose();
      ps.mulPose(Axis.YP.rotationDegrees(spin));
      ps.mulPose(Axis.XP.rotationDegrees(spin * 0.7F));

      for (int i = -1; i < 14; i++) {
         ps.pushPose();
         if (i >= 0) {
            double y = 1.0 - ((double)i + 0.5) / 14.0 * 2.0;
            double rr = Math.sqrt(1.0 - y * y);
            double th = (double)i * 2.39996;
            ps.translate(Math.cos(th) * rr * (double)radius, y * (double)radius, Math.sin(th) * rr * (double)radius);
            ps.mulPose(Axis.YP.rotationDegrees((float)i * 37.0F));
            ps.mulPose(Axis.ZP.rotationDegrees((float)i * 23.0F));
         }

         float b = radius * (i < 0 ? 1.3F : 0.8F);
         ps.scale(b, b, b);
         ps.translate(-0.5, -0.5, -0.5);
         mc.getBlockRenderer().renderSingleBlock(st, ps, buf, light, OverlayTexture.NO_OVERLAY);
         ps.popPose();
      }

      ps.popPose();
   }

   @SubscribeEvent
   public static void onTick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            FLASHES.clear();
            BEAMS.clear();
            CHUNKS.clear();
            RIPPLES.clear();
            DEBRIS.clear();
            DEBRIS_STATE.clear();
            STEP.clear();
            GLOW.clear();
            WHEELS.clear();
            if (cut != null) {
               endCut();
            }
         } else {
            long now = now();
            FLASHES.removeIf(f -> (double)now - f[3] > f[4]);
            BEAMS.removeIf(b -> (double)now - b[6] > b[7]);
            CHUNKS.removeIf(c -> now - c.born > 110L);
            stepDebris(mc);
            RIPPLES.removeIf(r -> ((double)now - r[3]) * 0.55F * (r[4] / 13.0) > r[4] + 3.0);

            for (double[] r : RIPPLES) {
               double rad = ((double)now - r[3]) * 0.55F * (r[4] / 13.0);

               for (int i = 0; i < 10; i++) {
                  double a = Math.random() * Math.PI * 2.0;
                  mc.level.addParticle(ParticleTypes.CLOUD, r[0] + Math.cos(a) * rad, r[1] + 0.3, r[2] + Math.sin(a) * rad, 0.0, 0.05, 0.0);
               }

               if (mc.player != null) {
                  double d = Math.hypot(mc.player.getX() - r[0], mc.player.getZ() - r[2]);
                  if (Math.abs(d - rad) < 1.2) {
                     shake = Math.max(shake, 1.6F);
                  }
               }
            }

            GLOW.values().removeIf(g -> now - g[0] > g[1] + 5L);
            shake = Math.max(0.0F, shake * 0.84F - 0.01F);

            for (MahoFx.Chunk c : CHUNKS) {
               Vec3 p = c.at((float)(now - c.born));
               if ((now + (long)c.serial) % 2L == 0L) {
                  mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, c.state), p.x, p.y, p.z, 0.0, 0.0, 0.0);
               }
            }

            for (Entity en : mc.level.entitiesForRendering()) {
               if (en instanceof MahoragaEntity) {
                  MahoragaEntity m = (MahoragaEntity)en;
                  if (m.isAlive()) {
                     int step = (int)Math.floor((double)(m.walkAnimation.position() * 0.55F / (float) Math.PI));
                     Integer last = STEP.put(m.getId(), step);
                     if (m.walkAnimation.speed() > 0.05F || m.action() == 13 || m.action() == 14) {
                        float sc = m.scale();
                        double yaw = Math.toRadians((double)m.yBodyRot);
                        double rx = Math.cos(yaw);
                        double rz = Math.sin(yaw);
                        if (now % 4L == 0L) {
                           int side = now / 4L % 2L == 0L ? 1 : -1;
                           mc.level
                              .addParticle(
                                 ParticleTypes.SMOKE,
                                 m.getX() + (double)side * rx * 0.95 * (double)sc,
                                 m.getY() + 3.2 * (double)sc,
                                 m.getZ() + (double)side * rz * 0.95 * (double)sc,
                                 (double)(-side) * rx * 0.01,
                                 0.03,
                                 (double)(-side) * rz * 0.01
                              );
                        }

                        if (now % 3L == 0L) {
                           mc.level
                              .addParticle(
                                 ParticleTypes.WHITE_ASH,
                                 m.getX() + (Math.random() - 0.5) * 2.4 * (double)sc,
                                 m.getY() + Math.random() * 4.0 * (double)sc,
                                 m.getZ() + (Math.random() - 0.5) * 2.4 * (double)sc,
                                 0.0,
                                 0.02,
                                 0.0
                              );
                        }
                     }

                     if (last != null && last != step && m.walkAnimation.speed() > 0.05F) {
                        shakeFrom(m.position(), m.intro() ? 0.9F : 0.6F, 22.0);
                        mc.level.playLocalSound(m.getX(), m.getY(), m.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 2.0F, 0.5F, false);
                        mc.level.playLocalSound(m.getX(), m.getY(), m.getZ(), SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 0.8F, 0.5F, false);
                     }
                  }
               }
            }

            if (cut != null) {
               cut.age++;
               boolean skip = mc.options.keyShift.isDown() && cut.age > 6 && mc.screen == null && cut.kind != 10;
               if (skip || cut.age >= cut.length() || mc.level.getEntity(cut.entity) == null || mc.player == null || !mc.player.isAlive()) {
                  endCut();
               }
            }

            RitualFx.tick(mc);
         }
      }
   }

   static void endCut() {
      Minecraft mc = Minecraft.getInstance();
      cut = null;
      cam = null;
      if (mc.player != null) {
         mc.setCameraEntity(mc.player);
      }

      if (prevType != null) {
         mc.options.setCameraType(prevType);
      }
   }

   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (e.phase == Phase.START && mc.level != null) {
         if (RitualFx.active()) {
            RitualFx.camera(mc, e.renderTickTime);
         } else if (cut != null && cam != null) {
            Entity m = mc.level.getEntity(cut.entity);
            if (m != null) {
               Vec3[] s = cut.shot(m, e.renderTickTime);
               place(mc, s[0], s[1], cut.kind != 19);
            }
         }
      }
   }

   static void place(Minecraft mc, Vec3 eye, Vec3 look) {
      place(mc, eye, look, true);
   }

   static void place(Minecraft mc, Vec3 eye, Vec3 look, boolean clip) {
      if (cam == null) {
         cam = new MahoFx.CamEntity(mc.level);
      }

      BlockHitResult hit = clip ? mc.level.clip(new ClipContext(look, eye, net.minecraft.world.level.ClipContext.Block.VISUAL, Fluid.NONE, mc.player)) : null;
      if (hit != null && hit.getType() != Type.MISS) {
         eye = hit.getLocation().add(look.subtract(eye).normalize().scale(0.4));
      }

      Vec3 d = look.subtract(eye);
      float yaw = (float)(Mth.atan2(d.z, d.x) * 180.0F / (float)Math.PI) - 90.0F;
      float pitch = (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0F / (float)Math.PI));
      cam.setPos(eye.x, eye.y, eye.z);
      cam.xo = cam.xOld = eye.x;
      cam.yo = cam.yOld = eye.y;
      cam.zo = cam.zOld = eye.z;
      cam.setYRot(yaw);
      cam.yRotO = yaw;
      cam.setXRot(pitch);
      cam.xRotO = pitch;
      if (mc.getCameraEntity() != cam) {
         mc.setCameraEntity(cam);
      }
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (e.getStage() == Stage.AFTER_ENTITIES) {
            drawChunks(mc, e.getPoseStack(), e.getCamera().getPosition(), e.getPartialTick());
         } else if (e.getStage() == Stage.AFTER_PARTICLES && AShaders.flame != null) {
            Matrix4f view = e.getPoseStack().last().pose();
            lastView = new Matrix4f(view);
            lastProj = new Matrix4f(e.getProjectionMatrix());
            float partial = e.getPartialTick();
            Vec3 c = e.getCamera().getPosition();
            long now = now();
            Gfx.setCamera(view, mc.gameRenderer.getDepthFar() * 0.9F);
            Gfx.f(AShaders.flame, "Time", (float)(now % 100000L) + partial);
            Gfx.f(AShaders.flame, "Bright", 1.0F);
            Gfx.begin();
            int n = 0;

            for (double[] f : FLASHES) {
               float k = Mth.clamp((float)(((double)now - f[3] + (double)partial) / f[4]), 0.0F, 1.0F);
               glow(c, new Vec3(f[0], f[1], f[2]), (float)f[5] * (0.5F + k), (1.0F - k) * (1.0F - k) * (float)f[7], (float)f[6]);
               n++;
            }

            for (double[] b : BEAMS) {
               float k = Mth.clamp((float)(((double)now - b[6] + (double)partial) / b[7]), 0.0F, 1.0F);
               Vec3 from = new Vec3(b[0], b[1], b[2]);
               Vec3 to = new Vec3(b[3], b[4], b[5]);
               int steps = (int)Math.min(80.0, from.distanceTo(to) * 3.0);

               for (int i = 0; i <= steps; i++) {
                  glow(c, from.lerp(to, (double)i / (double)steps), 0.55F * (1.0F - k * 0.5F), 0.7F * (1.0F - k), (float)b[8]);
               }

               n++;
            }

            for (Entity en : mc.level.entitiesForRendering()) {
               if (en instanceof MahoragaEntity) {
                  MahoragaEntity m = (MahoragaEntity)en;
                  float wg = wheelGlow(m.getId(), partial);
                  Vec3 w = WHEELS.get(m.getId());
                  if (wg > 0.01F && w != null) {
                     glow(c, w, 1.3F + 0.2F * Mth.sin(((float)now + partial) * 0.5F), 0.040000003F * wg, 0.0F);
                     n++;
                  }

                  if (m.action() == 4 && m.clientBlade != null) {
                     float t = m.actionAge(partial);
                     float k = Mth.clamp((t - 9.0F) / 12.0F, 0.0F, 1.0F) * Mth.clamp((38.0F - t) / 6.0F, 0.0F, 1.0F);
                     if (k > 0.01F) {
                        Vec3 tip = m.clientBlade;
                        Vec3 elbow = m.clientHands != null ? m.getPosition(partial).add(0.0, 2.6, 0.0) : tip;

                        for (int i = 0; i <= 5; i++) {
                           glow(c, tip.lerp(elbow, (double)i / 10.0), (0.5F + 0.6F * k) * (1.0F - (float)i * 0.08F), 0.085F * k, 0.0F);
                        }

                        glow(c, tip, 2.5F * k, 0.05F * k, 0.0F);
                        n++;
                     }
                  }
               }
            }

            if (n > 0) {
               Gfx.f(AShaders.flame, "Mode", 4.0F);
               Gfx.flush(AShaders.flame, view, e.getProjectionMatrix(), true);
            } else {
               Gfx.flushEmpty();
            }
         }
      }
   }

   private static void drawChunks(Minecraft mc, PoseStack ps, Vec3 cam, float partial) {
      if (!CHUNKS.isEmpty() || !DEBRIS.isEmpty() || !RIPPLES.isEmpty()) {
         BufferSource buf = mc.renderBuffers().bufferSource();
         long now = now();

         for (double[] r : RIPPLES) {
            double max = r[4];
            double rad = ((double)now - r[3] + (double)partial) * 0.55F * (max / 13.0);
            int reach = (int)Math.ceil(Math.min(rad, max) + 2.5);
            int cx = Mth.floor(r[0]);
            int cz = Mth.floor(r[2]);

            for (int x = cx - reach; x <= cx + reach; x++) {
               for (int z = cz - reach; z <= cz + reach; z++) {
                  double d = Math.hypot((double)x + 0.5 - r[0], (double)z + 0.5 - r[2]);
                  if (!(d > max) && !(d < 1.2) && !(Math.abs(d - rad) > 2.4)) {
                     double hgt = 1.15 * Math.exp(-Math.pow((d - rad) / 0.95, 2.0)) * (1.0 - 0.55 * d / max);
                     if (!(hgt < 0.06)) {
                        int top = mc.level.getHeight(Types.WORLD_SURFACE, x, z) - 1;
                        if (!(Math.abs((double)top - r[1]) > 3.0)) {
                           BlockPos bp = new BlockPos(x, top, z);
                           BlockState st = mc.level.getBlockState(bp);
                           if (!st.isAir() && st.getFluidState().isEmpty() && !st.hasBlockEntity()) {
                              ps.pushPose();
                              ps.translate((double)x + 0.5 - cam.x, (double)top + hgt - cam.y, (double)z + 0.5 - cam.z);
                              float tilt = (float)((rad - d) * 9.0 * Math.min(1.0, hgt));
                              float ax = (float)((double)z + 0.5 - r[2]);
                              float az = (float)(-((double)x + 0.5 - r[0]));
                              float al = Mth.sqrt(ax * ax + az * az) + 1.0E-4F;
                              ps.mulPose(new Quaternionf().rotateAxis(tilt * (float) (Math.PI / 180.0), ax / al, 0.0F, az / al));
                              ps.translate(-0.5, 0.0, -0.5);
                              mc.getBlockRenderer()
                                 .renderSingleBlock(st, ps, buf, LevelRenderer.getLightColor(mc.level, bp.above()), OverlayTexture.NO_OVERLAY);
                              ps.popPose();
                           }
                        }
                     }
                  }
               }
            }
         }

         for (int i = 0; i < DEBRIS.size(); i++) {
            double[] d = DEBRIS.get(i);
            float age = (float)(now - (long)d[6]) + partial;
            float fade = (float)Mth.clamp((d[7] - (double)age) / 10.0, 0.0, 1.0);
            double x = d[0] + d[3] * (double)partial;
            double y = d[1] + d[4] * (double)partial;
            double zx = d[2] + d[5] * (double)partial;
            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, zx - cam.z);
            ps.mulPose(new Quaternionf().rotateAxis(age * 0.35F, (float)d[9] - 0.5F, (float)d[10] - 0.5F + 0.01F, (float)d[11] - 0.5F));
            float b = (float)d[8] * fade;
            ps.scale(b, b, b);
            ps.translate(-0.5, -0.5, -0.5);
            mc.getBlockRenderer()
               .renderSingleBlock(DEBRIS_STATE.get(i), ps, buf, LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, zx)), OverlayTexture.NO_OVERLAY);
            ps.popPose();
         }

         for (MahoFx.Chunk c : CHUNKS) {
            float age = (float)(now - c.born) + partial;
            Vec3 p = c.at(age);
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
            drawBall(ps, buf, c.state, light, age * 19.0F, 0.85F);
            ps.popPose();
         }

         buf.endBatch();
      }
   }

   static void glow(Vec3 cam, Vec3 p, float half, float a, float pal) {
      double d = p.distanceTo(cam);
      float near = (float)Mth.clamp((d - (double)half * 0.6 - 0.25) / 1.5, 0.0, 1.0);
      if (near > 0.01F && a > 0.003F) {
         Gfx.billboard(p.x - cam.x, p.y - cam.y, p.z - cam.z, half, 0.0F, 0.0F, 1.0F, pal, Mth.clamp(a * near, 0.0F, 1.0F));
      }
   }

   @SubscribeEvent
   public static void onAngles(ComputeCameraAngles e) {
      if (shake > 0.001F) {
         float t = (float)((double)System.nanoTime() / 4.0E7 % 10000.0);
         e.setYaw(e.getYaw() + (Mth.sin(t * 2.7F) + Mth.sin(t * 5.9F) * 0.5F) * shake);
         e.setPitch(e.getPitch() + (Mth.cos(t * 3.3F) + Mth.sin(t * 7.1F) * 0.5F) * shake);
         e.setRoll(e.getRoll() + Mth.sin(t * 4.1F) * shake * 0.8F);
      }
   }

   @SubscribeEvent
   public static void onFov(ComputeFov e) {
      if (RitualFx.active()) {
         e.setFOV((double)RitualFx.fov());
      } else if (cut != null) {
         e.setFOV((double)cut.fov);
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void onOverlay(Pre e) {
      if (cut != null || RitualFx.active()) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onNameTag(RenderNameTagEvent e) {
      if (cut != null || RitualFx.active()) {
         e.setResult(Result.DENY);
      }
   }

   @SubscribeEvent
   public static void onHand(RenderHandEvent e) {
      if (cut != null || RitualFx.active()) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onGui(Post e) {
      GuiGraphics g = e.getGuiGraphics();
      Minecraft mc = Minecraft.getInstance();
      int w = g.guiWidth();
      int h = g.guiHeight();
      float partial = e.getPartialTick();
      long now = now();
      if (RitualFx.active()) {
         RitualFx.drawGui(g, mc, partial);
      } else {
         if (cut != null) {
            float t = (float)cut.age + partial;
            float k = Mth.clamp(t / 4.0F, 0.0F, 1.0F) * Mth.clamp(((float)cut.length() - t) / 4.0F, 0.0F, 1.0F);
            int bar = (int)((float)h * 0.12F * k);
            g.fill(0, 0, w, bar, -16777216);
            g.fill(0, h - bar, w, h, -16777216);
         }

         float st = (float)(now - summonAt) + partial;
         if (st >= 0.0F && st < 90.0F) {
            float a = Mth.clamp(st / 8.0F, 0.0F, 1.0F) * Mth.clamp((90.0F - st) / 12.0F, 0.0F, 1.0F);
            int ai = (int)(a * 255.0F) << 24;
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.62F, 0.0F);
            g.pose().scale(1.3F, 1.3F, 1.0F);
            g.drawCenteredString(mc.font, "魔虚羅", 0, 0, ai | 16777215);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.62F + 15.0F, 0.0F);
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawCenteredString(mc.font, "Eight-Handled Sword Divergent Sila Divine General Mahoraga", 0, 0, ai | 14208944);
            g.pose().popPose();
         }

         float ct = (float)(now - cardAt) + partial;
         if (card != null && ct >= 0.0F && ct < 70.0F && cut == null) {
            float a = Mth.clamp(ct / 4.0F, 0.0F, 1.0F) * Mth.clamp((70.0F - ct) / 10.0F, 0.0F, 1.0F);
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.24F, 0.0F);
            g.pose().scale(0.6F, 0.6F, 1.0F);
            g.drawCenteredString(mc.font, card, 0, 0, (int)(a * 255.0F) << 24 | 15784080);
            g.pose().popPose();
         }

         float dt = (float)(now - domainTextAt) + partial;
         if (dt >= 0.0F && dt < 110.0F) {
            float a = Mth.clamp(dt / 6.0F, 0.0F, 1.0F) * Mth.clamp((110.0F - dt) / 15.0F, 0.0F, 1.0F);
            g.drawCenteredString(
               mc.font,
               Component.literal("What… he has adapted to… my SLASHES?").withStyle(ChatFormatting.ITALIC),
               w / 2,
               (int)((float)h * 0.74F),
               (int)(a * 255.0F) << 24 | 16777215
            );
         }

         float wt = (float)(now - warnAt) + partial;
         if (wt >= 0.0F && wt < (float)warnLife) {
            float a = Mth.clamp(wt / 5.0F, 0.0F, 1.0F) * Mth.clamp(((float)warnLife - wt) / 20.0F, 0.0F, 1.0F);
            float pulse = 0.75F + 0.25F * Mth.sin(wt * 0.4F);
            g.fill(0, 0, w, h, (int)(a * 22.0F) << 24 | 3145728);
            g.drawCenteredString(mc.font, "IF YOU SUMMON MAHORAGA, YOU WILL DIE", w / 2, (int)((float)h * 0.3F), (int)(a * pulse * 255.0F) << 24 | 16722474);
            int left = (int)(((float)warnLife - wt) / 20.0F) + 1;
            g.pose().pushPose();
            g.pose().translate((float)w / 2.0F, (float)h * 0.3F + 12.0F, 0.0F);
            g.pose().scale(0.7F, 0.7F, 1.0F);
            g.drawCenteredString(
               mc.font,
               "and the Ten Shadows is sealed for an hour.  Press again to perform the ritual (" + left + "s)",
               0,
               0,
               (int)(a * 220.0F) << 24 | 16756912
            );
            g.pose().popPose();
         }
      }
   }

   static final class CamEntity extends Entity {
      CamEntity(Level level) {
         super(EntityType.MARKER, level);
         this.noPhysics = true;
      }

      protected void defineSynchedData() {
      }

      protected void readAdditionalSaveData(CompoundTag tag) {
      }

      protected void addAdditionalSaveData(CompoundTag tag) {
      }

      public Packet<ClientGamePacketListener> getAddEntityPacket() {
         throw new UnsupportedOperationException("client-only camera");
      }
   }

   private static record Chunk(int serial, int owner, Vec3 start, Vec3 vel, BlockState state, long born) {
      Vec3 at(float age) {
         return this.start.add(this.vel.scale((double)age)).add(0.0, -0.05 * (double)age * (double)(age - 1.0F) / 2.0, 0.0);
      }
   }

   static final class Cut {
      final int kind;
      final int entity;
      int other = -1;
      final Vec3 target;
      int age;
      float fov = 60.0F;

      Cut(int kind, int entity, Vec3 target) {
         this.kind = kind;
         this.entity = entity;
         this.target = target;
      }

      int length() {
         return switch (this.kind) {
            case 1 -> 36;
            case 10 -> 58;
            case 19 -> 200;
            default -> 38;
         };
      }

      Vec3[] shot(Entity m, float partial) {
         float t = (float)this.age + partial;
         Vec3 p = m.getPosition(partial);
         Vec3 fwd = this.target.subtract(p).multiply(1.0, 0.0, 1.0);
         if (this.kind == 10) {
            fwd = Vec3.directionFromRotation(0.0F, m.getYRot());
         }

         fwd = fwd.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : fwd.normalize();
         Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
         MahoragaEntity me = m instanceof MahoragaEntity x ? x : null;
         if (this.kind == 1) {
            if (t < 9.0F) {
               this.fov = 50.0F;
               return new Vec3[]{p.add(fwd.scale(4.0)).add(right.scale(1.5)).add(0.0, 0.4, 0.0), p.add(0.0, 3.5, 0.0)};
            } else if (t < 26.0F) {
               this.fov = 70.0F;
               return new Vec3[]{this.target.add(fwd.scale(5.0)).add(right.scale(4.0)).add(0.0, 1.0, 0.0), p.add(0.0, 3.5, 0.0)};
            } else {
               this.fov = 64.0F;
               return new Vec3[]{this.target.add(right.scale(9.0)).add(fwd.scale(-3.0)).add(0.0, 3.0, 0.0), p.add(0.0, 1.5, 0.0)};
            }
         } else if (this.kind == 19) {
            float sc = me != null ? me.scale() : 1.0F;
            Entity o = m.level().getEntity(this.other);
            Vec3 sp = o != null ? o.getPosition(partial) : this.target;
            Vec3 toS = sp.subtract(p).multiply(1.0, 0.0, 1.0);
            double dist = toS.length();
            Vec3 f2 = dist < 0.001 ? fwd : toS.scale(1.0 / dist);
            Vec3 r2 = new Vec3(-f2.z, 0.0, f2.x);
            Vec3 mid = p.add(sp).scale(0.5);
            float k = Mth.clamp(t / (float)this.length(), 0.0F, 1.0F);
            this.fov = 58.0F;
            return new Vec3[]{
               mid.add(r2.scale(dist * 0.75 + (double)(6.0F * sc))).add(f2.scale(((double)k - 0.5) * 2.0)).add(0.0, 1.6 + 0.6 * (double)sc, 0.0),
               mid.add(0.0, 1.4 * (double)sc, 0.0)
            };
         } else if (this.kind == 10) {
            Vec3 hands = me != null && me.clientHands != null ? me.clientHands : p.add(fwd.scale(1.9)).add(0.0, 2.4, 0.0);
            float push = Mth.clamp((t - 30.0F) / 16.0F, 0.0F, 1.0F);
            this.fov = 62.0F - 18.0F * push;
            return new Vec3[]{hands.add(right.scale(5.5 - 2.0 * (double)push)).add(fwd.scale(1.2)).add(0.0, 0.4, 0.0), hands};
         } else if (t < 10.0F) {
            this.fov = 64.0F;
            return new Vec3[]{this.target.add(fwd.scale(2.6)).add(right.scale(1.4)).add(0.0, 2.2, 0.0), p.add(0.0, 2.6, 0.0)};
         } else if (!(t < 24.0F)) {
            this.fov = 66.0F;
            return new Vec3[]{this.target.add(fwd.scale(2.2)).add(right.scale(-1.2)).add(0.0, 2.2, 0.0), p.add(0.0, 2.6, 0.0)};
         } else {
            this.fov = 40.0F;
            Vec3 blade = me != null && me.clientBlade != null ? me.clientBlade : p.add(0.0, 2.0, 0.0);
            Vec3 out = blade.subtract(p).multiply(1.0, 0.0, 1.0);
            out = out.lengthSqr() < 1.0E-4 ? right : out.normalize();
            return new Vec3[]{blade.add(out.scale(3.2)).add(fwd.scale(3.0)).add(0.0, -0.4, 0.0), blade};
         }
      }
   }
}
