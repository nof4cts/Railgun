package com.geto.csm.client;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CutType;
import com.geto.csm.network.CutscenePacket;
import com.geto.csm.network.FxPacket;
import com.geto.csm.network.QuestionPacket;
import com.geto.csm.network.StatePacket;
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
        switch (CutType.byId(p.type)) {
            case SUMMON -> CameraDirector.play(Cutscenes.summon(p.anchor, CurseKind.byId(p.arg(0))));
            case UZUMAKI -> CameraDirector.play(Cutscenes.uzumaki(p.anchor, p.arg(0), p.arg(1) == 1));
            case STAR -> CameraDirector.play(SpellCutscenes.star(p.anchor, new Vec3(p.arg(0) / 10.0, p.arg(1) / 10.0, p.arg(2) / 10.0)));
            case HOLE -> CameraDirector.play(SpellCutscenes.hole(p.anchor, new Vec3(p.arg(0) / 10.0, p.arg(1) / 10.0, p.arg(2) / 10.0)));
            case LANCE -> CameraDirector.play(SpellCutscenes.lance(p.anchor, new Vec3(p.arg(0) / 10.0, p.arg(1) / 10.0, p.arg(2) / 10.0), p.arg(3) / 10f));
        }
    }

    public static void question(QuestionPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.stage == 0) {
            if (mc.screen instanceof QuestionScreen q && q.entityId == p.entityId) q.onClose();
            return;
        }
        mc.setScreen(new QuestionScreen(p.entityId, p.stage, p.timeoutTicks));
    }

    private ClientPacketHandler() {}
}
