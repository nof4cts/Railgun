package com.aegis.command.client.screen;

import com.aegis.command.AegisCommand;
import com.aegis.command.client.ui.Theme;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Builds a monochrome tactical terrain map of the real world around the laptop: height shading,
 * contour lines, water and unloaded-sector hatching, tinted in the terminal accent colour.
 * Generated a few rows per frame so it "scans in" on screen.
 */
public class TacticalMap {
    public static final ResourceLocation TEX = AegisCommand.id("dynamic/tactical_map");
    public final int radius;
    public final int size;
    private final BlockPos center;
    private final DynamicTexture texture;
    private final int[] heights;
    private int row;
    private final int accent;

    public TacticalMap(BlockPos center, int radius) {
        this.center = center;
        this.radius = radius;
        this.size = radius * 2;
        this.texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, size, size, true));
        this.heights = new int[size * size];
        this.accent = Theme.accent();
        Minecraft.getInstance().getTextureManager().register(TEX, texture);
    }

    public float progress() { return row / (float) size; }
    public boolean done() { return row >= size; }

    public void step(int rows) {
        ClientLevel level = Minecraft.getInstance().level;
        NativeImage img = texture.getPixels();
        if (level == null || img == null || done()) return;
        int end = Math.min(size, row + rows);
        for (; row < end; row++) {
            int wz = center.getZ() - radius + row;
            for (int px = 0; px < size; px++) {
                int wx = center.getX() - radius + px;
                img.setPixelRGBA(px, row, toAbgr(color(level, wx, wz, px, row)));
            }
        }
        texture.upload();
    }

    private int color(ClientLevel level, int wx, int wz, int px, int pz) {
        if (!level.hasChunk(wx >> 4, wz >> 4)) {
            heights[pz * size + px] = Integer.MIN_VALUE;
            return ((wx + wz) & 7) == 0 ? 0x10201A : 0x070C0A;
        }
        int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        heights[pz * size + px] = h;
        BlockPos pos = new BlockPos(wx, h, wz);
        BlockState st = level.getBlockState(pos);
        boolean water = !st.getFluidState().isEmpty();
        MapColor mc = st.getMapColor(level, pos);
        int c = mc == null ? 0x404040 : mc.col;
        float lum = (0.3f * ((c >> 16) & 255) + 0.59f * ((c >> 8) & 255) + 0.11f * (c & 255)) / 255f;

        int hn = pz > 0 ? heights[(pz - 1) * size + px] : h;
        int hw = px > 0 ? heights[pz * size + px - 1] : h;
        if (hn == Integer.MIN_VALUE) hn = h;
        if (hw == Integer.MIN_VALUE) hw = h;
        float shade = 1f + Math.max(-0.35f, Math.min(0.35f, (h - hn) * 0.09f + (h - hw) * 0.05f));
        boolean contour = Math.floorDiv(h, 6) != Math.floorDiv(hn, 6) || Math.floorDiv(h, 6) != Math.floorDiv(hw, 6);

        if (water) {
            return Theme.mix(0x061A20, Theme.scale(accent, 0.25f), 0.25f + 0.1f * (((wx * 3 + wz) & 3) == 0 ? 1 : 0));
        }
        float k = (0.1f + 0.42f * lum) * shade;
        int base = Theme.scale(accent, k);
        if (contour) base = Theme.mix(base, accent, 0.45f);
        return base;
    }

    private static int toAbgr(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(TEX);
    }
}
