package com.ewitulsk.villagersimulator.neoforge.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The internal UI toolkit v1 (docs/ARCHITECTURE.md §16): a small tree of nodes that measure themselves against a
 * maximum width, get placed, render and handle clicks. Screens build a tree and let {@link UiScreen} lay it out.
 */
public abstract class UiNode {
    protected int x, y, width, height;

    /** Computes {@link #width} and {@link #height}, given at most {@code maxWidth}. */
    public abstract void measure(Font font, int maxWidth);

    /** Positions the node (and its children) with its top-left at {@code (x, y)}. */
    public void place(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public abstract void render(GuiGraphics g, Font font, int mouseX, int mouseY);

    /** @return true if the click was handled */
    public boolean click(double mouseX, double mouseY) {
        return false;
    }

    /** Tooltip under the mouse, or {@code null}. */
    public Component tooltip(double mouseX, double mouseY) {
        return null;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    // ------------------------------------------------------------------------------------------------ nodes

    /** Wrapped text. */
    public static final class Label extends UiNode {
        private final Component text;
        private final int color;
        private List<net.minecraft.util.FormattedCharSequence> lines = List.of();

        public Label(Component text, int color) {
            this.text = text;
            this.color = color;
        }

        @Override
        public void measure(Font font, int maxWidth) {
            lines = font.split(text, maxWidth);
            width = 0;
            for (var l : lines) width = Math.max(width, font.width(l));
            height = lines.size() * (font.lineHeight + 1);
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
            int ly = y;
            for (var l : lines) {
                g.drawString(font, l, x, ly, color, false);
                ly += font.lineHeight + 1;
            }
        }
    }

    /** A clickable button; disabled buttons are greyed out and explain why in a tooltip. */
    public static final class Button extends UiNode {
        private static final int H = 18;
        private final Component label;
        private final boolean enabled;
        private final Component tooltip;
        private final Runnable onClick;

        public Button(Component label, boolean enabled, Component tooltip, Runnable onClick) {
            this.label = label;
            this.enabled = enabled;
            this.tooltip = tooltip;
            this.onClick = onClick;
        }

        @Override
        public void measure(Font font, int maxWidth) {
            width = maxWidth;
            height = H;
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
            boolean hover = enabled && contains(mouseX, mouseY);
            g.fill(x, y, x + width, y + height, hover ? 0xFF5A4A30 : enabled ? 0xFF3A3024 : 0xFF2A2A2A);
            g.renderOutline(x, y, width, height, hover ? 0xFFE0C080 : 0xFF6A5A40);
            g.drawCenteredString(font, label, x + width / 2, y + (H - font.lineHeight) / 2 + 1, enabled ? 0xFFF0E6D0 : 0xFF808080);
        }

        @Override
        public boolean click(double mouseX, double mouseY) {
            if (!enabled || !contains(mouseX, mouseY)) return false;
            onClick.run();
            return true;
        }

        @Override
        public Component tooltip(double mouseX, double mouseY) {
            return contains(mouseX, mouseY) ? tooltip : null;
        }
    }

    /** Children stacked vertically. */
    public static class Column extends UiNode {
        protected final List<UiNode> children = new ArrayList<>();
        private final int gap;

        public Column(int gap) {
            this.gap = gap;
        }

        public Column add(UiNode child) {
            children.add(child);
            return this;
        }

        @Override
        public void measure(Font font, int maxWidth) {
            width = 0;
            height = 0;
            for (UiNode c : children) {
                c.measure(font, maxWidth);
                width = Math.max(width, c.width);
                height += c.height + (height > 0 ? gap : 0);
            }
        }

        @Override
        public void place(int x, int y) {
            super.place(x, y);
            int cy = y;
            for (UiNode c : children) {
                c.place(x, cy);
                cy += c.height + gap;
            }
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
            for (UiNode c : children) c.render(g, font, mouseX, mouseY);
        }

        @Override
        public boolean click(double mouseX, double mouseY) {
            for (UiNode c : children) if (c.click(mouseX, mouseY)) return true;
            return false;
        }

        @Override
        public Component tooltip(double mouseX, double mouseY) {
            for (UiNode c : children) {
                Component t = c.tooltip(mouseX, mouseY);
                if (t != null) return t;
            }
            return null;
        }
    }

    /** Children side by side, sharing the width equally. */
    public static final class Row extends Column {
        private final int gap;

        public Row(int gap) {
            super(gap);
            this.gap = gap;
        }

        @Override
        public void measure(Font font, int maxWidth) {
            int n = Math.max(1, children.size());
            int each = (maxWidth - gap * (n - 1)) / n;
            height = 0;
            for (UiNode c : children) {
                c.measure(font, each);
                height = Math.max(height, c.height);
            }
            width = maxWidth;
        }

        @Override
        public void place(int x, int y) {
            this.x = x;
            this.y = y;
            int cx = x;
            for (UiNode c : children) {
                c.place(cx, y);
                cx += c.width + gap;
            }
        }
    }

    /** A framed background around one child. */
    public static final class Panel extends UiNode {
        private final UiNode child;
        private final int padding;

        public Panel(UiNode child, int padding) {
            this.child = child;
            this.padding = padding;
        }

        @Override
        public void measure(Font font, int maxWidth) {
            child.measure(font, maxWidth - 2 * padding);
            width = maxWidth;
            height = child.height + 2 * padding;
        }

        @Override
        public void place(int x, int y) {
            super.place(x, y);
            child.place(x + padding, y + padding);
        }

        @Override
        public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
            g.fill(x, y, x + width, y + height, 0xE0201810);
            g.renderOutline(x, y, width, height, 0xFF8A7050);
            child.render(g, font, mouseX, mouseY);
        }

        @Override
        public boolean click(double mouseX, double mouseY) {
            return child.click(mouseX, mouseY);
        }

        @Override
        public Component tooltip(double mouseX, double mouseY) {
            return child.tooltip(mouseX, mouseY);
        }
    }
}
