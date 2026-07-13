package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactSymbolButton {
    public static final String CLOSE = "X";
    public static final String MOVE_UP = "^";
    public static final String MOVE_DOWN = "v";
    public static final String EXPAND = "+";
    public static final String COLLAPSE = "-";

    private CompactSymbolButton() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, String symbol,
                              boolean hovered, boolean active, boolean danger) {
        if (graphics == null || font == null || bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;
        var context = UiContexts.overlay(graphics, font, -10000, -10000);
        var colors = context.theme().colors();
        int fill = danger ? autismclient.util.AutismTheme.recolor(0xCC351317, autismclient.util.AutismTheme.Channel.DANGER) : 0xBB121319;
        int border = danger ? colors.bad : colors.borderSoft;
        UiRenderer.frame(graphics, bounds, fill, border);
        if (hovered && active) UiRenderer.rect(graphics, bounds.inset(1), 0x20FFFFFF);
        if (!active) UiRenderer.rect(graphics, bounds.inset(1), 0x66000000);
        String glyph = symbol == null ? "" : symbol;
        context.text().drawCentered(graphics, glyph, bounds, active ? colors.text : colors.disabled);
    }

    public static void renderGlyph(GuiGraphicsExtractor graphics, Font font, UiBounds bounds, String symbol,
                                   int color, float alpha) {
        if (graphics == null || font == null || bounds == null || alpha <= 0.001f) return;
        int resolved = UiRenderer.applyAlpha(color, alpha);
        String glyph = symbol == null ? "" : symbol;
        UiContexts.overlay(graphics, font, -10000, -10000).text().drawCentered(graphics, glyph, bounds, resolved);
    }

    public static Font minecraftFont() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }
}
