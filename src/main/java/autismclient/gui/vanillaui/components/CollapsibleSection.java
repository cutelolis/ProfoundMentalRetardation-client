package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

import java.util.Locale;

public final class CollapsibleSection {
    private CollapsibleSection() {
    }

    public static void renderHeader(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean hovered) {
        var colors = context.theme().colors();
        UiRenderer.frame(context.graphics(), bounds, hovered ? colors.rowHover : colors.row, colors.accent);
        context.text().drawFitted(
            context.graphics(),
            (collapsed ? "+ " : "- ") + safe(title).toUpperCase(Locale.ROOT),
            bounds.x() + 5,
            context.text().centeredY(bounds),
            Math.max(1, bounds.width() - 10),
            colors.text
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
