package com.abomination.horror.client.render;

import com.abomination.horror.AbominationMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sculpted, smooth-shaded meshes baked by tools/sculpt.py (assets/abomination/meshes/abomination.bin).
 * Each entry is a list of quads in bone-local space: position, smooth normal, atlas UV, and a baked
 * ambient-occlusion tint. Nothing is drawn with cubes.
 */
public final class MeshLibrary {
    private static final int STRIDE = 9; // x y z nx ny nz u v shade
    private static MeshLibrary instance;
    private static boolean failed;

    private final Map<String, float[]> meshes = new HashMap<>();

    private MeshLibrary() {}

    public static MeshLibrary get() {
        if (instance == null && !failed) {
            try {
                instance = load();
            } catch (Exception e) {
                failed = true;
                AbominationMod.LOGGER.error("Failed to load Abomination meshes", e);
            }
        }
        return instance;
    }

    private static InputStream open() throws Exception {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(AbominationMod.id("meshes/abomination.bin"));
        if (res.isPresent()) return res.get().open();
        InputStream in = MeshLibrary.class.getResourceAsStream("/assets/abomination/meshes/abomination.bin");
        if (in == null) throw new IllegalStateException("abomination.bin not found");
        return in;
    }

    private static MeshLibrary load() throws Exception {
        MeshLibrary lib = new MeshLibrary();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(open(), 1 << 16))) {
            int entries = in.readInt();
            for (int e = 0; e < entries; e++) {
                String name = in.readUTF();
                int quads = in.readInt();
                float[] data = new float[quads * 4 * STRIDE];
                for (int v = 0; v < quads * 4; v++) {
                    int i = v * STRIDE;
                    for (int k = 0; k < 8; k++) data[i + k] = in.readFloat();
                    data[i + 8] = (in.readInt() & 0xFF) / 255.0F;
                }
                lib.meshes.put(name, data);
            }
        }
        return lib;
    }

    public boolean has(String name) {
        float[] d = meshes.get(name);
        return d != null && d.length > 0;
    }

    public void draw(String name, PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        float[] d = meshes.get(name);
        if (d == null || d.length == 0) return;
        Matrix4f pm = pose.pose();
        Matrix3f nm = pose.normal();
        for (int i = 0; i < d.length; i += STRIDE) {
            float s = d[i + 8];
            vc.vertex(pm, d[i], d[i + 1], d[i + 2])
                    .color(r * s, g * s, b * s, a)
                    .uv(d[i + 6], d[i + 7])
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(nm, d[i + 3], d[i + 4], d[i + 5])
                    .endVertex();
        }
    }
}
