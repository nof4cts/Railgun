package com.abomination.horror.client;

import com.abomination.horror.client.fx.ClientFinisher;
import com.abomination.horror.client.fx.ClientFx;
import com.abomination.horror.network.FinisherPacket;
import com.abomination.horror.network.FxPacket;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void onFinisher(FinisherPacket msg) {
        ClientFinisher.onPacket(msg);
    }

    public static void onJumpscare(com.abomination.horror.network.JumpscarePacket msg) {
        com.abomination.horror.client.fx.ClientJumpscare.start(msg.creatureId);
    }

    public static void onFx(FxPacket msg) {
        ClientFx.apply(msg.shake, msg.aberration, msg.flash, msg.flicker);
    }
}
