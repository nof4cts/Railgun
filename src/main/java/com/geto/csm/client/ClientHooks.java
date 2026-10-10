package com.geto.csm.client;

import net.minecraft.client.Minecraft;

public final class ClientHooks {

    public static void openSelector() {
        Minecraft.getInstance().setScreen(new SelectorScreen());
    }

    private ClientHooks() {}
}
