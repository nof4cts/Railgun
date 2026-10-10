package com.sixpaths.client;

import com.sixpaths.network.CutType;
import com.sixpaths.network.CutscenePacket;
import com.sixpaths.network.FxPacket;
import com.sixpaths.network.StatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class ClientPacketHandler {

    public static void fx(FxPacket p) {
        FxDispatcher.handle(p);
    }

    public static void state(StatePacket p) {
        ClientState.apply(p);
    }

    public static void cutscene(CutscenePacket p) {
        if (Minecraft.getInstance().player == null) return;
        Vec3 at = new Vec3(p.arg(0) / 10.0, p.arg(1) / 10.0, p.arg(2) / 10.0);
        switch (CutType.byId(p.type)) {
            case CHIBAKU -> CameraDirector.play(Cutscenes.chibaku(p.anchor, at));
            case DESCENT -> CameraDirector.play(Cutscenes.descent(p.anchor, at));
            case SAMSARA -> CameraDirector.play(Cutscenes.samsara(p.anchor, at));
            case KING -> CameraDirector.play(Cutscenes.king(p.anchor, at, p.arg(3) / 10f));
            case CANNON -> CameraDirector.play(Cutscenes.cannon(p.anchor, at));
            case SOUL -> CameraDirector.play(Cutscenes.soul(p.anchor, p.arg(0)));
            case ASCEND -> CameraDirector.play(Cutscenes.ascend(p.anchor));
            case SUMMON -> CameraDirector.play(Cutscenes.summon(p.anchor, at, p.arg(3)));
        }
    }

    private ClientPacketHandler() {}
}
