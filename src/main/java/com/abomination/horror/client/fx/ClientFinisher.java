package com.abomination.horror.client.fx;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.config.AbomConfig;
import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.network.FinisherPacket;
import com.abomination.horror.client.render.MeshLibrary;
import com.abomination.horror.registry.ModParticles;
import com.abomination.horror.registry.ModSounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.abomination.horror.finisher.FinisherManager.*;

/**
 * Client half of the finisher: everything the victim and every onlooker sees and hears.
 * <ul>
 *   <li>The victim's own head (rendered with their real skin) is torn off their shoulders and lifted overhead.</li>
 *   <li>A 13-vertebra spine is drawn out of the neck stump one segment at a time, then swings freely under the
 *       head as a verlet chain, dripping.</li>
 *   <li>Arterial blood fountains from the stump in pulses, sprays off the spine as it is pulled, and pools.</li>
 *   <li>For the victim: camera takeover (face close-up, impact freeze, third-person rip shot), letterboxing,
 *       flashes, chromatic fringing, cut to black.</li>
 * </ul>
 */
public final class ClientFinisher {
    private static final ResourceLocation ATLAS = AbominationMod.id("textures/entity/abomination.png");
    private static final int SEGMENTS = 13;
    private static final double SPACING = 0.13D;
    private static final double NECK = 1.45D;

    private static final class Active {
        final int creatureId;
        final int victimId;
        final long start;
        final boolean local;
        ResourceLocation skin = DefaultPlayerSkin.getDefaultSkin();
        float yaw;
        float scale = 1.0F;
        float victimBodyYaw;
        Vec3 creaturePos = Vec3.ZERO;
        Vec3 neck = Vec3.ZERO;
        final Vec3[] pos = new Vec3[SEGMENTS + 1];
        final Vec3[] prev = new Vec3[SEGMENTS + 1];
        boolean chainInit;
        boolean detached;
        int extracted;
        int lastEvent = -1;

        Active(int creatureId, int victimId, long start, boolean local) {
            this.creatureId = creatureId;
            this.victimId = victimId;
            this.start = start;
            this.local = local;
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static Active local;
    private static CameraType savedCamera;
    private static boolean cameraTaken;

    private static ModelPart headPart, hatPart;

    private ClientFinisher() {}

    // =============================================================================== lifecycle
    public static void onPacket(FinisherPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.action == FinisherPacket.ABORT) {
            Active a = ACTIVE.remove(p.victimId);
            if (a != null && a == local) releaseCamera();
            return;
        }
        boolean isLocal = mc.player != null && mc.player.getId() == p.victimId;
        Active a = new Active(p.creatureId, p.victimId, p.startTime, isLocal);
        Entity v = mc.level.getEntity(p.victimId);
        Entity c = mc.level.getEntity(p.creatureId);
        if (v instanceof AbstractClientPlayer acp) a.skin = acp.getSkinTextureLocation();
        if (c != null && v != null) {
            a.yaw = (float) (Mth.atan2(v.getZ() - c.getZ(), v.getX() - c.getX()) * (180.0F / (float) Math.PI)) - 90.0F;
        } else if (c != null) {
            a.yaw = c.getYRot();
        }
        if (c instanceof AbominationEntity ab && ab.isAwakened()) a.scale = 1.18F;
        a.creaturePos = c != null ? c.position() : v != null ? v.position() : Vec3.ZERO;
        a.neck = v != null ? v.position().add(0, NECK, 0) : a.creaturePos.add(AbominationEntity.forward(a.yaw).scale(1.35D)).add(0, NECK, 0);
        a.victimBodyYaw = a.yaw + 180.0F;
        ACTIVE.put(p.victimId, a);
        if (isLocal) {
            local = a;
            if (AbomConfig.CLIENT.cinematicCamera.get()) {
                savedCamera = mc.options.getCameraType();
                cameraTaken = true;
            }
        }
    }

    public static void releaseCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (cameraTaken) {
            mc.options.setCameraType(savedCamera != null ? savedCamera : CameraType.FIRST_PERSON);
            cameraTaken = false;
        }
        local = null;
    }

    public static void reset() {
        releaseCamera();
        ACTIVE.clear();
    }

    public static boolean localActive() {
        return local != null;
    }

    /** True for the victim's body (not a respawned player reusing the same entity id) once the head is gone. */
    public static boolean isHeadless(Entity entity) {
        Active a = ACTIVE.get(entity.getId());
        Minecraft mc = Minecraft.getInstance();
        return a != null && mc.level != null && mc.level.getGameTime() - a.start >= RIP_START && isSameBody(a, entity);
    }

    private static boolean isSameBody(Active a, Entity e) {
        return e.position().add(0, NECK, 0).distanceToSqr(a.neck) < 16.0D;
    }

    /** The local player respawned: drop their own cinematic state. */
    public static void onLocalRespawn() {
        Minecraft mc = Minecraft.getInstance();
        releaseCamera();
        if (mc.player != null) ACTIVE.remove(mc.player.getId());
    }

    private static float time(Active a, float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0F : (mc.level.getGameTime() - a.start) + partial;
    }

    // =============================================================================== tick
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!ACTIVE.isEmpty()) reset();
            return;
        }
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        Iterator<Active> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Active a = it.next();
            int t = (int) (now - a.start);
            Entity c = mc.level.getEntity(a.creatureId);
            Entity v = mc.level.getEntity(a.victimId);
            if (c != null && c.isAlive()) a.creaturePos = c.position();
            if (v instanceof LivingEntity lv && lv.deathTime == 0 && !v.isRemoved() && isSameBody(a, v)) {
                a.neck = v.position().add(0, NECK, 0);
                a.victimBodyYaw = lv.yBodyRot;
            }
            for (int k = a.lastEvent + 1; k <= t; k++) events(mc, a, k);
            a.lastEvent = Math.max(a.lastEvent, t);
            if (t >= RIP_START) stepChain(a, t);
            blood(mc, a, t);
            if (t >= CREATURE_RELEASE_TICK || t < -60) {
                if (a == local) releaseCamera();
                it.remove();
            }
        }
        if (local != null) {
            int t = (int) (now - local.start);
            if (t > KILL_TICK + 40) releaseCamera();
            else if (cameraTaken) driveCamera(mc, local, t);
        }
    }

    private static void driveCamera(Minecraft mc, Active a, int t) {
        LocalPlayer p = mc.player;
        if (p == null || t >= KILL_TICK) return;
        Vec3 f = AbominationEntity.forward(a.yaw);
        if (t < RIP_START) {
            // dragged into its shadow, forced to look up into its face
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            Vec3 face = a.creaturePos.add(f.scale(0.9D * a.scale)).add(0, 3.25D * a.scale, 0);
            Vec3 d = face.subtract(p.getEyePosition());
            float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0F / (float) Math.PI)) - 90.0F;
            float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180.0F / (float) Math.PI));
            setRot(p, yaw, pitch);
        } else {
            // pull back: watch your own head being torn off, from behind your own shoulders
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            float orbit = 25.0F + (t - RIP_START) * 0.5F;
            setRot(p, a.yaw + 180.0F + orbit, -22.0F);
        }
    }

    private static void setRot(LocalPlayer p, float yaw, float pitch) {
        p.yRotO = p.getYRot();
        p.xRotO = p.getXRot();
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yHeadRotO = p.yHeadRot;
        p.yHeadRot = yaw;
    }

    // =============================================================================== spine physics
    static Vec3 headBase(Active a, float tf) {
        Vec3 f = AbominationEntity.forward(a.yaw);
        Vec3 left = new Vec3(f.z, 0, -f.x);
        Vec3 start = a.neck;
        Vec3 end = a.creaturePos.add(f.scale(1.2D * a.scale)).add(left.scale(0.45D * a.scale)).add(0, 4.3D * a.scale, 0);
        float u = (tf - RIP_START) / (float) (RIP_END - RIP_START);
        Vec3 p;
        if (u <= 0.0F) {
            p = start;
        } else if (u < 0.18F) {
            // tension: the skull strains against the neck before it gives
            float s = u / 0.18F;
            p = start.add(Mth.sin(tf * 3.7F) * 0.03D * s, 0.08D * s, Mth.cos(tf * 4.1F) * 0.03D * s);
        } else if (u < 1.0F) {
            double e = 1.0D - Math.pow(1.0D - (u - 0.18F) / 0.82F, 3.0D);
            Vec3 base = start.add(0, 0.08D, 0);
            Vec3 ctrl = start.add(0, 1.7D, 0).add(f.scale(-0.1D));
            p = base.scale((1 - e) * (1 - e)).add(ctrl.scale(2 * (1 - e) * e)).add(end.scale(e * e));
        } else {
            p = end.add(0, Mth.sin(tf * 0.12F) * 0.04D, 0).add(left.scale(Mth.sin(tf * 0.15F) * 0.06D));
        }
        float lower = Mth.clamp((tf - 140.0F) / 10.0F, 0.0F, 1.0F);
        return p.add(0, -1.5D * lower, 0);
    }

    private static void stepChain(Active a, int t) {
        Vec3 anchor = headBase(a, t).add(0, -0.18D, 0);
        Vec3 stumpP = a.neck.add(0, -0.05D, 0);
        if (!a.chainInit) {
            for (int i = 0; i <= SEGMENTS; i++) {
                a.pos[i] = stumpP;
                a.prev[i] = stumpP;
            }
            a.chainInit = true;
        }
        int out = Math.min(SEGMENTS, (int) (anchor.distanceTo(stumpP) / SPACING) + 1);
        if (!a.detached && out >= SEGMENTS && t > RIP_START + 4) a.detached = true; // last vertebra rips free
        a.extracted = a.detached ? SEGMENTS : out;

        for (int i = 1; i <= SEGMENTS; i++) {
            if (!a.detached && i > a.extracted) {
                a.prev[i] = a.pos[i];
                a.pos[i] = stumpP;
                continue;
            }
            Vec3 vel = a.pos[i].subtract(a.prev[i]).scale(0.9D);
            a.prev[i] = a.pos[i];
            a.pos[i] = a.pos[i].add(vel).add(0, -0.045D, 0);
        }
        a.prev[0] = a.pos[0];
        a.pos[0] = anchor;

        for (int iter = 0; iter < 8; iter++) {
            for (int i = 0; i < SEGMENTS; i++) {
                Vec3 d = a.pos[i + 1].subtract(a.pos[i]);
                double len = d.length();
                if (len < 1.0E-6D) continue;
                double diff = (len - SPACING) / len;
                boolean pinA = i == 0;
                boolean pinB = !a.detached && i + 1 > a.extracted;
                if (pinA && pinB) continue;
                if (pinA) a.pos[i + 1] = a.pos[i + 1].subtract(d.scale(diff));
                else if (pinB) a.pos[i] = a.pos[i].add(d.scale(diff));
                else {
                    a.pos[i] = a.pos[i].add(d.scale(diff * 0.5D));
                    a.pos[i + 1] = a.pos[i + 1].subtract(d.scale(diff * 0.5D));
                }
            }
        }
    }

    // =============================================================================== blood
    private static void blood(Minecraft mc, Active a, int t) {
        int level = AbomConfig.CLIENT.goreLevel.get();
        if (level == 0 || mc.level == null) return;
        float m = level == 1 ? 0.4F : level == 2 ? 1.0F : 2.2F;
        RandomSource r = mc.level.random;
        Vec3 n = a.neck;

        if (t == IMPACT_TICK) {
            for (int i = 0; i < (int) (40 * m); i++) {
                Vec3 dir = new Vec3(r.nextDouble() - 0.5D, r.nextDouble() * 0.6D, r.nextDouble() - 0.5D).normalize().scale(0.15D + r.nextDouble() * 0.2D);
                mc.level.addParticle(ModParticles.BLOOD.get(), n.x, n.y + 0.2D, n.z, dir.x, dir.y, dir.z);
            }
        }
        if (t >= RIP_START && t < CREATURE_RELEASE_TICK) {
            float decay = t < RIP_END ? 1.0F : Math.max(0.12F, 1.0F - (t - RIP_END) / 60.0F);
            boolean spurt = (t - RIP_START) % 7 < 2; // arterial pulses in time with a dying heartbeat
            int cnt = (int) ((spurt ? 14 : 5) * m * decay);
            for (int i = 0; i < cnt; i++) {
                mc.level.addParticle(ModParticles.BLOOD.get(), n.x + (r.nextDouble() - 0.5D) * 0.15D, n.y + 0.05D, n.z + (r.nextDouble() - 0.5D) * 0.15D,
                        (r.nextDouble() - 0.5D) * 0.12D, (spurt ? 0.45D : 0.22D) + r.nextDouble() * 0.2D, (r.nextDouble() - 0.5D) * 0.12D);
            }
            if (a.chainInit) {
                int vis = a.detached ? SEGMENTS : a.extracted;
                for (int i = 0; i < (int) (3 * m) + 1; i++) {
                    Vec3 p = a.pos[r.nextInt(vis + 1)];
                    mc.level.addParticle(ModParticles.BLOOD.get(), p.x, p.y, p.z, (r.nextDouble() - 0.5D) * 0.05D, -0.02D, (r.nextDouble() - 0.5D) * 0.05D);
                }
                if (t < RIP_END + 4) {
                    // spray flung off the spine as it is wrenched out
                    for (int i = 0; i < (int) (5 * m); i++) {
                        int k = r.nextInt(vis + 1);
                        Vec3 v = a.pos[k].subtract(a.prev[k]).scale(0.6D);
                        mc.level.addParticle(ModParticles.BLOOD.get(), a.pos[k].x, a.pos[k].y, a.pos[k].z,
                                v.x + (r.nextDouble() - 0.5D) * 0.2D, v.y + r.nextDouble() * 0.15D, v.z + (r.nextDouble() - 0.5D) * 0.2D);
                    }
                }
                Vec3 hb = headBase(a, t);
                for (int i = 0; i < (int) (2 * m) + 1; i++) {
                    mc.level.addParticle(ModParticles.BLOOD.get(), hb.x + (r.nextDouble() - 0.5D) * 0.25D, hb.y - 0.15D, hb.z + (r.nextDouble() - 0.5D) * 0.25D, 0, -0.05D, 0);
                }
            }
        }
        if (t == CREATURE_RELEASE_TICK - 1) {
            Vec3 hb = headBase(a, t);
            for (int i = 0; i < (int) (50 * m); i++) {
                mc.level.addParticle(r.nextBoolean() ? ModParticles.BLOOD.get() : ModParticles.ICHOR.get(), hb.x, hb.y, hb.z,
                        (r.nextDouble() - 0.5D) * 0.5D, r.nextDouble() * 0.4D, (r.nextDouble() - 0.5D) * 0.5D);
            }
        }
    }

    // =============================================================================== sound timeline
    private static void events(Minecraft mc, Active a, int k) {
        switch (k) {
            case 0 -> { play(mc, a, ModSounds.GORE.get(), 1.0F, 0.8F); play(mc, a, ModSounds.PAIN.get(), 1.0F, 1.0F); play(mc, a, ModSounds.GRAB.get(), 0.5F, 0.9F); }
            case 6 -> play(mc, a, ModSounds.AMBIENT.get(), 0.6F, 0.6F);
            case 8 -> play(mc, a, ModSounds.PAIN.get(), 1.0F, 1.05F);
            case 10 -> play(mc, a, ModSounds.WHISPER.get(), 0.5F, 0.6F);
            case 14 -> { play(mc, a, ModSounds.HEARTBEAT.get(), 0.8F, 1.0F); play(mc, a, ModSounds.PAIN.get(), 1.0F, 0.95F); }
            case 18 -> play(mc, a, ModSounds.HEARTBEAT.get(), 0.9F, 1.25F);
            case 22 -> {
                if (a.local) { // sudden silence
                    mc.getSoundManager().stop(null, SoundSource.MUSIC);
                    mc.getSoundManager().stop(null, SoundSource.AMBIENT);
                    mc.getSoundManager().stop(null, SoundSource.HOSTILE);
                }
            }
            case IMPACT_TICK -> {
                play(mc, a, ModSounds.GORE.get(), 1.0F, 0.7F);
                play(mc, a, ModSounds.GORE.get(), 1.0F, 1.0F);
                play(mc, a, ModSounds.CRACK.get(), 1.0F, 0.8F);
                play(mc, a, ModSounds.PAIN.get(), 1.0F, 1.1F);
                play(mc, a, ModSounds.IMPACT.get(), 0.35F, 0.8F);
            }
            case RIP_START -> {
                play(mc, a, ModSounds.RIP.get(), 1.0F, 0.9F);
                play(mc, a, ModSounds.RIP.get(), 1.0F, 0.7F);
                play(mc, a, ModSounds.GUSH.get(), 1.0F, 0.9F);
                play(mc, a, ModSounds.PAIN.get(), 1.0F, 1.2F);
            }
            case 56 -> {
                play(mc, a, ModSounds.ROAR.get(), 0.4F, 0.7F);
                play(mc, a, ModSounds.GORE.get(), 1.0F, 0.6F);
                if (a.local) mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.FINISHER_STING.get(), 1.0F, 1.0F));
            }
            case 64, 76 -> play(mc, a, ModSounds.GUSH.get(), 1.0F, 0.7F);
            case 70 -> play(mc, a, ModSounds.GORE.get(), 1.0F, 0.6F);
            case BLACKOUT_TICK -> {
                if (a.local) {
                    mc.getSoundManager().stop(null, SoundSource.HOSTILE);
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.WHISPER.get(), 0.5F, 1.0F));
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SUB_BASS.get(), 0.7F, 1.0F));
                }
            }
            case CREATURE_RELEASE_TICK - 1 -> play(mc, a, ModSounds.GORE.get(), 1.0F, 0.5F);
            default -> { }
        }
        if (k > RIP_START && k < RIP_END && k % 2 == 0) {
            // vertebrae popping free one by one, pitch climbing
            play(mc, a, ModSounds.CRACK.get(), 1.0F, 0.9F + (k - RIP_START) * 0.015F);
        }
        if (k == 34 || k == 38 || k == 42 || k == 46 || k == 50 || k == 54) play(mc, a, ModSounds.GORE.get(), 1.0F, 0.65F + (k - 34) * 0.015F);
        if (k == 40 || k == 50 || k == 60) play(mc, a, ModSounds.GUSH.get(), 1.0F, 0.8F);
        if (k == 38 || k == 48) play(mc, a, ModSounds.PAIN.get(), 1.0F, 1.0F + (k - 38) * 0.03F);
    }

    /**
     * The victim hears every layer directly in their ears on the Master slider (Minecraft caps sound volume at 1.0, so
     * positional playback let the louder synthesized layers bury the gore). Onlookers hear it from the body, carried
     * further than a normal sound.
     */
    private static void play(Minecraft mc, Active a, SoundEvent sound, float vol, float pitch) {
        if (mc.level == null) return;
        if (a.local) {
            ClientJumpscare.ear(mc, sound, vol, pitch);
        } else {
            mc.level.playLocalSound(a.neck.x, a.neck.y, a.neck.z, sound, SoundSource.HOSTILE, Math.max(vol, 1.0F) * 2.0F, pitch, false);
        }
    }

    // =============================================================================== world rendering
    private static void ensureModels() {
        if (headPart != null) return;
        ModelPart player = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot();
        headPart = player.getChild("head");
        hatPart = player.getChild("hat");
    }

    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ensureModels();
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        for (Active a : ACTIVE.values()) {
            float tf = time(a, pt);
            if (tf < RIP_START || !a.chainInit) continue;
            Vec3 hb = headBase(a, tf);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(hb));
            MeshLibrary lib = MeshLibrary.get();
            VertexConsumer gore = buf.getBuffer(RenderType.entityCutoutNoCull(ATLAS));

            // ---- the severed head, wearing the victim's own skin
            float u = Mth.clamp((tf - RIP_START) / (RIP_END - RIP_START), 0.0F, 1.0F);
            float droop = 0.25F + 0.35F * u + Mth.sin(tf * 0.19F) * 0.06F;
            float roll = Mth.sin(tf * 0.21F) * 0.15F + (u < 0.2F ? Mth.sin(tf * 5.0F) * 0.1F : 0.0F);
            ps.pushPose();
            ps.translate(hb.x - cam.x, hb.y - cam.y, hb.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - a.yaw));
            ps.scale(-1.0F, -1.0F, 1.0F);
            headPart.xRot = droop;
            headPart.zRot = roll;
            hatPart.copyFrom(headPart);
            headPart.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(a.skin)), light, OverlayTexture.NO_OVERLAY);
            hatPart.render(ps, buf.getBuffer(RenderType.entityTranslucent(a.skin)), light, OverlayTexture.NO_OVERLAY);
            if (lib != null) {
                // ragged neck meat and dangling strands hanging off the base of the skull
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotation(roll));
                ps.mulPose(Axis.XP.rotation(droop));
                lib.draw("prop_neckgore", ps.last(), gore, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                ps.popPose();
            }
            headPart.xRot = 0.0F;
            headPart.zRot = 0.0F;
            ps.popPose();

            // ---- the spine, vertebra by vertebra
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(ATLAS));
            int n = a.detached ? SEGMENTS : a.extracted;
            for (int i = 0; i < n; i++) {
                Vec3 p0 = a.prev[i].lerp(a.pos[i], pt);
                Vec3 p1 = a.prev[i + 1].lerp(a.pos[i + 1], pt);
                Vec3 mid = p0.add(p1).scale(0.5D);
                Vec3 d = p1.subtract(p0);
                ps.pushPose();
                ps.translate(mid.x - cam.x, mid.y - cam.y, mid.z - cam.z);
                if (d.lengthSqr() > 1.0E-8D) {
                    ps.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) d.x, (float) d.y, (float) d.z));
                }
                float sc = 1.0F - i * 0.025F;
                ps.scale(sc, sc, sc);
                if (lib != null) lib.draw("prop_vertebra", ps.last(), vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                ps.popPose();
            }

            // ---- the open stump on the body while it still stands
            Entity v = mc.level.getEntity(a.victimId);
            if (v instanceof LivingEntity lv && lv.deathTime == 0 && !v.isRemoved() && isSameBody(a, v)) {
                Vec3 np = lv.getPosition(pt).add(0, NECK + 0.05D, 0);
                ps.pushPose();
                ps.translate(np.x - cam.x, np.y - cam.y, np.z - cam.z);
                ps.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(pt, lv.yBodyRotO, lv.yBodyRot)));
                ps.scale(-1.0F, -1.0F, 1.0F);
                if (lib != null) lib.draw("prop_stump", ps.last(), buf.getBuffer(RenderType.entityCutoutNoCull(ATLAS)), light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
                ps.popPose();
            }
        }
        buf.endBatch();
    }

    // =============================================================================== camera / input / HUD
    public static float shakeAmount(float partial) {
        Minecraft mc = Minecraft.getInstance();
        float s = 0.0F;
        for (Active a : ACTIVE.values()) {
            float tf = time(a, partial);
            float amp;
            if (tf < IMPACT_TICK) amp = 0.05F + 0.25F * Mth.clamp((tf - 10.0F) / 14.0F, 0.0F, 1.0F);
            else if (tf < RIP_START) amp = 0.0F;  // impact freeze
            else if (tf < RIP_END) amp = 0.9F;
            else amp = Math.max(0.0F, 0.35F - (tf - RIP_END) / 80.0F);
            if (!a.local) {
                if (mc.player == null) continue;
                double d = mc.player.position().distanceTo(a.neck);
                amp = d < 16.0D && tf >= IMPACT_TICK ? amp * 0.45F * (float) (1.0D - d / 16.0D) : 0.0F;
            }
            s = Math.max(s, amp);
        }
        return s;
    }

    public static void onFov(ViewportEvent.ComputeFov e) {
        if (local == null || !cameraTaken) return;
        float tf = time(local, (float) e.getPartialTick());
        double mult;
        if (tf < IMPACT_TICK) mult = 1.0D - 0.5D * Mth.clamp((tf - 4.0F) / 20.0F, 0.0F, 1.0F);
        else if (tf < RIP_START) mult = 0.4D;
        else mult = 0.85D;
        e.setFOV(e.getFOV() * mult);
    }

    public static void onInput(MovementInputUpdateEvent e) {
        if (local == null) return;
        if (time(local, 0.0F) >= KILL_TICK) return;
        Input in = e.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.up = false;
        in.down = false;
        in.left = false;
        in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
        if (local != null && cameraTaken && !AbominationMod.MODID.equals(e.getOverlay().id().getNamespace())) {
            e.setCanceled(true);
        }
    }

    /** Draws the victim's cinematic overlay. Returns true while the cinematic owns the screen. */
    public static boolean renderOverlay(GuiGraphics g, int w, int h, float partial, boolean fx) {
        if (local == null || !cameraTaken) return false;
        float tf = time(local, partial);
        int bar = (int) (h * 0.11F * Mth.clamp(tf / 10.0F, 0.0F, 1.0F));
        if (fx) {
            float vig = tf < RIP_START ? Mth.clamp(tf / 24.0F, 0.0F, 1.0F) * 0.85F
                    : 0.6F + 0.25F * Math.abs(Mth.sin((tf - RIP_START) * PI_7));
            ClientFx.vignette(g, w, h, 0.35F, 0.0F, 0.0F, vig, 0);
            if (tf >= IMPACT_TICK && tf < IMPACT_TICK + 1.5F) {
                g.fill(0, 0, w, h, 0xF0FFFFFF);
            } else if (tf >= IMPACT_TICK + 1.5F && tf < RIP_START) {
                g.fill(0, 0, w, h, 0xC0000000);
                ClientFx.noise(g, w, h, 0.5F, 1.0F, 0.2F, 0.2F);
            } else if (tf >= RIP_START && tf < BLACKOUT_TICK) {
                ClientFx.chromatic(g, w, h, 0.65F);
                if (((int) tf - RIP_START) % 7 < 1) g.fill(0, 0, w, h, 0x38FF0000);
                ClientFx.glitchBars(g, w, h, 0.5F);
            }
        }
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        if (tf >= BLACKOUT_TICK) g.fill(0, 0, w, h, 0xFF000000);
        return true;
    }

    private static final float PI_7 = (float) Math.PI / 7.0F;
}
