package com.ewitulsk.villagersimulator.neoforge.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A screen showing one {@link UiNode} tree, centred, at most {@link #maxWidth()} wide. */
public abstract class UiScreen extends Screen {
    private UiNode root;

    protected UiScreen(Component title) {
        super(title);
    }

    /** Builds the node tree; called on init and whenever {@link #rebuild()} is called. */
    protected abstract UiNode build();

    protected int maxWidth() {
        return 260;
    }

    protected void rebuild() {
        root = build();
        layout();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void layout() {
        if (root == null) return;
        int w = Math.min(maxWidth(), width - 20);
        root.measure(font, w);
        root.place((width - root.width()) / 2, Math.max(10, (height - root.height()) / 2));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (root == null) return;
        root.render(g, font, mouseX, mouseY);
        Component tip = root.tooltip(mouseX, mouseY);
        if (tip != null) g.renderTooltip(font, tip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && root != null && root.click(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
