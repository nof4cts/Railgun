package com.ironlung.client.screen;

import com.ironlung.block.ControlType;
import com.ironlung.client.ClientMissionState;
import com.ironlung.client.ClientSounds;
import com.ironlung.client.FeedSystem;
import com.ironlung.client.StaticNoise;
import com.ironlung.mission.CameraMount;
import com.ironlung.mission.Mission;
import com.ironlung.mission.PhotoRecord;
import com.ironlung.network.ModNetwork;
import com.ironlung.network.Packets;
import com.ironlung.ocean.Poi;
import com.ironlung.registry.ModSounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * The only window. A live feed that is grainy, slow and unreliable; photographs that are
 * clearer but take time to develop. A/D pan, W/S tilt, Q/E change mount, SPACE capture, TAB photographs.
 */
public class CameraScreen extends SubScreen {
    private boolean gallery;
    private int galleryIndex = -1;
    private int reviewId = -1;
    private long reviewStart;
    private int heartbeat;
    private int servoCooldown;

    public CameraScreen() {
        super(Component.translatable("screen.ironlung.camera"));
        reviewId = ClientMissionState.lastPhotoId;
    }

    private boolean down(int key) {
        return minecraft != null && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key);
    }

    @Override
    public void tick() {
        super.tick();
        boolean powered = ClientMissionState.powered();
        boolean moved = false;
        if (powered && !gallery) {
            float rate = (float) Math.toRadians(1.6);
            if (down(GLFW.GLFW_KEY_A) || down(GLFW.GLFW_KEY_LEFT)) { ClientMissionState.camPan -= rate; moved = true; }
            if (down(GLFW.GLFW_KEY_D) || down(GLFW.GLFW_KEY_RIGHT)) { ClientMissionState.camPan += rate; moved = true; }
            if (down(GLFW.GLFW_KEY_W) || down(GLFW.GLFW_KEY_UP)) { ClientMissionState.camTilt += rate; moved = true; }
            if (down(GLFW.GLFW_KEY_S) || down(GLFW.GLFW_KEY_DOWN)) { ClientMissionState.camTilt -= rate; moved = true; }
            ClientMissionState.camPan = (float) Math.max(-CameraMount.MAX_PAN, Math.min(CameraMount.MAX_PAN, ClientMissionState.camPan));
            ClientMissionState.camTilt = (float) Math.max(-CameraMount.MAX_TILT, Math.min(CameraMount.MAX_TILT, ClientMissionState.camTilt));
        }
        if (servoCooldown > 0) servoCooldown--;
        if (moved && servoCooldown == 0) {
            ClientSounds.playAt(ControlType.CAMERA, ModSounds.CAMERA_SERVO.get(), 0.35f, 0.9f + (float) Math.random() * 0.2f);
            servoCooldown = 9;
        }
        if (--heartbeat <= 0 || moved) {
            sendCamera(false);
            heartbeat = 10;
        }
        if (ClientMissionState.lastPhotoId != reviewId) {
            reviewId = ClientMissionState.lastPhotoId;
            reviewStart = System.nanoTime();
            ClientSounds.playAt(ControlType.CAMERA, ModSounds.CAMERA_DEVELOP.get(), 0.7f, 1.0f);
        }
    }

    private void sendCamera(boolean capture) {
        ModNetwork.toServer(new Packets.CameraState(ClientMissionState.camMount, ClientMissionState.camPan, ClientMissionState.camTilt, capture));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB) {
            gallery = !gallery;
            if (gallery) galleryIndex = ClientMissionState.photos.size() - 1;
            return true;
        }
        if (gallery) {
            int n = ClientMissionState.photos.size();
            if (n > 0 && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A)) galleryIndex = Math.floorMod(galleryIndex - 1, n);
            if (n > 0 && (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D)) galleryIndex = Math.floorMod(galleryIndex + 1, n);
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            sendCamera(true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_Q || key == GLFW.GLFW_KEY_E) {
            if (!ClientMissionState.powered()) return true;
            int n = CameraMount.values().length;
            ClientMissionState.camMount = Math.floorMod(ClientMissionState.camMount + (key == GLFW.GLFW_KEY_E ? 1 : -1), n);
            ClientMissionState.camPan = 0;
            ClientMissionState.camTilt = 0;
            ClientSounds.playAt(ControlType.CAMERA, ModSounds.CAMERA_SERVO.get(), 0.6f, 0.6f);
            ClientMissionState.glitchTicks = Math.max(ClientMissionState.glitchTicks, 8);
            sendCamera(false);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float pt) {
        int fh = (int) Math.min(height * 0.66, (width * 0.78) * 3 / 4);
        int fw = fh * 4 / 3;
        int fx = (width - fw) / 2, fy = Math.max(14, (height - fh) / 2 - 18);
        panel(g, fx - 14, fy - 12, fw + 28, fh + 60);
        bezel(g, fx, fy, fw, fh);

        if (gallery) {
            renderGallery(g, fx, fy, fw, fh);
        } else {
            renderFeed(g, fx, fy, fw, fh);
        }
        renderReadouts(g, fx, fy + fh + 8, fw);
    }

    private void renderFeed(GuiGraphics g, int fx, int fy, int fw, int fh) {
        boolean powered = ClientMissionState.powered();
        Packets.SubState s = ClientMissionState.cur;
        float cond = s == null ? 1f : s.cameraCondition;
        double reviewAge = (System.nanoTime() - reviewStart) / 1e9;
        PhotoRecord review = reviewId >= 0 ? findPhoto(reviewId) : null;

        if (review != null && reviewAge < 6.0) {
            if (reviewAge < 1.8) {
                g.fill(fx, fy, fx + fw, fy + fh, 0xFF060302);
                int bw = (int) (fw * 0.4);
                int bx = fx + (fw - bw) / 2, by = fy + fh / 2 + 6;
                textCentered(g, "DEVELOPING", fx + fw / 2, fy + fh / 2 - 8, TEXT);
                g.fill(bx, by, bx + bw, by + 3, 0xFF200806);
                g.fill(bx, by, bx + (int) (bw * Math.min(1, reviewAge / 1.8)), by + 3, TEXT);
            } else {
                ResourceLocation loc = FeedSystem.photo(review);
                if (loc != null) {
                    int[] sz = FeedSystem.photoSize(review);
                    g.blit(loc, fx, fy, fw, fh, 0, 0, sz[0], sz[1], sz[0], sz[1]);
                    text(g, String.format(Locale.ROOT, "PHOTO %02d", review.id), fx + 6, fy + 6, 0xFFE8C8B0);
                } else {
                    textCentered(g, "DEVELOPING", fx + fw / 2, fy + fh / 2 - 4, TEXT);
                }
            }
        } else if (!powered) {
            StaticNoise.draw(g, fx, fy, fw, fh, ClientMissionState.power() == Mission.Power.RESTORING ? 0.25f : 0.05f);
            if (blink(20)) textCentered(g, "NO SIGNAL", fx + fw / 2, fy + fh / 2 - 4, TEXT_DIM);
        } else {
            double glitch = ClientMissionState.glitchTicks > 0 ? 0.65 : (1 - cond) * 0.2;
            double grain = 0.28 + (1 - cond) * 0.6;
            ResourceLocation tex = FeedSystem.live(glitch, grain, 1.0);
            if (tex != null && FeedSystem.liveWidth() > 0) {
                int lw = FeedSystem.liveWidth(), lh = FeedSystem.liveHeight();
                g.blit(tex, fx, fy, fw, fh, 0, 0, lw, lh, lw, lh);
            }
            float sa = 0.035f + (1 - cond) * 0.12f + (ClientMissionState.glitchTicks > 0 ? 0.3f : 0f);
            StaticNoise.draw(g, fx, fy, fw, fh, sa);
            // frame furniture
            int c = 0xA0C04030;
            int L = 10;
            g.fill(fx + 8, fy + 8, fx + 8 + L, fy + 9, c);
            g.fill(fx + 8, fy + 8, fx + 9, fy + 8 + L, c);
            g.fill(fx + fw - 8 - L, fy + 8, fx + fw - 8, fy + 9, c);
            g.fill(fx + fw - 9, fy + 8, fx + fw - 8, fy + 8 + L, c);
            g.fill(fx + 8, fy + fh - 9, fx + 8 + L, fy + fh - 8, c);
            g.fill(fx + 8, fy + fh - 8 - L, fx + 9, fy + fh - 8, c);
            g.fill(fx + fw - 8 - L, fy + fh - 9, fx + fw - 8, fy + fh - 8, c);
            g.fill(fx + fw - 9, fy + fh - 8 - L, fx + fw - 8, fy + fh - 8, c);
            g.fill(fx + fw / 2 - 4, fy + fh / 2, fx + fw / 2 + 5, fy + fh / 2 + 1, 0x60C04030);
            g.fill(fx + fw / 2, fy + fh / 2 - 4, fx + fw / 2 + 1, fy + fh / 2 + 5, 0x60C04030);
            if (blink(12)) {
                g.fill(fx + fw - 40, fy + 14, fx + fw - 35, fy + 19, 0xFFD02818);
                text(g, "REC", fx + fw - 31, fy + 13, 0xC0D02818);
            }
            long secs = (long) ClientMissionState.now();
            text(g, String.format(Locale.ROOT, "%s  %02d:%02d:%02d", CameraMount.byId(ClientMissionState.camMount).label,
                    secs / 3600, (secs / 60) % 60, secs % 60), fx + 14, fy + 14, 0xB0C04030);
            if (s != null && s.photoCooldown > 0) text(g, "CHARGING", fx + 14, fy + fh - 20, 0xB0C04030);
        }
        if (ClientMissionState.flashTicks > 0) {
            int a = (int) (ClientMissionState.flashTicks / 6.0 * 220);
            g.fill(fx, fy, fx + fw, fy + fh, (a << 24) | 0xFFF0E0);
        }
    }

    private void renderGallery(GuiGraphics g, int fx, int fy, int fw, int fh) {
        int n = ClientMissionState.photos.size();
        if (n == 0) {
            textCentered(g, "NO EXPOSURES", fx + fw / 2, fy + fh / 2 - 4, TEXT_DIM);
            return;
        }
        galleryIndex = Math.max(0, Math.min(n - 1, galleryIndex));
        PhotoRecord p = ClientMissionState.photos.get(galleryIndex);
        ResourceLocation loc = FeedSystem.photo(p);
        if (loc != null) {
            int[] sz = FeedSystem.photoSize(p);
            g.blit(loc, fx, fy, fw, fh, 0, 0, sz[0], sz[1], sz[0], sz[1]);
        } else {
            textCentered(g, "DEVELOPING", fx + fw / 2, fy + fh / 2 - 4, TEXT);
        }
        g.fill(fx, fy, fx + fw, fy + 14, 0xB0000000);
        text(g, String.format(Locale.ROOT, "PHOTO %02d / %02d    X %.1f  Y %.1f  DEPTH %.1f", p.id, n, p.x, p.z, -p.y), fx + 4, fy + 3, 0xFFD8B8A0);
        StringBuilder tags = new StringBuilder();
        if (ClientMissionState.map != null) {
            for (int idx : p.hits) {
                if (idx < 0 || idx >= ClientMissionState.map.pois.size()) continue;
                Poi poi = ClientMissionState.map.pois.get(idx);
                int oi = ClientMissionState.map.objectives.indexOf(poi);
                if (tags.length() > 0) tags.append("   ");
                tags.append(oi >= 0 ? String.format(Locale.ROOT, "SITE %02d CONFIRMED", oi + 1) : "UNCATALOGUED");
            }
        }
        if (p.has(PhotoRecord.FLAG_RUINED)) tags.append(tags.length() > 0 ? "   " : "").append("EXPOSURE LOST");
        if (tags.length() > 0) {
            g.fill(fx, fy + fh - 14, fx + fw, fy + fh, 0xB0000000);
            text(g, tags.toString(), fx + 4, fy + fh - 11, 0xFFE0563F);
        }
    }

    private PhotoRecord findPhoto(int id) {
        for (PhotoRecord p : ClientMissionState.photos) if (p.id == id) return p;
        return null;
    }

    private void renderReadouts(GuiGraphics g, int x, int y, int w) {
        boolean powered = ClientMissionState.powered();
        int rx = x + w - 102;
        if (!powered) {
            text(g, "CAMERA OFFLINE", x, y, TEXT_DIM);
        } else {
            double[] pose = ClientMissionState.cameraPose();
            double yawDeg = ((Math.toDegrees(pose[3]) % 360) + 360) % 360;
            text(g, String.format(Locale.ROOT, "X %06.1f  Y %06.1f  BRG %03.0f  PAN %+03.0f  TILT %+03.0f",
                    pose[0], pose[2], yawDeg, Math.toDegrees(ClientMissionState.camPan), Math.toDegrees(ClientMissionState.camTilt)), x, y, TEXT);
            Packets.SubState s = ClientMissionState.cur;
            int sig = s == null ? 100 : Math.round(s.cameraCondition * 100);
            if (ClientMissionState.glitchTicks > 0) sig = Math.max(3, sig - 60 - (int) (Math.random() * 20));
            text(g, String.format(Locale.ROOT, "SIGNAL %03d%%", sig), rx, y + 12, sig < 50 ? TEXT_HOT : TEXT_DIM);
        }
        text(g, gallery ? "TAB FEED   A/D BROWSE" : "A/D PAN   W/S TILT   Q/E MOUNT", x, y + 12, TEXT_DIM);
        if (!gallery) text(g, "SPACE CAPTURE   TAB PHOTOS", x, y + 24, TEXT_DIM);
        int done = 0;
        for (boolean b : ClientMissionState.done) if (b) done++;
        text(g, String.format(Locale.ROOT, "SITES %d/%d  EXP %02d", done, ClientMissionState.done.length, ClientMissionState.photos.size()),
                rx, y + 24, done == ClientMissionState.done.length ? TEXT_HOT : TEXT_DIM);
    }
}
