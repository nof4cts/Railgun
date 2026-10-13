package com.shrine.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class RenderTex {
   public final int width;
   public final int height;
   public final int texture;
   public final int fbo;

   public RenderTex(int width, int height, boolean hdr) {
      this.width = Math.max(1, width);
      this.height = Math.max(1, height);
      this.texture = GL11.glGenTextures();
      GlStateManager._bindTexture(this.texture);
      GL11.glTexParameteri(3553, 10241, 9729);
      GL11.glTexParameteri(3553, 10240, 9729);
      GL11.glTexParameteri(3553, 10242, 33071);
      GL11.glTexParameteri(3553, 10243, 33071);
      GL11.glTexImage2D(3553, 0, hdr ? '蠚' : '聘', this.width, this.height, 0, 6408, hdr ? 5126 : 5121, (ByteBuffer)null);
      GlStateManager._bindTexture(0);
      this.fbo = GL30.glGenFramebuffers();
      GL30.glBindFramebuffer(36160, this.fbo);
      GL30.glFramebufferTexture2D(36160, 36064, 3553, this.texture, 0);
      int status = GL30.glCheckFramebufferStatus(36160);
      GL30.glBindFramebuffer(36160, 0);
      if (status != 36053) {
         this.close();
         throw new IllegalStateException("Shrine render target incomplete: 0x" + Integer.toHexString(status));
      }
   }

   public void bindWrite() {
      GlStateManager._glBindFramebuffer(36160, this.fbo);
      GlStateManager._viewport(0, 0, this.width, this.height);
   }

   public void close() {
      GL30.glDeleteFramebuffers(this.fbo);
      GlStateManager._deleteTexture(this.texture);
   }
}
