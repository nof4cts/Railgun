package com.firestar.silver.client.render;

import com.firestar.silver.client.ClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderPlayerEvent;

/**
 * Afterimages: the fighter drawn again in silver light where he just was. Left behind by every dodge and vanish, and in
 * Zenith trailing him whenever he moves. Also the soft silver glow round an awakened body.
 */
public final class Afterimages {
    private static final Map<Integer, Deque<Vec3>> TRAIL = new HashMap<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static boolean drawing;

    private record Ghost(int id, Vec3 pos, long start, float life) {}

    private Afterimages() {}

    /** Leave a single ghost behind at {@code pos}, fading over {@code seconds}. */
    public static void ghost(int entityId, Vec3 pos, float seconds) {
        GHOSTS.add(new Ghost(entityId, pos, System.nanoTime(), seconds));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAIL.clear();
            GHOSTS.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            Deque<Vec3> d = TRAIL.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            if (ClientState.form(p) < 2) {
                d.clear();
                continue;
            }
            d.addFirst(p.position());
            while (d.size() > 5) {
                d.removeLast();
            }
        }
        long now = System.nanoTime();
        GHOSTS.removeIf(g -> (now - g.start) / 1.0E9F > g.life);
    }

    public static void after(RenderPlayerEvent.Post event) {
        if (drawing || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        PlayerRenderer renderer = event.getRenderer();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource base = event.getMultiBufferSource();
        float partial = event.getPartialTick();
        Vec3 now = player.getPosition(partial);
        float yaw = Mth.lerp(partial, player.yRotO, player.getYRot());
        ClientState.Info info = ClientState.get(player);
        float power = info == null ? 0.0F : Mth.lerp(partial, info.powerO, info.power);
        drawing = true;
        try {
            // the silver glow round an awakened body, breathing with the aura
            if (power > 0.02F) {
                float breathe = 0.8F + 0.2F * (float) Math.sin(System.nanoTime() / 1.0E9 * 5.0);
                float a = 0.13F * power * breathe;
                MultiBufferSource glow = type -> new Tint(base.getBuffer(RenderType.lightning()), 0.72F, 0.88F, 1.0F, a);
                pose.pushPose();
                pose.translate(0, -0.03, 0);
                pose.scale(1.06F, 1.04F, 1.06F);
                renderer.render(player, yaw, partial, pose, glow, 0xF000F0);
                pose.popPose();
            }
            Deque<Vec3> d = TRAIL.get(player.getId());
            if (d != null) {
                int i = 0;
                for (Vec3 past : d) {
                    if (i++ == 0) {
                        continue;
                    }
                    Vec3 off = past.subtract(now);
                    if (off.lengthSqr() < 0.09) {
                        continue;
                    }
                    draw(renderer, player, pose, base, off, yaw, partial, 0.35F * (1.0F - i / 6.0F));
                }
            }
            long t = System.nanoTime();
            Iterator<Ghost> it = GHOSTS.iterator();
            while (it.hasNext()) {
                Ghost g = it.next();
                if (g.id != player.getId()) {
                    continue;
                }
                float k = (t - g.start) / 1.0E9F / g.life;
                draw(renderer, player, pose, base, g.pos.subtract(now), yaw, partial, 0.55F * (1.0F - k));
            }
        } finally {
            drawing = false;
        }
    }

    private static void draw(PlayerRenderer renderer, AbstractClientPlayer player, PoseStack pose, MultiBufferSource base, Vec3 off, float yaw,
                             float partial, float a) {
        if (a <= 0.01F) {
            return;
        }
        MultiBufferSource ghost = type -> new Tint(base.getBuffer(RenderType.lightning()), 0.62F, 0.82F, 1.0F, a);
        pose.pushPose();
        pose.translate(off.x, off.y, off.z);
        renderer.render(player, yaw, partial, pose, ghost, 0xF000F0);
        pose.popPose();
    }

    /** Passes positions through and paints every vertex one glowing colour. */
    record Tint(VertexConsumer to, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            to.color(r * a, g * a, b * a, a);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            to.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
