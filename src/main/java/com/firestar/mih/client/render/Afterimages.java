package com.firestar.mih.client.render;

import com.firestar.mih.client.ClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
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
 * Double Acceleration and accelerated time leave him behind himself: the player model drawn again at where he was a
 * few ticks ago, in fading clock-green light, like frames of a film run too fast.
 */
public final class Afterimages {
    private static final Map<Integer, Deque<Vec3>> TRAIL = new HashMap<>();
    private static boolean drawing;

    private Afterimages() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAIL.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            ClientState.Info i = ClientState.get(p);
            boolean fast = i != null && (i.daUntil > now || ClientState.accelerating(i, now));
            Deque<Vec3> d = TRAIL.computeIfAbsent(p.getId(), k -> new ArrayDeque<>());
            if (!fast) {
                d.clear();
                continue;
            }
            d.addFirst(p.position());
            while (d.size() > 6) {
                d.removeLast();
            }
        }
    }

    public static void after(RenderPlayerEvent.Post event) {
        if (drawing || !(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        Deque<Vec3> d = TRAIL.get(player.getId());
        if (d == null || d.size() < 2) {
            return;
        }
        PlayerRenderer renderer = event.getRenderer();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource base = event.getMultiBufferSource();
        Vec3 now = player.getPosition(event.getPartialTick());
        drawing = true;
        try {
            int i = 0;
            for (Vec3 past : d) {
                i++;
                if (i == 1) {
                    continue;
                }
                Vec3 off = past.subtract(now);
                if (off.lengthSqr() < 0.04) {
                    continue;
                }
                float a = 0.42F * (1.0F - i / 7.0F);
                MultiBufferSource ghost = type -> new Tint(base.getBuffer(RenderType.lightning()), 0.45F, 1.0F, 0.82F, a);
                pose.pushPose();
                pose.translate(off.x, off.y, off.z);
                renderer.render(player, Mth.lerp(event.getPartialTick(), player.yRotO, player.getYRot()), event.getPartialTick(), pose, ghost, 0xF000F0);
                pose.popPose();
            }
        } finally {
            drawing = false;
        }
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
