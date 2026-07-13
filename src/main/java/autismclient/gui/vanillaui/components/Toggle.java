package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;

public final class Toggle {
    private Toggle() {
    }

    public static void render(UiContext context, UiBounds bounds, boolean enabled, boolean hovered) {
        var colors = context.theme().colors();
        int fill = enabled ? AutismTheme.recolor(0xAA10301D, Channel.SUCCESS) : AutismTheme.recolor(0xAA351317, Channel.DANGER);
        int border = enabled ? colors.success : colors.accent;
        UiRenderer.frame(context.graphics(), bounds, fill, border);
        if (hovered) UiRenderer.rect(context.graphics(), bounds.inset(1), 0x12FFFFFF);
        int knobW = Math.max(5, bounds.width() / 2 - 2);
        int knobX = enabled ? bounds.right() - knobW - 3 : bounds.x() + 3;
        UiRenderer.rect(context.graphics(), UiBounds.of(knobX, bounds.y() + 3, knobW, Math.max(1, bounds.height() - 6)), enabled ? colors.success : colors.accent);
    }
}
