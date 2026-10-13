package com.jjk.mod.client;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;

public final class CloudHide {
   private static final Set<String> OWNERS = new HashSet<>();
   private static CloudStatus saved;

   private CloudHide() {
   }

   public static void set(String owner, boolean hide) {
      Minecraft mc = Minecraft.getInstance();
      boolean was = !OWNERS.isEmpty();
      if (hide) {
         OWNERS.add(owner);
      } else {
         OWNERS.remove(owner);
      }

      boolean now = !OWNERS.isEmpty();
      if (now && !was) {
         saved = (CloudStatus)mc.options.cloudStatus().get();
         mc.options.cloudStatus().set(CloudStatus.OFF);
      } else if (!now && was && saved != null) {
         mc.options.cloudStatus().set(saved);
         saved = null;
      }
   }
}
