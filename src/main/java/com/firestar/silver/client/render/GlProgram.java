package com.firestar.silver.client.render;

import com.firestar.silver.SilverMod;
import com.mojang.blaze3d.platform.GlStateManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** A raw GLSL program loaded from assets/santoryu/shaders/fx, with #include "file" support. */
public final class GlProgram implements AutoCloseable {
    private static final Pattern INCLUDE = Pattern.compile("#include\\s+\"([^\"]+)\"");
    private final int id;
    private final Map<String, Integer> locations = new HashMap<>();

    private GlProgram(int id) {
        this.id = id;
    }

    public static GlProgram load(ResourceManager rm, String vertex, String fragment) throws IOException {
        int vs = compile(GL20.GL_VERTEX_SHADER, vertex, source(rm, vertex, new HashSet<>()));
        int fs;
        try {
            fs = compile(GL20.GL_FRAGMENT_SHADER, fragment, source(rm, fragment, new HashSet<>()));
        } catch (IOException e) {
            GL20.glDeleteShader(vs);
            throw e;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            String log = GL20.glGetProgramInfoLog(program, 32768);
            GL20.glDeleteProgram(program);
            throw new IOException("Linking " + fragment + " failed:\n" + log);
        }
        return new GlProgram(program);
    }

    private static int compile(int type, String file, String src) throws IOException {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IOException("Compiling " + file + " failed:\n" + log);
        }
        return shader;
    }

    private static String source(ResourceManager rm, String file, Set<String> seen) throws IOException {
        if (!seen.add(file)) {
            return "";
        }
        ResourceLocation loc = SilverMod.id("shaders/fx/" + file);
        Resource res = rm.getResource(loc).orElseThrow(() -> new IOException("Missing shader " + loc));
        String text;
        try (InputStream in = res.open()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher m = INCLUDE.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(source(rm, m.group(1), seen)));
        }
        m.appendTail(out);
        return out.toString();
    }

    public void use() {
        GlStateManager._glUseProgram(id);
    }

    private int loc(String name) {
        return locations.computeIfAbsent(name, n -> GL20.glGetUniformLocation(id, n));
    }

    public void set(String u, int v) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform1i(l, v);
    }

    public void set(String u, float v) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform1f(l, v);
    }

    public void set(String u, float x, float y) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform2f(l, x, y);
    }

    public void set(String u, float x, float y, float z) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform3f(l, x, y, z);
    }

    public void set(String u, float x, float y, float z, float w) {
        int l = loc(u);
        if (l >= 0) GL20.glUniform4f(l, x, y, z, w);
    }

    public void set(String u, Matrix4f m) {
        int l = loc(u);
        if (l >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                m.get(fb);
                GL20.glUniformMatrix4fv(l, false, fb);
            }
        }
    }

    @Override
    public void close() {
        GL20.glDeleteProgram(id);
    }
}
