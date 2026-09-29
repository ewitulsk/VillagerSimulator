package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.neoforge.net.DialoguePayload;

/** Client-side packet handlers, in their own class so the server never loads client code. */
public final class ClientHandlers {
    private ClientHandlers() {}

    public static void overlay(com.ewitulsk.villagersimulator.neoforge.net.DebugOverlayPayload payload) {
        DebugOverlayRenderer.update(payload);
    }

    public static void dialogue(DialoguePayload payload) {
        DialogueScreen.show(payload);
    }
}
