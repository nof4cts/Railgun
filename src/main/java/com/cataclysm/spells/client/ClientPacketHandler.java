package com.cataclysm.spells.client;

import com.cataclysm.spells.network.CutType;
import com.cataclysm.spells.network.CutscenePacket;
import com.cataclysm.spells.network.FxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class ClientPacketHandler {

    public static void fx(FxPacket p) {
        FxDispatcher.handle(p);
    }

    public static void cutscene(CutscenePacket p) {
        if (Minecraft.getInstance().player == null) return;
        Vec3 at = new Vec3(p.arg(0) / 10.0, p.arg(1) / 10.0, p.arg(2) / 10.0);
        switch (CutType.byId(p.type)) {
            case STAR -> CameraDirector.play(SpellCutscenes.star(p.anchor, at));
            case HOLE -> CameraDirector.play(SpellCutscenes.hole(p.anchor, at));
            case LANCE -> CameraDirector.play(SpellCutscenes.lance(p.anchor, at, p.arg(3) / 10f));
        }
    }

    private ClientPacketHandler() {}
}
