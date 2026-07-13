package autismclient.gui.vanillaui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiRenderer {
    private UiRenderer() {
    }

    public static void rect(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color);
    }

    public static void horizontalEdge(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        if (width <= 0) return;
        rect(graphics, UiBounds.of(x, y, width, 1), color);
    }

    public static void verticalEdge(GuiGraphicsExtractor graphics, int x, int y, int height, int color) {
        if (height <= 0) return;
        rect(graphics, UiBounds.of(x, y, 1, height), color);
    }

    public static void outline(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        horizontalEdge(graphics, bounds.x(), bounds.y(), bounds.width(), color);
        if (bounds.height() > 1) {
            horizontalEdge(graphics, bounds.x(), bounds.bottom() - 1, bounds.width(), color);
        }
        if (bounds.height() > 2) {
            verticalEdge(graphics, bounds.x(), bounds.y() + 1, bounds.height() - 2, color);
            if (bounds.width() > 1) {
                verticalEdge(graphics, bounds.right() - 1, bounds.y() + 1, bounds.height() - 2, color);
            }
        }
    }

    public static void frame(GuiGraphicsExtractor graphics, UiBounds bounds, int fill, int border) {
        rect(graphics, bounds, fill);
        outline(graphics, bounds, border);
    }

    public static void window(GuiGraphicsExtractor graphics, UiBounds bounds, int headerHeight,
                              int fill, int headerFill, int bodyShade, int border, int accent, float alpha) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        int headerBottom = Math.min(bounds.bottom(), bounds.y() + Math.max(1, headerHeight));
        rect(graphics, bounds, applyAlpha(fill, alpha));
        rect(graphics, UiBounds.of(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.width() - 2), Math.max(0, headerBottom - bounds.y() - 2)),
            applyAlpha(headerFill, alpha));
        if (headerBottom < bounds.bottom() - 1) {
            rect(graphics, UiBounds.of(bounds.x() + 1, headerBottom, Math.max(0, bounds.width() - 2), Math.max(0, bounds.bottom() - headerBottom - 1)),
                applyAlpha(bodyShade, alpha));
        }
        outline(graphics, bounds, applyAlpha(border, alpha));
        if (headerBottom > bounds.y()) {
            horizontalEdge(graphics, bounds.x(), headerBottom - 1, bounds.width(), applyAlpha(accent, alpha));
        }
    }

    public static void popup(GuiGraphicsExtractor graphics, UiBounds bounds, int fill, int border, int accent) {
        frame(graphics, bounds, fill, border);
        horizontalEdge(graphics, bounds.x(), bounds.y(), bounds.width(), accent);
    }

    public static int applyAlpha(int color, float alpha) {
        int sourceAlpha = (color >>> 24) & 0xFF;
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * Math.max(0.0f, Math.min(1.0f, alpha)))));
        return (resolvedAlpha << 24) | (color & 0x00FFFFFF);
    }
}
