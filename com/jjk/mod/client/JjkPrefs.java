package com.jjk.mod.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.client.Minecraft;

public final class JjkPrefs {
   private static Boolean skip;

   private JjkPrefs() {
   }

   private static Path file() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("jjk-client-prefs.properties");
   }

   public static boolean skipCutscenes() {
      if (skip == null) {
         Properties p = new Properties();

         try (Reader r = Files.newBufferedReader(file())) {
            p.load(r);
         } catch (Exception var6) {
         }

         skip = Boolean.parseBoolean(p.getProperty("skipCutscenes", "false"));
      }

      return skip;
   }

   public static void setSkipCutscenes(boolean value) {
      skip = value;
      Properties p = new Properties();
      p.setProperty("skipCutscenes", Boolean.toString(value));

      try {
         Files.createDirectories(file().getParent());

         try (Writer w = Files.newBufferedWriter(file())) {
            p.store(w, "Jujutsu Kaisen mod - client preferences");
         }
      } catch (Exception var7) {
      }
   }
}
