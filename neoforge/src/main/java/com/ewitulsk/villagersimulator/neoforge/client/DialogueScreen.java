package com.ewitulsk.villagersimulator.neoforge.client;

import com.ewitulsk.villagersimulator.content.players.Dialogue;
import com.ewitulsk.villagersimulator.neoforge.client.ui.UiNode;
import com.ewitulsk.villagersimulator.neoforge.client.ui.UiScreen;
import com.ewitulsk.villagersimulator.neoforge.net.DialogueChoicePayload;
import com.ewitulsk.villagersimulator.neoforge.net.DialoguePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Talking with a villager (docs/DESIGN.md §11.3). Options and replies come from the server's sim. */
public final class DialogueScreen extends UiScreen {
    private DialoguePayload state;

    private DialogueScreen(DialoguePayload state) {
        super(Component.literal(state.name()));
        this.state = state;
    }

    /** Opens the screen, or updates it if it's already showing this villager. */
    public static void show(DialoguePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DialogueScreen open && open.state.entityId() == payload.entityId()) {
            open.state = payload;
            open.rebuild();
        } else {
            mc.setScreen(new DialogueScreen(payload));
        }
    }

    @Override
    protected UiNode build() {
        UiNode.Column body = new UiNode.Column(6)
                .add(new UiNode.Label(Component.literal(state.name()), 0xFFF0C060))
                .add(new UiNode.Label(Component.literal(state.header()), 0xFFA09070))
                .add(new UiNode.Label(Component.literal("“" + state.greeting() + "”"), 0xFFF0E6D0));
        // Addons' panels (mod-api DialoguePanel), from the facts the server sent.
        var context = new com.ewitulsk.villagersimulator.api.mod.ui.DialoguePanel.Context(state.name(), state.facts());
        for (var panel : DialoguePanels.all()) {
            try {
                for (Component line : panel.lines(context)) body.add(new UiNode.Label(line, 0xFFC8D8F0));
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("VillagerSim").warn("A dialogue panel failed", e);
            }
        }
        if (!state.response().isEmpty()) {
            body.add(new UiNode.Label(Component.literal("“" + state.response() + "”").withStyle(s -> s.withItalic(true)), 0xFFD8F0C0));
        }
        UiNode.Column options = new UiNode.Column(3);
        for (Dialogue.Option o : state.options()) {
            options.add(new UiNode.Button(Component.literal(o.label()), o.enabled(),
                    o.reason().isEmpty() ? null : Component.literal(o.reason()),
                    () -> PacketDistributor.sendToServer(new DialogueChoicePayload(state.entityId(), o.id()))));
        }
        options.add(new UiNode.Button(Component.literal("Goodbye"), true, null, this::onClose));
        body.add(options);
        return new UiNode.Panel(body, 10);
    }
}
