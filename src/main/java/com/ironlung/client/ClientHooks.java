package com.ironlung.client;

import com.ironlung.block.ControlType;
import com.ironlung.client.screen.CameraScreen;
import com.ironlung.client.screen.ChartScreen;
import com.ironlung.client.screen.HelmScreen;
import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openControl(ControlType type) {
        if (!ClientMissionState.inSub()) return;
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case HELM, BALLAST -> mc.setScreen(new HelmScreen());
            case CAMERA -> mc.setScreen(new CameraScreen());
            case CHART -> mc.setScreen(new ChartScreen());
            default -> {}
        }
    }
}
