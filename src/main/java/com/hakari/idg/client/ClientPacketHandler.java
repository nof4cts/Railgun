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
        if (mc.player == null) return;
        switch (CutType.byId(p.type)) {
            case DOMAIN -> CameraDirector.play(Cutscenes.domain(p.anchor, p.arg(0) == 1, p.arg(1) == 1));
            case RIICHI -> RiichiOverlay.start(p.anchor, p.arg(0), p.arg(1), p.arg(2), p.arg(3), p.arg(4) == 1, p.arg(5) == 1);
            case JACKPOT -> {
                boolean full = p.arg(2) == 1;
                if (full) {
                    CameraDirector.play(Cutscenes.jackpot(p.anchor, p.arg(0), p.arg(1) == 1));
                } else {
                    // bystanders: no camera hijack, but they still feel it
                    Entity e = mc.level == null ? null : mc.level.getEntity(p.anchor);
                    Vec3 at = e != null ? e.position().add(0, 1, 0) : null;
                    RiichiOverlay.jackpotReels(p.anchor, p.arg(0));
                    Sfx.later(1000, () -> {
                        ScreenFx.split(30, 500, 18);
                        ScreenFx.impact(ScreenFx.GOLD, 200, at);
                        CameraDirector.shake(1.6f, 600);
                        Sfx.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.6f);
                        ScreenFx.card(new ScreenFx.Card("大当たり", "HE HIT THE JACKPOT", "", ScreenFx.Card.POP, 0xFFFFD34D, 1500, true));
                        if (at != null) {
                            Vfx.add(new Vfx.Coins(at.add(0, -1, 0), 60, 2400));
                            Vfx.add(new Vfx.Pillar(at.add(0, -1, 0), 60, 2.2, 0xFFFFD34D, 2200));
                        }
                    });
                }
            }
            case MICRO_ROUGH -> CameraDirector.play(Cutscenes.microRough(p.anchor));
            case MICRO_OVERWHELM -> CameraDirector.play(Cutscenes.microOverwhelm(p.anchor));
            case MICRO_SURGE -> CameraDirector.play(Cutscenes.microSurge(p.anchor));
            case MICRO_RUSH -> CameraDirector.play(Cutscenes.microRush(p.anchor));
        }
    }

    private ClientPacketHandler() {}
}
