package com.hakari.idg.client;

import com.hakari.idg.network.CutType;
import com.hakari.idg.network.CutscenePacket;
import com.hakari.idg.network.FxPacket;
import com.hakari.idg.network.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ClientPacketHandler {
   public static void fx(FxPacket p) {
      FxDispatcher.handle(p);
   }

   public static void state(StatePacket p) {
      ClientState.put(p);
   }

   public static void cutscene(CutscenePacket p) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         switch (CutType.byId(p.type)) {
            case DOMAIN:
               CameraDirector.play(Cutscenes.domain(p.anchor, p.arg(0) == 1, p.arg(1) == 1));
               break;
            case RIICHI:
               RiichiOverlay.start(p.anchor, p.arg(0), p.arg(1), p.arg(2), p.arg(3), p.arg(4) == 1, p.arg(5) == 1);
               break;
            case JACKPOT:
               boolean full = p.arg(2) == 1;
               if (full) {
                  CameraDirector.play(Cutscenes.jackpot(p.anchor, p.arg(0), p.arg(1) == 1));
               } else {
                  Entity e = mc.level == null ? null : mc.level.getEntity(p.anchor);
                  Vec3 at = e != null ? e.position().add(0.0, 1.0, 0.0) : null;
                  RiichiOverlay.jackpotReels(p.anchor, p.arg(0));
                  Sfx.later(1000L, () -> {
                     ScreenFx.split(30.0F, 500L, 18.0F);
                     ScreenFx.impact(3, 200L, at);
                     CameraDirector.shake(1.6F, 600L);
                     Sfx.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 0.6F);
                     ScreenFx.card(new ScreenFx.Card("大当たり", "HE HIT THE JACKPOT", "", 2, -11443, 1500L, true));
                     if (at != null) {
                        Vfx.add(new Vfx.Coins(at.add(0.0, -1.0, 0.0), 60, 2400L));
                        Vfx.add(new Vfx.Pillar(at.add(0.0, -1.0, 0.0), 60.0, 2.2, -11443, 2200L));
                     }
                  });
               }
               break;
            case MICRO_ROUGH:
               CameraDirector.play(Cutscenes.microRough(p.anchor));
               break;
            case MICRO_OVERWHELM:
               CameraDirector.play(Cutscenes.microOverwhelm(p.anchor));
               break;
            case MICRO_SURGE:
               CameraDirector.play(Cutscenes.microSurge(p.anchor));
               break;
            case MICRO_RUSH:
               CameraDirector.play(Cutscenes.microRush(p.anchor));
         }
      }
   }

   private ClientPacketHandler() {
   }
}
